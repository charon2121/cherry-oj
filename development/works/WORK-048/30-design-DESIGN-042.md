---
id: "DESIGN-042"
type: "design"
title: "Linux 沙箱隔离与资源计量硬化"
status: "checked"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-004"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-09"
updated_at: "2026-09-10"
---

# DESIGN-042：Linux 沙箱隔离与资源计量硬化

## 背景

依据 IMPROVEMENT-004、docs/engine.md §5.5 与 WORK-044/047。沿用 sandbox 不理解判题语义、container 隔离与 cgroup 限量分别负责的设计。

## 目标与限制

首站为用户专用 Ubuntu 24.04.4 / Linux 6.8.0-124-generic / x86_64 测试服务器，root SSH 已授权，只读接口检查已完成；实际隔离与限额行为待独立测试。保留 macOS host 仅供可信开发；Linux 正式模式不能悄悄降级。

## 整体方案

1. 每次 /run 建立新 cgroup 和独立执行环境，编译也如此；只能复用可信只读 rootfs，不能复用存活进程或任务计量组。
2. 使用 mount/pid/net/ipc/uts namespace，私有挂载传播，pivot_root 并卸载旧根；只读最小工具链 rootfs、独立有大小上限的可写工作区与临时目录，proc 仅映射新 PID namespace，最小设备集合。不挂宿主 home、socket、凭据、测试答案或可写 cgroupfs。
3. 推荐特权能力集中在本机最小 helper，HTTP 服务以非特权账号运行，通过受文件权限与对端凭据校验的 Unix socket 调用。helper 固定 rootfs 与挂载策略，拒绝任意宿主路径和挂载参数。用户程序切换专用非特权 UID/GID，清空 supplementary groups 和 capability 集合，设置 no_new_privs；禁止 setuid 提权。
4. user namespace 作为明确候选，不假设系统允许非特权 userns；首轮支持矩阵在 Ubuntu 能力探测后冻结。无 userns 的特权 helper 方案仍必须隔离其他 namespace 并降权，不能让用户程序以宿主 root 运行。
5. 建立独立于 Go 多线程运行时的可信启动路径，exec 用户代码之前完成入组、挂载、降权和 seccomp。优先评估 clone3/CLONE_INTO_CGROUP；备用受控握手只能在可信 launcher 中阻塞，成功入组后才允许 exec，禁止用户代码先执行再写 cgroup.procs。
6. PID namespace 的可信 init 负责回收后代。任务结束/取消/OOM/输出超限时停止整个 cgroup，等待 populated=0，再读取最终资源、取回允许产物、卸载并删除；清理失败隔离槽位、拒绝复用。daemon/helper 崩溃恢复只清理本服务拥有的路径和组，不全局杀进程。

## 模块与数据

新增独立 cgroup 管理组件，container 仅消费创建好的句柄；runner 协调生命周期、资源事实及状态映射。pool 的复用单位改为可信容量槽位，是否复用空挂载空间必须以无残留测试为依据。store、编译产物传输保持有界；所有输入/输出文件访问拒绝 symlink、magic-link、硬链接逃逸和 TOCTOU，优先 dirfd/openat2 在指定根内解析，不依赖字符串路径检查。

内存用单次 cgroup memory.peak，memory.max 限额、memory.swap.max=0、memory.oom.group=1；同时读取 memory.events，不能把任意 SIGKILL 都判成 MLE。memory.peak 是组内内存记账峰值，可能包含文件缓存/tmpfs，不再冒称单进程 RSS；launcher/init 固定开销纳入口径并记录，禁止靠减父 RSS 校正。

CPU 由 cpu.stat 的累计 usage_usec 换算 ns；cpu.max 限速不能替代总预算。监听/轮询累计用量达预算后杀整组，另设独立墙钟定时器。首轮在目标机按 1 秒预算验证超限误差，建议阈值 max(50ms,5%预算)，包含采样周期与允许并行度；这是待实测的验收目标，不承诺零误差。短程序报告真实计量粒度，不伪造纳秒精度。

pids.max 覆盖线程及子进程；补充 nofile/fsize/core rlimit；可写 tmpfs 限额和 inode/文件数上限必须同时覆盖。控制面输出缓冲不在任务 cgroup 中，需有节点级 MemoryMax/TasksMax、并发/排队上限与有界输出，防止任务限制正确但服务自身 OOM。

## 接口与状态

维持当前 RunSpec/RunResult 与 ns/bytes 单位。OOM、总 CPU/墙钟终止、输出溢出、策略拒绝与平台失败需有确定优先级；依据已记录事实映射，未知平台错误不得误报 AC 或把任意 signal 当资源超限。若现有状态不足以无歧义表达，先提交契约增量设计再实施。

## 安全与失败

seccomp 对编译与执行分别制定基于体系结构的最小允许策略；验证动态链接、线程和编译子进程，拒绝额外 namespace、mount、ptrace、bpf、perf、内核管理与危险设备操作。seccomp 只是系统调用过滤，不代替文件/网络隔离。禁用 core dump，close-on-exec/显式 fd 白名单，exec 前关闭特权 IPC。环境能力、策略或 rootfs 不完整时 fail closed。

## 监控与部署

推荐首轮 Ubuntu 原生 systemd 托管 helper 与 sandbox；Judge 同机通过回环/私有网络连接。仅部署进程持有必要权限，不向应用开放 Docker socket，不以 --privileged 容器作为正式交付方案。沙箱 API 只允许可信 Judge 访问，不直接暴露公网；跨主机时先设计加密和认证。SSH 接入信息与密钥不进 Git，服务器提供后先只读探测，再执行已审核部署。

部署包包含只读能力探针、配置示例、安装与卸载脚本、systemd 单元、工具链/rootfs 摘要、健康检查和恢复手册。本机配置留项目内，Ubuntu 系统服务配置采用明确路径配置文件，不依赖手工输入一串启动变量，不存到个人 ~/ 隐藏目录。

## 迁移与兼容

host backend 显式标记不安全开发用途；Ubuntu 必须选择 Linux backend。Ubuntu 后端、rootfs、seccomp、内核/架构和工具链变化纳入执行环境身份，更新节点身份并走现有环境切换/部署/校准。不能复用旧标定，也不能只换二进制然后绕过指纹校验。

## 备选方案

runc/其他成熟隔离执行器可减少自研启动路径风险，但增加依赖及生命周期整合；保留为能力探测后的对照方案。完全自行写 namespace launcher 符合学习目标但安全审查负担较大，必须先验证启动链和退出链；轻量虚拟机更强隔离留后续需求评估。

## 风险与重审条件

服务器版本/权限/架构不匹配、rootfs兼容失败或启动路径难以可靠验证时重审 DECISION-026，不放宽隔离来通过测试。默认先独立验证服务器，禁止直接在承载其他业务的宿主进行资源耗尽测试。

## 依据

[Linux cgroup v2 官方文档](https://docs.kernel.org/admin-guide/cgroup-v2.html)、[namespaces 手册](https://man7.org/linux/man-pages/man7/namespaces.7.html)、[seccomp 手册](https://man7.org/linux/man-pages/man2/seccomp.2.html)。核实日期 2026-09-09；实际支持能力以目标服务器探针结果为准。

## 历史：Ubuntu 22 目标确认

用户已明确 Ubuntu 22 云服务器且可 sudo。不能仅凭发行版判断 memory.peak、cgroup.kill 或 CLONE_INTO_CGROUP 可用：目标小版本、内核、架构和虚拟化限制由 TASK-093 核实。若必要计量接口缺失，先提交受支持内核升级方案及回退评估；不得以轮询 memory.current 冒充无损峰值，也不得自动升级内核、重启云主机或关闭系统安全策略。后续隔离压力测试需确认服务器没有其他业务。

## 变更记录

- 2026-09-09：状态变更：draft → review。原因：完成隔离计量和Ubuntu部署提案，目标内核能力先探测
- 2026-09-09：结构与内容校验通过，由工具置为 checked。

## 本地 go-sandbox 参考审读（2026-09-09）

用户明确要求参考而非照抄。已只读检查 dev-dependency/go-sandbox，独立仓库 HEAD 为 0595b11cc1c170d8d94f20cd0088d8d847dfa823，工作区无修改；MIT 许可。以下是源码审读结论，不是该依赖在本项目中的安全认证或运行测试。依赖目录不进项目 Git，正式实现不要求克隆时存在该目录；参考说明记录相对路径与提交摘要，避免形成构建依赖。

| 已读位置 | 机制与证据 | 本项目取舍 |
|---|---|---|
| pkg/forkexec/fork_linux.go 的 syncWithChild | 父子 socket 同步，SyncFunc 完成后确认继续执行；失败走启动失败路径 | 保留 exec 前确认隔离就绪的不变量；不采用用户代码运行后入组 |
| pkg/forkexec/runner_linux.go、clone3_linux.go | CgroupFd、namespace flags、凭据与 seccomp 配置分别传入启动路径 | 隔离与限量保持解耦，能力探测决定可用启动机制 |
| pkg/forkexec/fork_child_linux.go | pivot_root、no_new_privs=1、capset、seccomp 和同步顺序 | 按本项目威胁模型重写最小启动链；注释可能失真，以调用实参为准 |
| container/environment_linux.go | 以自身可执行文件启动可信容器进程，传递 socket FD、映射 UID/GID、Pdeathsig；包含 ambient capabilities | 可信引导进程的模式值得参考；不把其权限集合直接用于用户程序或 HTTP 服务 |
| container/container_exec_linux.go | 主进程结束后 kill 后代并等待全部回收，存在 SyncAfterExec 变体 | 保留全后代回收；首版禁止 exec 后才挂 cgroup，采用每次新执行环境 |
| pkg/cgroup/v2_linux.go | cpu.stat usage_usec 转 ns，读取 memory.peak；cpu.max 与 CPUUsage 独立接口 | 单次 cgroup 为权威事实；单位在自己的接口中写明，禁用 v1 分支 |
| container/container_exec_linux.go 的 convertReply | 底层仍有 wait4 用户 CPU/Maxrss，SIGKILL 映射 TLE | 不照搬为最终计量或状态；以 cgroup 资源、OOM 事件和本方终止原因组合判定 |
| pkg/forkexec/fork_unix.go、vfork/ | Go runtime linkname 和多架构底层启动实现 | 不直接移植或删减汇编/运行时钩子；先评估当前 Go 标准能力与独立 launcher 的最小方案 |

参考库的 prefork 优化、ptrace 路线、Darwin 实现、cgroup v1、32 位与冷门架构支持不进入首版。无 seccomp、无 cgroup 或缺少关键计量能力时不会为了兼容而回退零隔离。必要的错误处理、启动握手、降权、FD 回收和后代回收不是“兼容代码”，不能一起删掉。

本项目仍暂定最小特权 helper 与原生 systemd 部署；这不是参考库原样架构。helper 的实现语言/启动方式、userns 使用方式在 TASK-093 冻结，并与复用成熟执行器比较。如果以后引入上游代码或实质性派生，保留许可和来源；当前仅作设计参考。

## 跨 Linux 支持原则与验证矩阵

用户要求后续在广泛 Linux 服务器上验证：Ubuntu 24.04 是当前首个环境，不是唯一支持对象。实现以能力与架构为边界，不按发行版名字分支；支持范围随实际测试扩展，不承诺未经测试的“所有 Linux”。

建议分层矩阵：Ubuntu（包含历史 22 和当前 24.04）、Debian、RHEL 兼容发行版；x86_64 与 arm64；系统默认内核与实际云厂商内核；systemd 的 cgroup 委派以及 AppArmor/SELinux 启用状态。具体发行版版本与架构由可提供机器清单冻结，不臆测已有服务器。最小 rootfs 的 libc/动态链接环境独立固定，不依赖把宿主整个 /usr 直接暴露给任务。

探针统一输出：发行版/内核/架构、虚拟化与权限、cgroup 挂载及委派、cpu/memory/pids、memory.peak、cgroup.kill、namespace、seccomp、启动系统和 LSM 状态。区分“缺能力”“被策略禁止”“权限不足”和“未验证”；文件存在不等于权限/行为正确，后续隔离测试阶段再做临时资源的行为探测。

每个平台分为：已验证支持、待验证、明确不支持。缺失必需能力的机器仍执行负向测试，证明明确拒绝启动；可选观测指标如 pids.peak 不应误作整个服务的最低要求。对于无 systemd 的环境先验证执行核心，服务托管适配另列，不把 systemd 缺失当作 Linux 隔离机制缺失。

任何备用启动/清理机制必须在同一套无竞态、计量和清理测试下等价成立，否则不添加。CPU 配额/总量、计量峰值准确性、权限和后代回收属于安全语义，不随发行版降低。

## 当前首个目标环境更新

用户重装系统后实测首个目标已变为 Ubuntu 24.04.4 LTS / 6.8.0-124-generic / x86_64；此前 Ubuntu 22/5.15 内容保留为历史探测。memory.peak 已存在，无需为此升级内核。宿主现有 swap，执行组必须按既定方案单独限制 swap。后续跨 Linux 测试方向不变，Ubuntu 22 仍可作为兼容/缺能力拒绝测试对象，不能把首次服务器升级等同于已兼容旧内核。

## TASK-093 冻结结果（2026-09-09，实施规格，尚未运行验证）

本节细化意图闸覆盖的候选方向；不改变隔离、计量和失败关闭要求，不代表授权执行后续任务。
现有只读探测足以选定第一版实施路线；涉及新建资源的行为证明放到后续 Linux 测试任务。
若行为不成立，暂停该路线并更新设计，不能以本节“冻结”字样跳过实测。

### 执行器比较与选择

| 方案 | 官方材料确认的机制 | 本项目仍需完成的适配 | 首版选择 |
|---|---|---|---|
| 本项目 Go helper + 纯 Go re-exec 启动器 | 本地 go-sandbox 已审读握手、FD、权限和回收机制；Go 标准库有 UseCgroupFD | 需验证运行时线程、降权与 seccomp 到最终 exec 的连续性，以及启动资源开销 | 用户确认优先采用，先验证最小启动链，不引入 C/cgo、自写 vfork/linkname |
| runc | OCI 容器启动器，支持 cgroup v2 | 固定版本及 OCI 配置、逐次组所有权、CPU 总预算、结果/产物/取消接线 | 保留为自研启动链失败时的替换候选，无需 Docker daemon |
| NsJail | namespace、seccomp/Kafel、cgroup v2、多种执行模式 | 固定依赖和策略，审查计量、清理与本项目单次组语义；禁止采用示例中的宽泛宿主挂载 | 保留对照，不直接将默认配置当安全基线 |
| Isolate | init/run/cleanup 生命周期，当前文档要求 cgroup v2，并有 systemd 委派配套 | box/UID 生命周期、统计口径、系统调用策略及文件交付需要逐项对齐 | 保留对照，不直接复用历史 box 计量 |

这是本项目范围与维护成本的判断，不是宣称自研比成熟执行器更安全；没有运行这三种执行器，也未固定任何第三方可发布版本。
资料核对于 2026-09-09：[runc README](https://github.com/opencontainers/runc)、
[NsJail README](https://github.com/google/nsjail)、[Isolate 手册](https://raw.githubusercontent.com/ioi/isolate/master/isolate.1.txt)。
本地 go-sandbox 的参考提交及 MIT 处理沿用上文；当前没有复制或实质性派生代码。

### 进程与权限

- HTTP sandbox 保持 Go，专用非特权服务账号；不接受公网请求。新增 Go helper 仅监听本机 Unix socket，
  socket 由 systemd 管理，0600 或受控 0660，核对 SO_PEERCRED 的 UID。helper 使用 root 身份但通过
  systemd 初始 capability 白名单为 SYS_ADMIN、SETUID、SETGID、SETPCAP、CHOWN、DAC_OVERRIDE、FOWNER、KILL；其用途分别为私有挂载/namespace、身份和能力收敛、受控文件准备与失败回收，实际逐项删减验证。不授予 SYS_MODULE、SYS_PTRACE、NET_ADMIN 或 SYS_RESOURCE；所需 rlimit 上限由受审服务单元给出，不由请求抬高。
  请求不能指定宿主 UID、cgroup 路径、挂载点、rootfs 路径、任意 FD 或原始 seccomp BPF。
- helper 与 launcher 均用 Go。由标准库启动受保护 helper 二进制的专用 init/exec 模式（re-exec），
  固定可信绝对路径、root 所有、服务账号不可写；内部分支必须核验受信通道与启动状态，不能仅凭命令行选项获得宿主权限。
  namespace 创建和入组由标准库在新进程启动 Go runtime 前完成；新进程再做 mount/pivot_root、文件准备和权限收敛。
  不在 fork 后的原始子进程里运行普通 Go 代码，不使用 C/cgo、runtime 内部钩子或自写 vfork 汇编。
- 第一版明确采用特权 helper 创建 mnt/pid/net/ipc/uts/cgroup namespace，不创建 user namespace。
  用户进程以每个并发槽位独立、与宿主服务及其他槽位不同的保留 UID/GID 执行，禁止 UID/GID 0。
  清空附加组、inheritable/permitted/effective/ambient/bounding capability，设置 no_new_privs、
  禁止 core、关闭非白名单 FD，再 exec。槽位 UID 只有在任务所有后代和文件完成回收后才允许再次分配。
- 可信 Go PID 1 负责回收，通过标准库启动独立的可信 payload 启动器后，收敛到单独监督 UID、清空 capabilities，
  并使用兼容其必要 Go runtime 调用的监督策略；该身份与能力收敛必须覆盖监督进程所有线程。
  payload 启动器完成最终降权与过滤后直接 exec 用户命令，不在安装最终策略后通过 os/exec 再启动一层 Go 进程。
  它与 payload UID 不同，payload 不能伪造控制消息或杀监督进程。组终止由外部 helper 执行，
  PID 1 只报告主命令退出及回收状态；控制通道 EOF 时 PID 1 退出，外部 systemd/helper 仍负责整组清理。
- userns 路线明确暂不支持，不以自动 fallback 表达。现机 AppArmor 的非特权 userns 限制保持开启；
  后续若新增 rootless/userns 后端，独立验证 UID 映射、LSM 与挂载规则，不关闭策略来迁就实现。

### 入组、握手与文件生命周期

1. systemd 为 helper 委派 cpu/memory/pids。委派根由 systemd 管总限额；helper 移入 supervisor 叶子，
   在空内部节点下建立 jobs，再为每个执行生成不可复用的随机叶子。只管理这一棵被委派子树，
   不修改 system.slice 或 user.slice。启动恢复清理完成前不接请求。
2. 在用户代码执行前建立全新叶子，写入并读回 cpu.max、memory.max、memory.swap.max=0、
   memory.oom.group=1、pids.max。先开监测和启动期限，再用 Go SysProcAttr.UseCgroupFD/CgroupFD
   加 namespace flags 启动可信 launcher。首版要求 clone3/CLONE_INTO_CGROUP；ENOSYS/EPERM 或
   入组失败时明确拒绝，不添加后补 cgroup.procs 的兼容分支。Go 的受维护标准库内部实现不拷入项目。
3. launcher 将挂载传播设为 private，准备只读版本化 rootfs 和独立 tmpfs 工作区（size 与 nr_inodes
   同时限制），通过 pivot_root 脱离旧根并关闭旧根句柄。仅有新 PID namespace 的 proc 和固定最小设备，
   不挂宿主 /usr、home、socket、cgroupfs、测试答案。工具链 rootfs 由部署任务制作和校验，不依赖宿主 g++。
4. 输入是有界字节流，由已入组的可信准备进程写入工作区，避免输入 tmpfs 页被错误记到控制服务。
   文件名经相对路径校验，再用受信 dirfd + openat2 的 BENEATH/NO_SYMLINKS/NO_MAGICLINKS 解析，
   在工作区范围内限制跨挂载；创建用 O_EXCL/O_NOFOLLOW，拒绝已有条目及非普通文件。
   不把输入路径拼成宿主可自由访问的路径。输入总量、条目数、单文件量及控制消息长度均有节点上限。
5. helper 保持仅由可信 launcher 传来的工作区 dirfd/挂载 namespace 句柄，用于任务退出后的受控产物读取。
   它们不交给 payload；payload 只继承标准输入输出及 exec 前会关闭的错误管道。工作区句柄有效期
   不能依赖主程序或 PID 1 仍存活，否则 cgroup.kill 后无法安全取回编译产物。
6. READY 只由可信 launcher 发出，表示入组、根目录、文件、权限准备已完成；payload 在最终降权与
   seccomp 完成后等待 GO，helper 校验启动状态和期限后才允许 exec。执行失败通过 CLOEXEC 错误管道
   报告 errno；EOF 结合监督消息、wait 事实判断，不能单凭 EOF 把崩溃当 exec 成功。
7. 主命令退出后先报告可信 wait 事实，helper 立即执行 cgroup.kill 清除所有后台后代；超时、取消、
   OOM、输出溢出及握手断开走同一清理链。等待 populated=0，并 reap 可信直接子进程，读取最终
   cpu.stat/memory.peak/memory.events。即使正常 exit 0 也必须完成这些步骤。
8. 全部进程退出后才读取允许的产物：openat2 + fstat 普通文件、nlink=1、大小/数量/总量上限，
   不跟随 symlink 或 magic-link；阻止硬链接，拒绝目录、FIFO、socket、设备。读取过程有界并保留 FD，
   不做“检查字符串后重新 open”。再关闭 namespace/dirfd、卸载、删除目录与 cgroup；读取失败也回收。
   私有挂载持有者、打开的 FD 和残留页必须全部纳入泄漏测试。
9. 清理使用独立有期限的上下文；失败将槽位隔离并停止新任务，不能丢弃 Reset/Close 错误。
   helper 崩溃由 systemd KillMode=control-group 清理其委派子树，重启只扫描 root 所有且带本项目身份
   的目录和组，遇到未知所有权时拒绝启动。不能依据客户端提供的路径递归删除。

上述 Go 入组能力依据 [Go exec_linux.go](https://go.dev/src/syscall/exec_linux.go)；
委派与 supervisor 叶子布局依据 [systemd cgroup delegation](https://systemd.io/CGROUP_DELEGATION/)；
文件解析依据 [openat2 手册](https://man7.org/linux/man-pages/man2/openat2.2.html)。实际 Go 版本、内核返回码与失败链由后续测试记录。

### seccomp 与支持条件

第一版只启用 x86_64 原生 ABI，必须验证 audit architecture，拒绝 x32/其他 ABI；arm64 待独立策略和真机测试。
策略通过纯 Go 的受审 BPF 构造和 Linux syscall 接口加载，不依赖 libseccomp 的 C 库或 cgo 绑定。
优先标准库；确需纯 Go syscall/BPF 支持库时固定版本并说明理由。策略文件仅 root 可写，规则生成、架构检查或加载失败均拒绝启动。
不依赖 kernel config 存在就跳过加载失败；实际允许的系统调用清单在 launcher 任务中以版本化策略文件成为唯一真源。

提供监督、工具链、普通命令三种本地执行策略。工具链由固定 rootfs 内受信可执行路径映射选择，
不增加“判题/编译”业务字段，不允许请求自选更宽特权。即使通过编译器参数启动用户程序，
也必须受同样的宿主文件、网络、权限和组限额约束。

基础候选允许普通文件 I/O、mmap/brk、信号、时钟、futex、动态链接及已验证的线程/子进程调用；
clone 参数掩码禁止所有 namespace 创建及 ptrace 类 flags，clone3 在 payload 策略中返回 ENOSYS，
仅在已证明 libc 能回退到受限 clone 的工具链上启用。mount/unshare/setns/ptrace/bpf/perf/
内核管理、模块加载、keyring、io_uring 和网络建连默认拒绝；具体正常程序兼容性必须逐项测试，
禁止通过自动学习后放行所有调用来“修复”失败。seccomp 违规保留 SIGSYS 或拒绝事实，不映射为 TLE。

能力矩阵包含 cgroup v2 三控制器、所需非根接口、clone3 入组、openat2、PID/mount/net/ipc/uts/cgroup
namespace、cap 降权、no_new_privs 和 seccomp 的实际成功。接口盘点只把测试候选标为“待验证”；
缺任一必需行为则正式模式拒绝。没有 systemd 的 Linux 保留为待托管适配，不宣称内核不支持。

### 计量、状态与节点上限

- 时间口径为新建任务组的 user+system 总 CPU（含可信 Go 启动/监督的实际开销）与全部后代，usage_usec
  换算 ns，不从时间里减固定常数；墙钟以 launcher 启动前到组清空为界，产物交付另设内部期限。
  cpu.stat 最终读取失败是平台错误，不能使用 wait4 顶替。
- 初始实验参数：CPU 采样 5ms、cpu.max 周期 10ms/配额 10ms，限制平均一核；瞬时可在多核运行，
  所以误差评估必须包含在线 CPU 数、采样延迟、配额突发、调度延迟和 kill 完成耗时。
  1 秒预算目标仍为 max(50ms,5%预算)，这不是硬实时保证。高并发下不达标时降低并发或调整方案，
  不能只放大显示阈值。CPU=0、输出=0 与字段缺省的语义先在契约任务中区分。
- 内存峰值使用新建组的 memory.peak，包含组内记账的 tmpfs/文件缓存及启动开销；共享只读 rootfs
  页的跨组记账会受热缓存影响，记录冷/热基线并重新校准，不能声称等价于纯匿名 RSS。
  OOM 使用本次 memory.events 差值（oom/oom_kill）及终止原因。只发生 memory.max 命中不能单独推断 MLE。
- 平台/启动/清理/计量错误优先 InternalError；取消保存取消事实，当前 wire 状态暂映射 InternalError，
  不伪装为时间超限。其次按串行记录的主动终止原因映射 CPU/墙钟→TLE、输出→OLE；
  无主动原因时 OOM kill 证据→MLE，然后按真实信号/退出码。并发事件保留全部内部事实，
  OOM 被监测前恰逢 CPU/输出终止时以已记录主动原因优先；最终 CPU 达预算也需覆盖采样间隙内退出。
  策略加载失败是平台错误；程序触发规则产生的信号属于 Signalled。
- 初始独立测试配置建议：节点任务总内存 2 GiB、任务总 pids 256、CPU 平均两核，并发 1、最多排队 2；
  HTTP 服务 256 MiB、helper supervisor 256 MiB 各自有界，留出宿主和代理余量。
  单次编译上限 768 MiB、运行按请求且不超过节点单任务上限；工作区初值 128 MiB/4096 inodes、
  单 stdout/stderr 节点硬上限 1 MiB、内联产物总量 1 MiB、artifact 总量 64 MiB、输入总量 64 MiB。
  这些是待测配置值，尚未部署，后续并发测试记录配置变化和容量依据；不改用户题目预算冒充校准。
- 节点队列、HTTP body、字段数量、helper 消息、输出 drain、blob 存储总量与保留期限都需有界。
  当前 pool 固定 NewHost、defer put 忽略回收错误，runner 忽略 Wait 错误、outputs 使用 io.ReadAll，
  Usage 缺少主动终止/OOM事实：后续接线任务必须修机制，不能只改 classify 的显示。

### 契约影响与后续范围

只读审查发现 contracts/run.schema.json 将 cpuNs 描述为“用户 CPU 时间”，与拟采用的总 CPU 不符；
Limits 又允许省略但现有 Go 类型抹平了显式 0。后续先设独立契约任务，明确总 CPU/组峰值、
缺省与 0、取消和信号口径，并同步类型与对齐测试，再实现接线。
不擅自添加 verdict 或绕过 contracts；若实施发现需要新增公开状态，再先更新本 WORK 设计。

当前只读任务与未来实施的分界明确：本轮可以完成本节设计与 TASK 文档；后续任务保持 todo，
其代码、构建依赖、远端临时资源及部署边界由人看到文档后明确允许，TASK-093 本身不会越界。

## 用户确认的纯 Go 路线修订（2026-09-09）

用户在比较 C launcher 与纯 Go 后明确要求“暂时就先按照纯 go 的方案来”。本节及上文更新后的进程方案
替代此前 C launcher 的实施提议；该选择已确认，纯 Go 启动链的正确性尚未运行验证。
安全目标、helper 权限边界、首版无 userns、原子入组和失败关闭要求不变；不因不使用 C 而删除安全步骤。

TASK-096 必须先完成最小启动链验证规格与实现，再展开完整接线，重点是：

- runtime.LockOSThread 只固定当前 goroutine，不消除其他运行时线程。最终降权、capability、
  no_new_privs、seccomp 与 exec 必须作用于同一受控执行线程，期间不解锁；监督进程的长期权限
  则须检查所有线程。不得用 GOMAXPROCS=1 冒充进程单线程。
- 准备最终参数、FD、信号状态及策略后，再进入权限和过滤收敛阶段；明确过滤安装前后允许执行的
  Go/系统调用路径。成功 exec 必须继承过滤与降权；exec 失败、运行时抢占、握手超时与父进程消失
  必须关闭任务，不能回到带特权的普通服务循环，也不能扩大用户策略来放行启动器全部运行时调用。
- 可通过专用 exec 模式替换自身成为用户程序；可信 PID 1 保留监督职责。需明确切根后如何可靠启动
  受保护的 Go exec 模式、何时关闭旧根/可执行文件/控制 FD，并实测用户程序不能继承这些入口。
  启动器的交付方式必须由服务固定，不能从用户工作区或请求指定路径加载。
- 用 payload 实际观察到的 UID/GID、全部 capability 集合、no_new_privs、seccomp、namespace 和 FD
  作为证据，并主动触发禁止调用；只检查启动器自报的 READY 不算通过。
- 测量每次 Go re-exec 和监督的 CPU、memory.peak、运行时线程峰值与冷/热启动差异。这些开销计入
  真实任务组，不能减常数或伪造资源事实。pids.max 覆盖所有线程，包括可信启动/监督线程；
  先明确 maxProcesses 与监督开销的兼容规则，不能悄悄抬高请求限额。过低预算的启动失败也要有确定结果。
- 首版必须验证 CGO_ENABLED=0 的 Linux 构建与实际启动。C++ 工具链仍作为被隔离执行的工作负载存在，
  “纯 Go”约束的是自研沙箱后端，不移除用户代码编译器。

依据：[Go 标准启动实现](https://go.dev/src/syscall/exec_linux.go)、
[LockOSThread 文档](https://pkg.go.dev/runtime#LockOSThread)、
[seccomp 继承及线程语义](https://man7.org/linux/man-pages/man2/seccomp.2.html)。
这些材料证明可评估的机制，不是本项目实现通过认证。若最小链无法同时满足不变量，记录具体失败点并重审，
不得自行切回 C 或引入 runtime 内部实现。TASK-098 仍负责完整 Linux 安全、资源、并发与故障回收验证。

## TASK-094 入口实现细化

Limits 保留现有数值字段以兼容 Go struct literal，JSON 解码额外保存每个字段是否出现；缺省在执行入口统一填入节点默认值，显式 0 原样保留。Marshal 保留字段缺省语义。Go literal 的零值默认视为省略；需显式零值时用 ExplicitLimits 构造完整限额，或在 WithDefaults 归一化后设置为 0。负数、null、未知字段及超出 int64 的数值在 JSON 边界拒绝。maxProcesses 采用非负 int32 上界以保证跨架构行为一致，包含可信启动器和用户程序的全部线程，低到无法启动时报告平台启动失败，不抬高请求值。CPU/墙钟零预算由执行入口直接返回 TLE；内存零预算返回 MLE；进程零预算拒绝启动；零输出允许无输出程序，实际产生首个字节时 OLE。

## TASK-096 通信与挂载实现细化

本轮按已授权范围实现独立 helper 入口，不接入现有 runner。公开本机协议固定版本：有界 JSON 元数据帧后紧跟按声明长度排列的输入字节流；不接收客户端 FD、宿主路径、UID 或策略。连接断开取消执行，无队列接纳超过配置并发的连接。结果先发有界事实帧，再流式交付指定产物，传输有期限。

helper 在监听前通过同一链执行已校验 rootfs 中的 `true`，检查真实启动、计量与清理；启动本身因此不是只读操作。结果产物 FD 全部关闭后发送 Completion 尾帧，客户端缺尾帧即失败，接线层须在成功确认后才发布暂存产物。init 另持有从准备阶段起生效的存活管道读端，唯一写端由 helper 持有；helper 消失时 PID 1 退出，不依赖 Go 父线程的 Pdeathsig 语义。

可信 init 使用专用 SOCK_SEQPACKET 控制通道，工作区通过 SCM_RIGHTS 只传一次目录 FD；外部 helper 不进入任务 mount namespace。挂载仅发生在新 namespace 内，私有传播，退出且全部 FD 关闭后由内核释放。工具链目录和 helper 文件必须 root 所有且组/其他用户不可写；根挂载只读且 nosuid。固定 proc 使用 hidepid=2,subset=pid，设备仅 null/zero/random/urandom；tmp 与工作区共享 size/inode 配额。payload 与 init 使用不同非 root UID，控制 FD 不跨最终 exec。

重启在接单前持有本地独占锁，只清理专用 jobs 子树中符合本组件随机组命名的叶子和 root 所有的空挂载点目录；任何未知条目、嵌套 cgroup 或清理失败均拒绝启动。helper 进程异常终止仍依赖 TASK-099 的 systemd KillMode=control-group；应用代码的退出清理不能代替服务管理器在 SIGKILL/主机故障下的托底。

## 用户确认的结构重构（2026-09-09）

用户审查现有 Container/runner 后指出新增 helper 职责重叠和工程规范问题，并明确“OK，先重构吧”。沿用原有 api → pool → runner → Container 与独立 Store：runner 负责输入引用解析、输出/产物发布及公开状态映射，Container 承接 Linux 后端；helper 仅承接特权操作、执行事实和安全资源管理。此前一次性输入/指定产物的 helper 协议不是 Container 生命周期的最终合同，TASK-097 接线前须对齐 PutFile/Start/Wait/GetFile/Reset/Close，不强迫 runner 改成第二套执行流程。

本轮 TASK-096 先重构已有执行链：准备/启动、监督、停止与等待、产物打开、最终释放显式分段；defer 仅用于局部资源兜底，不在大型 defer 内组装结果。FD 有单一关闭责任，并发关闭采用一次性所有权包装；执行错误与回收错误分别保存且保留 cause，多项失败合并。进程退出事实与主动终止原因使用具名类型；纯数据响应不拥有文件句柄。安全机制、独立 cgroup、最终采样、停止全部后代和回收失败停接单不变。

TASK-096 不修改现有 Container/runner/pool 或公开契约；TASK-097 的接线仍等待最小 Linux 链证明。重构测试证明本地可测试的所有权、取消和失败收尾顺序，不声称证明内核隔离。

第二轮探针使用Go os/exec创建后代，触发可选pidfd_open能力探测而被默认seccomp终止。将pidfd_open定义为ENOSYS拒绝，使标准库走已有fork/wait/kill路径；不放开该系统调用或pidfd权限。该返回策略与已有clone3能力拒绝一致，新增BPF分支回归并以真机后代用例验证。

C++真机夹具发现仅解包Ubuntu usr-merged软件包缺少根目录加载器别名。包锁新增显式layout=usr-merged；构建器仅为存在的usr/bin、usr/sbin、usr/lib、usr/lib64创建对应固定相对链接，冲突或未知布局拒绝。布局进入锁摘要和完整manifest，生成新rootfs版本，不修改已校验版本，也不借宿主动态加载器。

命令名称与交付文件路径分别验证：命令basename允许受限ASCII字母、数字、点、下划线、加号与连字符，以支持g++/clang++；仍拒绝斜杠、空格与shell语法，不改变文件段规则，不通过shell解释命令。


## TASK-097 接线与生命周期优化（2026-09-09）

用户明确继续接线并允许优化Container/pool。维持api→pool→runner→Container与Store边界：Container保持PutFile/Start/Wait/GetFile/Close，Spec增加需保留的输出路径，Usage增加主动终止、OOM与墙钟事实；不将helper协议交给runner。Linux适配器在服务私有目录用随机文件名暂存有界输入/产物，只用逻辑路径索引，流式调用helper；完成尾帧确认前GetFile不可用。编译与运行每次新组，暂存目录与FD由Container独占并在Close释放。

pool注入工厂，仅复用并发容量，每次新建单次Container，移除Reset/闲置容器队列；这避免旧工作区状态和坏槽位复用。排队容量有界，关闭时拒绝新请求、取消在途请求、等待Close完成，清理失败返回错误且停接单。runner保留引用解析/状态映射/Store发布，处理Wait和文件关闭错误，产物失败回滚已写ref；零值按已确认契约归一化，取消/平台失败优先于正常退出，普通SIGKILL不推断TLE/MLE。

Linux启动模式显式配置helper socket及服务私有暂存/Store目录，启动先走同一适配链执行true验证可用性，失败拒绝监听；host仅显式trusted-host开发配置。API请求体、输出、内联产物、排队、Store单blob/总量/条目/保留期均设界限。Store采用私有目录、固定ref、拒绝链接/特殊文件、并发容量预留及过期回收。部署/节点注册/校准仍由TASK-099负责，不替换当前服务。原judge输出默认值可能超过helper硬界，需在配置示例给出Linux组合，不能静默截小请求预算。


TASK-097实现细化：暂存根持有独占锁，启动恢复只接受本组件随机execution/data命名、服务所有的0700目录/0600单链接普通文件；未知项保留并拒绝启动。Store使用目录FD、NOFOLLOW/NONBLOCK和nlink校验，上传先写.pending，完整关闭后原子改名；启动只清理已确认归属的半成品。正在上传预留单blob最大容量，已unlink但未关闭的读者继续占总量。Store默认单blob64MiB、总量512MiB、4096条目、1h保留期，服务每分钟清理，Get/Put也检查到期。helper客户端暂存最多64MiB输入+64MiB产物/执行，临时FD和文件随Container关闭。HTTP默认2MiB请求体、有界handler数量与读写期限；pool默认1并发、8排队。

默认backend=linux，实际启动要求Linux/amd64、非root服务及可用helper；macOS开发须显式trusted-host。judge默认stdout/stderr各1MiB与首版helper硬界一致，示例保留显式可信开发配置，Linux部署需同步对应输出策略及新节点身份。组内memory.peak不会单独作为OOM证据；OOMKill事件与主动原因共同映射，host历史峰值路径仅留可信开发。TASK-098继续验证整条接线在Linux上的资源与安全行为，不能将本地协议替身当作内核验收。

TASK-098整链FD检查发现helper匿名pipe对留存。核对Go1.26.3的internal/poll/splice_linux.go：io.Copy从Unix socket到os.File可进入splicePipePool，由GC清理其缓存FD，不受单次执行Close约束。回到TASK-096将特权输入传输改为普通有界Read/Write复制，隐藏ReadFrom/WriteTo快路径，避免把FD回收正确性依赖运行时缓存/GC；不修改隔离策略或限额。用方法哨兵单测和同一Linux初末FD快照复验，不能直接放宽FD断言。

## 聚合OOM归因修订（2026-09-10）

TASK-098实测：每次128MiB的两个任务被共享jobs的96MiB总上限杀死，原实现仅凭叶子oom_kill计数判为MLE，错误归责用户。叶子oom_kill表示该组有进程成为受害者，不独自证明该任务触及自身上限。当前修复以叶子OOM>0且OOMKill>0作为任务OOM所需证据；OOMKill>0但OOM=0明确为平台错误，禁止清除init丢失错误或对外发布产物，Container也不能把这种受害事实标为任务OOM。正常任务OOM保留MLE，SIGKILL仍按退出事实。该修复消除本轮已复现误判，不宣称同时发生任务与祖先OOM时已获得完整因果追踪；后续节点部署仍须预留总量余量。

新增TASK-102限定helper执行收尾/测试、Container适配/测试四文件及WORK文档；不更改公开契约、judge业务或平台上限。TASK-098继续只写测试驱动，生产修复在TASK-102按已授权的状态归因/错误修复范围实施。

## 独立复核修正（2026-09-10）

独立复核发现并发payload身份仍共享，未满足每槽位独立身份的冻结要求；按原设计修复，不删减防御层。PayloadUID/GID与InitUID/GID配置为槽0基值，槽i各加2*i；配置校验所有派生UID/GID非零、32位有效、与服务及其他槽角色均不重叠，最多4槽。槽令牌携带编号，只有执行及产物FD交付回收完成后才归还；启动时逐槽真实探测。部署方须保留全部派生身份并检查既有账号/进程，本轮测试并发2使用61002～61005。

复核同时要求保留执行错误的原始errno：rlimit/close_range/seccomp失败取包装链中的syscall.Errno，非errno失败以errno0配合阶段编号区分，不伪造EPERM。额外拒绝过滤器以ENOSYS/EINVAL验证不同错误未被压成权限拒绝。新增TASK-103为这两项复核修复的精确代码边界。

## TASK-099部署与身份实施细化（2026-09-10）

用户在阅读TASK-099/100步骤后要求继续工作。本轮先完成本地部署包和节点身份；远端正式安装前提供具体清单，机器重启与现有节点切换仍另行确认。

现有ProbeEnvironment依赖容器内Python、/usr/local/bin/sandbox与/sys/fs/cgroup；精简隔离rootfs不提供这些宿主信息。Linux原生模式通过Node配置新增deploymentManifest指向root管理的部署清单，可信宿主读取实际内核/CPU/发行版与固定文件摘要，通过现有/run执行g++ --version验证工具链可用，并要求/version isolation=linux。清单绑定sandbox/helper、rootfs manifest、包锁、helper配置、systemd单元与资源设置；judge二进制仍由Node.New实际摘要绑定。保留原host开发探针，但Linux后端缺清单拒绝注册，不修改公开契约或sandbox。

部署采用版本化/var/lib/cherry-sandbox/releases/<release>和/etc/cherry-sandbox配置；三个固定服务及节点slice封顶。HTTP监听loopback，外部调用走明确的受保护连接方案；任务自身network namespace无宿主loopback访问，宿主root与运维进程属于可信域。不得宣称loopback能鉴权同宿主任意恶意进程。helper只保留实际所需capability，真实验证未完成前不标已验证。安装器先检查账号/完整UID-GID范围/路径/单元冲突，记录资源所有权；卸载停止本项目单元并保留数据与账号，不能递归删除用户数据。重启前明确恢复所有权状态，未完成清理则拒绝接单。部署脚本不得安装宿主包、关闭LSM或自动重启机器。

TASK-099启动顺序补充：Judge先成功绑定HTTP端口，再启动注册心跳，防止端口被占用时注册一个实际无法服务的endpoint。安装前探测新服务端口冲突，管理脚本启动命令或健康检查失败均停止本次单元；健康检查核对服务名称。此修订仅限cmd/judge与部署脚本，不改判题编排或公开契约。

TASK-099实机部署修订：systemd255在显式User=root、seccomp约束与NoNewPrivileges组合下，其exec-invoke的keep_seccomp_privileges路径会移除未列入ambient的CAP_SETUID；实测helper的bounding含此位，但permitted/effective缺失，payload降权返回EPERM。helper单元显式AmbientCapabilities=CAP_SETUID，仍受原十项bounding约束；不关闭NoNewPrivileges或其他加固。现有启动器在GO前对全部线程清除ambient/bounding、切UID并清空全部cap集合，必须实测确认payload与init最终权限为零。来源：[systemd v255 exec-invoke.c](https://github.com/systemd/systemd/blob/v255/src/core/exec-invoke.c)。本次尚未注册，按旧摘要核对后修订已装单元、部署清单与安装回执，保留修订前副本及新旧摘要审计；不得给已注册旧指纹原地伪造身份。

TASK-099卸载恢复补充：停止式回退仍为默认；为实测卸载后的可恢复性，uninstall在删除单元前将四个原文件保存至STATE/unit-backup，单链接root保护，摘要纳入回执。restore只接受uninstalled回执，核验所有保留文件/备份和专用账号UID/GID，拒绝已存在单元文件、drop-in、活动/外来unit；用排他创建恢复四个原文件，verify+daemon-reload后标installed，但不启动或enable。恢复遇冲突保留证据、拒绝覆盖，不递归删除目录/账号/数据。相同部署清单不变；不得把恢复当跨版本升级入口。

TASK-099正式helper崩溃发现部署就绪竞态：遗留socket inode使health.py过早判ready，而新helper仍在逐槽自检，sandbox随后connect refused。修复仅部署health.py：同时要求文件为socket，且当前/proc/net/unix中对应固定路径为监听态stream socket（SO_ACCEPTCON标志）；不建立半开协议连接占用helper槽位，不删除未知socket或缩短能力自检。增加遗留inode/非监听/错误路径/正常监听单测，重新执行helper在途崩溃与恢复。health脚本只决定服务启动排序，不改变已冻结执行策略、rootfs或资源身份；按旧回执核验并留审计更新该部署文件。

TASK-099权限核查计划：源码未发现CAP_SYS_CHROOT（仅pivot_root）、CAP_KILL（由cgroup.kill回收）或CAP_FOWNER的必需调用路径。以尚未接正式业务的新节点做串行有界对照：正常stop后，在本项目helper的/run/systemd/system精确临时drop-in里仅缩小bounding，保持NNP/其余单元设置，期间仅启动helper/sandbox、不启动judge注册；用实际C++及产物交付验证候选七项集合，再逐一去掉七项观察启动拒绝。drop-in内容与路径固定、root保护，finally只删除本次文件并恢复原单元；所有记录留本项目审计，不关闭系统策略。若七项足够，则更新正式单元与清单，使用新节点IDcherry-linux-2注册新环境，旧cherry-linux-1保持未激活/离线，不复制其标定。原ACTIVE环境不动。权限变化按执行身份变更处理，不能沿用旧指纹。

## TASK-108 首站整机重启受控恢复（2026-09-10）

后续处置：用户已明确暂不实施，本节作为留置方案保留，退出本轮验收范围；不构成开机启用、隧道改造或重启授权。当前验收范围以 IMPROVEMENT-004 的用户决定为准。

用户选择受控恢复：服务和 SSH 隧道自动恢复，数据通过现有管理入口重新部署与核验。当前三服务已运行但均未 enable。仅启用 cherry-sandbox-judge.service 的 multi-user.target 链接，利用已有 Requires/After 依赖启动 sandbox/helper；保留启动就绪检查、BindsTo 和 Restart=no，开机启动不等于运行故障后无限自动重试。安装管理脚本核验原回执和实际链接状态、记录启用变更，撤销时只恢复本次修改。单元内容、执行二进制和策略不变，启动链接不纳入执行环境身份，但必须核对实际指纹，不能靠推断复用。

本地新增项目内隧道监督入口，监督前台 ssh -N -T，通过现有认证连接私有配置指定服务器，维持 loopback 的本地 15051 与远端 18084 两个既有转发。要求 BatchMode、ExitOnForwardFailure、连接期限与 ServerAlive 探测，保留主机密钥验证；掉线后有界退避、日志限量。使用项目专属锁/PID/control socket，停止时仅处理经所有权核对的自身连接；原手工隧道须受控交接，不能杀占端口的任意进程。连接信息和运行文件留在项目 .local，不新增个人目录配置，不落管理员密码。监督进程在本机持续运行期间恢复远端重启造成的断连，不覆盖 Mac 重启/睡眠。

恢复按 SSH、服务健康、节点注册、数据就绪、真实判题分阶段验证。judge 每次启动生成新 session；Java 注册逻辑使旧 session 的数据回执失效。现有安装接口收到完整部署请求后会核验已存在数据并返回当前 session 的新回执，所以本任务复用正常管理入口重新部署既有 v4 数据，无需修改 Go/Java，也不能直接把旧回执标为有效。环境指纹未变且既有校准仍 VALID 时沿用同一环境校准；如内核或受绑定文件发生变化，则停止本任务的原身份恢复，另按新身份注册、校准与切换流程处理。

机器重启前先核对实际启动内核、无排队/在途任务和项目资源、安装回执及后台服务，正常停止本项目服务以阻止接单，再进行一次正常重启。保留系统和云厂商代理。SSH 等待最多 5 分钟，后续服务、注册和数据步骤均有期限并逐段记录；超时保留证据和拒绝接单状态，不连续重启或改安全策略。任务边界、预检、回退和验收以 TASK-108 为准，本节是待用户审核的实施材料。
