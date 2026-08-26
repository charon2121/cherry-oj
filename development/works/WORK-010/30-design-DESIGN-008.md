---
id: "DESIGN-008"
type: "design"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003", "EXPERIENCE-004"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-26"
---



# DESIGN-008：建立跨语言可观测性基础设施

> 历史方案：DECISION-008 已因代码侵入性不可接受撤回本设计的运行时实现；不得继续作为编码依据。

## 背景

CAPABILITY-003 要求把现有 Gateway public request ID、Java 服务骨架、Go judge/sandbox 和未来 Kafka
判题链放进同一套观测语义。现状并非完全“没有日志”：Spring Boot 有默认 Logback，Gateway 错误处理
有一条 SLF4J 日志，Go testcase 可注入 `slog`；但它们没有统一输出、上下文与采集。Actuator 也只暴露
health/info，没有 Metrics exporter。`judge-events` 已有自由格式 traceId，`run.schema.json` 还有一个
Go 类型未实现的可选 body `requestId`，两者都需要与 transport context 分清。

## 目标与限制

目标是建立最小但完整的 logs/metrics/traces 三信号闭环，并让当前 HTTP 边界可传播、可测试、可查询。
限制是：不实现缺失的业务 API/Kafka 链路，不修改 public OpenAPI/业务响应/verdict，不让观测后端成为
运行依赖，不记录敏感正文，不引入 Java/Go 跨构建产物，也不承诺本地 reference backend 可直接生产。

## 整体方案

应用侧采用厂商中立的“结构化 stdout + OpenTelemetry”边界：

```text
Browser ── public X-Request-Id ──► Gateway ── trusted X-Request-Id + traceparent ──► Java services
                                      │
                                      └─ future Kafka header traceparent + envelope.traceId
                                                                │
                                                                ▼
judging-service ── traceparent ──► Go judge ── traceparent ──► Go sandbox

Java/Go ── OTLP Metrics + Traces ──► Grafana Alloy ──► pluggable backend
Java/Go ── JSON stdout ────────────► node/runtime log source ──► Alloy ──► Loki-compatible backend
```

Java 使用 Spring Boot 4.1 原生 ECS structured logging、Micrometer Observation 与
`spring-boot-starter-opentelemetry`；Go 使用可注入 `slog` JSON logger、OpenTelemetry Go SDK 和
`otelhttp`。Trace 与 Metrics 使用 OTLP/HTTP；日志不直接走 OTLP，避免依赖仍处于 Beta 的 Go OTel Logs，
也确保日志后端故障不会进入请求线程。采集层用 Alloy 统一收 OTLP、Docker/file logs 和后端路由。
本地 reference backend 使用 `grafana/otel-lgtm`，生产可替换为任意兼容 OTLP/Loki 的服务。

## 模块与数据

- `apps/server`：根 POM 统一依赖版本，各服务显式声明运行依赖；Gateway WebFlux 与四个 MVC 服务分别
  使用适合各自线程模型的 request context，不能用 ThreadLocal/MDC 跨 Reactor 异步边界。业务代码只
  依赖 SLF4J/Micrometer API，不直接持有 exporter。
- `apps/judge-engine`：新增小型 observability 初始化包，由两个 `cmd` 拥有 SDK 生命周期并注入 logger/
  meter/tracer；库包不调用全局 `log.Printf`。HTTP server/client 由 `otelhttp` 包装，flow/runner 只在有
  领域价值的阶段建立 span/metric。
- `contracts`：不向 JudgeRequest/RunSpec 增加 Trace 字段。`judge-events.traceId` 约束为 32 位小写 hex；
  `traceparent`/`tracestate` 位于 HTTP/Kafka header。现有 RunSpec body `requestId` 不作为新基建入口，
  由契约任务先标记/清理，避免与 HTTP request ID 重名。
- `observability/` 与根 Compose：保存 Alloy、reference backend、Grafana provisioning、dashboard 和
  smoke 配置；应用构建不 import 该目录。

本工作无数据库迁移。已有数据库设计中的 trace_id 保持可容纳 32 hex；是否持久化由所属业务 WORK
决定，本工作不提前建表或写 Mapper。

## 接口与状态

三类标识严格分工：

- `requestId`：一次同步 HTTP 支持关联。Gateway 始终生成 `req_...`，忽略外部同名 header；内部服务
  只从可信网络接受受长度/字符约束的值，无值时生成 local ID。它可以写日志/response header，但不进
  metric label、Kafka 或数据库。
- `traceId/spanId`：OpenTelemetry 当前 span 的机器关联，W3C `traceparent` 是传播真源；`tracestate`
  原样按标准传播，baseline 不启用 baggage。public response 不暴露内部 Trace ID。
- `submissionId/taskId/attemptNo/problemId`：领域对账字段，只作为受控日志/Trace attribute；Kafka key、
  eventId 和幂等语义保持各自职责，绝不由 Trace/Request ID 替代。

Gateway 作为公开信任边界丢弃客户端传入的 `traceparent`、`tracestate`、`baggage` 并新建内部 root；
下游 Java 服务仅从内部网络提取，judge 仅信任 judging-service 北向网络，sandbox 只信任 engine 网络。
Kafka producer 把当前 context 注入 headers，并把当前 32-hex traceId 复制到 envelope 供持久查询；
consumer 从 header 建 child span。header 缺失时新建 root，不能只凭 envelope.traceId 合成 parent。

基础 Metrics 包括 runtime/process 与标准 HTTP server/client RED。领域 Metrics 使用逻辑名
`cherry.judge.requests`、`cherry.judge.duration`、`cherry.judge.active`、`cherry.sandbox.runs`、
`cherry.sandbox.duration`、`cherry.sandbox.cpu.time`、`cherry.sandbox.clock.time`、
`cherry.sandbox.memory.peak` 和 pool/blob 信号；单位由 OTel metadata 声明为 seconds/bytes/`1`。
只允许 service、environment、templated route、method、status code/class、mode、phase、verdict、
sandbox status 与有限 languageId 作为 attributes。

基础日志事件至少覆盖 process.started/stopped、http.server.completed、http.client.completed、
judge.request.completed、sandbox.run.completed 和 exporter 状态。info 只记每个边界一次完成事件；测试点
逐项与 blob 细节默认 debug。错误记录稳定 error.type/code 和安全摘要，不序列化任意 request/response、
exception object、source、stdin/stdout/stderr 或环境变量。

## 安全与失败

所有来自 header、路径和业务对象的 log/trace 值先做长度限制；不采集 Cookie、Authorization、JWT、
密码、源代码、测例、标准答案或用户输出正文。request/trace/business ID 不成为 Loki label 或 metric
attribute 的无界维度，只保留在日志 JSON 字段和 span attributes 中按需查询。

SDK 使用 batch processor、有界 queue、export timeout 和限频错误；无 endpoint 时只保留本地
context/log/metrics API，不尝试阻塞网络。collector 故障不影响 readiness/liveness，也不改变 HTTP
status、RunStatus 或 Verdict。关闭时 `cmd` 在独立超时 context 内 flush；超时只写警告并退出。
采样开发默认 100%，生产通过标准配置使用比例 head sampling；任何采样率下关键失败日志都保留。

## 监控与部署

本地增加独立 observability Compose profile：Alloy 对宿主机暴露 OTLP/HTTP，容器内服务用内部地址；
`grafana/otel-lgtm` 提供开发/演示用 Grafana、Metrics、Loki 和 Tempo。Alloy 通过 Docker discovery 收集
judge/sandbox stdout；从宿主机启动的 Java 服务可在本地 profile 下把同一 ECS JSON 额外写入被 Alloy
只读挂载的临时目录，默认/生产仍只写 stdout。Docker socket 挂载仅限本地 profile，并在文档中说明
权限；生产使用节点 agent 或平台原生日志管道。

dashboard 只承诺最小服务 RED、JVM/Go runtime、judge verdict/duration、sandbox status/resource 与
collector 自监控。告警阈值需要真实 SLO/流量，本工作只提供查询与示例规则，不伪造生产阈值。管理端口
只绑定 loopback/内部网络，不能经 Gateway `/api` 暴露。

## 迁移与兼容

第一阶段 exporter opt-in、日志字段 additive、public API 不变，可逐服务启用。先固化契约，再并行接入
Java/Go，最后接 collector 和跨运行时 smoke。旧文本日志消费者若存在，可通过关闭 structured logging
短期回退；一旦 dashboard/查询依赖新字段，只允许 additive 演进或在 collector 做兼容映射。

`judge-events` 目前未部署生产消费者，traceId 收紧可契约先行完成；如果实施时发现已有外部消费者，
停止任务并改为兼容读取/双格式迁移。RunSpec 的 body requestId 只做明确废弃/清理，不用它传播 Trace。

## 备选方案

- 只保留文本日志并手写 request ID：依赖少，但无法跨异步边界、无法形成 RED/Trace，也会让 Java/Go
  各自演进，不满足 CAPABILITY-003。
- Java/Go 直接使用 Loki/Prometheus/Tempo vendor SDK：短期配置少，但应用绑定后端协议，迁移和测试
  成本高，且多个 exporter 故障面进入应用。
- 全部遥测（包括日志）直接 OTLP push：信号统一，但 Go OTel Logs 仍为 Beta，且网络日志 exporter
  比 stdout + node agent 更容易在应用内形成背压；baseline 不采用。
- 仅部署 OpenTelemetry Java Agent/自动探针：能快速得到通用 HTTP/JVM 信号，但 request ID 信任边界、
  判题领域指标、slog 字段和脱敏仍需源码约束；适合作为未来补充，不作为唯一方案。

## 风险与重审条件

主要代价是跨七个进程的依赖、配置与测试增加，以及本地 reference stack 的资源占用。实现时若
Spring Boot 4.1 starter 与当前 Cloud BOM 不兼容、Go SDK 显著增加二进制/延迟、Alloy 无法在受支持平台
安全采集日志，必须回到 DECISION-007；不能偷偷换 vendor SDK。出现 Kubernetes/托管平台、明确 SLO/
留存/成本、tail sampling、RUM/profiling 或多租户要求时，另建生产观测工作重审 collector/backends。

参考依据：Spring Boot 4.1 官方 structured logging、Actuator Observability/Tracing/Metrics 文档；
OpenTelemetry Go 官方状态与 instrumentation/propagation 文档；Prometheus metric/label 命名与基数
指南；Grafana Alloy 与 docker-otel-lgtm 官方文档。具体版本在实施 TASK 中由锁文件和验证证据固定。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：跨语言信号、传播、采集、失败隔离、安全和迁移方案已完成，提交技术复核
- 2026-08-26：状态变更：review → approved。原因：负责人确认 DECISION-007 推荐方案，技术设计作为实施依据
