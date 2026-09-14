---
id: "TASK-129"
type: "task"
title: "S5 提取执行结论纯函数与显式状态转移"
status: "todo"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-128"]
related: ["CHANGE-014", "DESIGN-051", "PLAN-041"]
implements: ["CHANGE-014#REQ-006", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml"]
write_paths: ["apps/judge-engine", "development/works/WORK-058"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "scripts", ".github", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050"]
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# TASK-129：S5 提取执行结论纯函数与显式状态转移

## 任务目标

把单次执行的结论从 `supervise` 与 `finish` 对同一批字段的顺序敏感读写中提取为
`Conclude(Facts) (Reason, error)`——不做输入输出、不改写状态、不依赖调用顺序；把执行状态机改为
显式转移表。资源回收仍保持命令式表达与既有顺序。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-006、REQ-013；
[DESIGN-051](30-design-DESIGN-051.md) 「结论推导：事实与回收分离」；[PLAN-041](50-plan-PLAN-041.md) 阶段 S5。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。不改变停组、等待、产物打开、环境释放的先后顺序；
不改变任何结论取值。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `helperd/internal/execution`：`Facts`（含握手结果、退出事实、init 事实、最终计量、预算、墙钟、
  输出事实、取消标记、平台故障）与 `Conclude`。
- `finish` 收缩为：停组 → 等待 → 收集 `Facts` → `Conclude` → 交付。
- 执行状态机的显式转移表，非法转移返回错误。
- `Facts → Reason` 表驱动测试，覆盖正常、墙钟超时、CPU 超预算、请求取消、输出超限、本任务 OOM、
  init 失联、握手超时、平台故障各路径。

## 完成标准

- [ ] `Conclude` 不含任何输入输出调用，不接收指针接收者，不写任何包级或结构体字段。
- [ ] `supervise` 与 `finish` 中不再存在对同一结论字段的先写后撤销。
- [ ] 表驱动测试在 macOS 上通过，且每条用例的期望结论与基线 `a611be3` 的实际行为一致，对照结果
      写入执行记录。
- [ ] 状态机的每一条合法转移都有用例；至少一条非法转移用例断言返回错误。
- [ ] Linux 回归中的结论与重构前逐条一致。

## 验证

```bash
cd apps/judge-engine
go test -race ./helperd/...      # 含表驱动结论测试，macOS 可跑
gofmt -l . && go vet ./...
```

外加 WORK-050 固化的 Linux 回归，比对超时、取消、OOM 三类场景的最终结论。

## 风险

搬运过程中可能丢失某个当前生效但未被测试覆盖的分支，表现为某类失败被判成错误结论。
处置：**测试先行**——先在重构前的实现上补齐表驱动测试并单独提交，确认全绿后再搬运逻辑，使任何
行为变化都表现为测试失败。

## 执行记录

- 2026-09-14：创建任务。
