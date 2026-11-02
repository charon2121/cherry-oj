#!/usr/bin/env python3
"""Build this checkout and the locked rootfs on a native Linux filesystem, without sudo."""
import argparse
import json
import os
from pathlib import Path
import platform

from command import install_signal_handlers, run
from report import ROOT, digest, git_sha, harness_sha
from report import manifest
from results import linux_units


MODULE = 'cherry-oj/judge-engine/'


def linux_packages():
    return sorted('./' + p[len(MODULE):] for p in manifest()['requiredGoTests'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--packages', type=Path, help='copy and verify an existing package set without downloading')
    args = parser.parse_args()
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise RuntimeError('native Linux/amd64 preparation required')
    output = args.output.resolve()
    output.mkdir(mode=0o755, parents=True, exist_ok=False)
    logs = output / 'logs'
    logs.mkdir()
    install_signal_handlers()
    run(['sh', ROOT / 'deploy/sandbox-linux/build-release.sh', output / 'release'], logs / 'build.log', 240)
    go = ROOT / 'apps/judge-engine'
    # 跑哪些包由清单决定，与 results.linux_units 的期望集合同源；
    # 各存一份的话，包一改位置就会出现「清单要求跑、命令行没跑」。
    run(['go', 'test', '-race', '-count=1', '-json'] + linux_packages(),
        logs / 'go-linux.log', 180, cwd=go)
    linux_units(logs / 'go-linux.log')
    # 所有语言的包合成一份锁，下载、缓存与校验只做一次；每门语言再按自己的锁各建一棵 rootfs。
    lock = output / 'packages.lock.json'
    run(['python3', ROOT / 'deploy/sandbox-linux/rootfs/toolchains.py', 'union', '--output', lock],
        logs / 'union.log', 30)
    if args.packages is not None:
        run(['python3', ROOT / 'deploy/sandbox-linux/ci/packages.py', '--lock', lock,
             '--source', args.packages, '--output', output / 'packages'], logs / 'packages.log', 600)
    else:
        run(['python3', ROOT / 'deploy/sandbox-linux/rootfs/download.py', '--lock', lock,
             '--output', output / 'packages', '--address-failover'], logs / 'download.log', 600)
    run(['python3', ROOT / 'deploy/sandbox-linux/rootfs/toolchains.py', 'build', '--release', output / 'release',
         '--packages', output / 'packages', '--work', output / 'toolchain-work'], logs / 'rootfs.log', 300)
    toolchains = {path.name: digest(path / 'manifest.json') for path in sorted((output / 'release/toolchains').iterdir())}
    metadata = dict(sourceSha=git_sha(), harnessSha=harness_sha(), architecture=platform.machine(),
                    packageLock=digest(lock), toolchains=toolchains,
                    binaries=dict(judge=digest(output / 'release/bin/judge'),
                                  executor=digest(output / 'release/libexec/sandbox')))
    (output / 'build.json').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    main()
