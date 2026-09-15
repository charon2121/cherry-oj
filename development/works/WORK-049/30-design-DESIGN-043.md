---
id: "DESIGN-043"
type: "design"
title: "按命令执行顺序重构 Go 判题引擎源码"
status: "checked"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-005", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-14"
---

# DESIGN-043：以所有权和生命周期组织沙箱

## 背景

2026-09-14，用户确认以沙箱服务、一次执行、隔离配置、隔离进程、执行产物这五个概念制定后续计划。方案交付后，用户明确要求开始实施；R0～R7 已完成本地实现，R8 的完整 Linux 93/93 项与独立阅读七问均通过，证据见 VERIFY-050；人工验收闸待签。既有意图闸和 CHANGE-013、DECISION-027 的签署不改。

B0～B4 已完成的数字整理、函数拆分和本地检查保留为历史事实。用户仍无法从结构判断“谁拥有沙箱、谁可以推进状态、谁负责清理”，因此后续以对象职责和所有权为主、以阶段函数为对象内部实现。新计划见 PLAN-033 的 R0～R8；本设计中的目标对象已在 R0～R7 落地，实施证据见 VERIFY-050。

## 目标与限制

本次仍是 CHANGE-013#REQ-001～007 下的等价结构重构：保留三个常驻服务、每条正常启动命令的两个临时进程、Linux 后端和 trusted-host 的选择、现有协议与配置、预算、校验、错误归因、权限、rootfs 和回收语义。不添加任意宿主目录挂载、rootless 后端、对象池复用或新常驻服务，不改变判题业务。

## 整体方案

现有 Pool、cgroup.Manager/Group 和 Container 有局部职责，但 helper.execution 同时保存进程状态、原始 FD、goroutine 完成通道、协议阶段、故障、捕获缓冲和产物。调用者必须了解 launch/supervise/finish 的正确组合，阶段之间依赖可变字段约定。

目标是让一个类型能回答三件事：它拥有什么、允许调用哪些操作、操作返回时哪些事实已成立。没有独立状态或资源的纯校验、编码和数值计算继续使用函数；不为每个步骤创建接口或结构体。

### 对象与进程不是一一对应

| 进程 | 当前职责 | 目标结构中的归属 | 本次保持的事实 |
|---|---|---|---|
| P1 judge，常驻 | 编译、测例、比对、verdict | TASK-106 的每次判题对象 | HTTP 调用 P2；一次正常 C++ 判题执行一次编译再逐点运行 |
| P2 sandbox，常驻 | HTTP、容量、输入引用、最终 RunResult | 现有 api/Pool/runner；Container 为本地适配对象 | helper.Call 是 P2 内的客户端调用，不是新进程；Container.Start 只代表请求在途 |
| P3 helper，常驻 | 特权执行监督、服务槽位 | service、execution、isolationPlan、isolatedProcess、executionResult | 创建 cgroup；启动 P4 时创建 namespace 并原子入组；持有宿主侧进程句柄 |
| P4 helper --isolated-init，临时 | PID 1、挂载、启动和等待 P5 | launcher.initSession 及 rootFilesystem | 在已创建的 namespace 中布置挂载并 pivot_root；保持为 PID 1 |
| P5 helper --isolated-exec，临时 | 最终限制、握手、execve | ExecSpec 与 RunExecStage | 继承隔离环境，降权/过滤后 execve 成用户程序；execve 不增加一个进程 |

Go 对象不会跨进程共享指针。P3 的 isolatedProcess 是控制 P4 的宿主侧句柄，P4 的 initSession 是另一个地址空间里的对象，二者通过既有 StageSpec/Event/FD 协议协作。命令自身创建的后代另计；后台 goroutine 不计作进程。

## 模块与数据

### 核心对象与所有权

类型名是本修订确定的目标定位，实施沿用 Go 私有具体类型与消费方窄接口，不因表中的中文概念全部增加导出 API。

| 概念与目标类型 | 存活范围 / 拥有的内容 | 对外操作及完成含义 | 不应进入该对象的状态 |
|---|---|---|---|
| 沙箱服务：P2 的现有 Pool；P3 的 helper.service | 服务级配置、资源组管理器、监听和接纳槽位、停服状态 | P2 Run 最后确认本地 Container.Close；P3 serveConn 创建一次 execution、调用 Run、交付并关闭 executionResult，再归还槽位 | 单条命令的 FD、启动阶段、编译和 verdict |
| 一次执行：helper.execution | 单次计划、计时起点、本次 group、isolatedProcess、主终止原因、最终清理结论 | Run(ctx) 是唯一的完整执行入口：创建资源→启动→监督→停止与等待→形成结果。不能只得到退出码便宣称完成 | HTTP JSON、Store ref、原始管道端和逐字节协议解析 |
| 隔离配置：helper.isolationPlan | 一次执行的只读值快照；只有配置和经过校验的请求可生成 | 工厂生成并校验；派生 init 的 StageSpec 和启动参数。无 Start/Close，不持有资源 | FD、进程句柄、ctx、可变结果、客户端指定的 UID/挂载来源 |
| 隔离进程：helper.isolatedProcess | P3 持有的 init 子进程、控制/存活/输入管道、输入与捕获任务、握手阶段、接收的工作区 FD | 启动后提供 ready/exit/输出超限/平台失败等执行事件；受控放行；停止输入和有界等待进程及 I/O；工作区 FD 只能移交一次 | cgroup 创建/删除、最终 OOM 归因、HTTP 和产物发布规则 |
| 执行产物：沿用 helper.executionResult；内部用 artifactSet 聚合 FD | 执行回收后仍用于交付的产物句柄及不可变事实 | WriteFiles 消费固定文件集合；Close 幂等并保留错误。交付者持有结果期间，原 execution 不能再次关闭这些 FD | Store ref、重新按宿主路径打开文件、执行中的可变文件视图 |

artifactSet 是对当前 executionResult.files 与 collectOutputs 的收拢，只在该聚合确实承载打开、总量校验和关闭责任时建立；executionResult 继续作为交付边界，不另建一层只转发 WriteFiles/Close 的结果对象。

### execution 的主入口

service 只调用一次 execution.Run(ctx)，不由 service 逐步调用 Start、Supervise、Stop、Collect、Close 来记住顺序。构造阶段不取得内核资源；Run 内部取得资源后立即登记所有权，普通错误和取消均进入统一结束路径。

Run 返回两类信息，保持当前 execute 的约定：执行事实与普通命令/启动错误进入 executionResult；不能确认回收、建组失败等当前会停服的错误仍单独返回给 service。迁移前逐项核对原 fatal 分类，不把所有非 nil error 都升级成停服，也不把 fatal 当成普通执行失败。

execution.Close 用于异常兜底和重复关闭，保留清理错误；正常 Run 必须显式完成并检查清理，再交付结果。已移交给 executionResult 的产物不再归 execution.Close。异常兜底不能被描述为能覆盖 SIGKILL；现有 systemd 与进程断连兜底保持。

计时仍从原 newExecution 记录 started 的位置开始，包含建组和隔离启动开销。构造对象、转移实现或创建只读计划不得顺便将计时推迟到 ready 或用户 exec。

### isolatedProcess 与监督的分工

isolatedProcess 封装 cmd.Start/Wait、FD 布局、StageSpec 写入、Event 接收、流复制和捕获任务。execution 只消费执行事件和完成事实，读取本次 group 的快照并决定预算/终止原因；不访问原始 FD，也不直接管理输入或捕获任务的完成通道。

ready 事件只表示子进程准备好；execution 必须再次检查取消、墙钟和 CPU 后才能请求放行。isolatedProcess 校验协议顺序并发送 GO。原“ready 与超时同时就绪”的复查不能移除。

组级终止由 execution 调 group.Stop。之后 isolatedProcess 解除 I/O 阻塞、有界等待 init 和后台任务，再关闭不再使用的句柄。不能把 process.Close 的“句柄已关闭”解释为“整组后代已清空”，也不能只 cmd.Wait 而漏掉同组后代。

Linux 细节归具体实现。沿用已存在且确有消费方的 executionGroup 等窄接口；测试替身放在资源边界。openOutput、shutdownControl 等行为不再与每次执行的可变业务状态混装。R0 要列明每项失败注入如何落在新的资源边界，不能为了去掉回调削减故障测试。

## 隔离配置必须显露三种机制

isolationPlan 按 namespace、filesystem、resources、identity 四组组织，字段名携带单位和来源。值由受保护的部署 Config 与已经归一化的 launcher.Request 合成；外部 RunSpec 仍只接受原字段。拥有 slice/map 的快照必须复制，避免下一条请求修改前一条的安全策略。

| 机制 | 计划表达 | 实际执行者与时机 | 原规则 |
|---|---|---|---|
| namespace | 固定的 mount/PID/network/IPC/UTS/cgroup 隔离集合 | P3 创建 P4 时经 SysProcAttr.Cloneflags 生效 | 不增加 user namespace；不把 CLONE_NEWCGROUP 当成资源限额 |
| cgroup | 既有内存、进程数、CPU 使用速率及累计预算 | P3 先创建/configure Group，P4 启动时 UseCgroupFD 入组；P5 继承 | 禁止启动后补写入组；速率与累计 CPU 时间分别表达 |
| 文件系统 | 固定 rootfs 来源、launcher 来源、工作区容量和固定内部布局 | P4 在自己的 mount namespace 内执行 rootFilesystem.Prepare | 保持 private propagation、只读 bind、tmpfs、pivot_root、旧根卸载顺序 |
| 身份与执行限制 | init/payload 分离身份及既有 rlimit、seccomp profile | P4/P5 在当前原位置降权与过滤；P5 在 GO 后 execve | 不提升 HTTP 权限；最终 exec 线程保持锁定；过滤后不新增不允许的 Go 调用 |

本轮采用固定挂载布局：RootFS→新根只读，helper 可执行文件→/.sandbox/launcher 只读，tmpfs→/work，/work/.tmp→/tmp，新 procfs→/proc，最小 /dev。计划值只描述这些已有规则，不提供任意 source/target 的外部挂载扩展点。编译器和运行库继续来自预构建 rootfs，输入继续复制进工作区。

P4 的 rootFilesystem 封装一次文件系统准备和取得的局部句柄，initSession 保留 PID 1 的流程与错误阶段。构造时不 mount，Prepare 才产生副作用；局部 FD 有明确关闭者。namespace 挂载的最终销毁仍来自进程生命周期，P3 只在确认停止后删除自己的空宿主目录，不能给 rootFilesystem.Close 编造跨进程卸载能力。

## 接口与状态

状态只由所属对象推进。以下是内部语义，不增加 wire 字段；采用私有阶段类型替换分散判断，不能在旧布尔值之外再维护第二份权威状态。

| execution 状态 | 允许的动作 | 离开状态的条件 |
|---|---|---|
| new | Run 一次；无资源时 Close | Run 开始取得资源；已关闭后不允许 Run |
| starting | 内部启动；取消/错误进入 finishing | 收到有效 ready 且预算复查通过后放行；放行后才进入 running |
| running | 监督执行事件/资源预算；取消进入 finishing | 退出、超限、断连或平台失败 |
| finishing | 停组、等进程/I/O、读取最终事实、准备产物、释放环境 | 所有回收结果已记录；失败也不能跳过后续可执行的清理 |
| finished | 返回/读取最终结论；幂等 Close | 确认回收成功，产物所有权可移交 |
| cleanupFailed | 返回失败结论；service 按原规则停服/隔离容量 | 不恢复成成功，不接纳新执行；重试语义沿用原资源实现 |

命令执行失败与 cleanupFailed 分开：非零退出、超时等可以在回收成功后进入 finished，但不会发布成功执行结果。init 因本任务 OOM 丢失报告的归因、祖先 OOM 的平台归因和独立回收错误继续按原优先级合成，不能由阶段枚举覆盖。

每个 execution 只允许一个 Run 调用者；明确拒绝重复/并发 Run。服务停服通过取消执行 ctx 等待在途 Run，不并发调用对象内部任意方法。输入/FD 的阻塞解除和一次关闭仍允许按原需求并发；幂等仅表示不重复释放，不能吞掉第一次失败。

### 资源移交与最终发布

| 资源 | 初始所有者 | 移交/释放边界 |
|---|---|---|
| P2 接纳名额、本地工作区、Store ref | Pool / Container / runner | 输入与 Container 关闭失败按原逻辑撤销 ref；关闭前不归还本地执行容量 |
| P3 helper 槽位 | service | execution 回收、交付/产物关闭、fatal 处理结束后归还，再关闭连接 |
| 本次 cgroup | execution（Manager 管理目录） | 先 Stop 取得最终计量，再 Close；不可靠回收按原规则隔离 Manager/service |
| init、控制/存活 FD、输入和捕获任务 | isolatedProcess | 部分启动也登记；终止后等待全部任务再关闭，未消费消息中的 FD 也要接管 |
| 新根挂载与 P4 局部文件 | initSession/rootFilesystem | 只在 P4 操作，进程退出及整组回收结束使用；不能从 P3 回调假装访问 P4 对象 |
| 工作区目录 FD | P4 发送；P3 isolatedProcess 接收 | 有效事件只接收一次，结束确认后移交给产物打开动作，随后关闭 |
| 产物 FD | execution 内的 artifactSet | 通过现有路径/总量校验后移交 executionResult；由 serveConn 交付并 Close |

P3 的 execution.Run 完成不等于 P2 已取得结果。保持 Result→文件流→关闭产物→Completion→归还 helper 槽位→正常 EOF 的既有成功协议。
P2 的调用取消可以先返回错误，P3 仍在独立期限内清理；不承诺“客户端取消返回意味着远端已停止”。输入复制完成与输入监测 goroutine 退出仍是不同事实。

## 源码落点与依赖方向

| 位置 | 目标归属 | 收敛要求 |
|---|---|---|
| helper/server_linux_amd64.go | service 的接纳、停服、交付编排 | 不编排原始 FD；不增一个包外 Service 外壳 |
| helper/isolation_plan.go（新增） | isolationPlan 和分组配置 | 只读策略值，无内核资源；固定原值可追溯 |
| helper/execution.go、execute_linux_amd64.go | execution 的状态、Run 和最终结论 | 当前 launch/supervise/finish 是迁移输入，最终由对象封装状态转移；删除无作用转发入口 |
| helper/process_linux_amd64.go（新增）及必要同职责文件 | isolatedProcess | 吸收 launch 的 cmd/FD/I/O 和 cleanup 的相应等待；文件边界服从对象，不强迫每个方法独立文件 |
| helper/capture.go、delivery.go | 捕获与执行产物 | 捕获任务归 process；产物集合与结果所有权在 delivery 附近聚合 |
| launcher/init_linux_amd64.go、rootfs_linux_amd64.go | initSession、rootFilesystem | 将挂载过程的局部状态收进对象；不新增通用 mount 框架 |
| launcher/dispatch、payload、exec、startup_protocol | 进程入口与最终执行 | 保持真实进程与线程边界；最终 exec 可以继续是受约束的过程函数 |
| container/isolated.go、helper/client.go | P2 的远端执行适配 | 继续实现 Container；结果与取消含义明确，不复制 P3 的生命周期状态机 |
| judge/flow | 后续每次判题对象 | 仅持有本次源码/编译引用和判题状态，通过 Sandbox 接口复用命令执行 |

依赖是 service 使用 execution，execution 使用 plan、group、isolatedProcess 和产物集合；isolatedProcess 使用 launcher 的既有协议，launcher 不回调 helper 对象。cgroup 保持独立资源能力。先在现有包内完成结构收敛，本轮不新增本机协议包，不改变模块依赖。

## 接口与状态验收

阅读者应只看类型定义、构造和主入口便能回答：谁创建 namespace/cgroup，谁 mount，谁 execve；哪个对象可以放行命令；正常、超时、取消、部分启动失败分别由谁收尾；产物何时移交；哪个失败会使节点停止接单。

每个目标对象必须给出拥有资源、操作前后条件和失败结果。若一个新对象只是把全局函数改成方法，仍由外部读写其 FD/状态字段，或仍要求 service 记住清理顺序，则该批结构验收不通过。

### 数字与固定值的处理

先清点再命名。每项记录：所在符号、实际含义、单位、原值、依据、定义所有者、关联约束、处理方式。
没有查到依据时明确记录“现有值，依据待确认”，保留原值，不编造性能或安全理由。

| 类别 | 当前例子 | 处理方式与边界 |
|---|---|---|
| 继承 FD | init 的 3/4/5，exec 的 3/4/5 | 按角色定义具名 FD；同一数字在两个进程角色中指代不同通道，不得混用。ExtraFiles 的数组顺序与子进程接收定义一起核对 |
| 协议与握手 | Version: 1、'R'、'G'、退出记录长度 8 | 协议所有者定义具名常量/类型；发送与接收共享定义。裸 JSON 中的版本也要纳入清点，保留线格式 |
| 错误阶段 | terminalFailure 的 1～7 | 定义带明确数值的阶段类型，报告与解码按同一映射；不能用会随插入重排的隐式顺序改变协议值 |
| 总传输期限 | helper 两端 150s、HTTP 写出 160s、交付写出 10s | 名称表达限制的操作，单位可读；注明多个计时起点/覆盖阶段。先核对已有关系，不默认它们已经满足所有最坏情况 |
| 容量和资源预算 | 128 个文件、4MiB 结果帧、64MiB 输入、128MiB 工作区 | 区分请求预算、节点硬上限和协议大小；仅真正同一约束共用定义。默认值不等于最大值 |
| 权限和系统值 | 0o700、0o600、syscall 数字 | 常见权限位保留八进制，补充所有者与访问目的；有平台常量时使用平台常量。不要把正常系统表达全部包成无信息量的新名字 |
| 字段索引与位标记 | Limits 的 index == 3、values[3]、present 位 | 用具名字段索引或统一字段描述保证映射；不能只把 3 换成另一个仍需记顺序的名字 |
| 特殊状态值 | ExitCode=-1、ReadyFD=0、limit=0 | 说明尚未退出、无握手测试模式、显式零预算等各自含义；缺省和显式零的语义原样保留 |
| 普通计算 | 循环初值、计数加一、min/max | 含义直接可见时保留字面量，不做机械提取 |

常量默认放在最窄的实际所有者旁边，不建立全模块 constants.go 大杂烩。
配置只承载部署方确实需要选择的策略；本次不因发现硬编码就扩大配置面。
不能在安全策略安装后的最终 exec 段引入新的格式化、分配或普通 Go 调用来替换裸值；该段的命名重构仍须保持调用约束。


## 数字、注释和行为兼容

沿用用户给出的完整注释规范：代码和命名优先；注释只补原因、约束、风险、取舍和公共 API 语义；不猜选值理由，不逐行翻译，不遗留注释掉的旧代码。待办标记说明上下文和触发条件；临时兼容方案说明问题来源和移除条件。新增注释逐条自检。

固定协议类型和字段先保持原归属和序列化内容；新增内部值对象不能自动成为 wire DTO。测试、错误正文、默认/显式零值、计时起点与最终事实均以实施起点核对，不因为新的类型设计改变行为。

## 可用于未来编码规范的候选规则

本节是候选规则的唯一草稿来源。本轮不复制到 docs/coding-standards；不改变现有规范的效力。
后续经人工确认长期适用并明确授权后，按当前 docs/coding-standards 索引分别迁入通用约定与 Go 专项，原处改为引用。

| 候选 | 规则 | 适用范围与例外 | 检查方式 |
|---|---|---|---|
| C-01 | 主流程按使用者关心的生命周期表达 | 请求、命令、作业；纯算法不必套阶段模板 | 从入口追到输出，能说出阶段顺序 |
| C-02 | 跨接口/线程/进程交接说明实现入口、对象和完成条件 | 异步或无法直接跳转的边界；普通直接调用无需重复解释 | 交接两端可定位，后台任务可找到收尾者 |
| C-03 | 函数名准确表达动作及返回时的进度 | 所有代码；领域中已有精确定义的简称可保留并解释 | prepare 是否偷偷启动，Start 是否说明仍在途 |
| C-04 | 文件按资源所有者或一起变化的职责组织 | 不设机械行数限制；平台后缀与构建约束优先 | 改一个阶段不需遍历无关文件，避免万能 utils/constants |
| C-05 | 有业务/协议/资源含义的数字必须可解释 | 普通计数、常见八进制权限表达可保留 | 用途、单位、依据、所有者、关联约束能回答 |
| C-06 | 同一协议定义共用，不同含义不因同值合并 | 协议值固定映射；独立安全边界可重复校验但解释归属 | 核对两端与旧值，不仅检查是否使用常量 |
| C-07 | 关键资源有明确所有者及完成条件 | 文件、连接、锁、进程、异步任务；Go 的 defer 仍保留异常兜底 | 正常、取消、失败各走一遍释放路径 |
| C-08 | 注释解释原因、约束和交接 | 教学项目可说明必要背景；不逐句翻译代码 | 去掉注释会丢失哪些有用知识，TASK 引用不代替说明 |
| C-09 | 共享上下文有明确生命周期 | 多参数反复出现时考虑私有对象；不机械按参数数目重构 | 不引入全局可变状态或万能 Context |
| C-10 | 阅读性变更验证行为等价 | 移动、重命名、提取；发现 bug 独立修复 | 对照契约、默认值、错误/取消及平台回归 |


新增候选 C-11：有资源的对象明确唯一所有者、移交点和合法操作；适用于异步执行和资源管理，纯计算不强制对象化。检查方式是从构造/主入口回答失败回收和状态前置条件，禁止仅按结构体数量评价设计。候选仍不全局生效。

## 安全与失败

隔离策略的来源仍是受保护的节点配置；内部计划对象不能成为客户端绕过字段校验、改变挂载或身份的入口。P3 原子入组、P4 文件系统隔离、P5 最终执行限制分别在原进程边界生效，按上表核对实际系统调用与资源移交。

失败路径由 execution 汇总，保持现有命令错误、平台错误、OOM 与回收错误的优先级。资源只取得一部分也必须进入结束路径；不能确认回收时沿用原停服/容量隔离规则，不能凭进程退出码发布可复用状态。

## 监控与部署

保留现有日志、错误阶段与部署入口。私有对象及内部阶段不增加对外状态字段；对象方法返回的错误仍由原报告边界输出，避免重构后同一失败被重复记录或漏报。运行部署方式、服务数量和节点权限保持不变，本计划无部署迁移步骤。

## 迁移与兼容

按 PLAN-033 的 R0～R8 逐批迁移，每批同步调用点、原测试内部引用和阅读入口，不长期并存两套 owner 或状态机。保持现有三个常驻服务和每次两个临时进程，不以合并进程消除协议复杂度。

## 备选方案

继续只按 launch/supervise/finish 拆函数，仍需调用者掌握共享字段与清理顺序，无法满足本次所有权验收。将每个步骤都包装成接口或对象会增加跳转，却不产生新的资源边界。本方案只为独立资源和状态建立对象，保留纯函数及最终 exec 的受约束过程实现。

## 风险与重审条件

主要风险是迁移期间出现双重所有者、产物提前关闭、后台任务尚未结束便归还容量，以及新旧状态判断同时生效。每批必须同步替换调用点与失败测试，以唯一所有者和完成条件验收。

若某种封装要求增加最终 seccomp 后的普通 Go 调用、改变 FD/握手/回收顺序或更改协议/权限，停止该部分并升级设计，不能以“面向对象”为理由接受行为变化。独立阅读复核和候选自身 Linux CI 分开记证据；本机通过不代表结构已被读者认可。

## 变更记录

- 2026-09-10～14：建立并实施 B0～B4 的数字、主线和文件整理；原始资源盘点与移动记录保留在下方历史附录及 VERIFY-050。
- 2026-09-14：依据用户确认的五个设计概念改写当前方案，以所有权、合法状态、P3/P4/P5 分工和单入口 Run 为后续重构依据。本轮只写文档，新模型未实施；CHANGE/DECISION 签署不改。

## 历史附录：B0～B4 盘点与实施记录

以下为当时的基线、符号和检查记录，不是后续实施方案；出现 prepare/monitor/wait 等旧名时应结合当时快照理解。后续以本文当前对象模型和 PLAN-033 为准。

## 2026-09-13 计划细化：资源盘点入口

主阅读路线的核心入口在当前基线仍存在。node 的旁路新增/保留 environment→probeDeployment→verifyDeployment，
本次重构必须把环境身份分支也留在阅读导航中。下面是实施前盘点入口，关闭顺序仍以源码为准，不改变已签署的权限模型。

| 资源 | 取得者与转移 | 正常/失败回收入口 | 发布约束 |
|---|---|---|---|
| 接纳与执行名额 | Pool.Run 接纳后等待 sem | Run 的兜底与 Pool.Close 取消/等待 | 容器关闭前不让下一次执行复用该名额 |
| store 输入读者 | runner.resolve/putInput 取得 | closeInput 的一次性关闭；取消回调解除读阻塞 | 不因取消放弃输入句柄回收 |
| 客户端暂存输入 | isolatedContainer.PutFile/Start 建立 | 启动后移交 inputStream/helper.Call；未启动由 Container.Close | 输入文件不可被用户进程直接访问 |
| helper 连接 | helper.Call 拨号，Serve 接收 | ctx 取消/超时/交付结束关闭；输入发送任务收尾 | 客户端必须收到 Completion 和正常 EOF |
| 单次 cgroup | helper.execute 创建 group | execution.finish 调 Stop/等待与 release/Close | 最终事实与清空确认后才能成功 |
| init 进程 | execution.prepare 创建并入组 | monitor 与 finish/wait 消费 cmd.Wait 事实 | init 退出不等于用户程序正常完成 |
| 输入、输出与控制任务 | prepare 启动并保留通道 | cancelInput、wait、collectCapture、drainEvents | I/O 结束前不释放仍使用的资源 |
| init 与 payload 控制 FD | ExtraFiles 传到新进程角色 | initSession own/close；exec 阶段 CLOEXEC/明确关闭 | FD 的角色和传递顺序固定，最终 exec 不泄漏特权通道 |
| 隔离工作区与目录句柄 | init.prepareRoot 后把目录 FD 传 helper | initSession 关闭本地引用，helper 保存至 collectOutputs/release | 回收后仍能从已持有的受控句柄读取声明产物 |
| 产物 FD 与传输 | execution.collectOutputs → executionResult | WriteFiles 后 Close，失败也 Close | 所有句柄关闭后才发 Completion |
| 暂存产物与 store ref | isolatedContainer.consume 保存，runner.collect 发布 | Container.Close；collect/Pool 失败回滚 ref | HTTP 响应前确认工作区关闭和回滚结论 |
| 判题源码/编译 ref | flow.Judge/compile 使用 Sandbox.Upload/Run | Judge 清理路径使用不随请求取消的上下文 | 结果可返回不意味着清理错误可以擅自改成新判题语义 |

### 数字盘点起始项

这是 TASK-104 必须扩展并核对的起始清单，不是穷尽所有生产数字。值取自当前源码；命名提取不改变它们。
定义归属与依据在源码只记录一次，文档盘点用于对照；完成迁移后文档保留原值/新符号证据，不另成为运行配置真源。

| 符号或位置 | 原值与用途 | 拟议归属 / 依据 |
|---|---|---|
| init 继承通道 | 3 control、4 stage input、5 liveness | launcher init 协议；与 helper 的 ExtraFiles 顺序绑定 |
| exec 继承通道 | 3 config、4 ready/GO、5 error | launcher exec 协议；与 init.startPayload 的 ExtraFiles 顺序绑定 |
| ready/GO | R/G 单字节 | 启动握手定义；两端共同使用，不能同进程状态混淆 |
| 帧与协议版本 | 4 字节大端长度、Version=1 | 帧编解码与协议定义；不得改变线格式 |
| exec 失败记录 | 8 字节、阶段 1～7、errno 小端布局 | exec 错误协议；保留显式枚举值和 padding |
| launcher.MaxFrameBytes | 64KiB 控制帧 | 协议大小上界，普通帧不能误用结果帧预算 |
| helper 结果帧 / 完成帧 | 4MiB / 1024 bytes | helper 请求/响应协议的帧约束；两端一致 |
| 输入 / 产物 | 各 64MiB | 不同资源的独立预算；即使同值也分别命名 |
| execution CPU 采样与周期 | 5ms / 10ms | 执行监督策略，采样间隔与 cgroup CPU 周期不是一件事 |
| execution 启动 / 清理 | 3s / 5s | 不同生命周期阶段；超时起点、ctx 使用与原值一起保留 |
| workspaceBytes/workspaceInodes | 128MiB / 4096 | 工作区固定策略；不是用户请求的 memoryBytes |
| 请求、命令和环境数量 | 文件 128、命令参数 256、环境 128 | 各个 Validate 中按对象命名，不按相同数值合并 |
| 已归一化执行硬上界 | CPU 60s、墙钟 120s、内存 1GiB、进程 256、stdout/stderr 各 1MiB | launcher.Request.Validate；与 runner 的默认限额区分 |
| Limits 索引 3 | maxProcesses 在 values/present 中的位置 | contract/limits.go 具名索引，保持序列化与零值语义 |
| 原生 sandbox 端口 | 15050（loopback） | node/deployment.go 的已验收端点限制；本次命名不扩大可配置范围 |
| 常用目录/文件权限 | 0o700 / 0o600 等 | 保留八进制并说明所有者；不同资源权限不能一律统一 |

固定生产选择值（如超时、容量）的历史最优性并未在本轮重新证明；依据不足时如实标为“既有策略，推导待查”。
协议布局与平台事实的依据直接来自两端代码；不能用“安全默认”代替尚未找到的选值理由。


## 2026-09-13 B0 实施起点与精确映射

用户已明确“开始第一轮重构”。实查 HEAD、harness/cases 摘要仍与 PLAN-033 冻结值完全一致；
源码/契约/部署/workflow 无未提交差异，所以本轮代码起点可由该 HEAD 重建。
已跟踪的 WORK-049/050 签署及交接、WORKS，以及 server 数据 ZIP 原样保留，不纳入源码回退。
本机另存完整 tracked diff 与状态到 /var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-b0-09w9m2si，供本次工作区核对；永久源码依据仍是冻结提交。

下表补足现有导航的目标符号。B1 先具名化协议，B2～B7 才移动行为主体；函数无职责变化时保留名字。
目标均为同包文件，不新增协议包或转发层：当前客户端依赖本机 Result/Reason 和 cgroup.Snapshot，
只迁移类型并不能解除依赖；本次没有足够收益支持新增包。

| 当前文件/符号（internal 下） | 目标文件/符号与批次 |
|---|---|
| sandbox/launcher/init_linux_amd64.go 与 exec_linux_amd64.go 的 FD/握手/失败记录 | launcher/startup_protocol.go 统一发送/接收定义，B1；StageSpec/Event 留现有协议定义至 B4 |
| launcher/protocol.go 的 Validate/ReadFrame/WriteFrame | 原地命名请求预算、路径深度、帧头长度；不拆转发函数，B1 |
| helper/client.go 与 server_linux_amd64.go 的帧上限/期限 | helper/protocol.go 放双方帧预算和会话期限；拨号与服务启动期限留实际所有者，B1 |
| sandbox/runner/runner.go 的 putInput/resolve/budgetReader | runner/input.go，名称不变，B2 |
| runner/runner.go 的 collect/classify | runner/result.go，名称不变；Run/defaultLimits 留入口，B2 |
| pool.Run/Close；isolatedContainer.Start/execute、isolatedProcess.Wait | 原地明确等待、关闭、发布的完成条件，B2 |
| helper/execute_linux_amd64.go 的 execution.prepare | launch_linux_amd64.go: execution.launch；显式表示创建并启动 init 与 I/O，B3 |
| helper.execute/monitor/handleEvent | execute 保留总编排；monitor/handleEvent 移到 supervise_linux_amd64.go，B3 |
| helper/execution.go 的 finish/wait/release/collectOutputs | cleanup.go 同名方法；execution.go 留状态与资源定义，B3 |
| launcher.Dispatch/runInit/initSession、RunExecStage | dispatch_linux_amd64.go 放模式分派；init/exec 各自文件保留真实角色主体，B4 |
| helper/helper.go 的 Config/Validate、Result/Output/Completion/Reason、boundedCapture | config.go、protocol.go、capture.go；已有同职责文件直接复用，B4 |
| judge/flow.Judge/compile/runCase/stdinFor/runInputs | flow.go 每请求私有 judgement；compile.go/case.go 相应方法，B5 |
| flow.evalCase/makeOutput/firstN/worse/scoreOf/systemError | result.go 同名函数；loadCases/deleteRef 留 Judge 资源主线附近，B5 |
| judge/node.New/executableDigest、Run/exchange、Handler/handleInstall/decodeJSON/lastPart | identity.go、control.go、http.go；已有 install.go/environment.go/deployment.go 保留，B6 |
| config/config.go 的加载/默认/Validate、duration 解析 | load.go、judge.go、sandbox.go、logging.go、duration.go；既有 node.go 保留，B7 |
| contract/limits.go 的 values/limitsFrom/限额 JSON 位映射 | 原文件按字段具名表达，不改变公开序列化/显式零值，B7 |

### 完成与错误路径核对

- /run 最终顺序：api.handleRun → Pool.Run → runner.Run → isolatedContainer.Start → 后台 execute → helper.Call → Serve/serveInSlot/serveConn → helper.execute → execution.prepare → launcher.Dispatch/runInit → initSession.run/startPayload → RunExecStage。
- 握手：exec 写 R 等 G → init.releasePayload 等 R、设置自身权限/策略、写 ready Event → helper.handleEvent 检查状态与预算、写 G → init 转发 G → execve。exec 线程从 LockOSThread 到 exec 不解锁。
- 命令退出：init.reportExit → helper.monitor → finish；finish 用独立清理上下文 Stop，cancelInput 解除读阻塞，wait 等 init/I/O 并排空收到但未消费的目录 FD，collectOutputs 取得产物 FD，再 release 环境。Stop/Wait/I/O/Close 失败保留错误并阻止成功交付。
- serveConn 写 Result/产物流、关闭产物 FD、写 Completion；serveInSlot 返回槽位后关闭连接。Call 必须收到 Completion **和正常 EOF**；reset、尾随数据、取消都不是成功。随后 isolatedProcess.Wait/runner.collect，Pool.Run 确认 Container.Close 并在失败时删除新 ref，api 才写响应。
- 输入所有权：runner.resolve 取得 store reader → putInput/Start 阶段消费；isolatedContainer 的暂存文件启动后交给 inputStream/Call。Call 所有返回分支经一次性 Close，已启动上传还需等待 sent；未启动资源由 Container.Close 回收。
- initSession 关闭已接收资源；life/control 特意存活到 PID 1 退出，由内核关闭。收到工作区 FD 后 init 关闭自身引用，helper 负责后续 release。错误报告没有解除 helper 的整组停止/等待责任。
- Judge 次线：judge/api.handleJudge → flow.Judge → loadCases/Sandbox.Upload → compile → runCase（stdinFor/runInputs → Sandbox.Run → evalCase）→ worse/scoreOf 汇总 → deleteRef（WithoutCancel 清理上下文）。Sandbox 实例由 cmd/judge 组装为 HTTP client；client.Run 对接 /run，节点注册/安装旁路不参与这条同步顺序。

### 数字补充清单与归属

以下与前文表共同覆盖 FD、协议、失败阶段、期限、容量、权限、字段位、特殊值八类。
协议值依据两端格式、FD 依据 os/exec ExtraFiles 映射；固定策略的用途可核对，历史选值推导尚未找到，全部保留现值。

| 原位置/值 | 含义、关联约束与目标 |
|---|---|
| exec 阶段 1/2/3/4/5/6/7 | 配置/rlimit/CLOEXEC/降权/seccomp/execve/握手；显式 execFailureStage，不按调用顺序重排 |
| exec 记录 offset 0、offset 4、长度 8 | 第一个字节 stage，1～3 padding，4～7 小端 errno；编码/解码共同定义，位移 8/16/24 保留可读字节拼装 |
| exit 125、ReadyFD 0 | 可信启动器失败退出；0 仅原语测试跳过握手，命令真实退出码继续由 WaitStatus 提供 |
| ReadFrame/WriteFrame 的 4 | uint32 大端帧头字节数，frameHeaderBytes；与传 FD 的 int32 大小无关 |
| Request.Validate 的 32KiB；ExecSpec.validate 的 64KiB | 请求 argv/env 与可信 path/argv/env 总字节各有独立上限；后者包含解析路径和注入环境，不合并 |
| Request 输入128/输出128/参数256/环境128；路径深度8 | 按对象分别定义；返回产物数复用请求 MaxOutputs；正则首字符+最多127后续字符保留并注释128字节边界 |
| ExecSpec NOFILE 8～1024、FSIZE 1GiB；payloadSpec 256/64MiB | 验证硬边界与实际执行策略分别命名；256 是 FD 数，不是请求 maxProcesses |
| 身份 >= 2^32−1 | Linux UID/GID 的全位1无效标识，按 ABI 命名；0 root 仍拒绝 |
| channel 收包1024、CmsgSpace(4×4)、最多1 FD | Event 字节预算；接收缓冲可容4个 int32 FD，但协议最多允许1个，异常时全部关闭，不缩小缓冲 |
| helper 接收事件通道/循环4 | 有限接收预算，正常 workspace/ready/exit 共3条，上限4的历史选值推导待确认；不改变次数与 drainEvents 收尾 |
| helper.Call 拨号3s、双方会话150s、服务 header5s、交付写10s | 分别 dialTimeout/sessionTimeout/requestHeaderTimeout/deliveryTimeout；客户端拨号后计时，服务端校验 header 后重新计时，执行后只刷新写端期限，不能当作统一绝对截止时间 |
| Serve recoverOwned5s；probeInstallation5s | 旧环境回收与安装冒烟不同阶段，分别命名，不能借与 cleanupTimeout 同值合并 |
| probeInstallation true 的 CPU2s/wall5s/memory128MiB/process64/stdout与stderr1024 | 固定安装冒烟策略，随 probe 所有者注释；与用户请求限额分别维护 |
| runner defaultLimits 的 CPU1s/wall5s/memory128MiB/process64/stdout与stderr64KiB | 上层缺省值，B2；runner 接受 stdout64MiB/stderr16MiB 是另一个边界，不能改成 helper 的1MiB |
| node 退避1s×2至30s；multipart预留2MiB、manifest1MiB、二进制摘要输入256MiB | control/http/identity 所有者 B6；+1 是探测超限额外读取一字节 |
| node/environment HTTP15s；deployment probe CPU2s/wall5s、stdout8192/stderr1024；端口15050 | 已验收环境身份路径 B6；保留部署分支与旧开发分支 |
| config SandboxTimeout60s、node heartbeat10s/request5s、上传100MiB/解压1GiB/单项64MiB/2000文件/倍率100 | 配置默认字段已有名称，B7 归属调整并保留实际值，不能提升为协议硬上界 |
| contract.Limits 索引0～5、index3进程数、1<<index present | CPUNs/ClockNs/MemoryBytes/MaxProcesses/StdoutMaxBytes/StderrMaxBytes 顺序不变，零值与缺省分别表达，B7 |
| mkdir0700、私有文件0600、服务组 socket0660 | 所有者独占目录/文件与受限服务组访问；保留八进制，各资源独立，不统一改权限 |

### 冻结测试入口（已逐个核对源码声明）

cases.json 共93项，requiredGoTests 共52个函数，包路径和名字如下。它们是保留清单；声明存在不代表本轮已执行。

| 包路径 | 固定测试名 |
|---|---|
| cherry-oj/judge-engine/internal/sandbox/cgroup | `TestCleanupTimeoutCanRetry`, `TestConcurrentCreatesAndBoundedHistory`, `TestFailedCloseStopsNewWorkEvenAfterRecovery`, `TestFreshGroupsAndFinalAccounting`, `TestKillAndAccountingFailureNeverBecomeSuccess`, `TestNewGroupDoesNotIssueKillBeforeFirstProcess`, `TestPartialCreateFailureQuarantinesManager`, `TestRejectWrongHierarchyAndNameCollision`, `TestUsageRejectsMalformedAndOverflow` |
| cherry-oj/judge-engine/internal/sandbox/helper | `TestAncestorOOMPreservesPlatformFailure`, `TestCaptureBoundsAndDrains`, `TestClientCancellationAfterCompletion`, `TestClientCancellationReleasesBlockedInput`, `TestClientEarlyFailureClosesBlockedInput`, `TestClientRejectsInvalidCompletion`, `TestClientRejectsMissingCleanupAcknowledgement`, `TestClientRejectsResetAfterCompletion`, `TestClientRejectsTrailingData`, `TestClientRejectsUnrequestedArtifact`, `TestClientStreamsInputsAndOutputs`, `TestClientWaitsForConnectionRelease`, `TestConfigRejectsPrivilegeConfusion`, `TestConnectionEOFReturnsCleanSlot`, `TestCopyInputOwnsNoCachedFastPath`, `TestDeliveryRejectsTruncatedArtifact`, `TestFailedStartAlwaysReclaimsOwnedResources`, `TestFinalOOMSurvivesInitExitDiagnostic`, `TestFinishCancelsBlockedInputBeforeWaiting`, `TestFinishClosesEarlierArtifactsWhenLaterOpenFails`, `TestFinishClosesUnconsumedReceivedFiles`, `TestFinishOOMDoesNotHideIndependentFailure`, `TestFinishOrdersCleanupAndWithholdsUnsafeArtifacts`, `TestFinishPreservesExecutionAndMultipleCleanupErrors`, `TestOwnedFileConcurrentCloseUnblocksReader`, `TestSlotIdentityRange`, `TestSlowDeliveryHoldsSlotUntilDeadline` |
| cherry-oj/judge-engine/internal/sandbox/launcher | `TestCompilerCommandNamesPreservePathBoundary`, `TestControlFDIsCloseOnExec`, `TestControlInterruptChecksClosedFiles`, `TestControlInterruptedSocketDelivery`, `TestControlReceiveFailureClosesFDs`, `TestControlReceiveInterruptedOnce`, `TestControlSendInterruptedOnce`, `TestControlSendPreservesErrors`, `TestControlShutdownUnblocksIO`, `TestExecSpecBoundary`, `TestFailureErrnoPreservesCause`, `TestFramePreservesFollowingStream`, `TestFrameRejectsInvalidWire`, `TestOutputRejectsLinksAndSpecialFiles`, `TestRequestRejectsEscapesAndUnboundedData` |
| cherry-oj/judge-engine/internal/sandbox/policy | `TestAMD64PolicyBoundary` |


## 2026-09-14 B2～B4 实施补充

当前主线入口见上面的已更新路线；完整移动映射与本地证据见 VERIFY-050。此前符号盘点中的 prepare/monitor/wait/release 为旧名，依次对应 launch/supervise/waitForInitAndIO/releaseEnvironment，不保留同名转发壳。

正常结果从明确的关闭步骤之后返回。runner 的输入关闭失败和 Pool 的容器关闭失败均撤销产物；helper.Call 在接收完整结果及完成确认后解除输入/连接阻塞、等上传任务、合并关闭错误。异步任务的取消不等同于完成：helper 客户端取消可以先于远端回收返回错误，远端仍要走独立期限的 finish，不能宣称取消响应证明隔离环境已清空。

未合并 helper 进程，也没有引入第二层执行状态或通用 helper 包。配置、请求、启动协议、输出捕获归各自文件，最终 exec 阶段保持原执行语句。所有新增原因注释以源码可验证的所有权、并发和协议约束为依据；默认预算/随机 ID 长度仅命名和换算，不附会历史选值理由。


## 2026-09-14 R0 迁移合同

用户已在计划交付后明确要求开始实施。实际源码起点保存于 `/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/work049-objects-0mpop4ya`，包含全部模块文件、WORK-049 文档、文件摘要与既有 diff。以下映射覆盖当前 execution 的全部字段；私有测试可跟随所有权迁移，原断言和固定测试入口保留。

| 当前字段/动作 | 新所有者与前置条件 | 移交或结束条件 | 原有覆盖 |
|---|---|---|---|
| request、Config、StageSpec 组装 | isolationPlan；入口已校验，复制所有请求切片 | 不持有内核资源；StageSpec 取得独立快照 | Request 校验；新增策略快照测试 |
| started、result、runErr、initReportLost、group | execution；Run 唯一推进状态 | 停组取得最终计量，独立合成 OOM/平台/清理结论 | TestFinishOOMDoesNotHideIndependentFailure、TestFinalOOMSurvivesInitExitDiagnostic、TestAncestorOOMPreservesPlatformFailure |
| makeGroup、File/Stop/Close | execution 创建和关闭 group；process 只取得启动所需 FD | Stop →进程/I/O 等待→句柄释放→group.Close→删除空宿主目录 | TestFinishOrdersCleanupAndWithholdsUnsafeArtifacts、TestFinishPreservesExecutionAndMultipleCleanupErrors |
| control、childControl、lifeR/W、dataR/W、outR/W、errR/W、cgroupFD、mountpoint | isolatedProcess；每次成功分配立即登记 | 子进程端 Start 后关闭；其余先解除阻塞和等待，再关闭；未确认停止保留目录 | TestFailedStartAlwaysReclaimsOwnedResources、TestOwnedFileConcurrentCloseUnblocksReader |
| stdoutDone、stderrDone、overflow、inputCopied、inputFinished、cancelInput | isolatedProcess；启动前接输出捕获，启动后交付输入 | 输入取消先于等待；输入复制结束不等于任务退出；完成事实不暴露原始通道 | TestFinishCancelsBlockedInputBeforeWaiting；新增进程完成事实测试 |
| initExited、initStopped、waitErr | isolatedProcess；有且只有一个 cmd.Wait | 返回停止事实、wait 错误与捕获结果；execution 负责 OOM 归因 | TestFinalOOMSurvivesInitExitDiagnostic、TestFailedStartAlwaysReclaimsOwnedResources |
| phase、events、received.dir、workspace | isolatedProcess；workspace→ready→GO→exit | 校验和保存目录；重复/乱序拒绝；未消费 FD 收尾关闭；完成后只移交一次 | TestFinishClosesUnconsumedReceivedFiles；新增 ready/乱序/放行测试 |
| openOutput、collectOutputs、result.files | 产物来源窄接口与 artifactSet；仅在停止/等待成功后打开 | executionResult 接管集合；旧 execution 清空引用；关闭结果保留第一次错误 | TestFinishClosesEarlierArtifactsWhenLaterOpenFails、TestDeliveryRejectsTruncatedArtifact |
| shutdownControl | isolatedProcess 的平台实现 | 不再作为 execution 的可变回调字段 | 输入/部分启动/控制接收回收测试 |
| prepareRoot 的 StageSpec、root、dir、stdin | P4 rootFilesystem；仅 root PID 1 的锁定线程调用 | 固定 mount/pivot/input 顺序；目录和 stdin 交给 initSession；失败随 PID 1 退出回收 | 固定 Linux boundary/kernel 用例；本机只编译核对 |
| serveConn 配置、manager、executable、slots、fatal、停服 | service；先安装检查、恢复、探测再接单 | 执行→交付→关闭产物→Completion→归还槽位→EOF | TestConnectionEOFReturnsCleanSlot、TestSlowDeliveryHoldsSlotUntilDeadline、TestClientRejectsResetAfterCompletion |

正常、超时、取消、启动失败统一进入执行结束路径；建组失败保持原单独 fatal 返回。正常报告丢失只有在最终本任务 OOM 证据成立时才可重归因；其他独立故障保留。低层 syscall、固定数值、协议字节与测试选择器对照本轮快照核验。


### R0～R7 实施落点

- P3：service → execution.Run → isolatedProcess；独立 isolationPlan 快照与 artifactSet 产物集合已实现。process.Next 在同一监督 goroutine 中等待执行事件和外部预算定时器，不引入额外转发任务；execution 决定预算和终止原因，process 判断握手顺序并持有 FD。
- P4：rootFilesystem.Prepare 明确 mountRoot、mountWorkspace、mountSystemViews、enterRoot、loadInputs；局部句柄通过 TakeInputs 交给 initSession。P5 原实现保持。
- P1：judgment 独占一次请求的测例、语言和源码/编译引用；node 的 installation 独占 staging，Node 保留服务锁与注册信息。配置按类型/加载、默认、校验归属，Limits 使用具名索引；没有给纯计算建立对象。
- 本地源码自查可以定位 PLAN 的七个结构问题，但不能充当未参与实现者的独立阅读验收。详细测试、冻结身份及未验证项见 VERIFY-050。
