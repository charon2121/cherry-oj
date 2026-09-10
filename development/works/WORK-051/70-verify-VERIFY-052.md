---
id: "VERIFY-052"
type: "verify"
title: "修复沙箱连续请求完成与容量归还的竞态"
status: "approved"
work: "WORK-051"
owners: ["codex/root"]
depends_on: ["TASK-114"]
related: []
implements: []
verifies: ["ISSUE-015#AC-001", "ISSUE-015#AC-002", "ISSUE-015#AC-003", "ISSUE-015#AC-004"]
tags: []
result: "pass"
created_at: "2026-09-10"
updated_at: "2026-09-11"
---

# VERIFY-052：连续请求异常修复验证

## 验证对象

ISSUE-015 AC-001～004：时序回归、收尾与容量安全、完整CI和边界复核。

## 对应要求

| 要求 | 本轮证据 | 状态 |
|---|---|---|
| AC-001 时序回归 | 旧客户端在完成后连接仍开启、尾部有垃圾两个用例失败；修复后本地及 Linux 通过，服务端受控 EOF/槽位测试通过 | 通过 |
| AC-002 收尾及容量 | 本地协议/取消/早期失败阻塞输入回归通过；Linux 实际 reset、慢交付、清理后复用及完整容量/故障套件通过 | 通过 |
| AC-003 完整回归 | 946e528及后续e44f9b4的8 job、Linux63项与45必需Go用例通过；TASK-115已验证并修复独立的语言功能测试期限问题；a0e1b35最终CI全绿 | 通过 |
| AC-004 边界及复核 | 生产仅两个 helper 文件；测试消费者边界先更新再修改。独立只读复核未发现本修复引入的阻断问题，范围及两项证据限制见下文；未部署或更改原节点 | 通过 |

## 检查与结果

环境：本地 Darwin 24.3.0 / arm64，Go 1.26.3。本轮均为基于 `7a66b35fd157272e6dcc0a3a80a7bca0ad8cd690` 的未提交修改，不是该 SHA 的干净运行，也不代表 Linux 支持验证。

1. 先仅新增客户端回归、保留旧 client.go，执行 `go test -race -count=1 -run 'TestClient(WaitsForConnectionRelease|RejectsTrailingData)' ./internal/sandbox/helper`：两个用例均 FAIL，分别报告“完成帧之后没有 EOF，客户端仍返回成功”和“完成帧之后存在多余字节，客户端仍返回成功”。首次受本地工具沙箱限制 bind 被拒，取得本地测试执行权限后才得到上述行为失败；未把权限失败算作缺陷复现。
2. 修改客户端与服务端后执行 `go test -race -count=1 -p=1 ./...`：全部可在本地运行的包 PASS；`go vet ./...` PASS。客户端正常流式交付、早期平台失败关闭阻塞输入、缺尾帧、非法尾帧、额外字节、期限内无 EOF、完成后取消和原有生命周期失败回归均通过。
3. `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go test -c -o /private/tmp/cherry-work051-helper.test ./internal/sandbox/helper` PASS，仅编译证据。新增服务端测试通过真实 Unix socket 与关闭前的同步观察确认槽位在 EOF 前可用；慢交付用信号固定占槽阶段；reset 用 Linux MSG_PEEK 保留未消费输入，关闭时要求实际 ECONNRESET。这三个用例尚未实跑。
4. `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work051-basic` PASS：15 安装、6 rootfs、18 CI 单测；43 Python AST、4 shell 语法通过。该目录是本地临时证据，不提交为固定基线。
5. `scripts/work check` 437 份文档通过，仅原有 WORK-033 推导提示；`git diff --check` PASS。CI 必需 Go 测试清单由 38 项增为 45 项，原 63 个 kernel 场景不减少。

源码收尾顺序：serveConn 完成执行清理、产物交付、FD Close 及取消 defer；fatal 先通知停服；serveInSlot 归还槽位后关闭 Unix socket；客户端验证正常 EOF 后返回。槽位归还之前包括全部可阻塞交付，之后没有协议写入或后台任务，仅本地 socket 关闭。保持 accept 处无槽即拒绝及原连接/写期限。

## 服务端受控旧顺序对照

Linux 的当前实现已实跑通过后，进一步补齐旧服务端顺序的确定性对照。macOS 使用临时 Go `-overlay`，把当前 server_linux_amd64.go 中的 serveInSlot 原函数及 server_linux_amd64_test.go 中的真实 Unix socket 槽位用例并入测试文件；旧版本仅交换该函数的两个 defer，恢复原“先关闭连接、后归还槽位”的顺序。两份 overlay 和提取内容保存在 `/private/tmp/cherry-work051-slot-order`，没有改动仓库源码或新增生产分支。

同一命令 `go test -race -count=1 -overlay <old|fixed>.json -run '^TestConnectionEOFReturnsCleanSlot$' ./internal/sandbox/helper`：旧顺序 FAIL，报“EOF 前下一连接未能取得已经清理的槽位”；当前顺序 PASS。测试在 Close 交付 EOF 之前同步尝试取得唯一槽位，且完成帧发出后用通道暂停清理；不靠循环碰调度窗口。此对照只证明收尾次序，Linux 系统行为仍以以下 CI 实跑为准。

## 修复后 Linux 实测

[CI 34470867753](https://github.com/charon2121/cherry-oj/actions/runs/34470867753) 对应 `946e52890aae40017ea1802dfc5784bb975eda83`：8 个 job 全部成功。Ubuntu 24.04.5 / Linux 6.17.0-1022-azure / x86_64，4 CPU，约 16 GiB RAM、3 GiB swap；LSM 为 lockdown/capability/landlock/yama/apparmor/ima/evm，runner image 20260907.300.1。没有关闭 LSM 或修改现有服务器。

下载 sandbox-kernel 与 sandbox-kernel-build 制品至本地临时目录后，重新执行 report.validate、report.verify_files、results.linux_units、results.boundary 和三个 results.chain 校验，全部通过。sourceSha 与本次提交一致，harnessSha 为 `425160ccb62b94c681673d79ebc0757629ec1f331e1ff4e8b04931dae81aedf3`；63 个 kernel 项全部 PASS，无 NOT_RUN。45 个必需 Go 测试无 skip/fail，包括本次 7 个回归；真实 Unix ECONNRESET、EOF 前取得已清理槽位和慢交付期限均通过。

- 原失败 magiclink 与 zero-output-writer 分别恢复预期 Signalled、OutputLimitExceeded；取消后的下一空程序 OK。
- CPU 1 秒预算：单循环 1.003224 秒 CPU / 1.063428 秒墙钟，整树 1.009736 秒 CPU / 1.053690 秒墙钟。输出超限后空程序独立峰值 8,126,464 bytes，没有继承历史峰值。
- 连续 1000 次全部成功，共 22.351416 秒；单次墙钟中位数 20,028,614 ns、最大 110,362,626 ns，峰值内存最大 10,604,544 bytes。前后同 PID 的 helper FD=9、HTTP FD=11，任务/组/挂载为空，工作目录及产物基线不增长。
- 两个 2 秒任务共 2.026625 秒，peakGroups=2，证明确实并发；两槽/四队列满载第七请求 503，10 个 handler 满载第十一个请求 503。
- init/HTTP/helper SIGKILL、正常停止、排队断连、恢复及清理均通过。祖先 96 MiB 聚合 OOM 保持 InternalError；任务自身 64 MiB OOM 为 MemoryLimitExceeded。不同槽身份及文件/PID隔离、提权拒绝通过。
- cleanup.json 为 PASS；resources-after.json 中 tasks/mounts/cgroups 均为空。报告与日志保存在该 CI 的制品中，本地下载副本 `/private/tmp/cherry-work051-ci-34470867753-kernel` 不提交为固定测试输入。

## 最新文档提交的独立 Go 失败

[b03e6bd / CI 34471753900](https://github.com/charon2121/cherry-oj/actions/runs/34471753900) 最终 7/8 成功。Linux 63 项和 45 必需 Go 测试及 cleanup 再次 PASS，下载后重新校验 sourceSha、harnessSha、逐项报告和循环/并发标记，resources-after 的 tasks/mounts/cgroups 为空。代码与 946e528 相同。

普通 Go job 失败于原 TestEndToEndJavaWithInnerClass：5.00 秒、编译 exit=-1，stderr 为空；未报告 race。源码显示它使用可信 host 默认 5 秒，且漏报 Wait error；尚无直接超时原因日志，不能称已确定根因。原日志保存为 `/private/tmp/cherry-work051-ci-34471753900-go.log`。这否定“最新 main 整体 CI 已全绿”的结论，但不抹去上一轮或两轮 Linux 专项通过的事实。

已在 WORK-050 整理待审 TASK-115，拟仅修正语言功能测试的诊断与测试专用编译期限；本轮未修改该测试、host、工作流或预算，未重跑覆盖。新增提案和本段记录暂留工作区，避免在红色 CI 上继续叠提交。

## 未通过项

无未解决的阻断项。TASK-115多轮诊断和测试专用15秒实施已完成，最新main现有8项CI通过；2026-09-11独立静态复核完成。旧红新绿和整链通过支持此修复解决已识别的次序问题，不声称所有连接 reset 都只可能有这一原因。

## 范围检查

实施限定在 TASK-114 的两个 helper 生产文件、对应测试及 README、cases.json、两个测试私有客户端及本工作文档。读取消费者前新增只读路径；根据已确认的同构建协议同步目标，在 DESIGN/PLAN/TASK 中先补入 tests/client.py 与 tests/smoke.py 的精确写边界，再增加 EOF 断言。未修改执行器、权限、cgroup、公开业务契约、服务配置或测试预算。

## 遗留问题

独立源码复核已完成；两项非阻断测试覆盖限制见末节，未据此声称覆盖生产慢交付期限或精确同步的主动取消阶段。验收闸仍由用户签署；按PLAN-035第5步，签署后才交回WORK-050继续部署及业务CI。

## 剩余风险

当前已有多轮内核及现有 8 job 全绿，但 WORK-050 尚有原生部署、真实业务及最终复核任务，不能宣称全部 CI 整理完成或开始 WORK-049 源代码重构。当前服务器未更新；修复后的配套二进制后续按新身份部署校准，不沿用旧指纹。

## 结论

技术验证通过：修复和后续语言功能期限调整的完整现有CI通过，独立复核未发现本次引入的阻断问题。TASK-114具备技术完成条件；VERIFY保持review等待人工验收，测试通过及用户委派复核授权均不代替验收签署。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：保留失败证据与未执行项，无修复通过结论
- 2026-09-10：记录实际旧红新绿、本地全量回归及交叉编译，明确 Linux 和复核缺口。
- 2026-09-10：记录修复提交 946e528 的 8 job、63 内核项、45 Go 测试及完整回收成功证据；没有代签验收或独立复核。
- 2026-09-11：独立源码复核完成，记录无阻断结论及两项证据限制；result由pending更新为pass，status仍为review等待人工验收。
- 2026-09-11：验收闸通过：review → approved。原因：确认连续请求修复及独立复核结论，接受已记录的测试覆盖限制

## 当前回归交接与独立复核材料

TASK-115已完成：e44f9b495e1f179c0bc7c49a590f73f7bd166ed6的[CI34478647561](https://github.com/charon2121/cherry-oj/actions/runs/34478647561)8/8通过，下载内核产物后report.validate、verify_files及linux_units确认63项case、45个必需Go用例与完整清理通过。最新a0e1b35f8037936906c69a3e80677dc60f24b12a的[CI34479485875](https://github.com/charon2121/cherry-oj/actions/runs/34479485875)亦8/8通过。`git diff 946e528..HEAD -- apps/judge-engine/internal/sandbox/helper deploy/sandbox-linux/tests/client.py deploy/sandbox-linux/tests/smoke.py`为空，helper修复及配套消费者未被后续期限工作改动。TASK-115只调整可信语言功能测试的编译等待，严格内核限额保留；旧失败日志仍在前文，不覆盖。

本次交给独立审查者的具体范围：

- 比较7a66b35fd157272e6dcc0a3a80a7bca0ad8cd690至946e52890aae40017ea1802dfc5784bb975eda83的TASK-114改动，再确认当前SHA相关文件一致；可读helper包以追踪调用，生产修改实际仅client.go与server_linux_amd64.go。
- 客户端：Completion后的正常EOF是否唯一成功路径；垃圾尾部、reset、超时、取消、输入阻塞的结束是否正确且有界。
- 服务端：serveConn的全部执行/交付/清理与fatal停服，是否先于归还槽位；serveInSlot中槽位归还是否先于socket关闭且恰好一次；真正满槽与慢客户端是否仍受限。
- 测试：旧红新绿是否验证真实次序，Linux的EOF/slot、慢交付和reset测试是否存在假阳性；ci/cases.json及私有Python客户端是否完整遵循同一完成语义。
- 对照本VERIFY的失败证据、当前完整CI和内核63项/1000次/并发/容量/故障/清理，输出按严重程度排序的问题、文件行号与理由。报告无问题也须列实际审阅范围与证据限制。

复核只读，不修改代码、不连接现有服务器、不触发部署/重启、不访问AGETNTS.local.md或测试ZIP，不代签任何人工闸。用户在看到此范围后于2026-09-11明确授权，已委派独立审查者完成以下复核。

## 独立源码复核（2026-09-11）

审查者：独立子智能体Zeno（`/root/work051_review`），不继承实施会话，由上述具体材料独立读取源码。审阅`7a66b35..946e528`指定八个文件及helper执行、清理、交付、输入线程和身份槽调用链；确认整个helper包和两个Python消费者从946e528至a0e1b35没有变化。结论：未发现WORK-051引入的可确认P0/P1/P2问题，无阻断该修复的源码发现。

- client.go要求合法Completion、正常EOF和未取消上下文才能成功；额外字节、reset、缺尾帧与读取错误拒绝成功。原defer关闭连接及输入并等待上传线程，输入有界性仍依赖原契约：input.Close必须解除读取阻塞。
- server_linux_amd64.go中serveInSlot涵盖serveConn执行/交付/资源关闭、fatal通知及stop；全部返回后归还槽位，最后关闭socket。清理失败不发送有效Completion，慢交付仍占槽，满槽立即拒绝；没有增加队列或重试。
- 旧次序由确定性测试检出：客户端保持连接直到期限，服务端在实际Close前同步取槽。reset测试要求实际ECONNRESET。helper的27个顶层测试与cases.json逐名一致，全部必需Go测试45个；两个Python消费者保留原socket期限并校验EOF。

审查同时指出两项非阻断证据限制，实施者已对照源码确认并收窄结论：

1. `server_linux_amd64_test.go:91`慢交付测试用net.Pipe与测试自行设置的100ms写期限，只验证包装器占槽及超时释放。生产serveConn的10秒交付期限仍在，但该测试不能直接检出生产期限被删除，前文“慢交付期限通过”仅指此包装器测试。
2. `client_test.go:190`在服务端写完Completion后主动取消，没有同步确认客户端已读完尾帧，可能在读尾帧前失败。因此不能单凭它宣称精确覆盖“进入EOF等待后的主动取消”；`TestClientWaitsForConnectionRelease`确实检查EOF等待阶段的期限取消，使用同一取消关闭连接机制。

本次独立工作仅为静态复核，没有重新运行测试、访问CI或下载制品。Linux及完整CI仍引用上文已有实跑证据，不增加本次实机验证声明。未改动生产代码或测试，未放宽任何断言；两项进一步增强测试的机会保留为覆盖限制，不当作已完成的测试。人工验收保持未签署。
