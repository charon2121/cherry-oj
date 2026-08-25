---
id: "MEMORY-004"
type: "memory"
title: "按类型与风险编排开发流程"
status: "approved"
work: "WORK-004"
owners: ["codex/root"]
depends_on: ["VERIFY-004"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# MEMORY-004：按类型与风险编排开发流程

## 背景

旧流程把所有类型放在一条通用阶段列表中，以 required/not-needed 表示适用性，也近似假设一个阶段
对应一种文档。这无法准确表达产品、基建、修复和重构的语义差异。

## 决定与原因

WORK Type 是主流程唯一分类依据；TASK 继承 WORK。风险、影响面和 concern 只能增量升级或插入控制。
流程是控制面，文档是产物面，阶段通过 artifacts 与本 WORK 文档建立零到多、多对多关系。00–80
前缀继续只排序文档，不排序所有操作阶段。

## 尝试与教训

- 用通用阶段加 not-needed 看似统一，实际会隐藏类型语义并让 flow 充满无关节点。
- requirement 和 progress 必须分开，否则 required 同时被误当成阶段状态。
- 只根据文档类型推断阶段不够；TASK 天然同时服务任务拆分和开发，需要显式 artifacts。
- 阶段状态不应成为第二套人工状态：有 artifacts 的阶段必须从事实同步，只有无 artifact 操作阶段可显式推进。
- `impact=system` 不等于一定有业务上线；delivery 标记和 concern 才决定内部维护是否要求上线/观察。

## 已知问题

当前不支持任意 WORK 级流程 overrides，模板和 overlays 由代码维护；这是有意限制，用来保证同类工作
和风险门禁可校验。阶段变更历史记录在 WORK 变更记录中，没有独立事件日志。

## 重新考虑条件

大量工作需要合法的自定义阶段、多人审批审计或并发推进，或者 Python 声明式模板无法清晰表达规则时，
评估 workflow overrides、独立事件历史或专用引擎；不得退回一阶段一文档或通用 not-needed 列表。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：流程分类、artifact 关系、教训和重审条件已沉淀
