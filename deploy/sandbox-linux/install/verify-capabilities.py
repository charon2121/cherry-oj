#!/usr/bin/env python3
"""逐项证明 setuid 执行器需要的 8 项能力都必不可少，且只靠这 8 项就能完成完整执行。

能力边界集设在 judge 服务单元上：judge 在本进程内调用的执行器以 setuid 获得的能力不会超出它。
"""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import time

sys.path.insert(0, '/opt/cherry-oj/operations')
sys.path.insert(0, '/opt/cherry-oj/etc')
import manage
import health

CAPS = ('CAP_SYS_ADMIN', 'CAP_SETUID', 'CAP_SETGID', 'CAP_SETPCAP',
        'CAP_CHOWN', 'CAP_DAC_OVERRIDE', 'CAP_MKNOD', 'CAP_KILL')
UNIT = 'cherry-sandbox-judge.service'
DIRECTORY = Path('/run/systemd/system/cherry-sandbox-judge.service.d')
DROPIN = DIRECTORY / '90-work048-capability-test.conf'


def stopped():
    manage.run('systemctl', 'stop', *reversed(manage.UNITS[1:]))


def configure(caps):
    content = '[Service]\nCapabilityBoundingSet=\nCapabilityBoundingSet=' + ' '.join(caps) + '\n'
    DROPIN.write_text(content)
    DROPIN.chmod(0o644)
    manage.run('systemctl', 'daemon-reload')
    return content


def sandbox_state():
    return manage.run('systemctl', 'show', UNIT, '-p', 'ActiveState', '--value')


def start_observation():
    raw = manage.run('systemctl', 'show', UNIT, '-p', 'InvocationID',
                     '-p', 'ExecMainStartTimestampMonotonic')
    values = dict(line.split('=', 1) for line in raw.splitlines())
    return dict(invocationID=values.get('InvocationID', ''),
                mainStartedNs=int(values.get('ExecMainStartTimestampMonotonic', '0')) * 1000)


def require_new_start(before, after):
    # Result can retain an earlier exit-code when start limiting prevents another process.
    # A refusal counts only when THIS capability profile actually started a new main process.
    assert re.fullmatch('[0-9a-f]{32}', after['invocationID']), after
    assert after['invocationID'] != before['invocationID'], (before, after)
    assert after['mainStartedNs'] > before['mainStartedNs'], (before, after)


def reset_failure():
    # Inactive successful units may already be garbage-collected by systemd; reset-failed
    # then returns "not loaded". They have no retained failed attempt to clear.
    state = sandbox_state()
    assert state in ('inactive', 'failed'), state
    if state == 'failed':
        manage.run('systemctl', 'reset-failed', UNIT)


def probe():
    before = start_observation()
    # These are independent deliberate-failure fixtures, not automatic retries of a failed run.
    # Keep the deployed rate-limit configuration intact; clear only this owned unit's history.
    reset_failure()
    # 启动时要核对整棵 rootfs 并跑一次真实执行作为自检，留足时间。
    subprocess.run(['systemctl', 'start', '--no-block', UNIT], capture_output=True, timeout=10)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if sandbox_state() == 'failed':
            observation = start_observation()
            require_new_start(before, observation)
            return False, observation
        if health.ready('judge'):
            observation = start_observation()
            require_new_start(before, observation)
            return True, observation
        time.sleep(.05)
    raise AssertionError('sandbox did not reach a definitive state')


def main():
    manage.owned()
    assert not DIRECTORY.exists() and not DIRECTORY.is_symlink()
    spec = importlib.util.spec_from_file_location('native', '/opt/cherry-oj/operations/verify-native.py')
    native = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native)
    native.clean()
    manage.operate('stop')
    DIRECTORY.mkdir(mode=0o755)
    content = None
    try:
        content = configure(CAPS)
        ready, observation = probe()
        assert ready, 'reduced set failed startup'
        # 完整一次判题：源码作为输入进入隔离环境编译，产物交回执行层再作为输入运行；
        # 程序在工作区写文件、留下一个睡眠的后代——墙钟远小于后代的睡眠，说明后代被整组回收。
        source = ('#include <unistd.h>\n#include <fcntl.h>\n#include <cstdio>\nint main(){int f=open("proof",O_CREAT|O_WRONLY,0600);'
                  'if(f<0)return 2;write(f,"cap-test",8);close(f);if(!fork()){sleep(10);return 0;}puts("cap-test");return 0;}\n')
        started = time.monotonic()
        result = native.trial(source, 'cap-test\n')
        assert result['verdict'] == 'AC', result
        assert time.monotonic() - started < 9, 'a sleeping descendant held the execution open'
        native.clean()
        print(json.dumps(dict(test='eight-capability-set', result='PASS',
                              compiledArtifact=True, workspaceWrite=True, descendantsReaped=True,
                              **observation)), flush=True)
        stopped()
        for excluded in CAPS:
            content = configure(tuple(cap for cap in CAPS if cap != excluded))
            ready, observation = probe()
            assert not ready, 'capability may be redundant: ' + excluded
            result = manage.run('systemctl', 'show', UNIT, '-p', 'Result', '--value')
            assert result != 'start-limit-hit', result
            print(json.dumps(dict(test='remove-capability', removed=excluded, startup='REFUSED', **observation)), flush=True)
            stopped()
    finally:
        stopped()
        if DROPIN.exists():
            assert DROPIN.read_text() == content
            DROPIN.unlink()
        DIRECTORY.rmdir()
        manage.run('systemctl', 'daemon-reload')
        manage.owned()
        reset_failure()
        manage.operate('start')
    print('Original deployment restored; no judge registration under temporary policy.', flush=True)


if __name__ == '__main__':
    main()
