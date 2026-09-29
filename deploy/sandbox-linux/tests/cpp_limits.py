#!/usr/bin/env python3
"""真实 C++ 多进程累计CPU、OOM、普通信号、后代与线程上限。

    cpp_limits.py <fixture>
"""
import json
from pathlib import Path
import sys
from executor_client import Executor, drop_to_service
base=Path(sys.argv[1])
assert base.parent==Path('/var/lib/cherry-sandbox-test')
drop_to_service()
executor=Executor(base)
source=br'''
#include <unistd.h>
#include <signal.h>
#include <pthread.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
void *hold(void*) { sleep(4); return nullptr; }
int main(int argc,char**argv) {
 if(argc!=2)return 2;
 if(!strcmp(argv[1],"cpu-tree")){ if(fork()<0)return 3; for(;;){} }
 if(!strcmp(argv[1],"kill")){ raise(SIGKILL); return 4; }
 if(!strcmp(argv[1],"background")){pid_t p=fork(); if(p<0)return 3; if(!p){setsid();sleep(4);} return 0;}
 if(!strcmp(argv[1],"threads")){pthread_t t[80]; for(int i=0;i<80;i++){int e=pthread_create(&t[i],nullptr,hold,nullptr);if(e){printf("denied %d %d\n",i,e);return 0;}}return 5;}
 if(!strcmp(argv[1],"memory")){for(;;){volatile char* p=(char*)malloc(1<<20);if(!p)return 6;for(int i=0;i<(1<<20);i+=4096)p[i]=1;}}
 return 7;
}
'''
facts,_,err,files=executor.call(['g++','-O2','-pthread','main.cpp','-o','program'],inputs=[('main.cpp',source,False)],outputs=['program'],cpu_ns=3000000000)
assert facts['exitCode']==0 and not facts['reason'] and not facts['error'],(facts,err)
print(json.dumps(dict(phase='compile-limits',facts=facts)),flush=True)
for mode in ['cpu-tree','kill','background','threads','memory']:
    facts,out,err,_=executor.call(['program',mode],inputs=[('program',files['program'],True)],memory_bytes=64<<20)
    print(json.dumps(dict(mode=mode,facts=facts,stdout=out.decode(errors='replace'),stderr=err.decode(errors='replace')[:500])),flush=True)
    assert not facts['error'],facts
    if mode=='cpu-tree':assert facts['reason']=='cpu' and facts['cpuNs']>=1000000000 and facts['clockNs']<2000000000
    if mode=='kill':assert facts['signal']==9 and facts['reason']=='' and facts['oomKill']==0
    if mode=='background':assert facts['exitCode']==0 and not facts['reason'] and facts['clockNs']<1000000000
    if mode=='threads':assert facts['exitCode']==0 and facts['pidsMaxEvents']>0 and out.startswith(b'denied ')
    if mode=='memory':assert facts['oomKill']>0 and facts['reason']=='' and facts['signal']==9
print('C++ resource / signal / descendant assertions passed',flush=True)
