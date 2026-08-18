---
id: "TASK-0003"
title: "将资源计量职责从 container 拆到 cgroup"
type: "tech-debt"
area: "sandbox/runner"
priority: "P2"
status: "todo"
assignee: null
depends_on: []
related: ["TASK-0004"]
created_at: "2026-08-18T19:10:35+08:00"
updated_at: "2026-08-18T19:10:35+08:00"
claim_branch: null
claimed_at: null
lease_until: null
completed_at: null
review_required: true
---

# TASK-0003：将资源计量职责从 container 拆到 cgroup

## 背景

当前 `internal/sandbox/container.Usage` 同时包含进程退出事实和 CPU、内存计量，host 实现用
`rusage` 兜底。这会混淆执行职责与 Linux cgroup 的权威计量职责。

## 目标

container 只报告退出码和信号；runner 从 cgroup 的 `memory.peak`、`cpu.stat` 等来源读取
权威的 CPU、内存和进程数数据。

## 范围

包含：

- 拆分退出事实与资源计量类型。
- 明确 runner 聚合两类结果的顺序和失败语义。
- 针对计量读取失败、进程退出和超时路径补充测试。

不包含：

- 非 Linux 平台提供与 cgroup 完全等价的精确计量。
- 修改 judge verdict 业务规则。

## 验收标准

- [ ] container API 不再负责提供权威 CPU 和内存计量。
- [ ] Linux 隔离实现从 cgroup 读取 CPU、峰值内存和进程事实。
- [ ] 计量读取失败不会被伪装成合法的零值结果。
- [ ] 单元测试、Linux 集成测试和竞态测试通过。

## 执行记录

- 2026-08-18：从本地 `docs/backlog.md` 迁入任务中心；详细背景见本地 `docs/engine.md` §5.5。

## 阻塞信息

无。

## 完成结果

尚未完成。

