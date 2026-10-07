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
JOBS=GROUP/'cherry-sandbox-judge.service/jobs'
BOXES=Path('/var/lib/cherry-sandbox/judge/boxes')
CAPS=('CapInh','CapPrm','CapEff','CapBnd','CapAmb')
PAYLOAD,INIT=61002,61006
# judge 自身没有任何有效能力；边界集只为它调用的 setuid 执行器保留 8 项：
# CHOWN、DAC_OVERRIDE、KILL、SETGID、SETUID、SETPCAP、SYS_ADMIN、MKNOD。
EXECUTOR_BOUNDING=(1<<0)|(1<<1)|(1<<5)|(1<<6)|(1<<7)|(1<<8)|(1<<21)|(1<<27)
LIMITS=dict(cpuNs=1_000_000_000,clockNs=5_000_000_000,memoryBytes=64<<20,
            maxProcesses=64,stdoutMaxBytes=8192,stderrMaxBytes=8192)

def trial(source,expected,**limits):
    """经 judge 判一次 C++ trial：编译与运行都走进程内执行层与 setuid 执行器。"""
    body=dict(submissionId='work061-native',problemId='work061-probe',
              languageId='cpp',source=source,mode='trial',
              cases=[dict(input='',expected=expected)],
              limits=dict(cpuNs=1_000_000_000,memoryBytes=64<<20,clockNs=5_000_000_000,**limits))
    c=http.client.HTTPConnection('127.0.0.1',15051,timeout=60)
    try:
        c.request('POST','/judge',json.dumps(body),{'Content-Type':'application/json'})
        r=c.getresponse();data=r.read(2<<20)
        assert not r.read(1) and r.status==200,(r.status,data[:256])
        return json.loads(data)
    finally:c.close()

def status(path):
    return dict(line.split(':',1) for line in path.read_text().splitlines() if ':' in line)

def credentials(values,uid):
    assert values['Uid'].split()==[str(uid)]*4 and values['Gid'].split()==[str(uid)]*4
    assert all(int(values[k],16)==0 for k in CAPS)
    assert values['NoNewPrivs'].strip()=='1' and values['Seccomp'].strip()=='2'

def service_credentials(values):
    # setuid 执行器要求 judge 不能带 NoNewPrivileges；judge 进程本身仍没有任何有效能力。
    assert values['Uid'].split()==['61010']*4 and values['Gid'].split()==['61010']*4
    assert all(int(values[k],16)==0 for k in CAPS if k!='CapBnd')
    assert int(values['CapBnd'],16)==EXECUTOR_BOUNDING,values['CapBnd']

def clean():
    assert not [p for p in JOBS.iterdir() if p.is_dir()]
    # 每次交付之后 box 只剩空的 in/ 与 out/。
    assert sorted(p.name for p in BOXES.iterdir())==['.lock','0']
    assert sorted(p.name for p in (BOXES/'0').iterdir())==['in','out']
    assert not any(any((BOXES/'0'/sub).iterdir()) for sub in ('in','out'))
    for proc in Path('/proc').glob('[0-9]*/status'):
        try:values=status(proc)
        except (FileNotFoundError,ProcessLookupError):continue
        assert not set(map(int,values['Uid'].split())) & set(range(PAYLOAD,INIT+4)),proc

def main():
    assert os.geteuid()==0
    manifest=json.loads(Path('/etc/cherry-sandbox/deployment.json').read_text())
    for path,value in manifest['limits'].items(): assert Path(path).read_text().strip()==value,path
    pid=subprocess.check_output(['systemctl','show','cherry-sandbox-judge.service','-p','MainPID','--value'],text=True).strip()
    rows=list(Path('/proc',pid,'task').glob('*/status'))
    for row in rows:service_credentials(status(row))
    counts={'cherry-sandbox-judge':len(rows)}
    clean()
    source='#include <unistd.h>\n#include <cstdio>\nint main(){puts("native isolation OK");fflush(stdout);sleep(2);return 0;}\n'
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        # 编译与运行都在隔离环境里；观察运行阶段（程序睡 2 秒）的全部任务线程。
        future=pool.submit(trial,source,'native isolation OK\n')
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            groups=[p for p in JOBS.iterdir() if p.is_dir()]
            rows=[]
            try:
                for group in groups:
                    for pid in (group/'cgroup.procs').read_text().split():
                        exe=os.readlink(f'/proc/{pid}/exe')
                        rows.extend((pid,exe,status(p)) for p in Path('/proc',pid,'task').glob('*/status'))
                payload=[r for r in rows if r[1].startswith('/work/')]
                # init 先降权、再装 seccomp：只有全部线程都已完全受限（能力为零、NNP、seccomp 过滤）才算观察到。
                restricted=all(all(int(v[k],16)==0 for k in CAPS) and v['NoNewPrivs'].strip()=='1' and v['Seccomp'].strip()=='2'
                               for _,_,v in rows)
                if payload and {v['Uid'].split()[0] for _,_,v in rows}=={str(PAYLOAD),str(INIT)} and restricted:break
            except (FileNotFoundError,ProcessLookupError):pass
            time.sleep(.01)
        else:raise AssertionError('fully restricted run-phase task not observed')
        for pid,_,values in rows:credentials(values,int(values['Uid'].split()[0]))
        for pid in {pid for pid,_,_ in rows}:
            for name in ('mnt','pid','net','ipc','uts','cgroup'):
                assert os.readlink(f'/proc/{pid}/ns/{name}')!=os.readlink(f'/proc/self/ns/{name}')
            mounts=Path(f'/proc/{pid}/mountinfo').read_text().splitlines()
            for target in ('/','/proc','/dev'):
                options=[line.split()[5].split(',') for line in mounts if line.split()[4]==target]
                assert len(options)==1 and 'ro' in options[0],target
        assert (groups[0]/'memory.swap.max').read_text().strip()=='0'
        result=future.result(timeout=40)
        assert result['verdict']=='AC',result
    print(json.dumps(dict(result='PASS',serviceThreads=counts,taskThreads=len(rows),
         taskUIDs=[PAYLOAD,INIT],capabilities=0,noNewPrivileges=1,namespaces=6,
         readOnlyMounts=3,verifiedLimits=len(manifest['limits']),
         runCpuNs=result['cpuNs'],runMemoryBytes=result['memoryBytes'])))
    clean()
    print('No task processes, execution cgroups or workspace files remain.')

if __name__=='__main__':main()
