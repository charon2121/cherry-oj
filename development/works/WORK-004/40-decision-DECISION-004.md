---
id: "DECISION-004"
type: "decision"
title: "按类型与风险编排开发流程"
status: "approved"
work: "WORK-004"
owners: ["codex/root"]
depends_on: ["DESIGN-004"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# DECISION-004：按类型与风险编排开发流程

## 要决定什么

流程是否与文档一一对应，以及不同 WORK Type 和风险因素应该如何共同决定实际开发流程。

## 背景

产品、基建和重构拥有不同语义顺序；风险和影响面只应增加控制，不能抹平类型差异。开发、复核、上线
等阶段未必产生独立 Markdown，而一个 TASK 又可能同时支撑任务拆分和开发。

## 候选方案

1. 通用流程列表加适用性开关。
2. 每个阶段强制生成一份文档。
3. WORK Type 声明式模板 + 风险增量 overlays + 多对多 artifacts。
4. 每个 WORK 自由手写流程。

## 决定

采用方案 3。流程是控制面，文档是产物面。主流程只能由 WORK Type 决定；风险、影响面和 concern
增量升级或插入阶段、检查、文档与确认。阶段通过 artifacts 关联零到多份本 WORK 文档。

## 理由

它直接保留产品、基建、修复、重构和改进的语义差异，也不会为了控制节点制造空 Markdown。来源与
理由使每个新增门禁可解释，模板重算使同类工作可校验；多对多 artifacts 能表达 TASK 同时支撑任务与
开发。通用列表无法准确表达类型差异，自由流程又无法稳定执行风险规则。

## 影响与风险

workflow front matter 结构发生不兼容变更，所有 WORK 必须重建。工具复杂度增加，需要维护模板、增量
顺序和阶段同步逻辑；收益是流程、文档生成、状态边界和风险门禁统一为一套可计算事实。

## 重新考虑条件

需要合法的 WORK 级任意流程定制、阶段审批审计、多人并发写入，或声明式 Python 常量难以表达条件时，
评估 workflow overrides、事件溯源或专用引擎；“WORK Type 决定主流程，风险只做增量”仍保留。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：确认流程为控制面、文档为产物面并采用多对多 artifacts
