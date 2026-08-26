---
id: "TASK-010"
type: "task"
title: "固化跨语言遥测语义与配置契约"
status: "done"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003", "DESIGN-008", "DECISION-007", "PLAN-008"]
related: []
implements: ["CAPABILITY-003#REQ-001", "CAPABILITY-003#REQ-002", "CAPABILITY-003#REQ-003", "CAPABILITY-003#REQ-004", "CAPABILITY-003#REQ-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", ".claude/rules/go.md", "contracts", "docs/architecture.md", "docs/backend.md", "apps/server/TOOLCHAIN.md", "apps/judge-engine/config.example.yaml", "development/works/WORK-010-cross-language-observability-foundation"]
write_paths: ["contracts", "scripts/contracts_test.py", "docs/architecture.md", "docs/backend.md", "development/works/WORK-010-cross-language-observability-foundation"]
forbidden_paths: ["apps/server", "apps/judge-engine", "apps/web", "docs/product.md", "development/works/WORK-002-cpp-acm-loop"]
created_at: "2026-08-25"
updated_at: "2026-08-26"
---




# TASK-010：固化跨语言遥测语义与配置契约

## 任务目标

在不修改应用实现的前提下，契约先行地冻结 request/trace/business ID、W3C HTTP/Kafka headers、日志
字段、metric label allowlist 与标准环境配置，解决 `judge-events.traceId` 和 RunSpec body requestId 的
歧义，为 Java/Go 两个实现任务提供可测试的唯一依据。

## 依据

只依据 approved 的 CAPABILITY-003、DESIGN-008、DECISION-007 和 PLAN-008，落实 REQ-001～REQ-004、
REQ-006 的跨语言部分；DECISION-007 未确认时不得开始。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

更新内部事件/运行契约与 examples、轻量契约测试、长期架构/后端文档和本 WORK 执行记录。输出必须
明确 transport header 与 JSON field 的真源关系，不引入应用 SDK 或修改任何 Java/Go 源码。

## 完成标准

- [x] `judge-events.traceId` 约束和 example 与 32-hex OTel Trace ID 一致，header 传播规则有契约测试。
- [x] RunSpec requestId 不再被误认为 Trace/HTTP request ID，兼容策略和对齐测试明确。
- [x] 日志字段、敏感字段禁令、metric label allowlist、单位与标准 `OTEL_*` 配置成为实现依据。
- [x] 不改变 public OpenAPI、JudgeRequest/JudgeResult 业务字段、verdict 或数据库。
- [x] `scripts/contracts_test.py` 与 `scripts/work check` 通过。

## 验证

运行 contracts JSON/schema/example 测试和 `scripts/work check`；构造非法 traceId、把业务 ID 放进 label
定义、试图用 event traceId 重建 parent 等反例，预期在文档/测试边界被拒绝。

实际执行 `python3 scripts/contracts_test.py`（8 tests, OK）、`scripts/work check`（78 份文档，0 提示）
和 `git diff --check`，全部通过。

## 风险

契约可能已有未发现消费者。发现部署或外部依赖时停止收紧，回到 DESIGN-008 设计兼容迁移。任何需要
修改应用源码、公开 API、数据库或判题业务字段的发现都不得在本任务扩范围。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-26：状态变更：todo → ready。原因：上游能力、设计、决策与计划均已批准，契约任务边界完整
- 2026-08-26：状态变更：ready → doing。原因：开始按契约先行原则固化跨语言遥测语义
- 2026-08-26：完成三份 transport context 契约、event traceId 收紧、RunSpec 歧义清理、契约测试与
  全局架构/后端基线同步；未修改任何应用源码、公开 API、verdict 或数据库。
- 2026-08-26：状态变更：doing → done。原因：跨语言传播契约、traceId 约束、RunSpec 歧义清理和契约验证均已完成
