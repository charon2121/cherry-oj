#!/usr/bin/env python3
"""scripts/work 的端到端测试。"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCRIPT = PROJECT_ROOT / "scripts" / "work"


class WorkToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "development").mkdir()
        shutil.copytree(
            PROJECT_ROOT / "development" / "templates",
            self.root / "development" / "templates",
        )
        shutil.copytree(
            PROJECT_ROOT / "development" / "schema",
            self.root / "development" / "schema",
        )
        (self.root / "development" / "index.json").write_text(
            json.dumps(
                {
                    "next_ids": {
                        "capability": 1,
                        "change": 1,
                        "decision": 1,
                        "design": 1,
                        "experience": 1,
                        "feature": 1,
                        "improvement": 1,
                        "issue": 1,
                        "memory": 1,
                        "plan": 1,
                        "product": 1,
                        "task": 1,
                        "verify": 1,
                        "work": 1,
                    }
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        (self.root / "AGENTS.md").write_text("# test rules\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def run_work(self, *arguments: str, success: bool = True) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--root", str(self.root), *arguments],
            check=False,
            text=True,
            capture_output=True,
        )
        if success and result.returncode != 0:
            self.fail(f"command failed: {result.args}\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}")
        if not success and result.returncode == 0:
            self.fail(f"command unexpectedly succeeded: {result.args}\nstdout:\n{result.stdout}")
        return result

    def create_fast_work(self) -> None:
        self.run_work(
            "new",
            "--title",
            "整理局部测试",
            "--type",
            "maintenance",
            "--risk",
            "low",
            "--impact",
            "local",
            "--owner",
            "agent/test",
        )

    def one_work_directory(self, work_id: str = "WORK-001") -> Path:
        paths = list((self.root / "development" / "works").glob(work_id))
        self.assertEqual(1, len(paths), paths)
        return paths[0]

    def one_work_file(self, filename: str, work_id: str = "WORK-001") -> Path:
        path = self.one_work_directory(work_id) / filename
        self.assertTrue(path.is_file(), path)
        return path

    def metadata(self, path: Path) -> dict[str, object]:
        lines = path.read_text(encoding="utf-8").splitlines()
        self.assertEqual("---", lines[0])
        end = lines.index("---", 1)
        return {
            key: json.loads(value)
            for key, value in (line.split(": ", 1) for line in lines[1:end])
        }

    def test_fast_work_generates_only_needed_documents(self) -> None:
        self.create_fast_work()
        work_directory = self.one_work_directory()
        self.assertEqual(
            [
                "00-work.md",
                "10-change-CHANGE-001.md",
                "60-task-TASK-001.md",
                "70-verify-VERIFY-001.md",
            ],
            sorted(path.name for path in work_directory.iterdir()),
        )
        work = self.one_work_file("00-work.md").read_text(encoding="utf-8")
        self.assertIn('required_documents: ["change", "task", "verify"]', work)
        workflow = self.metadata(self.one_work_file("00-work.md"))["workflow"]
        stages = {stage["stage"]: stage for stage in workflow}
        self.assertEqual(["TASK-001"], stages["tasks"]["artifacts"])
        self.assertEqual(["TASK-001"], stages["development"]["artifacts"])
        self.assertEqual("skipped", stages["design"]["status"])
        self.assertEqual([], stages["review"]["artifacts"])
        self.assertFalse((self.root / "development" / "designs").exists())
        self.assertFalse((self.root / "development" / "tasks").exists())
        self.assertEqual("WORK-001", work_directory.name)
        works_index = (self.root / "development" / "WORKS.md").read_text(encoding="utf-8")
        self.assertIn("| WORK-001 | 整理局部测试 | 整理维护 | 待确认 |", works_index)
        self.assertIn("[00-work.md](./works/WORK-001/00-work.md)", works_index)
        checked = self.run_work("check")
        self.assertIn("4 份开发文档通过校验", checked.stdout)

    def test_risky_flags_upgrade_risk_and_expand_workflow(self) -> None:
        result = self.run_work(
            "new",
            "--title",
            "修改缓存格式",
            "--type",
            "maintenance",
            "--risk",
            "low",
            "--impact",
            "local",
            "--data-change",
            "--owner",
            "agent/test",
        )
        self.assertIn("从 low 自动升级为 medium", result.stdout)
        work = self.one_work_file("00-work.md").read_text(encoding="utf-8")
        self.assertIn('risk: "medium"', work)
        self.assertIn('required_documents: ["change", "design", "plan", "task", "verify"]', work)
        workflow = self.metadata(self.one_work_file("00-work.md"))["workflow"]
        stages = {stage["stage"]: stage for stage in workflow}
        self.assertEqual("required", stages["release"]["requirement"])
        self.assertEqual("required", stages["observe"]["requirement"])

    def test_product_profile_uses_feature_and_user_experience_flow(self) -> None:
        self.run_work(
            "new",
            "--title",
            "增加题目搜索",
            "--type",
            "product",
            "--risk",
            "medium",
            "--impact",
            "multi-module",
            "--owner",
            "agent/test",
        )
        metadata = self.metadata(self.one_work_file("00-work.md"))
        self.assertEqual(
            ["feature", "experience", "design", "plan", "task", "verify", "memory"],
            metadata["required_documents"],
        )
        labels = [stage["label"] for stage in metadata["workflow"]]
        self.assertEqual(
            [
                "需求澄清",
                "功能定义",
                "体验设计",
                "技术方案",
                "开发计划",
                "开发任务",
                "开发",
                "复核",
                "验证",
                "上线",
                "线上观察",
                "项目记忆",
            ],
            labels,
        )
        self.assertTrue(self.one_work_file("10-feature-FEATURE-001.md").is_file())
        self.assertTrue(self.one_work_file("20-experience-EXPERIENCE-001.md").is_file())

    def test_infra_profile_uses_capability_and_operations_experience_flow(self) -> None:
        self.run_work(
            "new",
            "--title",
            "增加日志采集能力",
            "--type",
            "infra",
            "--risk",
            "medium",
            "--impact",
            "multi-module",
            "--owner",
            "agent/test",
        )
        metadata = self.metadata(self.one_work_file("00-work.md"))
        stages = {stage["stage"]: stage for stage in metadata["workflow"]}
        self.assertEqual("能力定义", stages["definition"]["label"])
        self.assertEqual("开发体验 / 运维要求", stages["experience"]["label"])
        self.assertEqual(["CAPABILITY-001"], stages["definition"]["artifacts"])
        self.assertTrue(self.one_work_file("10-capability-CAPABILITY-001.md").is_file())

    def test_fix_profile_keeps_issue_and_regression_flow_minimal(self) -> None:
        self.run_work(
            "new",
            "--title",
            "修复空提交崩溃",
            "--type",
            "fix",
            "--risk",
            "low",
            "--impact",
            "local",
            "--owner",
            "agent/test",
        )
        metadata = self.metadata(self.one_work_file("00-work.md"))
        self.assertEqual(["issue", "task", "verify"], metadata["required_documents"])
        stages = {stage["stage"]: stage for stage in metadata["workflow"]}
        self.assertNotIn("clarify", stages)
        self.assertNotIn("experience", stages)
        self.assertNotIn("plan", stages)
        self.assertEqual("问题说明、复现与预期", stages["definition"]["label"])
        self.assertEqual("skipped", stages["design"]["status"])
        self.assertEqual("回归验证", stages["verification"]["label"])

    def test_improvement_profile_requires_metrics_observation_and_memory(self) -> None:
        self.run_work(
            "new",
            "--title",
            "降低判题延迟",
            "--type",
            "improvement",
            "--risk",
            "medium",
            "--impact",
            "multi-module",
            "--concern",
            "performance",
            "--owner",
            "agent/test",
        )
        metadata = self.metadata(self.one_work_file("00-work.md"))
        self.assertEqual(
            ["improvement", "design", "plan", "task", "verify", "memory"],
            metadata["required_documents"],
        )
        stages = {stage["stage"]: stage for stage in metadata["workflow"]}
        self.assertEqual("改进说明与目标指标", stages["definition"]["label"])
        self.assertEqual("持续观察", stages["observe"]["label"])
        self.assertIn("performance", stages["observe"]["checks"])
        self.assertEqual("required", stages["memory"]["requirement"])

    def test_high_system_overlay_inserts_decision_memory_and_checks(self) -> None:
        self.run_work(
            "new",
            "--title",
            "重构任务模型",
            "--type",
            "maintenance",
            "--risk",
            "high",
            "--impact",
            "system",
            "--concern",
            "reliability",
            "--owner",
            "agent/test",
        )
        metadata = self.metadata(self.one_work_file("00-work.md"))
        stages = {stage["stage"]: stage for stage in metadata["workflow"]}
        self.assertEqual("overlay:risk-impact", stages["decision"]["source"])
        self.assertEqual("required", stages["memory"]["requirement"])
        self.assertEqual("required", stages["observe"]["requirement"])
        self.assertIn("independent-review", stages["review"]["checks"])
        self.assertIn("rollback", stages["plan"]["checks"])
        self.assertIn("cross-module-regression", stages["verification"]["checks"])
        self.assertIn("reliability", stages["observe"]["checks"])

    def test_check_rejects_workflow_that_does_not_match_profile(self) -> None:
        self.create_fast_work()
        work_path = self.one_work_file("00-work.md")
        content = work_path.read_text(encoding="utf-8")
        content = content.replace('"label": "改动说明与边界"', '"label": "功能定义"', 1)
        work_path.write_text(content, encoding="utf-8")
        result = self.run_work("check", success=False)
        self.assertIn("workflow 阶段顺序或配置与 WORK Type / 风险规则不一致", result.stderr)

    def test_set_stage_handles_only_documentless_operational_stages(self) -> None:
        self.create_fast_work()
        self.run_work(
            "set-stage",
            "WORK-001",
            "release",
            "skipped",
            "--reason",
            "纯仓库维护无需业务上线",
        )
        workflow = self.metadata(self.one_work_file("00-work.md"))["workflow"]
        stages = {stage["stage"]: stage for stage in workflow}
        self.assertEqual("skipped", stages["release"]["status"])
        self.assertEqual("manual", stages["release"]["status_source"])

        result = self.run_work(
            "set-stage",
            "WORK-001",
            "review",
            "skipped",
            "--reason",
            "试图跳过必做复核",
            success=False,
        )
        self.assertIn("必做阶段 review 不能跳过", result.stderr)

        result = self.run_work(
            "set-stage",
            "WORK-001",
            "definition",
            "doing",
            "--reason",
            "试图手工推进文档阶段",
            success=False,
        )
        self.assertIn("有 artifacts 的阶段由文档/TASK/VERIFY 状态推进", result.stderr)

    def test_new_doc_uses_existing_work_directory_and_level(self) -> None:
        self.create_fast_work()
        self.run_work(
            "new-doc",
            "--work",
            "WORK-001",
            "--type",
            "experience",
            "--title",
            "补充开发者体验",
        )
        experience = self.one_work_file("20-experience-EXPERIENCE-001.md")
        self.assertIn('work: "WORK-001"', experience.read_text(encoding="utf-8"))
        self.assertFalse((self.root / "development" / "experiences").exists())

    def test_check_rejects_missing_reference(self) -> None:
        self.create_fast_work()
        task_path = self.one_work_file("60-task-TASK-001.md")
        content = task_path.read_text(encoding="utf-8")
        task_path.write_text(content.replace("related: []", 'related: ["DESIGN-999"]'), encoding="utf-8")
        result = self.run_work("check", success=False)
        self.assertIn("引用了不存在的 DESIGN-999", result.stderr)

    def test_check_rejects_dependency_cycle(self) -> None:
        self.create_fast_work()
        task_path = self.one_work_file("60-task-TASK-001.md")
        verify_path = self.one_work_file("70-verify-VERIFY-001.md")
        task_content = task_path.read_text(encoding="utf-8")
        task_path.write_text(
            task_content.replace('depends_on: ["CHANGE-001"]', 'depends_on: ["CHANGE-001", "VERIFY-001"]'),
            encoding="utf-8",
        )
        result = self.run_work("check", success=False)
        self.assertIn("文档依赖形成环", result.stderr)
        self.assertIn("TASK-001", result.stderr)
        self.assertIn("VERIFY-001", result.stderr)

    def test_check_rejects_wrong_level_and_cross_work_placement(self) -> None:
        self.create_fast_work()
        change_path = self.one_work_file("10-change-CHANGE-001.md")
        wrong_level_path = change_path.with_name("30-change-CHANGE-001.md")
        change_path.rename(wrong_level_path)
        result = self.run_work("check", success=False)
        self.assertIn("文件名必须是 10-change-CHANGE-001.md", result.stderr)

        wrong_level_path.rename(change_path)
        self.run_work(
            "new",
            "--title",
            "整理另一组测试",
            "--type",
            "maintenance",
            "--risk",
            "low",
            "--impact",
            "local",
            "--owner",
            "agent/test",
        )
        task_path = self.one_work_file("60-task-TASK-001.md")
        moved_path = self.one_work_directory("WORK-002") / task_path.name
        task_path.rename(moved_path)
        result = self.run_work("check", success=False)
        self.assertIn("必须与所属 WORK-001 位于同一工作项目录", result.stderr)

    def test_check_rejects_legacy_type_directory(self) -> None:
        self.create_fast_work()
        (self.root / "development" / "tasks").mkdir()
        result = self.run_work("check", success=False)
        self.assertIn("development/tasks", result.stderr)
        self.assertIn("development 顶层只允许", result.stderr)

    def test_check_rejects_slug_in_work_directory(self) -> None:
        self.create_fast_work()
        self.one_work_directory().rename(
            self.root / "development" / "works" / "WORK-001-local-tests"
        )
        result = self.run_work("check", success=False)
        self.assertIn("工作项目录名必须匹配 WORK-001", result.stderr)

    def test_stable_status_rejects_placeholders_and_open_dependencies(self) -> None:
        self.create_fast_work()
        result = self.run_work(
            "set-status",
            "TASK-001",
            "ready",
            "--reason",
            "准备开发",
            success=False,
        )
        self.assertIn("仍有未完成依赖", result.stderr)

        result = self.run_work(
            "set-status",
            "CHANGE-001",
            "approved",
            "--reason",
            "审核通过",
            success=False,
        )
        self.assertIn("不再逐份审批", result.stderr)

        # 占位内容仍然过不去，只是防线移到了闸上。
        self.run_work("set-status", "CHANGE-001", "review", "--reason", "初稿写完")
        result = self.run_work(
            "gate",
            "WORK-001",
            "intent",
            "--reason",
            "审核通过",
            success=False,
        )
        self.assertIn("仍含占位内容", result.stderr)

        result = self.run_work(
            "set-status",
            "TASK-001",
            "done",
            "--reason",
            "试图跳过开发",
            success=False,
        )
        self.assertIn("不能从 todo 进入 done", result.stderr)

    def test_context_contains_only_task_work_and_direct_upstream(self) -> None:
        self.create_fast_work()
        result = self.run_work("context", "TASK-001")
        self.assertIn("# 智能体任务上下文包", result.stdout)
        self.assertIn("WORK-001", result.stdout)
        self.assertIn("CHANGE-001", result.stdout)
        self.assertIn("TASK-001", result.stdout)
        self.assertNotIn("VERIFY-001：", result.stdout)
        self.assertIn("按技术方案补充", result.stdout)

    def test_index_is_monotonic_across_multiple_creations(self) -> None:
        self.create_fast_work()
        self.run_work(
            "new",
            "--title",
            "整理另一组测试",
            "--type",
            "maintenance",
            "--risk",
            "low",
            "--impact",
            "local",
            "--owner",
            "agent/test",
        )
        index = json.loads((self.root / "development" / "index.json").read_text(encoding="utf-8"))
        self.assertEqual(3, index["next_ids"]["work"])
        self.assertEqual(3, index["next_ids"]["task"])
        self.assertTrue(self.one_work_file("00-work.md", "WORK-002").is_file())

    def test_ready_work_cannot_have_blockers(self) -> None:
        self.run_work(
            "new",
            "--title",
            "等待决定的功能",
            "--type",
            "product",
            "--risk",
            "medium",
            "--impact",
            "multi-module",
            "--owner",
            "agent/test",
            "--blocking",
            "确认权限范围",
        )
        work_path = self.one_work_file("00-work.md")
        workflow = self.metadata(work_path)["workflow"]
        self.assertEqual("blocked", workflow[0]["status"])
        content = work_path.read_text(encoding="utf-8")
        work_path.write_text(content.replace('status: "todo"', 'status: "ready"', 1), encoding="utf-8")
        result = self.run_work("check", success=False)
        self.assertIn("不允许存在 blocking_items", result.stderr)

    def test_check_rejects_stale_works_index(self) -> None:
        self.create_fast_work()
        index_path = self.root / "development" / "WORKS.md"
        index_path.write_text("# 过期内容\n", encoding="utf-8")
        result = self.run_work("check", success=False)
        self.assertIn("WORKS.md: 内容与工作项不一致", result.stderr)

        self.run_work("sync-works")
        checked = self.run_work("check")
        self.assertIn("4 份开发文档通过校验", checked.stdout)

    def test_archive_keeps_document_in_work_directory(self) -> None:
        self.create_fast_work()
        change_path = self.one_work_file("10-change-CHANGE-001.md")
        content = change_path.read_text(encoding="utf-8")
        content = content.replace("待补充", "已确认无需补充")
        change_path.write_text(content, encoding="utf-8")

        self.run_work("set-status", "CHANGE-001", "review", "--reason", "初稿写完")
        self.run_work("gate", "WORK-001", "intent", "--reason", "确认改动范围")
        self.assertIn('status: "approved"', change_path.read_text(encoding="utf-8"))
        self.run_work("rebuild-flow", "WORK-001")
        self.run_work("archive", "CHANGE-001", "--reason", "仅保留历史参考")
        self.assertTrue(change_path.is_file())
        self.assertIn('status: "archived"', change_path.read_text(encoding="utf-8"))
        self.assertFalse((self.root / "development" / "archive").exists())


    def create_full_work(self) -> None:
        """高风险维护会生成两类文档：决定类（CHANGE/DECISION）与记录类（DESIGN/PLAN/MEMORY）。"""
        self.run_work(
            "new",
            "--title",
            "高风险维护",
            "--type",
            "maintenance",
            "--risk",
            "high",
            "--impact",
            "system",
            "--owner",
            "agent/test",
        )

    def fill(self, *document_ids: str) -> None:
        """补全占位内容并声明写完，模拟智能体交付初稿。"""
        for document_id in document_ids:
            path = next(self.one_work_directory().glob(f"*-{document_id}.md"))
            content = path.read_text(encoding="utf-8")
            for marker in ("待补充", "待填写", "示例内容", "TODO"):
                content = content.replace(marker, "已确认内容")
            path.write_text(content, encoding="utf-8")
            self.run_work("set-status", document_id, "review", "--reason", "初稿写完")

    def status_of(self, document_id: str) -> str:
        path = next(self.one_work_directory().glob(f"*-{document_id}.md"))
        return str(self.metadata(path)["status"])

    def test_intent_gate_settles_every_decision_document_at_once(self) -> None:
        self.create_full_work()
        self.fill("CHANGE-001", "DESIGN-001", "DECISION-001")
        self.assertEqual(
            {"intent": "pending", "acceptance": "pending"},
            self.metadata(self.one_work_file("00-work.md"))["gates"],
        )

        result = self.run_work("gate", "WORK-001", "intent", "--reason", "确认要做的事和边界")
        # 一次签署覆盖全部决定类文档，这正是把审批点从「每份文档」收拢到「每个工作」的含义。
        self.assertIn("CHANGE-001→approved", result.stdout)
        self.assertIn("DECISION-001→approved", result.stdout)
        self.assertEqual("approved", self.status_of("CHANGE-001"))
        self.assertEqual("approved", self.status_of("DECISION-001"))
        # 决定类与记录类在依赖链上交替，闸必须在同一次事务里把两者都推进到位。
        self.assertEqual("checked", self.status_of("DESIGN-001"))
        self.assertEqual(
            "passed",
            self.metadata(self.one_work_file("00-work.md"))["gates"]["intent"],
        )

    def test_decision_documents_cannot_be_approved_one_by_one(self) -> None:
        self.create_full_work()
        self.fill("CHANGE-001")
        result = self.run_work(
            "set-status", "CHANGE-001", "approved", "--reason", "想逐份批", success=False
        )
        self.assertIn("不再逐份审批", result.stderr)
        self.assertIn("gate WORK-001 intent", result.stderr)
        self.assertEqual("review", self.status_of("CHANGE-001"))

    def test_record_documents_are_settled_by_the_tool_not_by_a_person(self) -> None:
        self.create_full_work()
        self.fill("CHANGE-001", "DESIGN-001", "DECISION-001", "PLAN-001")
        result = self.run_work("gate", "WORK-001", "intent", "--reason", "确认边界")
        # 记录类文档只陈述事实，由工具定稿，不出现在任何人工确认点上。
        for document_id in ("DESIGN-001", "PLAN-001"):
            self.assertEqual("checked", self.status_of(document_id), document_id)
            self.assertIn(f"{document_id}→checked", result.stdout)
        # checked 只属于记录类；决定类不能借这条路径绕过闸。
        result = self.run_work(
            "set-status", "VERIFY-001", "checked", "--reason", "想绕过验收闸", success=False
        )
        self.assertIn("checked 只用于记录类文档", result.stderr)

    def test_acceptance_gate_requires_intent_gate_and_finished_tasks(self) -> None:
        self.create_full_work()
        self.fill("VERIFY-001")
        result = self.run_work(
            "gate", "WORK-001", "acceptance", "--reason", "想直接收尾", success=False
        )
        self.assertIn("意图闸尚未通过", result.stderr)
        self.assertIn("TASK-001", result.stderr)

    def test_revoking_a_gate_returns_its_documents_to_review(self) -> None:
        self.create_full_work()
        self.fill("CHANGE-001", "DESIGN-001", "DECISION-001")
        self.run_work("gate", "WORK-001", "intent", "--reason", "确认边界")

        result = self.run_work("gate", "WORK-001", "intent", "--revoke", "--reason", "边界说错了")
        self.assertIn("CHANGE-001→review", result.stdout)
        self.assertEqual("review", self.status_of("CHANGE-001"))
        self.assertEqual("review", self.status_of("DECISION-001"))
        self.assertEqual(
            "pending",
            self.metadata(self.one_work_file("00-work.md"))["gates"]["intent"],
        )
        self.run_work("check")


if __name__ == "__main__":
    unittest.main(verbosity=2)
