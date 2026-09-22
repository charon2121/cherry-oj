---
id: "WORK-060"
type: "work"
title: "收敛个人 Agent 开发的审核材料与默认文档"
status: "todo"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: []
depends_on: []
related: ["VERIFY-061"]
implements: []
verifies: []
tags: []
required_documents: ["verify"]
required_checks: ["definition", "scope", "impact-analysis", "automated-tests"]
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
read_paths: ["AGENTS.md", "development", "scripts", "docs"]
write_paths: ["AGENTS.md", "development/README.md", "development/SPECIFICATION.md", "development/specification", "development/templates", "development/schema/document.schema.json", "development/index.json", "development/WORKS.md", "development/works/WORK-060", "scripts/work", "scripts/work_test.py"]
forbidden_paths: ["apps", "contracts", "deploy", ".github"]
created_at: "2026-09-22"
updated_at: "2026-09-22"
format: "compact"
work_type: "maintenance"
---

# WORK-060：收敛个人 Agent 开发的审核材料与默认文档

## 变化

每个需求不再默认生成整套文档。新工作用一份主文档说明要改变什么、需要接受什么代价，另一份证据展示实际结果。你可以先读完需要判断的内容，再按需查看技术细节。

## 边界

新工作采用精简格式；历史工作、签署、证据和路径保持原样。两道人工闸、实施范围、风险检查和验证证据保留。不改产品功能，不迁移既有工作。提交与推送按用户后续明确授权执行。

## 取舍

主文档同时承载定义与执行方案，用章节区分职责；只有复杂方案、重要取舍、任务依赖或独立委派时才拆文件。继续沿用 00-work.md 与 VERIFY 文件名，避免更名产生额外迁移。你批准明确展示的目标、边界与取舍，不表示审查过全部技术细节。

## 未知

没有阻塞本次实现的未知。实际阅读负担是否下降，仍需在后续小修复、界面变化和跨模块工作中试用，自动测试不能替代你的使用反馈。

## 验收

- AC-001：新工作默认只生成主文档与证据；高风险保留复核、回退和回归检查，附件按需创建。
- AC-002：审核入口直接展示主文档的五项判断内容，交付展示实际结果、差异、验证和遗留问题；可按需展开细节。
- AC-003：无独立任务也能完成实施到人工验收的流程；未授权、失败检查、缺失证据和越界任务不能被放行。
- AC-004：历史工作继续可读、可校验，规则、模板、工具与测试一致。

## 执行方案

用 format=compact 区分新格式，缺省或 layered 继续走原规则。保留原文件命名与编号，新增精简模板。
改动 CLI 创建、状态、闸、视图、上下文与校验；独立 TASK 只能收窄主工作范围，附件 checked 不代表人工逐份批准。
同步 AGENTS.md、README、规范与 Schema。用临时目录端到端测试覆盖生命周期与拒绝路径，并校验真实历史文档。
回退时整体撤销本工作工具与规则改动，精简工作记录先保留，不能用旧工具处理 compact 后假装格式兼容。
本次授权来自用户在讨论具体方案后的“可以，你可以直接改动”；允许直接实施已确认方案。
两道正式闸仍由人签署，本记录不伪造签署事实；技术结果与验证见 VERIFY-061。

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 确认目标与边界 | ○ 就绪 | 必需 | WORK-060 `todo` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 实施 | · 未开始 | 必需 | WORK-060 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证与验收 | ▶ 进行中 | 必需 | VERIFY-061 `review` | 用可复现的证据确认要求逐条满足 |

## 变更记录

- 2026-09-22：用户在审阅优化方案后明确授权直接改动；以本工作记录范围与依据，不代签人工闸。
- 2026-09-22：用户明确要求整理提交并进行 CI 验证；本工作独立提交，随后提交 WORK-058 修复，正式闸记录保持原状。
