#!/usr/bin/env python3
"""Run previously manual deployment checks without executing privileged fixtures."""
import argparse
import ast
import os
from pathlib import Path
import platform
import subprocess
import sys
import unittest

from report import ROOT, Report, git_sha, validate, verify_files


def checked_files(suffix):
    data = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard',
                                    '-z', 'deploy/sandbox-linux'], cwd=ROOT)
    return sorted({ROOT / p.decode() for p in data.split(b'\0') if p and p.decode().endswith(suffix)})


def unit_group(directory, output):
    suite = unittest.TestLoader().discover(str(directory), pattern='*_test.py')
    with output.open('w') as log:
        result = unittest.TextTestRunner(stream=log, verbosity=2).run(suite)
    if not result.wasSuccessful() or result.skipped or result.testsRun == 0:
        raise RuntimeError('unit suite failed, empty or skipped: ' + directory.name)
    return result.testsRun


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = Report('basic', args.output, git_sha(), os.environ.get('GITHUB_RUN_ID', 'local'),
                    {'system': platform.system(), 'kernel': platform.release(), 'architecture': platform.machine()})
    try:
        for name in ('install', 'rootfs', 'ci'):
            count = unit_group(ROOT / 'deploy/sandbox-linux' / name, args.output / (name + '.log'))
            if count < {'install': 15, 'rootfs': 6, 'ci': 18}[name]:
                raise RuntimeError('required unit coverage was removed: ' + name)
            report.record(['basic.' + name], 'PASS', [name + '.log'], tests=count)
        paths = checked_files('.py')
        for path in paths:
            ast.parse(path.read_text(), filename=str(path))
        (args.output / 'python.log').write_text('\n'.join(str(p.relative_to(ROOT)) for p in paths) + '\n')
        report.record(['basic.python'], 'PASS', ['python.log'], files=len(paths))
        paths = checked_files('.sh')
        with (args.output / 'shell.log').open('w') as log:
            for path in paths:
                subprocess.run(['sh', '-n', str(path)], check=True, stdout=log, stderr=log, timeout=5)
                log.write(str(path.relative_to(ROOT)) + '\n')
        report.record(['basic.shell'], 'PASS', ['shell.log'], files=len(paths))
    finally:
        report.finish(cleanup=True)  # No services, mounts, accounts or subprocesses survive these synchronous checks.
    validate(report.data, 'basic', git_sha())
    verify_files(args.output, report.data)


if __name__ == '__main__':
    sys.dont_write_bytecode = True
    main()
