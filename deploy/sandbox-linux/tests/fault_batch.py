#!/usr/bin/env python3
"""TASK-098: bounded HTTP cancellation/crash/recovery tests on an owned static fixture."""
import http.client
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time

BASE = Path(sys.argv[1])
assert BASE.parent == Path('/var/lib/cherry-sandbox-test')
assert BASE.name.startswith('work048-fault-') and not BASE.is_symlink()
PREFIX = 'cherry-sandbox-test-' + BASE.name
HELPER = PREFIX + '-helper'
HTTP = PREFIX + '-http'
STATE = Path('/run') / HELPER
CG = Path('/sys/fs/cgroup/system.slice') / (HELPER + '.service')
JOBS = CG / 'jobs'
SERVICE = BASE / 'service'
PORT = 15051
CAPACITY = len(sys.argv) == 3 and sys.argv[2] == 'capacity'
LIMITS = dict(cpuNs=1_000_000_000, clockNs=5_000_000_000,
              memoryBytes=64 << 20, maxProcesses=64,
              stdoutMaxBytes=8192, stderrMaxBytes=8192)


def report(name, **facts):
    print(json.dumps(dict(test=name, **facts)), flush=True)


def wait_for(check, message, seconds=3):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(.01)
    raise AssertionError(message)


def launch(name, props, args):
    cmd = ['systemd-run', '--collect', '--unit=' + name]
    for prop in props:
        cmd += ['-p', prop]
    subprocess.run(cmd + args, check=True)


def start_helper(recover=False):
    launch(HELPER, ['Delegate=yes', 'MemoryMax=768M', 'MemorySwapMax=0',
                   'TasksMax=192', 'CPUQuota=100%', 'RuntimeMaxSec=120',
                   'KillMode=control-group'],
           ['python3', str(BASE / 'bootstrap.py'), str(BASE), HELPER]
           + (['recover'] if recover else []) + (['parallel2'] if CAPACITY else []))
    def ready():
        # A crashed helper leaves a socket inode. Existence alone is not readiness.
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(.2)
            try:
                client.connect(str(STATE / 'helper.sock'))
                return True
            except OSError:
                return False
    wait_for(ready, 'helper not listening after recovery', 10)


def start_http():
    launch(HTTP, ['MemoryMax=256M', 'MemorySwapMax=0', 'TasksMax=96',
                  'CPUQuota=50%', 'RuntimeMaxSec=120', 'KillMode=control-group',
                  'NoNewPrivileges=yes'],
           ['setpriv', '--reuid=61001', '--regid=61001', '--clear-groups',
            '--bounding-set=-all', '--no-new-privs', str(BASE / 'sandbox'),
            '-config', str(BASE / 'sandbox.yaml')])
    def ready():
        try:
            c = http.client.HTTPConnection('127.0.0.1', PORT, timeout=.2)
            c.request('GET', '/version')
            r = c.getresponse()
            return r.status == 200 and json.loads(r.read())['isolation'] == 'linux'
        except (OSError, http.client.HTTPException):
            return False
        finally:
            c.close()
    wait_for(ready, 'HTTP not started', 10)


def main_pid(unit):
    p = subprocess.run(['systemctl', 'show', unit, '-p', 'MainPID', '--value'],
                       capture_output=True, text=True)
    return int(p.stdout.strip() or '0')


def group_paths():
    return [p for p in JOBS.iterdir() if p.is_dir()] if JOBS.exists() else []


def kill_owned(pid, uid, exe, cgroup):
    fd = os.pidfd_open(pid)
    try:
        status = Path('/proc', str(pid), 'status').read_text()
        assert next(l for l in status.splitlines() if l.startswith('Uid:')).split()[1:] == [str(uid)] * 4
        assert os.readlink('/proc/' + str(pid) + '/exe') == exe
        assert cgroup in Path('/proc', str(pid), 'cgroup').read_text()
        signal.pidfd_send_signal(fd, signal.SIGKILL)
    finally:
        os.close(fd)


def begin(mode='sleep', command=None, inputs=None):
    c = http.client.HTTPConnection('127.0.0.1', PORT, timeout=8)
    body = dict(command=command if command is not None else ['probe', mode], limits=LIMITS,
                inputs=inputs if inputs is not None else {'seed': {'text': 'owned fault-test input'}})
    c.request('POST', '/run', json.dumps(body), {'Content-Type': 'application/json'})
    return c


def finish(c, disconnected=False):
    try:
        response = c.getresponse()
        data = response.read(2 << 20)
        assert response.status == 200, (response.status, data[:256])
        return json.loads(data)
    except (OSError, http.client.HTTPException):
        if disconnected:
            return {'status': 'Disconnected'}
        raise
    finally:
        c.close()


def identity():
    result = finish(begin('identity'))
    assert result['status'] == 'OK' and not result.get('error'), result
    fields = json.loads(result['stdout'])['status']
    assert set(fields['Uid'].split()) <= ({'61002', '61004'} if CAPACITY else {'61002'})
    assert fields['Seccomp'] == '2' and fields['NoNewPrivs'] == '1'
    return result


def active_init():
    for group in group_paths():
        try:
            pids = (group / 'cgroup.procs').read_text().split()
            init = None
            payload = False
            for pid in pids:
                status = Path('/proc', pid, 'status').read_text()
                if 'Uid:\t61003\t' in status:
                    init = int(pid)
                if 'Uid:\t61002\t' in status and 'Seccomp:\t2' in status:
                    payload = True
            if init and payload:
                return init, group
        except FileNotFoundError:
            pass
    return None


def drained(workspace=True):
    assert not group_paths(), 'execution cgroup remained'
    for p in Path('/proc').glob('[0-9]*/status'):
        try:
            uid = next(l for l in p.read_text().splitlines() if l.startswith('Uid:')).split()[1:]
            assert not set(uid) & set(map(str, range(61002, 61010))), ('payload remained', str(p))
        except (FileNotFoundError, ProcessLookupError):
            pass
    assert str(BASE) not in Path('/proc/self/mountinfo').read_text()
    if workspace:
        assert sorted(p.name for p in (SERVICE / 'work').iterdir()) == ['.lock']
    return True


def snapshot():
    drained()
    result = {}
    for unit in (HELPER, HTTP):
        pid = main_pid(unit)
        assert pid > 0
        result[unit.rsplit('-', 1)[-1]] = dict(pid=pid, fds=len(list(Path('/proc', str(pid), 'fd').iterdir())))
    result['work'] = sorted(p.name for p in (SERVICE / 'work').iterdir())
    result['blobs'] = sorted(p.name for p in (SERVICE / 'blobs').iterdir())
    return result


def stopped(unit):
    return not Path('/sys/fs/cgroup/system.slice', unit + '.service').exists() and main_pid(unit) == 0


def stop(unit):
    subprocess.run(['systemctl', 'stop', unit], check=False, capture_output=True)
    wait_for(lambda: stopped(unit), 'unit did not stop: ' + unit)


def capacity_cases():
    import concurrent.futures
    import socket

    connections = []
    try:
        # Two running + four queued; no seventh request may enter the pool.
        connections = [begin() for _ in range(6)]
        wait_for(lambda: len(group_paths()) == 2, 'parallelism did not reach two')
        time.sleep(.2)
        extra = begin()
        response = extra.getresponse()
        body = response.read(4096)
        extra.close()
        assert response.status == 503 and b'queue is full' in body, (response.status, body)
        assert len(group_paths()) == 2
        report('pool-saturation', admitted=6, groups=2, rejectedHTTP=503)
    finally:
        for c in connections:
            c.close()
    wait_for(lambda: not group_paths(), 'cancelled saturated group remained')
    time.sleep(.1)
    drained()
    identity()

    def isolated(token):
        return finish(begin(command=['probe', 'isolation', token, str(main_pid(HTTP))],
                            inputs={'own': {'text': token}}))
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        paired = [pool.submit(isolated, token) for token in ('left', 'right')]
        payload_ids, init_ids = set(), set()
        while not all(future.done() for future in paired):
            for group in group_paths():
                try:
                    for pid in (group / 'cgroup.procs').read_text().split():
                        status = Path('/proc', pid, 'status').read_text()
                        uid = int(next(line for line in status.splitlines() if line.startswith('Uid:')).split()[1])
                        if uid in (61002, 61004): payload_ids.add(uid)
                        if uid in (61003, 61005): init_ids.add(uid)
                except FileNotFoundError:
                    pass
            time.sleep(.005)
        assert payload_ids == {61002, 61004} and init_ids == {61003, 61005}, (payload_ids, init_ids)
        for token, future in zip(('left', 'right'), paired):
            result = future.result()
            assert result['status'] == 'OK' and result['stdout'] == 'private ' + token + '\n', result
    escalation = finish(begin('privilege'))
    assert ((escalation['status'] == 'Signalled' and escalation['signal'] == 31)
            or (escalation['status'] == 'OK' and escalation['stdout'] == 'denied\n')), escalation
    drained()
    report('peer-isolation-and-privilege', peerFilesVisible=False, hostPIDVisible=False,
           privilegeStatus=escalation['status'], privilegeSignal=escalation['signal'],
           payloadUIDs=sorted(payload_ids), initUIDs=sorted(init_ids))

    # Occupy all ten handler slots with bounded incomplete bodies, then release them.
    slow = []
    try:
        for _ in range(10):
            sock = socket.create_connection(('127.0.0.1', PORT), timeout=2)
            slow.append(sock)
            sock.sendall(b'POST /run HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 64\r\n\r\n')
        time.sleep(.2)
        c = http.client.HTTPConnection('127.0.0.1', PORT, timeout=2)
        c.request('GET', '/version')
        response = c.getresponse()
        body = response.read(4096)
        c.close()
        assert response.status == 503 and b'error' in body, (response.status, body)
        assert not group_paths()
        report('handler-saturation', handlers=10, rejectedHTTP=503)
    finally:
        for sock in slow:
            sock.close()
    time.sleep(.2)
    identity()
    drained()

    # Bound the sum of both executions without lowering their individual 128MiB limit.
    memory = JOBS / 'memory.max'
    oom_group = JOBS / 'memory.oom.group'
    old_memory, old_group = memory.read_text(), oom_group.read_text()
    def events():
        return {name: {key: int(value) for key, value in
                       (line.split() for line in (JOBS / name).read_text().splitlines())}
                for name in ('memory.events.local', 'memory.events')}
    before = events()
    try:
        memory.write_text(str(96 << 20))
        oom_group.write_text('1')
        assert memory.read_text().strip() == str(96 << 20)
        def allocate():
            c = http.client.HTTPConnection('127.0.0.1', PORT, timeout=8)
            c.request('POST', '/run', json.dumps(dict(command=['probe', 'memory'], limits=dict(LIMITS, memoryBytes=128 << 20))), {'Content-Type': 'application/json'})
            return finish(c)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(allocate) for _ in range(2)]
            results = [future.result() for future in futures]
        after = events()
        assert after['memory.events.local']['oom_group_kill'] > before['memory.events.local']['oom_group_kill'], (before, after)
        assert after['memory.events']['oom_kill'] > before['memory.events']['oom_kill'], (before, after)
        assert all(r['status'] == 'InternalError' and 'without task-local OOM' in r.get('error', '') for r in results), results
        wait_for(lambda: not group_paths(), 'aggregate OOM groups remained')
        drained()
        report('aggregate-memory', limitBytes=96 << 20, before=before, after=after,
               statuses=[r['status'] for r in results], errors=[r.get('error', '') for r in results])
    finally:
        memory.write_text(old_memory)
        oom_group.write_text(old_group)
    identity()
    c = begin('memory')
    task_oom = finish(c)
    assert task_oom['status'] == 'MemoryLimitExceeded' and not task_oom.get('error'), task_oom
    report('task-local-memory', status=task_oom['status'], memoryBytes=task_oom['memoryBytes'])
    identity()
    drained()


for name in (HELPER, HTTP):
    assert not subprocess.check_output(['systemctl', 'list-units', '--all', '--no-legend', name + '.service'], text=True).strip()
SERVICE.mkdir(mode=0o700)
os.chown(SERVICE, 61001, 61001)
config = dict(logging=dict(path=str(SERVICE / 'logs')), sandbox=dict(
    backend='linux', httpAddr='127.0.0.1:' + str(PORT),
    helperSocket=str(STATE / 'helper.sock'), workspaceRoot=str(SERVICE / 'work'),
    parallelism=2 if CAPACITY else 1, queueSize=4, maxRequestBytes=2 << 20,
    store=dict(root=str(SERVICE / 'blobs'), maxBlobBytes=64 << 20,
               maxTotalBytes=256 << 20, maxEntries=128, retention='1h')))
(BASE / 'sandbox.yaml').write_text(json.dumps(config))
try:
    start_helper()
    start_http()
    identity()
    before = snapshot()
    report('before', snapshot=before)
    for unit in (HELPER, HTTP):
        subprocess.run(['systemctl', 'show', unit, '-p', 'MemoryMax', '-p', 'TasksMax',
                        '-p', 'CPUQuotaPerSecUSec', '-p', 'MemorySwapMax'], check=True)

    if CAPACITY:
        capacity_cases()
    else:
        for name, command, inputs in (
                ('missing-command', ['missing-command'], {}),
                ('invalid-executable', ['bad'], {'bad': {'text': 'not an executable'}})):
            result = finish(begin(command=command, inputs=inputs))
            assert result['status'] == 'InternalError' and result.get('error'), result
            drained()
            identity()
            report(name, status=result['status'], error=result['error'])

        # A occupies the only slot; B closes while A is still running.
        active = begin()
        _, original = wait_for(active_init, 'first payload not observed')
        queued = begin()
        time.sleep(.2)
        assert group_paths() == [original]
        queued.close()
        result = finish(active)
        assert result['status'] == 'OK', result
        start = time.monotonic()
        identity()
        elapsed = time.monotonic() - start
        assert elapsed < 1, ('cancelled request occupied slot', elapsed)
        drained()
        report('queued-disconnect', nextRequestSeconds=elapsed)

        # Kill namespace PID 1 only after payload is observed, using its pidfd.
        active = begin()
        pid, group = wait_for(active_init, 'launcher payload not observed')
        kill_owned(pid, 61003, str(BASE / 'sandbox-helper'), '/' + str(group.relative_to('/sys/fs/cgroup')))
        result = finish(active)
        assert result['status'] not in ('OK', 'TimeLimitExceeded', 'MemoryLimitExceeded'), result
        wait_for(lambda: not group_paths(), 'launcher group not reclaimed')
        drained()
        identity()
        report('init-SIGKILL', status=result['status'], error=result.get('error', ''))

        # HTTP crash leaves service staging files; helper must still reap its execution.
        active = begin()
        wait_for(active_init, 'HTTP crash payload not observed')
        kill_owned(main_pid(HTTP), 61001, str(BASE / 'sandbox'), '/' + HTTP + '.service')
        assert finish(active, disconnected=True)['status'] == 'Disconnected'
        wait_for(lambda: stopped(HTTP), 'HTTP cgroup survived crash')
        wait_for(lambda: not group_paths(), 'HTTP crash did not cancel helper')
        drained(workspace=False)
        leftovers = sorted(p.name for p in (SERVICE / 'work').iterdir())
        start_http()
        identity()
        drained()
        report('HTTP-SIGKILL-restart', beforeRecovery=leftovers, afterRecovery=['.lock'])

        # Helper crash: systemd kills the entire delegated group; restart recovers socket/state.
        active = begin()
        wait_for(active_init, 'helper crash payload not observed')
        kill_owned(main_pid(HELPER), 0, str(BASE / 'sandbox-helper'), '/' + HELPER + '.service/supervisor')
        result = finish(active)
        assert result['status'] == 'InternalError', result
        wait_for(lambda: stopped(HELPER), 'helper cgroup survived crash')
        drained()
        state_before = sorted(p.name for p in STATE.iterdir())
        start_helper(recover=True)
        # Pool may fail closed after transport failure: restart its owning HTTP service explicitly.
        stop(HTTP)
        start_http()
        identity()
        drained()
        report('helper-SIGKILL-restart', status=result['status'], stateBeforeRecovery=state_before)

        active = begin()
        wait_for(active_init, 'graceful stop payload not observed')
        start = time.monotonic()
        stop(HTTP)
        elapsed = time.monotonic() - start
        result = finish(active, disconnected=True)
        assert elapsed < 2 and result['status'] != 'OK', (elapsed, result)
        drained()
        start_http()
        identity()
        report('HTTP-graceful-stop-restart', seconds=elapsed, status=result['status'])

    time.sleep(.2)
    after = snapshot()
    for key in ('helper', 'http'):
        assert after[key]['fds'] <= before[key]['fds'] + 2, (before, after)
    report('after', snapshot=after)
    print('PASS fault chain', flush=True)
finally:
    stop(HTTP)
    stop(HELPER)
