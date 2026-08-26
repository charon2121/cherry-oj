---
id: "TASK-012"
type: "task"
title: "接入 Go 判题引擎可观测性基线"
status: "done"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003", "DESIGN-008", "DECISION-007", "PLAN-008", "TASK-010"]
related: []
implements: ["CAPABILITY-003#REQ-001", "CAPABILITY-003#REQ-002", "CAPABILITY-003#REQ-003", "CAPABILITY-003#REQ-005", "CAPABILITY-003#REQ-006", "CAPABILITY-003#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", ".claude/rules/go.md", "contracts", "docs/architecture.md", "docs/backend.md", "apps/judge-engine", "development/works/WORK-010"]
write_paths: ["apps/judge-engine", "development/works/WORK-010"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "compose.yaml", "docs/data-model.md", "docs/database-design.md", "development/works/WORK-002"]
created_at: "2026-08-25"
updated_at: "2026-08-26"
---




# TASK-012：接入 Go 判题引擎可观测性基线

## 任务目标

把 judge/sandbox 的进程日志迁到可注入 `slog` JSON，建立由两个 `cmd` 管理生命周期的 OpenTelemetry
Trace/Metrics，并对 net/http server、judge→sandbox client 和关键判题/执行阶段接入有限基数观测；
不改 verdict、运行契约或容器部署。

## 依据

只依据 approved 的 CAPABILITY-003、DESIGN-008、DECISION-007、PLAN-008 和已完成 TASK-010，落实
REQ-001～REQ-003、REQ-005～REQ-007 的 Go 部分，并完整遵循 `.claude/rules/go.md` 的注入、生命周期、
错误与 race 测试规则。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

小型 observability 初始化包、标准 OTEL 配置、两个 `cmd` 的 SDK/logger 生命周期、HTTP middleware/
transport、judge/sandbox 领域 Metrics/Trace、测试和 config example。库包通过 Options/消费方接口获得
logger/meter/tracer，不把 vendor exporter 散进 flow/runner。

## 完成标准

- [x] 两个进程一行一个 JSON 日志，service/environment/level/message 与活动 trace/span/request 字段
  一致；原有 `log.Printf/Fatalf` 主路径清理，库 logger 可注入并可测试。
- [x] judge inbound、judge→sandbox 的 upload/run/delete 和 sandbox inbound span 父子正确，context
  cancel 继续传播；非法/missing header 新建 root，不使用 RunSpec body requestId 传播。
- [x] judge request/active/duration/verdict 与 sandbox run/status/duration/cpu/clock/memory/pool/blob Metrics
  采用有界 attributes，不记录每个业务 ID 或原始 path/error。
- [x] 逐测试点日志默认不是 info；源码、stdin/stdout/stderr、测例、标准答案、环境变量与命令参数不被
  自动写入日志/Trace。
- [x] OTLP 未配置、collector 超时/拒绝、shutdown flush 超时时 HTTP/RunStatus/Verdict 与未启用时一致，
  goroutine/queue 有界。
- [x] `gofmt -l .` 无输出、`go vet ./...` 与 `go test -race -count=1 ./...` 全绿，`go mod tidy` 无漂移。

## 验证

使用 `httptest`、in-memory exporter 与注入 buffer handler 验证字段、父子关系、Metrics 和脱敏；并发
请求断言 request/trace 不串号。运行 gofmt/vet/build/race tests 和 `go mod tidy` diff 检查；用不可达
OTLP endpoint 启动两个 cmd，预期健康和判题结果不受影响。

## 风险

新增 OTel 依赖与全局 provider、重复 otelhttp 包装、每测试点 span/log 爆量、shutdown goroutine 泄漏
和 sandbox 安全边界是主要风险。需要改 contracts、Compose、runner verdict/limit、记录输入输出或将
observability 融入 domain API 时停止并升级设计，不在本任务扩大范围。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-26：状态变更：todo → ready。原因：TASK-010 已完成，Go 任务上游与路径边界完整
- 2026-08-26：状态变更：ready → doing。原因：TASK-011 已完成并通过全量 Maven verify，开始接入 Go judge/sandbox 可观测性基线
- 2026-08-26：新增进程级 `slog` JSON logger 与显式拥有的 OTel SDK 生命周期；judge/sandbox HTTP
  server、judge→sandbox client、judge request、sandbox run/blob 接入 W3C-only Trace 和低基数 Metrics。
  未配置 OTLP 时不建立 exporter，配置失败与 3 秒导出/5 秒 shutdown 超时均 fail-open；日志继续只写
  stdout，不传播 baggage 或业务正文。
- 2026-08-26：`gofmt -l .` 无输出，`go vet ./...`、`go build ./...`、
  `go test -race -count=1 ./...` 全部通过；二次 `go mod tidy` 前后 `go.mod/go.sum` SHA-256 一致。
  httptest/in-memory exporter 覆盖跨 HTTP 父子 span、并发 request/trace 隔离、领域 Metrics、Collector
  不可达不改变 HTTP 响应，以及源码、输入输出、答案、命令和环境变量不进入遥测。
- 2026-08-26：状态变更：doing → done。原因：Go judge/sandbox 可观测性基线已实现，W3C/Request ID/JSON slog/领域 Metrics/OTLP fail-open 测试及 gofmt、vet、build、race 全部通过
