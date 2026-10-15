"""回收检查：一个样本跑完后，被测节点本机上不能留下任务进程、执行 cgroup 和工作区文件（协议 §8）。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from executor import ExecutorConfig

CLEAN_MEMORY_LIMIT = 16 << 20  # 干净运行的内存峰值上限（协议 §8）


@dataclass
class Residue:
    processes: int
    cgroups: int
    work_files: int

    def clean(self) -> bool:
        return self.processes == 0 and self.cgroups == 0 and self.work_files == 0

    def to_report(self, clean_probe: str) -> dict[str, Any]:
        return {"processes": self.processes, "cgroups": self.cgroups, "workFiles": self.work_files,
                "cleanProbe": clean_probe}

    def describe(self) -> str:
        return f"残留进程 {self.processes}，残留 cgroup {self.cgroups}，残留工作区文件 {self.work_files}"


class NodeInspector:
    """读被测节点本机的 /proc、cgroup 和 box 目录。运行者必须和被测节点在同一台机器上。"""

    def __init__(self, config: ExecutorConfig, proc: Path = Path("/proc")) -> None:
        self.config, self.proc = config, proc

    def _task_ids(self) -> list[range]:
        n = self.config.box_count
        return [range(self.config.payload_uid, self.config.payload_uid + n),
                range(self.config.init_uid, self.config.init_uid + n)]

    def processes(self) -> int:
        """真实 uid 落在 payload 或 init 身份范围内的进程个数。"""
        ranges, count = self._task_ids(), 0
        for entry in self.proc.iterdir():
            if not entry.name.isdigit():
                continue
            try:
                for line in (entry / "status").read_text().splitlines():
                    if line.startswith("Uid:"):
                        uid = int(line.split()[1])
                        count += any(uid in r for r in ranges)
                        break
            except (OSError, ValueError):
                continue  # 进程在读取期间退出
        return count

    def cgroups(self) -> int:
        """jobs/ 下遗留的执行组（box-*）个数。"""
        try:
            return sum(1 for p in self.config.cgroup.iterdir() if p.is_dir() and p.name.startswith("box-"))
        except OSError:
            return 0

    def box_files(self) -> int:
        """各 box 的 in/、out/ 里遗留的文件个数（运行者自己清理之后应当为 0）。"""
        count = 0
        for index in range(self.config.box_count):
            for sub in ("in", "out"):
                directory = self.config.boxes / str(index) / sub
                if directory.is_dir():
                    count += sum(1 for _ in directory.iterdir())
        return count

    def residue(self, call_leftovers: int = 0) -> Residue:
        return Residue(self.processes(), self.cgroups(), self.box_files() + call_leftovers)

    @staticmethod
    def check_probe_facts(facts: dict[str, Any], stdout: bytes) -> list[str]:
        """干净运行（sandbox 入口）的判据。"""
        problems = []
        if facts["exitCode"] != 0 or facts["reason"] != "" or facts["error"] != "":
            problems.append(f"干净运行没有正常结束：exitCode={facts['exitCode']} reason={facts['reason']!r} "
                            f"error={facts['error']!r}")
        if facts["memoryBytes"] >= CLEAN_MEMORY_LIMIT:
            problems.append(f"干净运行的内存峰值 {facts['memoryBytes']} 字节，应小于 {CLEAN_MEMORY_LIMIT}")
        if stdout != b"OK\n":
            problems.append(f"干净运行的输出应是 OK，实际 {stdout[:40]!r}")
        return problems


DEFAULT_EXECUTOR_CONFIG = Path("/opt/cherry-oj/etc/executor.conf")
DEFAULT_EXECUTOR = Path("/opt/cherry-oj/current/libexec/sandbox")
