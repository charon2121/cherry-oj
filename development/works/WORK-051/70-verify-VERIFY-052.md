---
id: "VERIFY-052"
type: "verify"
title: "修复沙箱连续请求完成与容量归还的竞态"
status: "review"
work: "WORK-051"
owners: ["codex/root"]
depends_on: ["TASK-114"]
related: []
implements: []
verifies: ["ISSUE-015#AC-001", "ISSUE-015#AC-002", "ISSUE-015#AC-003", "ISSUE-015#AC-004"]
tags: []
result: "pending"
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# VERIFY-052：连续请求异常修复验证

## 验证对象

ISSUE-015 AC-001～004：时序回归、收尾与容量安全、完整CI和边界复核。

## 对应要求

| 要求 | 本轮证据 | 状态 |
|---|---|---|
| AC-001 时序回归 | 旧客户端在完成后连接仍开启、尾部有垃圾两个用例失败；修复后本地通过。服务端 EOF/槽位测试已编译，须在 Linux 实际运行 | 部分完成 |
| AC-002 收尾及容量 | 本地协议/取消/早期失败阻塞输入回归通过；新增 Linux reset、慢交付及清理后复用测试等待实跑，真实满槽及故障沿用内核套件 | 部分完成 |
| AC-003 完整回归 | 本地完整 Go race、vet、基础 39 项通过；修复后的 Linux 63 项未运行 | 部分完成 |
| AC-004 边界及复核 | 生产仅两个 helper 文件；测试消费者边界先更新再修改。未部署或更改原节点；尚无本次独立复核 | 部分完成 |

## 检查与结果

环境：本地 Darwin 24.3.0 / arm64，Go 1.26.3。本轮均为基于 `7a66b35fd157272e6dcc0a3a80a7bca0ad8cd690` 的未提交修改，不是该 SHA 的干净运行，也不代表 Linux 支持验证。

1. 先仅新增客户端回归、保留旧 client.go，执行 `go test -race -count=1 -run 'TestClient(WaitsForConnectionRelease|RejectsTrailingData)' ./internal/sandbox/helper`：两个用例均 FAIL，分别报告“完成帧之后没有 EOF，客户端仍返回成功”和“完成帧之后存在多余字节，客户端仍返回成功”。首次受本地工具沙箱限制 bind 被拒，取得本地测试执行权限后才得到上述行为失败；未把权限失败算作缺陷复现。
2. 修改客户端与服务端后执行 `go test -race -count=1 -p=1 ./...`：全部可在本地运行的包 PASS；`go vet ./...` PASS。客户端正常流式交付、早期平台失败关闭阻塞输入、缺尾帧、非法尾帧、额外字节、期限内无 EOF、完成后取消和原有生命周期失败回归均通过。
3. `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go test -c -o /private/tmp/cherry-work051-helper.test ./internal/sandbox/helper` PASS，仅编译证据。新增服务端测试通过真实 Unix socket 与关闭前的同步观察确认槽位在 EOF 前可用；慢交付用信号固定占槽阶段；reset 用 Linux MSG_PEEK 保留未消费输入，关闭时要求实际 ECONNRESET。这三个用例尚未实跑。
4. `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work051-basic` PASS：15 安装、6 rootfs、18 CI 单测；43 Python AST、4 shell 语法通过。该目录是本地临时证据，不提交为固定基线。
5. `scripts/work check` 437 份文档通过，仅原有 WORK-033 推导提示；`git diff --check` PASS。CI 必需 Go 测试清单由 38 项增为 45 项，原 63 个 kernel 场景不减少。

源码收尾顺序：serveConn 完成执行清理、产物交付、FD Close 及取消 defer；fatal 先通知停服；serveInSlot 归还槽位后关闭 Unix socket；客户端验证正常 EOF 后返回。槽位归还之前包括全部可阻塞交付，之后没有协议写入或后台任务，仅本地 socket 关闭。保持 accept 处无槽即拒绝及原连接/写期限。

## 未通过项

修复后 Linux 测试和完整 CI、1000 次、并发、故障及容量结果均未形成；也未完成本次独立复核。不能仅凭客户端旧红新绿声称已经解释全部真实 reset。

## 范围检查

实施限定在 TASK-114 的两个 helper 生产文件、对应测试及 README、cases.json、两个测试私有客户端及本工作文档。读取消费者前新增只读路径；根据已确认的同构建协议同步目标，在 DESIGN/PLAN/TASK 中先补入 tests/client.py 与 tests/smoke.py 的精确写边界，再增加 EOF 断言。未修改执行器、权限、cgroup、公开业务契约、服务配置或测试预算。

## 遗留问题

Linux 实跑后核对 EOF 顺序能否消除原始链路故障，并检查完整容量和清理结果。用户已授权本次提交推送及 GitHub Linux CI；独立复核委派仍待对应授权，验收闸仍由用户签署。

## 剩余风险

当前 CI 基线未全绿，不能完成 WORK-050 或开始 WORK-049 源代码重构。当前服务器未更新；修复后的配套二进制后续按新身份部署校准，不沿用旧指纹。

## 结论

result=pending；用户已签署意图闸并允许实施，本地修复及回归已完成，Linux 实机、完整 CI 和独立复核尚待执行。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：保留失败证据与未执行项，无修复通过结论
- 2026-09-10：记录实际旧红新绿、本地全量回归及交叉编译，明确 Linux 和复核缺口。
