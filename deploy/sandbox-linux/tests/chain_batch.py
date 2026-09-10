#!/usr/bin/env python3
"""仅管理work048-chain专用测试单元。单批180s上限，finally停止本批服务。"""
import http.client
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time
base=Path(sys.argv[1]);mode=sys.argv[2]
assert base.parent==Path('/var/lib/cherry-sandbox-test') and base.name.startswith('work048-chain-')
assert mode in ('smoke','repeat','concurrency')
unit='cherry-sandbox-test-work048-chain-'+mode+'-v5'
if len(sys.argv) == 4:
    unit = sys.argv[3]
    assert re.fullmatch('cherry-sandbox-test-work048-chain-' + mode + '-[a-z0-9-]+', unit)
helper=unit+'-helper';server=unit+'-http';driver=unit+'-driver'

def launch(name,props,args,wait=False):
    cmd=['systemd-run','--unit='+name,'--collect']
    if wait:cmd+=['--wait','--pipe']
    for p in props:cmd+=['-p',p]
    return subprocess.run(cmd+args,check=True,text=True)

assert not subprocess.check_output(['systemctl','list-units','--all','--no-legend',unit+'-*'],text=True).strip()
service=base/'service';service.mkdir(mode=0o700,exist_ok=True);os.chown(service,61001,61001)
config=dict(logging=dict(path=str(service/'logs')),sandbox=dict(backend='linux',httpAddr='127.0.0.1:15050',helperSocket='/run/'+helper+'/helper.sock',workspaceRoot=str(service/'work'),parallelism=2 if mode=='concurrency' else 1,queueSize=4,maxRequestBytes=2<<20,store=dict(root=str(service/'blobs'),maxBlobBytes=64<<20,maxTotalBytes=256<<20,maxEntries=128,retention='1h')))
# JSON是YAML子集，避免远端安装YAML库。
(base/'sandbox.yaml').write_text(json.dumps(config))
common=['MemorySwapMax=0','RuntimeMaxSec=180','KillMode=control-group','CPUQuota=100%']
try:
    launch(helper,common+['MemoryMax=768M','TasksMax=192','Delegate=yes'],['python3',str(base/'bootstrap.py'),str(base),helper,'cpp']+(['parallel2'] if mode=='concurrency' else []))
    deadline=time.monotonic()+20
    while not Path('/run',helper,'helper.sock').exists():
        assert time.monotonic()<deadline,'helper startup timeout'
        time.sleep(.1)
    launch(server,['MemoryMax=256M','MemorySwapMax=0','TasksMax=96','CPUQuota=50%','RuntimeMaxSec=180','KillMode=control-group','NoNewPrivileges=yes'],['setpriv','--reuid=61001','--regid=61001','--clear-groups','--bounding-set=-all','--no-new-privs',str(base/'sandbox'),'-config',str(base/'sandbox.yaml')])
    while True:
        try:
            c=http.client.HTTPConnection('127.0.0.1',15050,timeout=.5);c.request('GET','/version');r=c.getresponse();r.read();c.close()
            if r.status==200:break
        except OSError:pass
        assert time.monotonic()<deadline,'HTTP startup timeout'
        time.sleep(.1)
    for name in (helper,server):
        subprocess.run(['systemctl','show',name,'-p','MemoryMax','-p','TasksMax','-p','CPUQuotaPerSecUSec','-p','MainPID'],check=True)
    launch(driver,['MemoryMax=128M','MemorySwapMax=0','TasksMax=16','CPUQuota=50%','RuntimeMaxSec=150'],['python3',str(base/'http_chain.py'),str(base),mode,unit],wait=True)
finally:
    subprocess.run(['systemctl','stop',server,helper],check=False)
    for name in (server,helper):
        path=Path('/sys/fs/cgroup/system.slice')/(name+'.service')
        assert not path.exists(),('remaining group',path)
