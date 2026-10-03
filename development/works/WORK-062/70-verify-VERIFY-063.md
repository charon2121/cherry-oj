---
id: "VERIFY-063"
type: "verify"
title: "将开发文档流程改为明确调用的仓库 skill"
status: "draft"
work: "WORK-062"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: ["WORK-062"]
tags: []
result: "pending"
created_at: "2026-10-03"
updated_at: "2026-10-03"
format: "compact"
---

# VERIFY-063：将开发文档流程改为明确调用的仓库 skill

## 实际结果

本轮已整理短工作主文和实施方案，明确仓库专用 skill、主动调用、五类任务材料、短说明写法、
聊天确认及历史兼容。尚未创建 skill 或修改规则与工具；本工作仍为 todo，正式闸保持 pending。

## 承诺差异

本轮仅交付拟议方案，不将文档准备描述为 WORK-062 的实现完成。是否接受取消新工作状态机与手动签闸，
由人审核主文后决定；尚未实施的行为不能计作验收通过。

## 验证情况

512 份开发文档结构校验通过，588 份 Markdown 入口与本地链接在临时 Git 索引中校验通过。
未运行 skill 行为检查或修改工具的测试，因为本轮没有实现改动。
文风和阅读负担需要人工评价，结构校验不能证明说明好读。

## 遗留问题

等待方案审核与后续实施授权。历史 WORK-033、WORK-058 的两条状态提示保持原样。
新 skill 的发现、调用策略和完整生命周期仍需实施后验证。

## 检查与结果

2026-10-03，本机仓库根目录：

- 已阅读现行开发文档入口、分类规范、全局规则、skill 创建指南与显式调用策略说明；只读核对 hooks、CI 和间接 WORK/TASK 入口。
- `scripts/work overview`：原有 61 个工作，确认已有 WORK-060 的精简材料调整与本次主动调用目标不同，单独创建 WORK-062。
- `scripts/work check`：新增工作主文、方案与证据后，512 份开发文档通过；仅有上述两条历史提示。
- `git diff --check`：已跟踪改动无空白错误；新文档另外核对正文与引用。
- 临时 `GIT_INDEX_FILE`、`GIT_OBJECT_DIRECTORY` 与只读对象借用：以 HEAD 建立临时索引，将本工作文件标为 intent-to-add 后运行 `python3 scripts/docs_test.py`，588 份 Markdown 入口与本地链接有效；临时索引下的 `git diff --check` 也通过，临时目录已删除。
- 本轮没有产品代码、规则实现、skill 文件、真实 Git 暂存、提交、推送或人工闸签署。
