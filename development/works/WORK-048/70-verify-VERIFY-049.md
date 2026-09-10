---
id: "VERIFY-049"
type: "verify"
title: "Linux 沙箱隔离与资源计量硬化"
status: "approved"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-093", "TASK-098", "TASK-099", "TASK-100"]
related: []
implements: []
verifies: ["IMPROVEMENT-004#AC-001", "IMPROVEMENT-004#AC-002", "IMPROVEMENT-004#AC-003", "IMPROVEMENT-004#AC-004", "IMPROVEMENT-004#AC-005", "TASK-093"]
tags: []
result: "pass"
created_at: "2026-09-09"
updated_at: "2026-09-10"
---

# VERIFY-049：Linux 沙箱隔离与资源计量硬化

## 验证对象

Linux 隔离、计量、故障回收和目标 Ubuntu 交付。

## 对应要求

IMPROVEMENT-004 AC-001～005，后续实施任务在生成时补充关联。

## 检查与结果

TASK-098首站隔离/资源/故障、1000次回收及独立复核完成；TASK-099原生部署与恢复完成；TASK-100新身份部署校准、真实自定义运行与正式提交完成。最终结果见对应收束章节及文末业务闭环。本报告按时间保留历史记录，早期“尚未实施”不表示当前状态。

## 范围检查

生产修复分别限定在对应TASK精确路径；TASK-100只增加验收夹具与WORK文档，通过既有管理入口和用户批准的审计CLI切换ACTIVE、部署、校准及发布。没有修改server/web生产代码或原测试数据ZIP，未提交推送。其他Linux与机器重启的限制见文末。

## 剩余风险

本报告 result=pass 仅覆盖用户修订后的本轮验收范围，人工验收闸未签署。共享内核不是虚拟机边界；同一时刻叶子和祖先OOM的精确因果仍有限制。实测故障和独立源码复核不能证明全部内核故障组合均安全。

## 未通过项

首站本轮验收范围无未解决测试失败或独立复核阻断项。机器重启恢复未执行，已由用户明确留置后续；不能以服务重启或卸载恢复代替机器重启证据。

## 遗留问题

其他Linux/内核尚待实机，arm64后端当前不支持；机器重启、开机自启及隧道自动重连留置 TASK-108 方案。当前自启 disabled，隧道和本机 IDEA 需持续运行；新 session 的数据就绪需通过正常管理入口重新部署核验。

## 结论

按用户 2026-09-10 明确暂缓重启恢复后的 IMPROVEMENT-004 范围，AC-001～005 均有首站证据，技术验证结果为 pass，供用户签署验收闸。该结论表示已测范围内未发现未解决的阻断问题，不表示没有任何潜在漏洞。本回合只核对既有证据与范围，没有新增实机测试；下文历史 partial/pending 结论保留其当时语境。

## 本回合设计证据

已审读本地 go-sandbox 独立仓库 0595b11cc1c170d8d94f20cd0088d8d847dfa823，未改参考库或执行其程序。设计补齐参考路径/机制/取舍表和跨 Linux 能力矩阵。仅文档检查，不声称多发行版测试通过。最终报告按每台机器的发行版、内核、架构、LSM和能力分别记录，不合并为笼统的 Linux pass。

## 用户提供测试服务器后的只读探测

2026-09-09：用户提供已配置 SSH 的测试服务器并明确授权登记与连接。主机地址仅保存于 Git 忽略的本地文件。实际系统 Ubuntu 22.04.5 LTS、5.15.0-181-generic、x86_64、KVM、systemd，4 个逻辑 CPU、3719 MiB 内存、无 swap；cgroup v2 列出 cpu/memory/pids。现有 system.slice 与 user.slice 中 cgroup.kill、memory.max、memory.swap.max、memory.oom.group、pids.max 存在，memory.peak 不存在。内核配置启用 namespace 与 seccomp/filter；LSM 包含 AppArmor。

因此目前不能直接满足方案的 memory.peak 计量前置条件，需要先设计受支持内核升级或等价计量方案并审核。仅只读连接，不创建 cgroup、不安装软件、不改系统配置、不重启，不计为隔离测试通过。

## 重装系统后的只读复查（2026-09-09）

用户重装后要求重新检查。当前为 Ubuntu 24.04.4 LTS、6.8.0-124-generic、x86_64、KVM/systemd，4 个逻辑 CPU、3723 MiB 内存、1987 MiB swap。cgroup v2，现有 system.slice/user.slice 的 memory.peak、cgroup.kill、cpu.stat、memory.max、memory.swap.max、memory.oom.group、pids.max 均存在；namespace 与 seccomp/filter 内核配置启用。

本结果替代上文 Ubuntu 22/5.15 的当前环境判断：memory.peak 缺失阻塞已解除。仍未创建 cgroup 或运行隔离实验，不能据此宣称限额和清理已验收。没有安装、改配置、重启或执行压力测试。

## TASK-093 意图闸后的只读盘点（2026-09-09）

本节为当前事实；前文“未连接”“Ubuntu 22 当前环境”等是设计/重装前历史记录。
已从 board 核验意图闸 passed，签署理由为“确认目标和验收标准，允许按 TASK-093 开展只读探测与实施边界冻结”。
用户又明确确认首站专用于 cherry-oj 测试，并要求保留系统和云厂商代理。

本地先执行 `sh -n deploy/sandbox-linux/probe.sh`，通过。通过本地已配置信任和认证的 SSH
将该脚本用标准输入交给远端 `sh -s`，没有写远端脚本文件。完整可复现命令在 deploy/sandbox-linux/README.md；
私有连接值只留项目本地记录。首次连接被本地执行沙箱网络限制拦截，随后经工具权限审查执行成功，SSH 返回 0。

实际探针时间：2026-09-09T06:13:28Z，probe_version=1，最终 `result=inventory-only`。
以下为该次输出的脱敏摘录，不是隔离测试结果：

| 项目 | 实际输出/结论 |
|---|---|
| OS / kernel / arch | Ubuntu 24.04.4 LTS / 6.8.0-124-generic / x86_64 |
| 虚拟化 / init | kvm / systemd 255 (255.4-1ubuntu8.15) |
| 资源 | 4 CPU；MemTotal=3812564 kB；MemAvailable=3259396 kB；SwapTotal/SwapFree=2035708 kB |
| 瞬时负载 | 1/5/15 分钟：0.05 / 0.07 / 0.02，仅本次快照 |
| SSH 调用者 | UID/GID=0；CapEff/CapBnd=000001ffffffffff；NoNewPrivs=0；Seccomp=0 |
| LSM | lockdown,capability,landlock,yama,apparmor；AppArmor enabled=Y |
| userns 条件 | unprivileged_userns_clone=1；apparmor_restrict_unprivileged_userns=1；max_user_namespaces=14566 |
| cgroup | cgroup2fs；根 subtree_control=cpu memory pids |
| system.slice | controllers=cpu memory pids；subtree_control=memory pids；type=domain |
| user.slice | controllers/subtree_control=cpu memory pids；type=domain |
| 两个现有非根组接口 | memory.peak、cgroup.kill、cpu.stat、cpu.max、memory.max、memory.swap.max、memory.oom.group、memory.events、pids.max、cgroup.events 存在 |
| namespace 与内核配置 | mnt/pid/net/ipc/uts/user/cgroup 接口存在；相关 namespace、cgroup、MEMCG、PIDS、SECCOMP/FILTER 配置为 y |
| 当前 SSH scope 委派 | Delegate=no；DelegateControllers 为空；不是未来 helper 服务的委派结果 |
| 已有工具 | cc/gcc/make 存在；g++、pkg-config、runc、crun、nsjail、isolate 不在 PATH |
| 共存检查 | 未见 Java、数据库或容器业务进程；有系统服务、tat_agent 和其他云厂商安全/监控进程 |
| 本项目单元 | cherry-sandbox* 单元列表为空 |

不能由 root cgroup 缺 memory.peak 推断内核缺能力；这里以非根组为准。
没有执行 clone3、openat2、setns/unshare、降权、seccomp 加载或限额写入。没有检查服务账号写权限，
因为服务账号和委派服务尚未安装。AppArmor sysctl 是策略条件，不等于实际 userns 调用已验证。
未安装编译器/库，未重启、未关闭代理或安全策略，未创建 cgroup 或运行压力测试。

## 当前跨 Linux 支持矩阵

| 发行版 / 实际内核 / 架构 | LSM / 权限条件 | 盘点证据 | 支持状态与下一步 |
|---|---|---|---|
| Ubuntu 24.04.4 / 6.8.0-124-generic / x86_64 | AppArmor 开启；root SSH；未来委派待安装验证 | 本节只读实测 | 待验证；TASK-098 执行隔离和资源套件 |
| Ubuntu 22.04.5 / 5.15.0-181-generic / x86_64（重装前） | AppArmor / root SSH | 历史只读实测，memory.peak 缺失 | 该组合不满足首版必要能力，明确不支持；启动拒绝行为尚未测试 |
| Ubuntu 其他内核版本 / x86_64、arm64 | LSM 与委派条件待机器确定 | 无运行证据 | 待验证；arm64 先补对应 ABI 策略 |
| Debian / 实际内核待定 / x86_64、arm64 | AppArmor/其他 LSM 以实机为准 | 无运行证据 | 待验证 |
| RHEL 兼容发行版 / 实际内核待定 / x86_64、arm64 | SELinux 保持启用，root/委派条件待定 | 无运行证据 | 待验证 |
| 无 systemd Linux | 内核能力与服务托管分开判断 | 无运行证据 | 执行核心和托管适配均待验证 |
| 其他架构、cgroup v1、缺必要接口或禁止入组/隔离的组合 | 首版实现不覆盖或必要条件不满足 | 不声称负向测试已通过 | 首版明确不支持 |

目前没有“已验证支持”的 Linux 组合。只交叉编译不改变本表的支持状态。
首站只读前置条件已经澄清；真实权限、启动握手、策略加载、限额与回收证据由 TASK-098 补齐。

## TASK-093 文档与源代码边界检查

新建只读探针/说明；更新 WORK-048 材料与工具生成的索引/流程，拆分 TASK-094～100，均保持 todo。
已查阅现有 Container/Usage、runner、pool、config、启动入口和 run schema；只记录后续修复范围，
没有改业务代码、契约或本地参考库。Git 中原有运行时 ZIP 保留，未提交/推送。
总体 VERIFY 仍为 pending，TASK-093 完成不代表 AC-001～005 通过。

## 变更记录

- 2026-09-09：状态变更：draft → review。原因：已记录 TASK-093 真实只读命令和结果；整体安全与业务验收仍 pending
- 2026-09-10：验收闸通过：review → approved。原因：确认当前首站沙箱硬化与业务闭环验收通过；重启恢复留置后续，接受已记录的平台及运维限制

## 本轮本地验证及流程工具限制

- `sh -n deploy/sandbox-linux/probe.sh`：通过。
- 本机 Darwin 执行 `sh deploy/sandbox-linux/probe.sh`：返回 2，输出 `result=unsupported-os`，符合探针约定。
- `scripts/work check`：401 份文档通过；仅 WORK-033 有既存状态提示，与本工作无关。
- `git diff -- apps/judge-engine apps/server apps/web contracts`：无代码差异；原有未跟踪 ZIP 保留。
- 对 WORK-048 与 deploy/sandbox-linux 扫描服务器地址、root SSH 字符串、私钥标记及模板占位标记：无命中。
- TASK-093 已为 done，TASK-094～100 为 todo；VERIFY result=pending。
- `scripts/work refresh WORK-048` 拒绝：存在已完成任务时尝试把 WORK 推进 doing，但后续任务仍为 todo，
  “实施任务”阶段尚未完成。没有为了通过刷新把未授权任务设 ready，也未修改流程工具。
  工具同时拒绝将有活跃依赖的 DESIGN/PLAN 回退 review；其 checked 沿用工具状态，不能理解为新增实施细节已被人签署。
  MEMORY 已置 review。此限制不影响文档 check 通过；后续任务获授权并按依赖推进后再刷新，若仍失败另立流程修复范围。

## 纯 Go 方案同步（2026-09-09）

用户确认优先纯 Go，本轮仅同步 DESIGN/DECISION/PLAN、TASK-093/096/098 与 MEMORY。
取消 C launcher 和 libseccomp C 绑定的实施要求，增加线程权限、过滤继承、受信 re-exec 入口及运行时开销验证项。
尚未构建或运行纯 Go 原型，未产生新的远端能力/安全结论；原有探针结果仅为历史只读盘点。

## TASK-094 实现证据

2026-09-09，Go 1.26.3 / darwin arm64：修改 run.schema.json 的资源口径、零值和整数范围，补齐 signal；新增 Limits 的存在性解码、序列化、ExplicitLimits/WithDefaults 与校验。覆盖省略、零值、null、负数、溢出、重复字段、Go literal 和转发往返。
`go test -race ./internal/contract/...` 通过。全量测试首次因默认 Go 缓存权限失败，改用 `GOCACHE=/private/tmp/cherry-oj-work048-go-cache` 并通过工具权限审查后，全量 `go test -race ./...` 和 `go vet ./...` 通过。没有 Linux 隔离运行证据；runner 行为接线仍由 TASK-097 完成。

## TASK-095 本地实现证据

新增 internal/sandbox/cgroup：校验已准备的 domain 委派子树，基于目录 FD 访问真实 cgroup2fs；独占随机组，设置并读回限额，核验新组无历史计量。CPU usage_usec 转 ns，内存只读 memory.peak；整组 kill、等待 populated=0、读取最终计量、删除组；回收失败禁止继续接单且支持关闭重试。
`GOCACHE=/private/tmp/cherry-oj-work048-go-cache go test -race ./internal/sandbox/cgroup/...`、`go vet ./...` 和 `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./internal/sandbox/cgroup/...` 通过。测试包含新组无历史污染、配置失败、kill/计量失败、populated 超时、回收隔离及并发对象回收，使用伪控制文件，不代表 Linux 隔离或限额行为通过。未写远端系统。

## TASK-096 首批实现证据（未完成）

新增 launcher 最终 exec 原语：预构造参数、设置 rlimit、非标准 FD 标记 CLOEXEC、全线程身份和 capabilities 收敛、no_new_privs、TSYNC 过滤后直接 exec；固定错误记录与 exit_group 失败退出。新增 policy 的 amd64 cBPF 构造与加载，默认拒绝，检查 ABI/x32、clone 参数、clone3 ENOSYS 和受限 prctl。策略单测用解释器检查分支，仅证明规则构造，尚未在内核加载。

本次验证环境仍为 Go 1.26.3 / darwin arm64，命令工作目录为 apps/judge-engine，均设置 `GOCACHE=/private/tmp/cherry-oj-work048-go-cache`：

- `go test -race ./...`、`go vet ./...`：全量通过（本机 HTTP 测试临时端口已通过工具权限审查）。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...`：通过。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./internal/sandbox/launcher/... ./internal/sandbox/policy/...`：通过。
- 首次 Linux 构建发现 syscall.Errno 与 nil 比较的编译错误，改为与 0 比较后复验通过。

TASK-096 保持 doing。尚缺 helper 通道、namespace/rootfs、init 监督、文件安全交付与完整回收，未接入 runner；没有 Linux 真实系统调用、纯 Go 线程权限连续性、资源开销或编译兼容性证据。不得将本地回归或交叉构建理解为已能安全运行不可信代码。未部署、未改变现有节点、未提交或推送；原有运行时 ZIP 保留。

## TASK-096 helper 与回收链编码证据

本节是上述“首批实现”之后的增量，不覆盖此前只读探测的历史事实。新增 helper 服务/客户端、固定内部流式协议、原子入组启动、namespace/rootfs/init、READY/GO 和独立存活管道、完整结束链与重启资源恢复代码；新增 rootfs 离线构建/清单工具。启动能力冒烟已写入 helper 监听前的执行路径，但本轮没有实际启动 Linux helper，也没有运行该冒烟。

本地环境 Go 1.26.3 / darwin arm64。Go 命令均在 apps/judge-engine 中执行，并设置 `GOCACHE=/private/tmp/cherry-oj-work048-go-cache`：

- `go test -race ./...`：通过；本机 Unix socket 与 HTTP 临时监听经工具权限审查运行。
- `go vet ./...`：通过。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...`：通过，包含新增 sandbox-helper。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./internal/sandbox/helper/... ./internal/sandbox/launcher/...`：通过；Linux 专属测试被编译检查，没有运行。
- 仓库根运行 `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/rootfs -p '*_test.py'`：2 项测试通过，覆盖权限收敛、硬链接转换、清单内容及特殊文件/挂载点链接拒绝。
- `gofmt -l`（helper/launcher/新入口）无输出；`git diff --check` 通过；新增代码/rootfs 材料未检出服务器地址、root SSH 或私钥标记。

本地 Go 新测试覆盖：路径越界、条目/字节上限、控制帧尾随内容与未知字段、元数据解析不吞文件流、零输出/大输出有界 drain、客户端流式交付、早期失败和取消解除阻塞输入、未授权产物拒绝、缺回收确认尾帧拒绝。macOS 初次 socket 测试遇到临时目录名称超过 Unix socket 路径上限，改用负责清理的独占短目录后复验通过；一次误在仓库根执行模块测试未找到模块，改到 Go 模块目录后通过。

Linux 专属源码测试另覆盖 openat2 拒绝 symlink/硬链接/FIFO、接收目录 FD 的 CLOEXEC、启动失败的回收顺序和失败停用语义；这些仍是待运行用例。没有实测 namespace 可见性、实际 UID/cap/NNP/seccomp、原子入组、OOM/CPU 精度、纯 Go 线程开销、1000 次清理、并发或 helper 崩溃回收。未生成首站经验证的工具链包锁，未安装 rootfs/服务，未修改远端、现有节点、server/web 或 runner/pool。TASK-096 保持 doing，VERIFY result 仍为 pending，跨 Linux 支持矩阵不变。

收尾 `scripts/work check`：401 份文档通过，仅既有 WORK-033 提示。`scripts/work refresh WORK-048` 再次被既有“doing 之前实施任务阶段未完成”约束拒绝；没有改工具、人工闸或提前把后续 TASK 置 ready 来绕过。

## TASK-096 结构重构证据

2026-09-09 用户明确授权“先重构”。本轮写入仍限 TASK-096，更新 DESIGN/PLAN/TASK 说明原有 Container 分层与后续协议对齐要求；没有将 TASK-097 置为 ready 或提前接线。

Go 1.26.3 / darwin arm64，在 apps/judge-engine 设置 `GOCACHE=/private/tmp/cherry-oj-work048-go-cache`：

- `go test -race ./internal/sandbox/helper -run 'Test(Finish|OwnedFile|Delivery)'`：通过，本地实际执行生命周期故障测试。
- `go test -race ./...`、`go vet ./...`：最终实现全量通过；本机 HTTP/Unix socket 测试通过工具权限审查执行。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...`：通过。
- `GOOS=linux GOARCH=amd64 go vet ./internal/sandbox/helper ./internal/sandbox/launcher`：通过，仅编译/静态检查 Linux 代码与专属测试。

故障覆盖包括：停止、等待及关闭失败仍执行后续回收；未确认安全时不打开/交付产物；执行错误和多个清理错误保留；第二个产物打开失败关闭第一个 FD；取消解除输入读取阻塞并等待生产者结束；并发 Close 一次；未消费控制 FD 回收；截断产物拒绝；OOM 事实不覆盖独立平台错误。真实 namespace、cgroup、降权、seccomp 与 1000 次回收未执行，支持矩阵不变。

初次未提权的 socket 测试受本地沙箱 bind 权限限制，后续经工具审查运行通过；重构中编译检查暴露的变量替换/路径错误已修复并完成复验。未修改远端、现有节点或原有 Container/runner/pool，没有提交推送，原有 ZIP 保留。

重构收尾：`git diff --check` 通过；helper/launcher 的 `gofmt -l` 无输出；`scripts/work check` 校验 401 份文档通过，仅 WORK-033 既有提示。`scripts/work refresh WORK-048` 仍因 doing 前“实施任务”阶段未完成而拒绝；未改流程工具、flow.json 或人工闸以绕过。

## TASK-096 首站最小 Linux 实机验证（2026-09-09）

用户在结构重构后明确允许先进行 Linux 实机验证。本次将最小链测试边界补入 TASK-096，未跳过 TASK-097 或把完整 TASK-098 提前标记完成。

环境：Ubuntu 24.04.4 LTS / Linux 6.8.0-124-generic / x86_64 / KVM / AppArmor；4 CPU、3723MiB 内存、1987MiB swap。开始前负载 0.01/0.02/0.04、可用内存约3181MiB；未发现新增业务，保留系统与云厂商代理。使用本地 Go 1.26.3 CGO_ENABLED=0 交叉构建；没有安装包、改全局策略、重启现有服务或替换节点。

测试夹具只有项目自编的静态 Go true/probe，不含 C++ 工具链。最终运行的 SHA-256：

- `sandbox-helper`：`f817bcaf854a2158255202d41779c68f429061a79458541d2631917ac518f3b8`
- `probe`：`c52e0b49aa92122fa0bf7cfd64a72c11f8a09cc096856866211af80cb2a65966`
- `manifest.json`：`b6917424f00896e479986be6f626cd19305e1a99cf09a00c352ac012dc1dffd2`

测试边界：独立 cherry-sandbox-test-work048-* 临时单元、独占上传目录和 /run 状态目录。实际活动 helper 单元读回 MemoryMax=805306368、TasksMax=192、CPUQuotaPerSecUSec=1s，外层 memory.swap.max=0；RuntimeMaxSec=180 和 KillMode=control-group 托底。每次请求内存64MiB、pids64、累计CPU1s、墙钟5s、stdout8192bytes、stderr4096bytes，串行。驱动使用独立128MiB/16任务/50%CPU/60s单元并降至61001；预检确认61001/61002/61003无账号或进程占用，没有新增系统账号。

### 实际运行结果

初始 Linux helper、launcher、cgroup、policy 四组测试二进制均 PASS，包含真实 openat2 文件拒绝、SCM_RIGHTS CLOEXEC 与失败启动回收用例；这批 Linux 二进制不带 race。修复后的新增回归另在本地 race 中运行，最终 Linux helper 通过下列实际行为断言。

| 探针 | 最终观测 | 结论范围 |
|---|---|---|
| identity | UID/GID 四组值均61002；CapAmb/Bnd/Eff/Inh/Prm全0；NoNewPrivs=1，Seccomp=2；ppid=1 | payload 观察到的身份与过滤继承通过；监督进程全部线程仍待独立检查 |
| 文件/FD | 宿主/etc/hostname不可见，/usr/bin写入被拒绝；FD仅匿名stdin、stdout/stderr管道与Go自身eventpoll/eventfd | 最小rootfs可见性与特权FD未泄漏探针通过，不能替代所有文件攻击测试 |
| cpu | CPUNs=1003750000，ClockNs=1064389957，客户端约1.065s，Reason=cpu | 累计1s预算本样本超出3.75ms；不能外推为最坏超限上界 |
| memory | memory.peak=67112960bytes，OOM=1，OOMKill=3，Signal=9，无平台错误 | 64MiB限制实际触发组OOM；峰值高于限制一页，不是单进程RSS |
| output | 仅保存8192bytes，OutputExceeded=true，Reason=output | 控制缓冲有界且终止整组 |
| 后续identity | 退出0，峰值3891200bytes，OOM=0 | OOM/大输出后正常程序未继承历史计量；本轮不是原历史空C++用例的完整回归 |
| network | socket syscall 后Signal=31/SIGSYS；Reason为空，OOMKill=0 | 禁止网络调用实际被过滤，未误判成TLE/MLE |

最终7次请求的 Completion 均成功，Usage.Populated 全为 false，全部断言通过。结束前 jobs 没有执行子目录；停止本次 helper 后遍历 /proc 的 exe/mountinfo 未发现本次目录引用，system.slice 无本次测试组，上传目录与6个状态目录清理完毕。系统 journal 保留系统自身审计记录，没有清空日志。

### 发现及修复

1. macOS tar 保留了本地UID，helper可信路径检查拒绝启动。仅修正本次目录所有权；复现说明改为使用 --no-same-owner，不弱化检查。
2. 对空组写 cgroup.kill 的“能力探测”会影响后续 CLONE_INTO_CGROUP：同样的 namespace flags 和宿主true，不预写kill时退出0，预写后在exec前SIGKILL；无OOM。使用独立受限 cloneprobe 双组对照复现。首站 unshare 同组合的对照成功；未将原因推给AppArmor或云代理，也未修改它们。TASK-096补充精确cgroup写路径后，改为仅O_WRONLY打开/关闭检查权限，结束时Stop仍写kill；增加禁止创建阶段发kill的回归。修复后完整helper冒烟通过。此处记录实测交互，不宣称已定位内核源码根因。
3. 新增init提前退出诊断在最终OOM采样后重新覆盖了资源事实。根据已取得的OOMKill事实避免追加这类退出诊断，独立平台错误仍保留；增加回归并在实机OOM重新通过。

诊断使用现有strace，在独立30s/16MiB文件上限单元仅跟踪项目helper；最终通过证据来自未跟踪运行。没有引入ptrace执行路线。

### 仍待验证

首站标记为“静态探针最小链已验证，完整后端支持待验证”。Ubuntu/Debian/RHEL兼容发行版与arm64仍待提供机器并运行；不宣布所有Linux支持。C++编译/动态链接、全部namespace对象比较、监督线程权限、进程/线程扩张、多进程CPU、取消与后台后代、helper崩溃/重启恢复、连续1000次和并发/故障注入、独立复核、节点部署/校准及真实提交尚未完成。TASK-096继续doing，VERIFY仍pending。

本地最终 go test -race ./...、go vet ./...、Linux amd64 CGO_ENABLED=0 go build ./... 及 Linux helper/launcher/cgroup/policy/探针静态检查通过。首次本地回归发现Linux专属cloneprobe缺平台文件后缀，改为main_linux.go后通过。未提交推送，原运行时ZIP保留。

收尾文档校验401份通过（仅WORK-033既有提示）；refresh仍因“实施任务”阶段约束拒绝，未绕过工具或人工闸。


## TASK-096 第二轮有界 Linux 实机验证（2026-09-09）

用户要求继续验证，并在 SSH 工具自动审查两次超时、命令均未执行后明确允许再次尝试；重试成功。环境仍为 Ubuntu 24.04.4 / Linux 6.8.0-124-generic / x86_64 / KVM / AppArmor，4 CPU、3723MiB 内存、1987MiB swap。开始负载0.16/0.03/0.01、可用内存3170MiB。沿用独占临时单元及目录，保留系统和云代理；helper外层768MiB、swap0、192任务、CPU100%、最长180s，每次执行≤128MiB、64线程、墙钟≤5s，串行。

本轮测试入口位于 deploy/sandbox-linux/tests。先运行静态夹具的 inspect_threads.py、extended.py，再以独立C++夹具运行 cpp.py、cpp_limits.py，最后在新单元运行 crash.py。各驱动传入本次socket或单元/目录参数，并在独立有界systemd单元中运行；普通客户端降为61001，只有线程检查和崩溃驱动保留读取/proc、定位测试helper的权限。bootstrap.py 负责委派子树及配置，不修改宿主全局控制器策略。

### 结果

| 场景 | 观测事实 | 结论与限制 |
|---|---|---|
| 全线程权限与namespace | 实查init/payload共11线程；UID分别61003/61002，全部cap字段0、NNP=1、Seccomp=2；mnt/pid/net/ipc/uts/cgroup均异于宿主且两进程共享；userns与宿主相同 | 首版不启用userns；根、proc和launcher挂载只读；读回swap0、128MiB、pids64、cpu.max=10000 10000 |
| Go后台后代/setsid | 主进程退出后约23ms完成，子进程原定sleep4s；populated=false | 后代被整体清理 |
| Go进程/线程扩张 | 进程创建被拒绝，pids事件8；线程场景事件20 | Go线程耗尽的fatal输出另触发16KiB输出上限，Reason=output，不能把它写成纯粹的进程上限状态映射 |
| 墙钟/低pids | 150ms期限实际156.875ms，Reason=wall；pids=1得到平台启动失败及pids事件 | 不擅自抬高用户预算 |
| 客户端断开 | sleep请求150ms后断开，等待150ms后下一次identity成功 | 取消后服务可用；关闭连接没有结果帧，不宣称精确取消延迟 |
| C++编译 | g++ -std=c++17 -O2 main.cpp -o program；CPU396.307ms、峰值47108096bytes、墙钟417.931ms、产物16408bytes | 输入流交付、编译隔离及产物安全读取通过 |
| C++运行 | 新组运行上述产物，CPU20.62ms、峰值3932160bytes、墙钟22.429ms，输出cherry-linux-cpp | 动态加载与编译/运行分别计量通过，尚未接入业务runner |
| C++多进程CPU | 父子共同忙循环；CPU1004049000ns、墙钟1057996443ns、Reason=cpu | 1s累计预算本次超出4.049ms，不是最坏上界 |
| 普通SIGKILL | Signal=9、Reason为空、OOMKill=0 | 没有误归因为CPU/内存超限 |
| C++后台后代 | fork+setsid子进程sleep4s；父退出后24.409ms完成 | Completion成功、populated=false |
| C++线程上限 | 57个pthread后EAGAIN，pids事件1、退出0 | pids64包括可信启动器线程，不能承诺64个用户线程 |
| C++触碰内存 | memory.peak=67108864bytes、OOM=1、OOMKill=3、Signal=9，无平台错误 | 64MiB组内OOM生效；事实供后续公开状态映射使用 |
| helper SIGKILL | sleep请求进行时核对supervisor唯一PID及exe，用pidfd定点SIGKILL；客户端失败关闭；2s内整个测试单元cgroup消失 | systemd KillMode=control-group托底通过；未覆盖重启恢复或全部故障点 |

正常响应均检查Completion及populated=false。结束后测试单元已全部消失；遍历/proc exe/mountinfo和cgroup树未发现本次进程、挂载、组。崩溃遗留一个空run目录和socket，逐项核验root所有及类型后，与本轮七个状态目录、933MiB独占上传/包/rootfs/trace目录一起清理。系统journal保留。没有替换现有节点、重启已有服务或提交推送，原ZIP未动。

### 问题及修复

- Go os/exec可选pidfd_open探测触发默认SIGSYS：策略对该调用返回ENOSYS，使标准库采用已有回退路径；未允许pidfd操作，BPF回归与实机后代测试通过。
- Ubuntu包直接解包缺usr-merged根目录别名，最终exec返回ENOENT：包锁显式layout=usr-merged，构建器添加固定相对链接、冲突拒绝、纳入摘要；生成新rootfs，不改已封存版本。
- 命令名复用文件段正则导致g++被拒绝：单独限定命令名称允许加号，文件路径规则不变；合法编译器名和路径/命令注入拒绝回归通过。
- 初次使用宿主已有APT索引下载出现旧包404；改用本次目录内的私有APT索引/cache/status并禁用宿主配置钩子，执行apt-get update及--download-only install。只下载/解包56个包，不运行安装脚本；下载前后的/var/lib/dpkg/status摘要一致。

实际包集合保存在 deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json，含公开来源、准确版本和每包SHA-256；这是通过本轮冒烟的测试夹具锁，不是已验收生产节点版本。

最终SHA-256：

- helper：f85104b091cc2cb1ce750973f21c7dfb6e0b52e134a0cb055351807316a63515
- 静态probe：470a504accc70aadd6773b40c9cff56bfddba85ae6301c0d25a948ebe9c07dee
- 静态manifest：f03044dacffbb372ad519bb773c0db19d434973595c07ade7644b843246cfaed
- C++包锁：4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c
- C++ rootfs-v2 manifest：ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0

本地Go1.26.3，apps/judge-engine内设置GOCACHE=/private/tmp/cherry-oj-work048-go-cache：go test -race ./...、go vet ./...、CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...、GOOS=linux GOARCH=amd64 go vet ./internal/sandbox/... ./tests/sandbox-linux/...均通过。rootfs unittest共4项通过，新增布局、冲突及未知布局拒绝覆盖。

### 剩余范围

TASK-096仍doing，VERIFY仍pending。原Container生命周期协议对齐及runner/pool接线、全故障点/重启恢复、连续1000次、并发与节点总量、独立复核、新节点部署/重新校准和真实提交闭环尚未完成。首站现在是“有界C++隔离链已实测，完整后端支持待验证”；Debian/RHEL兼容环境、其他内核和arm64仍待真实运行，不扩大支持声明。

第二轮收尾：Python脚本AST解析、下载脚本sh -n、gofmt及git diff --check通过；新增材料未检出服务器地址、root SSH或私钥标记，未生成pycache。实际包锁56项且SHA-256与远端一致。scripts/work check校验401份文档通过（仅WORK-033既有提示）；refresh仍因doing之前“实施任务”未完成而拒绝，未改工具、流程状态或人工闸绕过。


## TASK-097 Container/pool接线与服务资源边界（2026-09-09）

用户明确“继续接线，目前的Container、pool的设计也可以进行优化”。先补DESIGN/PLAN接线边界，并核对TASK-096两轮最小链证据后将其done；TASK-097按依赖进入ready/doing。本轮只改其声明的container/runner/pool/store/api/config/cmd及配置示例，helper执行核心、runner之外的judge、server/web与公开契约均未新增修改。此前未提交的契约与其他任务代码保留。

完成的代码事实：

- Container采用单次PutFile/Start/Wait/GetFile/Close。Spec声明需要保留的产物，适配器在服务私有随机文件中流式暂存并调用helper；没有Complete尾帧或传输失败时禁止GetFile。runner保留ref解析、发布与状态映射，既有judge编译和运行调用都经过同一pool工厂。移除Reset和闲置Container队列，没有复用历史cgroup。
- pool配置工厂、正数并发与有限队列；关闭取消排队/在途执行，等待容器关闭；失败不返回成功，撤回已存产物并停止接纳。异常panic也有Close兜底。终止、退出、OOM、Wait/关闭错误不再互相覆盖；普通SIGKILL保持Signalled，显式零CPU/墙钟、内存、pids按契约处理，零输出不再默认放大。
- Store独占目录锁、固定ref、目录FD、普通单链接文件校验；总量/条目/单项/TTL约束、上传预留、半成品恢复与已删除但仍打开文件的记账。暂存根也有独占锁，重启校验已知普通文件后回收，未知条目不删除。
- API拒绝超大/未知/尾随JSON，HTTP处理数量和读写期限有界；版本端点报告实际选择的后端。默认linux，未知平台/架构、root服务、helper不可用时拒绝监听；trusted-host必须显式选择。新默认judge输出各1MiB，已有显式更大配置须在Linux节点部署时修改，不能把请求静默截小。

新增验证：真实Unix socket客户端/服务替身覆盖输入流和Completion缺失；完整pool→runner→Container→Store链验证产物ref进入下一次执行。另测断连取消、重复Start拒绝、工作区清理/独占/恢复/未知条目、排队饱和、shutdown与清理失败停接单、panic回收、零值和状态优先级、产物发布失败回滚、Store上限/过期/在途读者/链接与FIFO/半成品恢复，以及API边界和配置拒绝。测试替身没有真实运行C++或Linux隔离。

回归发现并修复：macOS短命令已退出时，输出超限的迟到kill会返回EPERM，现核对目标进程组确已消失才视为取消完成，真实取消错误保留；pool关闭的AfterFunc取消异步执行，曾让已排队请求在令牌释放后启动，增加对池自身取消状态的同步检查。相关关闭用例使用race连续20次通过。旧Reset复用及parallelism=0测试按新单次/显式零值合同替换；测试目录改为主动新建0700子目录，未弱化安全检查。

本地环境Go1.26.3/darwin arm64，命令在apps/judge-engine运行并设置GOCACHE=/private/tmp/cherry-oj-work048-go-cache。全模块go test -race ./...通过；本地HTTP/Unix socket测试经工具权限审查。go vet ./...、CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...及GOOS=linux GOARCH=amd64 go vet ./internal/sandbox/... ./cmd/sandbox/...通过。交叉构建仅算构建，接线后的Linux实机回归、1000次/并发故障和独立复核仍在TASK-098。

本轮未连接或修改远端，未启动/重启用户服务，未迁移旧Store目录、部署节点、复制标定或改环境身份。原ZIP和既有未提交修改保留，无commit/push。TASK-097技术完成不等于WORK验收，验收闸仍pending。

TASK-097收尾：最终全模块race（含panic关闭兜底）通过；gofmt无输出、git diff --check通过，新增路径未检出远端地址/SSH或私钥标记。TASK-097通过工具置done，401份文档校验通过（WORK-033既有提示）；refresh仍因doing之前“实施任务”阶段未完成而拒绝，未提前推进TASK-098～100或修改人工闸来绕过。

## TASK-098 HTTP整链、1000次回收与双并发（2026-09-09）

本轮按用户“记下来继续进行Linux的整链验证”推进。环境为Ubuntu24.04.4 / Linux6.8.0-124-generic / x86_64 / AppArmor，专用测试服务器，保留系统和云代理。预检无Java、数据库或容器业务，空闲内存约3170MiB；使用独占work048-chain目录和临时systemd单元，未替换现有节点。

运行路径为公开HTTP /run → pool → runner → 单次Container → helper → namespace/cgroup执行；先隔离g++编译并发布program ref，后续请求通过同一ref运行，每次新建执行组。并非Java调度、judge比对或浏览器的业务闭环。

复现驱动为deploy/sandbox-linux/tests/chain_batch.py与http_chain.py；准备固定C++ rootfs后依次执行 `python3 <测试目录>/chain_batch.py <测试目录> smoke`、`repeat`、`concurrency`。helper封顶768MiB/swap0/pids192/CPU100%，HTTP封顶256MiB/swap0/pids96/CPU50%，驱动128MiB/pids16/CPU50%；批次最长180s，驱动150s。实查systemd资源属性。HTTP使用现有setpriv降到61001，不创建账号；逐批读回全部UID=61001、所有cap字段0、NNP=1。单次请求最多128MiB、64线程、5s墙钟。

### 实测结果

| 场景 | 实测 | 结论边界 |
|---|---|---|
| 隔离编译/动态运行及stdin | 编译CPU149.529ms、峰值15052800bytes；echo输出18bytes | HTTP输入、编译、产物ref与再次运行贯通 |
| 单进程/父子共同CPU死循环 | 1s预算分别计量1002.490ms/1007.217ms，墙钟1067.799ms/1073.299ms，均TLE | 本次超限误差2.490ms/7.217ms，不是最坏上界 |
| 64MiB触碰内存 | MLE，memoryBytes=67108864，signal9 | 使用组峰值和OOM事实 |
| 输出超限后空程序 | OLE且stdout恰8192bytes；随后空程序OK，峰值4329472bytes | 历史大输出没有污染下一次内存计量 |
| 普通signal/非零退出 | 自发SIGKILL为Signalled/9；exit7为NonzeroExitStatus/7 | 未误映射TLE/MLE |
| 后台fork+setsid | 子进程原定sleep4s，父退出后18.487ms结束 | 初末快照无残留后代 |
| pthread扩张 | 创建失败后受控退出并输出denied | 64组内任务包含启动器线程 |
| 150ms墙钟 | 156.473ms结束，TLE | CPU和墙钟独立限制 |
| 网络/mount/ptrace | SIGSYS31 | 对应syscall被seccomp拒绝 |
| 宿主文件 | 无法读取宿主/etc/hostname | 仅覆盖本夹具路径 |
| symlink/magic-link/硬链接 | 创建链接阶段SIGSYS31，无发布产物 | 不冒充已创建恶意文件后的TOCTOU读取测试 |
| 显式零限制 | CPU/墙钟为TLE、内存MLE、pids为InternalError；零输出空程序OK、写输出OLE且返回0bytes | 没有静默恢复默认值 |
| HTTP实际断连 | sleep请求150ms后关闭连接，等待300ms，随后空程序OK | 无结果帧，未宣称精确取消延迟 |
| 连续1000次 | 全部OK，总22.975s；墙钟中位20.541ms、最大49.072ms；CPU中位16.998ms；最大峰值4435968bytes | 串行空C++任务，不能当作复杂任务吞吐承诺 |
| 双并发 | 两个sleep2s请求总2.038s，采样执行组峰值2 | 两次独立执行真正重叠，完成后两组均消失 |

smoke/repeat/concurrency三批均保留初末快照：helper FD为9→9，HTTP FD为11→11，FD目标一致；jobs为空、work仅.lock，blob仅.lock和本批唯一编译产物，payload/init UID进程（含僵尸）为空、宿主mountinfo无本次引用。驱动finally删除产物ref，批次finally停止服务并断言单元cgroup消失。1000次通过不覆盖全部故障恢复条件，TASK-098仍doing。

### 整链暴露的问题与TASK-101修复

最初HTTP启动使用systemd User=未注册数字身份返回217/USER，改为现有setpriv并逐项核验身份/权限；不修改系统账号。链接测试最初期待WorkspaceError，实测创建syscall即SIGSYS，断言改为精确Signalled/31且无产物，不放宽策略。

旧helper在功能测试全部通过后FD从11升到17，200ms后仍保持；逐FD检查为三个匿名pipe对。核对本机Go1.26.3标准库internal/poll/splice_linux.go：socket→File复制的splicePipePool通过GC清理缓存pipe。这不是已经证明的永久泄漏，但不能提供逐次明确释放的FD生命周期。没有增加FD容忍阈值或强制GC掩盖问题。

原计划返回TASK-096，流程工具拒绝重开已有完成依赖的任务；保留其done历史，单列精确三个helper文件写边界的TASK-101。copyInput包装Reader/Writer隐藏可选快路径，以固定大小普通复制替代输入io.CopyN快路径；精确字节数、短读和IO错误语义不变。增加快路径哨兵、短读、写失败测试。新helper在同一冒烟以及1000次/双并发均维持9→9。

最终SHA-256：sandbox `20b37fc11d60bcb0b4abe865df730f8e4fa5006b5c6e28022f0c6be1353e0945`；helper `721a2c6ad8217eead4ba38cf39edcbbc6eef6a77095b97b4a38a165afdcaa86e`；包锁 `4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c`；rootfs manifest `ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0`。私有APT仅下载/解包56个包，新锁与上轮逐字节一致，宿主dpkg状态摘要不变。

本地Go1.26.3/darwin arm64：全模块 `go test -race ./...`、`go vet ./...`、`CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...`通过，GOCACHE沿用本工作临时目录。初次受限测试因Unix socket bind EPERM失败，经工具权限审查重新执行后通过；没有将该失败隐藏成测试skip。

剩余：完整排队取消/启动握手故障/launcher与HTTP崩溃/重启恢复矩阵、节点总量压力和独立复核仍待完成；TASK-099部署、注册与重新校准和TASK-100真实提交/自定义运行尚未执行。Ubuntu首站仅上述场景已验证，Debian/RHEL兼容发行版、其他内核与arm64仍待真实运行，未承诺通用Linux支持。验收闸保持pending，无commit/push。

本轮收尾：原始v5日志已保存本地临时证据目录。清理前逐项核对无本次systemd单元/cgroup，遍历/proc未发现本次exe或挂载引用以及61002/61003进程；work/blobs均只剩.lock，七个state目录均root所有、仅普通单链接lock/owner-v1。随后删除本轮656MiB独占测试目录及七个state目录，确认不存在；保留系统journal和云代理。宿主dpkg状态摘要再次校验一致。

TASK-101已done，TASK-098保持doing。Python AST、sh -n、gofmt、git diff --check通过；新增相关路径未检出远端地址/root SSH/私钥标记。scripts/work check共402份文档通过（WORK-033既有提示）；refresh仍因doing之前“实施任务”未完成拒绝，未改工具、提前推进后续任务或修改人工闸绕过。原运行时ZIP未动，未提交推送。

## TASK-098 故障回收与服务重启续轮（2026-09-10）

本轮实际Git状态仍包含此前未提交代码/文档及原ZIP；意图闸passed、验收pending，TASK-098 doing。用户“继续工作”沿用独立Linux验证授权。只新增fault_batch.py、给bootstrap.py增加显式recover参数及状态目录所有权检查，没有修改internal/cmd或其他生产路径。

首站仍为Ubuntu24.04.4 / Linux6.8.0-124-generic / x86_64；预检负载0.03/0.08/0.08，可用3152MiB，swap使用0，无旧测试单元，监听仅SSH/DNS，数字测试身份无进程。保留系统和云代理。本轮复用静态Go探针，不下载/安装软件，不重新运行C++性能测试。

### 驱动与运行边界

本地用Go1.26.3、CGO_ENABLED=0、GOOS=linux、GOARCH=amd64构建sandbox、sandbox-helper、tests/sandbox-linux/probe；prepare_fixture.py生成封存静态rootfs。helper摘要仍721a2c6ad8217eead4ba38cf39edcbbc6eef6a77095b97b4a38a165afdcaa86e，静态manifest仍f03044dacffbb372ad519bb773c0db19d434973595c07ade7644b843246cfaed；不是C++节点rootfs。

每批在全新work048-fault目录运行 `python3 <本批目录>/fault_batch.py <本批目录>`；外层systemd驱动128MiB/swap0/pids16/CPU50%/150s，helper768MiB/swap0/pids192/CPU100%/120s，HTTP256MiB/swap0/pids96/CPU50%/120s；实查相应cgroup属性。HTTP仅127.0.0.1:15051，setpriv降为61001。探针请求每次64MiB/64任务/1s CPU/5s墙钟/8KiB输出，sleep4s。只用pidfd对实查UID、exe与本批cgroup匹配的进程发送SIGKILL。

### 最终第三批结果

| 场景 | 观测事实 | 结论及限制 |
|---|---|---|
| 命令不存在 | InternalError，resolve-command errno=0 | 可信启动阶段返回错误；errno=0是当前诊断内容，不将其解释为成功 |
| 文件不是可执行格式 | InternalError，payload exec stage=6 errno=8 | exec失败上报并回收，随后identity成功 |
| 排队请求关闭连接 | 单槽先运行sleep4s，第二请求排队200ms后关闭；第一完成后identity约29.472ms返回 | 未执行第二个4s任务；组数保持一个；不把已断开请求的服务端日志HTTP500解释为客户端收到结果 |
| namespace init SIGKILL | 观察到payload后定位UID61003的init，定点强杀；InternalError，EOF/init signal killed | 没误判TLE/MLE，组/后代回收，随后identity成功；只覆盖运行阶段init崩溃，不等于所有启动握手点 |
| HTTP SIGKILL | 活跃请求断开，helper执行组消失；留下一个execution-*暂存目录 | HTTP同名临时单元重启后只剩.lock，identity成功 |
| helper SIGKILL | 请求InternalError；整个委派单元cgroup消失，遗留helper.sock和一个空run-*目录 | 显式recover重启helper清理socket/run目录，随后重启HTTP并identity成功；没有尝试让未知状态自动继续 |
| HTTP正常停服 | 在4s任务运行时stop测试HTTP；18.869ms完成，结果InternalError | 在途取消且工作文件回收；重新启动后identity成功 |

第三批总5.683s，驱动峰值3.3MiB、swap0。初末helper FD9→9、HTTP11→11（崩溃场景进程PID已变化，是重启后的基线对照）；work/blobs仅.lock，jobs为空，遍历/proc未发现61002/61003存活或僵尸，宿主mountinfo无本次挂载引用。第二批已通过核心崩溃恢复场景，总5.355s；第三批新增启动错误与正常停服后再次完整通过。

### 测试驱动修正

第一批首次把正在运行的driver自身当成单元冲突，尚未启动被测服务即失败；改为只检查helper/HTTP精确单元。第二次运行该批，前三项通过，但helper恢复后HTTP启动失败。日志明确为旧socket尚在、connect refused；HTTP按照预期拒绝监听。驱动原先用socket文件存在判断ready，改为实际Unix socket连接成功后才启动HTTP，保留生产失败关闭逻辑，没有增加固定sleep掩盖竞态。bootstrap的recover只复用root所有且不可被其他身份写入的目录；helper自己验证标记和未知条目。

这一点同时是TASK-099部署约束：仅After顺序或socket inode存在不足以证明helper已经可用，部署必须安排实际就绪检查和受控重试。当前测试只验证本次同名临时服务重启，没有机器重启、正式systemd部署或新节点注册。

### 当前覆盖与待验项

已补齐排队取消、运行阶段init/HTTP/helper崩溃、暂存和socket恢复、正常停服，以及resolve/最终exec失败回收。每个启动握手点的确定性故障注入、文件TOCTOU攻击、缺能力矩阵与节点总量压力、独立复核仍未完成；TASK-098保持doing。TASK-099/100未推进，未切换节点、复制校准或宣称多Linux全部支持。

本轮收尾：三批原始日志已存本地临时证据目录；清理前扫描全部/proc的exe/mountinfo/UID以及systemd/cgroup，未发现本次引用或payload。逐项核对三个root所有state目录只含单链接普通lock/owner-v1、标记精确匹配本次jobs、work/blobs只剩.lock，随后清理三个独占上传/rootfs/日志目录及state，确认全部不存在。保留journal及系统/云代理。Python AST、git diff --check通过，相关材料未检出服务器地址/SSH/私钥。402份文档校验通过；refresh仍受既有“实施任务”阶段约束拒绝，未改闸或绕过。原ZIP未修改，没有commit/push。

## TASK-098 启动协议边界、文件读取与缺委派拒绝（2026-09-10）

本轮仅新增tests/sandbox-linux/boundary三个Linux集成测试文件、deploy/sandbox-linux/tests/boundary_batch.py及本文档记录，没有修改生产启动器/helper。测试导入现有launcher/cgroup导出接口，直接充当可信控制端；与上一轮公开HTTP崩溃测试互补，不冒充完整HTTP链。

预检首站负载0.06/0.03/0.04，可用内存3154MiB、swap使用0，无旧测试单元，测试UID无进程。仍为Ubuntu24.04.4/Linux6.8.0-124-generic/x86_64/AppArmor。两批使用全新work048-boundary目录和独立Delegate单元，768MiB/swap0/pids192/CPU100%/60s，测试自身45s期限；每例新建64MiB/pids64/CPU100%组。第二批另对supervisor设置并读回128MiB/swap0/pids16/CPU50%。第一批只有单元总封顶，没有独立supervisor封顶，第二批已补齐后复跑通过。测试命令只有固定静态identity探针，不运行用户提供代码；此驱动没有helper累计CPU监测器，不能据此当作CPU预算验证。

构建命令在apps/judge-engine：`CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go test -c -o <目录>/boundary.test ./tests/sandbox-linux/boundary`，对应Linux go vet通过。prepare_fixture.py生成静态rootfs，远端在独立单元内运行 `python3 <目录>/boundary_batch.py <目录>`，由驱动建立本批supervisor/jobs并设置CHERRY_BOUNDARY_BASE/CHERRY_BOUNDARY_JOBS。普通go test缺少这两个显式环境变量会skip；本报告只计实机明确执行的PASS，不把skip或交叉编译算运行支持，也不宣称本轮执行了Linux race。

### 启动边界实测

| 中断点 | 结果 |
|---|---|
| 配置帧提前EOF | error/configuration，errno0 |
| 不存在的rootfs（可信测试配置） | error/rootfs-input，errno2 |
| 输入声明1byte但提前EOF | error/rootfs-input，errno0 |
| workspace事件后断开控制通道 | init在2s断言期限内退出，无用户输出 |
| ready事件后断开控制通道 | 等待50ms仍无用户输出，断开后退出 |
| ready后发送错误GO字节X | error/go-handshake，errno0，无用户输出 |
| ready后关闭liveness | init退出，无用户输出 |
| 合法GO对照 | 等待50ms无输出，发送G后收到exit0及非空identity输出 |

八例均通过Stop、Wait、Close，最终Snapshot.Populated=false，空挂载目录可直接rmdir，jobs无子组。无合法GO的七例输出均为0；固定identity程序合法GO后确实有输出，避免所有用例都没执行而误判。EOF/协议错误的errno0只是非系统调用错误的报告值。rootfs故障由可信测试配置注入，并非公开请求能指定宿主路径。

### 文件读取实测

直接调用生产OpenOutput读取已创建的文件，因此不再停留于“seccomp拒绝创建链接”：多硬链接文件（原名和别名）被链接数校验拒绝；symlink与指向/proc/self/fd/0的链接得到ELOOP；FIFO、字符设备、目录被普通文件校验拒绝；../及绝对路径被路径规则拒绝。设备夹具只在独占目录创建/dev/null对应节点，结束删除，不修改系统/dev。

成功打开安全文件并获得FD后，把路径替换成链接，读取仍为原来的safe内容。另以RENAME_EXCHANGE在安全普通文件和链接之间并发交换1000次，进行1000次OpenOutput：第二批361次读到safe、639次拒绝，没有读到攻击目标；第一批327次安全、673次拒绝。该有界竞态测试覆盖路径重绑定，不等于所有并发文件内容修改的证明；生产产物读取仍要求先清空任务组。

### 缺能力拒绝实测

普通文件系统目录被拒绝为“目录不在cgroup v2文件系统中”。只对本批无子组的jobs逐项移除cpu/memory/pids的subtree_control，分别得到明确缺对应控制器错误；每次defer恢复，最后正常Open成功。没有修改system.slice或其他祖先控制器。这覆盖缺委派，不代表旧内核缺memory.peak或clone3、LSM限制等支持矩阵已验证。

第一批文件与启动测试全部通过，总327ms；第二批增加能力拒绝和supervisor封顶后全部通过，总334ms、CPU162ms、单元报告峰值2.5MiB、swap0。最终测试二进制SHA-256：84befde276e8a9b2b35bd0b9387d40993b06a931f48fda5ff9bf5f9875e2617d；helper保持721a2c6ad8217eead4ba38cf39edcbbc6eef6a77095b97b4a38a165afdcaa86e；静态manifest保持f03044dacffbb372ad519bb773c0db19d434973595c07ade7644b843246cfaed。

剩余故障点须继续明确：payload-config/内部ready、降权/seccomp安装失败、FD交付失败和系统调用资源耗尽尚未逐点确定性注入；节点总量压力、独立复核、其他发行版/内核/arm64及TASK-099/100仍待完成。TASK-098不勾选全握手故障完成，验收保持pending。

本轮收尾：两批原始日志已保存在本地临时证据目录。扫描所有/proc的exe、mountinfo和UID，无本次引用或payload，systemd/cgroup无本次单元；两个目录仅剩明确测试文件，没有mount-/output-/files-等中间条目。已清理两个独占目录，确认不存在。gofmt、Python AST、Linux构建/vet、git diff --check及402份文档校验通过，相关材料未检出远端地址/SSH/私钥。refresh仍因“实施任务”阶段未完成拒绝，未绕过闸或修改流程工具。未改生产代码、原ZIP或现有服务，未提交推送。

## TASK-098 容量与聚合内存，TASK-102 OOM归因修复（2026-09-10）

沿用独立Linux测试授权。首站负载0.08/0.02/0.01、可用3171MiB、swap使用0，端口仅SSH/DNS，61001～61003无进程且无旧测试单元。本轮fault_batch.py新增capacity模式，静态探针与rootfs不变。使用work048-fault-04～06三批新目录；每批helper768MiB/swap0/pids192/CPU100%，HTTP256MiB/swap0/pids96/CPU50%，driver128MiB/swap0/pids32/CPU50%/150s，服务120s上限。实际命令为独立systemd单元内 `python3 <目录>/fault_batch.py <目录> capacity`，不改已有节点或系统祖先。

### 容量事实

- 并发2、队列4：发送六个sleep4s请求，实测两个执行组；第七个请求HTTP503，错误为queue is full。关闭六个连接后组全部消失，workspace清空，identity成功。
- HTTP最大处理数10：十个连接只发送小型POST头并声明64byte body，暂持200ms，不发送body；第十一个GET得到HTTP503，未产生执行组。finally关闭连接后identity成功。每连接超时2s，本测试不是无限连接或长期慢速攻击。
- 聚合上限：在本批空jobs设置memory.max=100663296bytes和memory.oom.group=1；两个请求各允许128MiB。结束后finally恢复原值，不改system.slice/其他祖先。最终本地事件max=41、oom=2、oom_group_kill=1、oom_kill=0；层级memory.events的oom_kill=4。节点总量限制实际生效，系统swap未使用。该结果覆盖测试jobs聚合限制，不代表正式节点完整容量规划已交付。

### 暴露的问题

04批在计数断言失败：误将祖先memory.events.local.oom_kill当作后代杀进程计数。本地组级OOM事件已增长；05批补读层级memory.events后确认杀死4个后代。更重要的是，05批两次请求错误返回MLE：单次允许128MiB，但撞到共享96MiB上限。不能把“未成功运行”当作正确归因而接受该结果。

按DESIGN/PLAN先补修订并创建TASK-102（依赖TASK-097），限定helper/execution.go及其测试、container/isolated.go及其测试四个文件。没有直接以TASK-098越界改生产代码，也没有重开done任务或代签闸。

helper现在在OOMKill>0但任务OOM=0时保留平台错误；只有同时存在任务OOM证据才允许将init丢失诊断归于任务OOM。Container同样要求OOM和OOMKill共同存在才标OOMKilled。新增收尾测试覆盖init丢失/未丢失两条路径，保留既有独立错误优先用例；真实Unix协议替身检查有/无任务OOM证据的适配。首轮新增测试遗漏Complete=true尾帧，被完整交付检查正确拒绝；修正替身后全模块race通过，没有放宽客户端校验。

### 修复后06批对照

| 场景 | 结果 |
|---|---|
| 同样96MiB聚合上限、两个各128MiB请求 | 两者均InternalError，保留“OOM victim without task-local OOM evidence”，没有产物 |
| 恢复聚合上限后，单任务64MiB分配 | MemoryLimitExceeded，无平台错误；组峰值67112960bytes |
| 两类OOM后下一次identity | 均OK |
| 容量饱和/handler饱和 | 仍按上述上限返回503并恢复 |
| 初末快照 | helper同PID FD9→9，HTTP同PID FD11→11；jobs为空，work/blobs仅.lock，无61002/61003存活或僵尸，mountinfo无本次引用 |

06批总1.833s，driver CPU249ms、峰值2.5MiB、swap0。共享上限误判已消除；同时发生任务自身和祖先OOM时的精确因果仍未完全追踪，不能宣称本补丁解决所有节点压力归因。

本地Go1.26.3/darwin arm64，GOCACHE沿用工作临时目录：全模块go test -race ./...、go vet ./...通过；CGO_ENABLED=0 GOOS=linux GOARCH=amd64构建sandbox/helper通过。最终sandbox SHA-256 b84c4a44ac4d4c9cc841556eb9406be630b55401ed920e352fb91ae548611f76；helper 81c2cb8d85040ec3c7f2fdd8f63353c177f0575ea3a915ebcc4bd6783ae297f2。新构建只用于测试实例，没有注册新节点或复制标定。

剩余：底层启动系统调用故障逐点注入、独立安全复核、其他Linux实机矩阵和TASK-099/100仍待完成。TASK-098保持doing；测试通过不代替验收签署。

本轮收尾：04/05/06原始日志保存在本地临时证据目录。扫描全部/proc exe/mountinfo/UID、systemd和cgroup确认无本次引用；逐项核对三个state标记精确匹配jobs，只有root所有普通单链接lock/owner-v1，work/blobs仅.lock。已删除本轮三个独占目录和state，保留journal及云代理。TASK-102 done，TASK-098 doing。gofmt、AST、git diff --check和403份文档校验通过，未检出远端地址/SSH/私钥；refresh仍受既有“实施任务”阶段约束拒绝，未修改工具或代签闸。原ZIP未动，未提交推送。

## TASK-098收束：最终构建、启动故障与独立复核（2026-09-10）

### 最终构建和复现入口

Go1.26.3/darwin arm64完成全模块`go test -race ./...`及`go vet ./...`；纯Go Linux/amd64构建和Linux相关vet通过。Linux边界测试为静态非race二进制；跨编译不计为Linux运行验证。

| 构建物 | SHA-256 |
|---|---|
| sandbox | 153db5e78bb3a57fb3e2ff816ec7b275446d8232203323801c3491ee16658fe4 |
| sandbox-helper | 498ffa2910618d2896f5d648bf43805465547725a62e9bd333d05e92b6bbcf9d |
| boundary.test | 93038854631ad2b871b05041afd40b189495022a28a94b94555e532d48be8949 |
| 新静态探针manifest | cb9192ca34b7e8ed70e5eea485bf067acc023d760ec9d8560cc9394b7ae692dc |
| C++ rootfs manifest | ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0 |

C++工具链重新在独占目录下载、解包56个包，与仓库ubuntu24-amd64-smoke.lock.json逐字比较一致；宿主dpkg状态哈希保持不变。没有宿主安装。测试仍使用前述systemd资源和时间封顶，各批swap峰值为0。

入口均在deploy/sandbox-linux/tests：`boundary_batch.py <独占目录>`、`fault_batch.py <独占目录> [capacity]`、`chain_batch.py <独占目录> smoke|repeat|concurrency`。边界二进制源在apps/judge-engine/tests/sandbox-linux/boundary；静态probe仅作隔离反例，C++编译执行走公开HTTP、pool、runner、Container、helper及artifact引用链。

### 启动与文件边界最终实测

boundary-04全部通过（总401ms）：configuration/rootfs输入/workspace/对外ready/GO/liveness的八项正反例延续前文；exec新增真实系统调用失败注入，仅在测试子进程安装拒绝过滤器，不在生产代码加入故障开关。每例新组、2秒期限、exit125，并确认空组后关闭。

| exec边界 | 固定错误记录 |
|---|---|
| 无效配置 | stage1 / EINVAL22 |
| rlimit | stage2 / EPERM1；追加EINVAL22 |
| close_range | stage3 / EPERM1；追加ENOSYS38 |
| 降权setgroups | stage4 / EPERM1 |
| seccomp安装 | stage5 / EPERM1；追加ENOSYS38 |
| 最终exec不存在 | stage6 / ENOENT2 |
| 内部READY后错误GO、控制端断开 | stage7 / EPIPE32 |

文件读取九类反例继续通过；1000次路径交换中310次安全读取、690次拒绝，无攻击目标读取。缺cpu/memory/pids委派和非cgroup文件系统均明确拒绝，控制器测试后恢复。所有用例关闭后无子组或挂载残留。

覆盖口径：外部启动握手逐边界中断、内部READY/GO及exec阶段失败已实测；配置编解码/FD交付截断和关闭失败由本地协议/生命周期测试补充，特权失败路径另由独立源码复核。没有穷举每一个supervisor系统调用的耗尽/内核失败组合，也没有将上述exec拒绝注入称作旧内核实机兼容测试。

### 最终资源、隔离与恢复实测

fault-08（2.281s）观测两槽payload UID为61002/61004、init UID为61003/61005；两任务私有文件互不可见，宿主HTTP PID不可见，setuid(0)得到SIGSYS31。并发2/队列4第七请求503，10个处理器饱和时下一连接503，断开后恢复。96MiB聚合上限的两个128MiB任务均InternalError，组级OOM事件1、后代kill5；恢复上限后单任务64MiB OOM为MLE（峰值67112960bytes）。helper同PID FD9→9、HTTP同PID11→11。

chain-02最终冒烟（4.679s）编译OK，编译CPU145.879ms、峰值15118336bytes；动态程序正常。1秒CPU预算单进程实际CPU1005.300ms/墙钟1077.806ms，多进程树1006.761ms/1062.494ms；这是此次样本误差5.300/6.761ms，监测周期与调度导致的误差不是硬实时上界。64MiB OOM正确MLE；8KiB输出限制OLE后空程序OK（峰值4075520bytes）；自行SIGKILL保持Signalled9，退出7、后台setsid后代、线程限额、宿主文件、网络、mount/ptrace、墙钟与取消全部按断言通过。

最终1000次全部OK，耗时22.6845s；执行墙钟中位20.482072ms、最大45.766674ms，CPU中位16.7635ms，最大组峰值4444160bytes。初末同PID helper FD9→9、HTTP11→11；jobs、payload/init进程（含僵尸）、挂载均为0，workspace只有.lock，blobs仅.lock与本批唯一编译产物，驱动finally删除该产物。并发2实测peakGroups=2，两次2秒sleep共2.0294s，初末同样稳定。

fault-09使用最终构建重跑全部崩溃恢复（5.572s）：命令不存在、ENOEXEC8、排队断连、init SIGKILL、HTTP SIGKILL、helper SIGKILL和显式恢复、正常停服取消均通过；正常停服18.1ms。崩溃导致的暂存目录在恢复后清空，后续identity均成功，初末FD基线9/11一致（重启场景PID发生变化，不当作同PID证据）。

### 独立复核与修复闭环

用户明确授权独立子智能体只读复核。复核者`/root/sandbox_security_review`（Boyle）检查特权路径、启动/FD/降权/过滤、cgroup与失败回收，未改文件或代做实机验证。首轮提出两项P2：并发槽共用身份偏离冻结设计；三处exec错误硬编码EPERM丢失真实原因。先建立TASK-103精确边界再修复，没有以TASK-098越界修改实现。

修复为每槽独立payload/init UID/GID、完整碰撞和范围检查、每槽监听前能力探测、完成回收后归还编号；保留包装syscall.Errno，非系统错误保留失败stage及errno0。原复核者再次检查循环变量捕获、归还时机、回收失败停止服务和错误接收语义，结论“两项P2源码修复充分，未发现新增阻断”。最终fault-08、boundary-04及1000次完成其要求的实机闭环。部署预留完整身份范围仍由TASK-099负责。

### 支持矩阵与完成范围

| 环境/实际内核/架构 | LSM与权限条件 | 状态 |
|---|---|---|
| Ubuntu24.04.4 / 6.8.0-124-generic / x86_64 | AppArmor启用，systemd委派，root helper、UID61001且cap0的HTTP，逐槽非特权任务 | 已验证支持本节TASK-098套件；正式部署仍待TASK-099 |
| 首站普通目录或缺cpu/memory/pids委派 | 同上，仅本批jobs故障注入 | 已验证明确拒绝启动 |
| Ubuntu22.04.5 / 5.15.0-181-generic / x86_64历史系统 | 只读发现缺memory.peak，重装前记录 | 明确不支持该缺能力配置；未运行完整拒绝/隔离套件 |
| 其他Ubuntu、Debian、RHEL兼容环境/实际内核待提供/x86_64 | LSM与委派条件待提供 | 待验证，不以发行版名承诺支持 |
| Linux/arm64，各发行版与LSM | 当前实现仅linux/amd64 | 当前后端明确不支持；未来实现及实机验证另行安排 |
| macOS/arm64 | host后端 | 仅可信开发，不支持不可信正式执行 |

TASK-098的首站测试和独立复核完成；其他机器未提供，不把skip算pass。TASK-099的正式systemd部署、helper最小capability、重启/回退验证、节点注册、重新校准和TASK-100真实提交/自定义运行仍待完成，WORK验收保持pending。

收尾核验：本轮boundary-03/04、fault-07/08/09、chain-02的九份原始日志保存在本地临时证据目录；清理脚本先检查全部/proc exe/mountinfo、61001～61009身份、systemd/cgroup，核对六个state仅含匹配owner-v1与单链接root锁，work/blobs仅.lock，再移除六个明确独占目录及六个state，最终断言均不存在。系统服务、云代理和journal保留。

最终本地收尾：gofmt、Python AST、git diff --check、WORK文档敏感信息检查及404份开发文档校验通过；仅保留既有WORK-033状态提示。TASK-098和TASK-103已由工具置done；refresh仍因未完成“实施任务”阶段拒绝推导WORK状态（TASK-099/100为todo），没有修改流程工具或绕过约束。全WORK复核阶段留待部署/业务任务完成后统一收束，本轮独立复核结论明确记录在TASK-098证据中。

## TASK-099本地部署准备（2026-09-10）

用户要求继续，并确认本机IDEA后端+SSH隧道。实施范围仅TASK-099允许的Node/config.node/cmd.judge和deploy/sandbox-linux、WORK文档。新增部署元数据校验覆盖root保护、文件完整性、活动发布链接、slice/服务/委派子树共24项实时限额，公开HTTP只用于/version的linux检查和隔离g++ --version；旧host探针保留开发用途。清单/hash、CPU特征或执行策略改变会生成新环境指纹，节点ID/endpoint/control token不作为新清单的执行兼容性输入。

本地全模块race/vet、Linux编译和node的Linux vet通过；新增Node测试覆盖清单缺失/错误后端、元数据与限额缺项/改变/无界/swap、rootfs/helper/策略变化的指纹、可变CPU频率不污染身份及不受保护文件拒绝。部署Python四项测试通过，覆盖渲染范围、账号分离/资源封顶、路径与公共地址拒绝、被改安装拒绝管理和卸载保留数据/账号；AST、shell语法、diff whitespace检查通过。这些是本地测试，不能替代systemd权限、真实注册或安装/恢复验证。

部署文件与具体清单见deploy/sandbox-linux/install/README.md。只读远端检查systemd255、Linux6.8.0-124-generic、可用61980MiB磁盘；拟用目录和61001～61010账号未发现冲突，当前仅SSH/DNS监听。本机Java8084和既有5051不变。没有远端安装/创建账号/服务/隧道，没有重启或节点切换。TASK-099 doing、TASK-100 todo；原测试ZIP和其他工作项不动，无提交推送。工作区同时出现其他WORK文档，校验总数415，不将它们计入本轮产出。

本轮最终产物补齐：官方归档56包逐一通过原锁SHA256。第一次在macOS bind mount构建产生3918个条目，而原生Linux tmpfs为3926个；缺失的8个条目涉及大小写不同的netfilter头文件，另261个symlink模式不同。未修改清单接受偏差。原生Linux构建再次得到已验证manifest `ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0`，以tar保留Linux语义。工具容器均独立、无网络、只读根、无capability，512/768MiB内存与64进程/CPU100%封顶，退出自动移除，现有Docker容器未改动。

完整本地候选包为deploy/sandbox-linux/.local/work048-linux-v1-bundle（22个文件及SHA256SUMS；目录被忽略，无实际token）。最终judge摘要 `b0da26eb19138f7f1237bce84c684cea4e1426f8840ed3c0b8f544f4522abf78`，sandbox `37215c94774ad73e274ecae1ce8f0ce3afc5af3baa2bad4788f85be8f9ae4504`，helper保持 `498ffa2910618d2896f5d648bf43805465547725a62e9bd333d05e92b6bbcf9d`；rootfs归档 `56c255b304dcc059a05e13f2ffc8dde0a5301d82a2485545ffed992d214fb783`。sandbox摘要因共享config结构变化而改变，应随新节点实测，不把TASK-098旧二进制证据直接当作这次正式部署通过。

追加启动回归：占用目标端口时Judge返回失败且没有注册；启动先bind再注册。管理脚本启动依赖失败也停止本次服务，健康检查核对名称。最终五项部署Python测试、六项rootfs/下载定位测试通过，Go全模块race/vet后对最后cmd/node改动再次race/vet与Linux构建通过。415份文档校验通过，仅原WORK-033提示；并行其他WORK文档编辑曾短暂影响全库校验，未修改其文件。refresh仍受既有“实施任务”阶段约束拒绝，未绕过工具。TASK-099保持doing；正式安装、systemd-analyze verify、最小权限行为、注册与恢复/卸载仍需下一步执行。

## TASK-099正式安装与注册（2026-09-10）

用户审核清单后明确“安装”。首站仍为Ubuntu24.04.4、Linux6.8.0-124-generic、x86_64、systemd255.4-1ubuntu8.15，保留AppArmor与系统/云代理。预检无同名文件、账号、服务或端口业务冲突；安装前旧节点仍由本机Docker提供5051，本机IDEA的judging-service为8084。创建清单内61001～61010专用账号、/etc/cherry-sandbox、/var/lib/cherry-sandbox及三个原生服务；只启动本项目服务，未安装宿主软件、enable、重启机器或修改全局安全策略。

原候选22文件SHA256全部匹配后安装。systemd隐式空闲slice被初版预检误认为冲突，修正为仅在无FragmentPath/DropInPaths/ControlGroup且inactive、非Transient时允许；六项Python部署测试通过。systemd-analyze verify实际通过。

首次服务链启动被helper真实能力自检拒绝，新节点当时未注册。受限strace仅作用本项目helper，输出缓冲截断为0，不追踪用户代码/凭据；发现payload setresuid返回EPERM。对照helper初始/proc状态：CapBnd=082401eb而CapPrm/CapEff=0824016b，缺CAP_SETUID。读取[systemd v255源码](https://github.com/systemd/systemd/blob/v255/src/core/exec-invoke.c)确认keep_seccomp_privileges分支会丢弃未列入ambient的SETUID。仅增加AmbientCapabilities=CAP_SETUID，未扩大十项bounding、关闭NNP或放宽payload策略；旧单元、清单、回执和新旧摘要保存在installation-revisions/001-helper-setuid，修订在首次注册前完成。

修订后helper CapPrm/Eff/Bnd均082401eb、CapAmb=80、NNP=1；sandbox UID/GID61001、judge61010，二者全部cap集合为零、NNP=1、Seccomp=2。节点总slice为1536MiB/swap0/384tasks/CPU200%，三个服务及helper的supervisor/jobs合计24项实际cpu.max、memory.max、memory.swap.max、pids.max逐一与部署清单一致。没有用配置文件存在代替实际cgroup读回。

原生验证命令在远端由独立临时测试单元运行：

```sh
systemd-run --unit=cherry-sandbox-test-work048-native --wait --pipe --collect \
  -p MemoryMax=128M -p MemorySwapMax=0 -p TasksMax=32 -p CPUQuota=50% -p RuntimeMaxSec=90s \
  /usr/bin/python3 /var/lib/cherry-sandbox/operations/verify-native.py
```

两次通过。最终一次：sandbox/judge各7个线程、任务与init共7线程；UID61002/61003各自全部res/fs UID/GID一致，五种cap集合全部零、NNP=1、Seccomp=2；mnt/pid/net/ipc/uts/cgroup与宿主不同，根、proc、launcher挂载只读。正常g++编译后以产物ref再次隔离执行，stdout匹配；编译CPU89,962,000ns、memory.peak9,859,072bytes，执行CPU23,157,000ns、memory.peak4,358,144bytes（sleep2s）。这是安装冒烟，不把单例当校准或性能基线。删除本例blob后无执行子组、61002～61009任务进程及工作文件残留。

生命周期验证用相同外层128MiB/swap0/32tasks/CPU50%/90s，逐项运行operations/verify-lifecycle.py --case helper-config、rootfs-manifest、helper-binary。每项先核验完整安装回执，正常停止本项目服务，临时移动精确文件，实际启动依赖链必须失败；finally停止单元、恢复原inode内容/路径及摘要、重新启动。三例均PASS、最终服务全部active，历次注册environmentId一致；测试不修改rootfs内容、清单身份或Java数据。这里覆盖的是缺文件启动拒绝与正常停止/恢复，不冒充正式单元的在途SIGKILL或机器重启测试。

通过本机现有`apps/server/scripts/judge-environment list`只读核对：

- 新环境cherry-linux-1：`01a089b0-1c67-7211-bda4-b1fc3da6282c`，REGISTERED，row_version=0，online_nodes=1。
- 原judge-local-2：ACTIVE，row_version=1，online_nodes=1；原退役环境仍RETIRED。没有执行switch。
- 本机`GET http://127.0.0.1:15051/version`经SSH隧道返回cherry-oj-judge；日志记录首次及三次恢复均注册同一新环境ID。
- 新三个单元is-enabled均disabled。双向隧道继续运行供联调，控制socket和连接状态保存在项目忽略目录；隧道/本机IDEA后端停止将影响新节点在线状态。

管理工具已保存至/var/lib/cherry-sandbox/operations，逐文件摘要纳入installation.json；对应新增审计002-operations。原生及生命周期日志、准备目录文件摘要在003-install-verification。私有上传token副本已删除，已装judge配置保持root:61010、0640；服务器地址、认证未复制到可提交文档。原候选归档保留历史，修订版24文件清单和独立revised归档避免把原包误认为最终包。准备目录其余产物保留供审计，没有清空系统临时目录。

本轮“安装”已完成。TASK-099保持doing：正式单元在途崩溃恢复、卸载实操、逐项capability最小集合仍待验证；HTTP信任边界为专用测试机可信宿主加loopback/SSH，未新增应用层鉴权。机器重启另需授权。TASK-100仍todo，尚未部署业务题目数据、校准或运行真实自定义/正式提交闭环；WORK验收闸未签。

本轮收尾：六项部署Python测试、Python语法、git diff --check及415份文档校验通过，保留既有WORK-033提示。refresh依然报告doing之前“实施任务”阶段未完成，未修改控制面绕过。原测试ZIP与WORK-049保留，不自动commit/push。验证日志已复制回deploy/sandbox-linux/.local/003-install-verification，临时本地token副本也已删除。

## TASK-099收束：正式服务故障、卸载恢复和能力收敛（2026-09-10）

用户自行执行原生验证PASS后明确继续补齐。本轮只操作已安装的新Linux节点，原ACTIVE环境不变。起始负载0.05/0.02/0.00、可用内存约3141MiB、swap未使用；系统和云代理继续运行。故障/卸载驱动128MiB、swap0、32tasks、CPU50%、RuntimeMax90s；能力对照同样限额、RuntimeMax120s，全部串行。测试通过Go Judge原生trial API完成临时C++编译与执行，不经过Java业务提交、不写业务库。

### 在途崩溃与修复

verify-faults.py先核对回执和空闲状态，用正常空程序取得基线指纹；再启动fork后sleep15s的程序，观测/work中的非特权payload确实执行后，用pidfd并核对exe/UID/cgroup定点SIGKILL指定服务。主程序和后代均不能靠自然退出伪装成及时回收。三例的任务进程与执行组清空；judge崩溃对外断连，sandbox/helper崩溃返回SE，未误判TLE/MLE。恢复后空程序RAN、指纹一致、工作区仅.lock；崩溃留下的本例blob经正常API删除。

首次helper故障暴露health.py遗留socket inode误判ready：新helper尚在自检，sandbox已启动并connect refused。修复为socket inode加/proc/net/unix实际监听态判断，不建立占槽连接、不绕过helper自检；正反例单测覆盖遗留inode、缺socket、错误类型/状态/路径。修复进入installation-revisions/004-readiness-restore，之后同场景通过。旧TASK-098临时实例的主动连接探测不等同正式单元原来的inode检测，保留本次新增发现。

### 卸载与恢复

manage新增显式restore。uninstall在清除单元前保存四个原单元到root保护的unit-backup，摘要纳入回执；restore只接受uninstalled回执，核验备份、保留文件和账号ID，拒绝文件/drop-in/单元冲突或备份改变，排他创建原单元并verify/reload，不启动或enable。单元验证失败只撤回本次创建且摘要吻合的文件，保留证据与数据。

verify-uninstall.py实际运行uninstall→检查→restore→start，两轮PASS（权限收敛前后各一轮）。四单元文件和空slice移除；专用账号、配置、部署文件和UID61010的0600测试数据标记内容/inode保留；恢复后原配置摘要一致、三个服务active且disabled，测试标记清理。没有重装宿主、删除业务数据或停系统代理。

本地15项部署测试通过，包含备份被改、目标单元冲突、账号改变、验证失败回滚、重复restore拒绝和就绪检测。运行时服务故障测试不等同安装器在任意文件写入点崩溃的事务保证；中断安装仍按回执拒绝并保留现场。

### 能力对照与最终新身份

源码审读未发现SYS_CHROOT/FOWNER/KILL的必要路径。verify-capabilities.py停止新节点后临时缩小helper的bounding，只启动helper/sandbox，judge保持停止，临时策略不会注册。七项集合实际通过嵌套输入g++编译、0600产物读取、fork后台sleep10s后代立即回收；逐一删除SYS_ADMIN、SETUID、SETGID、SETPCAP、CHOWN、DAC_OVERRIDE、MKNOD都拒绝启动，日志分别呈现namespace EPERM、降权失败、socket chown EPERM、rootfs EACCES/EPERM。最终驱动显式排除start-limit-hit。中间一次驱动对未加载单元reset-failed报错，已修正且最终完整重跑通过；未将该失败算PASS。

正式配置删除三项冗余权限，helper CapEff=082001c3（七项）、NNP=1；sandbox UID61001、judge UID61010，cap0/NNP1。权限收敛改变执行环境，因此以cherry-linux-2新身份注册；旧单元/清单/备份在005-minimum-capabilities审计保留。原二进制/rootfs不变，不复制旧校准、复用旧指纹或切换ACTIVE环境。

最终七项配置上的完整复验：

| 场景 | 实测结果 |
|---|---|
| judge在途SIGKILL | 断连，回收并恢复RAN；从请求启动至确认清空0.163s |
| sandbox在途SIGKILL | SE，回收并恢复RAN；同口径0.163s |
| helper在途SIGKILL | SE，依赖停服/回收后恢复RAN；同口径0.165s，无旧socket竞态 |
| 卸载→restore→start | PASS，配置/数据/账号保留、同一新清单、均active且disabled |
| 原生编译/运行/24项限额 | PASS；编译CPU93,236,000ns、内存9,900,032bytes；运行CPU19,309,000ns、内存3,944,448bytes |
| 全线程权限/隔离 | 两服务各7线程、任务/init共7线程；任务UID61002/61003、cap0/NNP1/seccomp2；六namespace不同、三挂载只读 |
| helper未授权访问 | root连接被peer身份检查关闭；judge UID直接连接被socket权限拒绝；正常sandbox链通过 |
| HTTP监听 | 15050/15051均仅127.0.0.1；仍以专用测试机可信宿主为前提，无应用层HTTP鉴权 |

一次临时鉴权检查命令因shell引号导致子Python语法错误，未计为权限拒绝；改为独立脚本并以明确PermissionError分支重测PASS。原生测试本身当次已PASS，未混同两个检查结果。

最后通过现有只读judge-environment list核对：

- cherry-linux-2：environmentId=`01a08a09-23b1-7ed5-bc0f-e03002e65e4a`，REGISTERED/row_version0/online_nodes1；最终指纹`79b8cb44ea253f47c44c2cc518fc4b0f785d9b7f69f857e110bfea9df07835bb`，三次故障恢复均一致。
- cherry-linux-1：原REGISTERED历史环境，online_nodes0，未激活；旧指纹`87332cfcb753ad729304e096f3640387b17c1f668faf6e640cea4b8b1c9a5854`未伪造覆盖。
- judge-local-2：仍ACTIVE/row_version1/online_nodes1，原退役环境状态不变，无switch。

最终无临时drop-in目录、执行组、任务、工作文件或测试blob。安装审计007-task099-final保存各批原始日志及最终单元状态，006保存驱动修订；服务与SSH隧道继续运行。源码部署包同步到最终27文件清单及work048-linux-mincaps-bundle.tar.gz，审核配置节点ID为cherry-linux-2；历史归档保留，不混作最终包。地址/认证仍只在私有材料，原ZIP与WORK-049未修改，无commit/push。

TASK-099在已授权首站范围内完成。机器重启未授权、未执行，开机自启仍disabled；其他Linux/架构保持既有矩阵状态，不承诺全Linux支持。TASK-100的数据部署、重新校准、真实自定义运行/正式提交与带审计切换尚未完成，WORK验收闸保持pending。

收尾校验：15项部署Python测试、Python语法与diff whitespace通过，415份开发文档校验通过，仅既有WORK-033提示。TASK-099已由工具置done；refresh仍因“实施任务”阶段前置约束拒绝推导（TASK-100未完成），未绕过控制面。最终日志已复制至deploy/sandbox-linux/.local/007-task099-final，原运行时ZIP仍原样未跟踪。

## TASK-100 切换前准备（2026-09-10）

本轮用户明确“继续下一步工作”，TASK-100已按依赖进入doing。查询确认原judge-local-2仍ACTIVE/version1/在线1，
cherry-linux-2仍REGISTERED/version0/在线1。远端manage.py status返回三个服务和slice全部active，
memory/swap/pids/CPU限额与最终安装一致；没有重启、改配置或重新运行资源压力。

管理页核验公开a-plus-b-ii仍v3，六组数据80 B及ZIP摘要与TASK-100清单一致；
“检查发布”显示部署READY和当前环境VALID。通过既有“创建新修订”操作准备v4草稿
`01a08a30-ff4c-7a71-9112-9a4dde2923e1`，复用原数据绑定并保存验收用途说明；
保存状态及草稿数据绑定均已从页面确认。未切换ACTIVE、部署新节点数据、校准、发布或创建正式提交。

4173预览页登录返回通用错误；只读配置显示网关受信来源为localhost:5173。
用原构建在空闲回环5173启动本任务独立预览（npm run preview，configLoader runner），
随后localhost页面已有admin会话且管理访问成功；没有提取浏览器凭据、关闭CSRF或修改配置。
本任务预览PID9078、执行会话30593，暂留供本次审核及后续验收，完成或取消任务后仅停止该进程；
原4173、IDEA、旧节点和SSH隧道未动。

acceptance目录新增十份静态C++夹具与有界串行验收清单。本地c++ -std=c++17 -fsyntax-only：
九份通过，一份包含指定#error并按预期失败；没有执行任何夹具，不能计为Linux业务通过。
scripts/work check：415份通过，仅既有WORK-033提示；git diff --check通过。

源码核验确认当前部署/校准只支持ACTIVE，题目校准只允许DRAFT。TASK-100与PLAN-032已据此记录
先准备草稿、再审批具体切换、部署校准和发布的顺序，以及v4发布后不能仅反向切换即恢复业务的限制。
下一动作须用户明确批准清单中的judge-local-2 → cherry-linux-2切换；不以本轮“继续”代签该批准。
WORK验收闸与VERIFY结果仍pending，未提交推送，原ZIP和WORK-049未动。


## TASK-100 实际部署与业务闭环（2026-09-10）

用户在阅读具体切换对象、不可用窗口和发布后回退限制后明确“批准”。刷新list确认版本未变，
执行既有judge-environment switch成功。新环境ACTIVE/row_version1，旧judge-local-2为RETIRED/row_version2且保留在线。
所有写入经管理页与审计CLI完成；取证SQL只读事务，不负责业务变更。

### 身份、部署与新校准

| 事实 | 实测记录 |
|---|---|
| 切换审计 | `01a08a35-02eb-707e-b1ea-aa15d65db71d`；ENVIRONMENT_SWITCHED；2026-09-10 07:25:25 UTC |
| ACTIVE | cherry-linux-2；`01a08a09-23b1-7ed5-bc0f-e03002e65e4a` |
| 指纹 | `79b8cb44ea253f47c44c2cc518fc4b0f785d9b7f69f857e110bfea9df07835bb`，与TASK-099最终身份一致 |
| 新公开版本 | a-plus-b-ii v4；`01a08a30-ff4c-7a71-9112-9a4dde2923e1`；PUBLISHED |
| 数据 | `01a081c5-1a7b-7bac-8320-e35047f40be6`；6组/12文件；available=1，当前节点session回执在线 |
| 部署摘要 | `17c63562178a77205e3e3d18f4a1eb81eabc0be922a6339dca8dd8cd7570dcf8` |
| 新校准 | `01a08a36-b4ef-736e-8ebe-45d83a2ef1ef`；v4/cpp/新环境；VALID；2026-09-10 07:27:16 UTC |
| 校准输入 | cpuNs=1000000000；memoryBytes=268435456；clockNs=null，实际沿用倍率后为10000000000 ns |
| 参考程序结果 | AC；maxCpuNs=22072000；maxMemoryBytes=4337664；maxClockNs=null（接口未提供，不填造值） |
| 参考程序SHA256 | `5b56836ebe662f3a958a631ce158d6136b8e8ca91e70de6e44746b4f0583b261`，与acceptance/calibration.cpp完全匹配 |

通过管理页“部署测试数据”得到cherry-linux-2可用，然后运行参考程序校准。发布检查的题面、样例、
语言、数据、在线节点、部署和校准全部通过才发布v4。新校准创建时间及新环境关联均从只读事务核实，
没有复制旧标定。原ZIP未上传重建、删除或提交。

### 真实业务结果

全部通过localhost:5173题目页 → IDEA后端 → SSH → 原生Linux judge/sandbox/helper。
公开页显示v4，9次自定义运行（8类加一次CPU观察补测）均串行，未重启服务；结果为页面显示值：

| 用例 | 北京时间 | 终态 | CPU（ms） | memory.peak（KiB） |
|---|---|---|---:|---:|
| stdout/stderr | 15:30:27 | COMPLETED | 26.549 | 3984 |
| 编译错误 | 15:31:18 | COMPILE_ERROR | 不返回运行用量 | 不返回运行用量 |
| exit(7) | 15:32:01 | RUNTIME_ERROR | 23.205 | 4060 |
| 普通SIGKILL | 15:32:24 | RUNTIME_ERROR | 20.373 | 4192 |
| CPU1 s | 15:33:16 | TIME_LIMIT_EXCEEDED | 1004.207 | 4116 |
| 触碰320 MiB页 | 15:34:23 | MEMORY_LIMIT_EXCEEDED | 216.910 | 262144 |
| 有界大输出 | 15:35:37 | OUTPUT_LIMIT_EXCEEDED | 18.445 | 3808 |
| 紧接大输出的空程序 | 15:36:16 | COMPLETED | 16.147 | 3880 |
| CPU补测 | 15:37:33 | TIME_LIMIT_EXCEEDED | 1004.343 | 3796 |

stdout/stderr用例捕获2/15 bytes，内容分别为3加换行、work048-stderr加换行；CE诊断包含预设#error标记。
OLE捕获1048576 bytes且展示截断，错误输出0；未将大输出正文写入报告。随后空程序正常且峰值仅3973120 bytes，
未沿用前次任务峰值。普通SIGKILL保持RE，没有映射为TLE/MLE。

CPU最终用量来自每次独立cgroup累计cpu.stat，页面换算ms；本次相对1 s预算超出4.207/4.343 ms。
只读observe_run.py对补测的Main进程每5 ms采样：可见区间1051588756 ns（约1.052 s），
最后样本usage_usec=1001110、cpu.max=10000 10000，memory.events中oom/oom_kill均0。
组为run-7cb68af382753eff7f03f78771c1e596。该观测从首次看见运行程序开始，不含编译/SSH时间；
存在采样和调度误差，不能作为严格exec/wait时长或5 ms硬误差保证。它与最终CPU用量共同说明本轮
并未拖到10 s墙钟才停止。memory.peak为组峰值，不是RSS，也不是memory.current轮询近似。

首轮观察脚本错误匹配小写main，而真实工具链产物是Main，60 s超时并未捕获；该轮观察失败明确排除。
修正后串行补测一次成功。另一次合并“读CPU结果并开始MLE”的浏览器操作被自动审批拒绝，理由为
上个可见状态仍在运行；改为先独立读取终态，再启动下一例，不绕过拒绝，不产生并发或重复提交。

正式提交仅新增2条，当前题目提交列表由3条变为5条：

| 提交ID | 结果 | CPU（ms） | memory.peak（KiB） |
|---|---|---:|---:|
| `01a08a40-ba47-795b-8367-8ac00a1f7d2c` | v4，AC，6/6，执行6点 | 20.225 | 4180 |
| `01a08a41-056d-7a86-ad49-d0e56a3e03e7` | v4，WA，0/6，执行6点 | 21.043 | 4056 |

两条历史源码分别匹配A+B与固定错误输出夹具；查看AC历史代码时右侧WA草稿保持不变。
未删除正式验收记录、改写旧提交或复制旧v3结果。结束时编辑器恢复为本次正确A+B夹具，未再提交。

### 回收、验证与边界

只读调用已安装verify-native.py的clean函数：无专用任务UID进程、无执行子组，工作区仅.lock；
blobs仅.lock，helper state仅helper.sock/owner-v1/lock。扫描所有可读/proc/*/mountinfo后，
项目run挂载和工作区子路径引用为0。没有清理无关系统资源，两个60 s观测进程均已结束。

read-evidence.sh复用既有配置和构建依赖，独立Java进程在只读事务内限行/限时核对上述身份、校准、
部署和切换审计，退出rollback并清理临时解包；不启动应用或迁移。程序实际编译执行及查询成功。
calibration.cpp摘要由Python验证；首次shasum因本机Perl locale故障未执行成功，未冒充通过。
生产Go/Java/Web与系统配置均未修改，原有服务没有重启，本轮无commit/push。

### 当前支持范围及WORK结论

| 平台/条件 | 当前结论 |
|---|---|
| Ubuntu24.04.4 / Linux6.8.0-124-generic / x86_64 / AppArmor / systemd委派 / root helper | 已验证TASK-098隔离资源套件、TASK-099原生服务恢复和TASK-100真实业务闭环 |
| 首站普通目录或缺cpu/memory/pids委派 | 已验证拒绝启动 |
| 历史Ubuntu22.04.5 / 5.15.0-181-generic / x86_64且缺memory.peak | 明确不支持该缺能力配置；没有运行完整拒绝套件 |
| 其他Ubuntu、Debian、RHEL兼容环境/实际内核与LSM条件 | 待提供机器并实机验证，不能由发行版名或交叉编译承诺支持 |
| Linux/arm64 | 当前后端明确不支持，未来实现与实机验证另行安排 |
| macOS host | 仅可信开发 |

TASK-100已授权完成标准满足。AC-001/002/003/005首站有证据；AC-004的机器重启未授权、未验证，
开机自启仍disabled，不能由服务崩溃恢复或卸载恢复推断。VERIFY结果partial、状态review，WORK人工验收闸保持pending。
共享内核、可信专用宿主的loopback HTTP边界及SSH隧道依赖仍是已有部署限制，没有扩大安全承诺。

收尾：本任务创建的5173预览PID9078在核对命令行后已停止，临时浏览器页已关闭；原4173、IDEA服务、
Linux节点与SSH隧道继续运行。TASK-100由工具置done，refresh成功将WORK-048推进implemented。
VERIFY维持review，仅更新result为partial；set-status拒绝review→review的无效状态转换后，按事实编辑结果字段。
415份文档及diff空白检查通过，仅既有WORK-033提示；没有执行人工gate命令。

## TASK-108 重启恢复前只读检查（2026-09-10）

本段是实施前现状，不是重启验收：三个项目服务均 active，UnitFileState=disabled，Restart=no；
执行 cgroup 为 0，workspace 与 blobs 仅 .lock。当前内核 6.8.0-124-generic，/boot 下仅发现
该版本 vmlinuz，无 reboot-required 标志；这不能单独证明下一次 GRUB 启动选择，实施前仍须核对。

代码核查确认 judge 新进程生成新 session，注册会使旧 session 数据回执失效；服务健康不能代替
数据就绪。用户已选择正常管理入口重新部署并核验既有数据的受控恢复。TASK-108 新增方案与
边界，本回合未更新远端文件、未 enable、未替换隧道、未重启机器。VERIFY 仍 partial / review，
reliability 待实际整机恢复证据，不能提前将 AC-004 标为通过。

## 用户留置重启后的验收收束（2026-09-10）

用户随后明确暂不实施重启恢复，并要求当前沙箱无阻断问题时准备验收。依据该人工范围决定，
IMPROVEMENT-004 的 AC-004 移出整机重启；TASK-108 由工具置 cancelled 表示取消本轮执行，
未完成方案仍保留，未来另建承接任务。不将任何重启、开机启用或隧道自动恢复记为成功。

复核既有最终证据：TASK-098 隔离/资源/1000次/故障及独立复核两项P2修复闭环，TASK-099正式服务
权限、限额、缺文件拒绝与卸载恢复，TASK-100新身份校准和真实自定义/正式提交，均无尚未解决的
已知阻断。按修订范围将技术结果记 pass，reliability/rollback 检查与 review 阶段收束；不表示新增
独立复核或重新运行实机套件。refresh 将 WORK 由 todo 推进 implemented，验收闸仍 pending。

416 份开发文档和 git diff --check 通过，仅既有 WORK-033 提示；本回合仅改工作材料及工具生成
控制面，没有修改代码、远端或运行时 ZIP，没有提交推送。当前支持边界及运维限制见本报告开头，
供用户签署验收时确认。

## 人工验收后的提交核对（2026-09-10）

用户已亲自签署验收闸；refresh 后 WORK 为 verified。用户随后授权全部修改 commit/push。
提交前核对发现既有可信开发 Compose 缺少显式后端选择，依 PLAN-032 / TASK-099 的交付边界
修订，仅增加 CHERRY_OJ_SANDBOX_BACKEND=trusted-host 和用途注释，保持正式 Linux 默认拒绝降级。
普通与 legacy Compose 合成配置均通过断言，read_only/cap_drop 保持不变；未重建或重启现有容器。

本地 gofmt/vet、Linux amd64 构建、go mod tidy -diff 通过；部署 15 项、rootfs 6 项、契约 11 项、
工作项工具 44 项和文档链接校验器 3 项测试通过。仓库新增文档需暂存后参与「链接目标进入 Git」
检查，未暂存时的 12 项链接错误均指向本轮 WORK-048/049 新材料，不是缺失文件。
本次没有新增 Linux 实机或重启测试，不扩大已签署验收的支持范围；完整 CI 结果以提交对应运行记录为准。
