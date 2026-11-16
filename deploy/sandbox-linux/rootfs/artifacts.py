#!/usr/bin/env python3
"""校验包锁 v2，并安全解包锁定的运行时归档。"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile
from typing import Any
from urllib.parse import urlsplit


ARCHIVE_LIMIT = 256 << 20
EXPANDED_LIMIT = 512 << 20
ENTRY_LIMIT = 65536
NAME = re.compile(r'[A-Za-z0-9_][A-Za-z0-9_.-]*')
PREFIX = re.compile(r'[A-Za-z0-9_][A-Za-z0-9_.+-]*')
SHA256 = re.compile(r'[0-9a-f]{64}')


def relative_path(value: str) -> PurePosixPath:
    if (not isinstance(value, str) or not value or len(value) > 4096 or '\\' in value
            or any(part in ('', '.', '..') for part in value.split('/'))):
        raise ValueError('归档路径必须是安全的相对路径')
    return PurePosixPath(value)


@dataclass(frozen=True)
class Archive:
    file: str
    url: str
    sha256: str
    size_bytes: int
    strip_prefix: str
    target: str
    bin: dict[str, str]

    @classmethod
    def parse(cls, value: Any) -> Archive:
        fields = {'file', 'url', 'sha256', 'sizeBytes', 'stripPrefix', 'target', 'bin'}
        if not isinstance(value, dict) or set(value) != fields:
            raise ValueError('运行时归档字段不完整或未知')
        for field in ('file', 'url', 'sha256', 'stripPrefix', 'target'):
            if not isinstance(value[field], str):
                raise ValueError('归档字段必须是字符串: ' + field)
        if not NAME.fullmatch(value['file']) or not value['file'].endswith('.tar.gz'):
            raise ValueError('归档必须是普通 .tar.gz 文件名')
        if not SHA256.fullmatch(value['sha256']):
            raise ValueError('归档 SHA-256 无效')
        url = urlsplit(value['url'])
        if (url.scheme != 'https' or not url.hostname or url.username is not None or url.password is not None
                or url.query or url.fragment or any(c.isspace() for c in value['url'])):
            raise ValueError('归档必须使用固定的无凭证 HTTPS URL')
        if type(value['sizeBytes']) is not int or not 0 < value['sizeBytes'] <= ARCHIVE_LIMIT:
            raise ValueError('归档大小超出协议范围')
        if not PREFIX.fullmatch(value['stripPrefix']):
            raise ValueError('归档顶层目录无效')
        relative_path(value['target'])
        commands = value['bin']
        if not isinstance(commands, dict) or len(commands) > 32:
            raise ValueError('归档命令表无效')
        for name, path in commands.items():
            if not isinstance(name, str) or not NAME.fullmatch(name):
                raise ValueError('归档命令名无效')
            relative_path(path)
        return cls(value['file'], value['url'], value['sha256'], value['sizeBytes'],
                   value['stripPrefix'], value['target'], dict(commands))

    def verify(self, path: Path) -> None:
        if path.is_symlink() or not path.is_file() or path.stat().st_size != self.size_bytes:
            raise ValueError('归档类型或大小不符: ' + self.file)
        with path.open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != self.sha256:
                raise ValueError('归档 SHA-256 不符: ' + self.file)


def read_lock(path: Path, *, for_build: bool = False) -> dict[str, Any]:
    document = json.loads(path.read_text())
    if (not isinstance(document, dict) or type(document.get('version')) is not int or document['version'] != 2
            or document.get('architecture') != 'amd64' or not isinstance(document.get('source'), str)
            or not document['source'] or not isinstance(document.get('packages'), list)
            or not 0 < len(document['packages']) <= 256):
        raise ValueError('锁文件必须声明 version=2、amd64、来源与非空 deb 闭包')
    if for_build and (not isinstance(document.get('language'), str)
                      or not re.fullmatch(r'[a-z][a-z0-9-]{0,31}', document['language'])
                      or not isinstance(document.get('roots'), list) or not document['roots']
                      or any(not isinstance(root, str) or not re.fullmatch(r'[a-z0-9][a-z0-9+.-]*', root)
                             for root in document['roots'])):
        raise ValueError('构建必须使用单门语言的锁与根包列表，不能使用并集锁')
    values = document.get('archives')
    if not isinstance(values, list) or len(values) > 16:
        raise ValueError('锁文件必须声明 archives 数组，最多 16 项')
    names = set()
    for package in document['packages']:
        name = package.get('file') if isinstance(package, dict) else None
        if (not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.+%:~-]*\.deb', name)
                or name in names or not isinstance(package.get('sha256'), str)
                or not SHA256.fullmatch(package['sha256'])):
            raise ValueError('deb 文件名或摘要无效或重复')
        names.add(name)
    targets: list[PurePosixPath] = []
    commands = set()
    for value in values:
        archive = Archive.parse(value)
        target = relative_path(archive.target)
        if archive.file in names or any(target == prior or target in prior.parents or prior in target.parents
                                       for prior in targets) or commands.intersection(archive.bin):
            raise ValueError('归档文件名、目录或命令重复')
        names.add(archive.file)
        targets.append(target)
        commands.update(archive.bin)
    return document


def directories(root: Path, relative: PurePosixPath) -> Path:
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink() or (current.exists() and not current.is_dir()):
            raise ValueError('不能通过链接或文件创建归档目录: ' + str(current))
        current.mkdir(mode=0o755, exist_ok=True)
    return current


def unpack(archive: Archive, source: Path, root: Path) -> None:
    """先写普通内容，最后建链接；归档链接不能成为任何写入路径的父目录。"""
    archive.verify(source)
    relative = relative_path(archive.target)
    parent = directories(root, relative.parent)
    target = parent / relative.name
    if target.exists() or target.is_symlink():
        raise ValueError('归档不能覆盖已有目录: ' + archive.target)
    target.mkdir(mode=0o755)
    with tarfile.open(source, 'r:gz') as stream:
        entries: list[tuple[tarfile.TarInfo, PurePosixPath]] = []
        seen = set()
        links = set()
        total = 0
        for info in stream:
            if len(seen) >= ENTRY_LIMIT:
                raise ValueError('归档条目超出上限')
            path = relative_path(info.name.rstrip('/'))
            if path.parts[0] != archive.strip_prefix or path in seen:
                raise ValueError('归档顶层目录不符或存在重复条目')
            seen.add(path)
            if not (info.isfile() or info.isdir() or info.issym()):
                raise ValueError('归档不允许硬链接或特殊文件')
            if len(path.parts) == 1:
                if not info.isdir():
                    raise ValueError('归档顶层必须是真目录')
                continue
            path = PurePosixPath(*path.parts[1:])
            if info.issym():
                if not info.linkname or PurePosixPath(info.linkname).is_absolute() or '\\' in info.linkname:
                    raise ValueError('归档链接必须是内部相对链接')
                links.add(path)
            if info.isfile():
                total += info.size
                if info.size < 0 or total > EXPANDED_LIMIT:
                    raise ValueError('归档解压大小超出上限')
            entries.append((info, path))
        if any(parent in links for _, path in entries for parent in path.parents):
            raise ValueError('归档条目不能写入符号链接目录')
        for info, path in entries:
            if info.issym():
                continue
            parent = directories(target, path.parent)
            dest = parent / path.name
            if info.isdir():
                directories(target, path)
            else:
                content = stream.extractfile(info)
                if content is None:
                    raise ValueError('归档文件缺少内容')
                with content, dest.open('xb') as output:
                    shutil.copyfileobj(content, output, length=65536)
                if dest.stat().st_size != info.size:
                    raise ValueError('归档文件实际大小不符')
                dest.chmod(info.mode & 0o755)
        for info, path in entries:
            if info.issym():
                parent = directories(target, path.parent)
                (parent / path.name).symlink_to(info.linkname)
        for path in links:
            try:
                destination = (target / path).resolve(strict=True)
            except (OSError, RuntimeError) as error:
                raise ValueError('归档链接不可解析') from error
            if not destination.is_relative_to(target.resolve()):
                raise ValueError('归档链接越界')
    commands = directories(root, PurePosixPath('usr/bin'))
    for name, path in archive.bin.items():
        dest = commands / name
        executable = (target / path).resolve(strict=True)
        if not executable.is_relative_to(target.resolve()) or not executable.is_file() or not os.access(executable, os.X_OK):
            raise ValueError('归档命令不是内部可执行文件')
        if dest.exists() or dest.is_symlink():
            raise ValueError('归档命令不能覆盖已有文件: ' + name)
        dest.symlink_to(os.path.relpath(target / path, commands))
