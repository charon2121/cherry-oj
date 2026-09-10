---
id: "DESIGN-044"
type: "design"
title: "将沙箱已验收回归固化为重构 CI"
status: "checked"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["CAPABILITY-008", "EXPERIENCE-021"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# DESIGN-044：已验收沙箱回归自动化

## 背景

依据 CAPABILITY-008 与 WORK-048/VERIFY-049。基线为已推送的 4a166d60a163f5d4afee2c7373d73160e11b1e68，CI 34455246088 六个 job 成功，但 Linux 特权边界测试依赖环境变量，普通 go test 会跳过；containers 使用显式 trusted-host；现有 custom-run Playwright 测试使用 route.fulfill，不能证明真实 Linux 业务链。

## 目标与限制

把已验收的有效场景固化，历史失败尝试保留为来源，不把每次人工探测命令逐字搬成 CI。以清单保证等价覆盖；Python 负责编排和事实断言，Go 保留真实边界夹具，Playwright 验证页面，不能引入另一个 sandbox 实现。当前本轮只写设计，CI 环境行为尚未验证。

## 整体方案

沿用 .github/workflows/ci.yml，新增普通脚本检查、真实 Linux、原生部署、真实业务和汇总 job。全部在 PR/main push/workflow_dispatch 运行，不按 paths 静默省略核心套件；默认取消旧提交运行，取消结果不能变成成功。单个 Linux job 不再并行启动互相占用固定端口/身份的测试批次，跨 job 使用独立 VM。

| 层/清单组 | 已有入口与证据 | 自动化要求 |
|---|---|---|
| basic | 现有 gofmt/vet/build/race/tidy、contracts、work/docs、Web、legacy Compose；install 15 项/rootfs 6 项 Python 测试 | 保留原检查并接入 Python AST、shell 语法与 Python 单测；Go 计量口径不改 |
| kernel-start-files | boundary_batch.py；tests/sandbox-linux/boundary；helper/launcher Linux 测试 | 编解码/FD/READY/GO/存活通道与 exec 各失败阶段及 errno、真实恶意链接/FIFO/设备/路径交换、缺委派拒绝；断言用例确实执行 |
| kernel-identity | inspect_threads.py、extended.py、cpp.py/cpp_limits.py、http_chain.py、fault_batch.py capacity | 编译和执行全线程 UID/GID/cap/NNP/seccomp、六 namespace、只读挂载、私有文件/身份及网络/宿主/提权拒绝；与已有断言逐项映射 |
| kernel-resources | chain_batch.py smoke/repeat/concurrency、http_chain.py、cpp_limits.py | 单/多进程 CPU、墙钟、OOM/swap/pids、普通 SIGKILL、OLE 后空程序、独立编译/运行计量；完整 1000 次、同 PID 初末 FD/进程/挂载/cgroup/文件快照 |
| kernel-faults | fault_batch.py 默认/capacity、crash.py | 排队/handler 饱和、取消、后台 setsid、init/HTTP/helper 崩溃及显式恢复、节点聚合 OOM 平台错误与任务自身 MLE 对照 |
| native | install/manage.py、verify-native.py、verify-lifecycle.py、verify-faults.py、verify-uninstall.py、verify-capabilities.py | 真实安装回执、rootfs/配置校验、24 项限额、七 cap 正反对照、三服务故障/缺文件、停服、卸载保留及恢复；不执行整机重启 |
| business | acceptance/*.cpp、observe_run.py、TASK-100 已验收流程 | 同轮新节点注册→正常数据上传/部署→校准→发布→8 类自定义→正式 AC/WA→代码回看；新建 UI 用例连接真实 Gateway |
| review-only / deferred | Boyle 独立复核、TASK-108 与跨平台矩阵 | 独立审查为人工/审查者动作，CI 不能自签；机器重启明确留置，其他平台未覆盖不计通过 |

由 TASK-109 将各组进一步展开成稳定 case ID 清单，记录 Go -run 选择器/脚本模式/预期数量/证据字段；任何一个原有断言都须有去向。旧 smoke/extended/cpp 与新整链的重复断言可合并，须写对应关系，不能靠测试总数相近证明覆盖。边界 Go 测试的 SKIP 只在普通 job 可说明，专用 job 中必需 case 缺失或 SKIP 均失败。

## 模块与数据

新增 deploy/sandbox-linux/ci 保存清单、准备/执行/清理/汇总驱动和报告 schema。驱动准备当前 SHA 的二进制、静态 rootfs 与锁定 C++ rootfs，复用生产构建器和安装器。旧脚本中的 work048 单元名、61001～61010、15050/15051 与路径假设逐项处理：在一次性 VM 顺序使用已有固定部署名，测试批次用 run ID；启动前核对冲突，不能删除既存对象腾位置。

工具链包仅按现有56包锁及 SHA256 下载解包，在 Linux 原生文件系统构建。缓存只加速已核验包或构建产物；key 包含锁摘要/架构/实现摘要，恢复后重新验摘要，冷缓存也要成功。不依赖项目 .local 中的旧 rootfs、二进制或 test ZIP。Action 依赖在实施时从官方来源确定版本并固定提交 SHA。

业务环境在独立 job 建立全新 MySQL（四业务库）、Redis、Kafka、五个真实 Java 服务和本轮原生 Linux 节点，JDK21/Maven Wrapper 沿用项目。全部使用独立实例/卷/端口及运行时生成凭据；显式 test 配置，不能读取 application-local.yaml 或个人 IDEA 状态。数据库建库与 schema 初始化限新数据库的正常迁移，业务注册/部署/校准/发布走公开或已有受审管理入口，不能用 SQL 伪造在线、ACTIVE、部署或校准。

node-e2e.py 可借鉴 bootstrap/登录/部署顺序，但它使用旧 Compose、硬编码旧场景、没有当前 Kafka 正式闭环，不能直接当成 TASK-100 完整覆盖。新驱动直接组织当前服务配置，合成 6 对 A+B 数据并规范生成 ZIP；读回生成的问题/版本/数据/节点/session/校准 ID。ReadEvidence.java 的固定旧 ID 不沿用，取证改为本轮上下文，查询仍限只读和有界。

真实页面使用独立 Playwright 配置/目录，禁止 route.fulfill 与模拟状态。复用现有前端构建，在同 job 测试端口连接真实 Gateway；按项目既有配置完成 Origin/CSRF/登录。API 完成可重复准备，UI 执行并验证 stdout/stderr、CE/RE/TLE/MLE/OLE、普通 SIGKILL 为 RE、紧接 OLE 的空程序、正式 AC 6/6、WA、代码回看及不覆盖编辑草稿。正式提交唯一请求标识、轮询期限，禁止重试整个场景隐藏第一次失败。

每轮都用实际二进制/rootfs/配置生成新节点身份并重新校准；重构后指纹可以变化，不能以保持旧指纹作为等价标准。比较的是协议、策略、限额、结果与清理不变量；源码布局变化导致 Go 测试导出符号变化时只适配调用，不能变更断言。

## 接口与状态

仓库内入口计划接受固定 suite 名称（basic/kernel/native/business/all）、独占输出目录和当前 source SHA；不接受任意 shell/任意远端目标。GitHub 中输入来自固定工作流上下文，不将 PR 标题/分支名插入 shell。报告至少含 sourceSha、harnessSha、runId、suite/caseId、status、start/end、环境/镜像版本、二进制/策略/rootfs 摘要、退出事实/主动原因、CPU/内存/超限误差与 cleanup 结果。

汇总 needs 所有既有必需 job 与新增套件，用 always() 执行；核对 job success、报告存在/schema、SHA 一致、case ID 完整且唯一、无未预期 SKIP、清理确认成功。每组原始失败不可被重试成功覆盖。缺产物或前置失败是未执行，不是假绿；测试报告内容不作为新代码执行。保存有界脱敏 JSON、Go JSON、JUnit/Playwright 结果、失败截图和清理快照，建议每套件日志上限20MiB、产物100MiB、保存14天；凭据、Cookie、私钥、原始授权头不上传，真实 browser trace 默认关闭以免包含会话。

CPU 功能回归保留当前预算与原用例误差阈值，单独记录5ms监测周期与观测误差，不承诺硬实时上界；总 HTTP 耗时不能代替运行墙钟。性能统计在同轮相同环境重复测量，波动不能通过自动放宽安全限额解决；历史4.2ms样本不能当所有runner的阈值。memory.peak仍是组峰值，不能轮询current替代。

## 安全与失败

首选 GitHub 标准 ubuntu-24.04 x64 一次性 VM（不使用 ubuntu-slim 或 job container 代替宿主）；sudo 仅在该 VM 内准备委派、账号、挂载和服务。当前 GitHub 文档说明标准托管 job 使用新 VM、Linux 支持无密码 sudo：[runner说明](https://docs.github.com/en/actions/concepts/runners/github-hosted-runners)、[权限参考](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)。这些说明不证明具体隔离接口可用，首次运行仍须行为探测；记录实际内核/LSM，缺能力失败，不降级 host 或关闭 AppArmor。

公共仓库 PR 不连接持久自托管机器；工作流使用 pull_request、contents:read、checkout persist-credentials:false，不使用 pull_request_target 执行来稿、不读取项目/云凭据、不授予部署权限。官方也明确提示公共仓库自托管 runner 的持久污染风险：[安全依据](https://docs.github.com/en/actions/reference/security/secure-use)。包安装如必需仅在一次性 runner 内完成，不修改用户服务器。参考与被测代码仅在该一次性环境里作为可信 CI 输入处理，其报告不等于任意恶意PR的安全证明。

外层每个批次使用 WORK-048 已实测 systemd MemoryMax/SwapMax/TasksMax/CPUQuota/RuntimeMaxSec；先设外层上限再运行 fork/OOM/死循环。内核套件预算不超过原 helper768MiB+HTTP256MiB+driver128MiB，原生节点slice保持1536MiB；业务依赖单独封顶并为宿主留余量。job初定 kernel15min/native20min/business35min，单case保留原90～180s驱动上限；准备与下载独立deadline，首次基线记录实际总量后冻结预算，不能静默上调。

资源所有权记录包含 run ID、路径、unit、PID/exe/UID/cgroup、容器label/卷；finally与工作流always清理并核验，SIGTERM取消也进入清理。runner被强制终止时systemd期限和VM销毁托底，但缺清理证据仍不得记PASS。未知文件、端口冲突或资源残留保留证据并失败，不执行全局prune/pkill/rm或服务器重启。部署故障只在本job尚无真实用户业务的节点串行执行。

## 监控与部署

只交付仓库CI工作流和测试驱动。汇总给出已执行数/必需数、缺失和失败，不由CI签署项目验收。先跑当前代码两次完整基线（至少一次冷缓存），再将具体报告与清单交给 WORK-049；重构候选按同一清单跑完整套件，不能只看到旧提交CI绿。

## 迁移与兼容

不改生产Go/Java/Web、公开schema、默认限额或权限。若适配自动化暴露真实实现问题，另拆边界修复，不在CI脚本里绕开。Ubuntu托管镜像持续更新，不能承诺与原服务器6.8内核完全相同；逐次记录，环境更新失败按环境故障处理，后续平台矩阵另行审阅。原有本地手动入口仍可使用，参数化保持其默认语义。

## 备选方案

现有专用服务器安装self-hosted runner会与已部署节点竞争固定UID、端口及单元，还引入持久来稿执行风险，本轮不选。每次另租Ubuntu6.8虚拟机可更接近历史基线，但需要云凭据和生命周期费用；仅当托管VM实际缺能力时重新提出，不自动申请或更改服务器。

## 风险与重审条件

GitHub内核/LSM与原服务器不一致、包下载失效、五服务栈内存不足、固定接口夹具无法在独立环境运行，均应留下明确失败阶段。内核能力不满足时暂停对应套件的支持结论，不能skip后批准重构。CI保障行为回归，源码可读性和特权路径独立复核仍由WORK-049完成；整机重启继续留置。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已盘点既有验收与CI缺口，补齐分层方案、边界及验收条件供人工审核；尚未实施
- 2026-09-10：结构与内容校验通过，由工具置为 checked。
