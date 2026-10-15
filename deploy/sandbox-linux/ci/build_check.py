"""核对 prepare.py 产出的构建目录：来源、工具链与二进制摘要都与 build.json 一致。"""
from pathlib import Path

from report import digest, git_sha, harness_sha, read_json


def verify_build(build: Path):
    data = read_json(build / 'build.json')
    if data['sourceSha'] != git_sha() or data['harnessSha'] != harness_sha() or data['architecture'] != 'x86_64':
        raise ValueError('build provenance mismatch')
    paths = {'packageLock': build / 'release/packages.lock.json', 'rootfsManifest': build / 'cpp-rootfs/manifest.json'}
    if any(digest(path) != data[key] for key, path in paths.items()):
        raise ValueError('build artifact digest mismatch')
    for name, path in (('judge', 'bin/judge'), ('executor', 'libexec/sandbox')):
        if digest(build / 'release' / path) != data['binaries'][name]:
            raise ValueError('binary digest mismatch')
    return data
