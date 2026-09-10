#!/usr/bin/env python3
"""串行扩展测试，失败立即停止，不把 skip 或启动失败算成功。"""
import json
import os
import socket
import sys
import time
from client import call,request,send

path=sys.argv[1]
assert path.startswith('/run/cherry-sandbox-test-')
os.setgroups([]);os.setgid(61001);os.setuid(61001)

def run(mode,**limits):
    req,data=request(['probe',mode],**limits)
    start=time.monotonic()
    result,out,err,_=call(path,req,data)
    result.update(mode=mode,observedSeconds=round(time.monotonic()-start,3),stdout=out.decode(errors='replace')[:500],stderr=err.decode(errors='replace')[:500])
    print(json.dumps(result),flush=True)
    return result

result=run('background')
assert result['ExitCode']==0 and not result['Reason'] and 'background-started' in result['stdout']
assert result['observedSeconds']<2 # 后代自行睡4s；必须在它自行退出之前完成整组清理。
result=run('processes')
assert result['Usage']['PidsMaxEvents']>0 and 'spawn-denied' in result['stdout']
assert result['observedSeconds']<2
result=run('threads')
assert result['Usage']['PidsMaxEvents']>0
result=run('sleep',clockNs=150000000)
assert result['Reason']=='wall' and result['observedSeconds']<1
result=run('sleep',maxProcesses=1)
assert result['Reason']=='platform' and result['Usage']['PidsMaxEvents']>0
# 断连必须取消在途睡眠；下一请求随即成功且不等待原程序的4秒。
req,data=request(['probe','sleep'])
with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as sock:
    sock.connect(path);send(sock,req,data);time.sleep(.15)
time.sleep(.15)
result=run('identity')
assert not result['Reason'] and result['ExitCode']==0
print('extended assertions passed',flush=True)
