#!/usr/bin/env python3
"""scripts/task 的端到端测试。"""

from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("task")


class TaskCommandTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "tasks" / "items").mkdir(parents=True)
        source_tasks = SCRIPT.parent.parent / "tasks"
        shutil.copytree(source_tasks / "schema", self.root / "tasks" / "schema")
        shutil.copytree(source_tasks / "templates", self.root / "tasks" / "templates")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_task(self, *arguments: str, succeeds: bool = True) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root), *arguments],
            check=False,
            text=True,
            capture_output=True,
        )
        if succeeds and result.returncode != 0:
            self.fail(f"task {' '.join(arguments)} 失败：\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}")
        if not succeeds and result.returncode == 0:
            self.fail(f"task {' '.join(arguments)} 应该失败，但成功了：\n{result.stdout}")
        return result

    def task_path(self, task_id: str) -> Path:
        matches = list((self.root / "tasks" / "items").glob(f"{task_id}-*.md"))
        self.assertEqual(len(matches), 1)
        return matches[0]

    def create(self, title: str, *extra: str) -> None:
        self.run_task(
            "new",
            "--title",
            title,
            "--type",
            "feature",
            "--area",
            "test",
            "--priority",
            "P1",
            *extra,
        )

    def test_reviewed_lifecycle(self) -> None:
        self.create("生命周期测试", "--slug", "lifecycle")
        self.run_task(
            "claim",
            "TASK-0001",
            "--agent",
            "codex/test",
            "--branch",
            "codex/task-0001",
        )

        path = self.task_path("TASK-0001")
        text = path.read_text(encoding="utf-8").replace("- [ ] 补充可以客观检查的完成条件。", "- [x] 已验证。")
        path.write_text(text, encoding="utf-8")

        self.run_task("review", "TASK-0001", "--result", "实现完成；验证命令通过。")
        self.run_task("done", "TASK-0001", "--result", "审核通过；无剩余风险。")
        result = self.run_task("check")
        self.assertIn("1 个任务通过校验", result.stdout)
        final_text = path.read_text(encoding="utf-8")
        self.assertIn('status: "done"', final_text)
        self.assertNotIn("completed_at: null", final_text)

    def test_new_task_records_product_links(self) -> None:
        self.create(
            "产品关联测试",
            "--slug",
            "product-link",
            "--requirement",
            "REQ-0001",
            "--milestone",
            "M1-traditional-oj",
        )
        text = self.task_path("TASK-0001").read_text(encoding="utf-8")
        self.assertIn('requirement_ids: ["REQ-0001"]', text)
        self.assertIn('milestone: "M1-traditional-oj"', text)

    def test_legacy_task_without_product_fields_remains_valid(self) -> None:
        self.create("旧任务兼容", "--slug", "legacy")
        path = self.task_path("TASK-0001")
        text = path.read_text(encoding="utf-8")
        text = text.replace("requirement_ids: []\n", "").replace("milestone: null\n", "")
        path.write_text(text, encoding="utf-8")
        result = self.run_task("check")
        self.assertIn("1 个任务通过校验", result.stdout)

    def test_dependencies_control_readiness(self) -> None:
        self.create("前置任务", "--slug", "first", "--no-review")
        self.create("下游任务", "--slug", "second", "--depends-on", "TASK-0001")

        ready = self.run_task("list", "--ready")
        self.assertIn("TASK-0001", ready.stdout)
        self.assertNotIn("TASK-0002", ready.stdout)

        rejected = self.run_task(
            "claim",
            "TASK-0002",
            "--agent",
            "codex/test",
            "--branch",
            "codex/task-0002",
            succeeds=False,
        )
        self.assertIn("依赖尚未完成", rejected.stderr)

        invalid_lease = self.run_task(
            "claim",
            "TASK-0001",
            "--agent",
            "codex/test",
            "--branch",
            "codex/task-0001",
            "--lease-hours",
            "nan",
            succeeds=False,
        )
        self.assertIn("720", invalid_lease.stderr)

        unsafe_slug = self.run_task(
            "new",
            "--title",
            "越界路径",
            "--type",
            "feature",
            "--area",
            "test",
            "--priority",
            "P1",
            "--slug",
            "../outside",
            succeeds=False,
        )
        self.assertIn("slug", unsafe_slug.stderr)

    def test_block_resume_release_and_cycle_detection(self) -> None:
        self.create("可释放任务", "--slug", "releasable")
        self.run_task(
            "claim",
            "TASK-0001",
            "--agent",
            "codex/test",
            "--branch",
            "codex/task-0001",
        )
        self.run_task("block", "TASK-0001", "--reason", "等待外部测试环境；环境恢复后继续。")
        self.run_task("resume", "TASK-0001")
        self.run_task("release", "TASK-0001", "--reason", "转交其他 agent。")

        released = self.task_path("TASK-0001").read_text(encoding="utf-8")
        self.assertIn('status: "todo"', released)
        self.assertIn("assignee: null", released)

        self.create("依赖前置任务", "--slug", "dependent", "--depends-on", "TASK-0001")
        first = self.task_path("TASK-0001")
        first.write_text(
            first.read_text(encoding="utf-8").replace("depends_on: []", 'depends_on: ["TASK-0002"]'),
            encoding="utf-8",
        )
        invalid = self.run_task("check", succeeds=False)
        self.assertIn("任务依赖形成环", invalid.stderr)


if __name__ == "__main__":
    unittest.main()
