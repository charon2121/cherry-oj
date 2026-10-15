"""把题目的测试数据发布到被测 judge 读得到的位置（测试数据协议的本地路径形式）。

目录 0755、文件 0644，与 problem-service 写出的权限一致。发布位置的每一级祖先目录都要让 judge 的运行身份进得去：
任一级缺少对其他用户的执行权限，读取就会失败并得到 SE，被误当作判题缺陷，所以发布后检查并给出警告。
"""

from __future__ import annotations

import os
import shutil
import stat
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from corpus import Problem


class PublishError(Exception):
    pass


def publish(problem: Problem, root: Path) -> Path:
    """复制 testdata/ 到 <root>/<题名>，返回这个绝对路径（即请求里的 testDataLocation）。"""
    if not root.is_absolute():
        raise PublishError(f"发布根 {root} 必须是绝对路径")
    root.mkdir(parents=True, exist_ok=True)
    target = root / problem.name
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(mode=0o755)
    target.chmod(0o755)
    # testdata.json 最后写入：它是「这份数据已完整」的标记（测试数据协议 §6）。
    files = sorted(problem.testdata.iterdir(), key=lambda p: (p.name == "testdata.json", p.name))
    for path in files:
        destination = target / path.name
        shutil.copyfile(path, destination)
        destination.chmod(0o644)
    return target


def untraversable(path: Path) -> list[Path]:
    """路径上缺少「其他用户可进入」权限的目录（含路径自身）。"""
    blocked = []
    for directory in [path, *path.parents]:
        try:
            if not os.stat(directory).st_mode & stat.S_IXOTH:
                blocked.append(directory)
        except OSError:
            blocked.append(directory)
    return blocked


@contextmanager
def tampered(location: Path, file: str, append_text: str) -> Iterator[None]:
    """在**发布副本**的 file 末尾追加内容，退出时还原；从不触碰语料本身（协议 §3.3）。"""
    path = location / file
    original = path.read_bytes()
    mode = stat.S_IMODE(path.stat().st_mode)
    try:
        path.write_bytes(original + append_text.encode())
        yield
    finally:
        path.write_bytes(original)
        path.chmod(mode)
