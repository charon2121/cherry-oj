#!/usr/bin/env python3
"""仅在本次独立、已封顶的 systemd Delegate 单元内运行。"""
import hashlib
import json
import os
from pathlib import Path
import sys

base=Path(sys.argv[1])
unit=sys.argv[2]
assert base.parent == Path('/var/lib/cherry-sandbox-test')
assert unit.startswith('cherry-sandbox-test-')
relative=Path('/proc/self/cgroup').read_text().strip().split('::',1)[1]
cg=Path('/sys/fs/cgroup'+relative)
assert cg.name == unit+'.service'
(cg/'supervisor').mkdir()
(cg/'supervisor/cgroup.procs').write_text(str(os.getpid()))
(cg/'cgroup.subtree_control').write_text('+cpu +memory +pids')
(cg/'jobs').mkdir()
(cg/'jobs/cgroup.subtree_control').write_text('+cpu +memory +pids')
state=Path('/run')/unit
state.mkdir(mode=0o755, exist_ok='recover' in sys.argv[3:])
assert not state.is_symlink() and state.stat().st_uid == 0 and state.stat().st_mode & 0o022 == 0
fixture=base/'cpp-rootfs-v2' if 'cpp' in sys.argv[3:] else base
config=dict(SocketPath=str(state/'helper.sock'),StateDir=str(state),JobsDir=str(cg/'jobs'),RootFS=str(fixture/'rootfs'),ManifestPath=str(fixture/'manifest.json'),ManifestSHA256=hashlib.sha256((fixture/'manifest.json').read_bytes()).hexdigest(),ServiceUID=61001,ServiceGID=61001,PayloadUID=61002,PayloadGID=61002,InitUID=61003,InitGID=61003,Parallelism=2 if 'parallel2' in sys.argv[3:] else 1)
path=base/'helper.json'
path.write_text(json.dumps(config))
command=[str(base/'sandbox-helper'),'--config',str(path)]
if 'trace' in sys.argv[3:]:
    command=['/usr/bin/strace','-ff','-s','128','-o',str(base/'trace'),'-e','trace=process,mount,umount2,pivot_root,prctl,seccomp,setresuid,setresgid,setgroups,capset,read,write']+command
os.execv(command[0],command)
