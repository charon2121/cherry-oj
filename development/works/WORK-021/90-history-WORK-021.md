---
id: "WORK-021"
type: "work"
title: "修复 IDEA 错误按叶子工程构建 user-service"
status: "cancelled"
work: null
owners: ["codex/root"]
risk: "low"
impact: "multi-module"
concerns: ["compatibility"]
depends_on: []
related: ["ISSUE-004", "DESIGN-017", "TASK-029", "VERIFY-021"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "compatibility"]
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-28"
updated_at: "2026-09-01"
work_type: "fix"
---

# WORK-021：修复 IDEA 错误按叶子工程构建 user-service

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-004 `deprecated` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-017 `deprecated` | 确定技术方案、边界与取舍 |
| 修复任务 | · 未开始 | 必需 | TASK-029 `cancelled` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | · 未开始 | 必需 | TASK-029 `cancelled` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | · 未开始 | 必需 | VERIFY-021 `deprecated` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

请负责人确认 ISSUE-004 与 DESIGN-017 中的最小修复：保留共享组件和现有 Maven 聚合结构，新增仓库共享
的 IDEA 启动项，并修正文档；不尝试把本来属于同一后端工程的模块伪装成完全独立发布的工程。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：确认失败来自 IDEA 以叶子 POM 启动 Maven，补充问题、方案、任务和验收边界，提交审核。
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
- 2026-09-01：状态变更：todo → cancelled。原因：问题已被其它改动顺带修复，且记录的根因不成立，本工作不再需要
