#!/usr/bin/env python3
"""Download locked Ubuntu packages; indexes locate files, the reviewed SHA256 authorizes bytes."""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import lzma
from pathlib import Path, PurePosixPath
from typing import Any
import tempfile
import urllib.parse
import urllib.request

from diagnostics import Diagnostics
from artifacts import Archive, read_lock

BASE = 'https://archive.ubuntu.com/ubuntu/'
# The live archive keeps only current versions; locked packages vanish after updates.
# Download from the snapshot taken when the lock was generated; change both together.
SNAPSHOT = 'https://snapshot.ubuntu.com/ubuntu/20260910T000000Z/'


def locations(open_url=urllib.request.urlopen, diagnostics=None, base=BASE):
    diagnostics = diagnostics or Diagnostics(False)
    result = {}
    for number, suite in enumerate(('noble', 'noble-updates'), 1):
        with diagnostics.request(number, suite + '-Packages.xz') as observation:
            url = base + 'dists/' + suite + '/main/binary-amd64/Packages.xz'
            with observation.open(open_url, url) as response:
                # Compressed indexes have a fixed size ceiling; never trust Content-Length.
                observation.phase('body')
                body = response.read((32 << 20) + 1)
                observation.progress(len(body))
            if len(body) > 32 << 20:
                raise ValueError('package index too large')
            observation.phase('parse')
            import io
            with lzma.open(io.BytesIO(body), 'rt') as stream:
                fields = {}
                for line in stream:
                    if line == '\n':
                        if 'Package' in fields and 'Filename' in fields:
                            result[fields['Package']] = fields['Filename']
                        fields = {}
                    elif not line[0].isspace():
                        key, _, value = line.partition(': ')
                        fields[key] = value.strip()
            observation.phase('index_ready')
    return result


def archive_path(package, location):
    # apt's local filename retains an escaped epoch; pool filenames omit it.
    version = package['version'].split(':', 1)[-1]
    name = package['package'] + '_' + version + '_' + package['architecture'] + '.deb'
    path = PurePosixPath(location).parent / name
    if path.parts[0] != 'pool' or '..' in path.parts or PurePosixPath(name).name != name:
        raise ValueError('unsafe archive location')
    return path


class HTTPSRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req: urllib.request.Request, fp: Any, code: int, msg: str,
                         headers: Any, newurl: str) -> urllib.request.Request | None:
        if urllib.parse.urlsplit(newurl).scheme != 'https':
            raise ValueError('归档跳转必须继续使用 HTTPS')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def download_archive(archive: Archive, output: Path, resume: bool) -> str:
    target = output / archive.file
    if target.exists() or target.is_symlink():
        if not resume:
            raise ValueError('拒绝已有归档: ' + archive.file)
        archive.verify(target)
        return archive.file
    opener = urllib.request.build_opener(HTTPSRedirect())
    with tempfile.TemporaryDirectory(prefix='.archive-', dir=output) as scratch:
        staged = Path(scratch) / archive.file
        total = 0
        with opener.open(archive.url, timeout=30) as response, staged.open('xb') as stream:
            while chunk := response.read(65536):
                total += len(chunk)
                if total > archive.size_bytes:
                    raise ValueError('归档下载超过锁定大小: ' + archive.file)
                stream.write(chunk)
        archive.verify(staged)
        staged.rename(target)
    return archive.file


def download(lock: Path, output: Path, resume: bool = False, address_failover: bool = False, base: str = BASE) -> None:
    open_url = urllib.request.urlopen
    if address_failover:
        from transport import source_urlopen
        open_url = source_urlopen(base)
    document = read_lock(lock)
    packages = document['packages']
    diagnostics = Diagnostics(address_failover)
    output.mkdir(mode=0o755, exist_ok=resume)
    if output.is_symlink():
        raise ValueError("output must not be a symlink")
    # 索引只用来定位缺的包：--resume 时已有的文件按摘要核对即可，全都在就不访问快照站点
    # （快照偶尔整片不可用，命中缓存的构建不该因此失败）。
    missing = [p for p in packages if not (output / p['file']).exists()]
    paths = locations(open_url, diagnostics, base) if missing else {}

    def fetch(item):
        number, package = item
        with diagnostics.request(number, package['file']) as observation:
            filename = package['file']
            if PurePosixPath(filename).name != filename:
                raise ValueError('unsafe package name')
            target = output / filename
            if target.exists() or target.is_symlink():
                if not resume or target.is_symlink() or not target.is_file():
                    raise ValueError('refusing existing output: '+filename)
                with target.open('rb') as existing:
                    if hashlib.file_digest(existing,'sha256').hexdigest() != package['sha256']:
                        raise ValueError('existing package differs: '+filename)
                observation.phase('verified_existing')
                return filename
            # 只有真要下载时才需要索引里的位置。
            path = archive_path(package, paths[package['package']])
            if path.parts[0] != 'pool' or '..' in path.parts:
                raise ValueError('unsafe archive location')
            url = base + urllib.parse.quote(str(path))
            h = hashlib.sha256()
            total = 0
            with observation.open(open_url, url) as response, target.open('xb') as file:
                observation.phase('body')
                while chunk := response.read(64 << 10):
                    total += len(chunk)
                    if total > 100 << 20:
                        raise ValueError('package exceeds download limit')
                    h.update(chunk)
                    file.write(chunk)
                    observation.progress(total)
            observation.phase('verify')
            if h.hexdigest() != package['sha256']:
                raise ValueError('locked package SHA256 mismatch: ' + filename)
            observation.phase('verified')
            return filename

    with ThreadPoolExecutor(max_workers=4) as workers:
        for filename in workers.map(fetch, enumerate(packages, 3)):
            # Keep legacy lines from splitting concurrent JSON observations.
            with diagnostics.lock:
                print('verified ' + filename, flush=True)
    for value in document['archives']:
        print('verified ' + download_archive(Archive.parse(value), output, resume), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--resume', action='store_true', help='reuse only already hash-verified files')
    parser.add_argument('--address-failover', action='store_true', help='try same-origin DNS peers within the connection deadline (direct HTTPS only)')
    args = parser.parse_args()
    download(args.lock, args.output, args.resume, args.address_failover, SNAPSHOT)
