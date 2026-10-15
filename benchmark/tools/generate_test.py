import json
import shutil
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

import check
import corpus as lib
import generate

REAL = Path(__file__).resolve().parents[1] / "corpus"


def quiet(function, *args):
    with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
        return function(*args)


class RealCorpusTest(unittest.TestCase):
    def test_checked_in_corpus_has_no_drift(self):
        self.assertEqual(check.check(REAL), [])

    def test_regenerating_twice_is_byte_identical(self):
        problem = lib.load_corpus(REAL).problems["a-plus-b"]
        first = generate.regenerate(problem.directory, problem.seed)
        second = generate.regenerate(problem.directory, problem.seed)
        self.addCleanup(shutil.rmtree, first.parent, True)
        self.addCleanup(shutil.rmtree, second.parent, True)
        self.assertEqual(generate.tree_bytes(first), generate.tree_bytes(second))


class WorkingCopyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "corpus"
        shutil.copytree(REAL, self.root)
        self.problem = self.root / "judge" / "a-plus-b"

    def test_disagreeing_reference_solutions_are_refused(self):
        (self.problem / "oracle" / "solution_b.py").write_text(
            "a, b = map(int, input().split())\nprint(a + b + 1)\n", encoding="utf-8")
        with self.assertRaises(generate.GenerateError) as caught:
            generate.regenerate(self.problem, 1)
        self.assertIn("不一致", str(caught.exception))
        self.assertEqual(quiet(generate.main, ["--corpus", str(self.root)]), 1)

    def test_missing_oracle_is_refused(self):
        (self.problem / "oracle" / "solution_a.py").unlink()
        with self.assertRaises(generate.GenerateError):
            generate.regenerate(self.problem, 1)

    def test_drift_is_reported_and_lock_stays_untouched_without_the_flag(self):
        before = (self.root / "corpus.lock").read_text(encoding="utf-8")
        gen = self.problem / "oracle" / "gen.py"
        gen.write_text(gen.read_text(encoding="utf-8").replace('"0 0\\n"', '"7 8\\n"'), encoding="utf-8")
        # expect.json 仍写着旧的逐点结论，但测试点个数没变，所以能读取；锁定文件对不上就必须失败。
        self.assertEqual(quiet(generate.main, ["--corpus", str(self.root)]), 1)
        self.assertEqual((self.root / "corpus.lock").read_text(encoding="utf-8"), before)
        self.assertNotEqual(check.check(self.root, regenerate=False), [])

    def test_updating_the_lock_needs_a_reason(self):
        with self.assertRaises(SystemExit):
            quiet(generate.main, ["--corpus", str(self.root), "--update-lock"])

    def test_update_lock_with_reason_fixes_the_drift(self):
        gen = self.problem / "oracle" / "gen.py"
        gen.write_text(gen.read_text(encoding="utf-8").replace('"0 0\\n"', '"7 8\\n"'), encoding="utf-8")
        self.assertEqual(quiet(generate.main, ["--corpus", str(self.root), "--update-lock", "--reason", "测试"]), 0)
        self.assertEqual(check.check(self.root), [])

    def test_regenerate_detects_hand_edited_expected_output(self):
        # 手工改了 .out 与 testdata.json，使它们自洽、锁定文件也对得上，只有重新生成才能发现。
        out = self.problem / "testdata" / "1.out"
        out.write_text("9\n", encoding="utf-8")
        metadata = lib.testdata_pack.build(self.problem / "testdata")
        (self.problem / "testdata" / "testdata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        lock = lib.lock_document({"a-plus-b": metadata["digest"]})
        (self.root / "corpus.lock").write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
        problems = check.check(self.root)
        self.assertTrue(any("数据漂移" in line for line in problems), problems)


if __name__ == "__main__":
    unittest.main()
