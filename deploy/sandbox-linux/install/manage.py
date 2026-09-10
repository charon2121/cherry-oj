#!/usr/bin/env python3
"""Explicit initial install, status, start, stop and uninstall of recorded resources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import pwd
import grp
import shutil
import stat
import socket
import subprocess
import sys

from layout import ACCOUNTS, ETC, STATE, UNITS
from render import write_json

RECEIPT = STATE/'installation.json'
SYSTEMD = Path('/etc/systemd/system')


def run(*args):
    return subprocess.run(args, check=True, text=True, capture_output=True, timeout=90).stdout.strip()


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while chunk := stream.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def regular(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise ValueError('expected regular single-link file: ' + str(path))


def protected(path):
    for item in [path, *path.parents]:
        info = item.lstat()
        if info.st_uid != 0 or info.st_mode & 0o022 or stat.S_ISLNK(info.st_mode):
            raise ValueError('not root-protected: ' + str(item))


def unit_conflicts(unit, facts):
    if facts.get('LoadState') == 'not-found':
        return False
    # systemd synthesizes inactive slice objects even when no unit file exists.
    implicit_slice = (unit.endswith('.slice') and facts.get('LoadState') == 'loaded'
                      and facts.get('ActiveState') == 'inactive'
                      and facts.get('Transient') == 'no'
                      and all(facts.get(key) == '' for key in ('FragmentPath','DropInPaths','ControlGroup')))
    return not implicit_slice


def preflight(plan, source):
    if platform.system() != 'Linux' or platform.machine() != 'x86_64' or os.geteuid() != 0:
        raise ValueError('installation requires root on Linux x86_64')
    for tool in ('systemctl','systemd-analyze','useradd','groupadd','python3'):
        if not shutil.which(tool):
            raise ValueError('missing tool: ' + tool)
    if int(run('systemctl','--version').splitlines()[0].split()[1]) < 255:
        raise ValueError('initial deployment requires systemd >= 255; other versions unvalidated')
    for path in (ETC, STATE, Path('/run/cherry-sandbox-helper')):
        if path.exists() or path.is_symlink():
            raise ValueError('initial install refuses existing path: ' + str(path))
        protected(path.parent)
    for unit in UNITS:
        properties=run('systemctl','show',unit,'-p','LoadState','-p','ActiveState',
                       '-p','Transient','-p','FragmentPath','-p','DropInPaths','-p','ControlGroup')
        facts=dict(line.split('=',1) for line in properties.splitlines())
        if unit_conflicts(unit,facts):
            raise ValueError('unit already exists: ' + unit)
    for port in (15050,15051):
        with socket.socket(socket.AF_INET,socket.SOCK_STREAM) as listener:
            listener.bind(('127.0.0.1',port))
    for name, uid in ACCOUNTS.items():
        for lookup, key in ((pwd.getpwnam,name),(pwd.getpwuid,uid),(grp.getgrnam,name),(grp.getgrgid,uid)):
            try:
                lookup(key)
            except KeyError:
                continue
            raise ValueError('account name/id already used: ' + name)
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            rows=(proc/'status').read_text().splitlines()
        except (FileNotFoundError, ProcessLookupError):
            continue
        for row in rows:
            if row.startswith(('Uid:','Gid:')) and set(map(int,row.split()[1:])) & set(ACCOUNTS.values()):
                raise ValueError('reserved identity still has processes')
    for name in ('sandbox','sandbox-helper','judge'):
        regular(source/'bin'/name)
        with (source/'bin'/name).open('rb') as f:
            header=f.read(20)
        if header[:6] != b'\x7fELF\x02\x01' or header[18:20] != b'\x3e\x00':
            raise ValueError('release binary is not Linux amd64 ELF: ' + name)
    for name in ('manifest.json','packages.lock.json'):
        regular(source/name)
    if digest(source/'manifest.json') != plan['rootfsSHA256']:
        raise ValueError('release rootfs manifest differs from reviewed plan')
    if not (source/'rootfs').is_dir() or (source/'rootfs').is_symlink():
        raise ValueError('release rootfs directory missing')


def install(review, source, token_file):
    plan=json.loads((review/'plan.json').read_text())
    if (plan.get('version') != 1 or plan.get('accounts') != ACCOUNTS or
            plan.get('units') != list(UNITS) or plan.get('startAutomatically') is not False):
        raise ValueError('review plan does not match installer')
    release=plan['release']
    import re
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}', release):
        raise ValueError('invalid release id')
    # Read without printing; control-plane token is supplied explicitly, never generated.
    regular(token_file)
    if token_file.stat().st_mode & 0o077:
        raise ValueError('private token file must have mode 0600 or stricter')
    token=token_file.read_text().strip()
    if len(token)<16 or len(token)>4096 or any(c.isspace() for c in token):
        raise ValueError('control token is missing or invalid')
    preflight(plan, source)
    STATE.mkdir(mode=0o755)
    receipt=dict(version=1, status='installing', release=release, accounts={}, files={}, enabled=False)
    write_json(RECEIPT,receipt,0o600)
    # Failures intentionally keep the audit and partial installation for inspection.
    for name, uid in ACCOUNTS.items():
        run('groupadd','--gid',str(uid),name)
        receipt['accounts'][name]={'uid':uid,'groupCreated':True,'userCreated':False}
        write_json(RECEIPT,receipt,0o600)
        run('useradd','--uid',str(uid),'--gid',str(uid),'--no-create-home','--no-user-group',
            '--home-dir','/nonexistent','--shell','/usr/sbin/nologin',name)
        receipt['accounts'][name]['userCreated']=True
        write_json(RECEIPT,receipt,0o600)
    (STATE/'releases').mkdir(mode=0o755)
    target=STATE/'releases'/release
    shutil.copytree(source,target,symlinks=True)
    for directory, dirs, files in os.walk(target,followlinks=False):
        for path in [Path(directory),*(Path(directory)/n for n in dirs+files)]:
            os.chown(path,0,0,follow_symlinks=False)
            if not path.is_symlink():
                path.chmod(stat.S_IMODE(path.lstat().st_mode)&~0o6022)
    (STATE/'current').symlink_to(Path('releases')/release)
    for name, uid in (('service',61001),('judge',61010)):
        path=STATE/name;path.mkdir(mode=0o700);os.chown(path,uid,uid)
    ETC.mkdir(mode=0o755)
    for name in ('helper.json','sandbox.json','helper-start.py','health.py'):
        shutil.copyfile(review/name,ETC/name);(ETC/name).chmod(0o644)
    judge=json.loads((review/'judge.json').read_text())
    judge['judge']['node']['controlToken']=token
    write_json(ETC/'judge.json',judge,0o640);os.chown(ETC/'judge.json',0,61010)
    for name in UNITS:
        shutil.copyfile(review/name,SYSTEMD/name);(SYSTEMD/name).chmod(0o644)
        receipt['files'][str(SYSTEMD/name)]=digest(SYSTEMD/name)
        write_json(RECEIPT,receipt,0o600)
    manifest=json.loads((review/'deployment.template.json').read_text())
    for record in manifest['files'].values():
        path=Path(record['path']);protected(path);regular(path)
        record['sha256']=digest(path)
    write_json(ETC/'deployment.json',manifest)
    # Bind every installed configuration/script too; never print the token-bearing file.
    for path in ETC.iterdir():
        receipt['files'][str(path)]=digest(path)
    # Keep the reviewed management tools after the temporary upload is removed.
    operations=STATE/'operations'
    operations.mkdir(mode=0o700)
    for name in ('manage.py','layout.py','render.py','verify-native.py','verify-lifecycle.py',
                 'verify-faults.py','verify-uninstall.py','verify-capabilities.py'):
        target=operations/name
        shutil.copyfile(Path(__file__).resolve().parent/name,target)
        target.chmod(0o600)
        receipt['files'][str(target)]=digest(target)
    write_json(RECEIPT,receipt,0o600)
    run('systemd-analyze','verify',*[str(SYSTEMD/name) for name in UNITS])
    run('systemctl','daemon-reload')
    receipt['status']='installed';write_json(RECEIPT,receipt,0o600)
    print('Installed, not enabled or started. Receipt: '+str(RECEIPT))


def owned(allow_uninstalled=False):
    protected(RECEIPT);regular(RECEIPT)
    receipt=json.loads(RECEIPT.read_text())
    statuses=('installed','stopped','running','uninstalled') if allow_uninstalled else ('installed','stopped','running')
    if receipt.get('version')!=1 or receipt.get('status') not in statuses:
        raise ValueError('no complete owned installation; inspect partial receipt before recovery')
    if not {str(SYSTEMD/name) for name in UNITS} <= set(receipt.get('files', {})):
        raise ValueError('unit ownership record incomplete')
    for path, expected in receipt['files'].items():
        if receipt['status']=='uninstalled' and path in {str(SYSTEMD/name) for name in UNITS}:
            continue
        path=Path(path);protected(path);regular(path)
        if digest(path)!=expected:
            raise ValueError('installed file changed; review before operation: '+str(path))
    for unit in UNITS:
        if run('systemctl','show',unit,'-p','DropInPaths','--value'):
            raise ValueError('unreviewed unit drop-in: '+unit)
    return receipt


def backup_units(receipt):
    directory=STATE/'unit-backup'
    directory.mkdir(mode=0o700,exist_ok=True)
    protected(directory)
    if not {p.name for p in directory.iterdir()} <= set(UNITS):
        raise ValueError('unknown files in unit backup')
    for name in UNITS:
        source=SYSTEMD/name
        target=directory/name
        expected=receipt['files'][str(source)]
        if target.exists() or target.is_symlink():
            protected(target);regular(target)
            if receipt['files'].get(str(target))!=expected or digest(target)!=expected:
                raise ValueError('unit backup changed: '+name)
        else:
            with target.open('xb') as stream:
                stream.write(source.read_bytes())
            target.chmod(0o600)
        if digest(target)!=expected:
            raise ValueError('unit backup differs: '+name)
        receipt['files'][str(target)]=expected
    write_json(RECEIPT,receipt,0o600)


def restore():
    receipt=owned(allow_uninstalled=True)
    if receipt['status']!='uninstalled':
        raise ValueError('restore requires an uninstalled receipt')
    for name,uid in ACCOUNTS.items():
        account=pwd.getpwnam(name)
        if account.pw_uid!=uid or account.pw_gid!=uid or grp.getgrnam(name).gr_gid!=uid:
            raise ValueError('reserved account changed: '+name)
    for name in UNITS:
        target=SYSTEMD/name
        backup=STATE/'unit-backup'/name
        protected(backup);regular(backup)
        expected=receipt['files'][str(target)]
        if receipt['files'].get(str(backup))!=expected or digest(backup)!=expected:
            raise ValueError('unit backup differs: '+name)
        if target.exists() or target.is_symlink():
            raise ValueError('restore refuses existing unit: '+name)
        properties=run('systemctl','show',name,'-p','LoadState','-p','ActiveState',
                       '-p','Transient','-p','FragmentPath','-p','DropInPaths','-p','ControlGroup')
        if unit_conflicts(name,dict(line.split('=',1) for line in properties.splitlines())):
            raise ValueError('restore unit conflicts: '+name)
    created=[]
    try:
        for name in UNITS:
            target=SYSTEMD/name
            with target.open('xb') as stream:
                created.append(target)
                stream.write((STATE/'unit-backup'/name).read_bytes())
            target.chmod(0o644)
        run('systemd-analyze','verify',*[str(SYSTEMD/name) for name in UNITS])
        run('systemctl','daemon-reload')
    except Exception:
        # Only the files exclusively created by this attempt are rollback candidates.
        for target in created:
            regular(target)
            if digest(target)==receipt['files'][str(target)]:
                target.unlink()
        run('systemctl','daemon-reload')
        raise
    receipt['status']='installed'
    write_json(RECEIPT,receipt,0o600)
    print('restore complete; original units restored, not enabled or started')


def operate(action):
    if action=='restore':
        restore()
        return
    receipt=owned()
    if action=='status':
        print(run('systemctl','show',*UNITS,'-p','Id','-p','ActiveState','-p','MainPID',
                  '-p','MemoryMax','-p','MemorySwapMax','-p','TasksMax','-p','CPUQuotaPerSecUSec'))
        return
    if action=='start':
        try:
            run('systemctl','start','cherry-sandbox-judge.service')
            run('python3',str(ETC/'health.py'),'judge')
        except Exception:
            run('systemctl','stop',*reversed(UNITS[1:]))
            raise
        receipt['status']='running'
    else:
        run('systemctl','stop',*reversed(UNITS[1:]))
        for name in UNITS[1:]:
            if (Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice')/name).exists():
                raise ValueError('service cgroup remains after stop: '+name)
        receipt['status']='stopped'
        if action=='uninstall':
            group=Path('/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice')
            if group.exists() and any(p.is_dir() for p in group.iterdir()):
                raise ValueError('unknown child groups remain in owned slice')
            backup_units(receipt)
            run('systemctl','stop','cherry-sandbox.slice')
            run('systemctl','disable',*UNITS[1:])
            for name in UNITS:
                (SYSTEMD/name).unlink()
            run('systemctl','daemon-reload')
            receipt['status']='uninstalled'
    write_json(RECEIPT,receipt,0o600)
    print(action+' complete; accounts, configuration, release, audit and judge data retained')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    commands=parser.add_subparsers(dest='action',required=True)
    install_parser=commands.add_parser('install')
    for name in ('review','release-source','token-file'):
        install_parser.add_argument('--'+name,type=Path,required=True)
    for action in ('status','start','stop','uninstall','restore'):
        commands.add_parser(action)
    args=parser.parse_args()
    if args.action=='install':
        install(args.review,args.release_source,args.token_file)
    else:
        operate(args.action)


if __name__=='__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        # subprocess stderr may include deployment metadata; no command body is printed.
        print('Deployment operation failed: '+str(error),file=sys.stderr)
        sys.exit(1)
