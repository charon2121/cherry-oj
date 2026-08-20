---
id: "TASK-0008"
title: "升级 contracts v2 并对齐 Go judge"
type: "feature"
area: "contracts"
priority: "P0"
status: "in_progress"
assignee: "codex/root"
depends_on: ["TASK-0007"]
related: []
created_at: "2026-08-20T10:33:22+08:00"
updated_at: "2026-08-20T10:33:44+08:00"
claim_branch: "codex/task-0008"
claimed_at: "2026-08-20T10:33:44+08:00"
lease_until: "2026-08-21T10:33:44+08:00"
completed_at: null
review_required: true
---

# TASK-0008：升级 contracts v2 并对齐 Go judge

## 背景

TASK-0007 已统一题目版本、提交快照、环境标定和异步判题边界，但当前 contracts 仍以旧单体模型为
真源：正式提交按 problemId 定位测试数据，语言字段仍叫 language，结果资源字段仍叫 time/memory，
Submission 也没有版本、环境和绝对限制快照。如果直接初始化 Java 服务，Java DTO、Go DTO 和数据库
会各自解释旧字段，无法保证一次提交可复现。

## 目标

把已确认的数据模型固化为可校验的 JSON Schema v2，并让 Go judge 类型、测试数据定位和响应字段与
新契约一致，为后续 Java DTO/服务初始化提供唯一真源。

## 范围

包含：

- 升级 `judge.schema.json` 与 `submission.json` 的版本、环境和资源字段。
- 新增 ProblemJudgeSnapshot、ExecutionProfile、JudgeInput 与 Kafka judge events schema。
- 明确事件 payload 禁止源码、模板、测试数据、密码和 JWT，并通过 schema 约束允许字段。
- 保持 `run.schema.json` 与 `verdict.json` 的领域边界不变。
- 对齐 Go JudgeRequest/JudgeResult/CaseResult 类型、正式测例目录键和契约测试。
- 同步受影响的 Go flow、testcase、API 和测试 fixture。
- 同步新克隆可见的 `CLAUDE.md` 契约说明与本地 data-model/architecture 状态。

不包含：

- 创建 Java/TypeScript 工程或生成 Java DTO。
- 实现 Kafka producer/consumer、Outbox/Inbox、数据库表或 Gateway API。
- 改动 sandbox 的 run 契约、运行状态集合或 verdict 集合。
- 实现多环境路由、测试数据部署和语言标定业务逻辑。

## 验收标准

- [ ] 正式 JudgeRequest 使用 testDataVersionId 定位测例，并保留 problemId/problemVersionId 对账字段。
- [ ] JudgeResult 返回 environmentFingerprint；资源字段统一为 cpuNs/memoryBytes。
- [ ] Submission 契约包含不可变版本、环境、标定、代码模式和绝对限制快照。
- [ ] 四份新增内部/事件 schema 与架构文档字段一致，且 JudgeRequested 无源码类字段。
- [ ] 所有 schema 示例能够被对应 Go 类型解码，关键字段有对齐断言。
- [ ] Go judge 不理解 CORE、Kafka、标定或 Submission 状态，只接收完整 source。
- [ ] `run.schema.json` 与 `verdict.json` 的业务定义没有漂移。
- [ ] `gofmt -l .`、`go vet ./...`、`go test -race ./...` 和 `scripts/task check` 通过。

## 执行记录

- 2026-08-20T10:33:22+08:00：创建任务。
- 2026-08-20T10:33:44+08:00：codex/root 在分支 codex/task-0008 认领任务，租约 24 小时。

## 阻塞信息

无。

## 完成结果

尚未完成。
