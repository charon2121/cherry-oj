#!/usr/bin/env python3
"""testdata_pack.py 的测试。digest 的黄金值与 Go 侧 judge/testcase/load_test.go 的常量一致，
两种语言对同一份数据必须得到同一个值，协议才算真的统一。"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import testdata_pack

# 与 apps/judge-engine/judge/testcase/load_test.go 中的 goldenDigest 相同。
GOLDEN_DIGEST = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4"


def write(directory: Path, files: dict[str, str]) -> None:
    for name, content in files.items():
        (directory / name).write_text(content, encoding="utf-8")


class PackTest(unittest.TestCase):
    def test_digest_matches_the_value_pinned_in_the_go_tests(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            write(directory, {"1.in": "1 2\n", "1.out": "3\n", "2.in": "100 -7\n", "2.out": "93\n"})
            metadata = testdata_pack.build(directory)
        self.assertEqual(metadata["digest"], GOLDEN_DIGEST)
        self.assertEqual((metadata["testcaseCount"], metadata["totalBytes"]), (2, 16))

    def test_cases_are_ordered_numerically_then_by_name(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            for name in ("10", "2", "1", "big-1", "small"):
                write(directory, {f"{name}.in": "i", f"{name}.out": "o"})
            order = [case["name"] for case in testdata_pack.build(directory)["testcases"]]
        self.assertEqual(order, ["1", "2", "10", "big-1", "small"])

    def test_unpaired_or_empty_or_badly_named_directories_are_refused(self) -> None:
        testcases = {
            "落单的 .in": {"1.in": "i", "1.out": "o", "2.in": "i"},
            "没有数据": {},
            "名字以点开头": {".hidden.in": "i", ".hidden.out": "o"},
        }
        for name, files in testcases.items():
            with self.subTest(name), tempfile.TemporaryDirectory() as tmp:
                directory = Path(tmp)
                write(directory, files)
                with self.assertRaises(ValueError):
                    testdata_pack.build(directory)

    def test_main_writes_testdata_json_and_refuses_to_overwrite(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            write(directory, {"1.in": "1 2\n", "1.out": "3\n"})
            import sys
            from unittest import mock

            with mock.patch.object(sys, "argv", ["testdata_pack.py", str(directory)]):
                self.assertEqual(testdata_pack.main(), 0)
                written = json.loads((directory / "testdata.json").read_text(encoding="utf-8"))
                self.assertEqual(written["testcaseCount"], 1)
                self.assertEqual(testdata_pack.main(), 1)


if __name__ == "__main__":
    unittest.main()
