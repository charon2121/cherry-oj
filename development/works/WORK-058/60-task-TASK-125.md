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
- 2026-09-14：**范围收窄（与本任务「产出」一节原文不同，理由如下）。** 原文把「启动协议 FD 与
  握手常量」列入 `internal/hostexec`。实际核对跨包引用后确认：`InitControlFD`/`InitInputFD`/
  `InitLivenessFD`/`ExecConfigFD`/`ExecReadyFD`/`ExecErrorFD`、`PayloadReady`/`PayloadGo`、
  `Event`、`StageSpec`、`execFailureStage` 只被 helper 与 launcher 的进程角色使用，非特权侧
  （container / runner / api / pool）一个都没有引用。把它们放进非特权侧要 import 的包，正是
  本工作要消除的那种泄漏，因此留在 `launcher`，随 S2 一并迁入 `helperd/internal/`。
  DESIGN-051 未列举该项，方案未受影响。
- 2026-09-14：完成搬运。新增 `internal/hostexec`（protocol.go / result.go）与
  `internal/hostexec/client`；删除 `launcher/protocol.go`、`helper/protocol.go`、`helper/client.go`。
  `cgroup.Snapshot` 与线格式解耦：新增 `hostexec.Usage` 作为协议类型，`helper/usage.go` 在交付
  边界做一次字段映射，使非特权侧不再依赖 cgroup 的实现类型。
- 2026-09-14：终止原因去重完成。`container.Reason` 已删除，全模块只剩
  `internal/hostexec/result.go:60` 一处 `type Reason string`；`container/isolated.go` 原有的
  `Reason(result.Reason)` 强转随之消失。
- 2026-09-14：测试拆分。`helper/client_test.go` 中 9 个 `TestClient*` 迁至
  `internal/hostexec/client/client_test.go`；`testRequest`/`fakeServer`/`writeTestCompletion`
  三个夹具被 helper 的服务端测试依赖，留在 `helper/fixtures_test.go`，两侧各持一份副本。
  `launcher/protocol_test.go` 迁至 `internal/hostexec/protocol_test.go`。
  `helper/server_linux_amd64_test.go` 改用 `client.Call` 做协议往返。
- 2026-09-14：新增 `runner/reason_test.go`（覆盖断言）与 `hostexec/wire_test.go`（线格式基准）。
  覆盖测试做过三次变异验证，全部被拦截：① 新增 `ReasonNetwork` 而不登记结论 → 报
  「终止原因 "network" 没有登记结论」；② 删除 `classify` 的 CPU/Wall 分支 → 报
  `classify("cpu") = InternalError，期望 TimeLimitExceeded`；③ 把 `ReasonPlatform` 从首个分支
  移除 → 报 `classify("platform") = MemoryLimitExceeded，期望 InternalError`。
  第三项说明表里必须带「让结论可区分的执行状态」：只断言「原因 → 状态」时该变异不会被发现，
  而它的真实后果是把平台故障报成 MLE。
- 2026-09-14：完成标准逐条核对。
  ① `go list` 确认 container / runner / api / pool / store 均不再引用 `sandbox/helper` 与
  `sandbox/launcher`。
  ② 全模块单一 `Reason` 定义，无跨包强转。
  ③ 与基线 `a611be3` 比对 47 个协议常量，取值全部一致；仅 `maxResultFrameBytes`、
  `maxCompletionFrameBytes`、`sessionTimeout` 三项因两端共用改为导出（`MaxResultFrameBytes`、
  `MaxCompletionFrameBytes`、`SessionTimeout`），语义与取值未变。
  ④ 客户端与服务端改动在同一工作区变更内，未分批。
  ⑤ `go.mod` / `go.sum` 未改动。
- 2026-09-14：本机验证通过：`gofmt -l .` 无输出；`go vet ./...` 与
  `GOOS=linux GOARCH=amd64 go vet ./...` 均无输出；`go test -race ./...` 24 个包全绿。
  附带确认 `internal` 可见性规则已生效：模块外的临时程序 import `internal/hostexec` 被编译器
  拒绝（`use of internal package ... not allowed`）。
- 2026-09-14：**尚未执行**：WORK-050 固化的 Linux 隔离与故障回收回归。该回归依赖 CI 的内核
  虚拟机与软件包准备（`deploy/sandbox-linux/ci/kernel.py` 等），本机 macOS 无法运行，需推送后
  由 CI 执行。在它通过之前，本任务不计完成。
- 2026-09-14：状态变更：todo → ready。原因：意图闸已签署，S1 可执行
- 2026-09-14：状态变更：ready → doing。原因：开始抽出 internal/hostexec 并消除 Reason 重复定义
