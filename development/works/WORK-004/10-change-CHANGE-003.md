---
id: "CHANGE-003"
type: "change"
title: "按类型与风险编排开发流程"
status: "approved"
work: "WORK-004"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# CHANGE-003：按类型与风险编排开发流程

## 为什么做

目前所有工作都走同一条流程，再按情况把其中某些步骤标成「需要」或「不需要」。但做新功能、
打地基、修问题、做重构和做优化本来就是五种不同的推进方式；用一条流程套五种工作，既说不清每种
工作该走哪些步骤，也分不清「流程走到哪一步」和「产出了哪份文档」是两件事。本次让不同类型的工作
各自拥有合适的流程，再按风险高低增减步骤。

## 当前状态

流程选择器使用固定的 clarify、definition、experience、design 等通用阶段列表，通过 required、optional
或 not-needed 表示适用性。文档生成会考虑部分 WORK Type 与风险，但阶段本身没有真实进度和 artifacts。

## 当前问题

产品、基建和重构虽然所需文档不同，`flow` 输出仍是同一条流程；开发体验与用户体验无法从阶段名称
区分。风险规则只能硬编码追加文档，不能说明某阶段来自基础模板还是增量规则。阶段和文档被近似当成
一一对应，开发、复核、上线等无文档阶段无法独立记录进度。

## 目标状态

- REQ-001：product、infra、fix、maintenance、improvement 分别拥有独立的有序基础流程模板。
- REQ-002：主流程由 WORK Type 决定；TASK 继承所属 WORK，不重新选择工作分类。
- REQ-003：风险、影响面和 concern 以增量规则升级或插入阶段、文档、checks 和人工确认。
- REQ-004：workflow 阶段包含 label、requirement、status、status_source、artifacts、checks、source 和 reason。
- REQ-005：阶段与文档是多对多关系，允许零份、一份或多份 artifacts，同一文档可支撑多个阶段。
- REQ-006：阶段进度使用 pending、ready、doing、done、skipped、blocked，并优先由事实推导。
- REQ-007：有 artifacts 的阶段不能手工代签；无 artifact 的操作阶段可在检查前置条件后显式推进。
- REQ-008：类型模板、增量结果、artifact 归属和阶段状态必须可由脚本重新计算并校验。
- REQ-009：SPECIFICATION、README、Schema、模板和项目规则必须准确描述并约束新模型。

## 不变条件

- REQ-010：继续使用一个 WORK 一个目录及 00–80 文档层级前缀；前缀不表示流程阶段。
- REQ-011：保留所有文档永久 ID、正文、状态、依赖和编号单调性。
- REQ-012：WORK、TASK 和普通文档既有生命周期含义不变，人工确认不能被测试结果代签。
- REQ-013：不适用阶段不生成占位节点，可选阶段的 skipped 必须有规则或人工理由。

## 影响范围

工作流常量与选择器、创建和 new-doc、flow/rebuild-flow/refresh/set-status/set-stage、结构校验、Schema、
模板、所有现有 WORK workflow、开发规范入口和端到端测试。

## 风险

主要风险是模板顺序漂移、阶段进度和文档状态不一致、风险增量覆盖主流程、无 artifact 阶段被随意完成，
以及迁移后工作状态倒退。通过声明式模板、来源字段、事实同步、状态边界和全量重建校验控制。

## 回归检查

- AC-001：产品流程生成 FEATURE、用户 EXPERIENCE 及产品阶段名称。
- AC-002：基建流程生成 CAPABILITY、开发/运维 EXPERIENCE 及基建阶段名称。
- AC-003：快速重构只要求 CHANGE、TASK、VERIFY，DESIGN/PLAN 明确 skipped。
- AC-004：高风险系统级工作插入 DECISION、MEMORY、独立复核、回退和跨模块回归。
- AC-005：数据/接口/安全/用户可见/release 关注要求上线观察，可靠性等关注要求观察。
- AC-006：同一 TASK 同时绑定 tasks 与 development，无 artifact 阶段 artifacts 为空。
- AC-007：流程配置、artifact 归属和未同步状态被 check 拒绝。
- AC-008：只有无 artifact 的操作阶段可以 set-stage，required 阶段不能 skipped。
- AC-009：现有文档、链接、ID、目录排序和既有 CLI 行为继续通过回归。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：类型流程、增量规则、artifact 与状态要求已明确
