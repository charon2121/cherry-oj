---
id: "MEMORY-008"
type: "memory"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["VERIFY-010"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-26"
---



# MEMORY-008：建立跨语言可观测性基础设施

> 历史记忆：运行时实现已由 WORK-012 撤回。当前长期结论以 MEMORY-009 为准。

## 背景

WORK-010 在五个 Java 服务与 Go judge/sandbox 只有零散日志/health 的基础上，按负责人确认的
DECISION-007 方案 A 建立了跨语言日志、Request ID、Trace 和 Metrics 基线，并用本地 reference stack
完成真实 Gateway 与 C++ 判题链验证。

## 决定与原因

长期基线为：Java 使用 Spring Boot ECS structured logging + Micrometer/OpenTelemetry，Go 使用 `slog`
JSON + OpenTelemetry SDK/`otelhttp`；Trace/Metrics 走 OTLP，日志只走 stdout/file，由 Alloy 采集。
本地验收固定 Alloy v1.19.0 与 `grafana/otel-lgtm:0.31.0`，后者仅是开发/演示/测试 reference backend，
不是生产存储承诺。

Gateway 是 public trust boundary：忽略外部 `X-Request-Id`、`traceparent`、`tracestate`、`baggage`，生成
自己的 public request ID 与内部 root；内部 HTTP 与未来 Kafka 只传播 W3C Trace Context，不启用
baggage。request ID、Trace ID、业务 ID、幂等键各自独立，`judge-events.traceId` 只是可查询副本，不能
代替 transport header 构造 parent。

高基数 ID 只允许进入日志正文/structured metadata 或受控 Trace attribute，绝不成为 Metrics 或 Loki
label。源码、输入输出、测例、标准答案、Cookie、JWT、命令和环境变量不进入遥测。exporter 默认 opt-in，
batch queue、timeout、retry、shutdown、Alloy memory/batch/queue 都有界，Collector 不参与业务 health、
HTTP status 或 verdict。

## 尝试与教训

跨栈 smoke 暴露过一个单元测试没覆盖的缺口：Gateway WebFlux 完成回调里的 MDC 已失去活动 span，日志
最初只有 requestId。正确修复不是扩大 ThreadLocal，而是从 Reactor Context 中显式读取 Micrometer
Observation 的 TraceContext，并回归断言完成日志的 requestId/traceId/spanId。采集联调必须验证真实
边界日志，不能只验证“某条手工日志在活动 scope 内有 traceId”。

Java ECS 的 service/environment 是嵌套对象，Go slog 使用带点的扁平 key；Alloy JSON stage 必须同时
兼容 `service.name` / `service.environment` 与 `"service.name"` / `"deployment.environment"`，否则
同名查询会静默漏掉一类运行时。

本地 Alloy 应以镜像 UID/GID `473:473`、`cap_drop: ALL` 和根级持久卷 `/alloy-data` 运行；Docker socket
附加组由 `DOCKER_SOCKET_GID` 对齐。socket 即使只读仍近似 daemon 权限，只能用于本地 profile，生产应
使用平台原生日志管道或受限 node agent。需要发布宿主机端口时，reference bridge 不能设 `internal`；
安全边界由 loopback port binding、独立网络与不加入产品 backend network 共同承担。

Go OTel Logs 当前不进入 baseline，避免把网络日志 exporter 的队列/生命周期放进应用；真实 JSON
stdout 已由 Docker discovery 验证。Docker Hub 不可用时 host fallback 可验证业务与三信号，并通过临时
容器重放真实 Go JSON 独立覆盖 Docker source，但不能把 fallback 伪装成产品镜像构建成功。

## 已知问题

生产 backend、HA、留存、容量、SLO、告警阈值、值班、凭据/TLS、多租户与成本尚未设计；当前 dashboard
没有生产阈值。未来 Kafka producer/consumer 尚不存在，只有 header/envelope 语义已经冻结。前端 RUM、
profiling 和 tail sampling 不在当前基线。生产发布与线上观察仍需明确平台、环境和授权。

## 重新考虑条件

部署平台、合规/多租户要求、信号量与成本、Go Logs 达到 stable 且平台不再可靠采 stdout、需要接受
外部 Trace、引入 tail sampling/RUM/profiling，或生产 SLO/留存/告警要求明确时，重审 DECISION-007 与
collector/backend 拓扑。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：已把实际 SDK/collector 边界、Reactor/Alloy 联调教训、安全约束与重审条件沉淀为长期记忆
- 2026-08-26：状态变更：review → approved。原因：长期结论均来自 VERIFY-010 通过事实，未把本地 reference stack 扩张为生产承诺
