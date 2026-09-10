#!/usr/bin/env python3
"""Probe a reduced helper capability set without registering a changed environment."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0, '/var/lib/cherry-sandbox/operations')
sys.path.insert(0, '/etc/cherry-sandbox')
import manage
import health

CAPS = ('CAP_SYS_ADMIN', 'CAP_SETUID', 'CAP_SETGID', 'CAP_SETPCAP',
        'CAP_CHOWN', 'CAP_DAC_OVERRIDE', 'CAP_MKNOD')
DIRECTORY = Path('/run/systemd/system/cherry-sandbox-helper.service.d')
DROPIN = DIRECTORY / '90-work048-capability-test.conf'


def stopped():
    manage.run('systemctl', 'stop', *reversed(manage.UNITS[1:]))


def configure(caps):
    content = '[Service]\nCapabilityBoundingSet=\nCapabilityBoundingSet=' + ' '.join(caps)
    content += '\nAmbientCapabilities=\n'
    if 'CAP_SETUID' in caps:
        content += 'AmbientCapabilities=CAP_SETUID\n'
    DROPIN.write_text(content)
    DROPIN.chmod(0o644)
    manage.run('systemctl', 'daemon-reload')
    return content


def helper_state():
    return manage.run('systemctl', 'show', 'cherry-sandbox-helper.service', '-p', 'ActiveState', '--value')


def probe(caps):
    subprocess.run(['systemctl', 'start', 'cherry-sandbox-helper.service'],
                   capture_output=True, timeout=10)
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        if helper_state() == 'failed':
            return False
        if health.ready('helper'):
            return True
        time.sleep(.05)
    raise AssertionError('helper did not reach a definitive state')


def main():
    manage.owned()
    assert not DIRECTORY.exists() and not DIRECTORY.is_symlink()
    spec = importlib.util.spec_from_file_location('native', '/var/lib/cherry-sandbox/operations/verify-native.py')
    native = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native)
    native.clean()
    manage.operate('stop')
    DIRECTORY.mkdir(mode=0o755)
    content = None
    try:
        content = configure(CAPS)
        assert probe(CAPS), 'reduced set failed startup'
        manage.run('systemctl', 'start', 'cherry-sandbox.service')
        manage.run('python3', '/etc/cherry-sandbox/health.py', 'sandbox')
        source = '#include <unistd.h>\n#include <fcntl.h>\nint main(){int f=open("proof",O_CREAT|O_WRONLY,0600);if(f<0)return 2;write(f,"cap-test",8);close(f);if(!fork())sleep(10);return 0;}\n'
        compiled = native.execute(['g++', 'nested/main.cpp', '-o', 'program'],
            inputs={'nested/main.cpp': {'text': source}}, artifacts=['program'],
            limits=dict(native.LIMITS, cpuNs=10_000_000_000, clockNs=20_000_000_000, memoryBytes=256 << 20))
        ref = compiled['artifacts']['program']
        try:
            result = native.execute(['program'], inputs={'program': {'ref': ref}}, outputs=['proof'])
            assert result['outputs']['proof'] == 'cap-test'
            assert result['clockNs'] < 1_000_000_000
            native.clean()
        finally:
            native.call('DELETE', '/blobs/' + ref)
        print(json.dumps(dict(test='seven-capability-set', result='PASS',
                              nestedInput=True, privateOutput=True, descendantsReaped=True)), flush=True)
        stopped()
        for excluded in CAPS:
            content = configure(tuple(cap for cap in CAPS if cap != excluded))
            assert not probe(CAPS), 'capability may be redundant: ' + excluded
            result = manage.run('systemctl', 'show', 'cherry-sandbox-helper.service', '-p', 'Result', '--value')
            assert result != 'start-limit-hit', result
            print(json.dumps(dict(test='remove-capability', removed=excluded, startup='REFUSED')), flush=True)
            stopped()
    finally:
        stopped()
        if DROPIN.exists():
            assert DROPIN.read_text() == content
            DROPIN.unlink()
        DIRECTORY.rmdir()
        manage.run('systemctl', 'daemon-reload')
        manage.owned()
        manage.operate('start')
    print('Original deployment restored; no judge registration under temporary policy.', flush=True)


if __name__ == '__main__':
    main()
