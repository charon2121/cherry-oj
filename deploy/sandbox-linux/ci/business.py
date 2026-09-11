#!/usr/bin/env python3
"""Real browser → five Java services → Kafka → fresh native Linux judge, on an exclusive VM."""
import argparse
import json
import os
from pathlib import Path
import signal
import time

from business_api import API, FIXTURES
from business_config import private
from business_evidence import Evidence
from business_observer import JOBS, Observer, counters, verify_observations
from business_resources import Dependencies, RECORD as BUSINESS_RECORD
from business_results import LIVE_CASES, verify_build as verify_business_build, verify_live
from business_stack import PRIVATE, Stack
from command import install_signal_handlers
from kernel import verify_build
from native import INSTALL, Native
from native_resources import Installation, RECORD as NATIVE_RECORD, STATE
from native_results import check as check_native
from owned import MARKER, Owned, github_vm, preflight
from report import ROOT, Report, git_sha, read_json, validate, verify_files


def save(output, name, value):
    (output / name).write_text(json.dumps(value, indent=2) + '\n')


def deadline(_signal, _frame):
    raise TimeoutError('business suite exceeded its 20 minute execution budget')


def cleanup(output, owned):
    owned.stop(*reversed(owned.data['units']))
    failures = []
    try:
        if BUSINESS_RECORD.exists() or BUSINESS_RECORD.is_symlink():
            Dependencies.load(PRIVATE, output).cleanup()
    except BaseException as error:
        failures.append(type(error).__name__)
    try:
        if NATIVE_RECORD.exists() or NATIVE_RECORD.is_symlink():
            Installation.load(output).cleanup()
    except BaseException as error:
        failures.append(type(error).__name__)
    if failures:
        save(Path(output), 'cleanup-failures.json', failures)
        # Keep BASE and the outstanding claims for the always() cleanup attempt.
        raise RuntimeError('business resource cleanup is incomplete')
    owned.cleanup()


def idle():
    groups = sorted(p.name for p in JOBS.iterdir() if p.is_dir())
    work = STATE / 'service/work'
    files = sorted(p.name for p in work.iterdir() if p.name != '.lock')
    tasks = []
    for path in Path('/proc').glob('[0-9]*/status'):
        try:
            uid = next(line.split()[1:] for line in path.read_text().splitlines() if line.startswith('Uid:'))
            if set(map(int, uid)) & set(range(61002, 61010)):
                tasks.append(int(path.parent.name))
        except (FileNotFoundError, ProcessLookupError):
            continue
    blobs = sorted(p.name for p in (STATE / 'service/blobs').iterdir() if p.name != '.lock')
    return dict(groups=groups, files=files, tasks=tasks, blobs=blobs)


def parent_events():
    parents = [JOBS.parent, JOBS.parent.parent]
    return {str(path): counters((path / 'memory.events.local').read_text()) for path in parents}


def verify_parent_events(before):
    after = parent_events()
    for path, old in before.items():
        if any(after[path].get(key, 0) != old.get(key, 0) for key in ('oom', 'oom_kill', 'oom_group_kill')):
            raise ValueError('node/helper exhausted its own memory budget')
    return after


class Business:
    def __init__(self, args, report, owned):
        self.args, self.report, self.owned = args, report, owned
        self.stack, self.native = None, None
        self.active = 'business.new-environment'

    def execute(self):
        metadata = read_json(self.args.application_build / 'business-build.json')
        verify_business_build(metadata)
        save(self.report.output, 'business-build.json', metadata)
        self.stack = Stack(self.owned, self.report.output, self.args.java)
        self.stack.start_dependencies()
        self.stack.java_services()
        self.native = Native(self.args.build, self.report, self.owned)
        self.native.control.mkdir(mode=0o700)
        private(self.native.control / 'token', self.stack.credentials['control'])
        source, review = self.native.prepare(8084)
        self.native.command('install', ['python3', '-B', INSTALL / 'manage.py', 'install', '--review', review,
                                       '--release-source', source, '--token-file', self.native.control / 'token'])
        self.native.command('start', ['python3', '-B', INSTALL / 'manage.py', 'start'])
        evidence = Evidence(self.stack.dependencies)
        context = evidence.identity(self.native.node)
        # Before resource fixtures, verify the actual installed 24 limits and Linux privileges.
        self.native.command('native', ['python3', '-B', STATE / 'operations/verify-native.py'])
        check_native('native', self.report.output / 'native.log', context['environmentFingerprint'])
        save(self.report.output, 'new-environment.json', context)
        self.report.record([self.active], 'PASS', ['new-environment.json', 'native.log'])
        api = API(self.report.output)
        self.active = 'business.deploy'
        api.login(self.stack.credentials)
        prepared, base = api.prepare(self.owned.identity)
        context.update(prepared)
        save(self.report.output, 'deployment.json', evidence.deployment(context))
        self.report.record([self.active], 'PASS', ['deployment.json'])
        self.active = 'business.calibrate'
        api.calibrate(base)
        calibration = evidence.calibration(context)
        context['languageCalibrationId'] = calibration['id']
        save(self.report.output, 'calibration.json', calibration)
        save(self.report.output, 'context.json', context)
        save(self.report.output, 'preparation-requests.json', api.events)
        self.report.record([self.active], 'PASS', ['calibration.json', 'preparation-requests.json'])
        self.active = 'business.io'
        live_context = dict(context, username=self.stack.credentials['username'], password=self.stack.credentials['password'])
        path = private(PRIVATE / 'live-context.json', json.dumps(live_context))
        os.chown(path, self.stack.runner.pw_uid, self.stack.runner.pw_gid)
        self.stack.browser_server(self.args.node)
        before = parent_events()
        with Observer(PRIVATE, self.report.output):
            self.stack.unit('browser', [self.args.node, ROOT / 'apps/web/node_modules/@playwright/test/cli.js',
                                       'test', '--config', 'playwright.live.config.ts'], wait=True,
                            seconds=900, memory=1024, cwd=ROOT / 'apps/web',
                            environment=dict(CHERRY_LIVE_PRIVATE=str(PRIVATE), CI='true',
                                             PLAYWRIGHT_BROWSERS_PATH=str(self.args.browsers)))
        if read_json(PRIVATE / 'playwright-status.json') != {'status': 'passed'}:
            raise ValueError('browser execution did not pass')
        live = read_json(PRIVATE / 'live.json')
        verify_live(live, context)
        verify_observations(read_json(self.report.output / 'execution-observations.json'), live)
        save(self.report.output, 'parent-memory-events.json', dict(before=before, after=verify_parent_events(before)))
        save(self.report.output, 'live.json', live)
        for key in LIVE_CASES[:8]:
            self.report.record(['business.' + key], 'PASS', ['live.json', 'execution-observations.json'])
        for key, fixture in [('ac', 'sum'), ('wa', 'wrong_answer')]:
            self.active = 'business.' + key
            facts = evidence.formal(context, live[key]['submissionId'], (FIXTURES / (fixture + '.cpp')).read_text())
            if facts['submission']['userId'] != live['history']['userId']:
                raise ValueError('formal submission belongs to another user')
            save(self.report.output, key + '-kafka.json', facts)
            self.report.record([self.active], 'PASS', ['live.json', key + '-kafka.json'])
        self.report.record(['business.history'], 'PASS', ['live.json'])
        self.active = 'business.cleanup'
        # Recheck online session and deployment; a restart must not hide OLE/empty leakage.
        if evidence.identity(self.native.node)['sessionId'] != context['sessionId']:
            raise ValueError('node restarted during business cases')
        evidence.deployment(context)
        deadline = time.monotonic() + 5
        while any((remaining := idle()).values()) and time.monotonic() < deadline:
            time.sleep(.05)
        save(self.report.output, 'live-idle.json', remaining)
        if any(remaining.values()):
            raise ValueError('payload resources survived live requests')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path)
    parser.add_argument('--application-build', type=Path)
    parser.add_argument('--java')
    parser.add_argument('--node')
    parser.add_argument('--browsers', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cleanup', action='store_true')
    args = parser.parse_args()
    github_vm()
    install_signal_handlers()
    if args.cleanup:
        if MARKER.exists() or MARKER.is_symlink():
            cleanup(args.output, Owned.load(args.output))
        return
    if any(value is None for value in (args.build, args.application_build, args.java, args.node, args.browsers)):
        parser.error('build, application-build, java, node and browsers are required')
    report = Report('business', args.output, git_sha(), os.environ['GITHUB_RUN_ID'], {})
    owned, business, clean = None, None, False
    signal.signal(signal.SIGALRM, deadline)
    signal.setitimer(signal.ITIMER_REAL, 1200)
    try:
        report.data['environment'] = preflight()
        save(report.output, 'environment.json', report.data['environment'])
        save(report.output, 'build.json', verify_build(args.build))
        owned = Owned(args.output)
        business = Business(args, report, owned)
        business.execute()
    except BaseException as error:
        # Export only a known step ID, never the Playwright error/call log.
        if business and business.active == 'business.io' and (PRIVATE / 'playwright-step.json').exists():
            progress = read_json(PRIVATE / 'playwright-step.json')
            if progress.get('step') in LIVE_CASES:
                business.active = 'business.' + progress['step']
        # No exception text: HTTP/auth/Playwright errors can contain private values.
        save(report.output, 'failure.json', dict(type=type(error).__name__,
             stage=business.active if business else 'business.new-environment'))
        case = business.active if business else 'business.new-environment'
        if next(c for c in report.data['cases'] if c['id'] == case)['status'] == 'NOT_RUN':
            report.record([case], 'CANCELLED' if isinstance(error, (InterruptedError, KeyboardInterrupt)) else 'FAIL', ['failure.json'])
        if business and business.stack:
            business.stack.diagnose()
        raise RuntimeError('business suite failed; see bounded report') from None
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)  # Cleanup has its own bounded commands.
        try:
            if owned is not None:
                cleanup(report.output, owned)
                clean = True
            if business and business.active == 'business.cleanup' and all(c['status'] == 'PASS' for c in report.data['cases'] if c['id'] != 'business.cleanup'):
                if next(c for c in report.data['cases'] if c['id'] == 'business.cleanup')['status'] == 'NOT_RUN':
                    report.record(['business.cleanup'], 'PASS', ['live-idle.json', 'dependencies-after.json', 'native-resources-after.json'])
        finally:
            report.finish(clean)
    validate(report.data, 'business', git_sha())
    verify_files(report.output, report.data)


if __name__ == '__main__':
    main()
