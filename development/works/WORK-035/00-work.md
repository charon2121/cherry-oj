---
id: "WORK-035"
type: "work"
title: "收敛设计系统为单一真源"
status: "implemented"
work: null
owners: ["claude/root"]
risk: "medium"
impact: "multi-module"
concerns: []
depends_on: []
related: ["CHANGE-010", "DESIGN-029", "PLAN-023", "TASK-056", "VERIFY-036", "TASK-057", "TASK-058"]
implements: []
verifies: []
tags: []
required_documents: ["change", "design", "plan", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-03"
updated_at: "2026-09-03"
work_type: "maintenance"
---

# WORK-035：收敛设计系统为单一真源

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
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-010 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-029 `checked` | 确定技术方案、边界与取舍 |
| 开发计划 | ✔ 完成 | 必需 | PLAN-023 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-056 `done`、TASK-057 `done`、TASK-058 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-056 `done`、TASK-057 `done`、TASK-058 `done` | 按任务实施，产出代码与测试 |
| 复核 | ○ 就绪 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-036 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-03：创建工作项并生成初始流程。
- 2026-09-03：意图闸：passed。原因：确认收敛范围与三阶段顺序
- 2026-09-03：根据文档、任务与 WORK 事实刷新流程阶段状态。
- 2026-09-03：根据文档、任务与验证事实刷新状态：todo → implemented。
