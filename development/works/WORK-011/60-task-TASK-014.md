---
id: "TASK-014"
type: "task"
title: "收敛 Go 领域日志调用"
status: "done"
work: "WORK-011"
owners: ["codex/root"]
depends_on: ["CHANGE-006"]
related: []
implements: ["CHANGE-006#REQ-001", "CHANGE-006#REQ-002", "CHANGE-006#REQ-003", "CHANGE-006#REQ-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", ".claude/rules/go.md", "apps/judge-engine", "development/works/WORK-010", "development/works/WORK-011"]
write_paths: ["apps/judge-engine", "development/works/WORK-011"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "compose.yaml", "observability", "docs", "development/works/WORK-010"]
created_at: "2026-08-26"
updated_at: "2026-08-26"
---




# TASK-014：收敛 Go 领域日志调用

## 任务目标

把 judge/sandbox 领域完成日志收敛为业务 Handler 中的一行具名调用，集中实现结构化字段、关联上下文、
截断和安全 allowlist，同时保持现有 Metrics、Trace 与业务行为不变。

## 依据

依据 CHANGE-006 的 REQ-001～REQ-004 和已批准 DECISION-007 五项边界；本任务是 WORK-010 验收后的内部
结构维护，不重新选择 SDK、日志运输或采集后端。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

领域事件日志适配器、judge/sandbox 单行调用、相应注入调整和字段/脱敏回归测试。

## 完成标准

- [x] `handleJudge` 和 sandbox run 完成路径不再直接拼装日志字段，每个事件只有一条具名调用。
- [x] 日志适配器只按 allowlist 读取领域结果，不序列化完整请求/响应对象。
- [x] 原有事件与关联字段测试通过，Metrics/Trace 测试没有放宽或删除。
- [x] Go 全量格式、vet、build、race tests 通过且 `go mod tidy` 无漂移。

## 验证

运行 `gofmt -l .`、`go vet ./...`、`go build ./...`、`go test -race -count=1 ./...`；检查业务文件不再
出现 `ContextLogger`/日志字段键，并对 JSON buffer 断言事件字段、关联 ID 与敏感 canary。

## 风险

若实现必须改变日志 schema、Metrics/Trace、contracts、采集配置或业务结果，则停止并升级 CHANGE，
不能以重构名义扩大范围。

## 执行记录

- 2026-08-26：创建任务。
- 2026-08-26：按负责人要求冻结“领域日志单行调用、结构化字段集中管理”的实现边界。
- 2026-08-26：状态变更：todo → ready。原因：CHANGE-006 已批准，读写边界、完成标准和回归命令完整
- 2026-08-26：状态变更：ready → doing。原因：开始收敛 judge/sandbox 领域日志调用并保持现有遥测语义
- 2026-08-26：新增 `observability.EventLogger`，由 judge/sandbox 的消费方小接口注入；业务完成路径分别
  只保留 `JudgeRequestCompleted` 与 `SandboxRunCompleted` 一行调用，结构化键、关联上下文和安全字段
  allowlist 集中到适配器。
- 2026-08-26：字段/脱敏测试、Go 全量 race 回归和默认真实容器 observability smoke 通过；业务包中
  已无 `ContextLogger`、`event.action` 或 `.Info(...)` 直接调用。
- 2026-08-26：状态变更：doing → done。原因：领域日志已收敛为两处单行具名调用，字段兼容、脱敏、Go 全量 race 与真实容器 smoke 均通过
