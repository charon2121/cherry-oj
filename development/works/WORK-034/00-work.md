---
id: "WORK-034"
type: "work"
title: "基于下载版重建 Web 设计系统并保留浅色主题"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["accessibility", "compatibility", "performance", "release"]
depends_on: ["WORK-033"]
related: ["IMPROVEMENT-002", "DESIGN-028", "PLAN-022", "TASK-052", "VERIFY-035", "MEMORY-027", "DECISION-019", "TASK-053", "TASK-054", "TASK-055"]
implements: []
verifies: []
tags: []
required_documents: ["improvement", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "accessibility", "compatibility", "performance", "release"]
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

# WORK-034：基于下载版重建 Web 设计系统并保留浅色主题

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-034 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 改进说明与目标指标 | ✔ 完成 | 必需 | IMPROVEMENT-002 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-028 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-019 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-022 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 实施任务 | ✔ 完成 | 必需 | TASK-052 `done`、TASK-053 `done`、TASK-054 `done`、TASK-055 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 实施 | ✔ 完成 | 必需 | TASK-052 `done`、TASK-053 `done`、TASK-054 `done`、TASK-055 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-035 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-027 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-03：创建工作项并生成初始流程。
- 2026-09-03：检查项 definition 记录结论：通过。原因：已把现状根因、八项需求和八项可验证验收标准写入 IMPROVEMENT-002
- 2026-09-03：检查项 scope 记录结论：通过。原因：TASK-052 已明确允许、禁止路径及 API/主题/WORK-033 不变边界
- 2026-09-03：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-03：检查项 definition 记录结论：通过。原因：下载版来源、当前差距、16 项系统级要求、风险与逐项验收标准已完整定义。
- 2026-09-03：检查项 scope 记录结论：通过。原因：TASK-052 至 TASK-055 已分阶段限定 docs、Foundation、组件、Shell 和业务页面的读写/禁止路径，并明确后端、API、数据与 WORK-033 业务合同不变。
- 2026-09-03：检查项 rollback 记录结论：通过。原因：PLAN-022 已定义 WORK-033 独立基线、分批可编译提交和不回退业务功能的 Git 恢复路径；实际回退仍需用户授权。
- 2026-09-03：检查项 release 记录结论：通过。原因：PLAN-022 已定义最终单系统原子交付、禁止长期混搭、全路由验收和无远程部署边界。
- 2026-09-03：用户明确要求保留浅色主题；重新打开定义、设计、决定与计划，改为一套新系统下的暗色/浅色双主题重建。
- 2026-09-03：检查项 definition 记录结论：通过。原因：已将目标修订为下载版主导的一套新设计系统，并明确保留暗色、浅色、主题切换、偏好及双主题验收。
- 2026-09-03：检查项 scope 记录结论：通过。原因：TASK-052 至 TASK-055 已覆盖双主题 Foundation、组件、运行时、Shell 和全路由，后端、API、数据与 WORK-033 业务合同仍不变。
- 2026-09-03：检查项 rollback 记录结论：通过。原因：PLAN-022 保留主题偏好数据，按可编译批次回退旧双主题设计系统，不回退 WORK-033 业务功能。
- 2026-09-03：检查项 release 记录结论：通过。原因：PLAN-022 定义原子交付一套新设计系统，其中暗色与浅色是正式主题，不提供旧新系统混搭。
- 2026-09-03：意图闸：passed。原因：确认保留浅色主题的双主题设计系统重建方案，并允许实施
- 2026-09-03：检查项 impact-analysis 记录结论：通过。原因：全站消费者、主题运行时、页面与业务不变边界逐项核对；未修改后端、contracts、数据库或业务 API
- 2026-09-03：检查项 automated-tests 记录结论：通过。原因：Web 32 文件/116 项、Chromium 30 项、Server Maven 133 项及 Judge Engine 全包测试通过
- 2026-09-03：检查项 cross-module-regression 记录结论：通过。原因：Web、Gateway/User/Problem/Submission/Judging 与 Judge Engine 在本地完整回归通过
- 2026-09-03：检查项 accessibility 记录结论：通过。原因：键盘焦点、语义名称、320px、forced-colors、reduced-motion 与对比度门禁通过；真实读屏器保留人工验收
- 2026-09-03：检查项 compatibility 记录结论：通过。原因：双主题首帧/持久化/刷新、桌面与 320px、生产 build/Storybook/Chromium 均通过
- 2026-09-03：检查项 performance 记录结论：通过。原因：CSS gzip 16.03 kB；工作台 757.24/256.14 kB，较 WORK-033 基线约增加 0.4%，未新增动画库
- 2026-09-03：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-03：检查项 independent-review 记录结论：通过。原因：用户在本地验收页面完成独立视觉与功能复核，明确确认验收通过并授权签署 WORK-034
- 2026-09-03：验收闸：passed。原因：用户明确确认 WORK-034 验收通过并授权签署
- 2026-09-03：根据文档、任务与验证事实刷新状态：implemented → verified。
