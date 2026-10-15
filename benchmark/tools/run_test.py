"""入口脚本的端到端测试：run.main 对假 judge 运行，检查退出码与报告（设计 §7）。"""

import json
import shutil
import socket
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # run.py 在 benchmark/ 下
import corpus as lib  # noqa: E402
import fake_judge  # noqa: E402
import run  # noqa: E402

REAL = Path(__file__).resolve().parents[1] / "corpus"


class RunTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.published = self.root / "published"
        self.report_path = self.root / "report.json"
        self.corpus = lib.load_corpus(REAL)
        self.problem = self.corpus.problems["a-plus-b"]
        self.location = self.published.resolve() / "a-plus-b"

    def go(self, judge_url: str, *extra: str, corpus: Path = REAL, select: str | None = "a-plus-b",
           declare: tuple[str, ...] = ("backend=linux", "languages=cpp")) -> tuple[int, str, str]:
        argv = ["--judge-url", judge_url, "--published-root", str(self.published), "--corpus", str(corpus),
                "--report", str(self.report_path), "--declare", *declare, *extra]
        if select:
            argv += ["--select", select]
        out, err = StringIO(), StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = run.main(argv)
        return code, out.getvalue(), err.getvalue()

    def report(self):
        return json.loads(self.report_path.read_text(encoding="utf-8"))

    def statuses(self, problem: str = "a-plus-b") -> dict[str, str]:
        report = next(p for p in self.report()["judge"] if p["name"] == problem)
        return {item["name"]: item["status"] for kind in ("submissions", "requests", "trials") for item in report[kind]}

    def test_all_as_expected_exits_zero_and_reports(self):
        with fake_judge.FakeJudge(fake_judge.ideal(self.problem, self.location)) as judge:
            code, out, err = self.go(judge.url)
        self.assertEqual((code, err), (0, ""))
        report = self.report()
        self.assertEqual(report["schemaVersion"], 1)
        self.assertEqual(report["environment"]["judgeVersion"], "0.0.0-fake")
        self.assertEqual(report["environment"]["declared"]["backend"], "linux")
        self.assertEqual(report["environment"]["corpusDigest"], lib.corpus_digest(self.corpus.digests()))
        self.assertIsNone(report["environment"]["kernel"])
        self.assertEqual(report["summary"]["judge"]["failed"], 0)
        self.assertEqual(report["summary"]["judge"]["ran"], 18)
        self.assertEqual(set(self.statuses().values()), {"pass"})
        self.assertIn("攻击样本：0，未覆盖沙箱安全", out)

    def test_time_class_submissions_repeat_three_times(self):
        with fake_judge.FakeJudge(fake_judge.ideal(self.problem, self.location)) as judge:
            self.go(judge.url)
            tle = [r for r in judge.requests if r["source"] == self.problem.submissions["tle_loop"].source]
            ac = [r for r in judge.requests if r["source"] == self.problem.submissions["ac_iostream"].source
                  and r["mode"] == "submit"]
        self.assertEqual(len(tle), 3)
        self.assertEqual(len(ac), 5)  # ac_iostream 本身，加上请求级用例里借用它的源码的 4 个
        self.assertEqual({r["limits"]["clockNs"] for r in tle}, {3_000_000_000})

    def test_a_wrong_verdict_exits_one_with_the_difference(self):
        ideal = fake_judge.ideal(self.problem, self.location)
        wa_source = self.problem.submissions["wa_minus"].source

        def respond(request):
            if request["mode"] == "submit" and request["source"] == wa_source:
                return 200, fake_judge.result(self.problem, "AC", ("AC", "AC", "AC"))
            return ideal(request)
        with fake_judge.FakeJudge(respond) as judge:
            code, out, _ = self.go(judge.url)
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses()["wa_minus"], "fail")
        diffs = next(i for i in self.report()["judge"][0]["submissions"] if i["name"] == "wa_minus")["diffs"]
        self.assertTrue(any("预期 WA，实际 AC" in d for d in diffs), diffs)
        self.assertIn("wa_minus", out)

    def test_flaky_time_class_submission_is_unstable_and_exits_two(self):
        ideal = fake_judge.ideal(self.problem, self.location)
        tle_source = self.problem.submissions["tle_loop"].source
        calls = []

        def respond(request):
            if request["mode"] == "submit" and request["source"] == tle_source:
                calls.append(1)
                if len(calls) == 2:
                    return 200, fake_judge.result(self.problem, "AC", ("AC", "AC", "AC"))
            return ideal(request)
        with fake_judge.FakeJudge(respond) as judge:
            code, _, _ = self.go(judge.url)
        self.assertEqual(code, 2)
        self.assertEqual(self.statuses()["tle_loop"], "unstable")

    def test_unreachable_judge_is_an_environment_error(self):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        code, _, err = self.go(f"http://127.0.0.1:{port}")
        self.assertEqual(code, 3)
        self.assertIn("环境或配置错误", err)

    def test_corpus_drifting_from_the_lock_is_an_environment_error(self):
        root = self.root / "corpus"
        shutil.copytree(REAL, root)
        lock = json.loads((root / "corpus.lock").read_text(encoding="utf-8"))
        lock["problems"]["a-plus-b"] = "0" * 64
        (root / "corpus.lock").write_text(json.dumps(lock), encoding="utf-8")
        with fake_judge.FakeJudge(fake_judge.ideal(self.problem, self.location)) as judge:
            code, _, err = self.go(judge.url, corpus=root)
        self.assertEqual(code, 3)
        self.assertIn("corpus.lock", err)
        self.assertEqual(judge.requests, [])

    def test_unmet_requires_are_skipped_not_failed(self):
        with fake_judge.FakeJudge(fake_judge.ideal(self.problem, self.location)) as judge:
            code, out, _ = self.go(judge.url, declare=("backend=linux", "languages=cpp,python"))
        self.assertEqual(code, 0)
        self.assertEqual(self.statuses()["language_not_installed"], "skipped")
        item = next(i for i in self.report()["judge"][0]["requests"] if i["name"] == "language_not_installed")
        self.assertIn("noLanguage:python", item["reason"])
        self.assertIn("跳过", out)
        self.assertEqual(self.report()["summary"]["judge"]["skipped"], 1)

    def test_undeclared_configuration_is_never_guessed(self):
        argv = ["--judge-url", "x", "--published-root", str(self.published), "--corpus", str(REAL),
                "--report", str(self.report_path), "--select", "a-plus-b"]
        with fake_judge.FakeJudge(fake_judge.ideal(self.problem, self.location)) as judge:
            argv[1] = judge.url
            with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                self.assertEqual(run.main(argv), 0)
        self.assertEqual(self.statuses()["language_not_installed"], "skipped")

    def run_everything(self, *declare: str) -> dict:
        strict = "strictWhitespace=true" in declare
        with fake_judge.FakeJudge(fake_judge.ideal_corpus(self.corpus, self.published.resolve(), strict)) as judge:
            code, _, err = self.go(judge.url, select=None, declare=("backend=linux", "languages=cpp", *declare))
        self.assertEqual((code, err), (0, ""))
        return self.report()["summary"]["judge"]

    def test_whole_corpus_runs_the_half_of_whitespace_the_declaration_selects(self):
        # 50 项 = 42 个提交 + 5 个请求级 + 3 个试运行；whitespace 的 8 个 _loose/_strict 只有一半会运行，探测项另计。
        self.assertEqual(self.run_everything(), dict(ran=41, passed=41, failed=0, unstable=0, skipped=8, probes=1))
        self.assertEqual(self.run_everything("strictWhitespace=false"),
                         dict(ran=45, passed=45, failed=0, unstable=0, skipped=4, probes=1))
        self.assertEqual(self.run_everything("strictWhitespace=true"),
                         dict(ran=45, passed=45, failed=0, unstable=0, skipped=4, probes=1))
        self.assertEqual(self.statuses("whitespace")["ws_crlf_strict"], "pass")

    def test_linux_only_problems_are_skipped_without_the_declaration(self):
        with fake_judge.FakeJudge(fake_judge.ideal_corpus(self.corpus, self.published.resolve())) as judge:
            code, _, _ = self.go(judge.url, select=None, declare=("languages=cpp",))
        self.assertEqual(code, 0)
        self.assertEqual(set(self.statuses("alloc-array").values()), {"skipped"})
        self.assertEqual(set(self.statuses("big-output").values()), {"skipped"})

    def test_select_runs_only_the_named_problem(self):
        with fake_judge.FakeJudge(fake_judge.ideal(self.problem, self.location)) as judge:
            code, _, _ = self.go(judge.url, select="a-plus-b")
            self.assertEqual(code, 0)
            code, _, err = self.go(judge.url, select="nope")
        self.assertEqual(code, 3)
        self.assertIn("没有这些题", err)

    def test_tampering_only_touches_the_published_copy_and_restores_it(self):
        seen = {}
        ideal = fake_judge.ideal(self.problem, self.location)
        source = self.problem.submissions["ac_iostream"].source

        def respond(request):
            if request["mode"] == "submit" and request["source"] == source and "tampered" in (
                    self.location / "1.in").read_text(encoding="utf-8"):
                seen["during"] = (self.location / "1.in").read_bytes()
            return ideal(request)
        with fake_judge.FakeJudge(respond) as judge:
            code, _, _ = self.go(judge.url)
        self.assertEqual(code, 0)
        self.assertIn(b"tampered", seen["during"])
        self.assertEqual((self.location / "1.in").read_bytes(), (self.problem.testdata / "1.in").read_bytes())
        self.assertNotIn(b"tampered", (REAL / "judge" / "a-plus-b" / "testdata" / "1.in").read_bytes())


if __name__ == "__main__":
    unittest.main()
