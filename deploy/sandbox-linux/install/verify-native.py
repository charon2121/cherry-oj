#!/usr/bin/env python3
"""Bounded native-install smoke: isolated C++, all-thread credentials, limits, cleanup."""
import concurrent.futures
import http.client
import json
import os
from pathlib import Path
import subprocess
import time

GROUP=Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice')
JOBS=GROUP/'cherry-sandbox-helper.service/jobs'
CAPS=('CapInh','CapPrm','CapEff','CapBnd','CapAmb')
LIMITS=dict(cpuNs=1_000_000_000,clockNs=5_000_000_000,memoryBytes=64<<20,
            maxProcesses=64,stdoutMaxBytes=8192,stderrMaxBytes=8192)

def call(method,path,value=None):
    c=http.client.HTTPConnection('127.0.0.1',15050,timeout=25)
    try:
        c.request(method,path,None if value is None else json.dumps(value),{'Content-Type':'application/json'})
        r=c.getresponse();body=r.read(2<<20)
        assert not r.read(1) and r.status==200,(r.status,body[:256])
        return json.loads(body) if body else None
    finally:c.close()

def execute(command,**kw):
    args=dict(command=command,limits=LIMITS.copy());args.update(kw)
    result=call('POST','/run',args)
    assert result['status']=='OK' and not result.get('error'),result
    return result

def status(path):
    return dict(line.split(':',1) for line in path.read_text().splitlines() if ':' in line)

def credentials(values,uid):
    assert values['Uid'].split()==[str(uid)]*4 and values['Gid'].split()==[str(uid)]*4
    assert all(int(values[k],16)==0 for k in CAPS)
    assert values['NoNewPrivs'].strip()=='1' and values['Seccomp'].strip()=='2'

def clean():
    assert not [p for p in JOBS.iterdir() if p.is_dir()]
    assert sorted(p.name for p in Path('/var/lib/cherry-sandbox/service/work').iterdir())==['.lock']
    for proc in Path('/proc').glob('[0-9]*/status'):
        try:values=status(proc)
        except (FileNotFoundError,ProcessLookupError):continue
        assert not set(map(int,values['Uid'].split())) & set(range(61002,61010)),proc

def main():
    assert os.geteuid()==0 and call('GET','/version')['isolation']=='linux'
    manifest=json.loads(Path('/etc/cherry-sandbox/deployment.json').read_text())
    for path,value in manifest['limits'].items(): assert Path(path).read_text().strip()==value,path
    counts={}
    for unit,uid in [('cherry-sandbox',61001),('cherry-sandbox-judge',61010)]:
        pid=subprocess.check_output(['systemctl','show',unit+'.service','-p','MainPID','--value'],text=True).strip()
        rows=list(Path('/proc',pid,'task').glob('*/status'))
        for row in rows:credentials(status(row),uid)
        counts[unit]=len(rows)
    clean()
    compiled=execute(['g++','-O2','main.cpp','-o','program'],
        inputs={'main.cpp':{'text':'#include <unistd.h>\n#include <cstdio>\nint main(){puts("native isolation OK");fflush(stdout);sleep(2);return 0;}\n'}},
        artifacts=['program'],limits=dict(LIMITS,cpuNs=10_000_000_000,clockNs=20_000_000_000,memoryBytes=256<<20))
    ref=compiled['artifacts']['program']
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            future=pool.submit(execute,['program'],inputs={'program':{'ref':ref}})
            deadline=time.monotonic()+3
            while time.monotonic()<deadline:
                groups=[p for p in JOBS.iterdir() if p.is_dir()]
                rows=[]
                try:
                    for group in groups:
                        for pid in (group/'cgroup.procs').read_text().split():
                            rows.extend((pid,status(p)) for p in Path('/proc',pid,'task').glob('*/status'))
                    if {v['Uid'].split()[0] for _,v in rows}=={'61002','61003'} and all(all(int(v[k],16)==0 for k in CAPS) for _,v in rows):break
                except (FileNotFoundError,ProcessLookupError):pass
                time.sleep(.01)
            else:raise AssertionError('fully restricted task not observed')
            for pid,values in rows:credentials(values,int(values['Uid'].split()[0]))
            for pid in {pid for pid,_ in rows}:
                for name in ('mnt','pid','net','ipc','uts','cgroup'):
                    assert os.readlink(f'/proc/{pid}/ns/{name}')!=os.readlink(f'/proc/self/ns/{name}')
                mounts=Path(f'/proc/{pid}/mountinfo').read_text().splitlines()
                for target in ('/','/proc','/.sandbox/launcher'):
                    options=[line.split()[5].split(',') for line in mounts if line.split()[4]==target]
                    assert len(options)==1 and 'ro' in options[0],target
            assert (groups[0]/'memory.swap.max').read_text().strip()=='0'
            result=future.result(timeout=10)
            assert result['stdout']=='native isolation OK\n',result
        print(json.dumps(dict(result='PASS',serviceThreads=counts,taskThreads=len(rows),
             taskUIDs=[61002,61003],capabilities=0,noNewPrivileges=1,namespaces=6,
             readOnlyMounts=3,verifiedLimits=len(manifest['limits']),
             compileCpuNs=compiled['cpuNs'],compileMemoryBytes=compiled['memoryBytes'],
             runCpuNs=result['cpuNs'],runMemoryBytes=result['memoryBytes'])))
    finally:call('DELETE','/blobs/'+ref)
    clean()
    print('No task processes, execution cgroups or workspace files remain.')

if __name__=='__main__':main()
