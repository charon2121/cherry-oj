#!/usr/bin/env python3
"""Exercise missing-input refusal and restart only for a verified owned installation."""
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
    parser.add_argument('--case',required=True,choices=('helper-config','rootfs-manifest','helper-binary'))
    args=parser.parse_args()
    receipt=manage.owned()
    manifest_path=manage.ETC/'deployment.json'
    identity=manage.digest(manifest_path)
    manifest=json.loads(manifest_path.read_text())
    key={'helper-config':'helperConfig','rootfs-manifest':'rootfsManifest','helper-binary':'helper'}[args.case]
    path=Path(manifest['files'][key]['path'])
    assert manage.digest(path)==manifest['files'][key]['sha256']
    saved=path.with_name(path.name+'.work048-missing-test')
    if saved.exists() or saved.is_symlink():raise RuntimeError('test backup already exists')
    manage.operate('stop')
    moved=False
    try:
        path.rename(saved);moved=True
        subprocess.run(['systemctl','start','--no-block','cherry-sandbox-judge.service'],check=True,timeout=5)
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            result=states()
            if result['cherry-sandbox-helper.service']=='failed' and all(result[u] in ('inactive','failed') for u in manage.UNITS[2:]):break
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
    print('Original files/identity restored; all three services healthy.',flush=True)

if __name__=='__main__':main()
