---
id: "WORK-049"
type: "work"
title: "按命令执行顺序重构 Go 判题引擎源码"
status: "todo"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability"]
depends_on: []
related: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-104", "VERIFY-050", "MEMORY-036", "TASK-105", "TASK-106", "TASK-107", "WORK-050"]
implements: []
verifies: []
tags: []
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability"]
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-10"
updated_at: "2026-09-10"
work_type: "maintenance"
---

# WORK-049：按命令执行顺序重构 Go 判题引擎源码

<!--
本文件是工作项的控制面入口，只回答一个问题：做到哪一步了。

「为什么做、怎样算完成、有什么风险、影响哪里」属于定义层文档（FEATURE / CAPABILITY / ISSUE /
CHANGE / IMPROVEMENT），不要在这里重复。同一个问题在两处各自表述一定会漂移，而本文件既不在
信息优先级链上，也不携带 REQ / AC 锚点，冲突时无法判定以谁为准。

「流程」一节由 `scripts/work` 生成，请勿手工编辑；阶段状态的真源是各文档、TASK 与 VERIFY
自己的状态，这里只是视图。
-->

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 改动说明与边界 | ▶ 进行中 | 必需 | CHANGE-013 `review` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ▶ 进行中 | 必需 | DESIGN-043 `review` | 确定技术方案、边界与取舍 |
| 技术决策 | ▶ 进行中 | 必需 | DECISION-027 `review` |  |
| 开发计划 | ▶ 进行中 | 必需 | PLAN-033 `review` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | · 未开始 | 必需 | TASK-104 `todo`、TASK-105 `todo`、TASK-106 `todo`、TASK-107 `todo` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | · 未开始 | 必需 | TASK-104 `todo`、TASK-105 `todo`、TASK-106 `todo`、TASK-107 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | · 未开始 | 必需 | VERIFY-050 `draft` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | · 未开始 | 必需 | MEMORY-036 `draft` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- 请审核 [CHANGE-013](10-change-CHANGE-013.md) 与 [DESIGN-043](30-design-DESIGN-043.md)：是否以命令执行顺序和数字可解释性作为重构验收目标。
- [DECISION-027](40-decision-DECISION-027.md) 提议本次保留现有进程/权限模型，helper 架构重选另行评估；当前未由人确认。
- DESIGN-043 的候选编码规则只供审阅，尚不提升为全局规范。
- 用户本轮仅要求文档。TASK 全部保持 todo；本轮交付后停止，意图闸和实施授权留给后续明确决定。

## 变更记录

- 2026-09-10：创建工作项并生成初始流程。
