---
id: "VERIFY-050"
type: "verify"
title: "按命令执行顺序重构 Go 判题引擎源码"
status: "approved"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["TASK-104", "TASK-105", "TASK-106", "TASK-107"]
related: []
implements: []
verifies: ["CHANGE-013#AC-001", "CHANGE-013#AC-002", "CHANGE-013#AC-003", "CHANGE-013#AC-004", "CHANGE-013#AC-005", "CHANGE-013#AC-006", "TASK-104", "TASK-105", "TASK-106", "TASK-107"]
tags: []
result: "pass"
created_at: "2026-09-10"
updated_at: "2026-09-15"
---

# VERIFY-050：阅读性重构验证记录

## 验证对象

WORK-049 的当前对象设计和 R0～R8 计划，以及已交付 B0～B4 的历史本地证据。R0～R8 技术实施与验证完成：独立结构阅读七问通过，最终候选完整 Linux CI 93/93 项及全部必需 job 通过。result=pass 表示技术证据通过，人工验收闸仍待用户签署。

## 对应要求

| 验收标准 | 需要的证据 | 当前状态 |
|---|---|---|
| CHANGE-013#AC-001 | 两条入口阅读路线与实际符号、跨进程对端 | R8 独立阅读通过；七问与符号见末尾附录 |
| CHANGE-013#AC-002 | 正常、超时、取消的事件与资源归属复核 | R8 已独立走通正常、超时、取消和部分启动失败路径 |
| CHANGE-013#AC-003 | 数字清单、原值/新值、协议映射与依据 | 固定挂载/exec/协议值对照通过；Limits 六字段显式零与默认映射测试通过 |
| CHANGE-013#AC-004 | 格式、静态检查、race 与 Linux 实机回归 | 最终候选本地及 Linux race 通过；Linux 93/93 与全部必需 job、清理证据通过 |
| CHANGE-013#AC-005 | 候选规范适用性记录及人工判断 | 11 项候选已逐项给出处置及依据；未提升全局规范，人工判断待签 |
| CHANGE-013#AC-006 | 冻结基线与最终 diff 行为等价检查 | R0 快照与候选模块比对通过；harness/93项/52入口不变，同候选同批 CI 完整通过 |

## 检查与结果

- 2026-09-10：读取仓库协作协议、Go/通用规范与 WORK-048 决策，运行 scripts/work overview、board WORK-048，并沿当前源码核对调用路径。
- 同日创建 WORK-049 文档，通过工具分配 CHANGE-013、DESIGN-043、DECISION-027、PLAN-033、TASK-104～107、VERIFY-050、MEMORY-036。
- 2026-09-10：在仓库根执行 scripts/work check，415 份开发文档通过；保留已有 WORK-033 状态推导提示，未修改该工作。
- 同日执行 scripts/work board WORK-049，确认 WORK 与四个 TASK 均为 todo、两道闸未签；上游提案为 review。git diff --check 无输出。
- 以上为文档校验，不能推断业务实现通过、阅读性已改善或人工已经签署。

## 未通过项

无未通过的 R8 技术验收项。首次本地 basic 的监听权限错误已定位并经允许监听后的同套件重跑消除，保留原失败记录。人工验收与候选规范是否全局化不由技术通过代签。

## 范围检查

首次建档修改 development/works/WORK-049 及工具维护的 development/WORKS.md、development/index.json；2026-09-13 本轮计划细化仅修改 WORK-049 和工具维护的 WORKS。
保留原有 WORK-048、业务代码、部署材料和用户工作区改动；没有提交、推送或部署。

## 遗留问题

- 2026-09-13 复核：WORK-048/050 已验收，HEAD 和 CI 摘要与冻结基线一致；TASK-104 已再次复查并记录工作区增量归属。
- 先前的 Linux 环境探测冲突已有 probeDeployment 分支处理，不再列为当前阻断。日志错误信息与配置校验耦合仍需按新基线复核，B1 没有夹带这些行为修复。
- 先前本机测试与 Linux 静态检查通过的事实只属于当时工作区，不能用来证明未来重构通过。

## 剩余风险

最终 exec 和回收链具有平台/线程敏感性；本次以真实 Ubuntu/Linux 回归验证指定候选，不据此保证未经测试的平台或未来改动。
helper 架构是否另行重选、哪些候选规则值得提升仍由人判断。

## 结论

用户指出 B0～B4 缺少结构后，本轮已按所有权和生命周期完成 R0～R7 本地重构及检查。TASK-105/106 的技术实施完成；R8 独立阅读与完整 Linux CI 均通过，技术结论 result=pass；整体验收仍由用户签署。


## 2026-09-13 签署核对与计划交付

- scripts/work board WORK-049：意图闸 passed；WORK-048/050 的 board 均为 verified、两道闸通过，TASK-113 done。本轮没有执行 gate 或改写人工签署。
- git rev-parse HEAD：8fe6e413a4cc0291204c298705403462ac4ed0d4；git status --porcelain 针对 apps/judge-engine、deploy/sandbox-linux、contracts 和 .github/workflows/ci.yml 无输出。
- 调用现有 report.harness_sha() 及计算 cases.json SHA256：分别与 PLAN-033 中的 d17e8e55…563f / 5be0031c…f83d 一致。cases.json 实际含 93 项。
- 读取当前 ProbeEnvironment/probeDeployment，确认 Linux 已按部署清单分支探测，纠正旧观察；未重新执行业务回归。
- 将四个 TASK 细化为 B0～B8，补充固定测试入口、harness 变化约束、资源与数字盘点起始项；TASK 仍 todo。
- scripts/work check：485 份文档通过，保留既有 WORK-033 状态推导提示。scripts/work refresh WORK-049：状态仍 todo。修改文档时产生的记录状态不同步已恢复原 checked 状态并经工具校验，不改变已签署的目标和决策。
- 文档计划完成不代表 TASK-104 的逐项盘点完成，也不代表重构或 CI 已重新通过。源码与测试、部署、全局规范均未修改；没有提交、推送、新 CI 运行或远端状态操作。


## 2026-09-13 B0 实施交接

用户已授权开始第一轮。再次核对代码路径无修改，HEAD、harness/cases 与 PLAN-033 一致；WORK-048/050 已验收的重叠实现作为实际起点。52 个 requiredGoTests 均在原包存在；93 项用例未修改。精确映射、资源失败路径与数字依据见 DESIGN-043 B0 附录。此项只证明盘点完成，不替代重构测试。


## 2026-09-13 B1 代码与验证

### 修改范围与数值比对

起点为 B0 的 8fe6e413a4cc0291204c298705403462ac4ed0d4 干净源码，终点为本轮未提交工作区。
仅修改 sandbox/helper、sandbox/launcher，新增模块 README 和本工作记录；没有提交、推送或远端动作。
没有新增包或服务，也没有改变 Config/Request/Result/Event 的字段、JSON 名称、错误正文及默认行为。

| 原表达 | 当前定义/位置 | 对照结果 |
|---|---|---|
| init FD3/4/5；exec FD3/4/5；ExtraFiles位置0/1/2 | launcher/startup_protocol.go 的 InitControlFD/InitInputFD/InitLivenessFD、ExecConfigFD/ExecReadyFD/ExecErrorFD、ExtraFilesBaseFD | 发送端用具名数组位置，接收端用相同定义；角色和数量不变 |
| R/G；固定 ready JSON Version1 | PayloadReady/PayloadGo、initReadyMessage | 字节不变；最终 exec 没有新增调用或分配路径，固定 JSON 未改成运行时编码 |
| 阶段1～7；记录8字节，stage0、errno4；退出125 | execFailureStage 的七个显式常量、execFailureRecordBytes/StageOffset/ErrnoOffset、launcherFailureExitCode | 原阶段数字不重排；保留零 padding、小端 errno、错误退出协议 |
| capability64；无效身份2^32−1 | capabilityABIBits、invalidLinuxID | ABI 值和检查顺序不变 |
| argv/env/输入/输出256/128/128/128、字符串32KiB、深度8、帧头4 | launcher/protocol.go 按请求对象命名；MaxOutputs 供响应端复用 | 接受/拒绝条件、累计方式不变；正则127后续字符保留并注释 |
| CPU60s/wall120s/memory1GiB/process256/stdout与stderr1MiB | maxCPUNs/maxClockNs/maxMemoryBytes/maxProcesses/maxStdoutBytes/maxStderrBytes | 纳秒值不变；不与 runner 默认值合并 |
| ExecSpec 字符串64KiB、NOFILE8～1024、FSIZE1GiB | exec.go 的 maxExecStringBytes/minExecNoFile/maxExecNoFile/maxExecFileSizeBytes | 与实际 payloadNoFile256/payloadFileSizeBytes64MiB 分开 |
| workspace128MiB/4096inodes | init 的 maxWorkspaceBytes/maxWorkspaceInodes | 验证硬上界与 helper 实际策略仍分别维护 |
| Event1024bytes、辅助缓冲4个int32 FD；事件接收4次 | channel 的 maxEventBytes/receivedFDCapacity/rightsFDBytes；helper 的 maxInitEvents | 协议仍最多接收一个 FD；异常关闭、EINTR、pin FD 和 drain 逻辑未改 |
| 结果4MiB、完成帧1024bytes、会话150s | helper/protocol.go 的 maxResultFrameBytes/maxCompletionFrameBytes/sessionTimeout | 双方共享，但计时起点与服务端后续写期限刷新不变 |
| 拨号3s、请求头5s、交付10s、恢复5s、安装探测5s | dialTimeout/requestHeaderTimeout/deliveryTimeout/recoveryTimeout/installationProbeTimeout | 各自原调用点设置，不因同值合并；安装探测的CPU2s/wall5s仅换成带单位表达 |

原值逐项对照源码 diff；terminalFailure 的具名数组索引仍产生 stage、三个零、四个 errno 字节。
LockOSThread、policy.Install、RawSyscall 和 KeepAlive 的调用及顺序保留；没有重排握手/释放、defer 或 goroutine。
README 给出当前真实阅读路径，不声称 prepare 等后续待重构入口已经改名。

### 已执行检查

- `gofmt -l apps/judge-engine` 无输出，`git diff --check` 通过。
- 在 apps/judge-engine：`GOCACHE=/tmp/cherry-oj-work049-go-build go vet ./...` 通过。
- 同目录：`GOCACHE=/tmp/cherry-oj-work049-go-build CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./...` 通过。
- 同目录：`go test -race ./...` 通过；包括 launcher/helper/container/pool/runner 等包，未更名或削减原有测试。
- 新增 `launcher.TestInitReadyMessageMatchesEventProtocol`：固定 ready 消息严格解码到 Event 后核对版本及全部字段，防止绕过动态编码的消息与协议漂移。原有协议/关闭/取消断言未改。
- 冻结提交与当前工作区分别 `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ... ./cmd/sandbox-helper` 均成功；当前 `go test -c ... ./tests/sandbox-linux/boundary` 成功。二进制只输出到 /tmp，不执行 Linux payload。
- 默认缓存路径首次 vet 被本机沙箱拒绝写入，切换可写 GOCACHE 后成功；race 使用获自动批准的本地测试权限，未修改测试绕开 socket。
- 尝试用 `go tool objdump` 比较最终 exec 指令，但本机 Go 工具链不含 objdump，未取得反汇编比较结论；不能将其记为通过。等价性依据为本批源码逐项数值/调用顺序核对与上述检查。

### 未执行与后续

完整 93 项 Linux/native/business CI 未在本轮运行；Linux 交叉编译不提供内核隔离、真实启动及回收的运行证据。
B2～B8 与独立源码阅读复核未执行，TASK-105 仍 doing、VERIFY 仍 pending。人验收闸未改。
本批撤销只涉及上述 sandbox 文件与新增 README/协议测试，保留 B0 前未提交文档、签署和 server 数据 ZIP；不要整目录恢复。


### 最终工作区核对

- harness 摘要仍为 d17e8e55a889d410839c6dab9a78fe610a7ce0405d98a28283794f52243d563f，cases 摘要仍为 5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d；93 项和 52 个固定测试入口再次核对通过，README 相对源码链接全部存在。
- 与 B0 快照逐段比较，WORK-050 原有 diff、CHANGE-013/DECISION-027 的签署相关 diff 完全一致；未覆盖其他人的增量。
- 本批源码补丁（包含4个新增文件）暂存于 `/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-b0-09w9m2si/b1-source.patch`，SHA256 为 `a833a42997fd6e755b2999e033cb4f2a46c55b82bfb4c809bf5ee6a980556d0f`。它只标识本批未提交源码，不是可替代 sourceSha 的 CI 身份。
- `scripts/work refresh WORK-049` 被现有流程约束拒绝：`doing 状态之前仍有未完成流程阶段：开发任务`。未绕过工具或将后续未执行 TASK 伪造为完成；WORK 汇总仍显示 todo，实际 TASK-104=done、TASK-105=doing 可由 board 查看。该汇总状态问题不影响本批代码检查，留待工作流范围处理。
- `scripts/work check`：485 份文档通过；保留既有 WORK-033 提示。


## 2026-09-14 /run 链路注释专项

### 范围与交付

用户明确要求按所给注释规范补齐执行链路。以本轮开始的未提交 B1 源码为依据，调整39个 Go 文件，覆盖两个 cmd 入口、api/pool/runner/container/helper/launcher 及 store/cgroup/policy 的相关资源与安全逻辑。
既有注释已充分说明约束的文件、简单转发和不支持平台的拒绝分支经检查保留；没有为每个函数机械补注释，也没有改动测试。
B2～B4 的函数拆分与重命名未在本轮实施，TASK-105 保持 doing。本轮规范应用于这些源码注释，未改全局工程规范。

| 位置 | 本轮补充或修正的约束 |
|---|---|
| cmd/sandbox、api、Pool | 服务级依赖释放顺序、HTTP与命令预算差别、Add/Wait与关闭互斥、Container关闭前不可发布成功 |
| Runner、Container、Store | 显式零限额、输入关闭失败回滚、溢出取消与请求取消区分、done发布结果、短文件/额外字节、reader持有期间的容量归还 |
| helper 启动/监督/回收 | 原子入组、并发I/O与阻塞解除、feedDone和feedFinished的区别、ready与超时同时到达时复查预算、最终OOM事实与回收错误分别处理 |
| init/exec、rootfs、FD控制通道 | 锁定OS线程、双向放行、固定失败记录、CLOEXEC与FD所有权、EINTR后重新固定句柄、目录FD打开产物、挂载传播和共享tmpfs预算 |
| cgroup/policy/信任与恢复 | 计量单位/快照边界、缺项不可当零、清空确认后删除、未知资源不清理、清单完整性、ENOSYS兼容规则的移除条件 |

修正了 RunExecStage 注释把握手写成上游已完成的问题；该函数实际在权限/过滤之后参与握手。
修正 stdin 注释：实际先创建命名文件，再只读重开并 unlink，随后才交付 payload。
删除了逐行翻译、API示范式教学噪声、只有旧TASK编号的说明，并把实际状态边界写清。
前轮重复的“历史推导待确认”从源码注释中移除；未知选值依据继续保存在 DESIGN-043，未替换为编造的性能或安全理由。
逐条复核新增注释是否提供代码外的信息、是否有源码支持、是否说明必要约束；临时/兼容行为给出可以改变或移除的前提。没有新增 TODO/FIXME 或注释掉的旧代码。

### 验证

- 本轮起点快照：`/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-comments-h276t26z`；比较的是实际工作区，不是只有旧 HEAD 的源码。
- 用标准库 go/scanner 对照全部78个 Go 文件（含平台文件和测试），非注释 token、字面量、自动分号完全一致；逐行核对 go:、build、line 指令一致。39个文件仅注释/格式变化，其余逐字节不变。
- `gofmt -l apps/judge-engine` 无输出；`git diff --check` 通过。
- 模块内 `GOCACHE=/tmp/cherry-oj-work049-go-build go vet ./...` 通过。
- 模块内 `GOCACHE=/tmp/cherry-oj-work049-go-build CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./...` 通过。
- 模块内 `go test -race ./...` 通过；使用既有测试，没有给注释编写镜像测试。
- 本轮注释 diff 暂存于 `/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-comments-h276t26z/comments.patch`，SHA256 `b988f708ec837c7a3ee289bdfe354ceb42b7cdcfd1b014380607c40ad325b933`；该值只用于区分本轮增量，不是 CI sourceSha。

### 与其他工作的隔离

进入本轮前 CI/workflow、AGENTS/CLAUDE 和工程文档已有其他工作区改动；本轮逐段比较其 diff，全部保留。
HEAD仍为 `8fe6e413a4cc0291204c298705403462ac4ed0d4`；本轮起止 harness 为 `3da9e679940de8114d61f1f4138a52b996f1272f3806e11925ed8a31c4eecab2`，cases为 `5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d`，均未被注释修改影响。
当前 harness 已不同于9月13日 B0/B1 冻结的 d17e8e55…563f，原因是本轮开始前已有其他 CI 改动；不混用这两份身份声称回归通过。
本轮没有运行完整远端93项 CI，没有提交/推送/修改人工闸，也没有把注释完善登记成 B2～B4 重构完成。


## 2026-09-14 B2～B4 重新重构

### 实际改动与源码入口

| 原位置/符号 | 当前职责与入口 |
|---|---|
| runner.go 混合校验、输入、执行、产物 | runner.go 保留 Run 总编排；request.go 校验/预算/输出声明，input.go 准备和关闭输入，result.go 映射事实，output.go 收集与显式回滚 |
| runner.Run、Pool.Run 的结果修改 defer | Run 主流程显式关闭输入/容器，失败撤销产物；OnceValue 和 defer 只兜底释放，返回值为普通值 |
| container/isolated.go 中的暂存和文件接收 | isolated.go 保留 Start → execute → Wait → Close；isolated_files.go 包含 PutFile、prepareRequest、receiveOutput、GetFile、spool 与输入流所有权 |
| helper.Call 的长接收和收尾 defer | Call 显式 uploadInput → receiveResult → awaitCompletion → finishUpload → 关闭；同文件下方可读各阶段 |
| helper.execute/prepare/monitor/handleEvent | execute_linux_amd64.go 总编排；launch_linux_amd64.go 的 launch 返回启动错误；supervise_linux_amd64.go 的 supervise/handleInitEvent 决定停止条件 |
| helper.execution.go 的状态和清理混杂 | execution.go 保留状态；cleanup.go 的 finish → waitForInitAndIO → collectOutputs → releaseEnvironment 处理完整回收 |
| helper.helper.go 的多种无关职责 | 删除该文件；Config/Validate → config.go，Result/Completion/Reason/Output → protocol.go，boundedCapture → capture.go，包注释 → doc.go |
| server_linux_amd64.go 的安装检查 | checkInstallation/probeInstallation → installation_linux_amd64.go；请求处理留在 server 文件 |
| launcher/init_linux_amd64.go | Dispatch → dispatch_linux_amd64.go；startPayload/payloadSpec/releasePayload/readHandshake → payload_linux_amd64.go；PID 1 编排、等待退出、回收后代留在 init 文件 |
| launcher/protocol.go 的可信阶段数据 | StageSpec/Event → startup_protocol.go；客户端 Request/校验/帧协议留在 protocol.go |

没有增加进程、包层级或转发壳。initStopped 表示尚未启动 init 或已确认它退出，initReportLost 表示未取得退出报告；initExited、inputCopied、inputFinished、stdoutDone/stderrDone 标明各通道等待的事实。旧字段引用已同步到原有白盒测试，测试名称与断言不变。

### 数值与语义核对

- runner 的默认 CPU/墙钟由 1e9/5e9 改为具名的 time.Second/5*time.Second 纳秒值；默认内存 128 MiB、进程 64、stdout/stderr 64 KiB 不变。文件数上界 128、服务输出硬界 64/16 MiB 原值具名。
- Pool 的 256 并发/1024 排队上界、执行目录随机 ID 的 16 字节原值具名；0700 写为 0o700。没有猜测固定数值的历史依据。
- helper 建组/采样/启动/墙钟计时起点、原子 UseCgroupFD 入组、启动前接输出管道、父进程关闭子端、输入复制与连接断开监测顺序不变。
- 正常、超时、取消都回到同一回收入口；OOM 归因和回收错误优先级未改。客户端取消不承诺已等到远端 Completion，此限制已同步阅读说明。
- 用 go/parser、go/printer、go/scanner 对本轮前后按函数比较非注释 token，归一化明确的私有字段/方法改名后，变更仅限 runner.Run/defaultLimits/collect、Pool.New/Run、isolated.Start/execute、helper.Call/execute，以及 prepare 的拆分和新增阶段函数。launcher 最终 exec、权限/过滤、握手、PID 1 函数执行语句一致；纯移动函数保持一致。该检查不是行为等价的形式化证明。

### 已执行检查与修正

- `gofmt -l .` 无输出；`git diff --check` 通过。
- 模块目录 `GOCACHE=/tmp/cherry-oj-work049-go-build go vet ./...` 通过。
- 模块目录 `GOCACHE=/tmp/cherry-oj-work049-go-build CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./...` 通过。
- `go test -race ./...` 最终通过。新增 TestRunClosesInputOnEveryExitPath 覆盖正常、Start/Wait 失败、墙钟超时、请求取消及 panic；TestInputCloseFailureRevokesArtifactsAndPreservesErrors 验证关闭失败回滚及保留双重错误；TestRunHoldsResultAndCapacityUntilContainerCloses 用阻塞关闭验证容量与结果持有、一次关闭、失败停池及引用删除。
- 第一次全模块运行发现新测试的嵌入替身 Start 返回了内层 controlled，未调用预期 Wait；为替身显式实现 Start 返回自身后复跑通过，没有修改生产逻辑来迎合测试。
- 初次不带本地测试权限运行受限于 Unix socket bind；使用自动批准的测试权限后通过，未跳过相关测试。
- Linux/amd64、CGO_ENABLED=0 构建 helper 到 `/tmp/work049-rework-sandbox-helper`，交叉编译 boundary 到 `/tmp/work049-rework-boundary.test`，均通过。没有在 macOS 上执行 Linux 测试二进制。

### 基线、限制与交接

本轮起点为实际工作区快照 `/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-rework-0icvlxn8`，含前两轮未提交源码、meta.json 和 before.patch。HEAD 为 e1f6f11e4981d3159747a197fa270c7610e2a28e；harness 为 3da9e679940de8114d61f1f4138a52b996f1272f3806e11925ed8a31c4eecab2；cases 为 5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d。

完整 93 项 Linux/native/business CI 与独立源码阅读审查没有执行，不能把本地检查当成人工验收。本轮仅完成 B2～B4 本地实现，不启动 TASK-106/107、不改人工签署、CI、部署或依赖。Task105 保留 doing，最后的平台不变量验收项待完整回归确认。撤销只针对本轮快照后的 sandbox 增量，不能整目录还原或覆盖前轮源码、WORK-050 文档与 server 数据删除。

### 本轮最终核对

- 起止 HEAD/harness/cases 三项一致，冻结的 93 项及 52 个 requiredGoTests 函数全部保留，未改测试选择器。
- 相对实际起点，本轮调整 31 个 Go 文件（含移动、删除、新增及测试）。增量补丁为上述快照目录中的 `rework-source.patch`，SHA256 `2b440c2ec0c2c578f69d87afa3333016a46a51822479fbfb7c0ff7c4e43f8231`；该摘要不是 CI sourceSha。
- 逐段比较本轮前后的 git diff，任务范围外的既有源码/文档、CHANGE/DECISION 人工签署和 server 数据删除均保留。模块 README 的全部源码链接存在。
- `scripts/work check`：485 份文档通过，只有既有 WORK-033 推导提示。未重试之前已确认的 WORK-049 refresh 汇总问题，也未为推进状态伪造后续任务完成。


## 2026-09-14 对象模型与后续计划修订

本轮用户只要求确定后续重构计划。DESIGN-043 的当前方案改为 service、execution、isolationPlan、isolatedProcess、执行产物及 P4 文件系统责任，PLAN-033 改为 R0～R8；TASK-105/106/107 与入口待确认项同步。旧 B0～B4 记录保留为历史，旧未执行的 B5～B8 已由 R6～R8 承接。

本轮未实现新对象，未改 Go 或模块 README，未重跑业务测试、提交、推送、部署或签闸。B1～B4 的历史绿色结果不能验证未来对象重构。新检查包括唯一 owner、状态前置条件、部分启动清理、策略快照隔离、产物单次移交和 P3/P4/P5 各自的隔离动作；全部仍待实施验证。

本轮文档起点保存于 `/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-object-plan-33qbx6eb`，含原 WORK-049 文档、模块源码 SHA256 清单与 before.patch；HEAD 为 e1f6f11e4981d3159747a197fa270c7610e2a28e。交付前校验文档关联和模块源码不变，实际结果附后。

交付检查：`scripts/work check` 的 485 份文档通过，只有既有 WORK-033 状态推导提示；`git diff --check` 通过，本轮涉及文档的相对链接均存在。起点清单中 150 项源码、依赖、模块 README 和人工签署文件的摘要一致，无新增 Go 文件，HEAD 不变；逐段比较前后 diff，本轮仅改动 WORK-049 的入口、设计、计划、TASK-105/106/107 和本验证记录，其他既有增量保留。


## 新对象实施：R0～R5 本地验证

2026-09-14 用户明确开始实施。起点为 work049-objects-0mpop4ya。已完成沙箱对象和所有权迁移；`GOCACHE=/tmp/work049-go-cache go test -race ./internal/sandbox/...`、对应 macOS vet、`CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./internal/sandbox/...` 与 helper 构建通过。新增对象生命周期测试覆盖请求快照、并发/重复 Run、ready 后预算复查、产物单次移交、启动/关闭错误、重复等待和重复关闭错误保留。原有测试名与断言保留，完整 Linux 和独立阅读待 TASK-107。


## R0～R7 最终本地交付与 R8 交接

本轮不以历史 B1～B4 的绿色结果验证新对象。实际工作目录为 apps/judge-engine，Go 1.26.3，macOS；所有 Go 命令使用 `GOCACHE=/tmp/work049-go-cache`。默认沙箱禁止本机 socket bind，含监听测试通过工具授权在沙箱外运行，未修改测试绕过权限错误。

| 检查 | 实际结果 |
|---|---|
| `gofmt -l .` | 无输出 |
| `go vet ./...` | 通过 |
| `go test -race ./...` | 全模块通过；原测试入口保留，新增 9 项对象/字段映射测试 |
| `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./...` | 通过 |
| 同 Linux 环境变量 `go build -o /tmp/work049-sandbox-helper ./cmd/sandbox-helper` | 通过 |
| 同 Linux 环境变量 `go test -c -o /tmp/work049-boundary.test ./tests/sandbox-linux/boundary` | 通过，仅交叉编译 |
| 同 Linux 环境变量 `go test -c -o /tmp/work049-helper.test ./internal/sandbox/helper` | 通过，仅交叉编译 |
| 最终 exec、P5 启动与权限、本机协议、依赖、environment/deployment、人签文件摘要 | 对照 R0 快照全部不变 |
| rootfs 的 Mount、Sethostname、Chmod、Mknod/Mkdev、PivotRoot、Unmount | 将接收者引用归一化后，参数和调用顺序与起点一致 |
| 固定清单 | 93 项 cases 与 52 个 requiredGoTests 入口保留 |
| 完整 Linux/kernel/native/business CI | 未执行；本机编译不作为运行证据 |
| 独立源码阅读 | 未执行；本轮自查不能替代未参与实现者的判断 |

HEAD 仍为 `e1f6f11e4981d3159747a197fa270c7610e2a28e`，代码尚未提交，不能将 HEAD 冒充新候选 sourceSha。harness 为 `3da9e679940de8114d61f1f4138a52b996f1272f3806e11925ed8a31c4eecab2`，cases 为 `5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d`，均与起点一致。本轮没有 runId/runAttempt，不能填入历史 CI 身份。

本轮模块增量共 36 个文件，包含新增/删除/测试/README；精确补丁为 R0 快照目录中的 `objects-source.patch`，SHA256 `b1c974b07c9e144d95a0d6a9270ebe97d035921068ba7780c7e947b6433a8d6f`。它只描述本轮增量，不是提交摘要；回退依据快照和该补丁，只撤销本轮对象重构。此前未提交改动、WORK-050 和 server 数据删除保留。

### 结构自查的具体入口

1. `helper.service.serveConn` 创建执行并调用 `execution.Run`，随后交付产物；无单次执行 FD/原始任务通道。
2. `isolatedProcess.startInit` 设置 namespace 与 UseCgroupFD；group 由 execution 建立；P4 `rootFilesystem.Prepare` 做挂载；P5 最终 exec 保持原代码。
3. 进程对象判断 workspace/ready/GO/exit 合法顺序；execution 在 ready 后重新检查预算才放行，不能凭共享布尔字段猜状态。
4. `process.Wait` 返回停止和捕获事实；`TakeWorkspace` 只移交一次；`execution.takeResult` 清除对 artifactSet 的引用。关闭 execution 不关闭已移交产物，已有测试覆盖。
5. 正常、墙钟、取消、启动失败进入 `finish`；OOM 归因、独立回收错误与节点停服语义单独保留；原故障测试已迁移。
6. `Run` 完成只说明 P3 执行结束；`WriteFiles/Close` 后才 Completion，归还槽位后 EOF；客户端取消返回仍不等于远端停止。
7. 三个常驻服务、每条命令 P4/P5 两个临时进程保持；execve 替换 P5 自身，后台 goroutine 不计入进程数量。

### 候选规范处置建议

C-01/02/03/07/09/10/11 暂保留为候选：本轮实际对象与测试提供了对应落点，但可读性仍待独立阅读。C-04 调整为优先按资源所有者和共同职责划分文件，避免再次把每个执行阶段机械拆成独立文件。C-05/06/08 保留数字、协议与注释的原规则；无依据的选值原因继续标为未知，不编造解释。所有候选仍未提升到全局规范。


## R8 独立阅读验收（2026-09-14）

审查者为未参与本轮实现的独立代理 `/root/r8_reading`。只读源码和 WORK-049 依据，未改代码、运行测试或签署人工验收。审查对象与候选 a611be3d427fdfa03b724df0c1235148f897f648 的模块内容一致；以 R0 实际快照对照。结论：七项结构问题通过，无须退回 TASK-105/106 的阅读断点，未确认 R0→R7 引入的具体回归。此结论不代替 Linux 测试和人工验收。

| 结构问题 | 独立追读证据与结论 |
|---|---|
| 接单到交付的对象协作 | helper/server_linux_amd64.go:172 的 serveConn 只接单、调用 execution.Run 并交付；execution.go:88 明确建组→Start→supervise→finish；通过 |
| namespace/cgroup/mount 的执行者 | isolation_plan.go:21 保存快照；process_linux_amd64.go:81 在 P3 创建 P4 时设置 Cloneflags/UseCgroupFD；launcher/rootfs_linux_amd64.go:29 在 P4 挂载/pivot_root/加载输入；通过 |
| service 是否依赖执行内部状态 | 不接触 init 管道、inputFinished、initStopped、工作区 FD；执行回收在 cleanup.go:15；通过 |
| 唯一所有权与移交 | process_cleanup.go:10 等待任务；process.go:182 仅在等待成功后 TakeWorkspace；execution.go:135 takeResult 清空原引用；artifactSet 保留第一次关闭失败；P4 TakeInputs 同样清除已移交引用；通过 |
| 失败归因与接单策略 | cleanup.go:22 取消输入后使用独立清理期限；本任务 OOM 不能遮蔽祖先 OOM 或独立清理失败；cgroup/cgroup.go:334 和 service 保留停服策略；通过源码比对 |
| 完成边界 | Result→文件→产物 Close→Completion→槽位归还→EOF；server_linux_amd64.go:145 与 client.go:117 对应；取消返回不承诺远端回收；通过 |
| P4/P5/execve | payload_linux_amd64.go:18 创建 P5；READY→P4 ready→P3 预算复查→GO；exec_linux_amd64.go:17 替换 P5 自身；三个常驻服务、两个临时进程，用户后代另计；通过 |

四条路径均从 P1 judgment 到 P2 /run、P3 execution、P4 init、P5 最终执行实际追读：

- 正常：编译或跳过编译→逐点执行→P4 报告退出→P3 停组/等 init 与 I/O/收集产物/释放环境→交付→P2 本地关闭；judgment.close 最后释放编译和源码引用。
- 超时：墙钟/CPU 原因进入同一 finish；ready 到达还会复查预算；runner 映射 TLE，不发布成功产物。
- 取消：P2 断连并等待本地上传；P3 监督退出但独立清理继续，槽位保留至收尾；不能把客户端返回当远端完成。
- 部分启动失败：FD 分配即登记；cmd.Start 前失败也 Stop/Wait/Close；已启动则等待进程和后台任务，只有确认整组与 init 停止才删除宿主空挂载目录。

审查对照了 FD 布局、namespace/入组、挂载/输入顺序、监督分支、OOM/清理优先级、尾帧和 flow 引用清理。无阻塞卡点或确认的新缺陷。最终 exec/payload/启动协议及 P2 client/runner/Pool 相对 R0 未改变。

### R8 本地预检与候选准备

完整重构相对仓库 HEAD 涉及 82 个模块文件，独立临时 clone 仅提交这些文件，未夹带 WORK-050 文档和 server 数据删除。原 main 工作区不切分支、不提交、不清理。

首次 basic 预检因受限环境禁止 127.0.0.1 监听，rootfs TLS 测试四项报 PermissionError；失败日志保留于 R8 快照的 basic-preflight。允许本地监听后，在新输出目录 /tmp/work049-r8-basic-allowed 重跑同一 basic.py 成功，未修改断言或跳过测试。这是 macOS 预检，不能计作 Linux 实机证据。最终 Linux 运行身份及结果随后附录。

最终候选已经由原提交钩子完成 gofmt/vet 及 `go test -race -count=1 ./...`，成功推送独立验证分支。现有 `ci.yml` 以 workflow_dispatch 运行：runId=34830811953、attempt=1、sourceSha=a611be3d427fdfa03b724df0c1235148f897f648。未改 workflow、未创建 PR、未合并 main。当前模块 185 个实际文件与候选逐字节一致，harness/cases 摘要与 R0 保持一致。


### R8 候选编码规则逐项处置

以下是本工作经独立阅读后的建议；是否提升为跨工作规范仍由人决定，本轮未改 docs/engineering 或 docs/coding-standards。

| 候选 | 建议 | 本轮依据与适用边界 |
|---|---|---|
| C-01 生命周期主流程 | 保留候选 | Run 可顺读建组、启动、监督、收尾；不要求纯算法套阶段模板 |
| C-02 交接入口与完成条件 | 保留候选 | P3/P4/P5 及 Completion/EOF 可分别定位；直接调用无需重复解释 |
| C-03 名称表达返回进度 | 保留候选 | Start 表示仍在途、Wait 表示本地完成、Take 表示移交，避免把开始当结束 |
| C-04 文件职责 | 按资源所有者调整后保留 | 独立审查可从 owner 找到状态与释放；不推广机械按步骤拆文件或限制行数 |
| C-05 数字用途与依据 | 保留候选 | 用途、单位与原值可对照；历史选值无依据的继续标未知，不编造原因 |
| C-06 协议共享与同值区分 | 保留候选 | 两端 FD/握手值同源；默认、上界和实际策略仍各归其主 |
| C-07 资源完成条件 | 保留候选 | 正常与三种异常路径可找到收尾者；defer 可继续用于异常兜底 |
| C-08 Why 注释 | 保留候选 | 并发、线程、移交约束有代码依据；不把每个方法都加注释作为验收要求 |
| C-09 上下文生命周期 | 保留候选 | judgment、installation、execution 是单次工作对象；不扩成全局可变 Context |
| C-10 等价回归 | 保留候选 | 固定候选 SHA、harness、93 项及清理证据约束本次结论；以后按变更风险选择验证 |
| C-11 唯一 owner 与合法操作 | 保留候选 | TakeWorkspace/takeResult/TakeInputs 清除移交引用；不强制纯计算对象化 |

以上通过的是本工作中的适用性复核；不把单次案例证明扩展为长期通用规范已经获批。


## R8 最终 Linux 实机证据（2026-09-14）

[完整 CI 运行与 artifacts](https://github.com/charon2121/cherry-oj/actions/runs/34830811953) 已成功完成；11 个前置必需 job 加 sandbox-summary 共 12 个 job 全部 success。只运行一次完整 workflow，未混用历史或其它 attempt 的报告，没有通过修改 harness 或跳过必需测试取得绿色结果。

| 身份 | 实际值 |
|---|---|
| 分支 | codex/work-049-r8-3w1rszph |
| sourceSha | a611be3d427fdfa03b724df0c1235148f897f648 |
| harnessSha | 3da9e679940de8114d61f1f4138a52b996f1272f3806e11925ed8a31c4eecab2 |
| cases SHA256 | 5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d |
| runId / runAttempt | 34830811953 / 1 |
| 执行环境 | GitHub Ubuntu 24.04.5 LTS VM，x86_64，Linux 6.17.0-1022-azure，runner image 20260907.300.1 |

| 必需套件 | 结果 | 清理 | report.json SHA256 |
|---|---|---|---|
| basic | 5/5 PASS | PASS | d0cdeabcfa14623e9a3e8014d67475cebd3ce2b59afae549e315306bca0f4f37 |
| kernel | 63/63 PASS | PASS | 36434ed691e3ba859a58ec84189f33b02af378f2ac74c7e39bc586ab48c710dd |
| native | 10/10 PASS | PASS | 56529d9d313d1b15f4aa0ffdf259d6a834307a52d91852f06f870ec180cbc474 |
| business | 15/15 PASS | PASS | e5d5a27e471410e0a0e7ccc63dfbb2736efcee01e39a2c683c9ac640aafad4f4 |

关键证据包括 namespace、只读挂载、静态与链路 CPU/OOM、1000 次执行、并发、故障回收、任务与聚合 OOM、原生安装与服务恢复，以及真实页面的新环境、部署、校准、I/O、CE/RE/signal/CPU/memory/output/empty/AC/WA/history/cleanup。kernel 的 go-linux.log 逐 JSON 事件核对 52 个 requiredGoTests 全部 pass，没有 fail/skip。

已下载四套报告与 summary 到 `/tmp/work049-r8-evidence`，在候选 clone 内调用原有 `summary.summarize`、`report.validate`、`report.verify_files` 复验：检查报告 schema、实际证据文件、全部用例、harness、sourceSha、runId、attempt 和 cleanup；重算的 summary 与 CI 上传文件完全一致且 PASS。另核对 GitHub 12 个 job 的真实结论，未仅信任报告自述。kernel/native/business 的 resources-after 均无 tasks/mounts/cgroups；原生与业务卸载后 paths/accounts/groups 同样为空。

当前本机 main 仍停留于 e1f6f11e4981d3159747a197fa270c7610e2a28e，模块工作区内容与候选提交一致。独立分支只是 CI 验证候选，未合并 main、未创建 PR，也未在开发宿主部署。WORK-049 文档证据本轮本地更新，未夹带进源码候选。验收 artifacts 的远端保留期为 14 天，持久结论、身份、摘要和链接记录于本节。

## 变更记录

- 2026-09-14：状态变更：draft → review。原因：独立阅读通过，最终候选完整 Linux 93/93 和全部必需检查通过，同批报告及清理复验通过；提交用户验收，未签闸

最终范围核对：与 R8 起点 before.patch 逐段比较，WORK-049 和工具生成 WORKS 之外的既有 tracked diff 完全不变；模块快照、main HEAD、CHANGE/DECISION 人签文件摘要一致。scripts/work check 的 485 份文档通过，仅保留既有 WORK-033 提示；git diff --check 通过。TASK-107=done，WORK-049=implemented，VERIFY-050=review/result=pass，人工 acceptance=pending。
- 2026-09-15：验收闸通过：review → approved。原因：功能通过验收
