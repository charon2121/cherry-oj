#!/usr/bin/env python3
"""root 仅查看本次 jobs 中的进程；子客户端先降权再请求。"""
import json
import os
from pathlib import Path
import sys
import time
from client import call,request

config=json.loads(Path(sys.argv[1]).read_text())
assert config['StateDir'].startswith('/run/cherry-sandbox-test-')
jobs=Path(config['JobsDir'])
child=os.fork()
if child==0:
    try:
        os.setgroups([]);os.setgid(61001);os.setuid(61001)
        req,data=request(['probe','sleep'])
        result,_,_,_=call(config['SocketPath'],req,data)
        assert result['ExitCode']==0 and not result['Reason'],result
        os._exit(0)
    except BaseException as err:
        print(repr(err),flush=True);os._exit(1)

def status(path):
    return {k:v.strip() for line in path.read_text().splitlines() if ':' in line for k,v in [line.split(':',1)]}

def snapshot(group, proc_root=Path('/proc')):
    """Read one complete sample; disappearance invalidates all of its facts."""
    rows=[]
    namespaces={}
    mounts={}
    for pid in (group/'cgroup.procs').read_text().split():
        proc=proc_root/pid
        for thread in (proc/'task').iterdir():
            rows.append((pid,thread.name,status(thread/'status')))
        namespaces[pid]={name:os.readlink(proc/'ns'/name) for name in ['mnt','pid','net','ipc','uts','cgroup','user']}
        mounts[pid]=(proc/'mountinfo').read_text().splitlines()
    controls={name:(group/name).read_text().strip() for name in ['memory.swap.max','memory.max','pids.max','cpu.max']}
    return rows,namespaces,mounts,controls

try:
    deadline=time.monotonic()+2
    while time.monotonic()<deadline:
        groups=[p for p in jobs.iterdir() if p.is_dir()]
        if len(groups)==1:
            try:
                rows,namespaces,mounts,controls=snapshot(groups[0])
            except (FileNotFoundError,ProcessLookupError):
                # A thread/process/group can disappear during any read, not only status.
                # Never combine a partial sample with facts from a later attempt.
                time.sleep(.01)
                continue
            ids={v['Uid'].split()[0] for _,_,v in rows}
            if ids=={'61002','61003'} and all(v['NoNewPrivs']=='1' and v['Seccomp']=='2' for _,_,v in rows):break
        time.sleep(.01)
    else:raise AssertionError('did not observe fully restricted init and payload')
    for pid,tid,values in rows:
        uid=values['Uid'].split()[0]
        assert values['Uid'].split()==[uid]*4 and values['Gid'].split()==[uid]*4
        assert all(int(v,16)==0 for k,v in values.items() if k.startswith('Cap'))
    for pid in sorted({pid for pid,_,_ in rows}):
        for name,value in namespaces[pid].items():
            host=os.readlink(f'/proc/self/ns/{name}')
            assert (value==host)==(name=='user'),(name,value,host)
        for target in ['/','/proc','/.sandbox/launcher']:
            matches=[line.split()[5].split(',') for line in mounts[pid] if line.split()[4]==target]
            assert len(matches)==1 and 'ro' in matches[0],(target,matches)
    assert len({tuple(v.items()) for v in namespaces.values()})==1
    assert controls=={'memory.swap.max':'0','memory.max':str(128<<20),'pids.max':'64','cpu.max':'10000 10000'}
    print(json.dumps(dict(threadCount=len(rows),identities=sorted(ids),namespaces=namespaces,controls=controls)),flush=True)
finally:
    _,exit_status=os.waitpid(child,0)
    assert exit_status==0,exit_status
assert not [p for p in jobs.iterdir() if p.is_dir()]
print('thread / namespace / mount / cleanup assertions passed',flush=True)
