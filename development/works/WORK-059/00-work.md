---
id: "WORK-059"
type: "work"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "todo"
work: null
owners: ["team/server"]
risk: "medium"
impact: "multi-module"
concerns: ["observability"]
depends_on: []
related: ["ISSUE-020", "DESIGN-052", "TASK-132", "VERIFY-060"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "observability"]
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-15"
updated_at: "2026-09-15"
work_type: "fix"
---

# WORK-059：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

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
| 问题说明、复现与预期 | ▶ 进行中 | 必需 | ISSUE-020 `review` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | · 未开始 | 必需 | DESIGN-052 `draft` | 确定技术方案、边界与取舍 |
| 修复任务 | · 未开始 | 必需 | TASK-132 `todo` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | · 未开始 | 必需 | TASK-132 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | · 未开始 | 必需 | VERIFY-060 `draft` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-15：创建工作项并生成初始流程。
- 2026-09-15：检查项 definition 记录结论：通过。原因：现象、证据可得性限制、已排除的身份链路假设、已定位的唯一 500 产出点（gateway ApiProblemHandler.handleUnexpected）、以及分三步且前两步不依赖复现的修复方向均已写明；无稳定复现方式这一点也已如实写出，不以推测代替
- 2026-09-15：检查项 scope 记录结论：通过。原因：范围划清为三步：第一步只动 deploy/sandbox-linux/ci 的证据导出，不碰 Java；第二步与第三步改 apps/server，需本工作自己的授权。第三步明确要求拿到带证据的失败后再动手，不接受推测性改动，也不接受「重跑不再复现」作为修复证据
