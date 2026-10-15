"""测试用的假 judge：按语料里的预期应答，再让各个测试按需改坏。不是测试文件（不以 _test 结尾）。"""

from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable

from corpus import Corpus, Problem

Respond = Callable[[dict[str, Any]], tuple[int, Any]]


def testcase_row(index: int, name: str, verdict: str, with_diff: bool = False) -> dict[str, Any]:
    row: dict[str, Any] = {"idx": index, "name": name, "verdict": verdict, "cpuNs": 1_000_000, "memoryBytes": 1 << 20}
    if verdict == "WA" and with_diff:
        row["diff"] = {"line": 1, "got": "x"}
    return row


def result(problem: Problem, verdict: str, testcases: tuple[str, ...], message: str | None = None) -> dict[str, Any]:
    payload: dict[str, Any] = {"verdict": verdict, "score": 100 if verdict == "AC" else 0}
    if message is not None:
        payload["message"] = message
    if testcases:
        payload["testDataDigest"] = problem.digest
        payload["testcaseResults"] = [testcase_row(i, n, v, True)
                                      for i, (n, v) in enumerate(zip(problem.testcase_names, testcases), start=1)]
    return payload


def ideal(problem: Problem, location: Path, strict_whitespace: bool = False) -> Respond:
    """一个行为完全符合语料预期的 judge；`strict_whitespace` 是它自己的节点配置。

    whitespace 的 _loose 与 _strict 源码相同，真实 judge 靠配置给出不同结论，所以这里只让与配置相符的那一半参与分派。
    """
    wrong_config = f"strictWhitespace:{'false' if strict_whitespace else 'true'}"
    by_source = {s.source: s for s in problem.submissions.values() if wrong_config not in s.requires}
    trials = {(c.source and problem.submissions[c.source].source, tuple((t.input, t.expected) for t in c.testcases)): c
              for c in problem.trials.values()}

    def respond(request: dict[str, Any]) -> tuple[int, Any]:
        language = request["languageId"]
        if language == "python":
            return 200, {"verdict": "SE", "message": "run: init failed: phase=6 errno=2"}
        if language != "cpp":
            return 200, {"verdict": "SE", "message": f'unknown language: "{language}"'}
        if request["mode"] == "trial":
            case = trials[(request["source"], tuple((t["input"], t.get("expected")) for t in request["testcases"]))]
            rows = []
            for index, t in enumerate(case.testcases, start=1):
                row = testcase_row(index, t.name, t.verdict, True)
                row["stderr"] = {"excerpt": "", "bytes": 0}
                if t.verdict in ("RAN", "AC", "WA"):
                    row["output"] = {"excerpt": "3\n", "bytes": 2}
                rows.append(row)
            return 200, {"verdict": case.verdict, "score": 0, "testcaseResults": rows}
        if request["testDataLocation"] != str(location):
            return 200, {"verdict": "SE", "message": "load testcases: unsupported test data location"}
        for name in (f"{n}{s}" for n in problem.testcase_names for s in (".in", ".out")):
            if (location / name).read_bytes() != (problem.testdata / name).read_bytes():
                return 200, {"verdict": "SE", "message": "testdata changed"}
        submission = by_source[request["source"]]
        message = "main.cpp: error: BENCHMARK_EXPECTED_COMPILE_ERROR" if submission.verdict == "CE" else None
        return 200, result(problem, submission.verdict, submission.testcases, message)

    return respond


def ideal_attack(corpus: Corpus, root: Path) -> Respond:
    """攻击样本的理想应答：满足样本 judge 入口的预期；干净探针得到 AC 且内存很小。"""
    samples = {f"attack-{s.name}": s for s in corpus.attack_samples}
    common = corpus.attack_common

    def respond(request: dict[str, Any]) -> tuple[int, Any]:
        if request["testDataLocation"] != str(root / "attack-common"):
            return 200, {"verdict": "SE", "message": "unsupported test data location"}
        rows = [{"idx": 1, "name": "1", "verdict": "AC", "cpuNs": 1, "memoryBytes": 1 << 20}]
        if request["problemId"] == "attack-probe":
            return 200, {"verdict": "AC", "score": 100, "memoryBytes": 1 << 20, "testDataDigest": common.digest,
                         "testcaseResults": rows}
        kind, value = samples[request["problemId"]].judge
        verdict = value if kind == "verdict" else "WA"
        rows[0]["verdict"] = verdict
        return 200, {"verdict": verdict, "score": 0, "testDataDigest": common.digest, "testcaseResults": rows}
    return respond


def ideal_corpus(corpus: Corpus, root: Path, strict_whitespace: bool = False) -> Respond:
    """行为完全符合整个语料预期的 judge：按请求里的 problemId 分派到各题和攻击样本。"""
    responders = {name: ideal(problem, root / name, strict_whitespace) for name, problem in corpus.problems.items()}
    attack = ideal_attack(corpus, root) if corpus.attack_common else None
    return lambda request: (attack(request) if request["problemId"].startswith("attack-")
                            else responders[request["problemId"]](request))


class FakeJudge:
    """在本机随机端口起一个 HTTP 服务，应答由 respond 决定；记录收到的每个判题请求。"""

    def __init__(self, respond: Respond, version: str = "0.0.0-fake") -> None:
        self.respond, self.version, self.requests = respond, version, []
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args: Any) -> None:  # 不往测试输出里写访问日志
                pass

            def _send(self, status: int, payload: Any) -> None:
                data = payload.encode() if isinstance(payload, str) else json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_GET(self) -> None:
                if self.path == "/version":
                    self._send(200, {"name": "cherry-oj-judge", "version": outer.version})
                else:
                    self._send(404, {"error": "not found"})

            def do_POST(self) -> None:
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                outer.requests.append(body)
                status, payload = outer.respond(body)
                self._send(status, payload)

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    @property
    def url(self) -> str:
        return f"http://127.0.0.1:{self.server.server_address[1]}"

    def __enter__(self) -> "FakeJudge":
        self.thread.start()
        return self

    def __exit__(self, *exc: object) -> None:
        self.server.shutdown()
        self.server.server_close()
