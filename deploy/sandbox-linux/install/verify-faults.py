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
JOBS = GROUP / 'cherry-sandbox.service/jobs'
EXECUTOR = (manage.STATE / 'current/libexec/sandbox').resolve()
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
            if 61002 <= int(uid) <= 61009:  # payload 61002-61005，init 61006-61009
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


def executor_pid():
    """正在执行的 setuid 执行器：root 身份，位于 sandbox 服务的 supervisor 叶子。"""
    for path in Path('/proc').glob('[0-9]*/exe'):
        try:
            if Path(os.readlink(path)) == EXECUTOR:
                return int(path.parent.name)
        except (FileNotFoundError, ProcessLookupError):
            continue
    return None


def kill_process(pid, exe, uid, cgroup):
    assert pid > 1
    fd = os.pidfd_open(pid)
    try:
        proc = Path('/proc') / str(pid)
        assert Path(os.readlink(proc / 'exe')) == exe
        values = next(line.split()[1:] for line in (proc / 'status').read_text().splitlines() if line.startswith('Uid:'))
        assert values == [str(uid)] * 4
        actual = (proc / 'cgroup').read_text().strip().removeprefix('0::')
        assert actual == str(cgroup).removeprefix('/sys/fs/cgroup'), actual
        signal.pidfd_send_signal(fd, signal.SIGKILL)
        return pid
    finally:
        os.close(fd)


def kill_target(case):
    if case == 'executor':
        return kill_process(wait_for(executor_pid, 'executor not observed'), EXECUTOR, 0,
                            GROUP / 'cherry-sandbox.service/supervisor')
    unit, exe, uid, leaf = {
        'sandbox': ('cherry-sandbox.service', 'sandbox', 61001, '/supervisor'),
        'judge': ('cherry-sandbox-judge.service', 'judge', 61010, ''),
    }[case]
    pid = int(manage.run('systemctl', 'show', unit, '-p', 'MainPID', '--value'))
    return kill_process(pid, (manage.STATE / 'current/bin' / exe).resolve(), uid, str(GROUP / unit) + leaf)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case', choices=('judge', 'sandbox', 'executor'), required=True)
    args = parser.parse_args()
    manage.owned()
    assert not groups() and not task_processes(), 'node must be idle'
    baseline = trial('int main(){return 0;}')
    assert baseline['verdict'] == 'RAN', baseline
    before = {p.name for p in BLOBS.iterdir()}
    manifest_hash = manage.digest(manage.ETC / 'deployment.json')
    started = time.monotonic()
    outcome = None
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            # Background descendant extends beyond the observation window, so natural exit cannot fake cleanup.
            future = executor.submit(trial, '#include <unistd.h>\nint main(){fork();sleep(15);return 0;}\n')
            payload = wait_for(running_payload, 'payload did not reach execution', 10)
            killed = kill_target(args.case)
            # 执行器被杀时，init 的 PDEATHSIG 让整个 namespace 随之终止；空的执行组留到下一次
            # 使用这个 box 时由执行器回收，所以这里只要求任务进程消失。
            wait_for(lambda: not task_processes() and (args.case == 'executor' or not groups()),
                     'tasks survived service crash')
            cleanup_seconds = time.monotonic() - started
            try:
                result = future.result(timeout=8)
                assert result['verdict'] == 'SE', result
                outcome = 'SE'
            except (OSError, http.client.HTTPException) as error:
                outcome = type(error).__name__
    finally:
        # 执行器丢失回收确认后，sandbox 的执行池停止接单，只能重启服务恢复。
        if args.case == 'executor':
            manage.run('systemctl', 'stop', *reversed(manage.UNITS[1:]))
        manage.operate('start')
        assert manage.digest(manage.ETC / 'deployment.json') == manifest_hash
        # Only artifacts created by this idle-node test may be deleted, via the normal blob API.
        for path in BLOBS.iterdir():
            if path.name not in before:
                assert re.fullmatch('[a-zA-Z0-9_-]+', path.name), path.name
                request(15050, 'DELETE', '/blobs/' + path.name)
    recovered = trial('int main(){return 0;}')
    assert recovered['verdict'] == 'RAN', recovered
    assert not groups() and not task_processes()
    boxes = manage.STATE / 'service/boxes'
    assert sorted(p.name for p in boxes.iterdir()) == ['.lock', '0']
    assert not any(any((boxes / '0' / sub).iterdir()) for sub in ('in', 'out'))
    assert {p.name for p in BLOBS.iterdir()} == before
    print(json.dumps(dict(test=args.case, result='PASS', killedPID=killed, observedPayload=payload,
                          requestOutcome=outcome, cleanupSeconds=round(cleanup_seconds, 3),
                          deployment=manifest_hash, recovered='RAN')), flush=True)


if __name__ == '__main__':
    main()
