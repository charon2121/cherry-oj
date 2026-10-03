---
id: "WORK-062"
type: "work"
title: "将开发文档流程改为明确调用的仓库 skill"
status: "todo"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: []
depends_on: []
related: ["VERIFY-063", "DESIGN-053"]
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
read_paths: ["AGENTS.md", "CLAUDE.md", "README.md", "development", "scripts", "docs", ".githooks", ".github/workflows/ci.yml", ".agents/skills"]
write_paths: [".agents/skills/dev-work", "AGENTS.md", "README.md", "development/README.md", "development/SPECIFICATION.md", "development/specification", "development/templates", "development/schema", "development/index.json", "development/WORKS.md", "development/works/WORK-062", "development/work-items", "scripts/work", "scripts/work_test.py", "scripts/docs_test.py", "scripts/docs_test_test.py", "docs/README.md", "docs/product.md", "docs/design-system.md", "docs/git-workflow.md", "docs/coding-standards/README.md", "docs/coding-standards/project-conventions.md", "docs/coding-standards/languages/typescript.md"]
forbidden_paths: ["apps", "contracts", "deploy", ".github", ".githooks"]
created_at: "2026-10-03"
updated_at: "2026-10-03"
format: "compact"
work_type: "maintenance"
---

# WORK-062：将开发文档流程改为明确调用的仓库 skill

## 变化

以后只有你明确调用 `$dev-work`，才启用开发文档流程。普通开发请求直接按项目规则处理。
启用后，每个工作有一份几分钟能读完的说明：为什么做、打算怎么解决、影响什么、怎样算完成。

## 边界

保留功能、修复、基建、维护、改进五类流程，技术材料交给 Agent 按类型准备。旧文档与历史确认原样保留，
代码规范、契约和测试要求继续生效。这个 skill 只用于本仓库，不改产品代码。

## 取舍

把流程从全局规则移到仓库 skill。你默认只看简短工作说明，方案细节和验证证据按需查看。
新工作用聊天完成开工和收束确认，取消强制编号、状态机和手动签闸；代价是减少机器对流程完整性的强制校验，
需要 Agent 如实维护依据、范围和证据。

## 未知

你已选择仓库专用。上述简化幅度和五类流程方案尚待本轮审核；实际阅读是否轻松，需要后续使用反馈。

## 验收

- AC-001：未调用 skill 时不创建或强制维护工作文档；明确调用后才启用。
- AC-002：五类任务的步骤、必需材料和附件触发条件都有明确规则。
- AC-003：每个工作默认只给人一份简短说明，重要风险和待确认问题不藏在附件中。
- AC-004：新流程可用聊天确认并完成交付，实际验证与人工接受仍能区分。
- AC-005：旧文档、工具兼容和通用工程约束保留，普通开发不被旧流程间接强制启动。

## 执行方案

详细实施方案与五类任务矩阵见 [DESIGN-053](./30-design-DESIGN-053.md)。
本轮只准备文档；按变更前规则保留 WORK、证据占位和两道待签闸，不把拟议的新流程提前当成授权。
后续明确通过并允许实施后，再创建 skill、收拢全局入口、兼容新旧记录并验证。
回退按本工作实际差异整体撤销，新工作记录保留；不改写历史签署，不提交或推送。

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 确认目标与边界 | ○ 就绪 | 必需 | DESIGN-053 `review`、WORK-062 `todo` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 实施 | · 未开始 | 必需 | WORK-062 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证与验收 | · 未开始 | 必需 | VERIFY-063 `draft` | 用可复现的证据确认要求逐条满足 |

## 变更记录

- 2026-10-03：创建。签署与重要变更在此记录，测试证据只写 VERIFY。
- 2026-10-03：用户要求将文档流程降级为明确调用的 skill，保留分类流程，并降低工作说明的阅读负担；已选择仓库专用。本轮整理方案，尚未实施。
