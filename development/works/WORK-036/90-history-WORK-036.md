---
id: "WORK-036"
type: "work"
title: "建立页面构图层并修复前景色层级"
status: "verified"
work: null
owners: ["claude/root"]
risk: "high"
impact: "system"
concerns: ["accessibility"]
depends_on: []
related: ["IMPROVEMENT-003", "DESIGN-030", "DECISION-020", "PLAN-024", "TASK-059", "VERIFY-037", "MEMORY-028", "TASK-060", "TASK-061", "TASK-062", "TASK-063", "TASK-064"]
implements: []
verifies: []
tags: []
required_documents: ["improvement", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "accessibility"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-09-03"
updated_at: "2026-09-04"
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
| 需求澄清 | ✔ 完成 | 必需 | WORK-036 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 改进说明与目标指标 | ✔ 完成 | 必需 | IMPROVEMENT-003 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-030 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-020 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-024 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 实施任务 | ✔ 完成 | 必需 | TASK-059 `done`、TASK-060 `done`、TASK-061 `done`、TASK-062 `done`、TASK-063 `done`、TASK-064 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 实施 | ✔ 完成 | 必需 | TASK-059 `done`、TASK-060 `done`、TASK-061 `done`、TASK-062 `done`、TASK-063 `done`、TASK-064 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-037 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-028 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-03：创建工作项并生成初始流程。
- 2026-09-03：意图闸：passed。原因：意图闸通过，开始工作
- 2026-09-04：检查项 rollback 记录结论：通过。原因：每阶段一个提交，均可独立编译并独立 revert；全部改动已推送 main，无数据或外部状态需回滚
- 2026-09-04：检查项 impact-analysis 记录结论：通过。原因：五个 TASK 均在各自 write_paths 内，三处边界扩大事前记录；后端、contracts、数据库未触及
- 2026-09-04：流程阶段 复核：ready → done。原因：复核确认边界未越，文档断言已逐条回核代码
- 2026-09-04：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-04：验收闸：passed。原因：通过验收
- 2026-09-04：检查项 independent-review 记录结论：通过。原因：文档断言逐条回核代码（18 条），三处边界扩大事前记录，五个 TASK 均在 write_paths 内
- 2026-09-04：检查项 automated-tests 记录结论：通过。原因：check 36 文件 136 测试、build、storybook:build、e2e 30/30、docs_test 362 文档、work check 293 文档全部通过
- 2026-09-04：检查项 cross-module-regression 记录结论：通过。原因：后端、contracts、生成 API 客户端与数据库未触及；题库 E2E 断言未改即通过，证明业务行为与 URL 参数不变
- 2026-09-04：检查项 accessibility 记录结论：通过。原因：四档前景两主题全部允许 surface ≥4.5:1；fg-disabled 按 WCAG 2.2 SC 1.4.3 豁免并加阶梯断言；焦点 1px 变色保留 forced-colors outline 回退，AA 满足、AAA 有意放弃；E2E 覆盖键盘、320px、forced-colors、reduced-motion
- 2026-09-04：根据文档、任务与验证事实刷新状态：implemented → verified。
