---
id: "TASK-011"
type: "task"
title: "接入 Java 服务可观测性基线"
status: "done"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003", "DESIGN-008", "DECISION-007", "PLAN-008", "TASK-010"]
related: []
implements: ["CAPABILITY-003#REQ-001", "CAPABILITY-003#REQ-002", "CAPABILITY-003#REQ-003", "CAPABILITY-003#REQ-005", "CAPABILITY-003#REQ-006", "CAPABILITY-003#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts", "docs/architecture.md", "docs/backend.md", "apps/server", "development/works/WORK-010"]
write_paths: ["apps/server", "development/works/WORK-010"]
forbidden_paths: ["apps/judge-engine", "apps/web", "contracts", "compose.yaml", "docs/data-model.md", "docs/database-design.md", "development/works/WORK-002"]
created_at: "2026-08-25"
updated_at: "2026-08-26"
---






# TASK-011：接入 Java 服务可观测性基线

## 任务目标

为 Gateway WebFlux 和四个 MVC 服务接入统一 Java 可观测性基线：ECS JSON、public/internal request
context、Micrometer/OpenTelemetry Metrics/Trace、OTLP fail-open 配置与自动化测试；不实现业务路由、
数据库、Kafka 或 Go 调用方。

## 依据

只依据 approved 的 CAPABILITY-003、DESIGN-008、DECISION-007、PLAN-008 和已完成 TASK-010，落实
REQ-001～REQ-003、REQ-005～REQ-007 的 Java 部分。现有 WORK-009 public request ID 契约不可改变。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

根/子模块 POM 依赖、五服务配置、WebFlux/MVC context 与 logging/tracing/metrics 接线、fake exporter 和
日志捕获测试、README/TOOLCHAIN 更新及任务执行证据。若共享少量 Java 基建，必须保持纯观测职责且不
共享业务 DTO/entity/Mapper。

## 完成标准

- [x] 五服务启动日志与应用日志是合法 ECS JSON，service/environment/level/message 和活动上下文字段
  可断言；Gateway Reactor context 不依赖不安全的 ThreadLocal 泄漏。
- [x] Gateway 继续替换来访 `X-Request-Id`，header/body/log 一致；可信同步下游传播受控 ID，四个 MVC
  服务缺值时有 local ID，request/trace/session/idempotency 不混用。
- [x] WebFlux/MVC inbound 与框架构建的 outbound HTTP client 生成/传播正确 W3C span；public edge 的
  外部 trace/baggage 清理规则有测试。
- [x] runtime/process、HTTP server/client Metrics 可由 in-memory/OTLP fake 读取，tag 只来自 allowlist。
- [x] endpoint 未配置、collector 拒绝/超时、采样关闭时五服务仍启动且现有 API/health 响应不变。
- [x] 错误日志与 span 不包含 body、Cookie、Authorization、JWT、源码/测例 fixture；全量 Maven verify
  通过。

## 验证

从 `apps/server` 运行 `./mvnw clean verify`。分别用 WebTestClient/MockMvc 测合法/非法 header、错误/
取消、MDC/Reactor 清理和并发不串号；用 in-memory/fake exporter 断言父子 span、metric 名/attributes；
关闭 collector 后复跑 status/health，预期 HTTP 行为不变且导出等待有界。

## 风险

Spring Boot 4.1 OTel starter/Cloud BOM 兼容、WebFlux context propagation、重复 server instrumentation 和
五模块配置漂移是主要风险。需要修改 public OpenAPI、业务服务职责、Kafka、Go、Compose 或采用 Java
Agent/vendor SDK 时停止并升级 DESIGN-008/DECISION-007。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-26：状态变更：todo → ready。原因：TASK-010 已完成，Java 任务上游与路径边界完整
- 2026-08-26：状态变更：ready → doing。原因：开始接入五个 Java 服务的结构化日志、Request ID、Trace 与 Metrics
- 2026-08-26：新增纯观测共享模块；五服务启用 ECS JSON、W3C-only、无 baggage、OTLP Logs 禁用及
  opt-in 的 Trace/Metrics exporter。Gateway 从配置层不消费公网 trace，四个 MVC 服务生成/接受受控
  internal request ID，RestClient/WebClient 自动传播 W3C，request/trace ID 不进入 Metrics tag。
- 2026-08-26：`cd apps/server && ./mvnw -q clean verify` 通过；测试断言合法 ECS JSON、活动
  traceId/spanId、public/internal request ID、runtime/process/HTTP server/client Metrics、W3C
  inbound/outbound、无 baggage、无 collector 时现有 API/health 不变。`git diff --check` 通过，敏感字段
  关键字扫描无命中。
- 2026-08-26：状态变更：doing → done。原因：五服务 Java 可观测性基线已实现，ECS/W3C/Request ID/Metrics/OTLP fail-open 测试与全量 Maven verify 通过
- 2026-08-26：状态变更：done → doing。原因：TASK-013 跨运行时烟测发现 Gateway 完成日志缺少活动 traceId/spanId，退回 Java 基线补齐并回归
- 2026-08-26：状态变更：doing → done。原因：修复 Gateway 完成日志 Reactor Context trace 关联，补充 requestId/traceId/spanId 回归断言，五服务 Maven clean verify 通过
