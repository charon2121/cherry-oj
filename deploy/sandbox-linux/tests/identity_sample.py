#!/usr/bin/env python3
"""采集执行组身份，只容忍读取期间资源已消失的明确错误。"""

from __future__ import annotations

import errno
from pathlib import Path
from typing import Iterable


def sample(groups: Iterable[Path]) -> tuple[set[int], set[int]]:
    payload_ids: set[int] = set()
    init_ids: set[int] = set()
    for group in groups:
        try:
            pids = (group / 'cgroup.procs').read_text().split()
        except OSError as error:
            # cgroup v2 删除与读取并发时既可能 ENOENT，也可能 ENODEV。
            if error.errno in (errno.ENOENT, errno.ENODEV):
                continue
            raise
        for pid in pids:
            try:
                status = Path('/proc', pid, 'status').read_text()
            except FileNotFoundError:
                continue
            uid = int(next(line for line in status.splitlines() if line.startswith('Uid:')).split()[1])
            if uid in (61002, 61004):
                payload_ids.add(uid)
            if uid in (61003, 61005):
                init_ids.add(uid)
    return payload_ids, init_ids
