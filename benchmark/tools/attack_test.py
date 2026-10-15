"""攻击样本与 sandbox 入口的端到端测试：run.main 对假 judge 与假执行器运行一份小语料。"""

import json
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # run.py 在 benchmark/ 下
import attack  # noqa: E402
import corpus as lib  # noqa: E402
import fake_executor  # noqa: E402
import fake_judge  # noqa: E402
import run  # noqa: E402
from fake_executor import program  # noqa: E402


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def make_corpus(root: Path, spin_expect=None) -> None:
    """一道只有一个测试点的题，加两个攻击样本。"""
    problem = root / "judge" / "tiny"
    (problem / "testdata").mkdir(parents=True)
    (problem / "testdata" / "1.in").write_text("1 2\n")
    (problem / "testdata" / "1.out").write_text("3\n")
    metadata = lib.testdata_pack.build(problem / "testdata")
    write_json(problem / "testdata" / "testdata.json", metadata)
    write_json(problem / "problem.json", {"schemaVersion": 1, "name": "tiny", "title": "t", "seed": 1,
               "limits": {"cpuNs": 1_000_000_000, "memoryBytes": 1 << 28, "clockNs": 3_000_000_000}, "requires": []})
    (problem / "submissions").mkdir()
    (problem / "submissions" / "ac.cpp").write_text(program(stdout="3\n"))
    write_json(problem / "expect.json", {"schemaVersion": 1, "requests": {}, "submissions": {
        "ac": {"verdict": "AC", "testcases": ["AC"],
               "sandbox": [{"testcase": "1", "facts": {"exitCode": 0}, "stdout": {"equals": "3\n"}}]}}})
    common = root / "attack-common"
    (common / "testdata").mkdir(parents=True)
    (common / "testdata" / "1.in").write_text("")
    (common / "testdata" / "1.out").write_text("OK\n")
    write_json(common / "testdata" / "testdata.json", lib.testdata_pack.build(common / "testdata"))
    (common / "probe.cpp").write_text(program(stdout="OK\n"))
    limits = {"cpuNs": 1_000_000_000, "memoryBytes": 1 << 28, "clockNs": 3_000_000_000}
    for name, behaviour, judge, facts in (
            ("spin", dict(facts={"reason": "cpu"}), {"verdict": "TLE"}, {"reason": "cpu"}),
            ("quiet", dict(facts={"signal": 31, "exitCode": -1}), {"verdictNot": "AC"}, {"signal": 31})):
        sample = root / "attack" / name
        sample.mkdir(parents=True)
        (sample / "sample.cpp").write_text(program(**behaviour))
        write_json(sample / "manifest.json", {
            "schemaVersion": 1, "name": name, "language": "cpp", "limits": limits, "judge": judge,
            "sandbox": [{"facts": spin_expect if name == "spin" and spin_expect else facts}],
            "requires": ["backend:linux"]})
    problems = lib.load_corpus(root, require_lock=False).problems
    write_json(root / "corpus.lock", lib.lock_document({n: p.digest for n, p in problems.items()}))


class AttackTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name).resolve()
        self.corpus_dir = self.dir / "corpus"
        make_corpus(self.corpus_dir)
        self.node = self.dir / "node"
        self.binary, self.config = fake_executor.install(self.node, self.node / "boxes", 2)
        (self.dir / "proc").mkdir()
        self.published = self.dir / "published"
        self.report_path = self.dir / "report.json"

    def go(self, *argv: str, judge=None, corpus=None):
        base = ["--corpus", str(corpus or self.corpus_dir), "--report", str(self.report_path),
                "--published-root", str(self.published), "--executor", str(self.binary),
                "--executor-config", str(self.config), "--proc-root", str(self.dir / "proc"),
                "--declare", "backend=linux", "languages=cpp"]
        if judge is not None:
            base += ["--judge-url", judge.url]
        out, err = StringIO(), StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = run.main(base + list(argv))
        return code, out.getvalue(), err.getvalue()

    def ideal(self, corpus=None):
        corpus = lib.load_corpus(corpus or self.corpus_dir)
        return fake_judge.FakeJudge(fake_judge.ideal_corpus(corpus, self.published))

    def report(self):
        return json.loads(self.report_path.read_text(encoding="utf-8"))

    def results(self, name):
        sample = next(s for s in self.report()["attack"] if s["name"] == name)
        return {r["entry"]: r for r in sample["results"]}

    def test_both_entries_run_in_one_invocation(self):
        with self.ideal() as judge:
            code, out, err = self.go("--entries", "judge,sandbox", judge=judge)
        self.assertEqual((code, err), (0, ""))
        report = self.report()
        self.assertEqual(report["environment"]["declared"]["entries"], ["judge", "sandbox"])
        for name in ("spin", "quiet"):
            results = self.results(name)
            self.assertEqual({e: r["status"] for e, r in results.items()}, {"judge": "pass", "sandbox": "pass"})
            self.assertEqual(results["sandbox"]["residue"], {"processes": 0, "cgroups": 0, "workFiles": 0, "cleanProbe": "pass"})
            self.assertEqual(results["judge"]["residue"], {"processes": 0, "cgroups": 0, "workFiles": 0, "cleanProbe": "pass"})
        self.assertEqual(report["summary"]["attack"],
                         {"samples": 2, "ran": 4, "passed": 4, "failed": 0, "unstable": 0, "skipped": 0})
        submission = next(i for i in report["judge"][0]["submissions"] if i["entry"] == "sandbox")
        self.assertEqual((submission["name"], submission["status"]), ("ac", "pass"))
        self.assertIn("攻击样本：2，运行 4", out)
        self.assertNotIn("攻击样本：0", out)

    def test_judge_entry_alone_judges_attack_samples_by_verdict(self):
        with self.ideal() as judge:
            code, _, _ = self.go(judge=judge)
        self.assertEqual(code, 0)
        self.assertEqual(self.results("spin")["judge"]["expected"], "TLE")
        self.assertEqual(self.results("quiet")["judge"]["expected"], "不是 AC")
        self.assertEqual(self.results("spin")["judge"]["repeats"], 3)  # TLE 默认重复 3 遍

    def test_sandbox_entry_alone_needs_no_judge(self):
        code, _, err = self.go("--entries", "sandbox")
        self.assertEqual((code, err), (0, ""))
        self.assertEqual(self.report()["environment"]["endpoint"], "（未使用 judge 入口）")
        self.assertEqual(set(self.results("spin")), {"sandbox"})

    def test_a_wrong_expectation_fails_the_sandbox_entry(self):
        broken = self.dir / "broken"
        make_corpus(broken, spin_expect={"reason": "wall"})
        code, out, _ = self.go("--entries", "sandbox", corpus=broken)
        self.assertEqual(code, 1)
        self.assertEqual(self.results("spin")["sandbox"]["status"], "fail")
        self.assertIn("reason", out)

    def test_leftover_task_processes_fail_both_entries(self):
        (self.dir / "proc" / "99").mkdir()
        (self.dir / "proc" / "99" / "status").write_text("Uid:\t61100\t61100\t61100\t61100\n")
        with self.ideal() as judge:
            code, _, _ = self.go("--entries", "judge,sandbox", judge=judge)
        self.assertEqual(code, 1)
        self.assertEqual({r["status"] for r in self.results("spin").values()}, {"fail"})
        self.assertEqual(self.results("spin")["judge"]["residue"]["processes"], 1)

    def test_judge_entry_without_node_access_skips_attack_samples_instead_of_guessing(self):
        with self.ideal() as judge:
            code, out, _ = self.go("--executor-config", str(self.dir / "no-such.conf"), judge=judge)
        self.assertEqual(code, 0)
        self.assertEqual(self.results("spin")["judge"]["status"], "skipped")
        self.assertIn("无法检查回收", self.results("spin")["judge"]["reason"])
        self.assertIn("攻击样本：2", out)

    def test_missing_executor_is_an_environment_error(self):
        code, _, err = self.go("--entries", "sandbox", "--executor", str(self.dir / "none"))
        self.assertEqual(code, 3)
        self.assertIn("不存在", err)

    def test_select_accepts_attack_sample_names(self):
        code, _, _ = self.go("--entries", "sandbox", "--select", "spin")
        self.assertEqual(code, 0)
        report = self.report()
        self.assertEqual([s["name"] for s in report["attack"]], ["spin"])
        self.assertEqual(report["judge"], [])

    def test_sandbox_entry_stays_skipped_when_the_node_is_not_declared_linux(self):
        code, _, _ = self.go("--entries", "sandbox", "--declare", "backend=devhost")
        self.assertEqual(code, 0)
        self.assertEqual(self.results("spin")["sandbox"]["status"], "skipped")


class LoadingTest(unittest.TestCase):
    def test_the_real_corpus_attack_samples_load(self):
        corpus = lib.load_corpus(Path(__file__).resolve().parents[1] / "corpus")
        samples = {s.name: s for s in corpus.attack_samples}
        self.assertEqual(len(samples), 20)
        self.assertEqual(samples["leak_series"].source, corpus.attack_common.probe_source)
        self.assertEqual((samples["leak_series"].repeat_judge, samples["leak_series"].repeat_sandbox), (100, 200))
        self.assertEqual(samples["privilege"].judge, ("verdictNot", "AC"))
        self.assertIsNone(samples["identity"].judge)
        left, right = samples["isolation_peer"].sandbox
        self.assertEqual((left.concurrent, right.concurrent), (("right",), ("left",)))
        for sample in samples.values():
            self.assertEqual(sample.requires, ("backend:linux",))
            self.assertTrue(sample.judge or sample.sandbox)

    def test_bad_manifests_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "corpus"
            shutil.copytree(Path(__file__).resolve().parents[1] / "corpus", root)
            manifest = root / "attack" / "cpu_spin" / "manifest.json"
            original = manifest.read_text(encoding="utf-8")
            cases = [
                (lambda m: m.update(extra=1), "未知字段"),
                (lambda m: m.update(name="other"), "与目录名"),
                (lambda m: m.update(judge={"verdict": "AC", "verdictNot": "AC"}), "恰好给出"),
                (lambda m: (m.pop("judge"), m.pop("sandbox")), "至少要有一个"),
                (lambda m: m.update(source="../../../outside.cpp"), "不在语料根之内"),
                (lambda m: m.update(requires=["gpu:1"]), "不认识"),
            ]
            for change, text in cases:
                value = json.loads(original)
                change(value)
                manifest.write_text(json.dumps(value), encoding="utf-8")
                with self.assertRaises(lib.CorpusError, msg=text) as caught:
                    lib.load_corpus(root)
                self.assertIn(text, str(caught.exception))


if __name__ == "__main__":
    unittest.main()
