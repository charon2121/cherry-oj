---
id: "DECISION-008"
type: "decision"
title: "撤回可观测性实现并保留追溯契约"
status: "approved"
work: "WORK-012"
owners: ["codex/root"]
depends_on: ["DESIGN-009"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---


# DECISION-008：撤回可观测性实现并保留追溯契约

## 要决定什么

是否撤回 DECISION-007 方案 A 的全部运行时实现与本地采集栈，以及追溯语义保留到哪一层。

## 背景

Go 侧实际实现显示日志、Metrics、领域 Span、SDK 生命周期和注入参数形成不可接受的横切侵入；日志
facade 重构不足以消除整体成本。实现尚未提交或发布，可以干净撤回。

## 候选方案

- A：只收敛 facade，保留全部信号与采集栈；侵入和依赖面仍然存在。
- B：保留 HTTP OTel 自动埋点，删除领域信号；仍保留运行时 SDK/导出生命周期，边界不够干净。
- C：撤回全部运行时观测与采集栈，只保留 Request ID/W3C Trace Context 契约设计。

## 决定

采用 C。负责人已明确要求回退本次所有观测系统代码和设计，只保留 traceId、requestId 等追溯设计。
保留是“契约与职责边界保留”，不表示当前应用继续安装 Trace SDK 或提供 Trace 存储查询。

## 理由

当前 MVP 优先保持业务代码直接、依赖可控。A/B 都无法满足负责人对侵入性的否决；C 能恢复已验证的
业务基线，同时避免未来重新讨论时丢失标识和传输职责。

## 影响与风险

撤回本地 Grafana、集中日志、Metrics 和 Trace 导出；保留 health 与普通日志。以后不得以 DECISION-007
为依据继续接入观测代码。历史文档保留但标记 superseded，防止设计结论失真。

## 重新考虑条件

有明确 SLO/线上故障样本、平台原生低侵入能力或经负责人接受的新方案时另建 WORK；不得在业务任务中
顺带恢复。

## 变更记录

- 2026-08-26：状态变更：draft → approved。原因：负责人已确认整体回退，只保留 traceId/requestId 追溯设计
