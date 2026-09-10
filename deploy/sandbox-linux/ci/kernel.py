#!/usr/bin/env python3
"""Run the existing Linux regressions in one exclusive, disposable GitHub VM."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import time

from command import install_signal_handlers, run
from owned import BASE, MARKER, Owned, github_vm, preflight
from report import ROOT, Report, digest, git_sha, harness_sha, manifest, read_json, validate, verify_files
import results

TESTS = ROOT / 'deploy/sandbox-linux/tests'


def ready_socket(path):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        with socket.socket(socket.AF_UNIX) as connection:
            connection.settimeout(.2)
            try:
                connection.connect(str(path))
                return
            except OSError:
                time.sleep(.05)
    raise TimeoutError('helper socket did not become ready')


def verify_build(build):
    data = read_json(build / 'build.json')
    if data['sourceSha'] != git_sha() or data['harnessSha'] != harness_sha() or data['architecture'] != 'x86_64':
        raise ValueError('build provenance mismatch')
    paths = {'packageLock': build / 'release/packages.lock.json', 'rootfsManifest': build / 'cpp-rootfs/manifest.json',
             'probe': build / 'probe', 'boundary': build / 'boundary.test'}
    if any(digest(path) != data[key] for key, path in paths.items()):
        raise ValueError('build artifact digest mismatch')
    for name in ('sandbox-helper', 'sandbox', 'judge'):
        if digest(build / 'release/bin' / name) != data['binaries'][name]:
            raise ValueError('binary digest mismatch')
    return data


class Kernel:
    def __init__(self, build, report, owned):
        self.build, self.report, self.owned = build, report, owned
        self.active = None
        self.groups = {}
        for case in manifest()['cases']:
            if case['suite'] == 'kernel':
                self.groups.setdefault(case['execution'], []).append(case['id'])

    def record(self, group, log):
        self.report.record(self.groups[group], 'PASS', [log])

    def fixture(self, kind, cpp=False):
        base = BASE / ('work048-' + kind + '-' + self.owned.identity)
        base.mkdir(mode=0o755)
        for name in ('sandbox-helper', 'sandbox'):
            shutil.copy2(self.build / 'release/bin' / name, base / name)
        shutil.copy2(self.build / 'probe', base / 'probe')
        shutil.copy2(self.build / 'boundary.test', base / 'boundary.test')
        for name in ('bootstrap.py', 'http_chain.py', 'client.py'):
            shutil.copy2(TESTS / name, base / name)
        run(['python3', TESTS / 'prepare_fixture.py', base], self.report.output / (kind + '-fixture.log'), 10)
        if cpp:
            # copytree preserves locked modes/links; root ownership comes from this root invocation.
            shutil.copytree(self.build / 'cpp-rootfs', base / 'cpp-rootfs-v2', symlinks=True)
        return base

    def boundaries(self):
        self.active = 'boundary'
        base = self.fixture('boundary')
        unit = 'cherry-sandbox-test-' + base.name
        self.owned.register(unit)
        self.owned.launch(unit, ['python3', TESTS / 'boundary_batch.py', base], 'boundary.log',
                          memory=768, tasks=192, seconds=60, delegate=True)
        results.boundary(self.report.output / 'boundary.log')
        self.record('boundary', 'boundary.log')

    def direct(self, cpp=False):
        self.active = 'cpp-limits' if cpp else 'static-identity'
        kind = 'cpp' if cpp else 'static'
        base = self.fixture(kind, cpp)
        helper = 'cherry-sandbox-test-' + base.name + '-helper'
        self.owned.register(helper)
        self.owned.launch(helper, ['python3', TESTS / 'bootstrap.py', base, helper] + (['cpp'] if cpp else []),
                          kind + '-helper.log', memory=768, tasks=192, seconds=180, delegate=True, wait=False)
        try:
            sock = Path('/run') / helper / 'helper.sock'
            ready_socket(sock)
            scripts = [('cpp_limits.py', 'cpp-limits', sock)] if cpp else [
                ('inspect_threads.py', 'static-identity', base / 'helper.json'),
                ('smoke.py', 'static-smoke', sock), ('extended.py', 'static-extended', sock)]
            for script, group, argument in scripts:
                self.active = group
                unit = 'cherry-sandbox-test-work048-' + group + '-' + self.owned.identity
                self.owned.register(unit)
                log = group + '.log'
                self.owned.launch(unit, ['python3', TESTS / script, argument], log)
                path = self.report.output / log
                if group == 'static-smoke':
                    results.markers(path, field='mode', required=('identity', 'cpu', 'memory', 'output', 'network'))
                else:
                    sentinel = {'static-identity': 'thread / namespace / mount / cleanup assertions passed',
                                'static-extended': 'extended assertions passed',
                                'cpp-limits': 'C++ resource / signal / descendant assertions passed'}[group]
                    results.markers(path, sentinel=sentinel)
                self.record(group, log)
        finally:
            self.owned.stop(helper)

    def chain(self, mode):
        self.active = 'chain-' + mode
        base = self.fixture('chain-' + mode, cpp=True)
        unit = 'cherry-sandbox-test-' + base.name
        self.owned.register(*(unit + '-' + suffix for suffix in ('helper', 'http', 'driver')))
        log = 'chain-' + mode + '.log'
        try:
            run(['python3', TESTS / 'chain_batch.py', base, mode, unit], self.report.output / log, 190)
            results.chain(self.report.output / log, mode)
            self.record('chain-' + mode, log)
        finally:
            self.owned.stop(unit + '-driver', unit + '-http', unit + '-helper')

    def fault(self, capacity=False):
        mode = 'capacity' if capacity else 'fault'
        self.active = mode
        base = self.fixture('fault-' + mode)
        unit = 'cherry-sandbox-test-' + base.name
        self.owned.register(*(unit + '-' + suffix for suffix in ('helper', 'http', 'driver')))
        log = mode + '.log'
        try:
            self.owned.launch(unit + '-driver', ['python3', TESTS / 'fault_batch.py', base] +
                              (['capacity'] if capacity else []), log, seconds=150)
            results.markers(self.report.output / log, sentinel='PASS fault chain', required=('before', 'after'))
            required = ('pool-saturation', 'peer-isolation-and-privilege', 'handler-saturation', 'aggregate-memory',
                        'task-local-memory') if capacity else ('missing-command', 'invalid-executable', 'queued-disconnect',
                        'init-SIGKILL', 'HTTP-SIGKILL-restart', 'helper-SIGKILL-restart', 'HTTP-graceful-stop-restart')
            results.markers(self.report.output / log, required=required)
            self.record(mode, log)
        finally:
            self.owned.stop(unit + '-driver', unit + '-http', unit + '-helper')

    def execute(self):
        self.active = 'go-unit'
        shutil.copy2(self.build / 'logs/go-linux.log', self.report.output / 'go-linux.log')
        results.linux_units(self.report.output / 'go-linux.log')
        self.record('go-unit', 'go-linux.log')
        self.boundaries()
        self.direct()
        for mode in ('smoke', 'repeat', 'concurrency'):
            self.chain(mode)
        self.direct(cpp=True)
        self.fault()
        self.fault(capacity=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cleanup', action='store_true')
    args = parser.parse_args()
    github_vm()
    install_signal_handlers()
    if args.cleanup:
        if MARKER.exists():
            Owned.load(args.output).cleanup()
        return
    if args.build is None:
        parser.error('--build is required')
    report = Report('kernel', args.output, git_sha(), os.environ['GITHUB_RUN_ID'], {})
    owned = None
    kernel = None
    clean = False
    try:
        report.data['environment'] = preflight()
        build = verify_build(args.build)
        (report.output / 'build.json').write_text(json.dumps(build, indent=2) + '\n')
        (report.output / 'environment.json').write_text(json.dumps(report.data['environment'], indent=2) + '\n')
        report.record(['kernel.capabilities'], 'PASS', ['environment.json', 'build.json'])
        owned = Owned(args.output)
        kernel = Kernel(args.build, report, owned)
        kernel.execute()
    except BaseException as error:
        (report.output / 'failure.json').write_text(json.dumps({'type': type(error).__name__, 'message': str(error)}) + '\n')
        affected = kernel.groups[kernel.active] if kernel and kernel.active else ['kernel.capabilities']
        pending = [case['id'] for case in report.data['cases'] if case['id'] in affected and case['status'] == 'NOT_RUN']
        status = 'CANCELLED' if isinstance(error, (InterruptedError, KeyboardInterrupt)) else ('FAIL' if kernel else 'ENVIRONMENT_ERROR')
        report.record(pending, status, ['failure.json'])
        raise
    finally:
        try:
            if owned is not None:
                owned.cleanup()
                clean = True
        finally:
            report.finish(clean)
    validate(report.data, 'kernel', git_sha())
    verify_files(report.output, report.data)


if __name__ == '__main__':
    main()
