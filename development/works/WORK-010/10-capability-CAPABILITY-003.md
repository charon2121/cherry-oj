---
id: "CAPABILITY-003"
type: "capability"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# CAPABILITY-003：建立跨语言可观测性基础设施

> 已由 DECISION-008 撤销运行时交付范围。当前只保留 Request ID/W3C Trace Context 契约，日志、Metrics、
> Trace SDK、collector 与 dashboard 均未实现。

## 为什么需要

当前各进程能启动和返回健康状态，但故障发生后缺少跨进程的统一证据。公开 request ID 只存在于
Gateway 局部，内部 HTTP/Kafka/Go 调用没有统一 Trace 传播；日志格式与字段不一致，Metrics 也只有
Actuator 依赖而没有导出和领域指标。需要先建立独立于业务功能和具体观测厂商的底座，后续业务任务
只补领域事件与指标，不再各自发明日志、ID 和 exporter。

## 使用者

- 开发者：在单元/集成测试和本地联调中获得稳定的 logger、meter、tracer 与上下文接入方式。
- 运维与值班人员：按服务、环境、request ID、trace ID 和有限业务关联字段定位请求，使用 RED 与资源
  指标判断影响面。
- Gateway、四个 Java 业务服务、Go judge 和 sandbox：共享传播和字段语义，同时保持构建与部署独立。

## 能力

- REQ-001（日志）：七个进程必须输出一行一个 JSON object 的结构化日志到 stdout/stderr；至少包含
  timestamp、level、message、service.name、deployment.environment，存在活动上下文时包含 traceId、
  spanId 和 requestId。业务关联字段使用稳定 key，不拼入 message。
- REQ-002（Request ID）：Gateway 继续忽略外部 `X-Request-Id` 并生成 `req_...` 公开 ID，响应 header/
  body 保持一致；仅在同一次同步调用链中向可信下游传播。没有 Gateway 上下文的内部 HTTP 请求生成
  有界 local request ID。request ID 不进入 Kafka/数据库，不承担 Trace、身份、幂等或业务主键语义。
- REQ-003（Trace）：内部 HTTP 与未来 Kafka transport 使用 W3C `traceparent`/`tracestate`；Gateway
  在公开边界新建内部 root，初期禁用 baggage。Java、judge、sandbox 的 server/client span 必须形成
  正确父子关系，错误和取消状态可查询。
- REQ-004（事件关联）：`judge-events.traceId` 是当前 16-byte Trace ID 的 32 位小写十六进制副本；
  Kafka header 才是继续父子上下文的真源。缺 header 时消费者新建 Trace，并把 envelope traceId 作为
  查询属性而不是伪造 parent。
- REQ-005（Metrics）：所有进程提供 runtime/process、HTTP server/client 的请求量、错误率和延迟；
  judge/sandbox 另提供请求、活跃数、verdict/status、阶段耗时、执行 CPU/clock/memory 与资源池信号。
- REQ-006（基数与安全）：metric attributes 只允许有界枚举和路由模板；requestId、traceId、spanId、
  submissionId、taskId、problemId、testDataVersionId、blob ref、原始 URL/path、错误文本、源码和用户
  输入不得成为 metric label。日志/Trace 不记录源码、Cookie、JWT、密码、测例正文或完整标准答案。
- REQ-007（导出与失败）：Metrics/Trace 通过可配置 OTLP 发往本地 collector；日志由运行环境采集
  stdout。导出异步有界、失败不改变 HTTP/Kafka/判题结果，应用启动不依赖 collector，关闭 flush 有
  明确超时。
- REQ-008（查询）：仓库提供可选本地采集栈和最小 dashboard/query 入口，支持 request ID → 日志 →
  trace ID → Trace，以及从服务/路由/verdict/status 指标回到 exemplars 或相邻日志；该栈明确只用于
  开发、演示和验收。
- REQ-009（验证）：提供 Java/Go 单测、传播集成测试、敏感信息与高基数负向测试、collector 缺失
  fail-open 测试和一条可重复的跨运行时 smoke；验证不得依赖人工截图代替可执行证据。

## 接入方式

应用只依赖 OpenTelemetry/Micrometer/`slog` API 与环境配置。通用配置优先采用 `OTEL_*` 标准环境变量；
业务配置继续使用现有 `CHERRY_OJ_*`。运维通过内部 OTLP endpoint 与 stdout collector 接入，产品 Web
和 browser-facing `/api` 不暴露 Metrics、Trace 或 collector 管理端口。

## 输入与输出

输入是 HTTP/Kafka transport context、应用事件、运行事实和配置；输出是结构化日志记录、OTLP Metrics/
Trace 和查询视图。public request ID 可返回给用户用于报障，internal trace/span ID 不进入公开响应。
所有时间类 metric 以 seconds、容量以 bytes 表示；业务 JSON 契约仍遵循仓库的 ns/bytes 规则，两者
不能互相替换。

## 限制与失败

collector、Loki/Tempo/Metrics backend 或 Grafana 不可用时，应用继续处理请求并在本地有界丢弃/重试
遥测；不得把观测故障映射为业务 5xx 或 SE。无活动 span 时日志可没有 traceId；采样未命中时仍必须有
request ID 与关键完成日志。无法验证或超长的传播 header 按“无上游上下文”处理，不回显解析细节。

## 质量要求

默认只记录边界完成事件和错误，逐测试点明细降到 debug，避免日志量随 testcase 数量无界放大。
metric label 集必须在测试中枚举；Trace attributes 和日志字符串有长度上限。遥测初始化与 exporter
必须可注入/可关闭，测试使用 in-memory/fake collector，不要求真后端。已有 API、verdict 与健康状态
不得因启用或禁用遥测而变化。

## 升级与迁移

先以 opt-in exporter 和可选 Compose profile 上线；确认字段、负载和基数后再提高采样率或设置留存。
OTLP 和 stdout JSON 保持 collector 可替换。本地 `grafana/otel-lgtm` 不是生产部署承诺；生产后端、
HA、容量和告警值班在有实际部署平台与 SLO 后另建工作项。

## 不做什么

不实现业务 API、Kafka producer/consumer、数据库字段、告警值班制度、生产 HA/长期留存、用户行为
埋点、profiling、前端 RUM，也不把 Actuator/collector/Grafana 暴露给浏览器。不为“便于排查”记录
请求正文、响应正文、源码、测例或凭据。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：能力目标、边界、失败语义和九项可验证要求已完整定义
- 2026-08-25：状态变更：review → approved。原因：用户已明确本项属于基建且范围覆盖日志、Request ID、Trace ID 与 Metrics；能力定义未引入额外产品行为
