#!/usr/bin/env python3
"""有界的取消、崩溃与恢复测试：经 judge 的 /judge 驱动进程内执行层与 setuid 执行器。
只作用于本次自有的 C++ 夹具与测试单元。

    fault_batch.py <fixture> [capacity]
"""
import concurrent.futures
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

import judge_service
from identity_sample import sample
from judge_program import begin, case, finish, judge, request

BASE = Path(sys.argv[1])
assert BASE.parent == Path('/var/lib/cherry-sandbox-test')
assert BASE.name.startswith('work048-fault-') and not BASE.is_symlink()
PREFIX = 'cherry-sandbox-test-' + BASE.name
SERVICE = PREFIX + '-judge'
CG = Path('/sys/fs/cgroup/system.slice') / (SERVICE + '.service')
JOBS = CG / 'jobs'
DATA = BASE / 'service'
BOXES = DATA / 'boxes'
EXECUTOR = str(BASE / 'sandbox-executor')
JUDGE = str(BASE / 'judge')
PORT = 15051
CAPACITY = len(sys.argv) == 3 and sys.argv[2] == 'capacity'


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


def start_judge():
    judge_service.launch(BASE, SERVICE, parallelism=2 if CAPACITY else 1, cpp=True, seconds=150)


def main_pid(unit):
    p = subprocess.run(['systemctl', 'show', unit, '-p', 'MainPID', '--value'], capture_output=True, text=True)
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


def run_phase(count=1):
    """运行阶段（不是编译阶段）的执行：组里有 init，且用户程序的可执行文件是编译产物 /work/Main。"""
    found = []
    for group in group_paths():
        try:
            init = None
            running = False
            for pid in (group / 'cgroup.procs').read_text().split():
                status = Path('/proc', pid, 'status').read_text()
                if '\nUid:\t6100' in status and 'Seccomp:\t2' in status and os.readlink(f'/proc/{pid}/exe') == '/work/Main':
                    running = True
                elif any(f'\nUid:\t{uid}\t' in status for uid in (61006, 61007)):
                    init = int(pid)
            if init and running:
                found.append((init, group))
        except (FileNotFoundError, ProcessLookupError):
            pass
    return found if len(found) >= count else None


def executor_pid():
    """执行器进程：可执行文件是本夹具的 setuid 执行器，且在 supervisor 叶子（init 同一可执行文件，但在 jobs 下）。"""
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


def verdict(result, name):
    rows = {c['name']: c for c in result.get('caseResults', [])}
    return rows[name]['verdict'] if name in rows else result['verdict']


def identity():
    result = judge(PORT, [case('identity')])
    row = result['caseResults'][0]
    assert row['verdict'] == 'RAN', result
    fields = dict(line.split(':', 1) for line in row['output']['excerpt'].splitlines() if ':' in line)
    assert set(fields['Uid'].split()) <= ({'61002', '61003'} if CAPACITY else {'61002'}), fields
    assert fields['Seccomp'].strip() == '2' and fields['NoNewPrivs'].strip() == '1', fields
    assert int(fields['CapEff'].strip(), 16) == 0, fields
    return result


def drained(workspace=True):
    assert not group_paths(), 'execution cgroup remained'
    assert not task_uids(), 'task remained'
    assert str(BASE) not in Path('/proc/self/mountinfo').read_text()
    if workspace:
        # 每次交付后 box 只剩空的 in/ 与 out/。
        assert not [p for p in BOXES.rglob('*') if not p.is_dir() and p.name != '.lock'], 'box files remained'
    return True


def snapshot():
    drained()
    pid = main_pid(SERVICE)
    assert pid > 0
    return dict(judge=dict(pid=pid, fds=len(list(Path('/proc', str(pid), 'fd').iterdir()))),
                blobs=sorted(p.name for p in (DATA / 'blobs').iterdir() if p.name != '.lock'))


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


def restart_clean():
    """judge 停止后 box 与 blob 中本次留下的文件属于已结束的判题；清掉再启动，下一次执行照常。"""
    for path in (DATA / 'blobs').iterdir():
        if path.name != '.lock':
            path.unlink()
    start_judge()


def fault_cases():
    # 命令不存在：C++ 工具链 rootfs 里没有 python3，执行器在 exec 前失败，judge 判 SE 并带出原因。
    result = judge(PORT, [case('empty')], language='python', source='print(1)\n')
    assert verdict(result, 'empty') == 'SE' and 'errno=2' in json.dumps(result), result
    drained()
    identity()
    report('missing-command', verdict='SE')

    # A 占住唯一的执行名额；B 排队时调用方断开，B 不能再占用名额。
    active = begin(PORT, request([case('sleep')]), timeout=30)
    wait_for(run_phase, 'first payload not observed', 20)
    queued = begin(PORT, request([case('sleep')]), timeout=30)
    time.sleep(.3)
    queued.close()
    assert verdict(finish(active), 'sleep') == 'RAN'
    start = time.monotonic()
    identity()
    elapsed = time.monotonic() - start
    # 这一次本身要编译再运行；若 B 仍占着名额，还要多等 B 的编译与 2s 睡眠。
    assert elapsed < 4.5, ('cancelled request occupied the slot', elapsed)
    drained()
    report('queued-disconnect', nextRequestSeconds=elapsed)

    # 只在观察到运行阶段之后，用 pidfd 杀掉 namespace 里的 PID 1。
    active = begin(PORT, request([case('sleep')]), timeout=30)
    (pid, group), = wait_for(run_phase, 'launcher payload not observed', 20)
    # init 是执行器 clone 出来的（没有再 exec），所以它的可执行文件仍是执行器本身。
    kill_owned(pid, 61006, EXECUTOR, '/' + str(group.relative_to('/sys/fs/cgroup')))
    result = finish(active)
    assert verdict(result, 'sleep') == 'SE', result
    wait_for(lambda: not group_paths(), 'launcher group not reclaimed')
    drained()
    identity()
    report('init-SIGKILL', verdict='SE')

    # judge 崩溃时 systemd 杀掉整个单元（含执行器与执行组）；box 中的文件留到重启时清理。
    active = begin(PORT, request([case('sleep')]), timeout=30)
    wait_for(run_phase, 'judge crash payload not observed', 20)
    kill_owned(main_pid(SERVICE), 61001, JUDGE, '/' + SERVICE + '.service/supervisor')
    assert finish(active, disconnected=True)['verdict'] == 'Disconnected'
    wait_stopped(SERVICE, 'judge cgroup survived crash')
    drained(workspace=False)
    leftovers = sorted(str(p.relative_to(BOXES)) for p in BOXES.rglob('*') if not p.is_dir() and p.name != '.lock')
    restart_clean()
    identity()
    drained()
    report('judge-SIGKILL-restart', beforeRecovery=leftovers, afterRecovery=[])

    # 执行器崩溃：init 的 PDEATHSIG 让整个 namespace 随之终止；执行层得不到回收确认，停止接单，
    # judge 以失败退出。重启后，下一次执行回收空的残留执行组。
    active = begin(PORT, request([case('sleep')]), timeout=30)
    wait_for(run_phase, 'executor crash payload not observed', 20)
    executor = wait_for(executor_pid, 'executor process not observed')
    kill_owned(executor, 0, EXECUTOR, '/' + SERVICE + '.service/supervisor')
    result = finish(active, disconnected=True)
    assert result['verdict'] in ('SE', 'Disconnected'), result
    wait_for(lambda: not task_uids(), 'tasks survived executor crash')
    wait_stopped(SERVICE, 'judge kept running after the execution layer stopped')
    restart_clean()
    identity()
    drained()
    report('executor-SIGKILL-restart', verdict=result['verdict'])

    active = begin(PORT, request([case('sleep')]), timeout=30)
    wait_for(run_phase, 'graceful stop payload not observed', 20)
    start = time.monotonic()
    stop(SERVICE)
    elapsed = time.monotonic() - start
    result = finish(active, disconnected=True)
    # systemd 向单元内全部进程（judge、执行器、用户程序）发 SIGTERM：在途判题可能来得及答完，
    # 也可能以 SE 或断开结束；要求的是停得快、停完之后什么都不留。
    assert elapsed < 12, (elapsed, result)
    # 执行器随单元一起被停止，回收得不到确认；box 中的文件与崩溃时一样留到重启时清理。
    drained(workspace=False)
    restart_clean()
    identity()
    drained()
    report('judge-graceful-stop-restart', seconds=elapsed, verdict=verdict(result, 'sleep'))


def capacity_cases():
    judge_pid = main_pid(SERVICE)

    def isolated(token):
        return judge(PORT, [case(f'isolation {token} {judge_pid}', f'private {token} peers=0 host=hidden\n')])
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        paired = [pool.submit(isolated, token) for token in ('left', 'right')]
        payload_ids, init_ids = set(), set()
        while not all(future.done() for future in paired):
            payload, init = sample(group_paths())
            payload_ids.update(payload)
            init_ids.update(init)
            time.sleep(.005)
        assert payload_ids == {61002, 61003} and init_ids == {61006, 61007}, (payload_ids, init_ids)
        for future in paired:
            assert future.result()['caseResults'][0]['verdict'] == 'AC', future.result()
    escalation = judge(PORT, [case('privilege', 'denied\n')])['caseResults'][0]
    # 降权后 setuid(0) 要么返回失败，要么直接被 seccomp 杀掉（RE）；绝不能成功。
    assert escalation['verdict'] in ('AC', 'RE'), escalation
    drained()
    report('peer-isolation-and-privilege', peerFilesVisible=False, hostPIDVisible=False,
           privilegeVerdict=escalation['verdict'], payloadUIDs=sorted(payload_ids), initUIDs=sorted(init_ids))

    # 两次执行各自只用 60MiB（远低于各自 128MiB 的限额），但 jobs 的总量被压到 96MiB：
    # 祖先 OOM 杀掉的是平台问题，不能判成用户超内存。
    memory = JOBS / 'memory.max'
    oom_group = JOBS / 'memory.oom.group'
    old_memory, old_group = memory.read_text(), oom_group.read_text()

    def events():
        return {name: {key: int(value) for key, value in
                       (line.split() for line in (JOBS / name).read_text().splitlines())}
                for name in ('memory.events.local', 'memory.events')}
    before = events()
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(judge, PORT, [case('hold')], limits=dict(memoryBytes=128 << 20)) for _ in range(2)]
            wait_for(lambda: run_phase(2), 'both holders not running', 30)
            time.sleep(.5)
            oom_group.write_text('1')
            memory.write_text(str(96 << 20))
            results = [future.result() for future in futures]
        after = events()
        assert after['memory.events']['oom_kill'] > before['memory.events']['oom_kill'], (before, after)
        rows = [r['caseResults'][0] for r in results]
        assert all(r['verdict'] == 'SE' for r in rows), results
        assert any('without task-local OOM' in r.get('message', '') for r in rows), results
        wait_for(lambda: not group_paths(), 'aggregate OOM groups remained')
        drained()
        report('aggregate-memory', limitBytes=96 << 20, before=before, after=after,
               verdicts=[r['verdict'] for r in rows], messages=[r.get('message', '') for r in rows])
    finally:
        memory.write_text(old_memory)
        oom_group.write_text(old_group)
    identity()
    task_oom = judge(PORT, [case('memory')])['caseResults'][0]
    assert task_oom['verdict'] == 'MLE', task_oom
    report('task-local-memory', verdict=task_oom['verdict'], memoryBytes=task_oom.get('memoryBytes', 0))
    identity()
    drained()


assert not subprocess.check_output(['systemctl', 'list-units', '--all', '--no-legend', SERVICE + '.service'], text=True).strip()
try:
    start_judge()
    identity()
    before = snapshot()
    report('before', snapshot=before)
    subprocess.run(['systemctl', 'show', SERVICE, '-p', 'MemoryMax', '-p', 'TasksMax',
                    '-p', 'CPUQuotaPerSecUSec', '-p', 'MemorySwapMax'], check=True)
    capacity_cases() if CAPACITY else fault_cases()
    time.sleep(.2)
    after = snapshot()
    assert after['judge']['fds'] <= before['judge']['fds'] + 2, (before, after)
    report('after', snapshot=after)
    print('PASS fault chain', flush=True)
finally:
    stop(SERVICE)
