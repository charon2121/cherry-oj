---
id: "VERIFY-012"
type: "verify"
title: "撤回可观测性实现并保留追溯契约"
status: "approved"
work: "WORK-012"
owners: ["codex/root"]
depends_on: ["TASK-015"]
related: []
implements: []
verifies: ["CHANGE-007", "TASK-015"]
tags: []
result: "pass"
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# VERIFY-012：撤回可观测性实现并保留追溯契约

## 验证对象

TASK-015 的运行时观测回退、追溯契约保留和旧设计撤回标记。

## 对应要求

CHANGE-007#REQ-001～REQ-006 与 AC-001～AC-003。

## 检查与结果

- Contracts：`python3 scripts/contracts_test.py`，8 项测试通过；验证 W3C header、禁用 baggage、
  HTTP body 无追溯字段，以及 event traceId 为 32 位小写十六进制。
- Java：在 `apps/server` 执行 `./mvnw -q clean verify`，五个服务全部通过。
- Go：在 `apps/judge-engine` 执行 `gofmt`、`go vet ./...`、`go build ./...`、`go mod tidy` 与
  `go test -race -count=1 ./...`，全部通过；tidy 前后 `go.mod`/`go.sum` 校验和一致，依赖恢复为基线。
- Compose：`docker compose config --services` 只返回 `sandbox`、`judge`；配置中无观测服务和环境变量。
- 容器：`docker compose up -d --build --wait` 使用回退代码重建 judge/sandbox，两者均为 `healthy`；
  Alloy/otel-lgtm 容器已移除且没有重建。
- 仓库：`./scripts/work check`、`git diff --check` 通过；对 Java/Go/Compose/scripts 的观测 SDK、
  collector 和 dashboard 残留扫描无产品代码命中。

## 未通过项

暂无。

## 范围检查

改动只落在 TASK-015 允许范围。Java/Go/Compose 的观测运行时差异已全部撤回，应用代码相对基线无
差异。contracts、contracts tests 和长期文档的新增差异均属于明确要求保留的追溯设计，不含运行时实现
承诺。

## 遗留问题

无阻断项。未删除两个历史观测数据卷，以避免不可恢复的数据破坏；它们不再被 Compose 引用。

## 剩余风险

- 当前没有集中日志、Metrics、Trace SDK、collector 或查询界面，这是本次回退的预期结果。
- Trace Context 目前只是契约设计，未来不得把 contract 的存在误判为运行时链路已实现。
- WORK-010/011 作为历史证据保留并加撤回提示，不能再作为当前实现基线。

## 结论

通过。REQ-001～REQ-006 与 AC-001～AC-003 均满足，可以完成 TASK-015。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：验证记录与范围检查已完成
- 2026-08-26：状态变更：review → approved。原因：contracts、Java、Go、Compose、容器与文档检查全部通过
