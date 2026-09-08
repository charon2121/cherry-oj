---
id: "WORK-038"
type: "work"
title: "兼容常见测试数据 ZIP 并返回可操作校验错误"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: ["compatibility", "reliability", "security"]
depends_on: []
related: ["ISSUE-010", "DESIGN-032", "TASK-069", "VERIFY-039"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "compatibility", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: true
security_sensitive: true
user_visible: true
created_at: "2026-09-05"
updated_at: "2026-09-09"
work_type: "fix"
---

# WORK-038：兼容常见测试数据 ZIP 并返回可操作校验错误

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-010 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-032 `checked` | 确定技术方案、边界与取舍 |
| 修复任务 | ✔ 完成 | 必需 | TASK-069 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-069 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-039 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- 是否确认采用“一个可选外层目录 + 明确忽略 macOS 元数据 + 其他目录结构继续拒绝”的兼容边界，并
  允许 Gateway 对 allowlist 业务错误保留经过约束的上游 detail。

## 变更记录

- 2026-09-05：创建工作项并生成初始流程。
- 2026-09-05：用实际 `testin.zip` 和数据库 FAILED 记录确认根因是 Finder 外层目录/macOS 元数据被
  `TEST_DATA_INVALID_ZIP_ENTRY` 拒绝，且 Gateway 丢失具体 detail；形成受限逻辑根归一化方案。
- 2026-09-05：意图闸：passed。原因：确认兼容单外层目录和 macOS 元数据，并改进 ZIP 校验错误提示
- 2026-09-05：流程阶段 复核：ready → done。原因：实现与 DESIGN-032 一致，修改未越过 TASK-069 边界，兼容范围没有扩大为任意目录解压
- 2026-09-05：检查项 impact-analysis 记录结论：通过。原因：影响仅覆盖 OpenAPI、problem 上传校验、judging 部署校验与 Gateway 错误映射；无数据库、Web、身份或 judge-engine 变更
- 2026-09-05：检查项 automated-tests 记录结论：通过。原因：三个模块定向测试通过，后端 clean verify 共 142 项测试、0 failure、0 error、1 个 Linux 专用测试按平台跳过
- 2026-09-05：检查项 compatibility 记录结论：通过。原因：既有平面 ZIP、READY 数据、manifest DTO、成功响应和原包下载语义不变，只兼容新增单外层目录形态
- 2026-09-05：检查项 reliability 记录结论：通过。原因：problem 与 judging 使用同一归一化规则和等价 Finder fixture，上传原包、逻辑 manifest、部署目录形成一致事实链
- 2026-09-05：检查项 security 记录结论：通过。原因：只忽略明确 macOS 元数据且不读取不解压；全部 entry 计数，路径、软链接、歧义结构、UTF-8 和大小限制继续验证
- 2026-09-05：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-09：验收闸：passed。原因：确认测试数据 ZIP 兼容与校验错误提示修复完成
- 2026-09-09：根据文档、任务与验证事实刷新状态：implemented → verified。
