#!/usr/bin/env python3
"""独立HTTP测试实例的有界整链验证；不调用helper私有协议。"""
import concurrent.futures
import hashlib
import http.client
import json
import os
from pathlib import Path
import statistics
import sys
import time

base=Path(sys.argv[1]); mode=sys.argv[2]; unit=sys.argv[3]
assert base.parent==Path('/var/lib/cherry-sandbox-test') and base.name.startswith('work048-chain-')
assert unit.startswith('cherry-sandbox-test-work048-chain-')
port=15050
refs=[]
limits=dict(cpuNs=1_000_000_000,clockNs=5_000_000_000,memoryBytes=64<<20,maxProcesses=64,stdoutMaxBytes=8192,stderrMaxBytes=8192)

def request(method,path,value=None):
    c=http.client.HTTPConnection('127.0.0.1',port,timeout=10)
    try:
        data=json.dumps(value).encode() if value is not None else None
        c.request(method,path,data,{'Content-Type':'application/json'})
        r=c.getresponse(); body=r.read(2<<20)
        assert not r.read(1),'response too large'
        assert r.status==200,(r.status,body[:512])
        return json.loads(body)
    finally:c.close()

def run(command,**kwargs):
    params=dict(command=command,limits=limits.copy());params.update(kwargs)
    return request('POST','/run',params)

def expect(name,result,status):
    assert result['status']==status,(name,result)
    assert not result.get('error'),(name,result)
    print(json.dumps(dict(test=name,**{k:v for k,v in result.items() if k not in ('stdout','stderr','outputs','artifacts')},stdoutBytes=len(result['stdout'].encode()),stderrBytes=len(result['stderr'].encode()))),flush=True)

def snapshot():
    import subprocess
    data={}
    for suffix in ('helper','http'):
        name=unit+'-'+suffix+'.service'
        pid=int(subprocess.check_output(['systemctl','show',name,'--property=MainPID','--value'],text=True))
        assert pid>0
        targets={}
        for fd in Path('/proc',str(pid),'fd').iterdir():
            try:targets[fd.name]=os.readlink(fd)
            except FileNotFoundError:pass
        data[suffix]=dict(pid=pid,fds=len(targets),targets=targets)
        if suffix=='http':
            values=dict(line.split(':',1) for line in Path('/proc',str(pid),'status').read_text().splitlines() if ':' in line)
            assert set(values['Uid'].split())=={'61001'} and values['NoNewPrivs'].strip()=='1',values
            assert all(int(values[key],16)==0 for key in ('CapInh','CapPrm','CapEff','CapBnd','CapAmb')),values
            data[suffix]['uid']=61001;data[suffix]['capabilities']=0;data[suffix]['noNewPrivileges']=True
    cg=Path('/sys/fs/cgroup/system.slice')/(unit+'-helper.service')/'jobs'
    data['jobs']=[p.name for p in cg.iterdir() if p.is_dir()]
    data['work']=[p.name for p in (base/'service/work').iterdir()]
    data['blobs']=[p.name for p in (base/'service/blobs').iterdir()]
    payloads=[]
    for p in Path('/proc').glob('[0-9]*/status'):
        try: lines=p.read_text().splitlines()
        except OSError:continue
        uid=next((line for line in lines if line.startswith('Uid:')),None)
        if uid and set(map(int,uid.split()[1:])) & set(range(61002,61010)):payloads.append(str(p))
    data['payloads']=payloads
    assert not payloads,data
    data['mounts']=sum(base.as_posix() in line for line in Path('/proc/self/mountinfo').read_text().splitlines())
    assert not data['jobs'] and data['work']==['.lock'],data
    return data

source=r'''
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/mount.h>
#include <sys/ptrace.h>
#include <fcntl.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <pthread.h>
static void* sleeper(void*) {sleep(4);return nullptr;}
int main(int argc,char**argv){
 const char*m=argc>1?argv[1]:"empty";
 if(!strcmp(m,"echo")){char b[4096];ssize_t n;while((n=read(0,b,sizeof b))>0)write(1,b,n);return 0;}
 if(!strcmp(m,"cpu")){for(;;){} }
 if(!strcmp(m,"tree")){fork();for(;;){} }
 if(!strcmp(m,"memory")){for(;;){char*p=(char*)malloc(1<<20);if(!p)return 5;memset(p,1,1<<20);asm volatile(""::"r"(p):"memory");}}
 if(!strcmp(m,"output")){char b[4096];memset(b,'x',sizeof b);for(;;)write(1,b,sizeof b);}
 if(!strcmp(m,"kill")){raise(SIGKILL);}
 if(!strcmp(m,"nonzero")){return 7;}
 if(!strcmp(m,"sleep")){sleep(2);}
 if(!strcmp(m,"background")){if(!fork()){setsid();sleep(4);}return 0;}
 if(!strcmp(m,"threads")){pthread_t t[80];int n=0;for(;n<80;n++){if(pthread_create(&t[n],nullptr,sleeper,nullptr)){printf("denied %d\n",n);return 0;}}return 8;}
 if(!strcmp(m,"network")){socket(AF_INET,SOCK_STREAM,0);return 8;}
 if(!strcmp(m,"mount")){mount("none","/tmp","tmpfs",0,nullptr);return 8;}
 if(!strcmp(m,"ptrace")){ptrace(PTRACE_TRACEME,0,0,0);return 8;}
 if(!strcmp(m,"hostfile")){return access("/etc/hostname",F_OK)==0?8:0;}
 if(!strcmp(m,"symlink")){symlink("/etc/passwd","out");}
 if(!strcmp(m,"magiclink")){symlink("/proc/self/fd/0","out");}
 if(!strcmp(m,"hardlink")){int f=open("first",O_CREAT|O_WRONLY,0600);write(f,"x",1);close(f);link("first","out");}
 return 0;
}
'''

try:
    assert request('GET','/version')['isolation']=='linux'
    compiled=run(['g++','-O2','-pthread','main.cpp','-o','program'],inputs={'main.cpp':{'text':source}},artifacts=['program'],limits=dict(limits,cpuNs=3_000_000_000,memoryBytes=128<<20))
    expect('compile',compiled,'OK')
    ref=compiled['artifacts']['program'];refs.append(ref)
    def program(name='empty',**kw):return run(['program',name],inputs={'program':{'ref':ref}},**kw)
    before=snapshot();print(json.dumps(dict(snapshot='before',data=before)),flush=True)
    if mode=='smoke':
        res=program('echo',stdin={'text':'cherry HTTP chain\n'});expect('echo',res,'OK');assert res['stdout']=='cherry HTTP chain\n'
        for name,status in [('cpu','TimeLimitExceeded'),('tree','TimeLimitExceeded'),('memory','MemoryLimitExceeded'),('output','OutputLimitExceeded'),('empty','OK'),('kill','Signalled'),('nonzero','NonzeroExitStatus'),('background','OK'),('threads','OK'),('hostfile','OK'),('network','Signalled'),('mount','Signalled'),('ptrace','Signalled')]:
            res=program(name);expect(name,res,status)
            if name in ('cpu','tree'):assert 1e9<=res['cpuNs']<1.2e9 and res['clockNs']<2e9,res
            if name=='kill':assert res['signal']==9,res
            if name=='background':assert res['clockNs']<1e9,res
            if name=='empty':assert res['memoryBytes']<16<<20,res
            if name=='threads':assert res['stdout'].startswith('denied '),res
        expect('wall',program('sleep',limits=dict(limits,clockNs=150_000_000)),'TimeLimitExceeded')
        for name in ('symlink','magiclink','hardlink'):
            res=program(name,artifacts=['out'])
            assert res['status']=='Signalled' and res['signal']==31 and not res.get('artifacts'),(name,res)
            print(json.dumps(dict(test=name,status=res['status'],error=res.get('error',''))),flush=True)
        for key,status in [('cpuNs','TimeLimitExceeded'),('clockNs','TimeLimitExceeded'),('memoryBytes','MemoryLimitExceeded'),('maxProcesses','InternalError')]:
            res=program(limits=dict(limits,**{key:0}));assert res['status']==status,(key,res)
            print(json.dumps(dict(test='zero-'+key,status=res['status'])),flush=True)
        expect('zero-output-empty',program(limits=dict(limits,stdoutMaxBytes=0)),'OK')
        expect('zero-output-writer',program('output',limits=dict(limits,stdoutMaxBytes=0)),'OutputLimitExceeded')
        # 客户端关闭真实HTTP请求，等待回收后再验证服务可用。
        c=http.client.HTTPConnection('127.0.0.1',port,timeout=5)
        c.request('POST','/run',json.dumps(dict(command=['program','sleep'],inputs={'program':{'ref':ref}},limits=limits)),{'Content-Type':'application/json'})
        time.sleep(.15);c.close();time.sleep(.3)
        expect('after-cancel',program(),'OK')
    elif mode=='repeat':
        clocks=[];cpus=[];peaks=[]
        start=time.monotonic()
        for i in range(1000):
            res=program();assert res['status']=='OK' and not res.get('error'),(i,res)
            clocks.append(res['clockNs']);cpus.append(res['cpuNs']);peaks.append(res['memoryBytes'])
            if (i+1)%100==0:print(json.dumps(dict(completed=i+1,elapsedSeconds=time.monotonic()-start)),flush=True)
        print(json.dumps(dict(test='1000',clockMedianNs=statistics.median(clocks),clockMaxNs=max(clocks),cpuMedianNs=statistics.median(cpus),memoryMaxBytes=max(peaks))),flush=True)
    elif mode=='concurrency':
        cg=Path('/sys/fs/cgroup/system.slice')/(unit+'-helper.service')/'jobs'
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            start=time.monotonic();futures=[pool.submit(program,'sleep') for _ in range(2)]
            peak=0
            while not all(f.done() for f in futures):
                peak=max(peak,sum(p.is_dir() for p in cg.iterdir()));time.sleep(.005)
            for f in futures:expect('parallel-sleep',f.result(),'OK')
        assert peak==2 and time.monotonic()-start<3.5,(peak,time.monotonic()-start)
        print(json.dumps(dict(test='parallel2',peakGroups=peak,elapsedSeconds=time.monotonic()-start)),flush=True)
    else:raise AssertionError(mode)
    time.sleep(.2)
    after=snapshot();print(json.dumps(dict(snapshot='after',data=after)),flush=True)
    assert before['blobs']==after['blobs'] and before['mounts']==after['mounts'],(before,after)
    for key in ('helper','http'):assert after[key]['fds']<=before[key]['fds']+2,(before,after)
    print('PASS '+mode,flush=True)
finally:
    for ref in refs:
        request('DELETE','/blobs/'+ref)
