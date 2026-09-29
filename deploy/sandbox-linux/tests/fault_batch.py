#!/usr/bin/env python3
"""有界的 HTTP 取消、崩溃与恢复测试，只作用于本次自有的静态夹具。

    fault_batch.py <fixture> [capacity]
"""
import http.client
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time

import http_service
from identity_sample import sample

BASE = Path(sys.argv[1])
assert BASE.parent == Path('/var/lib/cherry-sandbox-test')
assert BASE.name.startswith('work048-fault-') and not BASE.is_symlink()
PREFIX = 'cherry-sandbox-test-' + BASE.name
HTTP = PREFIX + '-http'
CG = Path('/sys/fs/cgroup/system.slice') / (HTTP + '.service')
JOBS = CG / 'jobs'
SERVICE = BASE / 'service'
BOXES = SERVICE / 'boxes'
EXECUTOR = str(BASE / 'sandbox-executor')
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


def start_http():
    http_service.launch(BASE, HTTP, port=PORT, parallelism=2 if CAPACITY else 1, cpp=False, seconds=120)


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
    assert set(fields['Uid'].split()) <= ({'61002', '61003'} if CAPACITY else {'61002'})
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
                if 'Uid:\t61006\t' in status:
                    init = int(pid)
                if 'Uid:\t61002\t' in status and 'Seccomp:\t2' in status:
                    payload = True
            if init and payload:
                return init, group
        except FileNotFoundError:
            pass
    return None


def executor_pid():
    """执行器进程：可执行文件是本夹具的 setuid 执行器，且不在执行组里（init 同一可执行文件，但在 jobs 下）。"""
    for p in Path('/proc').glob('[0-9]*/exe'):
        try:
            if os.readlink(p) == EXECUTOR and '/supervisor' in (p.parent / 'cgroup').read_text():
                return int(p.parent.name)
        except (FileNotFoundError, ProcessLookupError):
            continue
    return None


def task_uids():
    found = []
    for p in Path('/proc').glob('[0-9]*/status'):
        try:
            uid = next(l for l in p.read_text().splitlines() if l.startswith('Uid:')).split()[1]
        except (FileNotFoundError, ProcessLookupError):
            continue
        if 61002 <= int(uid) <= 61009:
            found.append(int(uid))
    return found


def drained(workspace=True):
    assert not group_paths(), 'execution cgroup remained'
    for p in Path('/proc').glob('[0-9]*/status'):
        try:
            uid = next(l for l in p.read_text().splitlines() if l.startswith('Uid:')).split()[1:]
            assert not set(uid) & set(map(str, range(61002, 61010))), ('task remained', str(p))
        except (FileNotFoundError, ProcessLookupError):
            pass
    assert str(BASE) not in Path('/proc/self/mountinfo').read_text()
    if workspace:
        # 每次交付后 box 只剩空的 in/ 与 out/。
        assert not [p for p in BOXES.rglob('*') if not p.is_dir() and p.name != '.lock'], 'box files remained'
    return True


def snapshot():
    drained()
    result = {}
    pid = main_pid(HTTP)
    assert pid > 0
    result['http'] = dict(pid=pid, fds=len(list(Path('/proc', str(pid), 'fd').iterdir())))
    result['blobs'] = sorted(p.name for p in (SERVICE / 'blobs').iterdir())
    return result


def stopped(unit):
    return not Path('/sys/fs/cgroup/system.slice', unit + '.service').exists() and main_pid(unit) == 0


# systemd 在主进程退出后还要杀掉整棵委派子树并逐层删除 cgroup；负载较重的 CI 机器上
# 3 s 不够。超时时带出残留子树，才能分辨是进程没死、目录没删，还是 systemd 没收尾。
UNIT_STOP_SECONDS = 10


def cgroup_tree(unit):
    root = Path('/sys/fs/cgroup/system.slice') / (unit + '.service')
    if not root.exists():
        return []
    rows = []
    for d in [root] + sorted(p for p in root.rglob('*') if p.is_dir()):
        try:
            events = (d / 'cgroup.events').read_text().splitlines()
            populated = next((line.split()[1] for line in events if line.startswith('populated ')), '?')
            procs = len((d / 'cgroup.procs').read_text().split())
        except OSError as error:
            populated, procs = 'unreadable', str(error)
        rows.append(dict(path=str(d.relative_to(root.parent)), populated=populated, procs=procs))
    return rows


def wait_stopped(unit, message):
    try:
        wait_for(lambda: stopped(unit), message, UNIT_STOP_SECONDS)
    except AssertionError:
        raise AssertionError(message + '; mainPID=' + str(main_pid(unit)) +
                             '; remaining=' + json.dumps(cgroup_tree(unit))) from None


def stop(unit):
    subprocess.run(['systemctl', 'stop', unit], check=False, capture_output=True)
    wait_stopped(unit, 'unit did not stop: ' + unit)


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
            payload, init = sample(group_paths())
            payload_ids.update(payload)
            init_ids.update(init)
            time.sleep(.005)
        assert payload_ids == {61002, 61003} and init_ids == {61006, 61007}, (payload_ids, init_ids)
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


assert not subprocess.check_output(['systemctl', 'list-units', '--all', '--no-legend', HTTP + '.service'], text=True).strip()
try:
    start_http()
    identity()
    before = snapshot()
    report('before', snapshot=before)
    subprocess.run(['systemctl', 'show', HTTP, '-p', 'MemoryMax', '-p', 'TasksMax',
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
        # init 是执行器 clone 出来的（没有再 exec），所以它的可执行文件仍是执行器本身。
        kill_owned(pid, 61006, EXECUTOR, '/' + str(group.relative_to('/sys/fs/cgroup')))
        result = finish(active)
        assert result['status'] not in ('OK', 'TimeLimitExceeded', 'MemoryLimitExceeded'), result
        wait_for(lambda: not group_paths(), 'launcher group not reclaimed')
        drained()
        identity()
        report('init-SIGKILL', status=result['status'], error=result.get('error', ''))

        # HTTP 崩溃时 systemd 杀掉整个单元（含执行器与执行组）；box 中的文件留到重启时清理。
        active = begin()
        wait_for(active_init, 'HTTP crash payload not observed')
        kill_owned(main_pid(HTTP), 61001, str(BASE / 'sandbox'), '/' + HTTP + '.service/supervisor')
        assert finish(active, disconnected=True)['status'] == 'Disconnected'
        wait_stopped(HTTP, 'HTTP cgroup survived crash')
        drained(workspace=False)
        leftovers = sorted(str(p.relative_to(BOXES)) for p in BOXES.rglob('*') if not p.is_dir() and p.name != '.lock')
        start_http()
        identity()
        drained()
        report('HTTP-SIGKILL-restart', beforeRecovery=leftovers, afterRecovery=[])

        # 执行器崩溃：init 的 PDEATHSIG 让整个 namespace 随之终止；HTTP 服务得不到回收确认，
        # 执行池停止接单并报平台错误。重启服务后，下一次执行回收空的残留执行组。
        active = begin()
        wait_for(active_init, 'executor crash payload not observed')
        executor = wait_for(executor_pid, 'executor process not observed')
        kill_owned(executor, 0, EXECUTOR, '/' + HTTP + '.service/supervisor')
        result = finish(active)
        assert result['status'] == 'InternalError', result
        wait_for(lambda: not task_uids(), 'tasks survived executor crash')
        stop(HTTP)
        start_http()
        identity()
        drained()
        report('executor-SIGKILL-restart', status=result['status'], error=result.get('error', ''))

        active = begin()
        wait_for(active_init, 'graceful stop payload not observed')
        start = time.monotonic()
        stop(HTTP)
        elapsed = time.monotonic() - start
        result = finish(active, disconnected=True)
        assert elapsed < 2 and result['status'] != 'OK', (elapsed, result)
        # 停止单元时 systemd 杀掉单元内全部进程并删除委派子树；等组消失后再核对。
        wait_for(lambda: not group_paths(), 'graceful stop did not reclaim the execution group', 6)
        # 执行器随单元一起被停止，回收得不到确认；box 中的文件与崩溃时一样留到重启时清理。
        drained(workspace=False)
        start_http()
        identity()
        drained()
        report('HTTP-graceful-stop-restart', seconds=elapsed, status=result['status'])

    time.sleep(.2)
    after = snapshot()
    assert after['http']['fds'] <= before['http']['fds'] + 2, (before, after)
    report('after', snapshot=after)
    print('PASS fault chain', flush=True)
finally:
    stop(HTTP)
