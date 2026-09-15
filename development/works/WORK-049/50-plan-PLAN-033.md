---
id: "PLAN-033"
type: "plan"
title: "按命令执行顺序重构 Go 判题引擎源码"
status: "checked"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-14"
---

# PLAN-033：按沙箱对象职责分批重构

## 目标

后续以 DESIGN-043 的所有权和生命周期模型实施。用户在计划交付后明确要求“开始重新重构”，已按 R0～R7 完成本地实现。R8 独立阅读与最终候选完整 Linux CI 已通过；验证候选已在独立分支提交、推送并运行现有 workflow，未合并 main 或在开发宿主部署。WORK-049 已有意图闸保持有效，不重复签原闸，不把本轮计划中的未实现内容登记成完成。

已完成的 B0～B4 是输入基线：保留数字具名化、显式清理、已通过的本地测试及全部工作区增量。此前按文件拆分的 B5～B8 由本计划 R6～R8 承接；不会先继续旧文件拆分，再另开一轮对象重构。

本轮目标是让类型和操作直接表达沙箱由什么组成、资源归谁、哪个进程执行隔离动作。每批必须解决一种实际所有权或状态问题；纯文件搬迁不计为结构交付。

## 改动区域

| 项目 | 交接依据 |
|---|---|
| 上轮源码起点 | e1f6f11e4981d3159747a197fa270c7610e2a28e，加实际未提交的 B1～B4 和注释增量 |
| B2～B4 源码快照 | VERIFY-050 中 work049-rework-0icvlxn8；rework-source.patch SHA256 为 2b440c2ec0c2c578f69d87afa3333016a46a51822479fbfb7c0ff7c4e43f8231 |
| 上轮 harness | 3da9e679940de8114d61f1f4138a52b996f1272f3806e11925ed8a31c4eecab2 |
| cases.json | 5be0031c6735e7d8cb580aae4fd4d0d8b6b6d55514484fc070ae58e4ca29f83d；93 项，requiredGoTests 52 个 |
| 历史验证 | B1～B4 的本地 race、双平台 vet 和交叉编译已通过；候选完整 Linux 回归和独立阅读验收未执行 |
| R0 要做的核对 | 重新取得 HEAD、所有未提交文件、源码/协议/harness 身份，记录与上述交接的差异，不能直接把旧 SHA 当作实施起点 |

TASK-105 仍只写 sandbox、相关 cmd、模块 README 与本 WORK；TASK-106 只写 judge/config/内部 Go contract、cmd/judge、README 与本 WORK；TASK-107 只写本 WORK 验证文档。现有 read_paths 只补充已迁移的 docs/coding-standards 以读取当前规范，写边界不扩展。

外部 contracts、Java/Web、部署脚本、CI/workflow、Go 依赖和全局规范不改。三个常驻进程、P3 创建 P4、P4 创建 P5、P5 execve 不增进程的模型保持。任意宿主目录挂载、扩大权限、改变默认值/错误/协议不属于该计划。

## 阶段与顺序

R0～R5 归 TASK-105，R6～R7 归 TASK-106，R8 归 TASK-107。R0～R7 已完成本地实现与约定检查；TASK-104 不重开，R8 未完成项见 VERIFY-050。

| 批次 | 修改对象与代码落点 | 可审阅的交付结果 | 退出检查 |
|---|---|---|---|
| R0：冻结本次迁移合同 | TASK-105；核对 execute/execution/launch/supervise/cleanup、delivery、init/exec、Pool/Container 当前源码与测试；只写盘点 | 旧字段→新 owner→状态前置条件→释放/移交点→覆盖测试的完整表；正常/超时/取消/部分启动失败及 fatal 优先级对照 | 无漏 owner、双 owner 或未有停止/等待者的后台任务；验证冻结的 93/52 入口；必要的新增测试对应真实缺口 |
| R1：建立隔离配置值 | helper/isolation_plan.go，Config/Request 与 StageSpec 的组装处 | 只读 isolationPlan 明确 namespace/filesystem/resources/identity；副作用仍在原执行位置，尚不迁移进程所有权 | 字段/快照不跨请求共享；原固定值、默认/零值、协议全部一致；不开放请求挂载/身份字段 |
| R2：收拢隔离进程 | helper/process_linux_amd64.go、launch/capture、cleanup 中进程与 I/O 部分 | isolatedProcess 接管 init、FD、握手和后台任务；原 execution 通过执行事件监督，部分启动可完整回收 | 部分启动、提前退出、乱序/重复事件、ready 与超时、阻塞输入、输出超限、未消费 FD 及一次关闭测试；cgroup 所有权不迁入 process |
| R3：形成执行与服务边界 | helper/execution.go、execute/supervise/cleanup、server | execution.Run 统一启动、监督、停止和回收；service 只创建/执行/交付/停服。旧自由编排入口及双重状态删除 | 重复 Run 拒绝；合法状态转移；原 OOM/取消/平台/清理错误优先级；起始计时不变；service 不读取 FD/任务通道 |
| R4：收拢 P4 文件系统职责 | launcher/rootfs_linux_amd64.go、init_linux_amd64.go | rootFilesystem 持有本次准备状态及局部句柄，initSession 清楚调用准备→启动 P5→放行→等待/报告 | 固定挂载来源/目标、private propagation、只读/设备约束、pivot_root/卸载旧根、输入与 stdin 顺序保持；P5 最终 exec 语句/线程约束不变 |
| R5：闭合结果所有权 | helper/delivery 与 artifactSet、execution 的结果移交、client；runner/Pool/Container 适配与 README | 结束的 execution 不再拥有已交付产物；Result、产物关闭、Completion、EOF、本地 ref 发布各有唯一负责方；P2 不复制 P3 状态机 | 产物打开/关闭/传输失败、关闭前保持槽位、取消先于远端结束、输入/容器关闭失败回滚；固定 frame/错误内容一致 |
| R6：每次判题对象 | judge/flow 及其测试 | 每次 Judge 的源码/编译引用、配置快照、测试点结果归一个私有对象；主线为准备→编译→逐点运行与比较→汇总→清引用 | 请求间无共享可变状态；C++ 编译一次、逐点执行，解释语言跳过编译；原 verdict、可见性、释放与指纹保持 |
| R7：控制面和配置责任 | judge/node、config、internal/contract | node 的服务级状态与安装事务区分，保持 environment/deployment 分流；配置与 Limits 映射按原定义归属 | 原锁/安装原子性/认证/字段顺序/指纹/退避、配置校验、默认/显式零值及编解码行为不变；不为无状态解析强制造对象 |
| R8：独立结构与行为验收 | TASK-107；VERIFY/MEMORY、候选规范结论 | 未参与实现的审查者从类型、主入口和进程边界读源码；最终候选运行完整固定回归；逐项 AC 记录 | 不以测试绿色替代结构验收；自身 sourceSha/harnessSha/runId/attempt 一致的完整 CI；人签验收闸仍由用户完成 |

R2 可先由现有 execution 持有新的 process，R3 再收拢总生命周期，R5 完成产物转移。每批同步替换实际调用点；原接口暂留必须写明下一个移除批次。R5 结束不允许遗留两套资源所有者、两套执行状态或仅为了旧名字保留的转发壳。

### 每批审阅内容

每批交付可编译 diff、旧字段/方法到新对象的映射、构造与主入口、资源转移和取消/失败路径、实际验证结果、已知未验证项及精确撤销范围。
首先读对象定义和主入口，再深入 syscall 实现。新对象必须拥有清楚的资源或状态，并封装合法操作；只是把一堆字段换个 struct 名称，或让调用方继续修改内部字段，不满足该批标准。

## 并行与依赖

按 R0→R1→R2→R3→R4→R5→R6→R7→R8 顺序推进，不同时改动两个 owner 的生命周期。当前不创建子任务会话或启动代理；独立审查在 R8 落实审查者。

TASK-105/106 的本地实施检查已完成，TASK-107 待落实独立审查者与最终 Linux 运行。此前 B1～B4 记录仅表示历史交付。技术任务完成依据其实现和约定检查，整体平台/阅读验收由 TASK-107 汇总；未运行的 Linux 检查明确移交，不能变成已通过。

## 验证

| 关注点 | 检查方式 | 何时检查 |
|---|---|---|
| 新对象无行为偏移 | 原测试先保留；新增测试只覆盖所有权、合法操作、快照隔离、清理/失败边界；不按每个 getter/方法写镜像测试 | 对应 R 批次 |
| 普通 Go 实现 | gofmt -l .、对应包 go vet 和 go test -race | 每批；最终全模块 |
| Linux 专用结构 | CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go vet ./...、helper/boundary 交叉编译；可用授权环境执行固定 Linux 测试 | R2～R5；没有环境时记录未运行 |
| 最终 exec 与挂载 | 前后 syscall/线程/FD/握手/挂载顺序对照；原启动边界、取消、故障、回收、并发测试 | R2～R5 及 R8 |
| 本机协议 | Request、StageSpec、Event、Result、Completion 的字段、限额、编码与尾部确认一致；内部类型不泄漏到 wire | R1～R5 |
| 固定 CI | basic 5、kernel 63、native 10、business 15，及现有必需 job；requiredGoTests 保留 52 个固定入口与原断言 | R8 最终候选 |
| 阅读验收 | 按 DESIGN 的对象/状态/资源问题实走源码，选正常、超时、取消、部分启动失败，记录具体卡点与修复复读 | 各批自查，R8 独立复核 |

CI 用例不减少、不重命名选择器以绕过失败。package 内测试可以适配私有符号，但保留固定测试名、条件和断言；tests/sandbox-linux 及 harness 默认不动。确需边界内适配时记录新 harness、断言等价性，并与原基线运行同样断言；修改 CI/部署/契约先升级范围。

报告固定 sourceSha、harnessSha、runId、runAttempt。历史 93 项通过与本地交叉编译不充当新候选的 Linux 证据。完整 CI 所需提交/推送/PR/dispatch 仅在已获对应授权时执行；本轮没有这些动作。

### 新增结构验收问题

1. 只读 service 和 execution.Run，能否说明从接单到交付的对象协作？
2. 只读隔离配置和进程启动入口，能否定位 namespace、cgroup、mount 分别由 P3/P4 谁执行？
3. service 是否仍需要碰原始 FD、猜 initStopped/inputFinished 或手工安排清理顺序？若需要，该批退回。
4. 每个 FD、group、后台任务和产物是否有唯一 owner，移交后原对象是否停止释放？
5. 取消、部分启动、OOM、回收失败时，结果状态和节点接纳策略是否保持？
6. 哪些完成只发生在本地，哪些需要远端 Completion/EOF；代码是否把它们混为一谈？
7. P4 与 P5 的继承、放行和 execve 是否清楚；函数、对象和 goroutine 是否被误数成进程？

## 迁移与交付

保持中间批次可编译，禁止长时间并存两条执行路线或通过运行时开关分流新旧实现。R0 保存实际文件和增量，每批交付对象职责、源码映射与对应验证证据。候选规范保留在 DESIGN，R8 后再由人决定是否提升至全局规范。

## 风险

所有权迁移最大的风险是重复关闭/漏关、关闭先于 I/O 完成、提前归还槽位、结果中仍携带已关闭产物、并行状态机漂移。通过状态/资源测试与固定 Linux 回归约束，不能因封装变美而降低等待保证。

若必须改变进程、权限、wire 或预算才能继续，停止相关批次并更新上游决策；不把发现的旧缺陷夹带进等价重构。

## 回退

不回退现有未提交重构，也不先重置到旧 HEAD。每批从 R0 记录的可比较起点开始，仅撤销该批引入的改动，保留之前有效源码、人工签署、WORK-050/其他 WORK、server 数据删除。没有部署迁移或远端回退动作；不使用全仓 reset、整目录 checkout 或修改宿主权限绕过失败。

## 历史与变更记录

- 2026-09-13：建立 B0～B8 计划，核验 WORK-049 意图闸与 WORK-050 验收，历史冻结提交为 8fe6e413a4cc0291204c298705403462ac4ed0d4、harness d17e8e55…563f。历史 CI 证据留在 WORK-050/MEMORY-037，不能与后续 harness 混用。
- 2026-09-13～14：B0～B4 已交付本地整理，详见 VERIFY-050；没有完成整体阅读或 Linux 验收。
- 2026-09-14：用户确认五个设计概念后，本轮把后续计划改为 R0～R8，取代旧的纯阶段/文件拆分实施顺序。只更新文档，未开始任何 R 批次。
