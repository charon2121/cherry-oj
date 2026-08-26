---
id: "DESIGN-009"
type: "design"
title: "撤回可观测性实现并保留追溯契约"
status: "approved"
work: "WORK-012"
owners: ["codex/root"]
depends_on: ["CHANGE-007"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---


# DESIGN-009：撤回可观测性实现并保留追溯契约

## 背景

依据 CHANGE-007。负责人明确否决当前观测实现的代码侵入性，同时要求保留 traceId/requestId 追溯设计。

## 目标与限制

目标是撤回尚未提交/发布的运行时观测能力，不用另一套 SDK 替换它。本次只保留传输与标识职责契约；
不实现日志平台、Metrics、Trace exporter、dashboard、告警或生产后端。

## 整体方案

tracked Java/Go/Compose/说明文件恢复到观测接入前 HEAD；删除新增的 application observability 模块、Go
observability 包、根 observability 目录和 smoke。contracts 中 `x-transport-context`、RunSpec body 清理
和 event traceId 约束保留，docs 只描述这些追溯职责并明确运行时实现待后续工作。

## 模块与数据

Gateway 保留 WORK-009 原有 RequestIdWebFilter。Java 其余服务回到独立骨架；Go 恢复标准库日志和原
API/flow；judge/sandbox 之间不新增观测 Options。无数据迁移。

## 接口与状态

`X-Request-Id` 只关联同步请求；`traceparent`/`tracestate` 是未来 Trace 父子传播真源；baggage 禁用；
request/trace 字段不进入 JudgeRequest/RunSpec。event traceId 只是查询副本，不能合成 parent。

## 安全与失败

撤掉 Docker socket、观测管理端口和 exporter，减少权限与失败面。追溯 header 仍不承担身份、授权、幂等
或业务主键职责；Gateway 既有外部 Request ID 覆盖行为不变。

## 监控与部署

本次之后仓库不提供观测栈、dashboard 或 Metrics/Trace 导出。当前只保留已有 health 与普通进程日志。

## 迁移与兼容

实现未提交/发布，无生产消费者迁移。停止并删除本地 Alloy/otel-lgtm 容器，保留 Docker volume 以避免
不可恢复删除；业务 judge/sandbox 容器不因清理观测 profile 被删除。

## 备选方案

备选是继续保留 OTel HTTP 自动埋点、只删领域日志/Metrics；仍保留 SDK 生命周期、依赖树和未获接受的
运行时观测基线，不能满足“回退本次所有观测代码”。另一备选是只重构 facade，WORK-011 已证明无法
消除整体侵入。

## 风险与重审条件

短期失去集中日志、Metrics 和 Trace 查询。只有出现明确 SLO/故障排查需求，并能提出业务代码零或极低
侵入、资源预算和可独立回退的方案时，才重新开启可观测性工作。

## 变更记录

- 2026-08-26：状态变更：draft → approved。原因：回退方案采用 HEAD 精确恢复和 contracts 保留清单，模块、清理与验证边界完整
