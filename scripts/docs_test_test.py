#!/usr/bin/env python3
"""测试文档链接校验器对 Git 跟踪状态的判断。"""

from __future__ import annotations

import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parent))

from docs_test import repository_target_is_tracked  # noqa: E402
import docs_test  # noqa: E402


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

    def test_skill_reference_is_checked_with_other_document_links(self) -> None:
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch).resolve()
            entrypoints = tuple(root / path.relative_to(docs_test.ROOT) for path in docs_test.ACTIVE_ENTRYPOINTS)
            for path in entrypoints:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("# 入口\n", encoding="utf-8")
            (root / ".gitignore").write_text("", encoding="utf-8")
            skill = root / ".agents/skills/dev-work/SKILL.md"
            skill.parent.mkdir(parents=True, exist_ok=True)
            skill.write_text("# Skill\n\n[依据](../../../development/missing.md)\n", encoding="utf-8")
            tracked = set(entrypoints) | {skill}
            with patch.object(docs_test, "ROOT", root), patch.object(docs_test, "ACTIVE_ENTRYPOINTS", entrypoints), patch.object(docs_test, "tracked_paths", return_value=tracked), patch("sys.stdout", new_callable=io.StringIO), patch("sys.stderr", new_callable=io.StringIO):
                self.assertEqual(docs_test.main(), 1)
                target = root / "development/missing.md"
                target.write_text("# 实际依据\n", encoding="utf-8")
                tracked.add(target)
                self.assertEqual(docs_test.main(), 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
