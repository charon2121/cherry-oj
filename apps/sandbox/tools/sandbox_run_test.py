#!/usr/bin/env python3
"""sandbox_run.py 中不依赖真实内核的部分：参数解析与请求拼装。"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import sandbox_run as tool

LIMITS = tool.Limits(1, 2, 3, 4, 5, 6)


class ParseTest(unittest.TestCase):
    def test_duration_and_size(self) -> None:
        self.assertEqual(tool.parse_duration("500ms"), 500_000_000)
        self.assertEqual(tool.parse_duration("1.5s"), 1_500_000_000)
        self.assertEqual(tool.parse_size("64k"), 65536)
        self.assertEqual(tool.parse_size("1g"), 1 << 30)
        for bad in ("5", "s", "1m"):
            with self.assertRaises(tool.UsageError):
                tool.parse_duration(bad)
        with self.assertRaises(tool.UsageError):
            tool.parse_size("1x")

    def test_put_and_get(self) -> None:
        self.assertEqual(tool.parse_put("a/b.cpp"), tool.Put(Path("a/b.cpp"), "b.cpp", False))
        self.assertEqual(tool.parse_put("Main:Run:x"), tool.Put(Path("Main"), "Run", True))
        for bad in ("a:b:y", "a:../b", "a:.hidden"):
            with self.assertRaises(tool.UsageError):
                tool.parse_put(bad)
        self.assertEqual(tool.parse_get("Main=out/Main"), tool.Get("Main", Path("out/Main")))
        with self.assertRaises(tool.UsageError):
            tool.parse_get("../x")


class SpecTest(unittest.TestCase):
    def test_spec_records_are_nul_terminated_in_order(self) -> None:
        spec = tool.build_spec(["g++", "m.cpp"], ["A=1"], [tool.Put(Path("m.cpp"), "m.cpp", False)],
                               [tool.Get("Main", Path("Main"))], LIMITS)
        self.assertEqual(spec.split(b"\0")[:-1], [
            b"arg=g++", b"arg=m.cpp", b"env=A=1", b"input=0:m.cpp", b"output=Main",
            b"cpu_ns=1", b"clock_ns=2", b"memory_bytes=3", b"max_processes=4",
            b"stdout_max_bytes=5", b"stderr_max_bytes=6"])
        self.assertTrue(spec.endswith(b"\0"))

    def test_rejects_path_command_and_bad_env(self) -> None:
        for command, env in ((["/bin/cat"], []), ([], []), (["cat"], ["noequals"])):
            with self.assertRaises(tool.UsageError):
                tool.build_spec(command, env, [], [], LIMITS)


class ClassifyTest(unittest.TestCase):
    def test_order_matches_judge(self) -> None:
        big = tool.Limits(10**12, 0, 0, 0, 0, 0)
        self.assertEqual(tool.classify({"exitCode": 0}, big), ("正常结束", True))
        self.assertEqual(tool.classify({"exitCode": 3}, big)[0], "退出码 3")
        self.assertEqual(tool.classify({"exitCode": -1, "signal": 9, "oom": 1, "oomKill": 1}, big)[0], "内存超限")
        self.assertEqual(tool.classify({"reason": "cpu"}, big)[0], "CPU 时间超限")
        self.assertEqual(tool.classify({"reason": "wall"}, big)[0], "墙钟超限")
        self.assertIn("平台故障", tool.classify({"error": "x"}, big)[0])
        self.assertIn("SIGSYS", tool.classify({"exitCode": -1, "signal": 31}, big)[0])


if __name__ == "__main__":
    unittest.main()
