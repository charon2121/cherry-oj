---
id: "TASK-101"
type: "task"
title: "收敛helper输入复制的FD生命周期"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-096"]
related: []
implements: ["IMPROVEMENT-004#REQ-003"]
verifies: []
tags: []
read_paths: ["apps/judge-engine/internal/sandbox/helper", "development/works/WORK-048"]
write_paths: ["apps/judge-engine/internal/sandbox/helper/file.go", "apps/judge-engine/internal/sandbox/helper/copy_test.go", "apps/judge-engine/internal/sandbox/helper/execute_linux_amd64.go", "development/works/WORK-048"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal/judge"]
created_at: "2026-09-09"
updated_at: "2026-09-09"
---

# TASK-101：收敛helper输入复制的FD生命周期

## 任务目标

helper输入复制不再创建由标准库GC管理的缓存pipe；单次执行完成后FD回到稳定基线。

## 依据

IMPROVEMENT-004#REQ-003及DESIGN-042关于整链FD快照的修订。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

一个隐藏ReaderFrom/WriterTo快路径的有界复制函数，替换输入传输调用，并增加短读/IO故障与快路径哨兵测试。

## 完成标准

- [x] 精确复制声明字节数，短读失败，不调用缓存FD快路径。
- [x] 本地race/vet与纯Go构建通过；同一整链冒烟初末FD不增长。

## 验证

go test -race ./internal/sandbox/helper，go vet ./...；Linux纯Go构建；TASK-098驱动初末快照复验，不扩大FD容忍值。

## 风险

普通复制可能比splice慢；优先明确FD回收与有界内存。禁止更改隔离策略、预算或通过标准。

## 执行记录

- 2026-09-09：创建任务。
- 2026-09-09：状态变更：todo → ready。原因：整链实测缺陷已定位，原实施授权覆盖回收修复，精确文件范围和完成条件已补齐
- 2026-09-09：状态变更：ready → doing。原因：以普通有界复制替代helper输入路径的隐式splice缓存

本地全模块race/vet和Linux纯Go构建通过；同一HTTP冒烟及1000次连续/双并发实机均helper FD9→9、HTTP11→11，快路径哨兵与短读/写失败测试通过。详细证据见VERIFY-049本轮记录。
- 2026-09-09：状态变更：doing → done。原因：有界输入复制及错误测试完成；全模块race/vet、Linux纯Go构建和实机冒烟/1000次/双并发FD快照通过
