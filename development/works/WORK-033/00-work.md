---
id: "WORK-033"
type: "work"
title: "重设计后台题目创建与编辑体验"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["accessibility"]
depends_on: []
related: ["IMPROVEMENT-001", "EXPERIENCE-017", "DESIGN-026", "DESIGN-027", "DECISION-018", "PLAN-021", "TASK-051", "VERIFY-034", "MEMORY-026"]
implements: []
verifies: []
tags: []
required_documents: ["improvement", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "accessibility"]
gates: {"intent": "passed", "acceptance": "passed"}
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

# WORK-033：重设计后台题目创建与编辑体验

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-033 `implemented` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 改进说明与目标指标 | ✔ 完成 | 必需 | IMPROVEMENT-001 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-026 `checked`、DESIGN-027 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-018 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-021 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 实施任务 | ✔ 完成 | 必需 | TASK-051 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 实施 | ✔ 完成 | 必需 | TASK-051 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-034 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-026 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-03：创建工作项并生成初始流程。
- 2026-09-03：检查项 definition 记录结论：通过。原因：IMPROVEMENT-001 已以十二项需求和验收标准定义用户目标、可观察行为与失败边界
- 2026-09-03：检查项 scope 记录结论：通过。原因：范围限定为 Web 管理端及文档，明确禁止后端、OpenAPI、数据、主题合同和公开题库改动
- 2026-09-03：检查项 rollback 记录结论：通过。原因：PLAN-021 已定义整体验证失败回退和编辑器/步骤壳层分组回退，且无数据迁移
- 2026-09-03：意图闸：passed。原因：确认后台题目创建与六步编辑体验、CodeMirror 6 编辑器方案并允许实施
- 2026-09-03：检查项 impact-analysis 记录结论：通过。原因：影响仅限 TASK-051 允许的 Web 依赖、UI、题目组件、路由、E2E 与文档；API、后端、数据和主题合同未变
- 2026-09-03：检查项 independent-review 记录结论：通过。原因：代码冻结后独立复核关闭保存并发覆盖、dirty 误报、参考源码状态与上传取消四项风险，无剩余阻断 finding
- 2026-09-03：流程阶段 复核：ready → done。原因：独立复核确认实现符合十二项要求、批准设计和 TASK 边界，已发现问题均修复并回归
- 2026-09-03：检查项 automated-tests 记录结论：通过。原因：npm run check 通过 112 项测试，生产/Storybook 构建通过，Chromium 30 项 E2E 全绿
- 2026-09-03：检查项 accessibility 记录结论：通过。原因：业务化 aria 名称与错误关联、Tab 退出、键盘 Dialog、320px、双主题、forced-colors、reduced-motion 和 200% 等效回归通过
- 2026-09-03：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-03：验收闸：passed。原因：题目创建与编辑体验验收通过
