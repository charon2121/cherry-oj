---
id: "MEMORY-009"
type: "memory"
title: "撤回可观测性实现并保留追溯契约"
status: "approved"
work: "WORK-012"
owners: ["codex/root"]
depends_on: ["VERIFY-012"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# MEMORY-009：撤回可观测性实现并保留追溯契约

## 背景

曾尝试按跨语言 OTel + structured logging + Alloy 方案一次性交付日志、Metrics 和 Trace，但 Go 侧横切
侵入经负责人复核不可接受，且实现尚未提交/发布，因此整体撤回。

## 决定与原因

当前只保留 Request ID 与 W3C Trace Context 的契约职责，不保留运行时 SDK、领域埋点、collector 或
dashboard。追溯契约保留是为避免未来混淆 ID 职责，不表示 Trace 已实现。

## 尝试与教训

为日志增加 facade 只能减少单个 Handler 的代码行，无法消除 SDK 生命周期、依赖注入、Metrics/Span
字段和采集栈的整体复杂度。横切基建必须先给出可量化侵入预算和最小切片，再进入编码。

## 已知问题

当前没有集中日志、Metrics 或分布式 Trace 查询；只有 Gateway 既有 Request ID 行为和 transport 契约。

## 重新考虑条件

出现明确线上 SLO/故障样本、平台原生零/低侵入采集能力，且新方案通过负责人对代码、依赖和运行成本
预算确认时重审。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：回退边界、教训与重新考虑条件已沉淀
- 2026-08-26：状态变更：review → approved。原因：长期记忆与 DECISION-008、VERIFY-012 一致
