---
id: "TASK-126"
type: "task"
title: "S2 重切为三棵服务子树并收薄命令行入口"
status: "ready"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-125"]
related: ["CHANGE-014", "DESIGN-051", "PLAN-041"]
implements: ["CHANGE-014#REQ-001", "CHANGE-014#REQ-013", "CHANGE-014#REQ-014", "CHANGE-014#REQ-016"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", ".github", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/tests", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh"]
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# TASK-126：S2 重切为三棵服务子树并收薄命令行入口

## 任务目标

把 `apps/judge-engine` 重排为 `judge/`、`sandbox/`、`helperd/` 三棵服务子树，实现包下沉到各自的
`internal/`，使跨信任边界的引用由 Go 的可见性规则在编译期拒绝；`cmd/*` 退化为参数解析并调用服务
入口。本阶段只做移动、重命名与 import 路径修改，不改变任何逻辑。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-001、REQ-013、REQ-014；
[DESIGN-051](30-design-DESIGN-051.md) 「整体方案」的目标结构；[PLAN-041](50-plan-PLAN-041.md) 阶段 S2。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`deploy/sandbox-linux/ci/` 的必跑用例清单在
可修改范围内，但**只允许更新 judge-engine 用例的包路径**：不得增删用例、改断言或放宽必需数量
（Go 必跑固定 52 项）。报告 schema 与 `deploy/` 下其余内容仍然禁止修改。理由见
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。本阶段禁止修改任何函数体逻辑、常量取值与 HTTP 路由。

## 依赖

以 front matter 的 `depends_on` 为准。TASK-125 必须先完成，否则 `container` 仍引用 helper 包，
子树划分不成立。

## 产出

- `judge/`：`judge.go`（`Run(ctx, Config) error`）、`doc.go`，实现包迁入 `judge/internal/`。
- `sandbox/`：`sandbox.go`、`doc.go`，实现包迁入 `sandbox/internal/`；`cmd/sandbox/main.go` 中的
  生命周期编排迁入 `sandbox.Run`。
- `helperd/`：`helperd.go`、`doc.go`，helper 服务端、执行、cgroup、launcher 角色实现、policy、
  trust、recovery 迁入 `helperd/internal/`；`Dispatch` 由 `helperd` 顶层再导出。
- `internal/` 顶层只保留 `contract`、`hostexec`、`platform` 三者。
- 三个 `cmd/*/main.go` 只保留参数解析与入口调用。

## 完成标准

- [ ] `go list` 显示 `sandbox/...` 不引用 `helperd/...`，`judge/...` 不引用 `sandbox/...` 与
      `helperd/...`，`helperd/...` 不引用 `judge/...` 与 `sandbox/...`。
- [ ] 人为构造一处从 `sandbox/internal` 引用 `helperd/internal` 的改动，`go build ./...` 失败；
      在执行记录中保留报错原文，随后撤销该改动。
- [ ] 顶层 `internal/` 下只有 `contract`、`hostexec`、`platform`。
- [ ] 三个 `cmd/*/main.go` 各自不超过 40 行。
- [ ] `git log --follow` 可追溯每个被移动文件的历史；本提交的 diff 中不含函数体逻辑变化。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
go list -deps ./... | grep cherry-oj    # 人工核对依赖方向
```

外加 WORK-050 固化的 CI 全部 job 通过。

## 风险

一次性大规模移动会产生难以逐行复核的 diff。处置：逐包搬运并分多次本地提交，最终压成一个阶段提交；
搬运过程中保持文件内容逐字节不变（仅改 package 行与 import 路径），使重命名检测生效。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-14：状态变更：todo → ready。原因：意图闸已签署
