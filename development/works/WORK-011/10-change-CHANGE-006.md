---
id: "CHANGE-006"
type: "change"
title: "收敛 Go 领域日志调用"
status: "approved"
work: "WORK-011"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---


# CHANGE-006：收敛 Go 领域日志调用

> 已撤销：CHANGE-007 整体移除了本变更所针对的领域日志实现。

## 当前状态

Go 侧已经使用 `slog` JSON，HTTP/process 日志也集中在可观测性基础设施中；但 judge request 与 sandbox
run 两个领域完成事件仍在业务 Handler 中手工组装结构化字段。

## 当前问题

业务代码直接依赖 `ContextLogger` 并逐项写日志字段，混合了判题流程和日志 schema。字段命名、关联
上下文、长度限制和敏感信息边界难以在一处复核，也使正常的一次领域完成日志占用多行主流程代码。

## 目标状态

- REQ-001：judge/sandbox 业务 Handler 对每个领域完成事件只调用一行具名日志方法；字段组装、关联 ID、
  长度限制和 JSON 输出由 `internal/observability` 负责。
- REQ-002：领域日志方法使用明确事件类型/参数，不向业务层暴露可变字段列表，避免重新退化为
  `Info(message, ...fields)` 的分散 schema。

## 不变条件

- REQ-003：保持 `judge.request.completed` 与 `sandbox.run.completed` 的消息、级别、字段、单位和
  requestId/traceId/spanId 关联语义；不得记录源码、stdin/stdout/stderr、测例、答案、命令或环境变量。
- REQ-004：不得改变 Metrics 名称/attributes、Trace span/attributes、HTTP 契约、判题 verdict、沙箱
  status、Request ID 传播、exporter 生命周期或 fail-open 行为。

## 影响范围

修改 `apps/judge-engine/internal/observability`、judge/sandbox API 的日志依赖和对应测试；不修改其他应用、
跨语言契约或部署配置。

## 风险

集中适配器可能出现字段回归，或者为了“一行调用”把完整业务对象反射/序列化进日志。实现必须显式
allowlist 字段，测试同时检查必需字段和敏感正文不出现。

## 回归检查

- AC-001：注入 JSON buffer 后，两个领域事件仍包含原有全部字段和活动 request/trace/span 关联字段。
- AC-002：源码、输入、输出、答案等 canary 不出现在日志中，Metrics/Trace 现有断言不变且通过。
- AC-003：`gofmt -l .`、`go vet ./...`、`go build ./...`、`go test -race -count=1 ./...` 全部通过。

## 变更记录

- 2026-08-26：状态变更：draft → approved。原因：负责人明确要求 Go 领域日志改为单行调用，允许少量领域 Metrics 埋点且不得用日志实现污染业务代码
