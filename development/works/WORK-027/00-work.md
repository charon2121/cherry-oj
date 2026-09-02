---
id: "WORK-027"
type: "work"
title: "把手写基础组件改为基于 shadcn 官方实现"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: ["accessibility", "compatibility"]
depends_on: []
related: ["CHANGE-009", "DESIGN-021", "PLAN-017", "TASK-043", "VERIFY-028", "TASK-044", "TASK-045"]
implements: []
verifies: []
tags: []
required_documents: ["change", "design", "plan", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "accessibility", "compatibility"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-01"
updated_at: "2026-09-02"
work_type: "maintenance"
---

# WORK-027：把手写基础组件改为基于 shadcn 官方实现

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
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-009 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-021 `checked` | 确定技术方案、边界与取舍 |
| 开发计划 | ✔ 完成 | 必需 | PLAN-017 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-043 `done`、TASK-044 `done`、TASK-045 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-043 `done`、TASK-044 `done`、TASK-045 `done` | 按任务实施，产出代码与测试 |
| 复核 | ○ 就绪 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-028 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-01：创建工作项并生成初始流程。
- 2026-09-01：意图闸：passed。原因：负责人审阅后批准：6 个组件换成 shadcn 官方实现文件、7 个已基于 base-ui 的只记录与官方的出入不改代码、link/typography/layout 因官方 registry 无对应而维持手写；调用方用法尽量不动，TASK-044 与 TASK-045 串行执行
- 2026-09-01：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-01：根据文档、任务与验证事实刷新状态：doing → implemented。
