#!/usr/bin/env python3
"""用隔离目录验证创建边界、只读导航和迁移原件保护。"""

from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class WorkToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        shutil.copyfile(ROOT / "scripts/work", self.root / "scripts/work")
        (self.root / "development/templates").mkdir(parents=True)
        shutil.copyfile(ROOT / "development/templates/overview.md", self.root / "development/templates/overview.md")
        folder = self.root / "development/works/WORK-001"
        folder.mkdir(parents=True)
        (folder / "00-work.md").write_text("# 原工作\n\n简短说明。\n", encoding="utf-8")
        original = folder / "90-history-WORK-001.md"
        original.write_text("# 原始资料\n\n未经签署。\n", encoding="utf-8")
        (folder / "60-task-TASK-001.md").write_text("# 原任务\n", encoding="utf-8")
        snapshot = self.root / "development/snapshot.zip"
        snapshot.write_bytes(b"historical snapshot")
        manifest = {
            "version": 1,
            "files": {original.relative_to(self.root).as_posix(): hashlib.sha256(original.read_bytes()).hexdigest()},
            "system_snapshot": {"path": snapshot.relative_to(self.root).as_posix(), "sha256": hashlib.sha256(snapshot.read_bytes()).hexdigest()},
            "works": [{"id": "WORK-001", "original": original.relative_to(self.root).as_posix()}],
        }
        (self.root / "development/migration.json").write_text(json.dumps(manifest), encoding="utf-8")

    def run_tool(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run([sys.executable, "-B", "scripts/work", *arguments], cwd=self.root, text=True, capture_output=True, check=False)

    def snapshot(self) -> dict[str, bytes]:
        return {path.relative_to(self.root).as_posix(): path.read_bytes() for path in self.root.rglob("*") if path.is_file()}

    def test_five_types_only_create_one_document(self) -> None:
        for kind in ("product", "infra", "fix", "maintenance", "improvement"):
            with self.subTest(kind=kind):
                result = self.run_tool("new", kind, kind, "--title", "工作标题", "--date", "2026-10-03")
                self.assertEqual(result.returncode, 0, result.stderr)
                files = list((self.root / f"development/works/2026-10-03-{kind}").iterdir())
                self.assertEqual([path.name for path in files], ["00-work.md"])
                self.assertIn("工作标题", files[0].read_text())
        self.assertFalse((self.root / "development/index.json").exists())

    def test_rejects_overwrite_and_invalid_paths_without_mutation(self) -> None:
        self.assertEqual(self.run_tool("new", "fix", "login", "--title", "登录", "--date", "2026-10-03").returncode, 0)
        before = self.snapshot()
        for slug, day in (("login", "2026-10-03"), ("../escape", "2026-10-03"), ("valid", "2026-02-30")):
            result = self.run_tool("new", "fix", slug, "--title", "覆盖", "--date", day)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(before, self.snapshot())

    def test_rejects_symlink_to_outside_repository(self) -> None:
        with tempfile.TemporaryDirectory() as external:
            (self.root / "development/works/2026-10-03-outside").symlink_to(external, target_is_directory=True)
            result = self.run_tool("new", "fix", "outside", "--title", "外部", "--date", "2026-10-03")
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(list(Path(external).iterdir()), [])

    def test_navigation_and_check_are_read_only(self) -> None:
        before = self.snapshot()
        for arguments in (("list",), ("show", "WORK-001"), ("show", "TASK-001"), ("check",)):
            with self.subTest(arguments=arguments):
                result = self.run_tool(*arguments)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(before, self.snapshot())

    def test_retired_commands_cannot_sign_or_advance_work(self) -> None:
        before = self.snapshot()
        for command in ("gate", "refresh", "advance", "rebuild", "status"):
            result = self.run_tool(command, "WORK-001")
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(before, self.snapshot())

    def test_check_detects_lost_or_modified_history(self) -> None:
        original = self.root / "development/works/WORK-001/90-history-WORK-001.md"
        original.write_text("伪造的新结果", encoding="utf-8")
        self.assertNotEqual(self.run_tool("check").returncode, 0)
        original.unlink()
        self.assertNotEqual(self.run_tool("check").returncode, 0)

    def test_check_detects_lost_current_entrance(self) -> None:
        (self.root / "development/works/WORK-001/00-work.md").unlink()
        self.assertNotEqual(self.run_tool("check").returncode, 0)

    def test_check_rejects_manifest_path_escape(self) -> None:
        path = self.root / "development/migration.json"
        manifest = json.loads(path.read_text())
        manifest["files"]["../outside"] = "0" * 64
        path.write_text(json.dumps(manifest), encoding="utf-8")
        result = self.run_tool("check")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("路径越出仓库", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
