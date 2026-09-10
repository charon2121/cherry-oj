---
id: "TASK-094"
type: "task"
title: "澄清执行资源契约与零值语义"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-093"]
related: []
implements: ["IMPROVEMENT-004#REQ-002", "IMPROVEMENT-004#REQ-005", "IMPROVEMENT-004#AC-002"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "contracts/run.schema.json", "apps/judge-engine/internal/contract", "apps/judge-engine/internal/judge/flow"]
write_paths: ["development/works/WORK-048", "contracts/run.schema.json", "apps/judge-engine/internal/contract"]
forbidden_paths: ["apps/server", "apps/web", "apps/judge-engine/internal/sandbox", "deploy"]
created_at: "2026-09-09"
updated_at: "2026-09-09"
---

# TASK-094：澄清执行资源契约与零值语义

## 任务目标

让执行资源的公开描述、Go 类型和缺省处理与 Linux 计量一致，保持 sandbox 不理解判题。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

## 可修改范围

以 write_paths 为准。先修改 run.schema.json，再同步 Go 类型和契约测试。CPU 口径改为组内总 CPU，内存明确组峰值；核对已有 Go signal 字段在 schema 中缺失的问题。字段缺省通过显式 presence 或入口归一化表达，不能破坏 Go 调用端构造方式而让本任务无法独立编译。取消先使用已有 InternalError 加诊断，不新增状态。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

schema/类型及对齐测试；记录现有调用者的兼容方式。

## 完成标准

- [x] 覆盖 JSON limits 缺省、字段缺省、显式 0、负数、溢出；显式 0 不被当默认值。
- [x] CPU 总量、组内存峰值与信号描述一致；不新增 OJ verdict 字段。
- [x] 现有 judge 调用及全 Go 模块能独立编译并通过契约回归。

## 验证

在 apps/judge-engine 运行 go test -race ./internal/contract/...，再 go test -race ./... 与 go vet ./...；校验 schema 与实际 JSON 样例。

## 风险

若必须新增字段/状态或修改 Java 消费者，先补本 WORK 设计与精确 TASK 边界，不在当前任务直接扩写。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。
- 2026-09-09：状态变更：todo → ready。原因：用户已审核纯 Go 材料并明确授权编码，TASK-093 完成，契约任务边界明确
- 2026-09-09：状态变更：ready → doing。原因：开始对齐资源契约并实现 JSON 字段存在性与零值语义

- 2026-09-09：完成 schema 总 CPU/组峰值/signal 对齐和 Limits 字段存在性处理；契约测试、全量 go test -race ./... 及 go vet ./... 通过。执行入口消费限额留 TASK-097。
- 2026-09-09：状态变更：doing → done。原因：资源契约和零值表示已实现，契约及全模块 race 测试和 vet 通过
