"""报告与退出码（协议 §9、§10）。"""

from __future__ import annotations

import json
from collections import Counter
from datetime import datetime, timezone
from typing import Any

from judge_entry import AttackResult, Declared, Item, ProblemResult

EXIT_PASS, EXIT_FAIL, EXIT_UNSTABLE, EXIT_ENVIRONMENT = 0, 1, 2, 3


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def tally_of(items: list[Item]) -> Counter:
    return Counter(item.status for item in items)


def counts(results: list[ProblemResult]) -> dict[str, int]:
    tally = tally_of([item for result in results for item in result.items])
    return {"ran": tally["pass"] + tally["fail"] + tally["unstable"], "passed": tally["pass"],
            "failed": tally["fail"], "unstable": tally["unstable"], "skipped": tally["skipped"],
            "probes": tally["probe"]}


def attack_counts(attack: list[AttackResult], samples: int) -> dict[str, int]:
    tally = tally_of([item for result in attack for item in result.items])
    return {"samples": samples, "ran": tally["pass"] + tally["fail"] + tally["unstable"],
            "passed": tally["pass"], "failed": tally["fail"], "unstable": tally["unstable"],
            "skipped": tally["skipped"]}


def exit_code(results: list[ProblemResult], attack: list[AttackResult] | None = None) -> int:
    tally = tally_of([item for result in results for item in result.items]
                     + [item for result in attack or [] for item in result.items])
    if tally["fail"]:
        return EXIT_FAIL
    return EXIT_UNSTABLE if tally["unstable"] else EXIT_PASS


def merge(results: list[ProblemResult]) -> list[ProblemResult]:
    """同一道题在两个入口各有一份结果，合并成一份（保持题目先后顺序）。"""
    merged: dict[str, ProblemResult] = {}
    for result in results:
        if result.name in merged:
            merged[result.name].items += result.items
            merged[result.name].warnings += result.warnings
        else:
            merged[result.name] = result
    return list(merged.values())


def merge_attack(results: list[AttackResult]) -> list[AttackResult]:
    merged: dict[str, AttackResult] = {}
    for result in results:
        if result.name in merged:
            merged[result.name].items += result.items
        else:
            merged[result.name] = result
    return list(merged.values())


def build_report(started: str, finished: str, environment: dict[str, Any], results: list[ProblemResult],
                 attack_samples: int, attack: list[AttackResult] | None = None) -> dict[str, Any]:
    attack = attack or []
    return {
        "schemaVersion": 1, "startedAt": started, "finishedAt": finished, "environment": environment,
        "summary": {
            "judge": counts(results),
            "attack": attack_counts(attack, attack_samples)},
        "judge": [result.to_report() for result in results],
        "attack": [result.to_report() for result in attack],
        "warnings": [warning for result in results for warning in result.warnings],
    }


def environment(endpoint: str, judge_version: str | None, declared: Declared, git_sha: str | None,
                corpus_digest: str) -> dict[str, Any]:
    # kernel、rootfsManifestSha256 是节点侧信息，由运行者提供；这里拿不到就写 null，不猜（协议 §10）。
    return {"endpoint": endpoint, "judgeVersion": judge_version, "declared": declared.to_report(),
            "kernel": None, "gitSha": git_sha, "corpusDigest": corpus_digest, "rootfsManifestSha256": None}


MARK = {"pass": "✓", "fail": "✗", "unstable": "~", "skipped": "-", "probe": "?"}


def line(label: str, item: dict[str, Any], failures: list, skipped: list) -> str:
    text = (f"  {MARK[item['status']]} {label:<52} [{item['entry']}] 预期 {brief(item['expected'])}  实际 {brief(item['actual'])}"
            + (f"  （重复 {item['repeats']} 遍）" if item["repeats"] > 1 else ""))
    if item["status"] == "skipped":
        skipped.append((f"{label} [{item['entry']}]", item.get("reason")))
    elif item["status"] in ("fail", "unstable"):
        failures.append((f"{label} [{item['entry']}]", item["status"], item.get("diffs", [])))
    return text


def brief(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, (dict, list)):
        text = json.dumps(value, ensure_ascii=False)
        return text if len(text) <= 70 else text[:70] + "…"
    return str(value)


def render_text(report: dict[str, Any]) -> str:
    env = report["environment"]
    judge = f"judge {env['endpoint']}（{env['judgeVersion']}）" if env["judgeVersion"] else "未使用 judge 入口"
    lines = [f"环境：{judge}，语料指纹 {env['corpusDigest'][:12]}…", f"声明：{env['declared']}", ""]
    failures: list = []
    skipped: list = []
    for problem in report["judge"]:
        for kind in ("submissions", "requests", "trials"):
            for item in problem[kind]:
                lines.append(line(f"{problem['name']}/{item['name']}", item, failures, skipped))
    for sample in report["attack"]:
        for item in sample["results"]:
            lines.append(line(f"attack/{sample['name']}", item, failures, skipped))
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
    attack = report["summary"]["attack"]
    # 数量为 0 时必须写明，不得省略（协议 §5）。
    if attack["samples"] == 0:
        lines.append("攻击样本：0，未覆盖沙箱安全")
    else:
        lines.append(f"攻击样本：{attack['samples']}，运行 {attack['ran']}，通过 {attack['passed']}，"
                     f"失败 {attack['failed']}，不稳定 {attack['unstable']}，跳过 {attack['skipped']}")
    for warning in report["warnings"]:
        lines.append(f"警告：{warning}")
    return "\n".join(lines)
