#!/usr/bin/env python3
"""对已下载包生成锁文件，调用现有 rootfs 构建器；不安装宿主软件。"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
sys.dont_write_bytecode=True
base=Path(sys.argv[1])
assert base.parent==Path('/var/lib/cherry-sandbox-test')
env=dict(os.environ,APT_CONFIG=str(base/'apt.conf'))
plan=subprocess.check_output(['apt-get','-s','install','g++','coreutils'],env=env,text=True)
wanted=dict(re.findall(r'^Inst (\S+) \((\S+)',plan,re.M))
records=[]
for path in sorted((base/'debs').glob('*.deb')):
    values=subprocess.check_output(['dpkg-deb','-f',str(path),'Package','Version','Architecture'],text=True)
    info=dict(line.split(': ',1) for line in values.strip().splitlines())
    package=info['Package'];version=info['Version']
    if wanted.get(package)!=version:continue
    records.append(dict(file=path.name,package=package,version=version,architecture=info['Architecture'],sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
assert {r['package'] for r in records}==set(wanted),(wanted,records)
lock=dict(version=1,architecture='amd64',layout='usr-merged',source='Ubuntu noble/noble-updates via http://mirrors.tencentyun.com/ubuntu; authenticated apt indexes; selected versions and SHA256 fixed in this lock',packages=records)
lock_path=base/'packages.lock.json';lock_path.write_text(json.dumps(lock,indent=2)+'\n')
spec=importlib.util.spec_from_file_location('builder',base/'build.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
builder.build(lock_path,base/'debs',base/'cpp-rootfs-v2')
print('packages',len(records))
