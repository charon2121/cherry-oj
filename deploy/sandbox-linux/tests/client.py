"""测试专用流式协议客户端；不发布产物，不参与生产 Container 接线。"""
import base64
import json
import socket
import struct


def exact(sock,n):
    out=bytearray()
    while len(out)<n:
        data=sock.recv(n-len(out))
        if not data: raise EOFError('helper truncated response')
        out.extend(data)
    return bytes(out)


def frame(sock):
    n=struct.unpack('>I',exact(sock,4))[0]
    if n>4<<20: raise ValueError('oversized response')
    return json.loads(exact(sock,n))


def request(command,inputs=(),outputs=(),**limits):
    budget=dict(cpuNs=1000000000,clockNs=5000000000,memoryBytes=128<<20,maxProcesses=64,stdoutMaxBytes=16384,stderrMaxBytes=16384)
    budget.update(limits)
    return dict(Version=1,Command=command,Env=['GOTRACEBACK=none'],Inputs=[dict(Path=p,SizeBytes=len(data),Executable=executable) for p,data,executable in inputs],Outputs=list(outputs),Limits=budget),b''.join(data for _,data,_ in inputs)


def send(sock,req,data):
    header=json.dumps(req).encode()
    sock.sendall(struct.pack('>I',len(header))+header+data)


def call(path,req,data=b''):
    with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as sock:
        sock.settimeout(12)
        sock.connect(path)
        send(sock,req,data)
        result=frame(sock)
        artifacts={}
        allowed=set(req['Outputs'])
        total=0
        for output in result.get('Outputs') or []:
            name=output['Path'];size=output['SizeBytes']
            assert name in allowed and 0<=size<=64*1024*1024-total
            allowed.remove(name);total+=size
            artifacts[name]=exact(sock,size)
        completion=frame(sock)
        assert completion==dict(Version=1,Complete=True)
    assert not result['Usage']['Populated'],result
    stdout=base64.b64decode(result.pop('Stdout') or '')
    stderr=base64.b64decode(result.pop('Stderr') or '')
    result.update(stdoutBytes=len(stdout),stderrBytes=len(stderr))
    return result,stdout,stderr,artifacts
