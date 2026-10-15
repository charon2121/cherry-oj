"""judge 入口：对每个提交、请求级用例、试运行用例发起 `POST /judge`，与预期逐项比对（协议 §7.1）。"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import publish
from corpus import Check, Problem, RequestCase, Submission, TrialCase
from judge_client import ClientError, JudgeClient

DEFAULT_REPEATS = 3
# 预期含这些结论的提交属于时间、内存类，默认重复运行以判定稳定性（协议 §7.3）。
REPEATED_VERDICTS = frozenset({"TLE", "MLE", "OLE"})


@dataclass(frozen=True)
class Declared:
    """运行者声明的节点配置（协议 §4）。没有声明的项就是未知，依赖它的项会被跳过，而不是去猜。"""

    backend: str | None = None
    strict_whitespace: bool | None = None
    reveal_expected: bool = False
    languages: tuple[str, ...] | None = None
    entries: tuple[str, ...] = ("judge",)

    def to_report(self) -> dict[str, Any]:
        return {"backend": self.backend, "strictWhitespace": self.strict_whitespace,
                "revealExpected": self.reveal_expected,
                "languages": list(self.languages) if self.languages is not None else None,
                "entries": list(self.entries)}

    def unmet(self, tags: tuple[str, ...]) -> str | None:
        """第一个不满足的标签的原因；全部满足返回 None。"""
        for tag in tags:
            kind, _, value = tag.partition(":")
            if kind == "backend":
                if self.backend != value:
                    return f"需要 {tag}，运行者声明的 backend 是 {self.backend!r}"
            elif kind == "strictWhitespace":
                if self.strict_whitespace is None or self.strict_whitespace != (value == "true"):
                    return f"需要 {tag}，运行者声明的 strictWhitespace 是 {self.strict_whitespace!r}"
            elif kind == "entry":
                if value not in self.entries:
                    return f"需要 {tag}，本次运行的入口是 {', '.join(self.entries)}"
            elif kind == "noLanguage":
                if self.languages is None:
                    return f"需要 {tag}，但运行者没有声明 languages"
                if value in self.languages:
                    return f"需要 {tag}，但节点声明有 {value}"
        return None


def parse_declared(pairs: list[str], entries: tuple[str, ...]) -> Declared:
    values: dict[str, str] = {}
    for pair in pairs:
        key, separator, value = pair.partition("=")
        if not separator or not key:
            raise ValueError(f"--declare 的值必须是 key=value，收到 {pair!r}")
        values[key] = value

    def boolean(key: str) -> bool | None:
        if key not in values:
            return None
        if values[key] not in ("true", "false"):
            raise ValueError(f"--declare {key} 必须是 true 或 false")
        return values[key] == "true"

    unknown = set(values) - {"backend", "strictWhitespace", "revealExpected", "languages"}
    if unknown:
        raise ValueError(f"--declare 不认识 {', '.join(sorted(unknown))}")
    return Declared(
        backend=values.get("backend"), strict_whitespace=boolean("strictWhitespace"),
        reveal_expected=bool(boolean("revealExpected")),
        languages=tuple(v for v in values["languages"].split(",") if v) if "languages" in values else None,
        entries=entries)


@dataclass
class Item:
    kind: str  # submission | request | trial
    name: str
    status: str = "pass"  # pass | fail | unstable | skipped | probe
    expected: str | None = None
    actual: str | None = None
    repeats: int = 1
    reason: str | None = None
    diffs: list[str] = field(default_factory=list)
    testcases: list[dict[str, Any]] = field(default_factory=list)

    def to_report(self) -> dict[str, Any]:
        report: dict[str, Any] = {"name": self.name, "entry": "judge", "status": self.status,
                                  "expected": self.expected, "actual": self.actual, "repeats": self.repeats}
        if self.reason:
            report["reason"] = self.reason
        if self.diffs:
            report["diffs"] = self.diffs
        if self.testcases:
            report["testcases"] = self.testcases
        return report


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


@dataclass
class Outcome:
    """一次 POST /judge 的结果；error 非空表示没拿到合法响应。"""

    http_status: int | None = None
    payload: Any = None
    error: str | None = None

    @property
    def result(self) -> dict[str, Any]:
        return self.payload if isinstance(self.payload, dict) else {}

    @property
    def verdict(self) -> str:
        if self.error:
            return "ERROR"
        if self.http_status != 200:
            return f"HTTP {self.http_status}"
        return str(self.result.get("verdict"))

    @property
    def testcase_results(self) -> list[dict[str, Any]]:
        rows = self.result.get("testcaseResults") or []
        return sorted((r for r in rows if isinstance(r, dict)), key=lambda r: r.get("idx", 0))

    @property
    def signature(self) -> tuple[str, tuple[str, ...]]:
        return self.verdict, tuple(str(r.get("verdict")) for r in self.testcase_results)

    def failure(self) -> str | None:
        if self.error:
            return f"请求失败：{self.error}"
        if self.http_status != 200:
            return f"HTTP {self.http_status}：{shorten(self.payload)}"
        return None


def shorten(value: Any, limit: int = 300) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    return text if len(text) <= limit else text[:limit] + "…"


class JudgeRunner:
    def __init__(self, client: JudgeClient, published_root: Path, declared: Declared,
                 repeats: int = DEFAULT_REPEATS, run_id: str = "run") -> None:
        self.client, self.root, self.declared = client, published_root, declared
        self.repeats, self.run_id, self.counter = repeats, run_id, 0

    # ----- 请求构造 -----

    def _submission_id(self) -> str:
        self.counter += 1
        return f"bench-{self.run_id}-{self.counter}"

    def _timeout(self, clock_ns: int, testcases: int) -> float:
        return (testcases + 1) * clock_ns / 1e9 + 60

    def _call(self, request: dict[str, Any], testcases: int) -> Outcome:
        timeout = self._timeout(request["limits"].get("clockNs", 3_000_000_000), testcases)
        try:
            status, payload = self.client.judge(request, timeout)
        except ClientError as error:
            return Outcome(error=str(error))
        return Outcome(http_status=status, payload=payload)

    # ----- 比对 -----

    def _leaks(self, outcome: Outcome) -> list[str]:
        """未声明 revealExpected 时，WA 的 diff 不得含 want（协议 §7.1）。"""
        if self.declared.reveal_expected:
            return []
        return [f"测试点 {row.get('idx')} 的 diff 带出了标准答案（want）"
                for row in outcome.testcase_results
                if row.get("verdict") == "WA" and isinstance(row.get("diff"), dict) and "want" in row["diff"]]

    def _compare(self, outcome: Outcome, verdict: str, testcases: tuple[str, ...] | None,
                 message: Check | None, body: Check | None, digest: str | None) -> list[str]:
        failure = outcome.failure()
        if failure:
            return [failure]
        diffs = []
        if outcome.verdict != verdict:
            diffs.append(f"整体 verdict：预期 {verdict}，实际 {outcome.verdict}")
        if testcases is not None:
            actual = [str(r.get("verdict")) for r in outcome.testcase_results]
            if len(actual) != len(testcases):
                diffs.append(f"逐点结果个数：预期 {len(testcases)}，实际 {len(actual)}")
            else:
                diffs += [f"测试点 {i}：预期 {want}，实际 {got}"
                          for i, (want, got) in enumerate(zip(testcases, actual), start=1) if want != got]
        if message is not None:
            text = outcome.result.get("message")
            if not isinstance(text, str) or not message.matches(text):
                diffs.append(f"message 应 {message.describe()}，实际 {shorten(text)}")
        if body is not None:
            text = json.dumps(outcome.payload, ensure_ascii=False)
            if not body.matches(text):
                diffs.append(f"响应正文应 {body.describe()}，实际 {shorten(text)}")
        if digest is not None and outcome.testcase_results and outcome.result.get("testDataDigest") != digest:
            diffs.append(f"testDataDigest：预期 {digest[:12]}…，实际 {shorten(outcome.result.get('testDataDigest'), 20)}")
        return diffs + self._leaks(outcome)

    @staticmethod
    def _testcase_rows(outcome: Outcome, expected: tuple[str, ...] | None) -> list[dict[str, Any]]:
        rows = []
        for position, row in enumerate(outcome.testcase_results):
            want = expected[position] if expected is not None and position < len(expected) else None
            rows.append({"idx": row.get("idx"), "expected": want, "actual": row.get("verdict"),
                         "cpuNs": row.get("cpuNs"), "memoryBytes": row.get("memoryBytes")})
        return rows

    def _settle(self, item: Item, outcomes: list[Outcome], diffs_per_run: list[list[str]],
                expected_testcases: tuple[str, ...] | None) -> Item:
        """多遍运行汇总成一个状态：遍与遍之间不一致是 unstable，一致但与预期不符是 fail。"""
        item.repeats = len(outcomes)
        last = outcomes[-1]
        item.actual = last.verdict
        item.testcases = self._testcase_rows(last, expected_testcases)
        signatures = {o.signature for o in outcomes}
        if len(signatures) > 1:
            item.status = "unstable"
            item.diffs = [f"第 {i} 遍：{o.verdict} {list(o.signature[1])}" for i, o in enumerate(outcomes, start=1)]
        elif any(diffs_per_run):
            item.status = "fail"
            item.diffs = next(d for d in diffs_per_run if d)
        else:
            item.status = "pass"
        return item

    # ----- 三类用例 -----

    def run_problem(self, problem: Problem) -> ProblemResult:
        location = publish.publish(problem, self.root)
        result = ProblemResult(problem.name, problem.digest)
        blocked = publish.untraversable(location)
        if blocked:
            result.warnings.append(
                f"发布位置的这些目录缺少「其他用户可进入」权限，judge 若以别的身份运行会读不到：{', '.join(map(str, blocked))}")
        for submission in problem.submissions.values():
            result.items.append(self.run_submission(problem, location, submission))
        for case in problem.requests.values():
            result.items.append(self.run_request(problem, location, case))
        for trial in problem.trials.values():
            result.items.append(self.run_trial(problem, trial))
        return result

    def _submit_request(self, problem: Problem, source: str, language: str, location: Path | str,
                        limits: dict[str, int] | None = None) -> dict[str, Any]:
        return {"submissionId": self._submission_id(), "problemId": problem.name, "mode": "submit",
                "testDataLocation": str(location), "languageId": language, "source": source,
                "limits": problem.limits.request(limits)}

    def run_submission(self, problem: Problem, location: Path, submission: Submission) -> Item:
        item = Item("submission", submission.name, expected=submission.verdict)
        reason = self.declared.unmet(problem.requires + submission.requires)
        if reason:
            item.status, item.reason = "skipped", reason
            return item
        timeboxed = submission.verdict in REPEATED_VERDICTS or bool(REPEATED_VERDICTS & set(submission.testcases))
        runs = self.repeats if timeboxed else 1
        request = self._submit_request(problem, submission.source, submission.language, location)
        outcomes, diffs = [], []
        for _ in range(runs):
            outcome = self._call(request, len(problem.testcase_names))
            outcomes.append(outcome)
            diffs.append(self._compare(outcome, submission.verdict, submission.testcases, submission.message,
                                       None, problem.digest))
        item = self._settle(item, outcomes, diffs, submission.testcases)
        if submission.probe:
            item.status, item.diffs = "probe", []
        return item

    def run_request(self, problem: Problem, location: Path, case: RequestCase) -> Item:
        item = Item("request", case.name, expected=case.verdict)
        reason = self.declared.unmet(problem.requires + case.requires)
        if reason:
            item.status, item.reason = "skipped", reason
            return item
        submission = problem.submissions[case.source] if case.source else None
        source = submission.source if submission else case.source_text or ""
        language = case.language_id or (submission.language if submission else "cpp")
        target = case.test_data_location if case.test_data_location is not None else location
        request = self._submit_request(problem, source, language, target, case.limits)
        if case.tamper:
            with publish.tampered(location, case.tamper["file"], case.tamper["appendText"]):
                outcome = self._call(request, len(problem.testcase_names))
        else:
            outcome = self._call(request, len(problem.testcase_names))
        diffs = self._compare(outcome, case.verdict, None, case.message, case.body, None)
        return self._settle(item, [outcome], [diffs], None)

    def run_trial(self, problem: Problem, trial: TrialCase) -> Item:
        item = Item("trial", trial.name, expected=trial.verdict)
        reason = self.declared.unmet(problem.requires + trial.requires)
        if reason:
            item.status, item.reason = "skipped", reason
            return item
        submission = problem.submissions[trial.source] if trial.source else None
        source = submission.source if submission else trial.source_text or ""
        language = submission.language if submission else "cpp"
        request = {"submissionId": self._submission_id(), "problemId": problem.name, "mode": "trial",
                   "languageId": language, "source": source, "limits": problem.limits.request(trial.limits),
                   "testcases": [{"name": c.name, "input": c.input, **({"expected": c.expected} if c.expected is not None else {})}
                                 for c in trial.testcases]}
        outcome = self._call(request, len(trial.testcases))
        expected = tuple(c.verdict for c in trial.testcases)
        diffs = self._compare(outcome, trial.verdict, expected, None, None, None)
        if not outcome.failure():
            for case, row in zip(trial.testcases, outcome.testcase_results):
                for label, check in (("output", case.output), ("stderr", case.stderr)):
                    if check is None:
                        continue
                    excerpt = (row.get(label) or {}).get("excerpt") if isinstance(row.get(label), dict) else None
                    if not isinstance(excerpt, str) or not check.matches(excerpt):
                        diffs.append(f"测试点 {case.name} 的 {label} 摘要应 {check.describe()}，实际 {shorten(excerpt)}")
        return self._settle(item, [outcome], [diffs], expected)
