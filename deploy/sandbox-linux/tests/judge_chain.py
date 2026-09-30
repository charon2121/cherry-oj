#!/usr/bin/env python3
"""独立测试实例的有界整链验证：judge 的 /judge → 进程内执行层 → setuid 执行器。

    judge_chain.py <fixture> <smoke|repeat|concurrency> <unit>
"""
import concurrent.futures
import json
import os
from pathlib import Path
import statistics
import subprocess
import sys
import time

from judge_program import begin, case, judge, request

base = Path(sys.argv[1]); mode = sys.argv[2]; unit = sys.argv[3]
assert base.parent == Path('/var/lib/cherry-sandbox-test') and base.name.startswith('work048-chain-')
assert unit.startswith('cherry-sandbox-test-work048-chain-')
PORT = 15051
SERVICE = unit + '-judge'
JOBS = Path('/sys/fs/cgroup/system.slice') / (SERVICE + '.service') / 'jobs'


def report(name, **facts):
    print(json.dumps(dict(test=name, **facts)), flush=True)


def verdicts(result):
    """trial 的测试点结果按请求顺序返回；按名字取出，便于逐项断言。"""
    assert result['verdict'] != 'CE', result
    return {c['name']: c for c in result['caseResults']}


def snapshot():
    pid = int(subprocess.check_output(['systemctl', 'show', SERVICE, '--property=MainPID', '--value'], text=True))
    assert pid > 0
    targets = {}
    for fd in Path('/proc', str(pid), 'fd').iterdir():
        try:
            targets[fd.name] = os.readlink(fd)
        except FileNotFoundError:
            pass
    values = dict(line.split(':', 1) for line in Path('/proc', str(pid), 'status').read_text().splitlines() if ':' in line)
    # setuid 执行器要求服务不带 NoNewPrivileges；judge 进程本身仍没有任何有效能力。
    assert set(values['Uid'].split()) == {'61001'}, values
    assert all(int(values[key], 16) == 0 for key in ('CapInh', 'CapPrm', 'CapEff', 'CapAmb')), values
    data = dict(judge=dict(pid=pid, fds=len(targets), uid=61001, capabilities=0))
    data['jobs'] = [p.name for p in JOBS.iterdir() if p.is_dir()]
    boxes = base / 'service/boxes'
    # 每次交付后 box 只剩空的 in/ 与 out/。
    data['work'] = sorted(str(p.relative_to(boxes)) for p in boxes.rglob('*') if not p.is_dir() and p.name != '.lock')
    # flow 在判题结束时删除自己上传的源码与编译产物。
    data['blobs'] = sorted(p.name for p in (base / 'service/blobs').iterdir() if p.name != '.lock')
    payloads = []
    for p in Path('/proc').glob('[0-9]*/status'):
        try:
            lines = p.read_text().splitlines()
        except OSError:
            continue
        uid = next((line for line in lines if line.startswith('Uid:')), None)
        if uid and set(map(int, uid.split()[1:])) & set(range(61002, 61010)):
            payloads.append(str(p))
    data['payloads'] = payloads
    data['mounts'] = sum(base.as_posix() in line for line in Path('/proc/self/mountinfo').read_text().splitlines())
    assert not payloads and not data['jobs'] and data['work'] == [] and data['blobs'] == [], data
    return data


def smoke():
    # 一次编译，多个测试点：每个测试点经执行层单独执行一次，结论由 judge 按执行事实映射。
    result = judge(PORT, [case('echo', 'cherry judge chain\n', rest='cherry judge chain\n'),
                          case('cpu'), case('tree'), case('memory'), case('output'), case('empty'),
                          case('kill'), case('nonzero'), case('threads', 'denied\n'), case('hostfile', 'hidden\n'),
                          case('network'), case('mount'), case('ptrace'),
                          case('symlink'), case('magiclink'), case('hardlink')])
    report('compile', verdict=result['verdict'], cases=len(result['caseResults']))
    got = verdicts(result)
    expected = dict(echo='AC', cpu='TLE', tree='TLE', memory='MLE', output='OLE', empty='RAN',
                    kill='RE', nonzero='RE', threads='AC', hostfile='AC', network='RE', mount='RE', ptrace='RE',
                    symlink='RE', magiclink='RE', hardlink='RE')
    for name, verdict in expected.items():
        facts = got[name]
        assert facts['verdict'] == verdict, (name, facts)
        if name in ('cpu', 'tree'):
            # CPU 超限按整组累计 CPU 判定，而不是墙钟：用量落在预算附近。
            assert 1e9 <= facts['cpuNs'] < 1.2e9, facts
        if name == 'empty':
            assert facts['memoryBytes'] < 16 << 20, facts
        report(name, verdict=facts['verdict'], cpuNs=facts.get('cpuNs', 0), memoryBytes=facts.get('memoryBytes', 0))
    # 主进程退出后 setsid 的后代必须随整组回收：墙钟上限 1s，后代睡 4s，若等它就会 TLE。
    background = verdicts(judge(PORT, [case('background')], limits=dict(clockNs=1_000_000_000)))['background']
    assert background['verdict'] == 'RAN', background
    report('background', verdict=background['verdict'])
    wall = verdicts(judge(PORT, [case('sleep')], limits=dict(clockNs=150_000_000)))['sleep']
    assert wall['verdict'] == 'TLE', wall
    report('wall', verdict=wall['verdict'])
    # 调用方断开真实 HTTP 请求：执行被取消并回收，之后的判题正常。
    c = begin(PORT, request([case('sleep')]), timeout=5)
    deadline = time.monotonic() + 15
    while not any(p.is_dir() for p in JOBS.iterdir()):
        assert time.monotonic() < deadline, 'cancelled run never started'
        time.sleep(.02)
    c.close()
    deadline = time.monotonic() + 10
    while any(p.is_dir() for p in JOBS.iterdir()):
        assert time.monotonic() < deadline, 'cancelled run was not reclaimed'
        time.sleep(.02)
    after = verdicts(judge(PORT, [case('empty')]))['empty']
    assert after['verdict'] == 'RAN', after
    report('after-cancel', verdict=after['verdict'])


def repeat():
    # 1000 次执行：10 次 trial、每次 100 个测试点，编译只做 10 次。
    cpus, peaks = [], []
    start = time.monotonic()
    for batch in range(10):
        result = judge(PORT, [case('empty') for _ in range(100)], limits=dict(clockNs=2_000_000_000))
        rows = result['caseResults']
        assert len(rows) == 100 and all(r['verdict'] == 'RAN' for r in rows), result
        cpus += [r.get('cpuNs', 0) for r in rows]
        peaks += [r.get('memoryBytes', 0) for r in rows]
        print(json.dumps(dict(completed=(batch + 1) * 100, elapsedSeconds=time.monotonic() - start)), flush=True)
    report('1000', cpuMedianNs=statistics.median(cpus), memoryMaxBytes=max(peaks),
           elapsedSeconds=time.monotonic() - start)


def concurrency():
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        start = time.monotonic()
        futures = [pool.submit(judge, PORT, [case('sleep')]) for _ in range(2)]
        peak = 0
        while not all(f.done() for f in futures):
            peak = max(peak, sum(p.is_dir() for p in JOBS.iterdir()))
            time.sleep(.005)
        for f in futures:
            assert verdicts(f.result())['sleep']['verdict'] == 'RAN', f.result()
    elapsed = time.monotonic() - start
    # 各自编译加一次 2s 睡眠；串行至少要 4s 以上的睡眠时间，并行时两组实际重叠。
    assert peak == 2 and elapsed < 7, (peak, elapsed)
    report('parallel2', peakGroups=peak, elapsedSeconds=elapsed)


before = snapshot()
print(json.dumps(dict(snapshot='before', data=before)), flush=True)
{'smoke': smoke, 'repeat': repeat, 'concurrency': concurrency}[mode]()
time.sleep(.2)
after = snapshot()
print(json.dumps(dict(snapshot='after', data=after)), flush=True)
assert before['mounts'] == after['mounts'], (before, after)
assert after['judge']['fds'] <= before['judge']['fds'] + 2, (before, after)
print('PASS ' + mode, flush=True)
