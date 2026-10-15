import json
import tempfile
import unittest
from pathlib import Path

import executor
import fake_executor


def facts(**override):
    base = {"version": 1, "exitCode": 0, "signal": 0, "cpuNs": 1, "memoryBytes": 1, "clockNs": 1, "reason": "",
            "oom": 0, "oomKill": 0, "memoryMaxEvents": 0, "pidsMaxEvents": 0, "cancelled": False,
            "outputExceeded": False, "stdoutBytes": 0, "stderrBytes": 0, "outputs": [], "error": ""}
    base.update(override)
    return json.dumps(base).encode()


class FactsTest(unittest.TestCase):
    def test_accepts_a_complete_fact(self):
        self.assertEqual(executor.parse_facts(facts())["version"], 1)

    def test_rejects_what_the_protocol_forbids(self):
        for data, text in [
            (b"not json", "合法的 JSON"),
            (b"[]", "JSON 对象"),
            (facts(version=2), "version"),
            (facts(reason="mystery"), "reason"),
            (facts(exitCode="0"), "类型"),
            (facts(exitCode=True), "类型"),
            (json.dumps({**json.loads(facts()), "extra": 1}).encode(), "不认识的字段"),
            (json.dumps({k: v for k, v in json.loads(facts()).items() if k != "oom"}).encode(), "缺少字段"),
        ]:
            with self.assertRaises(executor.ExecutorError, msg=text) as caught:
                executor.parse_facts(data)
            self.assertIn(text, str(caught.exception))


class SpecTest(unittest.TestCase):
    def test_records_are_nul_terminated_key_values(self):
        limits = {"cpuNs": 1, "memoryBytes": 2, "clockNs": 3, "maxProcesses": 4, "stdoutMaxBytes": 5, "stderrMaxBytes": 6}
        data = executor.spec_bytes(["g++", "Main.cpp"], limits, [("Main.cpp", False), ("Main", True)], ["Main"])
        self.assertTrue(data.endswith(b"\0"))
        self.assertEqual(data[:-1].split(b"\0"), [
            b"arg=g++", b"arg=Main.cpp", b"input=0:Main.cpp", b"input=1:Main", b"output=Main", b"cpu_ns=1",
            b"clock_ns=3", b"memory_bytes=2", b"max_processes=4", b"stdout_max_bytes=5", b"stderr_max_bytes=6"])

    def test_nul_in_a_record_is_refused(self):
        limits = {name: 1 for name in executor.KEYS}
        with self.assertRaises(executor.ExecutorError):
            executor.spec_bytes(["a\0b"], limits, [], [])


class ConfigTest(unittest.TestCase):
    def test_reads_known_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            _, config = fake_executor.install(Path(temp), Path(temp) / "boxes", 3)
            loaded = executor.load_config(config)
        self.assertEqual((loaded.box_count, loaded.payload_uid, loaded.init_uid), (3, 61100, 61400))

    def test_missing_file_and_missing_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(executor.ExecutorError):
                executor.load_config(Path(temp) / "none")
            partial = Path(temp) / "partial"
            partial.write_text("boxes=/x\n")
            with self.assertRaises(executor.ExecutorError) as caught:
                executor.load_config(partial)
            self.assertIn("缺少", str(caught.exception))


class CallTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.dir = Path(self.temp.name)
        binary, config = fake_executor.install(self.dir, self.dir / "boxes")
        self.executor = executor.Executor(binary, executor.load_config(config))
        self.limits = {"cpuNs": 1_000_000_000, "memoryBytes": 1 << 28, "clockNs": 3_000_000_000, "maxProcesses": 64,
                       "stdoutMaxBytes": 1 << 20, "stderrMaxBytes": 1 << 20}

    def test_compile_then_run_follows_the_protocol(self):
        binary, compiled = self.executor.compile(fake_executor.program(stdout="hi\n", stderr="note\n"), "cpp")
        self.assertIsNotNone(binary)
        ran = self.executor.run_binary(binary, 0, self.limits, b"stdin-text")
        self.assertEqual((ran.exit_code, ran.stdout, ran.stderr), (0, b"hi\n", b"note\n"))
        calls = fake_executor.calls(self.dir)
        self.assertEqual(calls[0]["arg"], ["g++", "Main.cpp", "-o", "Main", "-O2", "-std=c++17"])
        self.assertEqual(calls[0]["limits"]["cpu_ns"], 10_000_000_000)  # 编译限制与 judge 一致
        self.assertEqual(calls[1]["arg"], ["Main"])
        self.assertEqual(calls[1]["input"], ["1:Main"])
        self.assertEqual(calls[1]["stdin"], "stdin-text")
        self.assertEqual(ran.leftovers, [])

    def test_compile_error_has_no_binary(self):
        binary, compiled = self.executor.compile("COMPILE_ERROR", "cpp")
        self.assertIsNone(binary)
        self.assertEqual(compiled.facts["exitCode"], 1)
        self.assertIn("COMPILE_ERROR", compiled.stderr.decode())

    def test_unexpected_files_are_reported_as_leftovers(self):
        binary, _ = self.executor.compile(fake_executor.program(leftover=True), "cpp")
        self.assertEqual(self.executor.run_binary(binary, 0, self.limits, b"").leftovers, ["out/unexpected"])

    def test_refusal_and_unreclaimed_exit_codes(self):
        binary, _ = self.executor.compile(fake_executor.program(exit=1), "cpp")
        refused = self.executor.run_binary(binary, 0, self.limits, b"")
        self.assertEqual((refused.exit_code, refused.facts), (1, None))
        binary, _ = self.executor.compile(fake_executor.program(exit=2), "cpp")
        self.assertEqual(self.executor.run_binary(binary, 0, self.limits, b"").exit_code, 2)

    def test_box_out_of_range(self):
        with self.assertRaises(executor.ExecutorError):
            self.executor.box(2)

    def test_clear_leaves_empty_in_and_out(self):
        binary, _ = self.executor.compile(fake_executor.program(stdout="x"), "cpp")
        self.executor.run_binary(binary, 0, self.limits, b"")
        self.executor.clear(0)
        box = self.executor.box(0)
        self.assertEqual(sorted(p.name for p in box.iterdir()), ["in", "out"])
        self.assertEqual(list((box / "in").iterdir()) + list((box / "out").iterdir()), [])


if __name__ == "__main__":
    unittest.main()
