---
id: "WORK-044"
type: "work"
title: "题目内自定义输入运行"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["security", "privacy", "reliability", "performance"]
depends_on: []
related: ["FEATURE-012", "EXPERIENCE-020", "DESIGN-038", "DECISION-025", "PLAN-030", "TASK-085", "VERIFY-045", "MEMORY-034", "TASK-086", "TASK-087", "TASK-088", "DESIGN-039", "TASK-089"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "performance", "privacy", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: true
security_sensitive: true
user_visible: true
created_at: "2026-09-07"
updated_at: "2026-09-09"
work_type: "product"
---

# WORK-044：题目内自定义输入运行

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-044 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | ✔ 完成 | 必需 | FEATURE-012 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | ✔ 完成 | 必需 | EXPERIENCE-020 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-038 `checked`、DESIGN-039 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-025 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-030 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-085 `done`、TASK-086 `done`、TASK-087 `done`、TASK-088 `done`、TASK-089 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-085 `done`、TASK-086 `done`、TASK-087 `done`、TASK-088 `done`、TASK-089 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-045 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-034 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-07：创建工作项并生成初始流程。
- 2026-09-08：意图闸：passed。原因：自定义输入运行方案审核通过，确认功能范围、页面交互与技术方案，允许开始实施
- 2026-09-08：检查项 rollback 记录结论：通过。原因：独立自测开关和兼容可选字段可撤回，无数据迁移，不影响正式提交
- 2026-09-08：检查项 automated-tests 记录结论：未通过。原因：常规Java/Go/Web及27项E2E通过，但显式Linux大输出隔离回归失败
- 2026-09-08：检查项 reliability 记录结论：未通过。原因：大输出后空程序被误判MLE，不能启用自测
- 2026-09-08：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-09：流程阶段 复核：ready → done。原因：VERIFY-048 已记录实现边界复核，用户自行重启服务后确认真实功能完成；不冒称独立安全代码审计
- 2026-09-09：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-09：检查项 impact-analysis 记录结论：通过。原因：技术边界复核见 VERIFY-048；配置和冷却语义由 WORK-045/046/047 承接
- 2026-09-09：检查项 independent-review 记录结论：通过。原因：用户自行重启服务后确认实际功能完成；技术复核见 VERIFY-048，不宣称独立安全审计
- 2026-09-09：检查项 automated-tests 记录结论：通过。原因：既有契约/Go/Java/Web测试与WORK-047补充回归通过；显式Linux压力失败按用户决定延期，失败事实保留
- 2026-09-09：检查项 reliability 记录结论：通过。原因：真实运行、错误输出、超时恢复及正式提交AC通过；大输出内存污染已明确延期，不宣称压力隔离通过
- 2026-09-09：检查项 performance 记录结论：通过。原因：真实运行耗时及10秒墙钟超时已记录；物理资源隔离和压力不在本轮接受范围
- 2026-09-09：检查项 privacy 记录结论：通过。原因：自测不保存输入结果，日志无程序正文和凭据；相关既有自动化与VERIFY-048复核完成
- 2026-09-09：检查项 security 记录结论：通过。原因：已有CSRF、内部凭据、输出限制和owner租约回归通过；已知sandbox限制保留
- 2026-09-09：验收闸：passed。原因：确认自定义输入运行完成，沙箱内存统计问题按既定决定延期
- 2026-09-09：根据文档、任务与验证事实刷新状态：implemented → verified。
