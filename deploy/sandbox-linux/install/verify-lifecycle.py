#!/usr/bin/env python3
"""缺执行器、执行器配置或 rootfs 清单时 judge 必须拒绝启动；只作用于已核验的本机安装。"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import manage


def states():
    return {unit:manage.run('systemctl','show',unit,'-p','ActiveState','--value') for unit in manage.UNITS[1:]}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case',required=True,choices=('executor-config','rootfs-manifest','executor-binary'))
    args=parser.parse_args()
    receipt=manage.owned()
    manifest_path=manage.ETC/'deployment.json'
    identity=manage.digest(manifest_path)
    manifest=json.loads(manifest_path.read_text())
    # 每门语言各有一份 rootfs 清单，缺任何一份都必须拒绝启动；这里取排序后的第一门作代表。
    rootfs=sorted(k for k in manifest['files'] if k.startswith('toolchain.') and k.endswith('.manifest'))[0]
    key={'executor-config':'executorConfig','rootfs-manifest':rootfs,'executor-binary':'executor'}[args.case]
    path=Path(manifest['files'][key]['path'])
    assert manage.digest(path)==manifest['files'][key]['sha256']
    saved=path.with_name(path.name+'.missing-test')
    if saved.exists() or saved.is_symlink():raise RuntimeError('test backup already exists')
    manage.operate('stop')
    moved=False
    try:
        path.rename(saved);moved=True
        subprocess.run(['systemctl','start','--no-block','cherry-sandbox-judge.service'],check=True,timeout=5)
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            result=states()
            # 缺执行器、它的配置或 rootfs 清单时，judge 的启动脚本或执行层冒烟失败，judge 不会上线。
            if result['cherry-sandbox-judge.service']=='failed':break
            time.sleep(.2)
        else:raise AssertionError(('services did not fail closed',states()))
        print(json.dumps(dict(case=args.case,result='PASS',states=result)),flush=True)
    finally:
        manage.run('systemctl','stop',*reversed(manage.UNITS[1:]))
        if moved:
            assert not path.exists() and manage.digest(saved)==manifest['files'][key]['sha256']
            saved.rename(path)
        assert manage.digest(manifest_path)==identity
        manage.owned()
        manage.operate('start')
    assert all(value=='active' for value in states().values())
    print('Original files/identity restored; judge healthy.',flush=True)

if __name__=='__main__':main()
