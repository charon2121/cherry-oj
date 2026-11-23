"""judge、sandbox 两个入口共用的结果记录及报告模型。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class Item:
    kind: str  # submission | request | trial | attack
    name: str
    status: str = "pass"  # pass | fail | unstable | skipped | probe
    expected: Any = None
    actual: Any = None
    repeats: int = 1
    reason: str | None = None
    diffs: list[str] = field(default_factory=list)
    testcases: list[dict[str, Any]] = field(default_factory=list)
    entry: str = "judge"
    residue: dict[str, Any] | None = None  # 攻击样本的回收检查结果
    runs: list[dict[str, Any]] = field(default_factory=list)  # sandbox 入口：每个运行说明的观测

    def to_report(self) -> dict[str, Any]:
        report: dict[str, Any] = {"name": self.name, "entry": self.entry, "status": self.status,
                                  "expected": self.expected, "actual": self.actual, "repeats": self.repeats}
        if self.residue is not None:
            report["residue"] = self.residue
        if self.runs:
            report["runs"] = self.runs
        if self.reason:
            report["reason"] = self.reason
        if self.diffs:
            report["diffs"] = self.diffs
        if self.testcases:
            report["testcases"] = self.testcases
        return report


@dataclass
class AttackResult:
    """一个攻击样本在各入口的结果。"""

    name: str
    items: list[Item] = field(default_factory=list)

    def to_report(self) -> dict[str, Any]:
        return {"name": self.name, "results": [item.to_report() for item in self.items]}


@dataclass
class ProblemResult:
    name: str
    digest: str
    items: list[Item] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def to_report(self) -> dict[str, Any]:
        def of(kind: str) -> list[dict[str, Any]]:
            return [item.to_report() for item in self.items if item.kind == kind]
        return {"name": self.name, "digest": self.digest, "submissions": of("submission"),
                "requests": of("request"), "trials": of("trial")}
