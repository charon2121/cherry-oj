#!/usr/bin/env python3
"""真实 C++ 多进程累计CPU、OOM、普通信号、后代与线程上限。"""
import json
import os
import sys
from client import call,request
path=sys.argv[1]
assert path.startswith('/run/cherry-sandbox-test-')
os.setgroups([]);os.setgid(61001);os.setuid(61001)
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
req,data=request(['g++','-O2','-pthread','main.cpp','-o','program'],inputs=[('main.cpp',source,False)],outputs=['program'],cpuNs=3000000000)
facts,_,err,files=call(path,req,data)
assert facts['ExitCode']==0 and not facts['Reason'],(facts,err)
print(json.dumps(dict(phase='compile-limits',facts=facts)),flush=True)
for mode in ['cpu-tree','kill','background','threads','memory']:
    req,data=request(['program',mode],inputs=[('program',files['program'],True)],memoryBytes=64<<20)
    facts,out,err,_=call(path,req,data)
    print(json.dumps(dict(mode=mode,facts=facts,stdout=out.decode(errors='replace'),stderr=err.decode(errors='replace')[:500])),flush=True)
    assert not facts['Error'],facts
    if mode=='cpu-tree':assert facts['Reason']=='cpu' and facts['Usage']['CPUNs']>=1000000000 and facts['ClockNs']<2000000000
    if mode=='kill':assert facts['Signal']==9 and facts['Reason']=='' and facts['Usage']['OOMKill']==0
    if mode=='background':assert facts['ExitCode']==0 and not facts['Reason'] and facts['ClockNs']<1000000000
    if mode=='threads':assert facts['ExitCode']==0 and facts['Usage']['PidsMaxEvents']>0 and out.startswith(b'denied ')
    if mode=='memory':assert facts['Usage']['OOMKill']>0 and facts['Reason']=='' and facts['Signal']==9
print('C++ resource / signal / descendant assertions passed',flush=True)
