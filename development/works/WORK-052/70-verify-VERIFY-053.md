---
id: "VERIFY-053"
type: "verify"
title: "修复沙箱启动通信被信号中断时的处理"
status: "review"
work: "WORK-052"
owners: ["codex/root"]
depends_on: ["TASK-116"]
related: []
implements: []
verifies: ["ISSUE-016#AC-001", "ISSUE-016#AC-002", "ISSUE-016#AC-003", "ISSUE-016#AC-004"]
tags: []
result: "pending"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-053：控制消息中断处理验证

## 验证对象

ISSUE-016 AC-001～004：确定性复现、消息/FD/期限语义、完整Linux及独立复核。

## 对应要求

四项AC均未完成，仅存在真实失败及源码读取证据。

## 检查与结果

[CI34503720792](https://github.com/charon2121/cherry-oj/actions/runs/34503720792)，提交a0aea384a810c4f2aa81d4176fbc11cf62ceb7d2。kernel的TestStartupBoundaries/wrong-go在start_linux_test.go:166调用event()时报interrupted system call（该行同时覆盖内部Poll和ReceiveEvent错误，不能定位到Recvmsg）；其他同组场景通过，后续kernel批次未执行。源码channel_linux.go直接返回unix.Recvmsg错误。

下载制品位于/private/tmp/cherry-work050-sandbox-kernel-34503720792；cleanup.json confirmed=true。该历史下载本身不包含本工作新增诊断的运行证据。该证据来自WORK-050，其他原生失败另归TASK-111。

## 未通过项

新增信号诊断已完成Linux交叉构建，但真实Linux复现、生产修复、FD/期限验证、修复后完整CI和独立复核尚未完成。

## 范围检查

本轮修改仅为WORK-050/052记录与TASK-116允许的两份boundary测试：给原Poll/ReceiveEvent报错添加阶段，新增独立有界子进程的真实信号诊断。没有改变生产、部署、协议、预算或重试行为。

## 遗留问题

具体中断信号、稳定复现方法及安全恢复方案需在获准后验证；不推断所有连接失败同因。

## 剩余风险

现有内核多轮通过不能排除这个已观察失败；后续偶然全绿也不能自动关闭。

## 结论

result=pending。意图闸及实施授权已核验，TASK-116进行中；本地检查通过不构成Linux验证、修复完成或人工验收。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：仅记录现有失败，修复与验证未执行

- 2026-09-11：核验用户意图闸为passed，将TASK-116从todo经ready推进doing；没有代签。
- 2026-09-11：macOS/Go1.26.3执行`go vet ./...`及`go test -race ./...`通过（部分已有包命中缓存）；`GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go test -c -o /private/tmp/cherry-work052-boundary.test ./tests/sandbox-linux/boundary`通过。Linux限定的新测试未在macOS运行，交叉构建不算信号注入验证。
- 2026-09-11：原日志仅有start_linux_test.go:166，event()内的Poll和ReceiveEvent均经同一require/t.Helper返回。x/sys v0.46.0/unix/syscall_linux.go:161的Poll直接调用Ppoll。本轮撤回“已确认生产Recvmsg导致CI失败”，增加明确阶段报错，候选生产修复暂未实施；没有用后续绿灯覆盖历史失败。

- 2026-09-11：Linux目标的boundary `go vet`通过；基础CI五项PASS（install15、rootfs6、ci35，共56项单测；Python AST与shell语法通过），报告/private/tmp/cherry-work052-diagnostic-basic。`scripts/work check`通过446份文档，仅既有WORK-033状态提示；`python3 scripts/docs_test.py`通过515份Markdown；`git diff --check`通过。尚未commit/push，等待本诊断批次发布授权。

- 2026-09-11：用户明确确认授权本批诊断测试及WORK-050/052工作记录commit、push到origin/main，并运行一次Linux CI；该授权不构成人工验收。
