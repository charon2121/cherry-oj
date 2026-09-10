---
id: "TASK-098"
type: "task"
title: "验证 Linux 隔离资源与故障回收并独立复核"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-097"]
related: []
implements: ["IMPROVEMENT-004#AC-001", "IMPROVEMENT-004#AC-002", "IMPROVEMENT-004#AC-003", "IMPROVEMENT-004#AC-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-044", "development/works/WORK-047", "apps/judge-engine/tests/sandbox-linux", "deploy/sandbox-linux/tests"]
write_paths: ["development/works/WORK-048", "apps/judge-engine/tests/sandbox-linux", "deploy/sandbox-linux/tests"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal", "apps/judge-engine/cmd"]
created_at: "2026-09-09"
updated_at: "2026-09-10"
---

# TASK-098：验证 Linux 隔离资源与故障回收并独立复核

## 任务目标

在受控 Linux 测试实例上给出逐项安全、计量、连续回收与故障注入证据。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

## 可修改范围

以 write_paths 为准。新增测试驱动、C++ 样例和临时 systemd 测试单元；首站只用 cherry-sandbox-test-* 单元、/run/cherry-sandbox-test-* 与 /var/lib/cherry-sandbox-test/ 下带任务标识的资源。真实行为会写远端，仅在本任务得到实施授权后进行；不执行包安装、全机重启或改全局 sysctl。工具缺失由受审构建产物交付解决。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

统一 Linux 套件、脱敏报告及独立复核结论；发现问题回到实现任务修复，本任务不直接越界改生产代码。

## 完成标准

- [x] 实际验证新建 cgroup、clone3 入组、权限降级、seccomp、namespace、委派，记录 errno 和缺能力拒绝。
- [x] 编译/动态链接正常，宿主文件/其他任务/网络/提权/mount/ptrace 访问被拒绝，文件攻击与输出攻击被限制。
- [x] 单线程和多子进程累计 CPU、OOM/swap、进程/线程限额，大输出后空程序及 1 秒 CPU 死循环回归。
- [x] 连续 1000 次后无存活/僵尸后代、挂载、FD 或 cgroup 残留，文件增长有界；必须记录初末快照。
- [x] 并发 1/2、排队取消、后台 setsid、主进程退出、launcher/helper/HTTP 崩溃、启动各握手点失败均回收。
- [x] 按机器/架构/LSM/权限输出矩阵；未提供机器保持待验证，不把 skip 算 pass。由独立复核者审查特权路径及失败链。

## 验证

先执行 TASK-096 纯 Go 最小启动链有界冒烟，检查 payload 权限/过滤继承、监督线程、失败关闭与启动开销，通过后再连续/并发/故障测试；外层 MemoryMax/TasksMax/CPUQuota 先确认生效。按 DESIGN-042 初始预算保留宿主与代理余量。记录测试命令、二进制/rootfs/策略摘要、CPU 误差和冷/热基线。

## 风险

用户确认首站专用且保留系统和云厂商代理；仍禁止未封顶 fork/OOM。缺权限/异常负载时停止，仅清理本项目资源。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。
- 2026-09-09：状态变更：todo → ready。原因：TASK-097完成，用户明确继续Linux整链验证，独立实例和资源边界已记录
- 2026-09-09：状态变更：ready → doing。原因：开始首站HTTP到helper整链、连续回收与并发有界测试
- 2026-09-10：状态变更：doing → done。原因：首站隔离资源、启动错误、文件攻击、容量并发、最终1000次与崩溃恢复通过，独立复核闭环，测试资源已清理；其他平台状态明确记录

## Linux整链接续授权（2026-09-09）

用户明确要求“记下来继续进行Linux的整链验证”。TASK-097已done，进入TASK-098，先运行HTTP→pool→runner→Container→helper的独立实例。沿用首站专用服务器、保留系统/云代理的授权；只写带work048-chain标识的临时单元及独占目录，不安装包、不重启现有服务、不切换节点。

helper外层768MiB/swap0/192任务/CPU100%，非特权HTTP实例256MiB/swap0/96任务/CPU50%，驱动128MiB/16任务/CPU50%，各批次最长180s。单请求≤128MiB、64线程、墙钟≤5s；先串行编译/执行与状态/文件攻击，再在同样封顶下连续1000次和并发1/2及取消。工具链在独占目录下载并解包，保留固定锁与摘要，不运行宿主安装或hooks。每批次完成前保存初末进程/FD/组/目录快照，停止本次服务，最后核对并清理本次资源。若发现实现问题，先记录并回到有精确写边界的实现任务修复；当前TASK-098不越界修改internal/cmd。完整故障矩阵与独立复核未执行前不得标done。

整链首次功能检查通过，但FD快照helper从11增至17，暂不开始1000次。复采确认额外匿名pipe对，核对标准库splice缓存后转TASK-096做明确复制生命周期修复，当前任务不直接越界修改internal。

原TASK-096重开被依赖检查拒绝，实际修复由新增TASK-101承担。新helper整链冒烟、连续1000次、双并发全部通过，初末FD/进程/挂载/组与目录证据见VERIFY-049。本任务仍doing，未完成的故障矩阵和独立复核不勾选完成。

## 故障验证续轮（2026-09-10）

用户要求继续工作，沿用已授权的独立实机验证范围。本轮以静态Go探针覆盖公开HTTP排队取消、HTTP/helper/namespace init定点SIGKILL及同实例重启恢复；不重复下载C++工具链、不重启宿主或已有服务。新增fault_batch.py，使用pidfd并核对exe/cgroup定位，仅杀本轮测试进程；每例最多一个执行组和4秒sleep，外层仍768MiB/192任务/CPU100%，HTTP256MiB/96任务/CPU50%，驱动封顶128MiB/16任务/150s。测试bootstrap增加显式recover模式复用已核验状态目录。所有握手点的确定性注入与跨Linux运行仍须单列证据，不能用随机杀进程冒充覆盖。

续轮最终故障套件通过：缺失命令、ENOEXEC、排队断连、运行阶段init/HTTP/helper崩溃及显式服务恢复、正常停服取消。静态夹具和完整初末快照见VERIFY-049的2026-09-10记录。首轮驱动存在就绪竞态已修正，生产拒绝服务行为未改；全握手点/独立复核尚缺，继续保持doing。

## 启动边界与文件读取验证续轮（2026-09-10）

在tests/sandbox-linux/boundary添加独立Linux集成测试，直接使用现有launcher/cgroup导出接口，不在生产启动器加入故障开关。测试驱动充当可信helper，按协议在configuration、rootfs-input、workspace、ready/GO边界主动截断/发送错误字节；每例新cgroup、64MiB/64任务/CPU100%，外层128MiB驱动和最多一个子组，总单元768MiB/192任务/CPU100%/60s。输出仅固定静态identity探针，未收到合法GO的场景断言无用户输出。另直接测试OpenOutput在恶意链接/FIFO/设备及交换路径竞态下的拒绝与FD读取稳定性，区别于上一轮“创建链接被seccomp拒绝”。只在独占测试目录、私有mount namespace与委派cgroup中运行；每例有独立回收期限，检查空组/空挂载点。

同轮追加cgroup能力拒绝验证：只对本批空jobs的subtree_control逐项禁用cpu/memory/pids，断言Manager拒绝，并在defer恢复；不修改system.slice或其他祖先。普通文件系统冒充cgroup也必须拒绝。这是实测委派缺失，不等价于旧内核缺memory.peak的运行证明。

本轮两批真实Linux测试通过：七个启动边界中断+合法GO对照、已存在恶意文件的安全读取/1000次路径交换，以及缺cpu/memory/pids委派/普通目录冒充cgroup拒绝。最终每例组为空、挂载点可删除。详细命令、摘要、计量和未覆盖故障点见VERIFY-049，仍保持doing。

## 容量饱和与总量续轮（2026-09-10）

本轮复用静态HTTP故障驱动增加capacity模式：并发2、队列4，六个sleep请求占满准入，额外请求须503；关闭连接后验证槽位/工作文件恢复。HTTP handler并发上限10，用10个声明小body但暂不发送的连接占用处理名额，第11请求须503；每连接最长测试持有不足2秒，finally关闭，不做无界慢连接攻击。另在本批helper的空jobs设置96MiB聚合memory.max/oom.group，两个单次128MiB内存探针触发本批聚合OOM，验证祖先事件、无残留及服务恢复；只改本次jobs并在finally恢复，不触及系统祖先和旧服务。外层768MiB/swap0/pids192/CPU100%，HTTP256MiB/swap0/pids96/CPU50%，驱动128MiB/swap0/pids32/CPU50%/150s。实测若暴露实现错误则回有精确边界的修复任务，不在TASK-098直接改生产代码。

容量续轮完成2槽/4队列及10个HTTP处理名额饱和与取消恢复。聚合内存测试发现并通过TASK-102修复误判MLE：修复后共享上限为平台错误，任务自身OOM仍MLE；最终实机对照和资源快照见VERIFY-049。仍保留底层故障点/独立复核等未完成项。

## 最终收束验证（2026-09-10）

用户明确要求完成TASK-098。继续在独立boundary测试子进程中以额外seccomp拒绝过滤器注入rlimit/close_range/setgroups/seccomp系统调用失败，并在真实RunExecStage的READY通道注入错误GO；过滤器只作用于测试子进程，不修改生产代码或宿主策略。每例继承本批独立cgroup且2s期限，最终payload路径固定不存在，禁止误执行宿主命令。以固定8byte stage/errno记录及exit125确认错误已沿真实链报告；配置、最终exec与控制端故障正反例一并覆盖。独立复核单独取得结论，不由实现者自签。

收束补充并发隔离对照：静态probe新增两个任务各自写private-left/right文件并互查不可见，读取各自输入；通过signal0检查本批HTTP宿主PID在任务PID namespace不可见，不发送破坏性信号。另尝试setuid(0)，必须被拒绝或SIGSYS，不能得到root。只更新测试夹具与驱动，新manifest摘要单独记录。

## 最终完成记录（2026-09-10）

最终构建通过boundary-04错误阶段/errno、fault-08并发身份与容量、chain-02 C++整链/1000次/并发2、fault-09崩溃恢复。独立复核两项P2由TASK-103修复，原复核者复查无新增阻断；验证范围与未穷举故障组合在VERIFY-049明确区分。原始日志已归档本地临时证据目录；六个本轮独占目录与六个state在检查无进程/挂载/cgroup且所有权标记匹配后清理。首站完成，其他平台按矩阵保持待验证或明确不支持，正式部署/业务闭环留TASK-099/100，未代签验收闸。
