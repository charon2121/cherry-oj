#!/usr/bin/env python3
"""核验应用发布清单、文件树与有界归档，不执行发布内容。"""

from __future__ import annotations

import gzip
import hashlib
import json
import os
import re
import stat
import tarfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, BinaryIO


SERVICES = ('user', 'problem', 'submission', 'judging', 'gateway')
DATABASES = SERVICES[:-1]
RELEASE = re.compile(r'[a-z0-9][a-z0-9-]{0,63}')
SHA256 = re.compile(r'[0-9a-f]{64}')
SOURCE_SHA = re.compile(r'[0-9a-f]{40}')
PACKAGE_LIMIT = 512 << 20
EXPANDED_LIMIT = 1 << 30
MANIFEST_LIMIT = 2 << 20
FILE_LIMIT = 16384
OPS = 'ops/service-identity-keys.sh'
JARS = frozenset(f'jars/{service}-service.jar' for service in SERVICES)


class TarReader:
    def __init__(self, stream: BinaryIO) -> None:
        self.stream = stream
        self.total = 0

    def read(self, size: int) -> bytes:
        value = self.stream.read(size)
        self.total += len(value)
        # tar 头、路径元数据和填充也计量，不能用无文件载荷的压缩流绕过解包总量保护。
        if self.total > EXPANDED_LIMIT + FILE_LIMIT * 5120:
            raise ValueError('tar 数据与元数据总量超限')
        return value


def bounded_tar_info() -> type[tarfile.TarInfo]:
    headers = 0
    metadata = 0

    class BoundedInfo(tarfile.TarInfo):
        @classmethod
        def frombuf(cls, buf: bytes, encoding: str, errors: str) -> tarfile.TarInfo:
            nonlocal headers, metadata
            member = super().frombuf(buf, encoding, errors)
            headers += 1
            if headers > FILE_LIMIT * 2 or member.size < 0:
                raise ValueError('tar 头数量或大小无效')
            if member.type in (tarfile.XHDTYPE, tarfile.XGLTYPE, tarfile.GNUTYPE_LONGNAME, tarfile.GNUTYPE_LONGLINK):
                metadata += member.size
                if member.size > MANIFEST_LIMIT or metadata > FILE_LIMIT * 4096:
                    raise ValueError('tar 路径元数据超限')
            elif not member.isfile() or member.type == tarfile.GNUTYPE_SPARSE:
                raise ValueError('tar 不允许链接、目录或特殊条目')
            return member

    return BoundedInfo


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return stream_digest(stream)


def stream_digest(stream: BinaryIO) -> str:
    return hashlib.file_digest(stream, 'sha256').hexdigest()


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('JSON 含重复字段: ' + key)
        result[key] = value
    return result


def read_json(path: Path, limit: int = MANIFEST_LIMIT) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError('JSON 文件类型或大小无效: ' + str(path))
    value = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_object)
    if not isinstance(value, dict):
        raise ValueError('JSON 必须是对象: ' + str(path))
    return value


def relative_path(name: str) -> PurePosixPath:
    if (not isinstance(name, str) or len(name) > 4096 or '\\' in name
            or any(not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]*', part) for part in name.split('/'))):
        raise ValueError('发布文件路径无效')
    return PurePosixPath(name)


def allowed_file(name: str) -> bool:
    relative_path(name)
    return name in JARS or name == OPS or name.startswith('web/')


def artifact_digest(files: dict[str, str]) -> str:
    return hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()


@dataclass(frozen=True)
class Manifest:
    release: str | None
    source_sha: str | None
    files: dict[str, str]
    artifact_sha256: str
    legacy: bool = False

    @classmethod
    def parse(cls, value: dict[str, Any], *, legacy: bool = False) -> Manifest:
        if 'version' in value:
            if (set(value) != {'version', 'release', 'sourceSha', 'files', 'artifactSetSha256'}
                    or type(value['version']) is not int or value['version'] != 1
                    or not isinstance(value['sourceSha'], str) or not SOURCE_SHA.fullmatch(value['sourceSha'])):
                raise ValueError('应用清单版本或字段无效')
            old = False
        else:
            fields = {'sourceSha', 'sourceBaseSha', 'sourceTreeSha256', 'files', 'artifactSetSha256'}
            if not legacy or not {'sourceSha', 'files'} <= set(value) <= fields:
                raise ValueError('新发布必须使用版本1清单')
            if value['sourceSha'] is not None and (
                    not isinstance(value['sourceSha'], str) or not re.fullmatch(r'[0-9a-f]{7,40}', value['sourceSha'])):
                raise ValueError('旧发布的来源身份无效')
            for key, pattern in (('sourceBaseSha', SOURCE_SHA), ('sourceTreeSha256', SHA256)):
                if key in value and (not isinstance(value[key], str) or not pattern.fullmatch(value[key])):
                    raise ValueError('旧发布的来源字段无效: ' + key)
            old = True
        files = value['files']
        if not isinstance(files, dict) or not 0 < len(files) < FILE_LIMIT:
            raise ValueError('发布文件清单为空或超限')
        for name, expected in files.items():
            if not allowed_file(name) or not isinstance(expected, str) or not SHA256.fullmatch(expected):
                raise ValueError('发布文件名或摘要无效: ' + name)
        if not (JARS | {OPS, 'web/index.html'}) <= set(files):
            raise ValueError('发布缺少固定 JAR、Web 入口或运维工具')
        actual = artifact_digest(files)
        if 'artifactSetSha256' in value and value['artifactSetSha256'] != actual:
            raise ValueError('发布摘要集合不符')
        release = None if old else value['release']
        if not old:
            expected_name = f'app-{value["sourceSha"][:12]}-{actual[:12]}'
            if not isinstance(release, str) or not RELEASE.fullmatch(release) or release != expected_name:
                raise ValueError('发布名与来源、摘要不符')
        return cls(release, value['sourceSha'], dict(files), actual, old)


def verify_tree(root: Path, *, legacy: bool = False, trusted: bool = False) -> Manifest:
    manifest = Manifest.parse(read_json(root / 'release.json'), legacy=legacy)
    expected = set(manifest.files) | {'release.json'}
    directories = {'.'} | {parent.as_posix() for name in expected for parent in PurePosixPath(name).parents}
    found = set()
    total = 0
    for directory, subdirs, filenames in os.walk(root, followlinks=False):
        parent = Path(directory)
        for path in [parent, *(parent / name for name in subdirs), *(parent / name for name in filenames)]:
            info = path.lstat()
            if not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode)):
                raise ValueError('发布不允许链接或特殊文件: ' + str(path))
            if path.is_dir() and path.relative_to(root).as_posix() not in directories:
                raise ValueError('发布存在清单外目录: ' + str(path))
            if trusted and (info.st_uid != 0 or stat.S_IMODE(info.st_mode) & 0o7022):
                raise ValueError('发布属主或权限不安全: ' + str(path))
            if trusted and not manifest.legacy:
                expected_mode = 0o755 if path.is_dir() or path.relative_to(root).as_posix() == OPS else 0o644
                if stat.S_IMODE(info.st_mode) != expected_mode:
                    raise ValueError('发布权限与清单规则不符: ' + str(path))
        for name in filenames:
            path = parent / name
            relative = path.relative_to(root).as_posix()
            found.add(relative)
            total += path.stat().st_size
            if len(found) > FILE_LIMIT or total > EXPANDED_LIMIT:
                raise ValueError('发布文件数或总大小超限')
            if relative not in expected:
                raise ValueError('发布存在清单外文件: ' + relative)
            if relative != 'release.json' and digest(path) != manifest.files[relative]:
                raise ValueError('发布文件摘要不符: ' + relative)
    if found != expected:
        raise ValueError('发布文件缺失')
    return manifest


def unpack(package: Path, expected: str, output: Path) -> Manifest:
    if not SHA256.fullmatch(expected):
        raise ValueError('归档期望摘要无效')
    descriptor = os.open(package, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, 'rb') as source:
        before = os.fstat(source.fileno())
        if not stat.S_ISREG(before.st_mode) or before.st_size > PACKAGE_LIMIT or stream_digest(source) != expected:
            raise ValueError('应用归档类型、大小或摘要不符')
        source.seek(0)
        output.mkdir(mode=0o700)
        seen = set()
        total = 0
        with gzip.GzipFile(fileobj=source) as compressed, \
                tarfile.open(fileobj=TarReader(compressed), mode='r|', tarinfo=bounded_tar_info()) as archive:
            for member in archive:
                name = member.name
                path = relative_path(name)
                if (not member.isfile() or name in seen or len(seen) >= FILE_LIMIT
                        or name != 'release.json' and not allowed_file(name)):
                    raise ValueError('归档条目类型、名称或数量无效')
                seen.add(name)
                total += member.size
                if member.size < 0 or total > EXPANDED_LIMIT or name == 'release.json' and member.size > MANIFEST_LIMIT:
                    raise ValueError('归档解包大小超限')
                destination = output / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                content = archive.extractfile(member)
                if content is None:
                    raise ValueError('归档条目缺少内容')
                with content, destination.open('xb') as target:
                    copied = 0
                    while chunk := content.read(65536):
                        copied += len(chunk)
                        if copied > member.size:
                            raise ValueError('归档实际解包大小超限')
                        target.write(chunk)
                    if copied != member.size:
                        raise ValueError('归档内容被截断')
                destination.chmod(0o755 if name == OPS else 0o644)
        source.seek(0)
        after = os.fstat(source.fileno())
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
                after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns) or stream_digest(source) != expected:
            raise ValueError('归档在核验期间改变')
    for directory, _, _ in os.walk(output):
        Path(directory).chmod(0o755)
    return verify_tree(output)


def migration_files(root: Path) -> dict[str, dict[str, str]]:
    import zipfile

    result = {}
    for service in DATABASES:
        with zipfile.ZipFile(root / f'jars/{service}-service.jar') as archive:
            files = {}
            for info in archive.infolist():
                if not info.filename.startswith('BOOT-INF/classes/db/migration/') or not info.filename.endswith('.sql'):
                    continue
                if info.filename in files or info.file_size > MANIFEST_LIMIT:
                    raise ValueError('JAR 的数据库迁移重复或超限')
                with archive.open(info) as content:
                    files[info.filename] = stream_digest(content)
            if not files:
                raise ValueError('JAR 缺少数据库迁移: ' + service)
            result[service] = files
    return result
