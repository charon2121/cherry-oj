---
id: "WORK-057"
type: "work"
title: "修复认证数据库回归的时钟夹具漂移"
status: "doing"
work: null
owners: ["team/server"]
risk: "medium"
impact: "local"
concerns: ["reliability"]
depends_on: []
related: ["ISSUE-019", "TASK-124", "VERIFY-058", "WORK-053", "WORK-056"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "reliability"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-12"
updated_at: "2026-09-12"
work_type: "fix"
---

# WORK-057：修复认证数据库回归的时钟夹具漂移

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-019 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 修复任务 | ✔ 完成 | 必需 | TASK-124 `doing` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ▶ 进行中 | 必需 | TASK-124 `doing` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | · 未开始 | 必需 | VERIFY-058 `draft` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-12：创建工作项并生成初始流程。
- 2026-09-12：意图闸：passed。原因：确认仅修复认证测试时钟夹具，允许实施
- 2026-09-12：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-12：检查项 impact-analysis 记录结论：通过。原因：仅指定测试夹具，四组纳秒及原会话断言不变，生产约束和预算未改
