#!/usr/bin/env python3
"""按每门语言一份的包锁（locks/<语言>.lock.json）组装发布里的工具链。

    toolchains.py union --output union.lock.json                    # 所有语言的包合成一份锁，下载与缓存只取一次
    toolchains.py build --release REL --packages PKGS --work WORK   # REL/toolchains/<语言>/{rootfs,manifest.json,packages.lock.json}
    toolchains.py digests --release REL                             # 每行 <语言>=<manifest SHA256>，即 render.py 的 --toolchain
    toolchains.py prune --lock union.lock.json --packages PKGS      # 删掉包目录里锁没列出的 .deb（旧缓存留下的）

每门语言一棵独立的 rootfs，互不共享文件：编译器、运行库按各自的包锁解包，换一门语言的工具链不碰别的语言。
构建必须在原生 Linux 文件系统上进行（见 README）。
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any, Sequence

import build as builder
from artifacts import read_lock

LOCKS = Path(__file__).resolve().parent / 'locks'
LANGUAGE = re.compile(r'[a-z][a-z0-9-]{0,31}')


def locks(directory: Path = LOCKS) -> dict[str, Path]:
    """语言编号 → 包锁路径；文件名就是语言编号，且必须与锁里的 language 一致。"""
    result = {}
    for path in sorted(directory.glob('*.lock.json')):
        language = path.name[:-len('.lock.json')]
        if not LANGUAGE.fullmatch(language) or read_lock(path, for_build=True)['language'] != language:
            raise ValueError(f'lock file name and language differ: {path}')
        result[language] = path
    if not result:
        raise ValueError(f'no package locks in {directory}')
    return result


def union(paths: Sequence[Path]) -> dict[str, Any]:
    """所有语言的包合成一份锁：同一个包文件在两份锁里的摘要必须相同，否则说明锁来自不同快照，拒绝。"""
    documents = [read_lock(path) for path in paths]
    base = documents[0]
    for document in documents[1:]:
        for key in ('version', 'architecture', 'layout'):
            if document.get(key) != base.get(key):
                raise ValueError(f'locks disagree on {key}')
    packages = {}
    for document in documents:
        for package in document['packages']:
            seen = packages.setdefault(package['file'], package)
            if seen['sha256'] != package['sha256']:
                raise ValueError('locks disagree on ' + package['file'])
    archives = {}
    for document in documents:
        for archive in document['archives']:
            seen = archives.setdefault(archive['file'], archive)
            if seen != archive:
                raise ValueError('locks disagree on ' + archive['file'])
    return {'version': base['version'], 'architecture': base['architecture'], 'layout': base.get('layout'),
            'source': 'union of ' + ', '.join(p.name for p in paths) + ' for download and caching only',
            'packages': [packages[name] for name in sorted(packages)],
            'archives': [archives[name] for name in sorted(archives)]}


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def build_release(release: Path, packages: Path, work: Path, directory: Path = LOCKS) -> None:
    toolchains = release / 'toolchains'
    toolchains.mkdir(mode=0o755)
    # 与 build.py 一样不复用已有目录：每门语言的中间产物放在新建的 work/<语言>/ 下，失败时留着检查。
    work.mkdir(mode=0o755)
    for language, lock in locks(directory).items():
        target = toolchains / language
        target.mkdir(mode=0o755)
        shutil.copyfile(lock, target / 'packages.lock.json')
        output = work / language
        builder.build(target / 'packages.lock.json', packages, output)
        shutil.copytree(output / 'rootfs', target / 'rootfs', symlinks=True)
        shutil.copyfile(output / 'manifest.json', target / 'manifest.json')
        print(f'{language}={digest(target / "manifest.json")}')


def prune(lock: Path, packages: Path) -> None:
    """CI 的包缓存可能按前缀回退命中旧锁的缓存：删掉锁里没有的 .deb，包目录才与锁完全一致（复制校验要求如此）。"""
    document = read_lock(lock)
    listed = {item['file'] for item in document['packages'] + document['archives']}
    for path in sorted(packages.iterdir()):
        if (path.name not in listed and (path.suffix == '.deb' or path.name.endswith('.tar.gz'))
                and path.is_file() and not path.is_symlink()):
            path.unlink()
            print('removed unlisted ' + path.name)


def release_digests(release: Path) -> dict[str, str]:
    root = release / 'toolchains'
    return {path.name: digest(path / 'manifest.json') for path in sorted(root.iterdir())}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest='command', required=True)
    union_parser = commands.add_parser('union')
    union_parser.add_argument('--output', type=Path, required=True)
    build_parser = commands.add_parser('build')
    build_parser.add_argument('--release', type=Path, required=True)
    build_parser.add_argument('--packages', type=Path, required=True)
    build_parser.add_argument('--work', type=Path, required=True)
    prune_parser = commands.add_parser('prune')
    prune_parser.add_argument('--lock', type=Path, required=True)
    prune_parser.add_argument('--packages', type=Path, required=True)
    digests_parser = commands.add_parser('digests')
    digests_parser.add_argument('--release', type=Path, required=True)
    args = parser.parse_args(argv)
    if args.command == 'union':
        document = union(list(locks().values()))
        args.output.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    elif args.command == 'prune':
        prune(args.lock, args.packages)
    elif args.command == 'build':
        build_release(args.release, args.packages, args.work)
    else:
        for language, value in release_digests(args.release).items():
            print(f'{language}={value}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
