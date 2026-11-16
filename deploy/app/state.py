#!/usr/bin/env python3
"""持久化应用切换事实，保护发布路径、互斥锁与中断恢复材料。"""

from __future__ import annotations

import fcntl
import json
import os
import re
import stat
import uuid
from contextlib import contextmanager
from dataclasses import asdict, dataclass, fields
from pathlib import Path
from typing import Any, Iterator

from artifacts import DATABASES, RELEASE, SERVICES, SHA256, SOURCE_SHA, Manifest, read_json, verify_tree
from host import Mysql


STATES = ('PREPARED', 'STOPPED', 'BACKED_UP', 'SWITCHED', 'HEALTHY', 'ROLLED_BACK', 'RECOVERY_REQUIRED')
TERMINAL = ('HEALTHY', 'ROLLED_BACK')


def atomic(path: Path, value: bytes, mode: int) -> None:
    temporary = path.with_name(path.name + '.next-' + uuid.uuid4().hex)
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, mode)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            os.fchmod(output.fileno(), mode)
            output.write(value)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        temporary.unlink(missing_ok=True)


def save(path: Path, value: dict[str, Any], mode: int = 0o600) -> None:
    atomic(path, (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode(), mode)


@dataclass
class Operation:
    version: int
    operationId: str
    action: str
    state: str
    fromRelease: str
    toRelease: str
    fromManifestSha256: str
    toManifestSha256: str
    packageSha256: str | None
    schemaBefore: dict[str, str] | None
    schemaAfter: dict[str, str] | None
    databaseBackupSha256: str | None
    mysql: dict[str, str]
    nodeBefore: dict[str, Any]
    errorCode: str | None

    @classmethod
    def parse(cls, value: dict[str, Any]) -> Operation:
        if set(value) != {field.name for field in fields(cls)}:
            raise ValueError('操作记录字段不完整或未知')
        result = cls(**value)
        if (type(result.version) is not int or result.version != 1
                or not isinstance(result.operationId, str) or not re.fullmatch(r'[0-9a-f]{32}', result.operationId)
                or result.action not in ('UPGRADE', 'ROLLBACK') or result.state not in STATES):
            raise ValueError('操作记录版本、身份或状态无效')
        for name in (result.fromRelease, result.toRelease):
            if not isinstance(name, str) or not RELEASE.fullmatch(name):
                raise ValueError('操作记录发布名无效')
        for value in (result.fromManifestSha256, result.toManifestSha256):
            if not isinstance(value, str) or not SHA256.fullmatch(value):
                raise ValueError('操作记录清单摘要无效')
        for value in (result.packageSha256, result.databaseBackupSha256):
            if value is not None and (not isinstance(value, str) or not SHA256.fullmatch(value)):
                raise ValueError('操作记录归档摘要无效')
        for history in (result.schemaBefore, result.schemaAfter):
            if history is not None and (not isinstance(history, dict) or set(history) != set(DATABASES)
                                       or any(not isinstance(value, str) or not SHA256.fullmatch(value)
                                              for value in history.values())):
                raise ValueError('操作记录的数据库历史无效')
        if (not isinstance(result.mysql, dict)
                or set(result.mysql) != {'container', 'defaultsFile', 'databasePrefix'}):
            raise ValueError('操作记录的数据库配置无效')
        Mysql(**result.mysql)
        node = result.nodeBefore
        if (not isinstance(node, dict) or set(node) != {'release', 'mainPid', 'activeState'}
                or not isinstance(node['release'], str) or not RELEASE.fullmatch(node['release'])
                or type(node['mainPid']) is not int or node['mainPid'] <= 0 or node['activeState'] != 'active'):
            raise ValueError('操作记录的原生节点事实无效')
        if result.errorCode is not None and (not isinstance(result.errorCode, str)
                                            or not re.fullmatch(r'[A-Z][A-Z_]{0,63}', result.errorCode)):
            raise ValueError('操作错误码无效')
        return result


def check_receipt(value: dict[str, Any]) -> None:
    ports = value.get('ports')
    if (not isinstance(ports, dict) or set(ports) != set(SERVICES)
            or any(type(port) is not int or not 0 < port <= 65535 for port in ports.values())
            or len(set(ports.values())) != len(SERVICES)
            or 18080 in ports.values()
            or value.get('webListen') != '127.0.0.1:18080'
            or not isinstance(value.get('services'), list)
            or sorted(value['services']) != sorted('cherry-app-' + name + '.service' for name in SERVICES)):
        raise ValueError('部署回执的本机服务配置无效')
    if 'version' in value:
        expected = {'version', 'status', 'release', 'previous', 'sourceSha', 'artifactSetSha256',
                    'ports', 'services', 'webListen', 'operationId'}
        if (set(value) != expected or type(value['version']) is not int or value['version'] != 1
                or value['status'] not in ('HEALTHY', 'SWITCHING', 'RECOVERY_REQUIRED')
                or not isinstance(value['operationId'], str) or not re.fullmatch(r'[0-9a-f]{32}', value['operationId'])):
            raise ValueError('部署回执版本或字段无效')
        if value['sourceSha'] is not None and (
                not isinstance(value['sourceSha'], str) or not SOURCE_SHA.fullmatch(value['sourceSha'])):
            raise ValueError('部署回执提交身份无效')
    else:
        known = {'sourceSha', 'judgeRelease', 'webListen', 'ports', 'services', 'sourceBaseSha',
                 'sourceTreeSha256', 'release', 'previous', 'artifactSetSha256'}
        if not set(value) <= known:
            raise ValueError('旧部署回执含未知字段')
    for field in ('release', 'previous'):
        name = value.get(field)
        if name is None and field == 'previous':
            continue
        if field == 'release' and field not in value and 'version' not in value:
            continue
        if 'version' not in value and field == 'previous' and isinstance(name, str) and name.startswith('releases/'):
            name = name[len('releases/'):]
        if not isinstance(name, str) or not RELEASE.fullmatch(name):
            raise ValueError('部署回执发布路径无效')
    if 'artifactSetSha256' in value and (not isinstance(value['artifactSetSha256'], str)
                                        or not SHA256.fullmatch(value['artifactSetSha256'])):
        raise ValueError('部署回执摘要无效')


class Store:
    def __init__(self, home: Path, *, trusted: bool = True) -> None:
        self.home = home
        self.trusted = trusted
        self.operations = home / 'operations'

    def check_path(self, path: Path, *, private: bool = False) -> None:
        if self.trusted:
            for part in (path, *path.parents):
                info = part.lstat()
                if (not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode))
                        or info.st_uid != 0 or stat.S_IMODE(info.st_mode) & 0o7022):
                    raise ValueError('运维路径属主、类型或权限不安全: ' + str(part))
            if private and stat.S_IMODE(path.stat().st_mode) & 0o077:
                raise ValueError('运维材料必须仅root可访问')
        elif path.is_symlink():
            raise ValueError('运维路径不允许链接')

    @contextmanager
    def lock(self) -> Iterator[None]:
        self.check_path(self.home)
        self.check_path(self.home / 'releases')
        self.operations.mkdir(mode=0o700, exist_ok=True)
        self.check_path(self.operations, private=True)
        descriptor = os.open(self.operations / 'lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        try:
            info = os.fstat(descriptor)
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or self.trusted and (
                    info.st_uid != 0 or stat.S_IMODE(info.st_mode) & 0o077):
                raise ValueError('运维锁类型或权限不安全')
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as error:
                raise RuntimeError('已有应用运维操作正在执行') from error
            yield
        finally:
            os.close(descriptor)

    def release_path(self, name: str) -> Path:
        if not RELEASE.fullmatch(name):
            raise ValueError('发布名无效')
        path = self.home / 'releases' / name
        self.check_path(path)
        return path

    def current(self) -> str:
        current = self.home / 'current'
        if not current.is_symlink() or self.trusted and current.lstat().st_uid != 0:
            raise ValueError('current 必须是root所有的发布链接')
        value = os.readlink(current)
        if not value.startswith('releases/') or not RELEASE.fullmatch(value[len('releases/'):]):
            raise ValueError('current 必须直接指向本安装的发布')
        name = value[len('releases/'):]
        self.release_path(name)
        return name

    def manifest(self, name: str) -> Manifest:
        result = verify_tree(self.release_path(name), legacy=True, trusted=self.trusted)
        if result.release is not None and result.release != name:
            raise ValueError('发布清单与安装目录身份不符')
        return result

    def receipt(self) -> dict[str, Any]:
        self.check_path(self.home / 'deployment.json')
        value = read_json(self.home / 'deployment.json')
        check_receipt(value)
        return value

    def healthy(self) -> tuple[str, Manifest, dict[str, Any]]:
        name = self.current()
        manifest = self.manifest(name)
        receipt = self.receipt()
        source = manifest.source_sha
        if 'version' in receipt and source is not None and not SOURCE_SHA.fullmatch(source):
            source = None
        if (receipt.get('status', 'HEALTHY') != 'HEALTHY' or receipt.get('release', name) != name
                or receipt.get('artifactSetSha256', manifest.artifact_sha256) != manifest.artifact_sha256
                or receipt.get('sourceSha') != source):
            raise ValueError('部署回执与实际运行发布不一致')
        return name, manifest, receipt

    def pending(self) -> list[Operation]:
        result = []
        for path in sorted(self.operations.iterdir()):
            if not re.fullmatch(r'[0-9a-f]{32}', path.name):
                continue
            self.check_path(path, private=True)
            record = path / 'operation.json'
            if not record.exists():
                continue  # 核验新包失败时尚未开始切换，仅保留独立诊断目录。
            self.check_path(record, private=True)
            operation = Operation.parse(read_json(record))
            if operation.operationId != path.name:
                raise ValueError('操作目录与记录身份不符')
            if operation.state not in TERMINAL:
                result.append(operation)
        return result

    def persist(self, operation: Operation) -> None:
        Operation.parse(asdict(operation))
        save(self.operations / operation.operationId / 'operation.json', asdict(operation))

    def switch(self, name: str) -> None:
        self.release_path(name)
        temporary = self.home / ('current.next-' + uuid.uuid4().hex)
        try:
            temporary.symlink_to('releases/' + name)
            os.replace(temporary, self.home / 'current')
            descriptor = os.open(self.home, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(descriptor)
            finally:
                os.close(descriptor)
        finally:
            temporary.unlink(missing_ok=True)

    def write_receipt(self, operation: Operation, original: dict[str, Any], status: str, target: str) -> None:
        manifest = self.manifest(target)
        source_sha = manifest.source_sha
        value = dict(version=1, status=status, release=target, previous=operation.fromRelease,
                     sourceSha=source_sha if source_sha is not None and SOURCE_SHA.fullmatch(source_sha) else None,
                     artifactSetSha256=manifest.artifact_sha256, ports=original['ports'], services=original['services'],
                     webListen=original['webListen'], operationId=operation.operationId)
        check_receipt(value)
        save(self.home / 'deployment.json', value, 0o644)
