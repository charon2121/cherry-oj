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

    def workflow(self, work_id: str = "WORK-001") -> list[dict]:
        """控制面存在工作项目录的 flow.json，不再放进人编辑的 front matter。"""
        path = self.one_work_directory(work_id) / "flow.json"
        self.assertTrue(path.is_file(), path)
        return json.loads(path.read_text(encoding="utf-8"))

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
                "flow.json",
            ],
            sorted(path.name for path in work_directory.iterdir()),
        )
        work = self.one_work_file("00-work.md").read_text(encoding="utf-8")
        self.assertIn('required_documents: ["change", "task", "verify"]', work)
        workflow = self.workflow()
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
        stages = {stage["stage"]: stage for stage in self.workflow()}
        # MVP 没有生产环境，任何升级规则都不得再引入上线与线上观察。
        self.assertNotIn("release", stages)
        self.assertNotIn("observe", stages)

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
        labels = [stage["label"] for stage in self.workflow()]
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
        stages = {stage["stage"]: stage for stage in self.workflow()}
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
        stages = {stage["stage"]: stage for stage in self.workflow()}
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
        stages = {stage["stage"]: stage for stage in self.workflow()}
        self.assertEqual("改进说明与目标指标", stages["definition"]["label"])
        self.assertNotIn("observe", stages)
        self.assertIn("performance", stages["verification"]["checks"])
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
        stages = {stage["stage"]: stage for stage in self.workflow()}
        self.assertEqual("overlay:risk-impact", stages["decision"]["source"])
        self.assertEqual("required", stages["memory"]["requirement"])
        self.assertNotIn("observe", stages)
        self.assertIn("independent-review", stages["review"]["checks"])
        self.assertIn("rollback", stages["plan"]["checks"])
        self.assertIn("cross-module-regression", stages["verification"]["checks"])
        self.assertIn("reliability", stages["verification"]["checks"])

    def test_check_rejects_workflow_that_does_not_match_profile(self) -> None:
        self.create_fast_work()
        flow_path = self.one_work_directory() / "flow.json"
        content = flow_path.read_text(encoding="utf-8")
        content = content.replace('"label": "改动说明与边界"', '"label": "功能定义"', 1)
        flow_path.write_text(content, encoding="utf-8")
        result = self.run_work("check", success=False)
        self.assertIn("workflow 阶段顺序或配置与 WORK Type / 风险规则不一致", result.stderr)

    def test_set_stage_handles_only_documentless_operational_stages(self) -> None:
        self.create_fast_work()
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
        workflow = self.workflow()
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


    def test_definition_documents_open_with_the_plain_language_entry(self) -> None:
        self.create_fast_work()
        change_path = self.one_work_file("10-change-CHANGE-001.md")
        body = change_path.read_text(encoding="utf-8")
        self.assertIn("## 为什么做", body)
        # 定义层是产品面入口，非技术读者读到的第一节必须是它。
        self.assertLess(body.index("## 为什么做"), body.index("## 当前状态"))

        # 只校验“存在”挡不住把入口挪到文末，因此顺序也必须被钉住。
        moved = body.replace("## 为什么做", "## 曾经的为什么做", 1)
        moved += "\n## 为什么做\n\n补在最后。\n"
        change_path.write_text(moved, encoding="utf-8")
        result = self.run_work("check", success=False)
        self.assertIn("第一节必须是", result.stderr)


    def test_work_entry_holds_only_the_control_plane(self) -> None:
        self.create_fast_work()
        body = self.one_work_file("00-work.md").read_text(encoding="utf-8")
        # 产品面内容归定义层；同一个问题在两处各自表述一定会漂移。
        for absent in ("## 为什么做", "## 成功标准", "## 风险点", "## 影响面", "## 关联文档"):
            self.assertNotIn(absent, body)
        self.assertIn("## 流程", body)
        # 流程图对一条线性阶段链没有表格之外的信息，只是多一处要同步的地方。
        self.assertNotIn("```mermaid", body)
        self.assertIn("| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |", body)
        # 控制面是机器状态，不放进人编辑的 front matter。
        self.assertNotIn("workflow:", body.split("---", 2)[1])

    def test_flow_view_is_generated_and_repairable(self) -> None:
        self.create_fast_work()
        path = self.one_work_file("00-work.md")
        original = path.read_text(encoding="utf-8")
        self.assertIn("· 未开始", original)
        path.write_text(original.replace("· 未开始", "✔ 完成"), encoding="utf-8")

        # 手改视图会被打回：过期的流程图看起来和最新的一模一样，最危险。
        result = self.run_work("check", success=False)
        self.assertIn("与实际控制面不一致", result.stderr)

        # 元数据没有任何变化，refresh 仍然必须能把视图原样修回去，
        # 且不因为一次修复在变更记录里留下噪音。
        self.run_work("refresh", "WORK-001")
        self.run_work("check")
        self.assertEqual(original, path.read_text(encoding="utf-8"))


    def test_no_profile_introduces_release_or_observe(self) -> None:
        """MVP 没有生产环境：上线与线上观察在任何组合下都不该出现。

        它们此前是必需阶段，而必需阶段不能标 skipped，于是只能手工标 blocked——
        18 个必需实例只完成过 1 次，「完成定义」对绝大多数工作永远无法闭合。
        """
        combinations = (
            ("product", "high", "system", ("--data-change", "--public-api-change")),
            ("infra", "medium", "multi-module", ("--security-sensitive",)),
            ("fix", "low", "local", ()),
            ("maintenance", "low", "local", ()),
            ("improvement", "high", "system", ("--user-visible",)),
        )
        for index, (kind, risk, impact, flags) in enumerate(combinations, start=1):
            with self.subTest(work_type=kind):
                self.run_work(
                    "new", "--title", f"验证 {kind}", "--type", kind,
                    "--risk", risk, "--impact", impact,
                    "--concern", "release", "--concern", "performance",
                    "--owner", "agent/test", *flags,
                )
                work_id = f"WORK-{index:03d}"
                stages = {s["stage"] for s in self.workflow(work_id)}
                self.assertNotIn("release", stages)
                self.assertNotIn("observe", stages)

    def test_verified_is_the_terminal_work_status(self) -> None:
        self.create_fast_work()
        result = self.run_work(
            "set-status", "WORK-001", "released", "--reason", "试图上线", success=False
        )
        self.assertIn("不支持状态 released", result.stderr)


    def test_board_separates_anchored_from_document_level_coverage(self) -> None:
        """把「锚定到条目」和「只引用了文档」混为一谈，追踪链看起来永远是满的。"""
        self.create_fast_work()
        change_path = self.one_work_file("10-change-CHANGE-001.md")
        change_path.write_text(
            change_path.read_text(encoding="utf-8").replace(
                "- REQ-002：待补充外部行为和关键边界中不能改变的部分。",
                "- REQ-002：外部行为保持不变。\n- REQ-003：命令行为保持不变。",
            ),
            encoding="utf-8",
        )
        board = self.run_work("board", "WORK-001").stdout
        self.assertIn("CHANGE-001 · 4 条", board)
        # 模板生成的 TASK 只有文档级 implements，三条要求都还没有人锚定认领。
        self.assertIn("锚定认领 0/4", board)
        self.assertIn("认领 ~ TASK-001", board)

        task_path = self.one_work_file("60-task-TASK-001.md")
        task_path.write_text(
            task_path.read_text(encoding="utf-8").replace(
                'implements: ["CHANGE-001"]', 'implements: ["CHANGE-001#REQ-001"]'
            ),
            encoding="utf-8",
        )
        board = self.run_work("board", "WORK-001").stdout
        self.assertIn("锚定认领 1/4", board)
        self.assertIn("REQ-001  认领 ✔ TASK-001", board)
        self.assertIn("REQ-002  认领 ✖", board)

    def test_board_reports_gates_and_next_action(self) -> None:
        self.create_fast_work()
        board = self.run_work("board", "WORK-001").stdout
        self.assertIn("意图闸", board)
        self.assertIn("验收闸", board)
        self.assertIn("下一步:", board)

    def test_trace_keeps_relation_and_target_on_one_line(self) -> None:
        """曾经是广度优先且先打关系再入队，一层的关系行全挤在一起、目标在后面平铺。"""
        self.create_fast_work()
        lines = self.run_work("trace", "WORK-001").stdout.splitlines()
        arrows = [line for line in lines if "↳" in line]
        self.assertTrue(arrows)
        for line in arrows:
            self.assertRegex(line, r"↳ \S+\s+[A-Z]+-\d{3} \[")


    def finish_fast_work(self) -> None:
        """造一个走到 implemented 的工作，供 outcome 用例使用。快速流程只有 CHANGE/TASK/VERIFY。"""
        self.create_fast_work()
        path = self.one_work_file("10-change-CHANGE-001.md")
        path.write_text(path.read_text(encoding="utf-8").replace("待补充", "已确认内容"), encoding="utf-8")
        self.run_work("set-status", "CHANGE-001", "review", "--reason", "写完")
        self.run_work("gate", "WORK-001", "intent", "--reason", "确认边界")
        task_path = self.one_work_file("60-task-TASK-001.md")
        task_path.write_text(
            task_path.read_text(encoding="utf-8").replace("待补充", "已确认内容").replace("- [ ]", "- [x]"),
            encoding="utf-8",
        )
        for status in ("ready", "doing", "done"):
            self.run_work("set-status", "TASK-001", status, "--reason", "推进")
        self.run_work("refresh", "WORK-001")

    def test_outcome_marks_conclusion_without_rewriting_history(self) -> None:
        """被推翻的工作确实走完过流程；把 status 改掉等于篡改历史。"""
        self.finish_fast_work()
        self.assertEqual("implemented", self.metadata(self.one_work_file("00-work.md"))["status"])

        # 判断为什么错，是这类工作唯一的产出；没有 MEMORY 就不许标记。
        result = self.run_work(
            "outcome", "WORK-001", "invalidated", "--reason", "前提不成立", success=False
        )
        self.assertIn("必须留下 MEMORY", result.stderr)
        self.run_work("new-doc", "--work", "WORK-001", "--type", "memory", "--title", "当时为什么判断错")

        result = self.run_work(
            "outcome", "WORK-001", "invalidated", "--reason", "前提不成立"
        )
        self.assertIn("结论已被证伪", result.stdout)
        metadata = self.metadata(self.one_work_file("00-work.md"))
        self.assertEqual("implemented", metadata["status"])
        self.assertEqual("invalidated", metadata["outcome"]["state"])

        # 必须进视图：不显示的字段等于不存在。
        self.assertIn("结论已被证伪", (self.root / "development" / "WORKS.md").read_text(encoding="utf-8"))
        self.assertIn("结论已不成立", self.run_work("overview").stdout)
        self.assertIn("⚠ 结论已被证伪", self.run_work("board", "WORK-001").stdout)

        self.run_work("outcome", "WORK-001", "--clear", "--reason", "判断有误")
        self.assertNotIn("outcome", self.metadata(self.one_work_file("00-work.md")))

    def test_outcome_rejects_work_without_output(self) -> None:
        self.create_fast_work()
        result = self.run_work(
            "outcome", "WORK-001", "invalidated", "--reason", "不做了", success=False
        )
        self.assertIn("没有产出", result.stderr)
        self.assertIn("cancelled", result.stderr)

    def test_superseded_requires_an_existing_successor(self) -> None:
        self.finish_fast_work()
        for by, expected in (
            ("WORK-999", "引用了不存在的工作项"),
            ("WORK-001", "不能指向自己"),
        ):
            with self.subTest(by=by):
                result = self.run_work(
                    "outcome", "WORK-001", "superseded",
                    "--by", by, "--reason", "被取代", success=False,
                )
                self.assertIn(expected, result.stderr)


    def test_audit_reports_gates_that_cannot_fail(self) -> None:
        """§1.3 说复杂度应当来自工作本身；这条要求需要一个能检验它的机制。"""
        self.create_fast_work()
        report = self.run_work("audit").stdout
        self.assertIn("① 分级维度的信息量", report)
        self.assertIn("② 门禁是否可证伪", report)
        self.assertIn("④ 追踪链完整度", report)
        # 检查项只出现在元数据里，没有任何地方记录结论——一个不会失败的检查不是门禁。
        self.assertIn("不会失败，也就不构成门禁", report)


    def test_gates_are_the_only_human_confirmation_mechanism(self) -> None:
        """两套并行的人工确认里，只要有一套没有命令能满足，工作就永远推不动。

        human_confirmations 曾经由规则生成、阻塞状态推导、又必须等于规则重新生成的值，
        于是把 4 个已完成的高风险工作按在 todo 上，任何操作都出不去。
        """
        self.run_work(
            "new", "--title", "安全敏感基建", "--type", "infra",
            "--risk", "high", "--impact", "system", "--security-sensitive",
            "--owner", "agent/test",
        )
        body = self.one_work_file("00-work.md").read_text(encoding="utf-8")
        self.assertNotIn("human_confirmations", body)
        self.assertIn("gates", body)

    def test_audit_detects_states_no_command_can_leave(self) -> None:
        self.create_fast_work()
        report = self.run_work("audit").stdout
        self.assertIn("⑥ 有没有推不动的状态", report)


if __name__ == "__main__":
    unittest.main(verbosity=2)
