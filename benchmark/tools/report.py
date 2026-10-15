"""报告与退出码（协议 §9、§10）。"""

from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
from typing import Any

from judge_entry import Declared, ProblemResult

EXIT_PASS, EXIT_FAIL, EXIT_UNSTABLE, EXIT_ENVIRONMENT = 0, 1, 2, 3


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def counts(results: list[ProblemResult]) -> dict[str, int]:
    tally = Counter(item.status for result in results for item in result.items)
    return {"ran": tally["pass"] + tally["fail"] + tally["unstable"], "passed": tally["pass"],
            "failed": tally["fail"], "unstable": tally["unstable"], "skipped": tally["skipped"],
            "probes": tally["probe"]}


def exit_code(results: list[ProblemResult]) -> int:
    tally = counts(results)
    if tally["failed"]:
        return EXIT_FAIL
    return EXIT_UNSTABLE if tally["unstable"] else EXIT_PASS


def build_report(started: str, finished: str, environment: dict[str, Any], results: list[ProblemResult],
                 attack_samples: int) -> dict[str, Any]:
    return {
        "schemaVersion": 1, "startedAt": started, "finishedAt": finished, "environment": environment,
        "summary": {
            "judge": counts(results),
            "attack": {"samples": attack_samples, "ran": 0, "passed": 0, "failed": 0, "unstable": 0,
                       "skipped": attack_samples}},
        "judge": [result.to_report() for result in results],
        "attack": [],
        "warnings": [warning for result in results for warning in result.warnings],
    }


def environment(endpoint: str, judge_version: str | None, declared: Declared, git_sha: str | None,
                corpus_digest: str) -> dict[str, Any]:
    # kernel、rootfsManifestSha256 是节点侧信息，由运行者提供；这里拿不到就写 null，不猜（协议 §10）。
    return {"endpoint": endpoint, "judgeVersion": judge_version, "declared": declared.to_report(),
            "kernel": None, "gitSha": git_sha, "corpusDigest": corpus_digest, "rootfsManifestSha256": None}


MARK = {"pass": "✓", "fail": "✗", "unstable": "~", "skipped": "-", "probe": "?"}


def render_text(report: dict[str, Any]) -> str:
    env = report["environment"]
    lines = [f"环境：judge {env['endpoint']}（{env['judgeVersion']}），语料指纹 {env['corpusDigest'][:12]}…",
             f"声明：{env['declared']}", ""]
    failures, skipped = [], []
    for problem in report["judge"]:
        for kind in ("submissions", "requests", "trials"):
            for item in problem[kind]:
                label = f"{problem['name']}/{item['name']}"
                lines.append(f"  {MARK[item['status']]} {label:<44} 预期 {item['expected']}  实际 {item['actual']}"
                             + (f"  （重复 {item['repeats']} 遍）" if item["repeats"] > 1 else ""))
                if item["status"] == "skipped":
                    skipped.append((label, item.get("reason")))
                elif item["status"] in ("fail", "unstable"):
                    failures.append((label, item["status"], item.get("diffs", [])))
    if skipped:
        lines += ["", "跳过："] + [f"  - {label}：{reason}" for label, reason in skipped]
    if failures:
        lines += ["", "未通过："]
        for label, status, diffs in failures:
            lines.append(f"  {label}（{status}）")
            lines += [f"      {diff}" for diff in diffs]
    tally = report["summary"]["judge"]
    lines += ["", f"判题部分：运行 {tally['ran']}，通过 {tally['passed']}，失败 {tally['failed']}，"
                  f"不稳定 {tally['unstable']}，跳过 {tally['skipped']}，探测 {tally['probes']}"]
    attack = report["summary"]["attack"]["samples"]
    # 数量为 0 时必须写明，不得省略（协议 §5）。
    lines.append("攻击样本：0，未覆盖沙箱安全" if attack == 0 else f"攻击样本：{attack}，本入口未运行")
    for warning in report["warnings"]:
        lines.append(f"警告：{warning}")
    return "\n".join(lines)
