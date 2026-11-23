#!/usr/bin/env python3
"""安装判题节点并管理回执记录的服务与发布。"""

from __future__ import annotations

import argparse
import grp
import os
import platform
import pwd
import shutil
import socket
import subprocess
import sys
from pathlib import Path

import installation
import layout
import node_release
import node_system


def preflight(plan: node_release.ReviewPlan, source: Path) -> None:
    if platform.system() != 'Linux' or platform.machine() != 'x86_64' or os.geteuid() != 0:
        raise ValueError('installation requires root on Linux x86_64')
    for tool in ('systemctl', 'systemd-analyze', 'useradd', 'groupadd', 'python3'):
        if not shutil.which(tool):
            raise ValueError('missing tool: ' + tool)
    if int(node_system.systemctl('--version').splitlines()[0].split()[1]) < 255:
        raise ValueError('initial deployment requires systemd >= 255; other versions unvalidated')
    if layout.STATE.exists() or layout.STATE.is_symlink():
        raise ValueError('initial install refuses existing path: ' + str(layout.STATE))
    installation.require_root_protected_path(layout.STATE.parent)
    for unit in layout.UNITS:
        if node_system.has_unit_conflict(unit):
            raise ValueError('unit already exists: ' + unit)
    for port in (15051,):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.bind(('127.0.0.1', port))
    for name, uid in plan['accounts'].items():
        for lookup, key in ((pwd.getpwnam, name), (pwd.getpwuid, uid), (grp.getgrnam, name), (grp.getgrgid, uid)):
            try:
                lookup(key)
            except KeyError:
                continue
            raise ValueError('account name/id already used: ' + name)
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            rows = (proc / 'status').read_text().splitlines()
        except (FileNotFoundError, ProcessLookupError):
            continue
        for row in rows:
            if row.startswith(('Uid:', 'Gid:')) and set(map(int, row.split()[1:])) & set(plan['accounts'].values()):
                raise ValueError('reserved identity still has processes')
    node_release.check_release(plan, source)


def install(review: Path, source: Path, token_file: Path) -> None:
    plan = node_release.read_plan(review)
    release = plan['release']
    # Read without printing; control-plane token is supplied explicitly, never generated.
    installation.require_single_link_regular_file(token_file)
    if token_file.stat().st_mode & 0o077:
        raise ValueError('private token file must have mode 0600 or stricter')
    token = token_file.read_text().strip()
    if len(token) < 16 or len(token) > 4096 or any(c.isspace() for c in token):
        raise ValueError('control token is missing or invalid')
    preflight(plan, source)
    # 目录模式由安装器决定，不受调用者的 umask 影响（执行器要求受信配置的祖先目录不可被他人写，也要求服务能穿过）。
    os.umask(0o022)
    layout.STATE.mkdir(mode=0o755)
    receipt = dict(
        version=1,
        status='installing',
        release=release,
        previous=None,
        releases=[release],
        accounts={},
        files={},
        enabled=False,
    )
    installation.write_receipt(receipt)
    # Failures intentionally keep the audit and partial installation for inspection.
    for name, uid in plan['accounts'].items():
        node_system.run('groupadd', '--gid', str(uid), name)
        receipt['accounts'][name] = {'uid': uid, 'groupCreated': True, 'userCreated': False}
        installation.write_receipt(receipt)
        node_system.run(
            'useradd',
            '--uid',
            str(uid),
            '--gid',
            str(uid),
            '--no-create-home',
            '--no-user-group',
            '--home-dir',
            '/nonexistent',
            '--shell',
            '/usr/sbin/nologin',
            name,
        )
        receipt['accounts'][name]['userCreated'] = True
        installation.write_receipt(receipt)
    (layout.STATE / 'releases').mkdir(mode=0o755)
    node_release.place_release(source, release)
    (layout.STATE / 'current').symlink_to(Path('releases') / release)
    node_release.check_executor()
    layout.JUDGE_DATA.mkdir(mode=0o700)
    os.chown(layout.JUDGE_DATA, layout.JUDGE_UID, layout.JUDGE_UID)
    layout.ETC.mkdir(mode=0o755)
    for name in layout.UNITS:
        shutil.copyfile(review / name, layout.SYSTEMD / name)
        (layout.SYSTEMD / name).chmod(0o644)
        receipt['files'][str(layout.SYSTEMD / name)] = installation.digest(layout.SYSTEMD / name)
        installation.write_receipt(receipt)
    node_release.write_config(review, token, receipt)
    installation.save_operations(receipt)
    node_release.save_review(review, release, receipt)
    installation.write_receipt(receipt)
    node_system.verify_units()
    node_system.systemctl('daemon-reload')
    receipt['status'] = 'installed'
    installation.write_receipt(receipt)
    print('Installed, not enabled or started. Receipt: ' + str(layout.RECEIPT))


def restore_uninstalled_units() -> None:
    receipt = installation.require_owned_installation(allow_uninstalled=True)
    if receipt['status'] != 'uninstalled':
        raise ValueError('restore requires an uninstalled receipt')
    for name, item in receipt['accounts'].items():
        uid = item['uid']
        account = pwd.getpwnam(name)
        if account.pw_uid != uid or account.pw_gid != uid or grp.getgrnam(name).gr_gid != uid:
            raise ValueError('reserved account changed: ' + name)
    for name in layout.UNITS:
        target = layout.SYSTEMD / name
        backup = layout.STATE / 'unit-backup' / name
        installation.require_root_protected_path(backup)
        installation.require_single_link_regular_file(backup)
        expected = receipt['files'][str(target)]
        if receipt['files'].get(str(backup)) != expected or installation.digest(backup) != expected:
            raise ValueError('unit backup differs: ' + name)
        if target.exists() or target.is_symlink():
            raise ValueError('restore refuses existing unit: ' + name)
        if node_system.has_unit_conflict(name):
            raise ValueError('restore unit conflicts: ' + name)
    created = []
    try:
        for name in layout.UNITS:
            target = layout.SYSTEMD / name
            with target.open('xb') as stream:
                created.append(target)
                stream.write((layout.STATE / 'unit-backup' / name).read_bytes())
            target.chmod(0o644)
        node_system.verify_units()
        node_system.systemctl('daemon-reload')
    except Exception:
        # Only the files exclusively created by this attempt are rollback candidates.
        for target in created:
            installation.require_single_link_regular_file(target)
            if installation.digest(target) == receipt['files'][str(target)]:
                target.unlink()
        node_system.systemctl('daemon-reload')
        raise
    receipt['status'] = 'installed'
    installation.write_receipt(receipt)
    print('restore complete; original units restored, not enabled or started')


def upgrade(review: Path, source: Path) -> None:
    """装一个新发布并切换过去；原来在运行就重新启动，启动或健康检查失败自动切回原发布。

    只换程序、工具链与 etc/ 配置：账号（并发数）与 systemd 单元不变，要改它们得重装。"""
    receipt = installation.require_owned_installation()
    plan = node_release.read_plan(review)
    release = plan['release']
    if plan['accounts'] != {name: item['uid'] for name, item in receipt['accounts'].items()}:
        raise ValueError('upgrade cannot change accounts or parallelism; reinstall instead')
    for name in layout.UNITS:
        if installation.digest(review / name) != receipt['files'][str(layout.SYSTEMD / name)]:
            raise ValueError('upgrade cannot change systemd units; reinstall instead: ' + name)
    known = node_release.recorded_releases(receipt)
    if (
        release in known
        or (layout.STATE / 'releases' / release).exists()
        or (layout.STATE / 'releases' / release).is_symlink()
    ):
        raise ValueError('release already installed: ' + release)
    node_release.check_release(plan, source)
    os.umask(0o022)
    token = node_release.read_installed_token()
    was_running = receipt['status'] == 'running'
    previous = receipt['release']
    # 复制与保存审核材料都在停机之前做，停机时间只花在切换与启动上。
    node_release.place_release(source, release)
    known.append(release)
    installation.write_receipt(receipt)
    node_release.save_review(review, release, receipt)
    installation.save_operations(receipt)
    installation.write_receipt(receipt)
    try:
        node_release.switch_release(receipt, release, token, was_running)
    except Exception as error:
        if receipt.get('status') != 'stopped' or not (node_release.reviews_directory() / previous).is_dir():
            raise
        node_release.switch_release(receipt, previous, token, was_running)
        raise ValueError(f'upgrade to {release} failed ({error}); switched back to {previous}') from error
    receipt['previous'] = previous
    installation.write_receipt(receipt)
    print(f'upgraded {previous} -> {release}; status {receipt["status"]}')


def rollback() -> None:
    receipt = installation.require_owned_installation()
    previous, current = receipt.get('previous'), receipt['release']
    if not previous:
        raise ValueError('no previous release to roll back to')
    node_release.switch_release(receipt, previous, node_release.read_installed_token(), receipt['status'] == 'running')
    receipt['previous'] = current
    installation.write_receipt(receipt)
    print(f'rolled back {current} -> {previous}; status {receipt["status"]}')


def prune() -> None:
    """删掉当前与上一个发布以外、由安装器记录的发布及其审核材料；只删回执里有的，不碰别的目录。"""
    receipt = installation.require_owned_installation()
    if os.readlink(layout.STATE / 'current') != str(Path('releases') / receipt['release']):
        raise ValueError('current does not point at the recorded release')
    keep = {receipt['release'], receipt.get('previous')}
    for release in [r for r in node_release.recorded_releases(receipt) if r not in keep]:
        for target in (layout.STATE / 'releases' / release, node_release.reviews_directory() / release):
            if target.is_symlink():
                raise ValueError('refusing symlinked release path: ' + str(target))
            if target.exists():
                installation.require_root_protected_path(target.parent)
                shutil.rmtree(target)
        prefix = str(node_release.reviews_directory() / release) + '/'
        receipt['files'] = {path: value for path, value in receipt['files'].items() if not path.startswith(prefix)}
        receipt['releases'].remove(release)
        installation.write_receipt(receipt)
        print('pruned ' + release)


def operate(action: str) -> None:
    if action == 'restore':
        restore_uninstalled_units()
        return
    receipt = installation.require_owned_installation()
    if action == 'status':
        print(node_system.systemctl('show', *layout.UNITS, '-p', 'Id', '-p', 'ActiveState', '-p', 'MainPID'))
        return
    if action == 'start':
        node_system.start_services()
        receipt['status'] = 'running'
    else:
        node_system.stop_services()
        receipt['status'] = 'stopped'
        if action == 'uninstall':
            group = Path(layout.GROUP)
            if group.exists() and any(p.is_dir() for p in group.iterdir()):
                raise ValueError('unknown child groups remain in owned slice')
            installation.backup_units(receipt)
            node_system.systemctl('stop', 'cherry-sandbox.slice')
            node_system.systemctl('disable', *layout.UNITS[1:])
            for name in layout.UNITS:
                (layout.SYSTEMD / name).unlink()
            node_system.systemctl('daemon-reload')
            receipt['status'] = 'uninstalled'
    installation.write_receipt(receipt)
    print(action + ' complete; accounts, configuration, release, audit and judge data retained')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    install_parser = commands.add_parser('install')
    for name in ('review', 'release-source', 'token-file'):
        install_parser.add_argument('--' + name, type=Path, required=True)
    upgrade_parser = commands.add_parser('upgrade')
    for name in ('review', 'release-source'):
        upgrade_parser.add_argument('--' + name, type=Path, required=True)
    for action in ('status', 'start', 'stop', 'uninstall', 'restore', 'rollback', 'prune'):
        commands.add_parser(action)
    args = parser.parse_args()
    if args.action == 'install':
        install(args.review, args.release_source, args.token_file)
    elif args.action == 'upgrade':
        upgrade(args.review, args.release_source)
    elif args.action == 'rollback':
        rollback()
    elif args.action == 'prune':
        prune()
    else:
        operate(args.action)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        # subprocess stderr may include deployment metadata; no command body is printed.
        print('Deployment operation failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
