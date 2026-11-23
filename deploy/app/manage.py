#!/usr/bin/env python3
"""升级或回滚已有应用安装，数据库迁移后不自动降级。"""

from __future__ import annotations

import argparse
import json
import os
import sys
import tarfile
import uuid
import zipfile
from pathlib import Path
from typing import Any

from artifacts import digest, migration_files, read_json, unpack
from host import Host, Mysql
from state import Operation, Store, atomic, check_receipt, previous_release


APP_HOME = Path('/opt/cherry-app')


def check_manifest(store: Store, name: str, expected: str) -> None:
    store.manifest(name)
    if digest(store.release_path(name) / 'release.json') != expected:
        raise ValueError('操作绑定的发布清单发生变化: ' + name)


def restore_original_release(store: Store, host: Host, operation: Operation) -> None:
    host.stop()
    check_manifest(store, operation.fromRelease, operation.fromManifestSha256)
    current = store.current()
    if current not in (operation.fromRelease, operation.toRelease):
        raise ValueError('实际 current 不属于待恢复操作')
    # 指针替换和日志不是一个事务；即使日志还停在 BACKED_UP，也必须按实际指针判断。
    before_switch = current == operation.fromRelease and operation.state in ('PREPARED', 'STOPPED', 'BACKED_UP')
    if not before_switch or operation.schemaBefore is not None:
        operation.schemaAfter = host.history()
        store.persist(operation)
        if operation.schemaBefore is None or operation.schemaAfter != operation.schemaBefore:
            raise ValueError('数据库迁移历史已改变或无法证明未变，拒绝自动回滚')
    original_path = store.operations / operation.operationId / 'deployment.before.json'
    store.check_path(original_path, private=True)
    original = read_json(original_path)
    check_receipt(original)
    store.switch(operation.fromRelease)
    atomic(store.home / 'deployment.json', original_path.read_bytes(), 0o644)
    host.start()
    host.unchanged_node(operation.nodeBefore)
    store.healthy()
    operation.state = 'ROLLED_BACK'
    store.persist(operation)


def mark_recovery_required(store: Store, host: Host, operation: Operation) -> bool:
    stopped = True
    try:
        host.stop()
    except (OSError, RuntimeError, ValueError):
        stopped = False
    operation.state = 'RECOVERY_REQUIRED'
    store.persist(operation)
    original = read_json(store.operations / operation.operationId / 'deployment.before.json')
    current = store.current()
    store.write_receipt(operation, original, 'RECOVERY_REQUIRED', current)
    return stopped


def apply_release_change(store: Store, host: Host, operation: Operation, original: dict[str, Any]) -> None:
    stage = 'STOP_FAILED'
    try:
        host.stop()
        operation.state = 'STOPPED'
        store.persist(operation)
        stage = 'BACKUP_FAILED'
        operation.schemaBefore = host.history()
        store.persist(operation)
        operation.databaseBackupSha256 = host.backup()
        operation.state = 'BACKED_UP'
        store.persist(operation)
        check_manifest(store, operation.fromRelease, operation.fromManifestSha256)
        check_manifest(store, operation.toRelease, operation.toManifestSha256)
        host.unchanged_node(operation.nodeBefore)
        stage = 'SWITCH_FAILED'
        store.write_receipt(operation, original, 'SWITCHING', operation.toRelease)
        store.switch(operation.toRelease)
        operation.state = 'SWITCHED'
        store.persist(operation)
        stage = 'START_FAILED'
        host.start()
        operation.schemaAfter = host.history()
        host.unchanged_node(operation.nodeBefore)
        check_manifest(store, operation.toRelease, operation.toManifestSha256)
        if store.current() != operation.toRelease:
            raise ValueError('current 在升级期间改变')
        store.write_receipt(operation, original, 'HEALTHY', operation.toRelease)
        operation.state = 'HEALTHY'
        store.persist(operation)
    except BaseException as error:
        operation.errorCode = stage
        try:
            restore_original_release(store, host, operation)
        except BaseException as recovery_error:
            stopped = mark_recovery_required(store, host, operation)
            status = '服务保持停止' if stopped else '无法确认服务全部停止'
            raise RuntimeError('应用需要人工恢复，' + status + '；操作材料: '
                               + str(store.operations / operation.operationId)) from recovery_error
        raise RuntimeError('应用操作失败，已恢复 ' + operation.fromRelease + '；操作材料: '
                           + str(store.operations / operation.operationId)) from error


def execute(store: Store, mysql: Mysql, action: str, package: Path | None = None,
            expected: str | None = None, host_type: type[Host] = Host) -> Operation:
    with store.lock():
        if store.pending():
            raise ValueError('存在未终结操作，请先执行 recover')
        old, _, original = store.healthy()
        operation_id = uuid.uuid4().hex
        directory = store.operations / operation_id
        directory.mkdir(mode=0o700)
        host = host_type(mysql, original['ports'], directory)
        node = host.preflight()
        if action == 'UPGRADE':
            if package is None or expected is None:
                raise ValueError('升级必须提供发布包与独立期望摘要')
            candidate = directory / 'candidate'
            manifest = unpack(package, expected, candidate)
            new = manifest.release
            destination = store.home / 'releases' / new
            if destination.exists() or destination.is_symlink():
                raise ValueError('发布名已使用，拒绝覆盖；切换旧发布请用 rollback')
            candidate.rename(destination)
        elif action == 'ROLLBACK':
            new = previous_release(original)
            if not new or new == old:
                raise ValueError('没有可回滚的上一发布')
        else:
            raise ValueError('不支持的应用操作')
        store.manifest(new)
        if action == 'ROLLBACK' and migration_files(store.release_path(old)) != migration_files(store.release_path(new)):
            raise ValueError('目标发布的数据库迁移不同，拒绝自动回滚')
        operation = Operation(
            1, operation_id, action, 'PREPARED', old, new,
            digest(store.release_path(old) / 'release.json'), digest(store.release_path(new) / 'release.json'),
            expected if action == 'UPGRADE' else None, None, None, None, host.settings(), node, None)
        atomic(directory / 'deployment.before.json', (store.home / 'deployment.json').read_bytes(), 0o600)
        store.persist(operation)
        print(json.dumps(dict(operationId=operation_id, action=action, target=new)), flush=True)
        apply_release_change(store, host, operation, original)
        return operation


def recover(store: Store, mysql: Mysql, host_type: type[Host] = Host) -> Operation:
    with store.lock():
        pending = store.pending()
        if len(pending) != 1:
            raise ValueError('recover 必须对应唯一的未终结操作')
        operation = pending[0]
        directory = store.operations / operation.operationId
        original_path = directory / 'deployment.before.json'
        store.check_path(original_path, private=True)
        original = read_json(original_path)
        check_receipt(original)
        host = host_type(mysql, original['ports'], directory)
        if host.settings() != operation.mysql:
            raise ValueError('数据库参数与待恢复操作不一致')
        try:
            restore_original_release(store, host, operation)
        except BaseException as error:
            operation.errorCode = 'RECOVERY_FAILED'
            stopped = mark_recovery_required(store, host, operation)
            status = '服务保持停止' if stopped else '无法确认服务全部停止'
            raise RuntimeError('无法自动恢复，' + status + '；操作材料: ' + str(directory)) from error
        return operation


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    for action in ('upgrade', 'rollback', 'recover'):
        command = commands.add_parser(action)
        command.add_argument('--mysql-container', required=True)
        command.add_argument('--mysql-defaults-file', required=True)
        command.add_argument('--database-prefix', required=True)
        if action == 'upgrade':
            command.add_argument('--package', type=Path, required=True)
            command.add_argument('--sha256', required=True)
    args = parser.parse_args(argv)
    try:
        if sys.platform != 'linux' or os.geteuid() != 0:
            raise ValueError('应用运维只允许在已安装的 Linux 节点上由root执行')
        os.umask(0o077)
        mysql = Mysql(args.mysql_container, args.mysql_defaults_file, args.database_prefix)
        store = Store(APP_HOME)
        if args.action == 'recover':
            result = recover(store, mysql)
        else:
            result = execute(store, mysql, args.action.upper(), getattr(args, 'package', None), getattr(args, 'sha256', None))
        print(json.dumps(dict(status=result.state, release=store.current(), operationId=result.operationId), ensure_ascii=False))
        return 0
    except (OSError, ValueError, RuntimeError, tarfile.TarError, zipfile.BadZipFile) as error:
        print('应用运维失败: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
