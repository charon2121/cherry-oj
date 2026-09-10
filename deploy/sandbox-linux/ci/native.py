#!/usr/bin/env python3
"""Run native installation and recovery checks on an exclusive ephemeral Linux VM."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import time

from command import install_signal_handlers, run
from kernel import verify_build
from native_control import validate_registration
from native_resources import Installation, RECORD, ETC, STATE, UNITS
import native_results
from owned import BASE, MARKER, Owned, github_vm, preflight
from report import ROOT, Report, digest, git_sha, read_json, validate, verify_files

INSTALL = ROOT / 'deploy/sandbox-linux/install'


def wait_for(check, seconds=10):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if value := check():
            return value
        time.sleep(.05)
    raise TimeoutError('native deployment observation did not arrive')


def registrations(directory):
    if (directory / 'protocol-error').exists():
        raise ValueError('native protocol receiver recorded an error')
    return read_json(directory / 'events.json') if (directory / 'events.json').exists() else []


def cleanup(output, owned):
    try:
        owned.stop(*reversed(owned.data['units']))
        if RECORD.exists() or RECORD.is_symlink():
            Installation.load(output).cleanup()
        owned.cleanup()
    except BaseException as error:
        (Path(output) / 'cleanup-error.json').write_text(json.dumps(dict(type=type(error).__name__, message=str(error))) + '\n')
        raise


class Native:
    def __init__(self, build, report, owned):
        self.build, self.report, self.owned = build, report, owned
        self.active = 'install'
        self.node = 'ci-native-' + owned.identity
        self.control = BASE / 'control'
        self.fingerprint = None

    def command(self, name, argv, seconds=90):
        unit = 'cherry-sandbox-test-work048-native-' + name + '-' + self.owned.identity
        self.owned.register(unit)
        self.owned.launch(unit, argv, name + '.log', seconds=seconds)

    def start_control(self):
        self.control.mkdir(mode=0o700)
        token = self.control / 'token'
        token.write_text(secrets.token_hex(32))
        token.chmod(0o600)
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        unit = 'cherry-sandbox-test-work048-native-control-' + self.owned.identity
        self.owned.register(unit)
        self.owned.launch(unit, ['python3', '-B', ROOT / 'deploy/sandbox-linux/ci/native_control.py',
                                '--directory', self.control, '--node', self.node, '--port', port],
                          'control-start.log', seconds=900, wait=False)
        wait_for(lambda: (self.control / 'ready').exists())
        return port

    def prepare(self, port):
        source = BASE / 'release'
        shutil.copytree(self.build / 'release', source, symlinks=True)
        shutil.copytree(self.build / 'cpp-rootfs/rootfs', source / 'rootfs', symlinks=True)
        shutil.copy2(self.build / 'cpp-rootfs/manifest.json', source / 'manifest.json')
        review = BASE / 'review'
        run(['python3', '-B', INSTALL / 'render.py', '--output', review, '--release', self.node,
             '--node-id', self.node, '--control-url', 'http://127.0.0.1:' + str(port),
             '--advertise-url', 'http://127.0.0.1:15051', '--manifest-sha256', digest(source / 'manifest.json')],
            self.report.output / 'render.log', 10)
        Installation(self.report.output, review)
        return source, review

    def identity(self, previous_session=None):
        def received():
            entries = [e['value'] for e in registrations(self.control) if e['route'] == 'register']
            if entries and entries[-1]['sessionId'] != previous_session:
                return entries[-1]
        value = wait_for(received)
        validate_registration(value, self.node)
        if self.fingerprint is not None and value['environmentFingerprint'] != self.fingerprint:
            raise ValueError('native environment changed during recovery')
        self.fingerprint = value['environmentFingerprint']
        return value

    def execute(self):
        port = self.start_control()
        source, review = self.prepare(port)
        self.command('install', ['python3', '-B', INSTALL / 'manage.py', 'install', '--review', review,
                                '--release-source', source, '--token-file', self.control / 'token'])
        # Read the audited receipt, never the token-bearing judge configuration.
        receipt = read_json(STATE / 'installation.json')
        if receipt['status'] != 'installed' or receipt['enabled'] is not False:
            raise ValueError('initial installation did not stay stopped and disabled')
        self.command('start', ['python3', '-B', INSTALL / 'manage.py', 'start'])
        identity = self.identity()
        wait_for(lambda: any(e['route'] == 'heartbeat' for e in registrations(self.control)), seconds=25)
        deployment = read_json(ETC / 'deployment.json')
        if len(deployment['limits']) != 24:
            raise ValueError('native resource limits incomplete')
        (self.report.output / 'installation.json').write_text(json.dumps(receipt, indent=2) + '\n')
        (self.report.output / 'identity.json').write_text(json.dumps(identity, indent=2) + '\n')
        (self.report.output / 'deployment.json').write_text(json.dumps(deployment, indent=2) + '\n')
        manifest_hash = digest(ETC / 'deployment.json')
        self.report.record(['native.install'], 'PASS', ['install.log', 'start.log', 'installation.json', 'identity.json', 'deployment.json'])
        cases = [('native', 'verify-native.py', [], 90),
                 *[(name, 'verify-lifecycle.py', ['--case', name], 90) for name in ('helper-config', 'rootfs-manifest', 'helper-binary')],
                 *[('kill-' + name, 'verify-faults.py', ['--case', name], 90) for name in ('judge', 'sandbox', 'helper')],
                 ('caps', 'verify-capabilities.py', [], 120), ('uninstall', 'verify-uninstall.py', [], 90)]
        for name, script, args, seconds in cases:
            self.active = name
            previous = self.identity()['sessionId']
            self.command(name, ['python3', '-B', STATE / 'operations' / script, *args], seconds)
            native_results.check(name, self.report.output / (name + '.log'), self.fingerprint)
            self.identity(previous_session=previous if name != 'native' else None)
            if digest(ETC / 'deployment.json') != manifest_hash:
                raise ValueError('native manifest changed after restoration')
            self.report.record(['native.' + name], 'PASS', [name + '.log'])
        events = registrations(self.control)
        (self.report.output / 'registration-events.json').write_text(json.dumps(events, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cleanup', action='store_true')
    args = parser.parse_args()
    github_vm()
    install_signal_handlers()
    if args.cleanup:
        if MARKER.exists() or MARKER.is_symlink():
            cleanup(args.output, Owned.load(args.output))
        return
    if args.build is None:
        parser.error('--build is required')
    report = Report('native', args.output, git_sha(), os.environ['GITHUB_RUN_ID'], {})
    owned, native, clean = None, None, False
    try:
        report.data['environment'] = preflight()
        metadata = verify_build(args.build)
        (report.output / 'build.json').write_text(json.dumps(metadata, indent=2) + '\n')
        (report.output / 'environment.json').write_text(json.dumps(report.data['environment'], indent=2) + '\n')
        owned = Owned(args.output)
        native = Native(args.build, report, owned)
        native.execute()
    except BaseException as error:
        (report.output / 'failure.json').write_text(json.dumps(dict(type=type(error).__name__, message=str(error))) + '\n')
        case = 'native.' + native.active if native else 'native.install'
        if next(c for c in report.data['cases'] if c['id'] == case)['status'] == 'NOT_RUN':
            status = 'CANCELLED' if isinstance(error, (InterruptedError, KeyboardInterrupt)) else ('FAIL' if native else 'ENVIRONMENT_ERROR')
            report.record([case], status, ['failure.json'])
        raise
    finally:
        try:
            if native is not None and (native.control / 'events.json').exists():
                events = read_json(native.control / 'events.json')
                (report.output / 'registration-events.json').write_text(json.dumps(events, indent=2) + '\n')
                (report.output / 'protocol.json').write_text(json.dumps(dict(error=(native.control / 'protocol-error').exists())) + '\n')
        finally:
            try:
                if owned is not None:
                    cleanup(report.output, owned)
                    clean = True
            finally:
                report.finish(clean)
    validate(report.data, 'native', git_sha())
    verify_files(report.output, report.data)


if __name__ == '__main__':
    main()
