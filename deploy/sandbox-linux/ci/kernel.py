#!/usr/bin/env python3
"""Run the existing Linux regressions in one exclusive, disposable GitHub VM."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

from command import install_signal_handlers, run
from owned import BASE, MARKER, Owned, github_vm, preflight
from report import ROOT, Report, digest, git_sha, harness_sha, manifest, read_json, validate, verify_files
import results

TESTS = ROOT / 'deploy/sandbox-linux/tests'
# 测试构建的执行器编译进去的受信配置路径；持有委派的单元在这里写配置。
CONFIG = BASE / 'executor.conf'
SERVICE_UID = 61001


def unit_output(unit):
    if not unit:
        return ''
    journal = subprocess.run(['journalctl', '--unit', unit, '--no-pager', '--lines', '60'],
                             capture_output=True, text=True)
    detail = (journal.stdout or journal.stderr or '').strip()
    return '\n' + detail if detail else ''


def verify_build(build):
    data = read_json(build / 'build.json')
    if data['sourceSha'] != git_sha() or data['harnessSha'] != harness_sha() or data['architecture'] != 'x86_64':
        raise ValueError('build provenance mismatch')
    paths = {'packageLock': build / 'release/packages.lock.json', 'rootfsManifest': build / 'cpp-rootfs/manifest.json',
             'probe': build / 'probe', 'testExecutor': build / 'test-executor'}
    if any(digest(path) != data[key] for key, path in paths.items()):
        raise ValueError('build artifact digest mismatch')
    for name, path in (('sandbox', 'bin/sandbox'), ('judge', 'bin/judge'), ('executor', 'libexec/sandbox')):
        if digest(build / 'release' / path) != data['binaries'][name]:
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
        shutil.copy2(self.build / 'release/bin/sandbox', base / 'sandbox')
        shutil.copy2(self.build / 'probe', base / 'probe')
        # 测试构建的执行器把受信配置编译为 BASE/executor.conf；与生产一样以 setuid-root 安装。
        executor = base / 'sandbox-executor'
        shutil.copy2(self.build / 'test-executor', executor)
        os.chown(executor, 0, SERVICE_UID)
        executor.chmod(0o4754)
        for name in ('holder.py', 'http_chain.py', 'http_service.py', 'executor_client.py', 'identity_sample.py'):
            shutil.copy2(TESTS / name, base / name)
        shutil.copy2(ROOT / 'deploy/sandbox-linux/install/sandbox-start.py', base / 'sandbox-start.py')
        run(['python3', TESTS / 'prepare_fixture.py', base], self.report.output / (kind + '-fixture.log'), 10)
        if cpp:
            # copytree preserves locked modes/links; root ownership comes from this root invocation.
            shutil.copytree(self.build / 'cpp-rootfs', base / 'cpp-rootfs-v2', symlinks=True)
        return base

    def hold(self, base, unit, cpp=False):
        """启动持有委派子树的单元，等它写好执行器配置。"""
        self.owned.register(unit)
        self.owned.launch(unit, ['python3', base / 'holder.py', base, unit] + (['cpp'] if cpp else []),
                          base.name.split('-')[1] + '-holder.log', memory=768, tasks=192, seconds=180,
                          delegate=True, wait=False)
        deadline = time.monotonic() + 20
        while not (base / 'holder-ready').exists():
            if time.monotonic() > deadline:
                raise TimeoutError('delegation holder did not become ready' + unit_output(unit))
            time.sleep(.05)

    def executor_suite(self):
        """执行器自己的真实内核测试（apps/sandbox/tests），在本次独立的委派单元里运行。"""
        self.active = 'boundary'
        base = self.fixture('boundary')
        holder = 'cherry-sandbox-test-' + base.name + '-holder'
        self.hold(base, holder)
        try:
            unit = 'cherry-sandbox-test-' + base.name + '-suite'
            self.owned.register(unit)
            jobs = (base / 'holder-ready').read_text()
            self.owned.launch(unit, ['python3', ROOT / 'apps/sandbox/tests/run_tests.py',
                                     '--binary', self.build / 'test-executor', '--probe', self.build / 'probe',
                                     '--root', base, '--config', CONFIG, '--install', base / 'suite-executor',
                                     '--jobs', jobs, '--service-uid', SERVICE_UID,
                                     '--payload-uid', 61002, '--init-uid', 61006],
                              'boundary.log', memory=256, tasks=64, seconds=120)
            results.executor_suite(self.report.output / 'boundary.log')
            self.record('boundary', 'boundary.log')
        finally:
            self.owned.stop(holder)

    def direct(self, cpp=False):
        self.active = 'cpp-limits' if cpp else 'static-identity'
        kind = 'cpp' if cpp else 'static'
        base = self.fixture(kind, cpp)
        holder = 'cherry-sandbox-test-' + base.name + '-holder'
        self.hold(base, holder, cpp)
        try:
            scripts = [('cpp_limits.py', 'cpp-limits')] if cpp else [
                ('inspect_threads.py', 'static-identity'), ('smoke.py', 'static-smoke'),
                ('extended.py', 'static-extended')]
            for script, group in scripts:
                self.active = group
                unit = 'cherry-sandbox-test-work048-' + group + '-' + self.owned.identity
                self.owned.register(unit)
                log = group + '.log'
                shutil.copy2(TESTS / script, base / script)
                self.owned.launch(unit, ['python3', base / script, base], log, memory=256, tasks=64)
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
            self.owned.stop(holder)

    def chain(self, mode):
        self.active = 'chain-' + mode
        base = self.fixture('chain-' + mode, cpp=True)
        unit = 'cherry-sandbox-test-' + base.name
        self.owned.register(*(unit + '-' + suffix for suffix in ('http', 'driver')))
        log = 'chain-' + mode + '.log'
        try:
            run(['python3', TESTS / 'chain_batch.py', base, mode, unit], self.report.output / log, 190)
            results.chain(self.report.output / log, mode)
            self.record('chain-' + mode, log)
        finally:
            self.owned.stop(unit + '-driver', unit + '-http')

    def fault(self, capacity=False):
        mode = 'capacity' if capacity else 'fault'
        self.active = mode
        base = self.fixture('fault-' + mode)
        unit = 'cherry-sandbox-test-' + base.name
        self.owned.register(*(unit + '-' + suffix for suffix in ('http', 'driver')))
        log = mode + '.log'
        try:
            shutil.copy2(TESTS / 'fault_batch.py', base / 'fault_batch.py')
            self.owned.launch(unit + '-driver', ['python3', base / 'fault_batch.py', base] +
                              (['capacity'] if capacity else []), log, seconds=150)
            results.markers(self.report.output / log, sentinel='PASS fault chain', required=('before', 'after'))
            required = ('pool-saturation', 'peer-isolation-and-privilege', 'handler-saturation', 'aggregate-memory',
                        'task-local-memory') if capacity else ('missing-command', 'invalid-executable', 'queued-disconnect',
                        'init-SIGKILL', 'HTTP-SIGKILL-restart', 'executor-SIGKILL-restart', 'HTTP-graceful-stop-restart')
            results.markers(self.report.output / log, required=required)
            self.record(mode, log)
        finally:
            self.owned.stop(unit + '-driver', unit + '-http')

    def execute(self):
        self.active = 'go-unit'
        shutil.copy2(self.build / 'logs/go-linux.log', self.report.output / 'go-linux.log')
        results.linux_units(self.report.output / 'go-linux.log')
        self.record('go-unit', 'go-linux.log')
        self.executor_suite()
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
