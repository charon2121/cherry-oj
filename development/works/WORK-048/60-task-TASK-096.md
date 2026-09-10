---
id: "TASK-096"
type: "task"
title: "实现特权 helper 与隔离启动清理链"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-095"]
related: []
implements: ["IMPROVEMENT-004#REQ-001", "IMPROVEMENT-004#REQ-003", "IMPROVEMENT-004#REQ-004", "IMPROVEMENT-004#AC-001", "IMPROVEMENT-004#AC-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "apps/judge-engine/internal/contract", "apps/judge-engine/internal/sandbox/cgroup", "dev-dependency/go-sandbox", "apps/judge-engine/go.mod", "apps/judge-engine/cmd/sandbox-helper", "apps/judge-engine/internal/sandbox/helper", "apps/judge-engine/internal/sandbox/launcher", "apps/judge-engine/internal/sandbox/policy", "apps/judge-engine/go.sum", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/tests", "apps/judge-engine/tests/sandbox-linux"]
write_paths: ["development/works/WORK-048", "apps/judge-engine/cmd/sandbox-helper", "apps/judge-engine/internal/sandbox/helper", "apps/judge-engine/internal/sandbox/launcher", "apps/judge-engine/internal/sandbox/policy", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/tests", "apps/judge-engine/tests/sandbox-linux", "apps/judge-engine/internal/sandbox/cgroup"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal/sandbox/runner", "apps/judge-engine/internal/sandbox/pool"]
created_at: "2026-09-09"
updated_at: "2026-09-09"
---

# TASK-096：实现特权 helper 与隔离启动清理链

## 任务目标

交付 Go helper、纯 Go re-exec 启动器、受控策略和 rootfs 构建材料，用户 exec 前完成全部隔离。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

## 可修改范围

以 write_paths 为准。按 DESIGN-042 冻结结果实现 Unix socket peer 校验、Go 标准库 CgroupFD 启动、纯 Go init/exec 专用模式、namespace/pivot_root、非特权 UID/GID、cap 清空和 seccomp。只允许新增必要 syscall 支持依赖并说明标准库不能满足的调用；不引入 C/cgo、runtime linkname 或自写 vfork 汇编。seccomp 使用纯 Go 构造与 syscall 加载，不依赖 C 库绑定。rootfs 构建固定版本、摘要、来源和许可，单独产出，不安装到远端。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

helper/launcher、固定架构策略、rootfs 构建与清单；启动/清理协议和 FD 所有权测试。

## 完成标准

- [x] 首版 x86_64、无 userns、clone3 原子入组；权限或策略加载失败均不执行用户代码。
- [x] 握手有版本/大小/期限，拒绝任意宿主路径、UID、FD、挂载参数或 BPF；验证客户端身份与通道断开。
- [x] 降权关闭全部非白名单 FD；PID 1 与 payload 身份分离；正常退出及失败均终止后代。
- [x] 工作区 size/inode 有界，输入与输出安全解析，组清空后仍能读编译产物，再释放 mount/FD。
- [x] CGO_ENABLED=0 构建成功；保留来源许可，必要纯 Go 依赖版本/摘要明确。
- [x] 最小启动链证明最终执行线程的降权、cap 清空、no_new_privs 与 seccomp 连续生效；监督进程全部线程的权限均收敛，不能把 LockOSThread 当作单线程证明。
- [x] 实测纯 Go 启动/监督的 CPU、组峰值内存和线程开销；明确低预算启动失败及 maxProcesses 兼容规则，不静默增加限额。
- [x] 切根后的受信 re-exec 入口可用且不受请求控制；最终 exec 失败、抢占/信号、握手中断时安全退出，用户程序不继承特权 FD。

## 验证

本地 Go 单测/race/vet，另以 CGO_ENABLED=0 构建 Linux 二进制（race 构建与纯 Go 交付构建分开记录）。交叉编译仅算构建。按 DESIGN-042 纯 Go 修订准备最小链用例；真实 syscall、降权与 seccomp 继承行为由 TASK-098 在明确远端边界内先做有界冒烟，通过后再扩展完整套件。

## 风险

纯 Go 仍有多线程权限、seccomp 到 exec 的连续性和额外资源开销风险；缺受控 Linux 构建条件时记录阻塞，不擅自在服务器 apt 安装。需引入上游派生实现时先补许可证。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户确认优先纯 Go；替换原 C launcher 提议，保持既有路径边界，尚未实施或验证。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。
- 2026-09-09：状态变更：todo → ready。原因：TASK-095 完成，用户已审核纯 Go 方案并允许编码
- 2026-09-09：状态变更：ready → doing。原因：开始实现纯 Go helper 与 re-exec 最小启动链

- 2026-09-09：已实现最终 exec 阶段原语与 amd64 seccomp 策略生成/TSYNC 加载；本地参数、BPF 分支测试及 Linux 纯 Go 交叉构建通过。helper 通信、namespace/rootfs、init 监督及完整清理尚未完成，任务保持 doing；以上不构成最小隔离链或 Linux 安全验证通过。
- 2026-09-09：新增 golang.org/x/sys v0.46.0（BSD-3-Clause，摘要固定在 go.sum）。标准库 syscall 未提供所需现代 close_range/seccomp 类型与常量；使用受维护纯 Go unix 包，避免自行维护系统调用 ABI 或引入 C 绑定。没有复制本地 go-sandbox 源码。

- 2026-09-09：用户明确继续补齐 helper、namespace/rootfs 与完整回收链。本轮新增独立 sandbox-helper 入口、root 管理配置校验、SO_PEERCRED/0660 socket、流式请求与产物、并发接纳上限、启动前真实能力冒烟的调用路径。现有 runner/pool 未修改。
- 2026-09-09：接通 clone3 原子入组、mount/pid/net/ipc/uts/cgroup namespace、private 挂载、只读 rootfs、pivot_root、受限 tmpfs/proc/设备、输入安全创建、init 与 payload 不同身份、全线程权限收敛后的 READY/GO、存活管道与主进程 wait 报告。错误报告保留启动阶段及最终 exec 的阶段/errno。
- 2026-09-09：统一正常退出、超时、CPU/输出超限、取消与通道失败的停止组、等待清空、最终计量、reap、受控产物读取及关闭/删除路径；遗留资源恢复仅处理所有权匹配的专用资源。回收失败停接单，产物交付需最终 Completion 确认。
- 2026-09-09：新增离线 deb rootfs 构建器、完整 manifest、权限/摘要校验和本地测试；不运行包安装脚本，不安装宿主软件。实际首站工具链包锁、完整 rootfs 产物和 Linux 运行证据尚未生成。
- 2026-09-09：全量 Go race/vet、Linux amd64 CGO_ENABLED=0 构建及 Linux 静态检查、Python rootfs 单测通过。Linux 专属文件/FD/失败回收测试仅经过编译检查，未执行。任务保持 doing，不以交叉构建替代最小链真机验证；下一轮须先明确最小真机验证与 TASK-098 完整套件的执行边界，避免将未验证链提前接入 runner。

- 2026-09-09：用户明确“OK，先重构吧”。按 DESIGN-042 结构重构补充实施，写路径不变；先拆分执行生命周期、统一 FD 所有权、分离事实响应与产物句柄、保留执行及清理错误，补所有权与故障收尾测试。现有 Container/runner/pool 仅作为职责依据，不在本任务改动。

- 2026-09-09：完成本轮结构重构：execution 持有单次执行状态，prepare/monitor/handleEvent 与 finish/wait/collectOutputs/release 分工；从大型 defer 移出结果组装与产物打开。ownedFile 保证 FD 并发关闭一次；executionResult 独占产物句柄，Result 只保留数据；客户端关闭输入/连接一次并传播错误。initSession 拆分配置、启动、放行和报告，登记/释放 FD 并保留阶段/errno；wait4 区分 EINTR、ECHILD 与其他失败。安装只读检查与启动能力冒烟也已拆开。
- 2026-09-09：增加可在 macOS 运行的生命周期故障测试，覆盖取消后独立 Stop/Close、停止与 wait 失败禁止打开产物、回收失败停用、多个错误保留、部分产物失败关闭先前句柄、未消费目录 FD 关闭、并发关闭解除阻塞输入和截断产物拒绝。全模块 race/vet 与 Linux 纯 Go构建/静态检查通过；原有 Container/runner/pool 未改，Linux syscall 用例仍未运行，任务继续 doing。
- 2026-09-09：状态变更：doing → done。原因：两轮最小Linux隔离链、全线程权限、C++编译运行及单点崩溃回收已验证，完整验证留TASK-098

## 最小 Linux 实机验证边界补充

用户在重构交付后明确允许先进行 Linux 实机验证。为解除 TASK-096 → TASK-097 → TASK-098 与最小证明之间的循环，将最小启动链测试归回当前 doing 的 TASK-096；TASK-098 仍保留完整编译/连续1000次/并发故障/独立复核。新增上述测试路径用于驱动和探针。

首站只写 cherry-sandbox-test-* 临时 systemd 单元、/run/cherry-sandbox-test-* 和 /var/lib/cherry-sandbox-test/ 下本次独占目录。外层 MemoryMax=768MiB、MemorySwapMax=0、TasksMax=192、CPUQuota=100%、RuntimeMaxSec≤180s；单次执行≤128MiB、64线程、墙钟≤5s，串行。本地构建纯 Go 静态探针和测试二进制；最小 rootfs 明确为测试夹具，不冒充正式工具链。保留系统/云代理，不安装包、不改全局策略、不重启现有服务。失败优先诊断并仅在当前实现路径修复；结束停止本次单元、核对无组/挂载/进程残留并移除本次目录。

- 实机修复边界：空组预写 cgroup.kill 导致随后 CLONE_INTO_CGROUP 子进程在 exec 前被 SIGKILL，对照用例已复现。当前 TASK-096 增加 cgroup 写路径，仅修复启动前接口探测为非破坏性可写打开/关闭，并补回归；结束时 Stop 写 kill 的语义不变。

- 首站最小实机链已执行：四组Linux测试通过；修复空组预写kill导致exec前SIGKILL的问题及OOM诊断覆盖后，7项有界行为断言通过。单元、进程、挂载、组与本次上传文件已清理；详细结果/摘要在VERIFY-049。C++、监督全部线程、完整故障链尚缺，保持doing，不提前接线或宣布支持。

## 第二轮有界验证补充

用户明确“继续验证”。沿用独立测试资源边界，补监督全部线程权限、namespace对照、进程/线程上限、主进程退出后的后台后代、连接取消与低预算拒绝，以及真实C++编译/产物再执行。依旧串行、单次墙钟≤5s、内存≤128MiB、pids≤64；外层768MiB/192任务/100%CPU。工具链只在独占测试目录下载并解包带版本/摘要的发行版包，不运行安装脚本、不安装宿主软件；Linux上的rootfs组装属于测试夹具准备，helper/探针仍本地交叉构建。下载/组装及各helper临时单元最长180s，必要时开下一批次，不放宽全局策略。完整1000次/并发/独立复核仍留TASK-098。

第二轮收尾增加一次helper SIGKILL单点检查：只用本次supervisor叶子的pidfd定位已核实exe的helper，在有界sleep请求进行时终止它，核对systemd托底清除该单元全部cgroup/后代。完整故障点矩阵与重复恢复仍留TASK-098。

- 第二轮有界实机验证完成：全线程权限/六类namespace、真实C++编译与新组运行、多进程CPU、pids/OOM、取消/后台后代、helper SIGKILL托底通过。修复pidfd可选探测、usr-merged布局和g++命令名拒绝，证据及限制见VERIFY-049。远端本次资源已核验清理；保留doing，未提前接线或代签验收。

交接核验：前七项由两轮实机、客户端/文件/策略单测及纯Go构建覆盖；末项的最终exec失败已在缺加载器时实测安全退出，通道/取消及回收故障有单测，helper SIGKILL有实机证据。全故障时序/重启/并发/重复验证仍归TASK-098，不把done当作完整安全验收。

TASK-098发现FD初末不一致，回到本任务仅修复helper输入复制：Go splice缓存pipe生命周期超出单次执行。修改helper复制函数与对应回归测试，补Linux初末FD证据；原文件/隔离/资源边界不变。
