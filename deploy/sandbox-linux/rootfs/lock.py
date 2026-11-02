#!/usr/bin/env python3
"""从固定的 Ubuntu 快照索引解析根包的依赖闭包，写出可审阅的包锁。

    lock.py --language c --root gcc --root coreutils --output locks/c.lock.json

rootfs 不是一台完整的系统，只装判题要用的包：给出几个根包，脚本沿 Depends 与 Pre-Depends 把它们直接、间接依赖的
包都找出来，锁定版本与 SHA256。规则（每一处选择都打印出来供人审阅，不悄悄决定）：

- 同一个包在 noble 与 noble-updates 里都有时取版本最高的（按 dpkg 的版本比较）；只认 amd64 与 all。
- 一组「A | B」依赖：已选中的包能满足就不再加；否则取第一个能满足的候选。
- 依赖的是虚包（只有别的包 Provides 它）：取第一个提供者，按包名排序以保证结果稳定。
- 版本约束满足不了就报错退出，不猜。

快照地址与 download.py 的 SNAPSHOT 是同一个；换快照要两处一起换。
"""
import argparse
import io
import json
import lzma
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from download import SNAPSHOT

SUITES = ('noble', 'noble-updates')
ARCHITECTURES = ('amd64', 'all')
MAX_INDEX_BYTES = 64 << 20


# ---------- dpkg 版本比较 ----------

def _order(char):
    # dpkg：~ 排在一切之前（包括空），字母在非字母符号之前。
    if char == '~':
        return -1
    if char.isdigit():
        return 0
    if char.isalpha():
        return ord(char)
    return ord(char) + 256


def _compare_part(a, b):
    while a or b:
        prefix_a = re.match(r'[^0-9]*', a).group()
        prefix_b = re.match(r'[^0-9]*', b).group()
        for i in range(max(len(prefix_a), len(prefix_b))):
            ca = _order(prefix_a[i]) if i < len(prefix_a) else 0
            cb = _order(prefix_b[i]) if i < len(prefix_b) else 0
            if ca != cb:
                return -1 if ca < cb else 1
        a, b = a[len(prefix_a):], b[len(prefix_b):]
        digits_a = re.match(r'[0-9]*', a).group()
        digits_b = re.match(r'[0-9]*', b).group()
        na, nb = int(digits_a or 0), int(digits_b or 0)
        if na != nb:
            return -1 if na < nb else 1
        a, b = a[len(digits_a):], b[len(digits_b):]
    return 0


def compare_versions(a, b):
    """按 dpkg 规则比较两个版本，返回 -1、0 或 1。"""
    def split(version):
        epoch, _, rest = version.rpartition(':') if ':' in version else ('0', '', version)
        upstream, _, revision = rest.rpartition('-') if '-' in rest else (rest, '', '0')
        return int(epoch or 0), upstream, revision

    ea, ua, ra = split(a)
    eb, ub, rb = split(b)
    if ea != eb:
        return -1 if ea < eb else 1
    return _compare_part(ua, ub) or _compare_part(ra, rb)


def satisfies(version, op, wanted):
    if op is None:
        return True
    c = compare_versions(version, wanted)
    return {'<<': c < 0, '<=': c <= 0, '=': c == 0, '>=': c >= 0, '>>': c > 0}[op]


# ---------- 索引 ----------

def parse_stanzas(text):
    stanzas, fields, key = [], {}, None
    for line in text.splitlines():
        if not line:
            if fields:
                stanzas.append(fields)
            fields, key = {}, None
        elif line[0].isspace():
            if key:
                fields[key] += '\n' + line.strip()
        else:
            key, _, value = line.partition(':')
            fields[key] = value.strip()
    if fields:
        stanzas.append(fields)
    return stanzas


def fetch_index(base, suite, component, open_url=urllib.request.urlopen, attempts=3, sleep=time.sleep):
    url = f'{base}dists/{suite}/{component}/binary-amd64/Packages.xz'
    for attempt in range(1, attempts + 1):
        try:
            # 30 秒是单次 socket 操作的期限；transport 的地址故障转移也要求连接期限不超过 30 秒。
            with open_url(url, timeout=30) as response:
                body = response.read(MAX_INDEX_BYTES + 1)
            break
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            # 快照站点偶尔返回 503 或很慢：只重试服务端与网络错误，4xx 说明地址不对，直接失败。
            if isinstance(error, urllib.error.HTTPError) and error.code < 500 or attempt == attempts:
                raise
            print(f'重试 {url}（第 {attempt} 次失败：{error}）', file=sys.stderr)
            sleep(5 * attempt)
    if len(body) > MAX_INDEX_BYTES:
        raise ValueError(f'package index too large: {url}')
    with lzma.open(io.BytesIO(body), 'rt', encoding='utf-8') as stream:
        return parse_stanzas(stream.read())


def read_index(path):
    if path.stat().st_size > MAX_INDEX_BYTES:
        raise ValueError(f'package index too large: {path}')
    with lzma.open(path, 'rt', encoding='utf-8') as stream:
        return parse_stanzas(stream.read())


class Index:
    """每个包名取最高版本；Provides 建成「虚包 → 提供者」。"""

    def __init__(self, stanzas):
        self.packages = {}
        for stanza in stanzas:
            name, arch = stanza.get('Package'), stanza.get('Architecture')
            if not name or arch not in ARCHITECTURES or 'SHA256' not in stanza or 'Filename' not in stanza:
                continue
            current = self.packages.get(name)
            if current is None or compare_versions(stanza['Version'], current['Version']) > 0:
                self.packages[name] = stanza
        self.providers = {}
        for name, stanza in self.packages.items():
            for group in parse_relations(stanza.get('Provides', '')):
                for virtual, _, _ in group:
                    self.providers.setdefault(virtual, []).append(name)
        for names in self.providers.values():
            names.sort()


RELATION = re.compile(r'^([a-z0-9][a-z0-9+.-]*)(?::[a-z0-9-]+)?\s*(?:\(\s*(<<|<=|=|>=|>>)\s*([^)\s]+)\s*\))?\s*(?:\[[^]]*\])?\s*(?:<[^>]*>)*$')


def parse_relations(field):
    """「a (>= 1), b | c」→ [[('a','>=','1')], [('b',None,None), ('c',None,None)]]。"""
    groups = []
    for group in field.split(','):
        group = group.strip()
        if not group:
            continue
        alternatives = []
        for alternative in group.split('|'):
            match = RELATION.match(alternative.strip())
            if not match:
                raise ValueError(f'cannot parse relation: {alternative.strip()!r}')
            alternatives.append(match.groups())
        groups.append(alternatives)
    return groups


# ---------- 解析闭包 ----------

def resolve(index, roots):
    """返回（选中的包名 → 索引条目，审阅说明列表）。"""
    chosen, notes, queue = {}, [], []

    def provided(name, op, version):
        # 已选中的包按包名或 Provides 能否满足这条依赖。
        stanza = chosen.get(name)
        if stanza is not None and satisfies(stanza['Version'], op, version):
            return True
        return op is None and any(provider in chosen for provider in index.providers.get(name, []))

    def candidate(name, op, version):
        stanza = index.packages.get(name)
        if stanza is not None:
            return name if satisfies(stanza['Version'], op, version) else None
        if op is None and index.providers.get(name):
            return index.providers[name][0]
        return None

    def add(name, reason):
        if name not in chosen:
            chosen[name] = index.packages[name]
            queue.append(name)

    for root in roots:
        if root not in index.packages:
            raise ValueError(f'root package not found: {root}')
        add(root, 'root')
    while queue:
        name = queue.pop(0)
        stanza = chosen[name]
        for field in ('Pre-Depends', 'Depends'):
            for group in parse_relations(stanza.get(field, '')):
                if any(provided(*alternative) for alternative in group):
                    continue
                picked = None
                for alternative in group:
                    picked = candidate(*alternative)
                    if picked:
                        break
                text = ' | '.join(a[0] + (f' ({a[1]} {a[2]})' if a[1] else '') for a in group)
                if picked is None:
                    raise ValueError(f'{name}: unsatisfiable {field}: {text}')
                if len(group) > 1 or picked != group[0][0]:
                    notes.append(f'{name}: {field} "{text}" → {picked}')
                add(picked, name)
    return chosen, notes


def local_filename(stanza):
    # apt 的本地文件名保留转义后的 epoch（1:2.5 → 1%3a2.5），现有包锁与 download.py 都按这个约定。
    version = stanza['Version'].replace(':', '%3a')
    return f"{stanza['Package']}_{version}_{stanza['Architecture']}.deb"


def lock_document(language, roots, chosen, base, components):
    packages = [
        {'file': local_filename(s), 'package': s['Package'], 'version': s['Version'],
         'architecture': s['Architecture'], 'sha256': s['SHA256']}
        for s in sorted(chosen.values(), key=lambda s: s['Package'])
    ]
    return {
        'version': 1,
        'architecture': 'amd64',
        'layout': 'usr-merged',
        'source': f"Ubuntu {'/'.join(SUITES)} ({'/'.join(components)}) via {base}; resolved by rootfs/lock.py",
        'language': language,
        'roots': list(roots),
        'packages': packages,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--language', required=True, help='语言编号，与 judge 的语言注册表一致')
    parser.add_argument('--root', action='append', required=True, help='根包，可重复')
    parser.add_argument('--component', action='append', help='索引组件，默认 main；需要 universe 时显式给出')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--address-failover', action='store_true', help='与 download.py 相同：连接期限内尝试同源 DNS 的其他地址')
    parser.add_argument('--index-dir', type=Path,
                        help='改读预先下载的索引 <套件>-<组件>-Packages.xz（同一快照），便于离线复现与留档审阅')
    args = parser.parse_args(argv)
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,31}', args.language):
        parser.error('invalid language id')
    components = tuple(args.component or ['main'])
    open_url = urllib.request.urlopen
    if args.address_failover:
        from transport import source_urlopen
        open_url = source_urlopen(SNAPSHOT)
    stanzas = []
    for suite in SUITES:
        for component in components:
            if args.index_dir:
                stanzas += read_index(args.index_dir / f'{suite}-{component}-Packages.xz')
            else:
                stanzas += fetch_index(SNAPSHOT, suite, component, open_url)
    chosen, notes = resolve(Index(stanzas), args.root)
    for note in notes:
        print('选择：' + note)
    document = lock_document(args.language, args.root, chosen, SNAPSHOT, components)
    args.output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'✓ {args.language}：{len(chosen)} 个包，写入 {args.output}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
