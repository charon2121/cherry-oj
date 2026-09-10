---
id: "TASK-095"
type: "task"
title: "实现 cgroup v2 单次资源组与计量"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-094"]
related: []
implements: ["IMPROVEMENT-004#REQ-002", "IMPROVEMENT-004#REQ-003", "IMPROVEMENT-004#AC-002", "IMPROVEMENT-004#AC-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "apps/judge-engine/internal/contract", "dev-dependency/go-sandbox/pkg/cgroup", "deploy/sandbox-linux", "apps/judge-engine/internal/sandbox/cgroup"]
write_paths: ["development/works/WORK-048", "apps/judge-engine/internal/sandbox/cgroup"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal/sandbox/runner", "apps/judge-engine/internal/sandbox/pool"]
created_at: "2026-09-09"
updated_at: "2026-09-09"
---

# TASK-095：实现 cgroup v2 单次资源组与计量

## 任务目标

实现仅操作被委派子树的新建、限额、计量、终止和删除接口。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

## 可修改范围

以 write_paths 为准。新增 internal/sandbox/cgroup；独占创建随机任务组，EEXIST 不复用；严格解析 cpu.stat/memory.events/peak、检查单位溢出，支持 KillAndWaitEmpty 与最终快照。接口返回错误与事实，不返回判题 verdict。写限额并读回，禁止主动管理祖先 slice。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

cgroup 组件、文件 I/O 故障注入单测及资源句柄所有权说明。

## 完成标准

- [x] 每次使用新组，写 memory.swap.max=0、oom.group=1、pids.max 和 cpu.max；无能力时失败。
- [x] CPU 总量与速率接口分离，内存只读 memory.peak，解析失败不回退 wait4。
- [x] 创建部分失败、kill 失败、populated 超时、删除失败与重复 Close 可验证；不能用旧任务组重新计量。

## 验证

本地 go test -race ./internal/sandbox/cgroup/...、go vet ./...；伪文件用例只证明解析与失败处理。真实组行为统一交 TASK-098，未运行前必须标注待验证。

## 风险

委派根下监督进程必须位于叶子；误写系统 cgroup 或吞错误会越过安全边界。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。
- 2026-09-09：状态变更：todo → ready。原因：TASK-094 完成，用户已明确授权编码，cgroup 写范围明确
- 2026-09-09：状态变更：ready → doing。原因：开始实现单次 cgroup 生命周期及资源事实读取

- 2026-09-09：新增仅 v2 的单次组管理，CPU 配额与累计统计分离；故障与并发 race 单测、vet、CGO_ENABLED=0 Linux amd64 包构建通过。内核行为待 TASK-098。
- 2026-09-09：状态变更：doing → done。原因：单次 cgroup 生命周期与计量已实现，故障/并发 race 测试和 Linux 纯 Go 构建通过，内核实测留 TASK-098
