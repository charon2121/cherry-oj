import tempfile
import time
import unittest
from pathlib import Path

import corpus as lib
import executor as executor_lib
import fake_executor
import sandbox_entry
import specs
from executor import Call
from judge_entry import Declared
from node import NodeInspector
from fake_executor import program

LIMITS = lib.Limits(1_000_000_000, 1 << 28, 3_000_000_000)
PROBE = program(stdout="OK\n")
LINUX = Declared(backend="linux", languages=("cpp",), entries=("sandbox",))


def item(source, runs, **extra):
    return sandbox_entry.SandboxItem(extra.pop("kind", "attack"), "sample", source, "cpp", LIMITS,
                                     specs.parse_run_specs(runs, "t", None), extra.pop("requires", ()), {},
                                     extra.pop("repeat", None))


class Base(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name)
        self.proc = self.dir / "proc"
        self.proc.mkdir()
        binary, config = fake_executor.install(self.dir, self.dir / "boxes", 2)
        self.config = executor_lib.load_config(config)
        self.executor = executor_lib.Executor(binary, self.config)
        self.inspector = NodeInspector(self.config, self.proc)

    def runner(self, declared=LINUX, probe=PROBE, repeats=3):
        return sandbox_entry.SandboxRunner(self.executor, self.inspector, declared, probe, repeats)


class RunItemTest(Base):
    def test_matching_facts_pass_and_leave_the_node_clean(self):
        result = self.runner().run_item(item(program(facts={"reason": "cpu", "cpuNs": 1_100_000_000}, stdout="x"),
                                             [{"facts": {"reason": "cpu", "cpuNs": {"ge": 1_000_000_000}}}]))
        self.assertEqual((result.status, result.diffs), ("pass", []))
        self.assertEqual(result.residue, {"processes": 0, "cgroups": 0, "workFiles": 0, "cleanProbe": "pass"})
        self.assertEqual(result.expected["reason"], "cpu")
        self.assertEqual(result.actual["reason"], "cpu")
        self.assertEqual(result.entry, "sandbox")

    def test_a_wrong_fact_fails_with_the_difference(self):
        result = self.runner().run_item(item(program(facts={"reason": "wall"}), [{"facts": {"reason": "cpu"}}]))
        self.assertEqual(result.status, "fail")
        self.assertTrue(any("reason" in d and "'wall'" in d for d in result.diffs), result.diffs)

    def test_default_and_overridden_limits_reach_the_executor(self):
        self.runner().run_item(item(program(), [{"limits": {"memoryBytes": 64 << 20, "maxProcesses": 8},
                                                 "facts": {"exitCode": 0}}]))
        run = [c for c in fake_executor.calls(self.dir) if c["arg"] == ["Main"]][0]
        self.assertEqual(run["limits"], {"cpu_ns": 1_000_000_000, "clock_ns": 3_000_000_000,
                                         "memory_bytes": 64 << 20, "max_processes": 8,
                                         "stdout_max_bytes": 1 << 20, "stderr_max_bytes": 1 << 20})

    def test_stdin_and_runner_pid_placeholder(self):
        self.runner().run_item(item(program(), [{"stdin": "left ${runnerPid}\n", "facts": {"exitCode": 0}}]))
        run = [c for c in fake_executor.calls(self.dir) if c["arg"] == ["Main"]][0]
        self.assertTrue(run["stdin"].startswith("left ") and run["stdin"][5:-1].isdigit())

    def test_compile_failure_is_a_failure_not_a_crash(self):
        result = self.runner().run_item(item("COMPILE_ERROR", [{"facts": {"exitCode": 0}}]))
        self.assertEqual(result.status, "fail")
        self.assertIn("编译失败", result.diffs[0])

    def test_unmet_requires_skip_without_touching_the_executor(self):
        result = self.runner(Declared(backend="devhost", entries=("sandbox",))).run_item(
            item(program(), [{"facts": {"exitCode": 0}}], requires=("backend:linux",)))
        self.assertEqual(result.status, "skipped")
        self.assertEqual(fake_executor.calls(self.dir), [])
        result = self.runner(Declared(backend="linux", entries=("judge",))).run_item(item(program(), [{"facts": {"exitCode": 0}}]))
        self.assertIn("entry:sandbox", result.reason)

    def test_executor_refusal_and_unreclaimed_are_failures(self):
        refused = self.runner().run_item(item(program(exit=1), [{"facts": {"exitCode": 0}}]))
        self.assertEqual(refused.status, "fail")
        self.assertIn("拒绝", refused.diffs[0])
        unreclaimed = self.runner().run_item(item(program(exit=2), [{"facts": {"exitCode": 0}}]))
        self.assertIn("作废", unreclaimed.diffs[0])

    def test_time_class_runs_repeat_and_plain_runs_do_not(self):
        timed = self.runner().run_item(item(program(facts={"reason": "cpu"}), [{"facts": {"reason": "cpu"}}]))
        plain = self.runner().run_item(item(program(), [{"facts": {"exitCode": 0}}]))
        self.assertEqual((timed.repeats, plain.repeats), (3, 1))
        explicit = self.runner().run_item(item(program(), [{"facts": {"exitCode": 0}}], repeat=5))
        self.assertEqual(explicit.repeats, 5)
        runs = [c for c in fake_executor.calls(self.dir) if c["arg"] == ["Main"]]
        self.assertEqual(len(runs), 3 + 1 + 5 + 3)  # 每个执行单元之后还有一次干净运行


class ResidueTest(Base):
    def test_leftover_processes_cgroups_and_files_fail_the_item(self):
        # 假 /proc：一个 payload 身份的进程；假 cgroup：一个遗留的 box-0 组。
        (self.proc / "4242").mkdir()
        (self.proc / "4242" / "status").write_text("Name:\tx\nUid:\t61100\t61100\t61100\t61100\n")
        (self.proc / "self").mkdir()
        (self.proc / "self" / "status").write_text("Uid:\t1000\t1000\t1000\t1000\n")
        (self.config.cgroup / "box-0").mkdir()
        result = self.runner().run_item(item(program(leftover=True), [{"facts": {"exitCode": 0}}]))
        self.assertEqual(result.status, "fail")
        self.assertEqual((result.residue["processes"], result.residue["cgroups"], result.residue["workFiles"]),
                         (1, 1, 1))  # 只有运行那一次在 out/ 里多留了协议没有规定的文件
        self.assertTrue(any("回收检查未通过" in d for d in result.diffs))

    def test_init_identities_count_too(self):
        (self.proc / "7").mkdir()
        (self.proc / "7" / "status").write_text("Uid:\t61401\t61401\t61401\t61401\n")
        self.assertEqual(self.inspector.processes(), 1)
        (self.proc / "8").mkdir()
        (self.proc / "8" / "status").write_text("Uid:\t61402\t0\t0\t0\n")  # 超出 box_count=2 的范围
        self.assertEqual(self.inspector.processes(), 1)

    def test_a_polluted_clean_probe_fails_the_item(self):
        dirty = program(stdout="OK\n", facts={"memoryBytes": 20 << 20})
        result = self.runner(probe=dirty).run_item(item(program(), [{"facts": {"exitCode": 0}}]))
        self.assertEqual(result.status, "fail")
        self.assertEqual(result.residue["cleanProbe"], "fail")
        self.assertTrue(any("内存峰值" in d for d in result.diffs))
        wrong = self.runner(probe=program(stdout="NOPE\n")).run_item(item(program(), [{"facts": {"exitCode": 0}}]))
        self.assertTrue(any("输出应是 OK" in d for d in wrong.diffs))
        self.assertTrue(any("没有干净探针" in d for d in
                            self.runner(probe=None).run_item(item(program(), [{"facts": {"exitCode": 0}}])).diffs))


class ConcurrencyTest(Base):
    GROUP = [{"id": "left", "concurrent": ["right"], "stdin": "left", "facts": {"exitCode": 0}},
             {"id": "right", "concurrent": ["left"], "stdin": "right", "facts": {"exitCode": 0}}]

    def test_members_get_distinct_boxes_and_really_overlap(self):
        result = self.runner().run_item(item(program(sleep=0.3), self.GROUP))
        self.assertEqual((result.status, result.diffs), ("pass", []))
        calls = [c for c in fake_executor.calls(self.dir) if c["arg"] == ["Main"] and c["stdin"] in ("left", "right")]
        self.assertEqual(sorted(c["box"] for c in calls), [0, 1])
        self.assertEqual(sorted(c["stdin"] for c in calls), ["left", "right"])
        self.assertEqual(result.repeats, 1)  # 并发组不重复
        self.assertEqual([r["box"] for r in result.runs], [0, 1])

    def test_a_group_larger_than_box_count_is_an_environment_error(self):
        group = [{"id": n, "concurrent": [m for m in "abc" if m != n], "facts": {"exitCode": 0}} for n in "abc"]
        with self.assertRaises(executor_lib.ExecutorError):
            self.runner().run_item(item(program(), group))

    def test_overlap_detection(self):
        def call(start, end):
            return Call(0, {}, "", b"", b"", {}, start, end)
        now = time.monotonic()
        self.assertTrue(sandbox_entry._overlap([call(now, now + 1), call(now + 0.5, now + 1.5)]))
        self.assertFalse(sandbox_entry._overlap([call(now, now + 1), call(now + 1.1, now + 2)]))
        self.assertTrue(sandbox_entry._overlap([call(now, now + 3), call(now + 1, now + 2), call(now + 1.5, now + 2.5)]))
        self.assertFalse(sandbox_entry._overlap([call(now, now + 1), call(now + 0.5, now + 1.5), call(now + 1.2, now + 2)]))


class CorpusItemsTest(unittest.TestCase):
    def test_every_sample_and_submission_with_sandbox_runs_becomes_an_item(self):
        corpus = lib.load_corpus(Path(__file__).resolve().parents[1] / "corpus")
        items = sandbox_entry.items_for(corpus, list(corpus.problems.values()), True)
        names = {(problem, it.name) for problem, it in items}
        self.assertEqual(len([n for n in names if n[0] is None]), len(corpus.attack_samples))
        self.assertIn(("a-plus-b", "re_abort"), names)
        by_name = {it.name: it for _, it in items}
        self.assertEqual(by_name["ac_iostream"].inputs["1"], b"3 5\n")
        self.assertEqual(by_name["tle_loop"].requires, ())


if __name__ == "__main__":
    unittest.main()
