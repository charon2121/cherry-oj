#!/usr/bin/env python3
"""本机 Unix socket 串行有界探针；只用于测试夹具，成功不代表完整验收。"""
import base64
import json
import os
import socket
import struct
import sys
import time

path=sys.argv[1]
assert path.startswith('/run/cherry-sandbox-test-')
os.setgroups([])
os.setgid(61001)
os.setuid(61001)

def exact(sock,n):
    data=b''
    while len(data)<n:
        chunk=sock.recv(n-len(data))
        if not chunk: raise EOFError('incomplete helper response')
        data+=chunk
    return data

def frame(sock):
    n=struct.unpack('>I',exact(sock,4))[0]
    assert n<=4<<20
    return json.loads(exact(sock,n))

for mode in ['identity','cpu','memory','output','identity','network','identity']:
    req=dict(Version=1,Command=['probe',mode],Limits=dict(cpuNs=1000000000,clockNs=5000000000,memoryBytes=64<<20,maxProcesses=64,stdoutMaxBytes=8192,stderrMaxBytes=4096))
    sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
    sock.settimeout(12)
    start=time.monotonic()
    try:
        sock.connect(path)
        data=json.dumps(req).encode();sock.sendall(struct.pack('>I',len(data))+data)
        result=frame(sock)
        assert not result.get('Outputs')
        completion=frame(sock)
        assert completion['Complete']
        stdout=base64.b64decode(result.get('Stdout') or '')
        result['StdoutBytes']=len(stdout)
        result.pop('Stdout',None)
        assert not result['Usage']['Populated']
        assert not result['Error'],result
        if mode=='identity':
            identity=json.loads(stdout)
            fields=identity['status']
            assert result['ExitCode']==0 and result['Reason']==''
            assert fields['Uid'].split()==['61002']*4
            assert fields['Gid'].split()==['61002']*4
            assert all(int(v,16)==0 for k,v in fields.items() if k.startswith('Cap'))
            assert fields['NoNewPrivs']=='1' and fields['Seccomp']=='2'
            assert fields['hostFileAbsent']=='true' and fields['rootWriteDenied']=='true'
            assert identity['ppid']==1
            assert all(target.startswith(('pipe:','anon_inode:','/work/.stdin')) for target in identity['fds'])
            result['identity']=identity
        elif mode=='cpu': assert result['Reason']=='cpu' and result['Usage']['CPUNs']>=1000000000
        elif mode=='memory': assert result['Usage']['OOMKill']>0 and result['Signal']==9 and result['Reason']==''
        elif mode=='output': assert result['Reason']=='output' and result['OutputExceeded'] and len(stdout)==8192
        elif mode=='network': assert result['Signal']==31 and result['Reason']==''
        result['mode']=mode;result['observedSeconds']=round(time.monotonic()-start,3)
        print(json.dumps(result),flush=True)
    finally:sock.close()
