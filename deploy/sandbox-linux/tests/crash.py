#!/usr/bin/env python3
"""一次 helper SIGKILL：只定位当前测试单元，使用 pidfd 避免 PID 复用误杀。"""
import json
import os
from pathlib import Path
import signal
import sys
import time
from client import call,request

config_path=Path(sys.argv[1]);config=json.loads(config_path.read_text())
jobs=Path(config['JobsDir']);unit=jobs.parent
assert unit.name.startswith('cherry-sandbox-test-work048-')
assert config_path.parent.parent==Path('/var/lib/cherry-sandbox-test')
child=os.fork()
if child==0:
    os.setgroups([]);os.setgid(61001);os.setuid(61001)
    try:
        req,data=request(['sleep','4'])
        call(config['SocketPath'],req,data)
    except (EOFError,ConnectionResetError,BrokenPipeError):os._exit(0)
    except BaseException as err:print(repr(err),flush=True);os._exit(2)
    os._exit(3)
try:
    deadline=time.monotonic()+2
    while time.monotonic()<deadline:
        groups=[g for g in jobs.iterdir() if g.is_dir()]
        ready=False
        for group in groups:
            for pid in (group/'cgroup.procs').read_text().split():
                try:status=Path('/proc',pid,'status').read_text()
                except FileNotFoundError:continue
                if 'Uid:\t61002\t' in status and 'Seccomp:\t2' in status:ready=True
        if ready:break
        time.sleep(.01)
    else:raise AssertionError('payload not observed')
    pids=(unit/'supervisor/cgroup.procs').read_text().split()
    assert len(pids)==1,pids
    pid=int(pids[0]);fd=os.pidfd_open(pid)
    try:
        assert os.readlink(f'/proc/{pid}/exe')==str(config_path.parent/'sandbox-helper')
        signal.pidfd_send_signal(fd,signal.SIGKILL)
    finally:os.close(fd)
finally:
    _,status=os.waitpid(child,0)
    assert status==0,status
for _ in range(200):
    if not unit.exists():break
    time.sleep(.01)
assert not unit.exists(),'systemd did not remove the entire test unit cgroup'
print('helper SIGKILL: client failed closed; complete test unit cgroup removed',flush=True)
