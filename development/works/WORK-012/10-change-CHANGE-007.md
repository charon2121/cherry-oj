---
id: "CHANGE-007"
type: "change"
title: "撤回可观测性实现并保留追溯契约"
status: "approved"
work: "WORK-012"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---


# CHANGE-007：撤回可观测性实现并保留追溯契约

## 为什么做

上一项工作为了让系统「可被观察」，一次性引入了日志、指标和调用链三套能力，并接进了判题程序和后端
服务。验收时发现，这些能力对业务代码的侵入超出了负责人能接受的程度——业务代码为了配合观测而变形。
这批改动还没有对外发布，现在撤回代价最小；等到上面又叠了新功能再撤就困难得多。本次撤回这批实现，
只保留已经确认有用的请求追溯能力。

## 当前状态

工作树包含尚未提交的跨 Java/Go 可观测性实现、Alloy/otel-lgtm 本地栈和配套设计；WORK-011 虽收敛了
两处日志调用，但没有消除 Go SDK 生命周期、领域 Metrics/Span 和依赖扩张。

## 当前问题

可观测性作为横切基础设施直接进入 Go Handler、Server Options、cmd 生命周期和依赖树，维护成本与代码
噪声超过当前 MVP 的收益。继续保留会让后续业务开发围绕一个未被负责人接受的基线展开。

## 目标状态

- REQ-001：删除本次引入的 Java/Go 日志、Metrics、领域 Trace、OTLP exporter 及其测试/配置，应用代码
  回到接入前基线。
- REQ-002：删除 Alloy、otel-lgtm、Grafana dashboard、Compose profile、host log 目录和跨栈 smoke。
- REQ-003：废止 DECISION-007 方案 A 作为当前技术基线，长期文档不再声称已有日志/Metrics/collector。

## 不变条件

- REQ-004：保留 WORK-009 已实现的 Gateway public `X-Request-Id` 生成、响应与错误体一致行为。
- REQ-005：保留 HTTP/Kafka 使用 W3C `traceparent`/`tracestate`、禁用 baggage、追溯字段不进入业务 body、
  event `traceId` 为 32-hex 查询副本的契约设计；本次不承诺其运行时 SDK 已实现。
- REQ-006：不得改变 contracts 业务字段、HTTP 响应、Verdict、RunStatus、数据库或部署安全边界。

## 影响范围

`apps/server`、`apps/judge-engine`、`compose.yaml`、`observability/`、相关 scripts/docs/development 工作项。
contracts 只保留追溯设计改动。

## 风险

最大风险是把 Request ID 或业务无关但有价值的契约清理一起回退。使用明确保留列表、HEAD 对照和契约
负向测试防止误删；本地观测数据卷不自动删除，必要时仍可手工恢复查看。

## 回归检查

- AC-001：`git diff` 中 Java/Go/Compose 只剩追溯契约与回退文档，不再出现 OTel/Alloy/Metrics/log schema。
- AC-002：Gateway Request ID 测试、contracts 追溯边界测试、Java Maven verify 和 Go race tests 通过。
- AC-003：默认 Compose config/build 仍只包含 judge/sandbox，且停止观测容器不删除业务容器或数据卷。

## 变更记录

- 2026-08-26：状态变更：draft → approved。原因：负责人明确要求撤回全部观测实现并保留追溯契约，范围和不变条件已冻结
