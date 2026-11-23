"""核验节点安装回执并维护其记录的文件。"""

from __future__ import annotations

import hashlib
import json
import shutil
import stat
from pathlib import Path
from typing import Any

import layout
import node_system

Receipt = dict[str, Any]

# 先保存依赖，再更新入口。仓库副本和已装副本都从各自目录导入这些文件。
OPERATION_SCRIPTS = (
    'layout.py',
    'node_system.py',
    'installation.py',
    'node_release.py',
    'render.py',
    'manage.py',
    'verify-native.py',
    'verify-lifecycle.py',
    'verify-faults.py',
    'verify-capabilities.py',
)


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while chunk := stream.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def require_single_link_regular_file(path: Path) -> None:
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise ValueError('expected regular single-link file: ' + str(path))


def require_root_protected_path(path: Path) -> None:
    for item in [path, *path.parents]:
        info = item.lstat()
        if info.st_uid != 0 or info.st_mode & 0o022 or stat.S_ISLNK(info.st_mode):
            raise ValueError('not root-protected: ' + str(item))


def write_receipt(receipt: Receipt) -> None:
    layout.write_json(layout.RECEIPT, receipt, 0o600)


def require_owned_installation(allow_uninstalled: bool = False) -> Receipt:
    require_root_protected_path(layout.RECEIPT)
    require_single_link_regular_file(layout.RECEIPT)
    receipt = json.loads(layout.RECEIPT.read_text())
    statuses = (
        ('installed', 'stopped', 'running', 'uninstalled') if allow_uninstalled else ('installed', 'stopped', 'running')
    )
    if receipt.get('version') != 1 or receipt.get('status') not in statuses:
        raise ValueError('no complete owned installation; inspect partial receipt before recovery')
    if not {str(layout.SYSTEMD / name) for name in layout.UNITS} <= set(receipt.get('files', {})):
        raise ValueError('unit ownership record incomplete')
    for path, expected in receipt['files'].items():
        if receipt['status'] == 'uninstalled' and path in {str(layout.SYSTEMD / name) for name in layout.UNITS}:
            continue
        path = Path(path)
        require_root_protected_path(path)
        require_single_link_regular_file(path)
        if digest(path) != expected:
            raise ValueError('installed file changed; review before operation: ' + str(path))
    for unit in layout.UNITS:
        if node_system.systemctl('show', unit, '-p', 'DropInPaths', '--value'):
            raise ValueError('unreviewed unit drop-in: ' + unit)
    return receipt


def backup_units(receipt: Receipt) -> None:
    directory = layout.STATE / 'unit-backup'
    directory.mkdir(mode=0o700, exist_ok=True)
    require_root_protected_path(directory)
    if not {p.name for p in directory.iterdir()} <= set(layout.UNITS):
        raise ValueError('unknown files in unit backup')
    for name in layout.UNITS:
        source = layout.SYSTEMD / name
        target = directory / name
        expected = receipt['files'][str(source)]
        if target.exists() or target.is_symlink():
            require_root_protected_path(target)
            require_single_link_regular_file(target)
            if receipt['files'].get(str(target)) != expected or digest(target) != expected:
                raise ValueError('unit backup changed: ' + name)
        else:
            with target.open('xb') as stream:
                stream.write(source.read_bytes())
            target.chmod(0o600)
        if digest(target) != expected:
            raise ValueError('unit backup differs: ' + name)
        receipt['files'][str(target)] = expected
    write_receipt(receipt)


def save_operations(receipt: Receipt) -> None:
    # Keep the reviewed management tools after the temporary upload is removed.
    operations = layout.STATE / 'operations'
    operations.mkdir(mode=0o700, exist_ok=True)
    for name in OPERATION_SCRIPTS:
        target = operations / name
        shutil.copyfile(Path(__file__).resolve().parent / name, target)
        target.chmod(0o600)
        receipt['files'][str(target)] = digest(target)
