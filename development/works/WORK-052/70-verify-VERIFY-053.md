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
verifies: ["ISSUE-016#REQ-001", "ISSUE-016#REQ-002", "ISSUE-016#REQ-003", "ISSUE-016#AC-001", "ISSUE-016#AC-002", "ISSUE-016#AC-003", "ISSUE-016#AC-004"]
tags: []
result: "pass"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-053：控制消息中断处理验证

## 验证对象

ISSUE-016 AC-001～004：确定性复现、消息/FD/期限语义、完整Linux及独立复核。

## 对应要求

| 要求 | 技术结论与证据 |
|---|---|
| REQ-001 / AC-001 | 通过。相同实现的参数注入旧红新绿，Linux真实接收信号、一次消息/FD及EOF；不追溯最早失败信号。 |
| REQ-002 / AC-002 | 通过。FD引用、短写/非法/截断/错误释放、连续中断遇Close、真实shutdown及Poll绝对期限；原helper期限/回收链保留，未将分项证据合称信号风暴整链实测。 |
| REQ-003 / AC-003 | 通过。b699604两台新VM完整9job成功，63内核/52必需Go/10原生、1000次/并发/故障及全空清理已下载复验。仅CI安装驱动256MiB例外由用户明确批准。 |
| AC-004 | 技术复核通过。独立复核覆盖生产及CI例外，观测P2已修正并复查；现有服务器/身份/业务不变。人工验收及交回仍待用户签闸。 |

## 检查与结果

[CI34503720792](https://github.com/charon2121/cherry-oj/actions/runs/34503720792)，提交a0aea384a810c4f2aa81d4176fbc11cf62ceb7d2。kernel的TestStartupBoundaries/wrong-go在start_linux_test.go:166调用event()时报interrupted system call（该行同时覆盖内部Poll和ReceiveEvent错误，不能定位到Recvmsg）；其他同组场景通过，后续kernel批次未执行。源码channel_linux.go直接返回unix.Recvmsg错误。

下载制品位于/private/tmp/cherry-work050-sandbox-kernel-34503720792；cleanup.json confirmed=true。该历史下载本身不包含本工作新增诊断的运行证据。该证据来自WORK-050，其他原生失败另归TASK-111。

## 未通过项

当前技术验收无未解决阻断。历史CI34557663857原生安装OOM仍保留为1FAIL/9NOT_RUN；后续通过不改写这条记录。256MiB安装仍有文件页压力，风险及证据限制见下节，不承诺未来不再失败。

## 范围检查

已发布生产仅channel_linux.go，测试覆盖FD/中断/关闭/期限与真实信号；本轮新增仅CI安装内存观测及其测试，编码前同步DESIGN/PLAN/TASK路径。安装器、协议、隔离策略、资源预算和现有服务器不变。

## 遗留问题

最早EINTR的具体信号/系统调用无法追溯；新增阶段日志、可控序列及真实信号验证只支持明示条件下的恢复。最早安装OOM缺少当时内存组成与安装阶段证据，根因未确证；再次失败按新观测调查，不自动上调或重试掩盖失败。

## 剩余风险

安装驱动按用户批准使用256MiB，仍触及上限，采样中主要为文件页；两台成功不是长期稳定性保证。真实recvmsg信号测试使用生产没有配置的SO_RCVTIMEO，不推断常态触发概率。连续EINTR+Close与shutdown为分项证据，不声称真实helper信号风暴下墙钟终止已实测。当前实跑仅记录Ubuntu24.04/Linux6.17/x86_64/AppArmor；现有服务器和其他平台未更新或新增验证。

## 结论

result=pass，指TASK-116范围内技术验证通过，状态仍review，人工验收闸pending。授权的中断修复、独立复核、发布、现有全量CI以及仅安装驱动256MiB实测均完成；保留历史失败及上述风险供用户签署。WORK-050真实业务与最终汇总未实现，本结论不能当作其完整93项基线或WORK-049重构放行。

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


## 本地修复候选（2026-09-11，发布前记录）

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

## 生产修复发布与新阻断（2026-09-11）

提交b3e5ec0e8355833e3ddc6699d717f8398fab4944，[CI34557663857](https://github.com/charon2121/cherry-oj/actions/runs/34557663857)八个job通过、原生job失败。同SHA的push运行34557663756在任务步骤开始前取消，不计为验证结果。

- 内核63项PASS、52个必需Linux Go测试通过且无skip；boundary与smoke/1000次/并发/故障日志经结果校验器复验。真实信号输出同时包含`poll=EINTR receiveCallsBeforeSend=0 message=workspace fd=once eof=true`与`recvmsg-blocked=true signal=SIGUSR1 message=workspace fd=once eof=true`。这验证构造条件下的恢复，不追溯历史来源，也不等同helper信号风暴下完整超时实测。
- 原生install.log记录519ms、CPU279ms、Memory peak128.0M、swap0、result=oom-kill；helper/sandbox/judge均尚未安装。1FAIL、9NOT_RUN，不能宣称本轮完整通过。
- 两套report精确sourceSha及harnessSha=60c0ae2b6e784e1c1c91a5436698d4ddea3fa3658372bf80dd681547393524ee通过validate与verify_files（原生successful=False）。cleanup confirmed=true；内核任务/挂载/cgroup为空，原生额外路径/账号/组也为空。下载目录/private/tmp/cherry-work052-{kernel,native}-34557663857。环境Ubuntu24.04.5、Linux6.17.0-1022-azure、x86_64、AppArmor，GitHub镜像20260907.300.1。
- 正常commit/push hooks通过，推送前全模块无缓存Go race通过；未触碰本地ZIP及现有服务。
- 只读独立复核新失败确认：128MiB覆盖安装驱动、账号创建子进程和文件页，现有证据不足以区分来源。补充组外10ms采样，尾部最多64条及最高memory.current记录，直接读取memory.peak并明确内存组成并非原子快照；失败前仅保存安装回执的状态/创建计数和release目录是否存在，不能导出配置/token。未提高预算或重试安装。
- 观测本地基础CI62项单测通过（安装15/rootfs6/CI41），Python与shell语法通过，446份开发文档及515份Markdown检查通过。观测自身尚待发布实测；TASK-116与WORK-052验收保持未完成。

- 2026-09-11：独立复核观测代码发现P2：安装OOM可能中断回执写入，JSON解析失败会阻止原服务诊断。已局部记录受控receiptReadError并继续systemctl/journal；截断、null及异常accounts结构三反例通过。此前34507853030与34508410501的安装成功日志均Memory peak128.0M，说明上限接触并非新版本独有；不据此断言此次OOM根因。

## 相同128MiB预算的两台新VM观测（2026-09-11）

源码41a53963bf8be433cf56c6e10ceb3542ba8df509，harness=5fe0d6892f74a7139d6ffe800517ba289ed756a5b555f60dfbd4cc90cf9b5066；[首轮34558367861](https://github.com/charon2121/cherry-oj/actions/runs/34558367861)九job通过，[第二轮34558596798](https://github.com/charon2121/cherry-oj/actions/runs/34558596798)为同SHA另一次完整workflow、新VM，无安装预热、无更改资源限制、没有重跑旧失败job。

| 安装观测 | 第一台 | 第二台 |
|---|---|---|
| 自然完成耗时 | 2.452s | 2.248s |
| 内核memory.peak | 134217728bytes | 134217728bytes |
| 最高current样本附近file | 119693312bytes | 120041472bytes |
| 最高current样本附近anon | 9564160bytes | 9412608bytes |
| 最后观测memory.events.max | 779 | 778 |
| 最后观测oom/oom_kill | 0/0 | 0/0 |
| 采样数/错误数 | 240/0 | 214/0 |

两轮最高current均达到128MiB，stat显示文件页为主要组成；第一台该样本还有29966336bytes写回，第二台有25849856bytes脏页。组成分别读取，并非峰值瞬间的原子快照；max计数不是OOM次数。前一次失败没有这些观测，不能将新样本倒推成旧OOM根因，也不能从受限峰值推导自然需求。

首轮63内核/52必需Go/10原生及完整清理已下载复验；第二轮亦九job全部成功，63内核/52必需Go、boundary与1000次/并发/故障、10原生及全部清理已下载复验。目录/private/tmp/cherry-work052-{kernel,native}-34558367861与/private/tmp/cherry-work052-{kernel,native}-34558596798，report.validate/verify_files及原生九类结果解析均通过；正式资源与安装限额未改变。

独立复核原始观测确认文件页主导这一有限结论，不认为任一更高额度已经证明足够。候选收窄为仅安装驱动256MiB起步，经用户确认后再实测；不是提升payload/编译内存，不修改其他管理驱动。TASK-116保持doing，尚不将已有一次OOM解释为已解决；对应候选见DESIGN-046，原PLAN禁止扩大预算的约束仍生效。

- 2026-09-11：用户明确同意仅安装驱动128→256MiB并验证，已同步DESIGN/PLAN例外，未代签验收。实施仅native.command按install选择256MiB，其余10个管理/验证步骤保持128MiB和原90/120秒期限；swap/pids/CPU/正式24项限额未改。basic62项及文档校验通过，独立复核确认仅此批准例外，无阻断。
- 2026-09-11：调整前归档版本8f898fb的第三台128MiB原生VM亦10项通过、安装4.781s/峰值128MiB，最高current附近file119709696、anon9453568bytes，最后max782/oom0；全部清理为空。下载/private/tmp/cherry-work052-native-34558941041，sourceSHA及原5fe0d6开头harness核验通过（使用此前已核验的同一harness，避免误与未提交256MiB工作树比较）。该成功再次支持文件页压力，仍不覆盖旧OOM失败。

## 用户批准256MiB后的验证（2026-09-11）

源码b6996040a657bca3a5f1e97759880d5ce05eeb03，harness=1eba2b3632c97b16cb8bf7446001d4ccb6a4d64c3efc96bb6c969aaacd76a1f9。[首次34559188597](https://github.com/charon2121/cherry-oj/actions/runs/34559188597)九job全部成功；下载/private/tmp/cherry-work052-{kernel,native}-34559188597，report.validate/verify_files、52必需Go、boundary、smoke/1000次/并发/故障及原生九类结果解析复验通过。63内核/10原生PASS，内核与原生全部归属资源为空、cleanup confirmed=true。两套实际环境Ubuntu24.04.5/Linux6.17.0-1022-azure/x86_64/AppArmor，GitHub镜像20260907.300.1。

安装2.088s，内核memory.peak=268435456bytes，最高current附近file244719616、anon9838592、kernel13447168bytes；最后max273/oom0/oom_kill0，199个无错样本。文件页仍可占满新上限；不能用受限峰值证明自然需求，也不继续自动上调。其余管理/验证驱动128MiB以及正式24项限额保持原样。

只读独立复核该观测未发现必须继续加额度或修改代码的阻断；认可核心中断修复的技术成果与安装压力剩余风险分别记录。最早128MiB OOM的当时内存组成未知，根因未确证，不能承诺已杜绝未来OOM；再次失败需按新证据调查，不自动上调或重试刷绿。第二台同SHA完整运行34559407284已结束，结果见下条。

- 2026-09-11：[第二台34559407284](https://github.com/charon2121/cherry-oj/actions/runs/34559407284)同b699604源码九job全部成功。下载/private/tmp/cherry-work052-{kernel,native}-34559407284，sourceSHA/harness、report.validate/verify_files、52必需Go、boundary/全部chain和九类原生结果逐项复验通过，63内核/10原生PASS，全部归属资源清理为空。安装2.040s、峰值268435456bytes，193个无错样本，最高current附近file242630656/anon11812864bytes，最后max273/oom0/oom_kill0。两轮保留原始独立运行，不重跑覆盖失败。
- 2026-09-11：独立复核确认无待修阻断，安装文件页触顶本身不要求再加额度；按用户批准维持256MiB，将未确证的最早OOM及观测限制列入验收风险。技术检查完成，result记录pass，未代签验收闸。
