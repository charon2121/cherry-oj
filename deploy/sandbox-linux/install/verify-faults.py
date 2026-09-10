#!/usr/bin/env python3
"""Kill one verified native service during a bounded trial, then verify recovery."""
import argparse
import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import re
import signal
import sys
import time

sys.path.insert(0, '/var/lib/cherry-sandbox/operations')
import manage

GROUP = Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice')
JOBS = GROUP / 'cherry-sandbox-helper.service/jobs'
BLOBS = manage.STATE / 'service/blobs'


def request(port, method, path, data=None):
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=30)
    try:
        connection.request(method, path, None if data is None else json.dumps(data),
                           {'Content-Type': 'application/json'})
        response = connection.getresponse()
        body = response.read(2 << 20)
        assert not response.read(1) and response.status == 200, (response.status, body[:256])
        return json.loads(body) if body else None
    finally:
        connection.close()


def trial(source):
    return request(15051, 'POST', '/judge', dict(
        submissionId='work048-native-fault', problemId='work048-probe',
        problemVersionId='v1', testDataVersionId='unused', languageId='cpp',
        source=source, mode='trial', cases=[dict(input='')],
        limits=dict(cpuNs=1_000_000_000, memoryBytes=64 << 20, clockNs=20_000_000_000)))


def wait_for(check, message, seconds=6):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(.02)
    raise AssertionError(message)


def task_processes():
    result = []
    for path in Path('/proc').glob('[0-9]*/status'):
        try:
            uid = next(line.split()[1] for line in path.read_text().splitlines() if line.startswith('Uid:'))
            if 61002 <= int(uid) <= 61009:
                result.append(int(path.parent.name))
        except (FileNotFoundError, ProcessLookupError):
            continue
    return result


def groups():
    return [p for p in JOBS.iterdir() if p.is_dir()] if JOBS.exists() else []


def running_payload():
    for pid in task_processes():
        try:
            exe = os.readlink(f'/proc/{pid}/exe')
            if exe.startswith('/work/'):
                return pid
        except (FileNotFoundError, ProcessLookupError):
            continue
    return None


def kill_service(unit):
    pid = int(manage.run('systemctl', 'show', unit, '-p', 'MainPID', '--value'))
    assert pid > 1
    fd = os.pidfd_open(pid)
    try:
        exe, uid = {
            'cherry-sandbox-helper.service': ('sandbox-helper', 0),
            'cherry-sandbox.service': ('sandbox', 61001),
            'cherry-sandbox-judge.service': ('judge', 61010),
        }[unit]
        proc = Path('/proc') / str(pid)
        assert Path(os.readlink(proc / 'exe')) == (manage.STATE / 'current/bin' / exe).resolve()
        values = next(line.split()[1:] for line in (proc / 'status').read_text().splitlines() if line.startswith('Uid:'))
        assert values == [str(uid)] * 4
        expected = str(GROUP / unit).removeprefix('/sys/fs/cgroup')
        actual = (proc / 'cgroup').read_text().strip().removeprefix('0::')
        assert actual == expected or (exe == 'sandbox-helper' and actual == expected + '/supervisor')
        signal.pidfd_send_signal(fd, signal.SIGKILL)
        return pid
    finally:
        os.close(fd)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case', choices=('judge', 'sandbox', 'helper'), required=True)
    args = parser.parse_args()
    manage.owned()
    assert not groups() and not task_processes(), 'node must be idle'
    baseline = trial('int main(){return 0;}')
    assert baseline['verdict'] == 'RAN', baseline
    before = {p.name for p in BLOBS.iterdir()}
    manifest_hash = manage.digest(manage.ETC / 'deployment.json')
    unit = {'judge': 'cherry-sandbox-judge.service', 'sandbox': 'cherry-sandbox.service',
            'helper': 'cherry-sandbox-helper.service'}[args.case]
    started = time.monotonic()
    outcome = None
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            # Background descendant extends beyond the observation window, so natural exit cannot fake cleanup.
            future = executor.submit(trial, '#include <unistd.h>\nint main(){fork();sleep(15);return 0;}\n')
            payload = wait_for(running_payload, 'payload did not reach execution', 10)
            killed = kill_service(unit)
            wait_for(lambda: not groups() and not task_processes(), 'tasks survived service crash')
            cleanup_seconds = time.monotonic() - started
            try:
                result = future.result(timeout=8)
                assert result['verdict'] == 'SE', result
                outcome = 'SE'
            except (OSError, http.client.HTTPException) as error:
                outcome = type(error).__name__
    finally:
        manage.operate('start')
        assert manage.digest(manage.ETC / 'deployment.json') == manifest_hash
        # Only artifacts created by this idle-node test may be deleted, via the normal blob API.
        for path in BLOBS.iterdir():
            if path.name not in before:
                assert re.fullmatch('[a-zA-Z0-9_-]+', path.name), path.name
                request(15050, 'DELETE', '/blobs/' + path.name)
    recovered = trial('int main(){return 0;}')
    assert recovered['verdict'] == 'RAN', recovered
    assert recovered['environmentFingerprint'] == baseline['environmentFingerprint']
    assert not groups() and not task_processes()
    assert sorted(p.name for p in (manage.STATE / 'service/work').iterdir()) == ['.lock']
    assert {p.name for p in BLOBS.iterdir()} == before
    print(json.dumps(dict(test=args.case, result='PASS', killedPID=killed, observedPayload=payload,
                          requestOutcome=outcome, cleanupSeconds=round(cleanup_seconds, 3),
                          fingerprint=baseline['environmentFingerprint'], recovered='RAN')), flush=True)


if __name__ == '__main__':
    main()
