---
id: "TASK-128"
type: "task"
title: "S4 执行后端改为一次性调用并回收容量池职责"
status: "todo"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-126"]
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
implements: ["CHANGE-014#REQ-005", "CHANGE-014#REQ-011", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml"]
write_paths: ["apps/judge-engine", "development/works/WORK-058"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "scripts", ".github", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050"]
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# TASK-128：S4 执行后端改为一次性调用并回收容量池职责

## 任务目标

把 `container.Container` 的四阶段时序协议（放输入 → 启动一次 → 等待 → 取产物 → 关闭）改为
`backend.Backend` 的一次性 `Execute`，使「只能执行一次」由「不存在可复用对象」保证；把暂存根管理
从执行实现中分出为 `workspace` 包；把产物回滚从容量池移回 `runner`；把零隔离后端改名 `devhost`
并在未显式允许时拒绝启动。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-005、REQ-011、REQ-013；
[DESIGN-051](30-design-DESIGN-051.md) 「执行后端：一次性调用」；[DECISION-035](40-decision-DECISION-035.md)
决定二；[PLAN-041](50-plan-PLAN-041.md) 阶段 S4。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。不改变本机执行协议的线格式、取消语义与回收顺序；
不降低隔离强度。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `sandbox/internal/backend`：`Job`、`Source`、`NamedSource`、`OutputSink`、`Facts` 与
  `Backend.Execute`；两个实现分别位于 `backend/hostexec` 与 `backend/devhost`。
- `sandbox/internal/workspace`：暂存根独占锁、启动恢复与单次执行目录分配。
- `sandbox/internal/pool`：移除产物回滚与 store 引用，只保留名额、排队与关闭。
- `sandbox/internal/runner`：承接产物回滚。
- `sandbox/config.go`：不安全后端开关，默认关闭。

## 完成标准

- [ ] `Backend` 接口只有 `Execute` 一个方法；两个实现中不再存在 `attempted`、`closed`、
      `process != nil` 之类用于守护调用顺序的状态字段。
- [ ] `pool` 包不再引用 `store`。
- [ ] `Execute` 返回 nil 时，资源回收已完成：正常完成、墙钟超时、请求取消、部分启动失败四条路径
      的 Linux 回归均无残留进程、挂载、cgroup 与临时文件。
- [ ] 配置未显式允许不安全后端时，以 `devhost` 启动 sandbox 失败并给出明确原因。
- [ ] `trusted-host` 字面量在代码中不再出现；配置迁移说明写入执行记录。
- [ ] 对外 `/run` 响应字段与状态取值与基线 `a611be3` 相同。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
```

外加 WORK-050 固化的 Linux 隔离与故障回收回归，逐条记录四条路径的清理证据。

## 风险

接口重写会改动取消与产物交付的时序，这是现有实现中最易出错的部分。处置：先在旧接口上补齐四条
路径的测试，确认通过后再重写；重写后用同一批测试对照。

把回收责任收进 `Execute` 后，若某条失败路径提前返回而未完成回收，会退化为「回收未确认却发布产物」。
处置：在 `Execute` 的每个返回点核对回收已发生，并保留「无法确认回收即停止接单」的既有行为。

## 执行记录

- 2026-09-14：创建任务。
