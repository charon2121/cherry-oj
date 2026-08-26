---
id: "DECISION-007"
type: "decision"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["DESIGN-008"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-26"
---



# DECISION-007：建立跨语言可观测性基础设施

> 已撤销：DECISION-008 取代本决定。方案 A 的日志、Metrics、Trace SDK 与采集栈不再是当前基线，只保留
> traceId/requestId 追溯契约。

## 要决定什么

决定跨 Java/Go 的遥测 API、传播格式、日志运输、Metrics/Trace 导出、collector/reference backend 和
request ID/trace ID 的职责边界。这是 TASK-010～TASK-013 的编码门禁；本文提出推荐，不代替负责人
确认。

## 背景

全局架构已要求日志包含 traceId，判题链还要有 submissionId/taskId/attemptNo；Gateway public request
ID 已由 WORK-009 固化。尚未决定的是如何用同一套标准贯通 Spring Boot、Go net/http、未来 Kafka 和
查询后端，同时满足 Go 标准库优先、服务独立部署、敏感信息不出边界和后端故障 fail-open。

## 候选方案

### 方案 A：OpenTelemetry + structured stdout + Alloy（推荐）

- Java 使用 Spring Boot 原生 ECS JSON、Micrometer/OpenTelemetry starter；Go 使用 `slog` JSON、OTel
  SDK/`otelhttp`。
- Trace/Metrics 通过 OTLP 到 Alloy；日志由 stdout 经 runtime/node agent 到 Alloy。
- W3C Trace Context 负责 HTTP/Kafka 传播，request ID 只关联同步支持请求。
- 本地用 `grafana/otel-lgtm`，生产后端保持 OTLP/Loki-compatible 可替换。
- 优点：跨语言标准统一、应用与存储解耦、可测试、collector 故障可隔离。
- 代价：需要维护 SDK 生命周期、collector 配置和字段规范；本地多一个可选 profile。

### 方案 B：直接绑定 Grafana/Prometheus SDK

Java/Go 分别直接写 Loki、Prometheus scrape/remote-write 和 Tempo/Jaeger。初期样例多，但应用知道三个
后端，配置、重试、认证与迁移重复；Go/Java 指标语义更容易漂移。

### 方案 C：只用自动探针/Java Agent

用 agent/eBPF/平台自动采集通用 HTTP/runtime 信号，源码只保留普通日志。侵入少，但无法可靠表达
public request ID 信任边界、判题 phase/verdict/resource 指标和敏感字段白名单；Go 与未来 Kafka 的
覆盖还依赖部署平台。

### 方案 D：日志、Trace、Metrics 全部从应用直接 OTLP

collector 入口最统一，但 Go OTel Logs 当前仍为 Beta；直接网络日志会把 queue/exporter 生命周期放进
应用。可以在以后日志 SDK 稳定且平台无 stdout collector 时重审。

## 决定

推荐采用方案 A，并确认以下边界：

- [x] public request ID 与 internal Trace ID 分离；Gateway 丢弃外部 request/trace/baggage 上下文并
  生成自己的 public request ID 与内部 root。
- [x] HTTP/Kafka 仅以 W3C `traceparent`/`tracestate` 传播，baseline 不启用 baggage；业务 JSON 不新增
  trace 字段，event envelope traceId 只作可查询副本。
- [x] Java 用 Spring Boot ECS + Micrometer/OTel，Go 用 slog + OTel；Trace/Metrics 走 OTLP，日志走
  structured stdout，不在应用内直推日志。
- [x] Alloy 是采集层；`grafana/otel-lgtm` 只作为本地 reference backend，不承诺生产 HA/留存/SLO。
- [x] exporter/collector fail-open，遥测不参与业务 health/verdict；高基数 ID 只进日志/Trace，不进
  Metrics/Loki labels。

**状态：已于 2026-08-26 由负责人确认方案 A 与全部五项边界，可以按 PLAN-008 实施。**

## 理由

方案 A 用 W3C/OTLP/stdout 把“应用如何产生遥测”与“后端如何保存查询”分开，既能利用 Spring Boot
4.1 已有 structured logging/Micrometer 自动配置，也能遵循 Go 的 `slog` 与可注入依赖规则。日志保留
stdout 路径避免采用 Beta Go Logs SDK，Alloy 只位于部署层。B/C 无法同时满足领域语义和后端解耦，D
在当前成熟度下把不必要的风险放进应用进程。

## 影响与风险

新增依赖和遥测有 CPU、内存、网络与存储成本；错误字段和 label 选择不当可能泄密或造成基数爆炸；
公共 edge 丢弃外部 Trace 会失去第三方端到端父子关系。通过有界导出、allowlist、采样、负向测试和
可信边界控制。此决定不授权公开管理端点、不授权记录 body，也不授权顺便实现 Kafka/业务 API。

## 重新考虑条件

Go OTel Logs 达到 stable 且平台缺少可靠 stdout agent；部署迁到具有原生 collector/managed backend 的
Kubernetes/云平台；需要跨第三方保留来访 Trace；出现多租户、tail sampling、长期留存、profiling、
RUM、合规或明确 SLO/成本约束；或基准显示 SDK/collector 开销不可接受时重新考虑。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：四种候选方案、推荐方案和负责人确认清单已就绪，等待人工决策
- 2026-08-26：负责人明确确认方案 A 与全部五项边界，解除编码门禁。
- 2026-08-26：状态变更：review → approved。原因：负责人于 2026-08-26 确认方案 A 与全部五项边界
