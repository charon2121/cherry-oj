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

已完成已发布诊断SHA的Linux回归；本地候选修复、可控收发旧红新绿和等待期限测试已通过，修复后的Linux实跑及独立复核仍未完成，四项AC不据此整体通过。

## 检查与结果

[CI34503720792](https://github.com/charon2121/cherry-oj/actions/runs/34503720792)，提交a0aea384a810c4f2aa81d4176fbc11cf62ceb7d2。kernel的TestStartupBoundaries/wrong-go在start_linux_test.go:166调用event()时报interrupted system call（该行同时覆盖内部Poll和ReceiveEvent错误，不能定位到Recvmsg）；其他同组场景通过，后续kernel批次未执行。源码channel_linux.go直接返回unix.Recvmsg错误。

下载制品位于/private/tmp/cherry-work050-sandbox-kernel-34503720792；cleanup.json confirmed=true。该历史下载本身不包含本工作新增诊断的运行证据。该证据来自WORK-050，其他原生失败另归TASK-111。

## 未通过项

真实Linux信号诊断已通过；生产修复、原失败的确定归因、收发旧红新绿、连续中断/取消/期限验证及独立复核尚未完成。

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

- 2026-09-11：首轮发布9a0ad6743574dc167b6659f7a3692797f269ab92，CI34507325931的kernel失败于新增control-signal-observation：`pending signal poll: invalid argument`；原八项启动场景全部通过。首轮未成功注入信号，不能支持EINTR归因。下载目录/private/tmp/cherry-work052-kernel-34507325931，经validate(successful=False)与verify_files核对，harness=ab2824a62b22fb5c0ce32f375d0bdccd5429dac4dd6e49dc7d3ebc77c5622889，kernel2PASS、4FAIL、57NOT_RUN，cleanup confirmed=true。
- 2026-09-11：核对锁定x/sys v0.46.0的zsyscall_linux.go:137，Ppoll底层把sigsetsize固定传0；原无掩码Poll不受影响，新增非空掩码夹具因此EINVAL。修正仅在该测试使用unix.Syscall6调用ppoll，传递Linux/amd64的64位内核掩码与8字节大小；不改依赖、生产、场景预算或错误判据。Linux目标vet与交叉构建通过，需新SHA实跑确认。


## 诊断发布结果（2026-09-11）

提交ebb8b7355d7c1e6bbf37ed4c8dc54b1f4ebe3bc9，[CI34507853030](https://github.com/charon2121/cherry-oj/actions/runs/34507853030)九个job全部success。第一轮34507325931的夹具错误与失败报告保留，不覆盖为PASS。

- 实际环境：Ubuntu24.04.5、Linux6.17.0-1022-azure、x86_64、LSM含AppArmor，GitHub镜像20260907.300.1。不是用户服务器测试，不代表其他Linux支持。
- 新增诊断输出`poll=EINTR receiveCallsBeforeSend=0 message=workspace fd=once eof=true`：线程定向待决SIGUSR1在ppoll原子解屏蔽时产生真实EINTR，生产ReceiveEvent尚未被调用；随后原生产SendEvent/ReceiveEvent完整交付一次消息、同一目录FD并观察EOF。该用例没有向生产Recvmsg注入EINTR，因此不能证明其恢复能力，也不能追溯证明历史失败的具体系统调用或信号。
- 内核63项PASS、45个必需Linux Go测试无skip；原八项启动边界及新增诊断、文件/exec边界、整链smoke、1000次、并发与故障完成。
- 原生10项PASS；重新执行native_results全部检查，包含八个不同的真实权限测试启动记录、服务故障恢复与卸载保留/还原。内核和原生cleanup.json均confirmed=true；任务、挂载、cgroup全部为空，原生额外路径、账户、用户组亦为空。
- 下载目录：/private/tmp/cherry-work052-kernel-34507853030及/private/tmp/cherry-work052-native-34507853030。两份report.validate与verify_files均通过，sourceSha与上述提交一致，harnessSha均为a0568d8816c5f137a52fb1b1c2469a5ec8d6fcbaa0eef0e9c6795ecc5ee8968c；已重新核验Linux必需测试、boundary/chain标记和原生断言。
- 发布正常执行hooks，无缓存Go race通过。未修改生产通道或现有服务器，未读取/纳入本地测试数据ZIP。TASK-116保持doing，WORK-052验收闸pending；本次诊断通过不关闭原始问题，也不解除TASK-112依赖。


## 本地修复候选（2026-09-11，未提交）

- 生产仅修改launcher/channel_linux.go。sendmsg/recvmsg每次在RawConn.Control持有文件引用后调用，EINTR仅在未交付数据/FD时继续；每次继续前重新获取文件引用，观察关闭，附带FD同样保护。非EINTR、短写与部分交付仍报错；接收错误先释放已收到的FD。
- 取消仍沿既有helper监控期限、停止资源组、shutdown、等待、Close的生命周期；未新增或重置生产预算。控制socket仍阻塞，Close单独唤醒不作承诺；SO_RCVTIMEO只用于Linux测试触发信号中断，不是生产新增配置。
- 本地使用Go overlay编译相同channel实现及白盒测试：仅在临时编译副本中将macOS缺少的SOCK_CLOEXEC/MSG_CMSG_CLOEXEC常量置0，所选用例不调用SocketPair或真实sendmsg/recvmsg，而是在窄参数适配器注入结果；使用真实本地文件描述符验证引用、关闭和FD归属。没有修改Darwin生产实现，不能将这些结果表述为Linux内核验证。
- 旧行为对照保存在/private/tmp/cherry-work052-channel/before_linux.go，只引入参数注入入口、保留原单次调用/提前返回语义。相同五组测试的before.log退出1：收发首个EINTR失败、连续中断无法完成、部分交付错误的防御性输入导致FD未关闭；after.log退出0。后者是故障注入的防御性检查，不是已观察到Linux内核返回FD同时报错的事实。
- 新增七个必需Linux Go测试，清单45→52；包含真实seqpacket在注入中断后的一次消息/FD/CLOEXEC/EOF，以及真实阻塞收发的shutdown唤醒。新增真实信号夹具会先观察接收线程进入/proc/self/task/TID/syscall中的recvmsg，再线程定向SIGUSR1并验证ReceiveEvent继续等待和后续交付；该新夹具尚未在Linux实跑。
- 测试自身Poll使用原两秒绝对截止时间，仅恢复其自身EINTR，后续ReceiveEvent不被测试重试。虚拟时钟用例验证连续中断的等待参数依次2000、1300、600毫秒，到期后没有第四次调用；就绪恢复、EIO和非法FD也分别校验。通过临时overlay原样编译该测试辅助函数和用例，在macOS执行race通过。
- 本地Go1.26.3：全模块go vet和go test -race通过（部分原有测试缓存命中）；Linux/amd64的launcher与boundary vet及测试二进制交叉构建通过；基础CI56项单测、AST/shell检查通过，报告/private/tmp/cherry-work052-channel-basic-final。交叉构建不代表新增Linux用例已经运行。
- 本批仅涉及TASK允许的生产channel、测试、cases清单及WORK-052记录；已在改变测试等待行为之前同步DESIGN/PLAN/TASK边界及理由。原服务器、IDEA、业务实现及本地ZIP未改动。独立复核、发布与Linux验证待本批授权；TASK-116仍doing，TASK-112/113仍按既定依赖等待。

- 2026-09-11：用户明确授权本批独立复核子智能体、修复问题后提交推送及Linux CI。只读work052_review已启动；不是人工验收。另将Poll恢复分支从临时副本删除进行变异验证，continuous-interruption和ready-after-interruption按预期失败（exit1），证明期限与恢复用例能拦住退化；记录/private/tmp/cherry-work052-channel/wait-no-recovery.log。

- 2026-09-11：独立只读work052_review完成源码、全部新增测试与Go1.26.3 RawControl引用语义审查，未发现新增P0/P1/P2阻断。确认生产只恢复无交付EINTR、文件引用与附带FD保护、helper原取消链及测试Poll绝对期限。复核未独立运行测试，真实Linux尚待本批CI；连续中断+Close白盒与shutdown测试分别取证，不能合称“真实helper在信号风暴下墙钟超时已验证”。shutdown用例20ms未返回不等于线程syscall观测；真实接收信号夹具用生产未配置的SO_RCVTIMEO构造可中断条件，不证明常态触发频率或最早失败来源。
