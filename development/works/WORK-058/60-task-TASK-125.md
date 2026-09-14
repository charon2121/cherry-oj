---
id: "TASK-125"
type: "task"
title: "S1 抽出本机执行协议并消除终止原因重复定义"
status: "doing"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
related: []
implements: ["CHANGE-014#REQ-004", "CHANGE-014#REQ-013", "CHANGE-014#REQ-015"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml"]
write_paths: ["apps/judge-engine", "development/works/WORK-058"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "scripts", ".github", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050"]
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# TASK-125：S1 抽出本机执行协议并消除终止原因重复定义

## 任务目标

把本机执行协议从 `internal/sandbox/helper` 与 `internal/sandbox/launcher` 中抽出为
`internal/hostexec`（协议与类型）和 `internal/hostexec/client`（非特权侧 `Call`），使
`internal/sandbox/container` 不再引用 helper 包；同时删除 `container.Reason` 这份重复枚举，
全模块统一使用 `hostexec.Reason`。

本阶段为纯移动与去重，不改变任何运行行为、线格式与字节值。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-004、REQ-013、REQ-015；
[DESIGN-051](30-design-DESIGN-051.md) 「模块与数据 / 依赖方向」；
[PLAN-041](50-plan-PLAN-041.md) 阶段 S1。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。本阶段禁止修改任何协议常量取值、帧布局、FD 编号、
握手字节与失败阶段编号。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `internal/hostexec/`：`Request`、`Input`、`Result`、`Output`、`Completion`、`Reason`、
  `ReadFrame`/`WriteFrame`、`ValidPath`、各协议上限常量、启动协议 FD 与握手常量。
- `internal/hostexec/client/`：`Call` 及其上传、接收、尾帧等待实现。
- `internal/sandbox/container`：改为引用 `hostexec` 与 `hostexec/client`；删除本地 `Reason` 定义与
  `Reason(result.Reason)` 字符串转换。
- `internal/sandbox/runner`：`classify` 改用 `hostexec.Reason`。
- 终止原因覆盖测试：断言每个 `Reason` 取值在结论映射处都有分支。
- `internal/sandbox/helper` 与 `internal/sandbox/launcher` 保留服务端与进程角色实现，改为引用
  `hostexec`；本阶段不搬运它们的位置。

## 完成标准

- [ ] `internal/sandbox/container` 与 `internal/sandbox/runner` 的 import 列表中不再出现
      `internal/sandbox/helper`。
- [ ] 全模块只有一处 `Reason` 类型定义，且不存在跨包的 `Reason` 字符串转换。
- [ ] 新增覆盖测试：删除 `classify` 中任一 `Reason` 分支会使测试失败。
- [ ] 协议常量、帧布局、FD 编号、握手字节与失败阶段编号与基线 `a611be3` 逐一相同，在执行记录中
      列出比对结果。
- [ ] helper 客户端与服务端的改动在同一提交内完成。
- [ ] `go.mod` / `go.sum` 未改动。

## 验证

```bash
cd apps/judge-engine
gofmt -l .            # 无输出
go vet ./...          # 无输出
go test -race ./...   # 全绿
```

外加 WORK-050 固化的 CI 全部 job 通过（含 Linux 隔离与故障回收回归）。

比对项：以 `git show a611be3:apps/judge-engine/internal/sandbox/launcher/startup_protocol.go` 等
为基线，逐条核对搬运后常量取值未变。

## 风险

搬运过程中可能误改 FD 顺序或常量取值，而这类错误在 macOS 上不可见，只有 Linux 回归才暴露。
处置：逐文件搬运，每次搬运后立即比对常量；不在本阶段合入任何逻辑修改，逻辑问题记录后留给后续阶段。

## 执行记录

- 2026-09-14：创建任务。
