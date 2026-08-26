---
id: "VERIFY-011"
type: "verify"
title: "收敛 Go 领域日志调用"
status: "approved"
work: "WORK-011"
owners: ["codex/root"]
depends_on: ["TASK-014"]
related: []
implements: []
verifies: ["CHANGE-006", "TASK-014"]
tags: []
result: "pass"
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# VERIFY-011：收敛 Go 领域日志调用

## 验证对象

TASK-014 交付的 Go 领域日志调用收敛，以及 CHANGE-006 的日志兼容和非侵入要求。

## 对应要求

- CHANGE-006#REQ-001～REQ-004。
- CHANGE-006#AC-001～AC-003。

## 检查与结果

- 静态范围：`rg` 确认 `internal/judge/api` 与 `internal/sandbox/api` 的非测试代码不再出现
  `ContextLogger`、`event.action` 或 `.Info(...)`；两处完成事件各只有一条具名调用。
- 结构化日志：judge/sandbox API 测试把适配器注入 JSON buffer，逐字段断言 action、submission/mode/
  language/verdict、status/cpu/clock/memory、traceId/spanId/duration，并保留源码、输入、输出、答案、
  命令和环境变量 canary 不泄漏的负向断言。
- Go 1.26.3/macOS：`test -z "$(gofmt -l .)" && go vet ./... && go build ./... &&
  go test -race -count=1 ./...` 全绿；沙箱内先因 `httptest` 禁止 bind 失败，按相同命令在允许本地监听的
  执行环境复跑后全部 package 通过。
- 依赖：`go mod tidy` 前后 `go.mod`/`go.sum` 的 `cksum` 分别保持 `3384135691/2926540958`，无漂移。
- 真实采集：`scripts/observability_smoke.py` 重建并启动 judge/sandbox 产品容器后通过；Loki 中 Judge
  requestId `req_cc9dee26db2147049ae10346` 关联到 traceId `34b74ed4b3f4fa421547bba9f2f69bba`，
  dashboard、Metrics/Trace、敏感字段和 Collector fail-open/恢复断言均通过。
- 仓库：`git diff --check` 与 `scripts/work check` 通过。

## 未通过项

暂无。

## 范围检查

实现只修改 TASK-014 允许的 `apps/judge-engine` 与 WORK-011 文档。未修改 contracts、Java、Compose、
Alloy/Grafana、公开 API、verdict/status、Metrics 名称/attributes 或 Trace 拓扑。

## 遗留问题

`testcase` 中缺失 `.out` 的历史警告仍使用该模块原有的可注入 `slog.Logger`；它不是 WORK-010 新增的
judge/sandbox 完成日志，本次未扩大 flow API 去重构该独立调用。

## 剩余风险

EventLogger 方法接收领域请求/结果，但实现只显式读取 allowlist 字段且负向测试覆盖敏感正文。以后增加
新的领域日志事件时仍需沿用具名方法和 allowlist，不能在业务层重新开放任意字段列表。

## 结论

通过。业务日志调用已收敛，原有结构化日志、关联、安全和其他遥测行为保持不变。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：字段兼容、脱敏、全量 Go 与真实采集证据已记录，进入验证确认
- 2026-08-26：状态变更：review → approved。原因：所有 AC 均通过，Loki Request ID 到 Trace ID 关联及 Collector fail-open 真实回归通过
