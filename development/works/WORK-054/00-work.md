---
id: "WORK-054"
type: "work"
title: "为CI软件包下载增加有界同源地址回退"
status: "doing"
work: null
owners: ["codex/root"]
risk: "high"
impact: "local"
concerns: ["reliability", "security"]
depends_on: []
related: ["ISSUE-018", "DESIGN-048", "DECISION-032", "PLAN-038", "TASK-118", "VERIFY-055", "MEMORY-041", "WORK-050"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-11"
updated_at: "2026-09-11"
work_type: "fix"
---

# WORK-054：为CI软件包下载增加有界同源地址回退

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-018 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-048 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-032 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-038 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-118 `doing` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ▶ 进行中 | 必需 | TASK-118 `doing` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-055 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | · 未开始 | 必需 | MEMORY-041 `draft` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-11：创建工作项并生成初始流程。
- 2026-09-11：意图闸：passed。原因：确认CI同源连接回退方案与边界，允许实施
- 2026-09-11：检查项 rollback 记录结论：通过。原因：已签PLAN-038：撤销CI显式参数即可恢复原连接路径，无包锁/数据/服务变更；新适配可独立回退
- 2026-09-11：检查项 impact-analysis 记录结论：通过。原因：仅显式下载连接策略、CI参数和测试；默认调用及包锁/来源/内容校验/240秒下载总期限不变，不改业务、安装器或用户环境
- 2026-09-11：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：用户授权work054_review只读复核；P2重定向截断后默认无界排空已修正，完整路径测试及独立10项复测通过，无剩余源码阻断；Linux证据仍待CI，见VERIFY-055
- 2026-09-11：检查项 automated-tests 记录结论：未通过。原因：97项本地及Linux基础通过，原生56包/10项通过；CI34580313653的kernel/business下载240秒终止，完整回归未通过，见VERIFY-055
- 2026-09-11：检查项 reliability 记录结论：未通过。原因：三VM均有真实回退，但两VM仍耗尽下载总期限；整体准备未稳定恢复，按PLAN重审，禁止以一次成功声称恢复
- 2026-09-11：检查项 security 记录结论：通过。原因：真实TLS拒绝、同源/重定向有界读取、socket关闭、默认行为与内容校验负例通过；独立源码复核及Linux证据复核完成，无关闭TLS或修改锁/预算，见VERIFY-055
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：用户授权work054_review复核：P2修正复测通过；Linux97项/原生56包10项及清理证据独立核验，两项240秒失败和归因限度也核验一致，无未解决源码问题，完整CI尚失败
- 2026-09-11：状态变更：doing → todo。原因：完整CI仍失败，按已签计划重审条件暂停实施；先退回待推进状态以记录TASK的实际阻断，保留所有已通过与失败证据
- 2026-09-11：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：本批逐包诊断独立复核通过；共享输出锁修正混合日志交错，复核者去锁后同一回归失败、当前通过；最终104项本地基础测试通过，Linux诊断待发布
