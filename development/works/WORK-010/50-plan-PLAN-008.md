---
id: "PLAN-008"
type: "plan"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003", "EXPERIENCE-004", "DESIGN-008", "DECISION-007"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-26"
---



# PLAN-008：建立跨语言可观测性基础设施

> 历史计划：实现已由 PLAN-009 整体回退，不得继续据此恢复观测代码。

## 目标

在 DECISION-007 获确认后，按契约先行顺序交付 CAPABILITY-003：先冻结字段、传播与基数规则，再并行
接入 Java/Go，最后用本地 collector/reference backend 做跨运行时 smoke；本计划不执行任何编码。

## 改动区域

- `contracts/` 与契约测试：event traceId、transport context 说明、RunSpec requestId 歧义清理。
- `apps/server`：依赖、structured logging、WebFlux/MVC request context、Micrometer/OTel、测试与说明。
- `apps/judge-engine`：observability 初始化、slog、HTTP instrumentation、judge/sandbox Metrics/Trace、配置。
- `observability/`、`compose.yaml`、`scripts/`：Alloy、local reference backend、dashboard、smoke 与运维说明。
- `docs/architecture.md`、`docs/backend.md`：只在实现/验证证明结论后同步长期全局基线。

## 阶段与顺序

0. 决策门禁：负责人逐项确认 DECISION-007；未确认时不把任何 TASK 置为 ready。
1. TASK-010：固化跨语言遥测语义与配置契约，先解决 event traceId 与 RunSpec requestId 歧义。
2. TASK-011 / TASK-012：在契约冻结后并行接入 Java 和 Go；各自使用 fake/in-memory exporter 验证。
3. TASK-013：接入 Alloy + local reference backend，提供 dashboard/query 与跨运行时 smoke，并验证
   collector 缺失时 fail-open。
4. 独立复核：检查影响面、敏感信息、基数、线程/Context 传播、资源开销和任务路径是否越界。
5. VERIFY-010：执行全量 Java/Go/contracts/Compose/跨模块矩阵；实际发布、观察和项目记忆后续推进。

## 并行与依赖

TASK-010 是共同前置。其完成后 Java/Go 可并行，不能互改对方目录或通过共享构建产物耦合。TASK-013
依赖前三个任务，只负责部署/验收，不在 smoke 阶段修应用代码；发现缺陷回到所属任务。未来 Kafka
producer/consumer 不存在，传播规则在契约中先冻结，实际接线由交付 Kafka 的业务/基建 WORK 实施。

## 迁移与上线

应用能力以 opt-in OTLP exporter 和可选 Compose profile 引入。先在本地 100% 采样完成 smoke，再在
单个非关键环境按低比例启用，确认日志量、series 数、export queue/drop 和业务延迟后逐服务扩大。
上线停止条件包括错误率/延迟明显上升、series 数无界增长、遥测含敏感字段、exporter 重试耗尽资源或
collector 影响业务网络。生产 backend/留存/告警阈值没有真实平台与 SLO，不能在本工作中假定。

## 风险

最大风险是“看起来可查”掩盖传播断链、MDC/Reactor 上下文丢失、metric 高基数、日志泄密和 exporter
反压。每一项必须有自动反例；需要变更 propagation/header、记录 body、增加 public endpoint、修改
verdict/contract v2 或引入共享业务模块时升级 DESIGN/DECISION，不扩大 TASK 路径。

## 验证

- 契约：JSON/schema/examples 与 Java/Go 对齐测试；traceId 32-hex，body 不承载 Trace。
- Java：`./mvnw clean verify`；WebFlux 与 MVC context、ECS JSON、OTLP fake exporter、collector absent。
- Go：gofmt/vet/build/`go test -race ./...`；server/client parent-child、slog 字段、judge/sandbox metrics。
- 部署：`docker compose --profile observability config`、容器 health、受限端口/网络、volume/权限检查。
- E2E：发送 Gateway 与 judge 请求，机器查询 requestId 日志、trace parent-child、RED 和领域 Metrics；
  停止 collector 重发请求，确认响应/verdict 不变且资源有界。
- 全局：`scripts/work check`、敏感 token fixture 不出现在日志/Trace、metric label allowlist 和 series 预算。

## 回退

应用先关闭 OTLP exporter/采样和 structured profile，保留现有 health 与业务行为；停止 observability
profile 不停止业务容器。依赖回退按 Java/Go task 的独立 commit 完成。event traceId 若已被消费者采用，
保留兼容读取，不回滚到无法验证的任意字符串；发现敏感数据时立即停采、清理本地卷并按实际后端流程
处理删除，不能只降低日志级别。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：实施顺序、四个任务边界、验证、上线与回退计划已就绪，等待上游技术决策确认
- 2026-08-26：状态变更：review → approved。原因：负责人授权按已确认方案和四任务依赖顺序开始实施
