"""sandbox 入口：不经过 judge，直接调用执行器运行样本，与运行说明比对事实（协议 §6、§7.2）。"""

from __future__ import annotations

import os
import threading
from dataclasses import dataclass
from typing import Any

import specs as spec_lib
from attack import AttackSample
from corpus import Corpus, Limits, Problem, Submission
from executor import DEFAULT_OUTPUT, DEFAULT_PROCESSES, Call, Executor, ExecutorError
from judge_entry import DEFAULT_REPEATS, AttackResult, Declared, Item, ProblemResult
from node import NodeInspector, Residue
from specs import RunResult, RunSpec

# 运行说明里出现这些结论的，属于时间、内存类，默认重复运行以判定稳定性（协议 §7.3）。
TIMEBOXED_REASONS = frozenset({"cpu", "wall", "output"})
IMPLICIT_REQUIRES = ("backend:linux", "entry:sandbox")


@dataclass(frozen=True)
class SandboxItem:
    """一个提交或样本在 sandbox 入口的执行单元。"""

    kind: str  # submission | attack
    name: str
    source: str
    language: str
    limits: Limits
    runs: tuple[RunSpec, ...]
    requires: tuple[str, ...]
    inputs: dict[str, bytes]  # 测试点名字 → .in 的内容（题目的提交用）
    repeat: int | None


def items_for(corpus: Corpus, problems: list[Problem], with_attack: bool) -> list[tuple[str | None, SandboxItem]]:
    """(题名, 执行单元)；攻击样本的题名为 None。"""
    items: list[tuple[str | None, SandboxItem]] = []
    for problem in problems:
        inputs = {name: (problem.testdata / f"{name}.in").read_bytes() for name in problem.testcase_names}
        for submission in problem.submissions.values():
            if submission.sandbox:
                items.append((problem.name, from_submission(problem, submission, inputs)))
    if with_attack:
        items += [(None, from_sample(sample)) for sample in corpus.attack_samples if sample.sandbox]
    return items


def from_submission(problem: Problem, submission: Submission, inputs: dict[str, bytes]) -> SandboxItem:
    return SandboxItem("submission", submission.name, submission.source, submission.language, problem.limits,
                       submission.sandbox, problem.requires + submission.requires, inputs, None)


def from_sample(sample: AttackSample) -> SandboxItem:
    return SandboxItem("attack", sample.name, sample.source, sample.language, sample.limits, sample.sandbox,
                       sample.requires, {}, sample.repeat_sandbox)


class SandboxRunner:
    def __init__(self, executor: Executor, inspector: NodeInspector, declared: Declared, probe_source: str | None,
                 repeats: int = DEFAULT_REPEATS) -> None:
        self.executor, self.inspector, self.declared = executor, inspector, declared
        self.probe_source, self.repeats = probe_source, repeats
        self._probe_binary: bytes | None = None

    # ----- 一次运行 -----

    def _limits(self, item: SandboxItem, spec: RunSpec) -> dict[str, int]:
        limits = {**item.limits.request(), "maxProcesses": DEFAULT_PROCESSES, "stdoutMaxBytes": DEFAULT_OUTPUT,
                  "stderrMaxBytes": DEFAULT_OUTPUT}
        limits.update(spec.limits)
        return limits

    @staticmethod
    def _stdin(item: SandboxItem, spec: RunSpec) -> bytes:
        if spec.testcase is not None:
            return item.inputs[spec.testcase]
        return (spec.stdin or "").replace(spec_lib.RUNNER_PID, str(os.getpid())).encode()

    def _run_group(self, item: SandboxItem, binary: bytes, group: list[RunSpec]) -> list[Call]:
        if len(group) > self.executor.config.box_count:
            raise ExecutorError(f"并发组有 {len(group)} 个成员，超过执行器配置的 box_count={self.executor.config.box_count}")
        if len(group) == 1:
            return [self.executor.run_binary(binary, 0, self._limits(item, group[0]), self._stdin(item, group[0]))]
        barrier = threading.Barrier(len(group))
        calls: list[Call | BaseException] = [ExecutorError("没有运行")] * len(group)

        def worker(position: int) -> None:
            try:
                calls[position] = self.executor.run_binary(
                    binary, position, self._limits(item, group[position]), self._stdin(item, group[position]), barrier)
            except BaseException as error:  # noqa: BLE001  交给调用方统一处理
                calls[position] = error
                barrier.abort()

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(len(group))]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        failures = [c for c in calls if isinstance(c, BaseException)]
        if failures:
            raise ExecutorError(f"并发运行失败：{failures[0]}")
        return [c for c in calls if isinstance(c, Call)]

    @staticmethod
    def _observe(call: Call) -> tuple[RunResult | None, list[str]]:
        """把一次调用变成可比对的观测；执行器拒绝或回收未确认时没有观测，只有差异。"""
        if call.exit_code != 0 or call.facts is None:
            note = "回收未确认，该 box 作废" if call.exit_code == 2 else "执行器拒绝了请求"
            return None, [f"{note}（退出码 {call.exit_code}）：{call.refusal or '没有说明'}"]
        return RunResult(call.facts, call.stdout.decode(errors="replace"), call.stderr.decode(errors="replace"),
                         call.elapsed_ms), []

    def _repeats_for(self, item: SandboxItem, spec: RunSpec) -> int:
        if item.repeat is not None:
            return item.repeat
        assertions = spec.any_of or ((spec.assertion,) if spec.assertion else ())
        timeboxed = any(a.facts.get("reason") in TIMEBOXED_REASONS or "oom" in a.facts for a in assertions)
        return self.repeats if timeboxed else 1

    # ----- 干净运行 -----

    def clean_probe(self) -> list[str]:
        """回收检查之后立即运行 probe.cpp，证明前一个样本没有污染后一次执行（协议 §8）。"""
        if self.probe_source is None:
            return ["语料里没有干净探针 attack-common/probe.cpp"]
        if self._probe_binary is None:
            binary, call = self.executor.compile(self.probe_source, "cpp")
            if binary is None:
                return [f"干净探针编译失败：{call.refusal or call.stderr.decode(errors='replace')[:200]}"]
            self._probe_binary = binary
        limits = {"cpuNs": 1_000_000_000, "memoryBytes": 256 << 20, "clockNs": 3_000_000_000,
                  "maxProcesses": DEFAULT_PROCESSES, "stdoutMaxBytes": DEFAULT_OUTPUT, "stderrMaxBytes": DEFAULT_OUTPUT}
        call = self.executor.run_binary(self._probe_binary, 0, limits, b"")
        observed, problems = self._observe(call)
        if observed is None:
            return problems
        return NodeInspector.check_probe_facts(call.facts or {}, call.stdout)

    # ----- 一个执行单元 -----

    def run_item(self, item: SandboxItem) -> Item:
        out = Item(item.kind, item.name, entry="sandbox")
        reason = self.declared.unmet(item.requires + IMPLICIT_REQUIRES)
        if reason:
            out.status, out.reason = "skipped", reason
            return out
        binary, compiled = self.executor.compile(item.source, item.language)
        if binary is None:
            out.status = "fail"
            out.diffs = [f"编译失败：{compiled.refusal or compiled.stderr.decode(errors='replace')[:300]}"]
            return out
        signatures: list[tuple[Any, ...]] = []
        leftovers = len(compiled.leftovers)
        diffs: list[str] = []
        for group in spec_lib.groups(item.runs):
            repeats = 1 if len(group) > 1 else self._repeats_for(item, group[0])
            per_repeat: list[tuple[Any, ...]] = []
            for _ in range(repeats):
                calls = self._run_group(item, binary, group)
                leftovers += sum(len(c.leftovers) for c in calls)
                signature, group_diffs, runs = self._judge_group(group, calls)
                per_repeat.append(signature)
                diffs += group_diffs
            out.runs += runs  # 报告里每个运行说明只留最后一遍的观测
            signatures += per_repeat
            if len(set(per_repeat)) > 1:
                out.status = "unstable"
            out.repeats = max(out.repeats, repeats)
        for index in range(self.executor.config.box_count):
            self.executor.clear(index)
        residue = self.inspector.residue(leftovers)
        probe_problems = self.clean_probe()
        self.executor.clear(0)
        out.residue = residue.to_report("pass" if not probe_problems else "fail")
        diffs += ([f"回收检查未通过：{residue.describe()}"] if not residue.clean() else []) + probe_problems
        if out.status != "unstable":
            out.status = "fail" if diffs else "pass"
        out.diffs = list(dict.fromkeys(diffs)) if out.status != "pass" else []  # 多遍重复的同一条差异只留一条
        expected = [spec_lib.expected_summary(s) for s in item.runs]
        actual = [r["actual"] for r in out.runs]
        out.expected, out.actual = (expected[0], actual[0]) if len(item.runs) == 1 else (expected, actual)
        return out

    def _judge_group(self, group: list[RunSpec], calls: list[Call]) -> tuple[tuple[Any, ...], list[str], list[dict[str, Any]]]:
        diffs: list[str] = []
        signature: list[Any] = []
        runs: list[dict[str, Any]] = []
        for position, (spec, call) in enumerate(zip(group, calls)):
            label = spec.id or f"#{position}"
            observed, refusal = self._observe(call)
            run_diffs = refusal if observed is None else spec_lib.evaluate(spec, observed)
            facts = call.facts or {}
            runs.append({"id": spec.id, "box": position if len(group) > 1 else 0,
                             "status": "pass" if not run_diffs else "fail",
                             "expected": spec_lib.expected_summary(spec),
                             "actual": spec_lib.actual_summary(spec, observed) if observed else {},
                             "elapsedMs": round(call.elapsed_ms), "diffs": run_diffs})
            signature.append((bool(run_diffs), facts.get("reason"), facts.get("signal"), facts.get("exitCode")))
            diffs += [f"{label}：{d}" for d in run_diffs]
        if len(group) > 1 and not _overlap(calls):
            diffs.append("并发组的成员没有真正重叠运行（「看不到对方」只有在对方正在运行时才有意义）")
            signature.append("no-overlap")
        return tuple(signature), diffs, runs

    # ----- 批量 -----

    def run(self, corpus: Corpus, problems: list[Problem], with_attack: bool) -> tuple[list[ProblemResult], list[AttackResult]]:
        by_problem: dict[str, ProblemResult] = {}
        attack: dict[str, AttackResult] = {}
        for problem_name, item in items_for(corpus, problems, with_attack):
            result = self.run_item(item)
            if problem_name is None:
                attack.setdefault(item.name, AttackResult(item.name)).items.append(result)
            else:
                problem = corpus.problems[problem_name]
                by_problem.setdefault(problem_name, ProblemResult(problem_name, problem.digest)).items.append(result)
        return list(by_problem.values()), list(attack.values())


def _overlap(calls: list[Call]) -> bool:
    """任意两次调用的时间区间都有交集。"""
    return all(a.started < b.ended and b.started < a.ended for i, a in enumerate(calls) for b in calls[i + 1:])
