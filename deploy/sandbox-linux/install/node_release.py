"""校验发布、保存审核配置并切换节点当前发布。"""

from __future__ import annotations

import json
import os
import re
import shutil
import stat
from pathlib import Path
from typing import Any

import layout
import node_system
from installation import Receipt, digest, require_root_protected_path, require_single_link_regular_file, write_receipt

ReviewPlan = dict[str, Any]
RELEASE_ID = re.compile(r'[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}')
REVIEW_FILES = (
    'plan.json',
    'executor.conf',
    'judge-start.json',
    'judge-start.py',
    'health.py',
    'judge.json',
    'deployment.template.json',
    *layout.UNITS,
)


def read_plan(review: Path) -> ReviewPlan:
    plan = json.loads((review / 'plan.json').read_text())
    if (
        plan.get('version') != 1
        or plan.get('accounts') != layout.accounts(plan.get('parallelism'))
        or plan.get('units') != list(layout.UNITS)
        or plan.get('startAutomatically') is not False
    ):
        raise ValueError('review plan does not match installer')
    if not isinstance(plan.get('release'), str) or not RELEASE_ID.fullmatch(plan['release']):
        raise ValueError('invalid release id')
    return plan


def check_release(plan: ReviewPlan, source: Path) -> None:
    for name in ('bin/judge', 'libexec/sandbox'):
        require_single_link_regular_file(source / name)
        with (source / name).open('rb') as f:
            header = f.read(20)
        if header[:6] != b'\x7fELF\x02\x01' or header[18:20] != b'\x3e\x00':
            raise ValueError('release binary is not Linux amd64 ELF: ' + name)
    check_toolchains(plan, source)


def check_toolchains(plan: ReviewPlan, source: Path) -> None:
    """发布里的工具链必须正是审核计划列出的那几门语言，每门的 rootfs 清单摘要与计划一致。"""
    reviewed = plan.get('toolchains')
    if not isinstance(reviewed, dict) or not reviewed:
        raise ValueError('review plan lists no toolchains')
    root = source / 'toolchains'
    if not root.is_dir() or root.is_symlink():
        raise ValueError('release toolchains directory missing')
    present = sorted(p.name for p in root.iterdir())
    if present != sorted(reviewed):
        raise ValueError('release toolchains differ from reviewed plan: ' + ', '.join(present))
    for language, manifest_hash in reviewed.items():
        directory = root / language
        for name in ('manifest.json', 'packages.lock.json'):
            require_single_link_regular_file(directory / name)
        if digest(directory / 'manifest.json') != manifest_hash:
            raise ValueError('release rootfs manifest differs from reviewed plan: ' + language)
        if not (directory / 'rootfs').is_dir() or (directory / 'rootfs').is_symlink():
            raise ValueError('release rootfs directory missing: ' + language)


def place_release(source: Path, release: str) -> Path:
    """复制一个发布并固定属主与权限；发布装好后不再改动（安装布局规则 3）。"""
    target = layout.STATE / 'releases' / release
    shutil.copytree(source, target, symlinks=True)
    for directory, dirs, files in os.walk(target, followlinks=False):
        for path in [Path(directory), *(Path(directory) / n for n in dirs + files)]:
            os.chown(path, 0, 0, follow_symlinks=False)
            if not path.is_symlink():
                path.chmod(stat.S_IMODE(path.lstat().st_mode) & ~0o6022)
    # 复制时清掉了所有 setuid 位；唯一例外是隔离执行器：root 所有、setuid，只有 judge 所在的组能执行；
    # 其他人可读不可执行（部署清单要核对它的摘要）。
    executor = target / 'libexec/sandbox'
    os.chown(executor, 0, layout.JUDGE_UID)
    executor.chmod(0o4754)
    return target


def check_executor() -> None:
    info = layout.EXECUTOR.stat()
    if info.st_uid != 0 or info.st_gid != layout.JUDGE_UID or stat.S_IMODE(info.st_mode) != 0o4754:
        raise ValueError('executor is not installed setuid-root for the judge group')


def write_deployment(review: Path) -> None:
    manifest = json.loads((review / 'deployment.template.json').read_text())
    for record in manifest['files'].values():
        path = Path(record['path'])
        require_root_protected_path(path)
        require_single_link_regular_file(path)
        record['sha256'] = digest(path)
    layout.write_json(layout.ETC / 'deployment.json', manifest)


def write_config(review: Path, token: str, receipt: Receipt) -> None:
    """按审核材料写 etc/：judge.json 填入令牌，部署清单按当前文件计算摘要，全部摘要记进回执。"""
    for name in ('executor.conf', 'judge-start.json', 'judge-start.py', 'health.py'):
        shutil.copyfile(review / name, layout.ETC / name)
        (layout.ETC / name).chmod(0o644)
    judge = json.loads((review / 'judge.json').read_text())
    judge['judge']['node']['controlToken'] = token
    layout.write_json(layout.ETC / 'judge.json', judge, 0o640)
    os.chown(layout.ETC / 'judge.json', 0, layout.JUDGE_UID)
    write_deployment(review)
    # Bind every installed configuration/script too; never print the token-bearing file.
    for path in layout.ETC.iterdir():
        receipt['files'][str(path)] = digest(path)


def reviews_directory() -> Path:
    return layout.STATE / 'operations' / 'reviews'


def save_review(review: Path, release: str, receipt: Receipt) -> None:
    directory = reviews_directory()
    directory.mkdir(mode=0o700, exist_ok=True)
    target = directory / release
    target.mkdir(mode=0o700)
    for name in REVIEW_FILES:
        require_single_link_regular_file(review / name)
        shutil.copyfile(review / name, target / name)
        (target / name).chmod(0o600)
        receipt['files'][str(target / name)] = digest(target / name)


def read_installed_token() -> str:
    return json.loads((layout.ETC / 'judge.json').read_text())['judge']['node']['controlToken']


def recorded_releases(receipt: Receipt) -> list[str]:
    # 早于 upgrade 的回执只记了当前发布。
    return receipt.setdefault('releases', [receipt['release']])


def switch_release(receipt: Receipt, release: str, token: str, start: bool) -> None:
    """停机切换到一个已装好的发布：按它保存的审核材料重写 etc/，原子地改 current，需要时再启动。

    中途失败时回执停在 switching，回执核验拒绝后续操作，留给人检查；不猜测哪一半已经生效。"""
    review = reviews_directory() / release
    if not (layout.STATE / 'releases' / release).is_dir() or not review.is_dir():
        raise ValueError('release or its saved review is missing: ' + release)
    node_system.stop_services()
    receipt['status'] = 'switching'
    write_receipt(receipt)
    link = layout.STATE / 'current.next'
    if link.is_symlink():
        link.unlink()
    link.symlink_to(Path('releases') / release)
    os.replace(link, layout.STATE / 'current')
    check_executor()
    write_config(review, token, receipt)
    receipt['release'] = release
    receipt['status'] = 'stopped'
    write_receipt(receipt)
    if start:
        node_system.start_services()
        receipt['status'] = 'running'
        write_receipt(receipt)
