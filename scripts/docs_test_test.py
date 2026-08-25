#!/usr/bin/env python3
"""测试文档链接校验器对 Git 跟踪状态的判断。"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))

from docs_test import repository_target_is_tracked  # noqa: E402


class RepositoryTargetTest(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path("/repository")

    def test_accepts_tracked_file(self) -> None:
        target = self.root / "docs" / "product.md"
        self.assertTrue(repository_target_is_tracked(target, {target}))

    def test_accepts_directory_with_tracked_content(self) -> None:
        target = self.root / "docs"
        tracked = {target / "README.md", target / "product.md"}
        self.assertTrue(repository_target_is_tracked(target, tracked))

    def test_rejects_existing_but_untracked_target(self) -> None:
        target = self.root / "tutorial" / "README.md"
        tracked = {self.root / "docs" / "README.md"}
        self.assertFalse(repository_target_is_tracked(target, tracked))


if __name__ == "__main__":
    unittest.main(verbosity=2)
