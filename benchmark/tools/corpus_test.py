import json
import shutil
import tempfile
import unittest
from pathlib import Path

import corpus as lib

REAL = Path(__file__).resolve().parents[1] / "corpus"


def copy_corpus() -> tuple[tempfile.TemporaryDirectory, Path]:
    temp = tempfile.TemporaryDirectory()
    root = Path(temp.name) / "corpus"
    shutil.copytree(REAL, root)
    return temp, root


def edit_json(path: Path, change) -> None:
    value = json.loads(path.read_text(encoding="utf-8"))
    change(value)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


class RealCorpusTest(unittest.TestCase):
    def test_loads_and_matches_lock(self):
        corpus = lib.load_corpus(REAL)
        self.assertIn("a-plus-b", corpus.problems)
        self.assertEqual(lib.verify_lock(corpus), [])
        problem = corpus.problems["a-plus-b"]
        self.assertEqual(problem.testcase_names, ("1", "2", "3"))
        self.assertEqual(len(problem.submissions), 10)
        self.assertEqual(sum(len(p.submissions) for p in corpus.problems.values()), 47)

    def test_core_problem_sends_driver_and_solution(self):
        problem = lib.load_corpus(REAL).problems["core-add"]
        self.assertIn('#include "solution.cpp"', problem.driver)
        solution = problem.submissions["ac_class"].source
        self.assertEqual(problem.sources(solution), {"source": problem.driver, "solutionSource": solution})
        acm = lib.load_corpus(REAL).problems["a-plus-b"]
        self.assertIsNone(acm.driver)
        self.assertEqual(acm.sources("x"), {"source": "x"})

    def test_covers_every_verdict_but_se_through_submissions(self):
        problem = lib.load_corpus(REAL).problems["a-plus-b"]
        seen = {s.verdict for s in problem.submissions.values()}
        self.assertTrue({"AC", "WA", "CE", "RE", "TLE"} <= seen)
        self.assertEqual({c.verdict for c in problem.requests.values()}, {"SE"})


class WhitespaceTwinsTest(unittest.TestCase):
    def test_loose_and_strict_variants_have_byte_identical_sources(self):
        problem = lib.load_corpus(REAL).problems["whitespace"]
        pairs = [name[:-len("_loose")] for name in problem.submissions if name.endswith("_loose")]
        self.assertEqual(len(pairs), 4)
        for base in pairs:
            self.assertEqual(problem.submissions[f"{base}_loose"].source, problem.submissions[f"{base}_strict"].source)
            self.assertEqual(problem.submissions[f"{base}_loose"].requires, ("strictWhitespace:false",))
            self.assertEqual(problem.submissions[f"{base}_strict"].requires, ("strictWhitespace:true",))
            self.assertEqual(problem.submissions[f"{base}_loose"].verdict, "AC")
            self.assertEqual(problem.submissions[f"{base}_strict"].verdict, "PE")


class DigestTest(unittest.TestCase):
    def test_corpus_digest_sorts_by_name_and_is_stable(self):
        a, b = "a" * 64, "b" * 64
        self.assertEqual(lib.corpus_digest({"zeta": b, "alpha": a}), lib.corpus_digest({"alpha": a, "zeta": b}))
        self.assertNotEqual(lib.corpus_digest({"alpha": a}), lib.corpus_digest({"alpha": b}))

    def test_lock_difference_names_the_problem(self):
        corpus = lib.load_corpus(REAL)
        tampered = lib.Corpus(corpus.root, corpus.problems,
                              lib.lock_document({"a-plus-b": "0" * 64}), corpus.attack_samples)
        self.assertTrue(any("a-plus-b" in line for line in lib.verify_lock(tampered)))
        stale = dict(corpus.lock, corpusDigest="0" * 64)
        stale_corpus = lib.Corpus(corpus.root, corpus.problems, stale, corpus.attack_samples)
        self.assertTrue(any("corpusDigest" in line for line in lib.verify_lock(stale_corpus)))


class RejectsBadCorpusTest(unittest.TestCase):
    def setUp(self):
        self.temp, self.root = copy_corpus()
        self.addCleanup(self.temp.cleanup)
        self.problem = self.root / "judge" / "a-plus-b"

    def assertRejected(self, text: str) -> None:
        with self.assertRaises(lib.CorpusError) as caught:
            lib.load_corpus(self.root)
        self.assertIn(text, str(caught.exception))

    def expect(self, change) -> None:
        edit_json(self.problem / "expect.json", change)

    def test_core_driver_must_exist(self):
        (self.root / "judge" / "core-add" / "driver.cpp").unlink()
        self.assertRejected("不存在")

    def test_core_driver_must_be_a_plain_file_name(self):
        edit_json(self.root / "judge" / "core-add" / "problem.json", lambda v: v.update(driver="../a-plus-b/x.cpp"))
        self.assertRejected("文件名")

    def test_core_submissions_cannot_run_in_the_sandbox_entry(self):
        edit_json(self.root / "judge" / "core-add" / "expect.json",
                  lambda v: v["submissions"]["ac_class"].update(sandbox=[{"testcase": "1", "facts": {"exitCode": 0}}]))
        self.assertRejected("不能写 sandbox")

    def test_unknown_problem_field(self):
        edit_json(self.problem / "problem.json", lambda v: v.update(extra=1))
        self.assertRejected("未知字段")

    def test_problem_name_must_match_directory(self):
        edit_json(self.problem / "problem.json", lambda v: v.update(name="other"))
        self.assertRejected("与目录名")

    def test_limits_must_be_positive(self):
        edit_json(self.problem / "problem.json", lambda v: v["limits"].update(cpuNs=0))
        self.assertRejected("正整数")

    def test_duplicate_json_keys(self):
        (self.problem / "problem.json").write_text('{"schemaVersion": 1, "schemaVersion": 1}', encoding="utf-8")
        self.assertRejected("重复的键")

    def test_submission_file_must_be_declared(self):
        self.expect(lambda v: v["submissions"].pop("ac_scanf"))
        self.assertRejected("一一对应")

    def test_unsupported_extension(self):
        (self.problem / "submissions" / "ac_c.c").write_text("int main(){}", encoding="utf-8")
        self.assertRejected("扩展名")

    def test_ce_has_no_testcase_results(self):
        self.expect(lambda v: v["submissions"]["ce_syntax"].update(testcases=["CE", "CE", "CE"]))
        self.assertRejected("CE 没有逐测试点")

    def test_overall_must_be_worst_of_testcases(self):
        self.expect(lambda v: v["submissions"]["wa_minus"].update(verdict="AC"))
        self.assertRejected("不一致")

    def test_testcase_count_must_match_testdata(self):
        self.expect(lambda v: v["submissions"]["ac_iostream"].update(testcases=["AC", "AC"]))
        self.assertRejected("个测试点")

    def test_request_needs_exactly_one_source(self):
        self.expect(lambda v: v["requests"]["missing_address"].update(sourceText="x"))
        self.assertRejected("恰好给出")

    def test_request_source_must_exist(self):
        self.expect(lambda v: v["requests"]["missing_address"].update(source="nope"))
        self.assertRejected("不是本题的提交")

    def test_tamper_must_name_a_testdata_file(self):
        self.expect(lambda v: v["requests"]["tampered_input"]["tamper"].update(file="9.in"))
        self.assertRejected("不是本题测试数据里的文件")

    def test_unknown_requires_tag(self):
        self.expect(lambda v: v["requests"]["language_not_installed"].update(requires=["gpu:yes"]))
        self.assertRejected("不认识")

    def test_assertion_needs_exactly_one_kind(self):
        self.expect(lambda v: v["submissions"]["ce_marker"].update(message={"equals": "a", "contains": "b"}))
        self.assertRejected("恰好给出")

    def test_trial_overall_must_match_testcases(self):
        self.expect(lambda v: v["trials"]["trial_wa"].update(verdict="AC"))
        self.assertRejected("不一致")

    def test_changed_testdata_is_detected(self):
        with (self.problem / "testdata" / "1.in").open("a", encoding="utf-8") as stream:
            stream.write("1\n")
        self.assertRejected("不一致")

    def test_missing_lock(self):
        (self.root / "corpus.lock").unlink()
        with self.assertRaises(lib.CorpusError):
            lib.load_corpus(self.root)
        self.assertEqual(lib.load_corpus(self.root, require_lock=False).lock["problems"], {})


class CheckTest(unittest.TestCase):
    def test_matching_kinds(self):
        self.assertTrue(lib.Check("equals", "a").matches("a"))
        self.assertFalse(lib.Check("equals", "a").matches("ab"))
        self.assertTrue(lib.Check("startsWith", "a").matches("ab"))
        self.assertFalse(lib.Check("startsWith", "b").matches("ab"))
        self.assertTrue(lib.Check("contains", "b").matches("abc"))

    def test_worst_uses_severity_order(self):
        self.assertEqual(lib.worst(["AC", "WA", "TLE"]), "TLE")
        self.assertEqual(lib.worst(["AC", "RAN"]), "RAN")
        self.assertEqual(lib.worst(["PE", "AC"]), "PE")
        self.assertEqual(lib.worst(["SE", "CE"]), "SE")


if __name__ == "__main__":
    unittest.main()
