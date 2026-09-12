---
id: "WORK-055"
type: "work"
title: "分离CI软件包准备与回归测试"
status: "doing"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security"]
depends_on: []
related: ["IMPROVEMENT-005", "DESIGN-049", "DECISION-033", "PLAN-039", "TASK-119", "VERIFY-056", "MEMORY-042", "TASK-120", "WORK-050", "WORK-054"]
implements: []
verifies: []
tags: []
required_documents: ["improvement", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-11"
updated_at: "2026-09-11"
work_type: "improvement"
---

# WORK-055：分离CI软件包准备与回归测试

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-055 `doing` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 改进说明与目标指标 | ✔ 完成 | 必需 | IMPROVEMENT-005 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-049 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-033 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-039 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 实施任务 | ✔ 完成 | 必需 | TASK-119 `done`、TASK-120 `doing` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 实施 | ▶ 进行中 | 必需 | TASK-119 `done`、TASK-120 `doing` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | · 未开始 | 必需 | VERIFY-056 `draft` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | · 未开始 | 必需 | MEMORY-042 `draft` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-11：创建工作项并生成初始流程。
- 2026-09-11：意图闸：passed。原因：确认统一包准备、缓存和独立冷下载方案，允许实施
- 2026-09-11：检查项 rollback 记录结论：通过。原因：回退只恢复本批workflow和prepare接线；无需迁移或清除用户数据，原600秒路径保留
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：work055_review复核TASK-119及120；发布后回收P2修复，独立20项包测试与8项workflow测试通过；Linux运行待发布授权
- 2026-09-11：检查项 impact-analysis 记录结论：通过。原因：仅批准的CI工作流/包准备与证据接线；默认部署下载、包锁、apps与测试服务限额未改，WORK-050既有错误保留
- 2026-09-11：根据文档、任务与验证事实刷新状态：todo → doing。
