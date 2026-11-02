#!/usr/bin/env python3
"""Explicit initial install, upgrade, rollback, prune, status, start, stop and uninstall of recorded resources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import pwd
import grp
import re
import shutil
import stat
import socket
import subprocess
import sys

from layout import accounts, ETC, EXECUTOR, GROUP, JUDGE_DATA, JUDGE_UID, JUDGE_UNIT, STATE, UNITS
from render import write_json

RECEIPT = STATE/'installation.json'
SYSTEMD = Path('/etc/systemd/system')
RELEASE_ID = re.compile(r'[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}')
# 审核目录里安装要用到的全部文件；每个发布的审核材料保存在 operations/reviews/<发布>/，回滚靠它恢复那个发布的配置。
REVIEW_FILES = ('plan.json', 'executor.conf', 'judge-start.json', 'judge-start.py', 'health.py', 'judge.json',
                'deployment.template.json', *UNITS)
OPERATION_SCRIPTS = ('manage.py', 'layout.py', 'render.py', 'verify-native.py', 'verify-lifecycle.py',
                     'verify-faults.py', 'verify-uninstall.py', 'verify-capabilities.py')


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
    if STATE.exists() or STATE.is_symlink():
        raise ValueError('initial install refuses existing path: ' + str(STATE))
    protected(STATE.parent)
    for unit in UNITS:
        properties=run('systemctl','show',unit,'-p','LoadState','-p','ActiveState',
                       '-p','Transient','-p','FragmentPath','-p','DropInPaths','-p','ControlGroup')
        facts=dict(line.split('=',1) for line in properties.splitlines())
        if unit_conflicts(unit,facts):
            raise ValueError('unit already exists: ' + unit)
    for port in (15051,):
        with socket.socket(socket.AF_INET,socket.SOCK_STREAM) as listener:
            listener.bind(('127.0.0.1',port))
    for name, uid in plan['accounts'].items():
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
            if row.startswith(('Uid:','Gid:')) and set(map(int,row.split()[1:])) & set(plan['accounts'].values()):
                raise ValueError('reserved identity still has processes')
    check_release(plan, source)


def check_release(plan, source):
    for name in ('bin/judge','libexec/sandbox'):
        regular(source/name)
        with (source/name).open('rb') as f:
            header=f.read(20)
        if header[:6] != b'\x7fELF\x02\x01' or header[18:20] != b'\x3e\x00':
            raise ValueError('release binary is not Linux amd64 ELF: ' + name)
    check_toolchains(plan, source)


def check_toolchains(plan, source):
    """发布里的工具链必须正是审核计划列出的那几门语言，每门的 rootfs 清单摘要与计划一致。"""
    reviewed = plan.get('toolchains')
    if not isinstance(reviewed, dict) or not reviewed:
        raise ValueError('review plan lists no toolchains')
    root = source/'toolchains'
    if not root.is_dir() or root.is_symlink():
        raise ValueError('release toolchains directory missing')
    present = sorted(p.name for p in root.iterdir())
    if present != sorted(reviewed):
        raise ValueError('release toolchains differ from reviewed plan: ' + ', '.join(present))
    for language, manifest_hash in reviewed.items():
        directory = root/language
        for name in ('manifest.json','packages.lock.json'):
            regular(directory/name)
        if digest(directory/'manifest.json') != manifest_hash:
            raise ValueError('release rootfs manifest differs from reviewed plan: ' + language)
        if not (directory/'rootfs').is_dir() or (directory/'rootfs').is_symlink():
            raise ValueError('release rootfs directory missing: ' + language)


def read_plan(review):
    plan=json.loads((review/'plan.json').read_text())
    if (plan.get('version') != 1 or plan.get('accounts') != accounts(plan.get('parallelism')) or
            plan.get('units') != list(UNITS) or plan.get('startAutomatically') is not False):
        raise ValueError('review plan does not match installer')
    if not isinstance(plan.get('release'), str) or not RELEASE_ID.fullmatch(plan['release']):
        raise ValueError('invalid release id')
    return plan


def place_release(source, release):
    """复制一个发布并固定属主与权限；发布装好后不再改动（安装布局规则 3）。"""
    target=STATE/'releases'/release
    shutil.copytree(source,target,symlinks=True)
    for directory, dirs, files in os.walk(target,followlinks=False):
        for path in [Path(directory),*(Path(directory)/n for n in dirs+files)]:
            os.chown(path,0,0,follow_symlinks=False)
            if not path.is_symlink():
                path.chmod(stat.S_IMODE(path.lstat().st_mode)&~0o6022)
    # 复制时清掉了所有 setuid 位；唯一例外是隔离执行器：root 所有、setuid，只有 judge 所在的组能执行；
    # 其他人可读不可执行（部署清单要核对它的摘要）。
    executor=target/'libexec/sandbox'
    os.chown(executor,0,JUDGE_UID)
    executor.chmod(0o4754)
    return target


def check_executor():
    info=EXECUTOR.stat()
    if info.st_uid!=0 or info.st_gid!=JUDGE_UID or stat.S_IMODE(info.st_mode)!=0o4754:
        raise ValueError('executor is not installed setuid-root for the judge group')


def write_deployment(review):
    manifest=json.loads((review/'deployment.template.json').read_text())
    for record in manifest['files'].values():
        path=Path(record['path']);protected(path);regular(path)
        record['sha256']=digest(path)
    write_json(ETC/'deployment.json',manifest)


def write_config(review, token, receipt):
    """按审核材料写 etc/：judge.json 填入令牌，部署清单按当前文件计算摘要，全部摘要记进回执。"""
    for name in ('executor.conf','judge-start.json','judge-start.py','health.py'):
        shutil.copyfile(review/name,ETC/name);(ETC/name).chmod(0o644)
    judge=json.loads((review/'judge.json').read_text())
    judge['judge']['node']['controlToken']=token
    write_json(ETC/'judge.json',judge,0o640);os.chown(ETC/'judge.json',0,JUDGE_UID)
    write_deployment(review)
    # Bind every installed configuration/script too; never print the token-bearing file.
    for path in ETC.iterdir():
        receipt['files'][str(path)]=digest(path)


def reviews():
    return STATE/'operations'/'reviews'


def save_review(review, release, receipt):
    directory=reviews()
    directory.mkdir(mode=0o700,exist_ok=True)
    target=directory/release
    target.mkdir(mode=0o700)
    for name in REVIEW_FILES:
        regular(review/name)
        shutil.copyfile(review/name,target/name)
        (target/name).chmod(0o600)
        receipt['files'][str(target/name)]=digest(target/name)


def save_operations(receipt):
    # Keep the reviewed management tools after the temporary upload is removed.
    operations=STATE/'operations'
    operations.mkdir(mode=0o700,exist_ok=True)
    for name in OPERATION_SCRIPTS:
        target=operations/name
        shutil.copyfile(Path(__file__).resolve().parent/name,target)
        target.chmod(0o600)
        receipt['files'][str(target)]=digest(target)


def install(review, source, token_file):
    plan=read_plan(review)
    release=plan['release']
    # Read without printing; control-plane token is supplied explicitly, never generated.
    regular(token_file)
    if token_file.stat().st_mode & 0o077:
        raise ValueError('private token file must have mode 0600 or stricter')
    token=token_file.read_text().strip()
    if len(token)<16 or len(token)>4096 or any(c.isspace() for c in token):
        raise ValueError('control token is missing or invalid')
    preflight(plan, source)
    # 目录模式由安装器决定，不受调用者的 umask 影响（执行器要求受信配置的祖先目录不可被他人写，也要求服务能穿过）。
    os.umask(0o022)
    STATE.mkdir(mode=0o755)
    receipt=dict(version=1, status='installing', release=release, previous=None, releases=[release],
                 accounts={}, files={}, enabled=False)
    write_json(RECEIPT,receipt,0o600)
    # Failures intentionally keep the audit and partial installation for inspection.
    for name, uid in plan['accounts'].items():
        run('groupadd','--gid',str(uid),name)
        receipt['accounts'][name]={'uid':uid,'groupCreated':True,'userCreated':False}
        write_json(RECEIPT,receipt,0o600)
        run('useradd','--uid',str(uid),'--gid',str(uid),'--no-create-home','--no-user-group',
            '--home-dir','/nonexistent','--shell','/usr/sbin/nologin',name)
        receipt['accounts'][name]['userCreated']=True
        write_json(RECEIPT,receipt,0o600)
    (STATE/'releases').mkdir(mode=0o755)
    place_release(source,release)
    (STATE/'current').symlink_to(Path('releases')/release)
    check_executor()
    JUDGE_DATA.mkdir(mode=0o700);os.chown(JUDGE_DATA,JUDGE_UID,JUDGE_UID)
    ETC.mkdir(mode=0o755)
    for name in UNITS:
        shutil.copyfile(review/name,SYSTEMD/name);(SYSTEMD/name).chmod(0o644)
        receipt['files'][str(SYSTEMD/name)]=digest(SYSTEMD/name)
        write_json(RECEIPT,receipt,0o600)
    write_config(review,token,receipt)
    save_operations(receipt)
    save_review(review,release,receipt)
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
    for name,item in receipt['accounts'].items():
        uid=item['uid']
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


def start_services():
    try:
        run('systemctl','start',JUDGE_UNIT)
        run('python3',str(ETC/'health.py'),'judge')
    except Exception:
        run('systemctl','stop',*reversed(UNITS[1:]))
        raise


def stop_services():
    run('systemctl','stop',*reversed(UNITS[1:]))
    for name in UNITS[1:]:
        if (Path(GROUP)/name).exists():
            raise ValueError('service cgroup remains after stop: '+name)


def installed_token():
    return json.loads((ETC/'judge.json').read_text())['judge']['node']['controlToken']


def releases(receipt):
    # 早于 upgrade 的回执只记了当前发布。
    return receipt.setdefault('releases',[receipt['release']])


def switch(receipt, release, token, start):
    """停机切换到一个已装好的发布：按它保存的审核材料重写 etc/，原子地改 current，需要时再启动。

    中途失败时回执停在 switching，owned() 拒绝后续操作，留给人检查；不猜测哪一半已经生效。"""
    review=reviews()/release
    if not (STATE/'releases'/release).is_dir() or not review.is_dir():
        raise ValueError('release or its saved review is missing: '+release)
    stop_services()
    receipt['status']='switching';write_json(RECEIPT,receipt,0o600)
    link=STATE/'current.next'
    if link.is_symlink():
        link.unlink()
    link.symlink_to(Path('releases')/release)
    os.replace(link,STATE/'current')
    check_executor()
    write_config(review,token,receipt)
    receipt['release']=release;receipt['status']='stopped'
    write_json(RECEIPT,receipt,0o600)
    if start:
        start_services()
        receipt['status']='running'
        write_json(RECEIPT,receipt,0o600)


def upgrade(review, source):
    """装一个新发布并切换过去；原来在运行就重新启动，启动或健康检查失败自动切回原发布。

    只换程序、工具链与 etc/ 配置：账号（并发数）与 systemd 单元不变，要改它们得重装。"""
    receipt=owned()
    plan=read_plan(review)
    release=plan['release']
    if plan['accounts']!={name:item['uid'] for name,item in receipt['accounts'].items()}:
        raise ValueError('upgrade cannot change accounts or parallelism; reinstall instead')
    for name in UNITS:
        if digest(review/name)!=receipt['files'][str(SYSTEMD/name)]:
            raise ValueError('upgrade cannot change systemd units; reinstall instead: '+name)
    known=releases(receipt)
    if release in known or (STATE/'releases'/release).exists() or (STATE/'releases'/release).is_symlink():
        raise ValueError('release already installed: '+release)
    check_release(plan,source)
    os.umask(0o022)
    token=installed_token()
    was_running=receipt['status']=='running'
    previous=receipt['release']
    # 复制与保存审核材料都在停机之前做，停机时间只花在切换与启动上。
    place_release(source,release)
    known.append(release);write_json(RECEIPT,receipt,0o600)
    save_review(review,release,receipt)
    save_operations(receipt)
    write_json(RECEIPT,receipt,0o600)
    try:
        switch(receipt,release,token,was_running)
    except Exception as error:
        if receipt.get('status')!='stopped' or not (reviews()/previous).is_dir():
            raise
        switch(receipt,previous,token,was_running)
        raise ValueError(f'upgrade to {release} failed ({error}); switched back to {previous}') from error
    receipt['previous']=previous
    write_json(RECEIPT,receipt,0o600)
    print(f'upgraded {previous} -> {release}; status {receipt["status"]}')


def rollback():
    receipt=owned()
    previous,current=receipt.get('previous'),receipt['release']
    if not previous:
        raise ValueError('no previous release to roll back to')
    switch(receipt,previous,installed_token(),receipt['status']=='running')
    receipt['previous']=current
    write_json(RECEIPT,receipt,0o600)
    print(f'rolled back {current} -> {previous}; status {receipt["status"]}')


def prune():
    """删掉当前与上一个发布以外、由安装器记录的发布及其审核材料；只删回执里有的，不碰别的目录。"""
    receipt=owned()
    if os.readlink(STATE/'current')!=str(Path('releases')/receipt['release']):
        raise ValueError('current does not point at the recorded release')
    keep={receipt['release'],receipt.get('previous')}
    for release in [r for r in releases(receipt) if r not in keep]:
        for target in (STATE/'releases'/release, reviews()/release):
            if target.is_symlink():
                raise ValueError('refusing symlinked release path: '+str(target))
            if target.exists():
                protected(target.parent)
                shutil.rmtree(target)
        prefix=str(reviews()/release)+'/'
        receipt['files']={path:value for path,value in receipt['files'].items() if not path.startswith(prefix)}
        receipt['releases'].remove(release)
        write_json(RECEIPT,receipt,0o600)
        print('pruned '+release)


def operate(action):
    if action=='restore':
        restore()
        return
    receipt=owned()
    if action=='status':
        print(run('systemctl','show',*UNITS,'-p','Id','-p','ActiveState','-p','MainPID'))
        return
    if action=='start':
        start_services()
        receipt['status']='running'
    else:
        stop_services()
        receipt['status']='stopped'
        if action=='uninstall':
            group=Path(GROUP)
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
    upgrade_parser=commands.add_parser('upgrade')
    for name in ('review','release-source'):
        upgrade_parser.add_argument('--'+name,type=Path,required=True)
    for action in ('status','start','stop','uninstall','restore','rollback','prune'):
        commands.add_parser(action)
    args=parser.parse_args()
    if args.action=='install':
        install(args.review,args.release_source,args.token_file)
    elif args.action=='upgrade':
        upgrade(args.review,args.release_source)
    elif args.action=='rollback':
        rollback()
    elif args.action=='prune':
        prune()
    else:
        operate(args.action)


if __name__=='__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        # subprocess stderr may include deployment metadata; no command body is printed.
        print('Deployment operation failed: '+str(error),file=sys.stderr)
        sys.exit(1)
