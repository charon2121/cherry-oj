#!/usr/bin/env python3
"""scripts/product 的端到端测试。"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("product")
SECTIONS = (
    "一句话结果",
    "目标用户与使用场景",
    "问题与依据",
    "用户流程",
    "产品规则",
    "体验状态",
    "范围",
    "产品验收场景",
    "成功信号",
    "交付映射",
    "决策与变更记录",
    "未决问题",
)
DECISION_SECTIONS = (
    "背景",
    "候选方案",
    "最终决策",
    "选择理由",
    "产品影响",
    "关联需求",
    "重新评估条件",
)
RELEASE_SECTIONS = (
    "版本目标",
    "需求范围",
    "演示路径",
    "验收结果",
    "已知限制与延期项",
    "发布结论",
)


class ProductCommandTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        source = SCRIPT.parent.parent / "product"
        shutil.copytree(source / "schema", self.root / "product" / "schema")
        shutil.copytree(source / "templates", self.root / "product" / "templates")
        (self.root / "product" / "requirements").mkdir()
        (self.root / "product" / "decisions").mkdir()
        (self.root / "product" / "releases").mkdir()
        (self.root / "tasks" / "items").mkdir(parents=True)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_product(self, *arguments: str, succeeds: bool = True) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root), *arguments],
            check=False,
            text=True,
            capture_output=True,
        )
        if succeeds and result.returncode != 0:
            self.fail(
                f"product {' '.join(arguments)} 失败：\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )
        if not succeeds and result.returncode == 0:
            self.fail(f"product {' '.join(arguments)} 应该失败，但成功了：\n{result.stdout}")
        return result

    def write_requirement(
        self,
        *,
        delivery_tasks: list[str] | None = None,
        status: str = "draft",
        accepted_at: str | None = None,
        omit_section: str | None = None,
    ) -> Path:
        metadata = {
            "id": "REQ-0001",
            "title": "用户可以完成测试流程",
            "product_area": "test",
            "milestone": "M1-test",
            "priority": "P0",
            "status": status,
            "owner": "product/owner",
            "reviewer": "product/owner",
            "target_users": ["测试用户"],
            "source_prd": ["测试章节"],
            "related_requirements": [],
            "related_decisions": [],
            "delivery_tasks": delivery_tasks or [],
            "created_at": "2026-08-24T00:00:00+08:00",
            "updated_at": "2026-08-24T00:00:00+08:00",
            "accepted_at": accepted_at,
        }
        front_matter = "\n".join(
            f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in metadata.items()
        )
        body = "\n\n".join(
            f"## {title}\n\n有效内容。" for title in SECTIONS if title != omit_section
        )
        path = self.root / "product" / "requirements" / "REQ-0001-test-flow.md"
        path.write_text(f"---\n{front_matter}\n---\n\n# REQ-0001：测试\n\n{body}\n", encoding="utf-8")
        return path

    def write_task(self, requirement_ids: list[str], *, status: str = "done") -> Path:
        path = self.root / "tasks" / "items" / "TASK-0001-test.md"
        path.write_text(
            "---\n"
            'id: "TASK-0001"\n'
            f"status: {json.dumps(status)}\n"
            f"requirement_ids: {json.dumps(requirement_ids)}\n"
            "---\n\n# TASK-0001：测试任务\n",
            encoding="utf-8",
        )
        return path

    def write_decision(self) -> None:
        metadata = {
            "id": "PD-0001",
            "title": "测试产品决策",
            "status": "proposed",
            "owner": "product/owner",
            "related_requirements": ["REQ-0001"],
            "created_at": "2026-08-24T00:00:00+08:00",
            "updated_at": "2026-08-24T00:00:00+08:00",
            "accepted_at": None,
            "superseded_by": None,
        }
        front_matter = "\n".join(
            f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in metadata.items()
        )
        body = "\n\n".join(f"## {title}\n\n有效内容。" for title in DECISION_SECTIONS)
        path = self.root / "product" / "decisions" / "PD-0001-test-decision.md"
        path.write_text(f"---\n{front_matter}\n---\n\n# PD-0001：测试\n\n{body}\n", encoding="utf-8")

    def write_release(self) -> None:
        metadata = {
            "id": "REL-0001",
            "title": "测试版本验收",
            "milestone": "M1-test",
            "status": "draft",
            "owner": "product/owner",
            "reviewer": "product/owner",
            "requirements": ["REQ-0001"],
            "created_at": "2026-08-24T00:00:00+08:00",
            "updated_at": "2026-08-24T00:00:00+08:00",
            "accepted_at": None,
            "released_at": None,
        }
        front_matter = "\n".join(
            f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in metadata.items()
        )
        body = "\n\n".join(f"## {title}\n\n有效内容。" for title in RELEASE_SECTIONS)
        path = self.root / "product" / "releases" / "REL-0001-test-release.md"
        path.write_text(f"---\n{front_matter}\n---\n\n# REL-0001：测试\n\n{body}\n", encoding="utf-8")

    def test_check_and_list_valid_bidirectional_links(self) -> None:
        self.write_requirement(delivery_tasks=["TASK-0001"])
        self.write_task(["REQ-0001"])
        checked = self.run_product("check")
        self.assertIn("1 个 REQ", checked.stdout)
        listed = self.run_product("list")
        self.assertIn("REQ-0001", listed.stdout)
        self.assertIn("完成 1", listed.stdout)

    def test_rejects_one_sided_requirement_task_link(self) -> None:
        self.write_requirement(delivery_tasks=["TASK-0001"])
        self.write_task([])
        result = self.run_product("check", succeeds=False)
        self.assertIn("未反向引用 REQ-0001", result.stderr)

    def test_validates_decision_and_release_references(self) -> None:
        self.write_requirement()
        self.write_decision()
        self.write_release()
        result = self.run_product("check")
        self.assertIn("1 个 PD", result.stdout)
        self.assertIn("1 个 REL", result.stdout)

    def test_rejects_unknown_requirement_from_task(self) -> None:
        self.write_requirement()
        self.write_task(["REQ-9999"])
        result = self.run_product("check", succeeds=False)
        self.assertIn("不存在的 REQ-9999", result.stderr)

    def test_rejects_missing_required_section(self) -> None:
        self.write_requirement(omit_section="产品验收场景")
        result = self.run_product("check", succeeds=False)
        self.assertIn("产品验收场景", result.stderr)

    def test_rejects_broken_local_markdown_link(self) -> None:
        path = self.write_requirement()
        path.write_text(path.read_text(encoding="utf-8") + "\n[断裂链接](./missing.md)\n", encoding="utf-8")
        result = self.run_product("check", succeeds=False)
        self.assertIn("本地链接不存在", result.stderr)

    def test_accepted_requirement_needs_acceptance_time_and_tasks(self) -> None:
        self.write_requirement(status="accepted")
        result = self.run_product("check", succeeds=False)
        self.assertIn("accepted_at", result.stderr)
        self.assertIn("至少一个交付 TASK", result.stderr)


if __name__ == "__main__":
    unittest.main()
