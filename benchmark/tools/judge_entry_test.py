import os
import stat
import tempfile
import unittest
from pathlib import Path

import corpus as lib
import fake_judge
import publish
import report
from judge_client import JudgeClient
from judge_entry import Declared, JudgeRunner, parse_declared

REAL = Path(__file__).resolve().parents[1] / "corpus"


class DeclaredTest(unittest.TestCase):
    def test_parse(self):
        declared = parse_declared(["backend=linux", "strictWhitespace=true", "languages=cpp,python"], ("judge",))
        self.assertEqual((declared.backend, declared.strict_whitespace, declared.languages),
                         ("linux", True, ("cpp", "python")))
        self.assertFalse(declared.reveal_expected)

    def test_parse_rejects_bad_input(self):
        for bad in (["backend"], ["strictWhitespace=maybe"], ["colour=red"]):
            with self.assertRaises(ValueError):
                parse_declared(bad, ("judge",))

    def test_requires_are_checked_against_declarations_only(self):
        none = Declared()
        self.assertIn("backend", none.unmet(("backend:linux",)))
        self.assertIsNone(Declared(backend="linux").unmet(("backend:linux",)))
        self.assertIn("backend", Declared(backend="devhost").unmet(("backend:linux",)))
        self.assertIn("strictWhitespace", none.unmet(("strictWhitespace:true",)))
        self.assertIsNone(Declared(strict_whitespace=True).unmet(("strictWhitespace:true",)))
        self.assertIn("strictWhitespace", Declared(strict_whitespace=False).unmet(("strictWhitespace:true",)))
        self.assertIn("languages", none.unmet(("noLanguage:python",)))
        self.assertIsNone(Declared(languages=("cpp",)).unmet(("noLanguage:python",)))
        self.assertIn("python", Declared(languages=("cpp", "python")).unmet(("noLanguage:python",)))
        self.assertIn("entry:sandbox", Declared().unmet(("entry:sandbox",)))


class RunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve() / "published"
        self.problem = lib.load_corpus(REAL).problems["a-plus-b"]
        self.location = self.root / "a-plus-b"
        self.ideal = fake_judge.ideal(self.problem, self.location)

    def run_with(self, respond, declared=None, only=None):
        declared = declared or Declared(backend="linux", languages=("cpp",))
        with fake_judge.FakeJudge(respond) as judge:
            runner = JudgeRunner(JudgeClient(judge.url), self.root, declared, 3, "t")
            result = runner.run_problem(self.problem)
        return {item.name: item for item in result.items if only is None or item.name in only}

    def test_standard_answer_must_not_leak_unless_declared(self):
        wa_source = self.problem.submissions["wa_minus"].source

        def leaky(request):
            status, payload = self.ideal(request)
            if request["mode"] == "submit" and request["source"] == wa_source:
                for row in payload["testcaseResults"]:
                    if row["verdict"] == "WA":
                        row["diff"]["want"] = "8"
            return status, payload
        items = self.run_with(leaky, only={"wa_minus"})
        self.assertEqual(items["wa_minus"].status, "fail")
        self.assertTrue(any("want" in d for d in items["wa_minus"].diffs))
        revealing = Declared(backend="linux", languages=("cpp",), reveal_expected=True)
        self.assertEqual(self.run_with(leaky, revealing, {"wa_minus"})["wa_minus"].status, "pass")

    def test_test_data_digest_must_be_the_published_one(self):
        def stale(request):
            status, payload = self.ideal(request)
            if request["mode"] == "submit" and "testDataDigest" in payload:
                payload["testDataDigest"] = "0" * 64
            return status, payload
        items = self.run_with(stale, only={"ac_iostream"})
        self.assertEqual(items["ac_iostream"].status, "fail")
        self.assertTrue(any("testDataDigest" in d for d in items["ac_iostream"].diffs))

    def test_non_200_is_a_failure_with_the_status(self):
        items = self.run_with(lambda request: (500, {"error": "boom"}), only={"ac_iostream", "unknown_language"})
        for item in items.values():
            self.assertEqual(item.status, "fail")
            self.assertTrue(any("HTTP 500" in d for d in item.diffs))

    def test_message_assertion(self):
        def silent(request):
            status, payload = self.ideal(request)
            payload.pop("message", None)
            return status, payload
        items = self.run_with(silent, only={"ce_marker", "unknown_language"})
        self.assertEqual(items["ce_marker"].status, "fail")
        self.assertEqual(items["unknown_language"].status, "fail")

    def test_wrong_number_of_testcase_results(self):
        def short(request):
            status, payload = self.ideal(request)
            if request["mode"] == "submit" and payload.get("testcaseResults"):
                payload["testcaseResults"] = payload["testcaseResults"][:1]
            return status, payload
        items = self.run_with(short, only={"ac_iostream"})
        self.assertTrue(any("逐点结果个数" in d for d in items["ac_iostream"].diffs))

    def test_trial_output_assertion(self):
        def wrong_output(request):
            status, payload = self.ideal(request)
            if request["mode"] == "trial":
                for row in payload["testcaseResults"]:
                    row["output"] = {"excerpt": "4\n", "bytes": 2}
            return status, payload
        items = self.run_with(wrong_output, only={"trial_ran"})
        self.assertEqual(items["trial_ran"].status, "fail")
        self.assertTrue(any("output" in d for d in items["trial_ran"].diffs))

    def test_probe_items_never_fail(self):
        problem = self.problem
        sub = problem.submissions["wa_minus"]
        probe = lib.Submission(**{**sub.__dict__, "probe": True})
        problem.submissions["wa_minus"] = probe  # 字典是可变的，frozen 只约束 Problem 的字段重绑定
        self.addCleanup(problem.submissions.__setitem__, "wa_minus", sub)
        items = self.run_with(lambda request: (200, {"verdict": "AC"}), only={"wa_minus"})
        self.assertEqual(items["wa_minus"].status, "probe")
        self.assertEqual(items["wa_minus"].diffs, [])


class ReportTest(unittest.TestCase):
    def test_exit_code_prefers_failure_over_unstable(self):
        from judge_entry import Item, ProblemResult

        def of(*statuses):
            return [ProblemResult("p", "d", [Item("submission", f"s{i}", status=s) for i, s in enumerate(statuses)])]
        self.assertEqual(report.exit_code(of("pass", "skipped", "probe")), 0)
        self.assertEqual(report.exit_code(of("pass", "unstable")), 2)
        self.assertEqual(report.exit_code(of("unstable", "fail")), 1)


class PublishTest(unittest.TestCase):
    def test_permissions_and_marker_order(self):
        problem = lib.load_corpus(REAL).problems["a-plus-b"]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve() / "pub"
            location = publish.publish(problem, root)
            self.assertEqual(stat.S_IMODE(location.stat().st_mode), 0o755)
            for path in location.iterdir():
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o644)
            self.assertEqual(sorted(p.name for p in location.iterdir()), sorted(p.name for p in problem.testdata.iterdir()))
            with self.assertRaises(publish.PublishError):
                publish.publish(problem, Path("relative"))

    def test_untraversable_directories_are_found(self):
        with tempfile.TemporaryDirectory() as temp:
            closed = Path(temp).resolve() / "closed"
            closed.mkdir()
            os.chmod(closed, 0o750)
            try:
                self.assertIn(closed, publish.untraversable(closed / "child"))
            finally:
                os.chmod(closed, 0o755)

    def test_tamper_restores_even_on_error(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "1.in"
            path.write_bytes(b"3 5\n")
            with self.assertRaises(RuntimeError):
                with publish.tampered(Path(temp), "1.in", "x"):
                    self.assertEqual(path.read_bytes(), b"3 5\nx")
                    raise RuntimeError
            self.assertEqual(path.read_bytes(), b"3 5\n")


if __name__ == "__main__":
    unittest.main()
