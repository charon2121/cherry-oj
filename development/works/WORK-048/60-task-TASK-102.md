---
id: "TASK-102"
type: "task"
title: "区分任务自身OOM与聚合资源受害"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-097"]
related: []
implements: ["IMPROVEMENT-004#REQ-002"]
verifies: []
tags: []
read_paths: ["apps/judge-engine/internal/sandbox", "development/works/WORK-048"]
write_paths: ["apps/judge-engine/internal/sandbox/helper/execution.go", "apps/judge-engine/internal/sandbox/helper/execution_test.go", "apps/judge-engine/internal/sandbox/container/isolated.go", "apps/judge-engine/internal/sandbox/container/isolated_test.go", "development/works/WORK-048"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal/judge"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-102：区分任务自身OOM与聚合资源受害

## 任务目标

共享节点上限杀死任务时返回平台错误，不将资源受害者误判为自身内存超限。

## 依据

DESIGN-042聚合OOM修订、TASK-098的96MiB总量/两次128MiB请求实测。

## 可查看范围

以read_paths为准。

## 可修改范围

以write_paths为准。helper收尾仅在任务OOM与OOMKill证据同时存在时消除init丢失诊断；否则OOM受害者返回平台错误。Container仅把有任务OOM证据的结果标OOMKilled。

## 禁止修改

以forbidden_paths为准。不改公开状态契约、判题流程或放大资源限额。

## 依赖

TASK-097已完成。修复在用户已授权的状态映射和实机发现问题修复范围内，不新增产品行为。

## 产出

四个精确文件内的事实判定与回归测试，TASK-098驱动增加反例/正例对照。

## 完成标准

- [x] 只有OOMKill而无任务OOM时返回平台错误，不清除诊断或发布产物。
- [x] 任务自身OOM保留MLE，普通SIGKILL不被影响。
- [x] 本地race/vet、Linux纯Go构建及实机两类OOM对照通过。

## 验证

helper收尾与Container协议适配测试；全模块race/vet；公开HTTP容量驱动总量OOM与单任务OOM对照，初末资源快照。

## 风险

同时发生叶子与祖先OOM的精确因果仍需更完整节点事件追踪，本修复不扩大支持声明。

## 执行记录

2026-09-10：创建，路径与修复依据已明确；未代签人工闸。
- 2026-09-10：状态变更：todo → ready。原因：实机复现聚合上限OOM误判MLE，设计及四文件修复边界已明确，沿用现有授权
- 2026-09-10：状态变更：ready → doing。原因：补齐任务自身OOM证据和共享上限反例，保持平台错误优先

2026-09-10：全模块race/vet、纯Go Linux构建通过；实机96MiB聚合OOM两次均InternalError，恢复后单次64MiB OOM为MLE，初末FD与资源快照稳定。证据见VERIFY-049本轮记录。
- 2026-09-10：状态变更：doing → done。原因：任务OOM证据判定及回归完成；全模块race/vet和Linux聚合OOM/单任务OOM对照通过
