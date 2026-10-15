"""sandbox 入口的运行说明：读取、校验与比对（协议 §6）。"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from corpus_base import CorpusError, Check, fields, parse_check, positive_int

# 执行器事实 JSON 里的字段（docs/protocols/sandbox-executor.md §7.2）；运行说明的 facts 只能用这些名字。
FACT_FIELDS = frozenset({
    "version", "exitCode", "signal", "cpuNs", "memoryBytes", "clockNs", "reason", "oom", "oomKill",
    "memoryMaxEvents", "pidsMaxEvents", "cancelled", "outputExceeded", "stdoutBytes", "stderrBytes",
    "outputs", "error"})
COMPARISONS = ("gt", "ge", "lt", "le", "in")
SPEC_ID = re.compile(r"[a-z0-9][a-z0-9_]{0,31}")
RUNNER_PID = "${runnerPid}"
# 运行说明里可以覆盖的限制，及缺省值（协议 §6）。
LIMIT_FIELDS = ("cpuNs", "memoryBytes", "clockNs", "maxProcesses", "stdoutMaxBytes", "stderrMaxBytes")
DEFAULT_EXTRA_LIMITS = {"maxProcesses": 64, "stdoutMaxBytes": 1 << 20, "stderrMaxBytes": 1 << 20}


@dataclass(frozen=True)
class Assertion:
    facts: dict[str, Any]
    stdout: Check | None
    stderr: Check | None


@dataclass(frozen=True)
class RunSpec:
    id: str | None
    testcase: str | None
    stdin: str | None
    limits: dict[str, int]
    assertion: Assertion | None
    any_of: tuple[Assertion, ...]
    elapsed_ms_lt: int | None
    concurrent: tuple[str, ...]


@dataclass(frozen=True)
class RunResult:
    """一次执行器调用的观测。"""

    facts: dict[str, Any]
    stdout: str
    stderr: str
    elapsed_ms: float


def _fact_matcher(value: Any, where: str) -> Any:
    if isinstance(value, dict):
        # 可以同时给出多个比较（例如 ge 与 lt 围出一个区间），全部成立才算符合。
        if not value or not set(value) <= set(COMPARISONS):
            raise CorpusError(f"{where} 必须由 {'、'.join(COMPARISONS)} 中的一个或几个组成")
        for kind, operand in value.items():
            if kind == "in":
                if not isinstance(operand, list) or not operand:
                    raise CorpusError(f"{where}.in 必须是非空数组")
            elif isinstance(operand, bool) or not isinstance(operand, (int, float)):
                raise CorpusError(f"{where}.{kind} 必须是数字")
    elif isinstance(value, list):
        raise CorpusError(f"{where} 不能是数组（要比较成员请用 in）")
    return value


def _parse_assertion(raw: Any, where: str) -> Assertion:
    obj = fields(raw, where, (), ("facts", "stdout", "stderr"))
    if not obj:
        raise CorpusError(f"{where} 至少要有 facts、stdout、stderr 之一")
    facts = obj.get("facts", {})
    if not isinstance(facts, dict):
        raise CorpusError(f"{where}.facts 必须是对象")
    unknown = sorted(set(facts) - FACT_FIELDS)
    if unknown:
        raise CorpusError(f"{where}.facts 有不认识的事实字段：{', '.join(unknown)}")
    return Assertion(
        facts={name: _fact_matcher(value, f"{where}.facts.{name}") for name, value in facts.items()},
        stdout=parse_check(obj["stdout"], f"{where}.stdout") if "stdout" in obj else None,
        stderr=parse_check(obj["stderr"], f"{where}.stderr") if "stderr" in obj else None)


def parse_run_specs(value: Any, where: str, testcases: tuple[str, ...] | None) -> tuple[RunSpec, ...]:
    """`testcases` 是所属题目的测试点名字；攻击样本传 None，表示不允许用 testcase。"""
    if not isinstance(value, list) or not value:
        raise CorpusError(f"{where} 必须是非空数组")
    specs = []
    for index, raw in enumerate(value):
        at = f"{where}[{index}]"
        obj = fields(raw, at, (), ("id", "testcase", "stdin", "limits", "facts", "stdout", "stderr", "elapsedMsLt",
                                   "concurrent", "anyOf"))
        spec_id = obj.get("id")
        if spec_id is not None and (not isinstance(spec_id, str) or not SPEC_ID.fullmatch(spec_id)):
            raise CorpusError(f"{at}.id 必须匹配 {SPEC_ID.pattern}")
        testcase, stdin = obj.get("testcase"), obj.get("stdin")
        if testcase is not None and stdin is not None:
            raise CorpusError(f"{at} 的 testcase 与 stdin 互斥")
        if testcase is not None and (testcases is None or testcase not in testcases):
            raise CorpusError(f"{at}.testcase {testcase!r} 不是所属题目的测试点")
        if stdin is not None and not isinstance(stdin, str):
            raise CorpusError(f"{at}.stdin 必须是字符串")
        limits = fields(obj.get("limits", {}), f"{at}.limits", (), LIMIT_FIELDS)
        for name, number in limits.items():
            positive_int(number, f"{at}.limits.{name}")
        any_of = obj.get("anyOf")
        if any_of is not None:
            if {"facts", "stdout", "stderr"} & set(obj):
                raise CorpusError(f"{at} 的 anyOf 与 facts / stdout / stderr 互斥")
            if not isinstance(any_of, list) or len(any_of) < 2:
                raise CorpusError(f"{at}.anyOf 必须是至少两项的数组")
            alternatives = tuple(_parse_assertion(item, f"{at}.anyOf[{i}]") for i, item in enumerate(any_of))
            assertion = None
        else:
            if not {"facts", "stdout", "stderr"} & set(obj):
                raise CorpusError(f"{at} 至少要有 facts、stdout、stderr 或 anyOf 之一")
            alternatives = ()
            assertion = _parse_assertion({k: obj[k] for k in ("facts", "stdout", "stderr") if k in obj}, at)
        elapsed = obj.get("elapsedMsLt")
        if elapsed is not None:
            positive_int(elapsed, f"{at}.elapsedMsLt")
        concurrent = obj.get("concurrent", [])
        if not isinstance(concurrent, list) or not all(isinstance(c, str) for c in concurrent):
            raise CorpusError(f"{at}.concurrent 必须是 id 数组")
        specs.append(RunSpec(spec_id, testcase, stdin, dict(limits), assertion, alternatives, elapsed,
                             tuple(concurrent)))
    ids = [s.id for s in specs if s.id]
    if len(ids) != len(set(ids)):
        raise CorpusError(f"{where} 里的 id 有重复")
    by_id = {s.id: s for s in specs if s.id}
    for spec in specs:
        for other in spec.concurrent:
            if spec.id is None:
                raise CorpusError(f"{where}：带 concurrent 的运行说明必须有 id")
            if other == spec.id or other not in by_id:
                raise CorpusError(f"{where}：{spec.id} 的 concurrent 引用了不存在或自身的 id {other!r}")
            if spec.id not in by_id[other].concurrent:
                raise CorpusError(f"{where}：concurrent 关系必须对称（{spec.id} → {other} 缺少反向）")
    return tuple(specs)


def groups(specs: tuple[RunSpec, ...]) -> list[list[RunSpec]]:
    """按 concurrent 关系分组：互相引用的成为一组，其余各自一组；保持在数组里的先后顺序。"""
    by_id = {s.id: s for s in specs if s.id}
    seen: set[int] = set()
    result = []
    for spec in specs:
        if id(spec) in seen:
            continue
        members, pending = [], [spec]
        while pending:
            current = pending.pop()
            if id(current) in seen:
                continue
            seen.add(id(current))
            members.append(current)
            pending += [by_id[other] for other in current.concurrent]
        result.append(sorted(members, key=specs.index))
    return result


# ---------------------------------------------------------------------------------------------------------------------
# 比对

def _matches(actual: Any, expected: Any) -> bool:
    if isinstance(expected, dict):
        return all(_comparison(actual, kind, operand) for kind, operand in expected.items())
    # 字面值要求类型也一致：1 不等于 true。
    return type(actual) is type(expected) and actual == expected


def _comparison(actual: Any, kind: str, operand: Any) -> bool:
    if kind == "in":
        return any(_matches(actual, item) for item in operand)
    if isinstance(actual, bool) or not isinstance(actual, (int, float)):
        return False
    return {"gt": actual > operand, "ge": actual >= operand, "lt": actual < operand, "le": actual <= operand}[kind]


def _describe(expected: Any) -> str:
    if isinstance(expected, dict):
        return " 且 ".join(f"{kind} {operand}" for kind, operand in expected.items())
    return repr(expected)


def _assertion_diffs(assertion: Assertion, result: RunResult) -> list[str]:
    diffs = []
    for name, expected in assertion.facts.items():
        if name not in result.facts:
            diffs.append(f"事实 {name}：预期 {_describe(expected)}，执行器没有给出")
        elif not _matches(result.facts[name], expected):
            diffs.append(f"事实 {name}：预期 {_describe(expected)}，实际 {result.facts[name]!r}")
    for label, check, text in (("stdout", assertion.stdout, result.stdout), ("stderr", assertion.stderr, result.stderr)):
        if check is not None and not check.matches(text):
            diffs.append(f"{label} 应 {check.describe()}，实际 {text[:120]!r}")
    return diffs


def evaluate(spec: RunSpec, result: RunResult) -> list[str]:
    """运行说明与一次观测的差异；空列表表示符合预期。"""
    if spec.any_of:
        attempts = [_assertion_diffs(a, result) for a in spec.any_of]
        diffs = [] if any(not a for a in attempts) else [
            "没有满足 anyOf 的任何一项：" + "；".join(f"[{'，'.join(a)}]" for a in attempts)]
    else:
        assert spec.assertion is not None
        diffs = _assertion_diffs(spec.assertion, result)
    if spec.elapsed_ms_lt is not None and not result.elapsed_ms < spec.elapsed_ms_lt:
        diffs.append(f"耗时应小于 {spec.elapsed_ms_lt} 毫秒，实际 {result.elapsed_ms:.0f} 毫秒")
    return diffs


def expected_summary(spec: RunSpec) -> dict[str, Any]:
    """报告里的「预期」：断言里提到的事实字段与取值。"""
    if spec.any_of:
        return {"anyOf": [dict(a.facts) for a in spec.any_of]}
    assert spec.assertion is not None
    return dict(spec.assertion.facts)


def actual_summary(spec: RunSpec, result: RunResult) -> dict[str, Any]:
    names = set()
    for assertion in (spec.any_of or ((spec.assertion,) if spec.assertion else ())):
        names |= set(assertion.facts)
    return {name: result.facts[name] for name in sorted(names) if name in result.facts}
