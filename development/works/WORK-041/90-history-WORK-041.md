---
id: "WORK-041"
type: "work"
title: "建立题目阅读与 Monaco 编码工作台"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "local"
concerns: ["accessibility", "reliability", "performance"]
depends_on: []
related: ["FEATURE-010", "EXPERIENCE-018", "DESIGN-035", "PLAN-027", "TASK-075", "VERIFY-042", "MEMORY-031"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "design", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "accessibility", "performance", "reliability"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-09-07"
updated_at: "2026-09-07"
work_type: "product"
---

# WORK-041：建立题目阅读与 Monaco 编码工作台

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-041 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | ✔ 完成 | 必需 | FEATURE-010 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | ✔ 完成 | 必需 | EXPERIENCE-018 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-035 `checked` | 确定技术方案、边界与取舍 |
| 开发计划 | ✔ 完成 | 必需 | PLAN-027 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-075 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-075 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-042 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-031 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

意图闸和验收闸均已由用户签署，WORK-041 已验证完成，详见 VERIFY-042。

## 变更记录

- 2026-09-07：创建工作项并生成初始流程。

- 2026-09-07：用户明确先交付前端页面与本机草稿，运行和提交暂未开放；本轮只准备审阅文档。
- 2026-09-07：意图闸：passed。原因：确认左右分栏、Monaco、登录状态和本机草稿方案，允许实施
- 2026-09-07：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-07：流程阶段 复核：ready → done。原因：独立复核识别并修复版本切换保护、手机IME、语言加载失败缓存；复查无新增P1/P2
- 2026-09-07：检查项 impact-analysis 记录结论：通过。原因：只涉及TASK允许的Web和事实文档，原题库与后台回归通过，未改后端与契约
- 2026-09-07：检查项 automated-tests 记录结论：通过。原因：Web check 165测试、浏览器44用例、build与storybook通过
- 2026-09-07：检查项 accessibility 记录结论：通过。原因：双主题、320px、200%等效视口、键盘、长中文、辅助显示、手机回退及IME协议验证通过
- 2026-09-07：检查项 performance 记录结论：通过。原因：记录Monaco及worker体积，C++按需本地加载，题库与后台无Monaco请求
- 2026-09-07：检查项 reliability 记录结论：通过。原因：空草稿、跨身份、版本、双页交错写、存储拒绝、IME与加载故障恢复均有证据
- 2026-09-07：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-07：流程阶段 复核：doing → done。原因：影响分析检查已记录；独立复核与修复复验完成，余下VERIFY和MEMORY等待人工验收
- 2026-09-07：验收闸：passed。原因：已人工确认题目页面符合预期，接受运行与提交暂未开放
- 2026-09-07：根据文档、任务与验证事实刷新状态：implemented → verified。
