---
id: "WORK-036"
type: "work"
title: "建立页面构图层并修复前景色层级"
status: "todo"
work: null
owners: ["claude/root"]
risk: "high"
impact: "system"
concerns: ["accessibility"]
depends_on: []
related: ["IMPROVEMENT-003", "DESIGN-030", "DECISION-020", "PLAN-024", "TASK-059", "VERIFY-037", "MEMORY-028", "TASK-060", "TASK-061", "TASK-062", "TASK-063"]
implements: []
verifies: []
tags: []
required_documents: ["improvement", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "accessibility"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-09-03"
updated_at: "2026-09-03"
work_type: "improvement"
---

# WORK-036：建立页面构图层并修复前景色层级

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-036 `todo` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 改进说明与目标指标 | ✔ 完成 | 必需 | IMPROVEMENT-003 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-030 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-020 `approved` |  |
| 开发计划 | ▶ 进行中 | 必需 | PLAN-024 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 实施任务 | · 未开始 | 必需 | TASK-059 `done`、TASK-060 `done`、TASK-061 `done`、TASK-062 `done`、TASK-063 `todo` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 实施 | · 未开始 | 必需 | TASK-059 `done`、TASK-060 `done`、TASK-061 `done`、TASK-062 `done`、TASK-063 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | · 未开始 | 必需 | VERIFY-037 `draft` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | · 未开始 | 必需 | MEMORY-028 `draft` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-03：创建工作项并生成初始流程。
- 2026-09-03：意图闸：passed。原因：意图闸通过，开始工作
