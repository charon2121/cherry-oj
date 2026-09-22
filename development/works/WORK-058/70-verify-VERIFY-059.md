---
id: "VERIFY-059"
type: "verify"
title: "判题引擎结构重切的回归验证"
status: "approved"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["PLAN-041"]
related: ["CHANGE-014", "DESIGN-051"]
implements: []
verifies: ["CHANGE-014#AC-001", "CHANGE-014#AC-002", "CHANGE-014#AC-003", "CHANGE-014#AC-004", "CHANGE-014#AC-005", "CHANGE-014#AC-006", "CHANGE-014#AC-007", "CHANGE-014#AC-008", "CHANGE-014#AC-009", "CHANGE-014#AC-010", "CHANGE-014#AC-011", "CHANGE-014#AC-012", "CHANGE-014#REQ-016"]
tags: []
result: "partial"
created_at: "2026-09-14"
updated_at: "2026-09-22"
---

# VERIFY-059：判题引擎结构重切的回归验证

**当前结论（2026-09-22）：R1–R10 已修复，本地回归与三位独立 Agent 的复审通过。**
首次 CI 的真实 Linux 隔离与原生部署通过，业务采样器失败；同类消失竞态已补回归与修复，等待复跑，
`result` 保持 `partial`。修复及验证见下方
「TASK-133 修复验证」；初次独立复核的两项 P1、八项 P2 与原始反例保留为历史证据。
2026-09-15 的人工签署与测试记录不代表本次修复候选已验收。

## 验证对象

`apps/judge-engine` 按 [PLAN-041](50-plan-PLAN-041.md) S1–S7 完成结构重切后的最终候选，与基线
`a611be3`（WORK-049 最终候选）比较。

本文档在实施前只记录验证方案；实际命令、环境与结果在各阶段完成后逐条补入。

## 对应要求

覆盖 [CHANGE-014](10-change-CHANGE-014.md) 的 AC-001 至 AC-012，已锚定进 front matter 的 `verifies`。

| 验收标准 | 验证方式 | 产生证据的阶段 |
|---|---|---|
| AC-001 边界由编译器强制 | 构造跨边界引用，记录 `go build` 失败输出，随后撤销 | S2 |
| AC-002 配置互不牵连 | 两组交叉配置分别启动 judge 与 sandbox | S3 |
| AC-003 指纹输入显式 | 基准测试三种情形的实际输出 | S3 |
| AC-004 终止原因覆盖完整 | 删除任一分支使测试失败 | S1 |
| AC-005 一次性调用完成回收 | 四条路径的 Linux 回归与清理证据 | S4 |
| AC-006 结论可离线验证 | macOS 表驱动测试，与基线行为逐条对照 | S5 |
| AC-007 部署校验双向覆盖 | 两类不一致的实际报错输出 | S6 |
| AC-008 预算冲突拒绝启动 | 人为冲突配置的启动失败输出 | S6 |
| AC-009 错误消息统一英文 | 检查命令输出 + 统一检索表达式验证 | S7 |
| AC-010 对外行为不变 | 与基线比对 + WORK-050 固化 CI 全量 | 全部阶段 |
| AC-011 文档与实现一致 | 按重写后文档实走源码 | S7 |
| AC-012 指纹轮换表现正确 | 节点协议观测：新指纹为 `REGISTERED` | S3 后 |

## 检查与结果

S1–S6 的逐阶段证据（命令、输出摘要、CI 运行编号）记在各自的 TASK 执行记录里
（[TASK-125](60-task-TASK-125.md) 至 [TASK-130](60-task-TASK-130.md)）；本文件不复制，
只登记 S7 的独立检查，以及最终候选的合并结论。

### S7：错误消息语言与文档实走（2026-09-15）

环境：macOS（darwin/arm64），Go 版本取 `apps/judge-engine/go.mod`；交叉检查目标 linux/amd64。

| 检查 | 命令 | 结果 |
|---|---|---|
| 格式 | `gofmt -l .` | 无输出 |
| 静态检查（本机） | `go vet ./...` | 无输出 |
| 静态检查（交叉） | `GOOS=linux GOARCH=amd64 go vet ./...` | 无输出（覆盖 `_linux_test.go` 与 `helperd/tests/boundary`） |
| 测试 | `go test -race ./...` | 全部通过 |
| AC-009 英文错误消息 | `grep -rnE '(fmt\.Errorf\|errors\.New)\(`?"' --include='*.go' . \| grep -P '[\x{4e00}-\x{9fff}]'` | 无输出（改写前 238 处） |
| AC-009 动词序列不变 | 改写前后逐条比对 `%w`/`%s`/`%d`/`%q`/`%+v` 序列 | 238 处全部相等 |
| AC-009 改动仅限消息 | 整份 diff 的删除行是否全部含汉字 | 241 行删除，全部含汉字 |
| 文档链接 | `python3 scripts/docs_test.py` | 573 份文档入口与本地链接全部有效 |
| 夹具自测 | `python3 deploy/sandbox-linux/ci/basic.py` | 5/5 PASS（含新增的 `business_journal` 三项） |
| 工作文档 | `python3 scripts/work check` | 499 份通过（1 个与本工作无关的 WORK-033 提示） |

关于 `go vet` 的边界：它能查出格式动词与实参**类型**不匹配，查不出 `%q` 被改写成 `%s`
这类同类型替换。因此动词序列相等这一条是独立于 `vet` 的检查，不能用「vet 过了」代替。

### AC-011 文档实走记录（2026-09-15）

按重写后的 `docs/engine.md` 逐节对照源码。**发现两处文档与实现不符，均已改文档**：

| 节 | 文档原先怎么写 | 实现是什么 | 处置 |
|---|---|---|---|
| §6.2 一次 `/run` 的内部顺序 | 先归一化限额，再由 `pool` 排队、打开 store、调用 `Execute` | 归一化在**排队之后**，发生在 `runner.Run` 里；打开 store 与调用 `Execute` 的也是 `runner` | 改文档为实际顺序，并补上「输出超限取消 `runCtx`、判定用原始 `ctx`」这条实走中确认的细节 |
| §1.2 helperd 如何认对端 | 「用 `SO_PEERCRED`，不是靠 socket 文件权限」 | **双重约束**：socket 为 `root:ServiceGID / 0660` 先放行服务专用组，建立连接后再核对 `SO_PEERCRED` 的 UID | 改文档为双重约束 |

逐节核对通过的部分：

- §1.4 四条跨边界 import 均为编译期错误（S2 已构造验证，本次复核结论未变）。
- §2 目录树与 `find` 输出一致；顶层 `internal/` 只有 `contract`、`hostexec`、`platform`。
- §4 契约表与 `contracts/` 目录、`internal/hostexec` 的注释一致；黄金用例
  `internal/hostexec/wire_test.go` 确实钉住三种帧的字节输出。
- §5.1 judge 端点与 `judge/internal/api/server.go`、`node/install/api.go` 的路由一致。
- §5.2 宽松配对与排序规则与 `testcase.Load` / `lessName` 一致（可转整数的按数值在前，其余按字符串在后，
  落单 `.in` 跳过并记 warning）。
- §5.3「只声明 cpp 不是遗漏」与 `identity` 的注释、Java 侧 `@Pattern(regexp="cpp")` 一致。
- §6.1 sandbox 五个端点与 `sandbox/internal/api/server.go` 一致；`GET /version` 的隔离字段确实是
  judge 节点模式的启动闸，且失败发生在 `net.Listen` 之前。
- §6.3 `Job`/`OutputSink`/`Backend`/`Facts` 字段与 `backend.go` 逐字段一致。
- §6.4 `NameLinux`/`NameDevHost` 常量、`allowUnsafeBackend` 默认拒绝、启动冒烟闸均与实现一致。
- §6.6 `CleanupError` 的文案与 `pool.poison` 的行为一致。
- §7.1 helperd 的自检项（`CGO_ENABLED=0`、root 托管、无 setuid、manifest 摘要钉住、三身份分离、
  启动冒烟、恢复检查）逐条见于 `installation_linux_amd64.go` 与 `recovery_linux.go`。
- §7.2 线格式与 `hostexec/protocol.go`、`result.go` 的常量与注释一致；
  `Completion` 与正常 EOF 的分工与 `client.awaitCompletion` 一致。
- §7.3 限额写后读回比对、新建 cgroup 不得有进程或历史计量，见 `cgroup.go`。
- §7.5 `conclude` 为纯函数、`executionTransitions` 为显式表、`ctx.Err()` 必须早于 `CancelInput()`，
  三条与 `conclusion.go` / `state.go` / `cleanup.go` 一致。
- §8 三道期限的顺序与 `sandbox/budget.go` 的 `checkBudget`、`judge/internal/config/validation.go` 一致。

AC-011 附带发现：`apps/judge-engine/README.md` 与 `helperd/internal/helper/README.md` 的路径与
类型名全部指向旧结构，已一并重写（两者都在 TASK-131 的 `write_paths` 内）。

### 最终候选的 CI 全量（AC-010，2026-09-15）

CI run **34938772322**（sourceSha `66a0a35`，run attempt **1**，一次通过）。
12 个 job 全部 success：`sandbox-packages`、`sandbox-basic`、`sandbox-kernel`、`sandbox-native`、
`sandbox-business`、`development`、`web`、`contracts`、`go`、`tidy`、`containers`、`sandbox-summary`。

必需回归汇总 `summary.json`：`"status": "PASS"`，93 项必需用例全部通过——
basic 5/5、kernel 63/63、native 10/10、business 15/15。

这条同时为 AC-010「对外行为不变」提供证据：错误消息语言切换与文档重写之后，
真实页面 → 五个 Java 服务 → Kafka → 原生 Linux 判题这条业务闭环的 15 项仍然全绿，
说明 Java 侧确实不匹配判题引擎的错误正文（与 TASK-131 执行记录中的分析一致）。

固定要求：

- `gofmt -l .`、`go vet ./...`、`go test -race ./...` 三项在每个阶段都必须记录。
- Linux 隔离、资源计量与故障回收回归以 WORK-050 固化的 CI 为准，记录运行编号与 job 通过情况。
- 未执行、跳过与失败分别记录，不互相替代；在非 Linux 平台跳过的项目必须写明跳过原因与补测计划。

## 未通过项

首次 CI run 35698867685 的业务采样失败，整体未通过。初次复核的 R1–R10 已有处置和回归，
真实 Linux 的 kernel 63 项、native 10 项通过；仍须补齐修复后的完整业务闭环与汇总。
WORK-060 的未跟踪入口问题已在提交整理时解决。

## TASK-133 修复验证（2026-09-22）

对象为基于 `26eddff830f40fc46980e300036f1d3acf469c52` 的本次修复，范围按 TASK-133。
环境：macOS arm64、Go 1.26.3；Go 测试使用 `GOCACHE=/tmp/cherry-work058-review-go-cache`。

| 发现 | 处置 | 直接证据 |
|---|---|---|
| R1 | Isolated.Execute 使用具名返回值，让 defer 中的暂存清理错误进入结果 | `TestIsolatedCleanupFailureRollsBackAndClosesPool` 注入真实目录权限故障，断言 InternalError、引用删除、后续拒单 |
| R2 | AfterFunc 只调用资源关闭函数，不读取登记中的 stop；sync.Once 保证正常收尾等待关闭完成 | `TestCancellationDuringSourceOpen` 在输入解析期间取消，循环 10,000 次，race 通过 |
| R3 | 已启动命令的非退出状态等待失败返回 CleanupError，并尝试停止进程组 | `TestDevhostWaitDelayClosesPool`：父进程退出、后代持管道，返回 InternalError 且下一次请求被拒；原有 OLE 回归仍通过 |
| R4 | runner 持有 backend/Store，由 sandbox 装配；pool 仅消费自己定义的 Executor | `TestPoolDoesNotDependOnExecutionImplementations` 检查生产包依赖；原有并发/排队/停单测试通过 |
| R5 | cgroup 采样只容忍 ENOENT/ENODEV，proc status 只容忍 ENOENT；其他错误重抛 | `identity_sample_test.py` 覆盖两个读取位置 × 五种 errno，先取得完整 UID 样本再注入错误；basic.ci 自动发现 |
| R6 | judge 拥有派生生命周期，Serve 返回后先取消，再等待注册心跳退出 | `TestServeFailureStopsRegistry` 注入监听失败，验证无需外部取消且心跳已退出 |
| R7 | sandboxTimeoutNs 重新进入显式指纹；flow 在上传前拒绝有效墙钟不小于调用期限的请求 | `TestTimeoutChangesVerdictAndFingerprint` 的合法配置分别得到 SE/TLE 且指纹不同；flow 测试覆盖显式/倍率两种墙钟及小于/等于/大于边界 |
| R8 | Version/Probe 共用原 transport 和超时，但使用禁止跳转的调用策略；普通 Run 不改策略 | 301/302/303/307/308 都被拒绝，目标收到零请求；环境探测集成反例通过 |
| R9 | 探测最多读 16 KiB + 1，超限拒绝并直接关闭响应；完整 json.Unmarshal | 16 KiB − 1、16 KiB、16 KiB + 1，尾随垃圾、第二个对象及 1 MiB 响应回归；普通 Run 的 32 KiB 输出仍通过 |
| R10 | engine §1.3 明确当前原生节点模式同机；§8 区分必要期限约束与完整耗时预算 | 对照 probe/deployment 的回环地址、本机清单/二进制/cgroup 核验；更正 TASK-130 的总预算过度声明 |

R7 的固定指纹基准显式更新为 `410c0b0549709ebbaa508baa841ce82750eef714ae036735abd872a9e6947c74`。
调用期限仍不能由 judge 配置证明覆盖排队、传输和回收耗时；因此保留超时对身份的影响，并在文档中
说明部署余量，不宣称已实现全链路总预算推导。不改契约、不切换 ACTIVE 环境。

| 检查 | 命令与结果 |
|---|---|
| Go 全量 race | `go test -race -count=1 ./...` 退出码 0；完整输出在本机 `/tmp/work058-repair-go-race.log` |
| 静态检查 | `go vet ./...`、`GOOS=linux GOARCH=amd64 go vet ./...` 均退出码 0 |
| 部署脚本 basic | `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /tmp/work058-repair-basic` 退出码 0；五项 PASS，install 15、rootfs 27、ci 116 个测试，无跳过 |
| 格式/差异 | `gofmt -l apps/judge-engine`、`git diff --check` 均无输出 |
| 开发文档 | `scripts/work check`：507 份通过，既有 WORK-033 状态提示保留 |
| 全局文档链接 | `python3 scripts/docs_test.py`：仅既有 WORKS → WORK-060 的未跟踪入口报错；未暂存其他工作以掩盖问题 |
| 真实 Linux 与业务闭环 | 本轮未运行；须由本候选的 CI 补齐，历史运行不替代 |

Python 全套首次受限运行在 TLS 夹具监听本机端口时发生 PermissionError，允许本机监听后 basic 全套
通过。这是执行环境限制，不计为产品失败。此次新增的源码测试已保存进仓库工作树，可按上述命令复跑；
临时日志不是唯一证据。原始 R1/R2/R3 反例先失败、修复后通过的过程另留在
`/tmp/work058-repair-before.log` 与本节对应测试中。

### 修复候选的独立复审

以下三位审查者均以新的空白会话检查当前工作树，没有参与修复、修改候选或代签人工闸。
复审覆盖新增测试文件；各自执行的测试与主协调全量检查分别记录。

| 审查者 | Agent ID | 范围 | 独立结论与执行证据 |
|---|---|---|---|
| Pascal | `01a0c7e9-df23-7053-b633-2399b5cef1a2` | R1–R4 | 无残留或新缺陷；backend/runner/pool 三包 `go test -mod=readonly -race -count=1 -v -timeout=90s` 通过。实际观察权限故障回滚且停单、取消竞态回归通过、WaitDelay 后停单；本次 childAlive=false，仅为该次观测 |
| Gibbs | `01a0c7e9-e341-7f00-bba9-c14bc6528f5b` | R5 | 无残留或新缺陷；212 组 errno/读取位置注入、真实采样循环 HEAD/候选的 20 组对照、后续组/PID 继续采样和四次脚本路径导入检查通过。确认 basic.ci 的现有发现规则纳入新增测试 |
| Russell | `01a0c7e9-e079-7de2-b215-66fbde82d823` | R6–R10 | 无残留或新缺陷；judge/flow/config/identity/probe/sandboxclient 六包 `go test -mod=readonly -race -count=1 -timeout=60s` 通过；对照原生部署检查核验 engine 说明 |

三位审查者的 Linux/systemd 与控制面集成都未实跑；isolated 与心跳使用替身。R7 短期限分支现在在
上传前被拒绝，其 SE 不是再次复现 HTTP 超时。`independent-review` 可记通过，整体回归仍为 partial。

### 提交整理检查（2026-09-22）

WORK-060 的工具、规范与入口以独立提交 `c9b2407` 纳入；本次 R1–R10 修复另行提交。
从真实暂存索引导出候选快照，排除 WORK-059 的 business_journal.py/business_test.py 未提交增量后，
重新运行 Python basic：五项 PASS，install 15、rootfs 27、ci 114 个测试，无跳过。上述早期工作树的
ci 116 个测试含那两项独立工作的测试，不能混称为本次提交的覆盖数量。
候选的 507 份工作文档、583 份 Markdown 入口与链接检查通过，WORK-060 未跟踪入口阻塞已消除；
WORK-033 的既有进度提示保留。没有提交 WORK-059 的两处脚本改动，也没有改动其文件内容。

### 首次提交 CI 与业务采样补修

[CI run 35698867685](https://github.com/charon2121/cherry-oj/actions/runs/35698867685)，attempt 1，
sourceSha `35d1d1c5e3a3440854e4cd9588cd3421da1a5416`。10 个 job 成功，business 和 summary 失败。
basic 5/5、kernel 63/63、native 10/10 全 PASS，三套资源清理也 PASS。

业务报告确认新环境、部署、校准通过；`browser-diagnostic.json` 的最终状态为 passed，
`execution-observations.json` 的 error 为 OSError，只有 CPU 样本，缺少内存执行证据。
Observer.__exit__ 因采样线程失败而拒绝整批验证，failure.json 的 history 只是最后浏览器阶段。
浏览器记录的 business.spec.ts:143 是 poll 内临时断言位置，不能据此把 passed 解释成浏览器整体失败。
该次采样错误没有导出 errno 与操作位置，因此不能断言该次异常必定是 ENODEV。

源码与注入测试另确认两项采样缺陷：发现执行组时只捕获 ENOENT/ESRCH，未处理 cgroup 消失的
ENODEV；读取已打开计数器时，目录消失会让 EACCES/EIO/EMFILE 也被吞掉。现在逐读取位置限定允许的
消失 errno，并增加固定操作名与有界 errno 诊断，不导出异常正文、凭据或任意路径；CPU/OOM/墙钟与
清理的验收条件不变。最初 3 项定向测试在原实现上出现 4 个失败和 1 个缺字段错误；补全到
4 项测试后，在修复实现上全部通过。
准确暂存快照的 Python basic 五项全过：install 15、rootfs 27、ci 118 项测试，无跳过。

Gibbs（Agent `01a0c7e9-e341-7f00-bba9-c14bc6528f5b`）独立复审采样补修，未发现可操作缺陷。
独立重跑完整四项回归：旧实现 8 个失败子项和 1 个错误，新实现通过；另运行 54 组错误注入，
核验已取得的 FD 均恰好关闭一次，敏感异常正文不被导出。verify_observations 的 AST 与旧提交相同，
15 组计量、时序、记录数量和消失确认反例仍被拒绝。纯 Python 验证不替代下面的真实 CI 复跑。

原始报告保存在本机 `/tmp/work058-ci-35698867685`；关键文件 SHA-256：

| 文件 | SHA-256 |
|---|---|
| execution-observations.json | `4a332264e80a3629731d1851b09b74230f0d31dbc7b06ea6f09ee7d53c679ff4` |
| browser-diagnostic.json | `dac8b147b2ad7201babfd62dd3e5064118a2543eb54e024442e52770e5ddc896` |
| summary.json | `d0ce1d060cb1d5d31b1ed27b5e95eec0555a0d4da84aa6372bd58179eddfe3f8` |

## 独立复核（2026-09-22）

### 对象、审查者与限制

审查区间：`a611be3d427fdfa03b724df0c1235148f897f648` →
`26eddff830f40fc46980e300036f1d3acf469c52`，只覆盖 WORK-058 已提交的实施范围。
三位 Agent 均以空白会话接收审查任务，没有继承实施会话，也没有修改被审查实现；主协调核对发现与
复现材料、保存证据。其他工作已有的未提交改动不计入本次审查。

| 审查者 | Agent ID | 分工 | 结论 |
|---|---|---|---|
| Newton | `01a0c7c6-4961-74b3-8799-744c0f86bd87` | backend、runner、pool、workspace、store、本机协议客户端 | 不通过：R1–R4 |
| Pauli | `01a0c7c6-4a79-7701-bb33-be5992b74eb3` | helperd、公共协议、故障回归脚本 | 不通过：R5 |
| Hooke | `01a0c7c6-4b72-7251-96a3-d512c315512f` | judge 生命周期、配置、身份、探测、部署与文档 | 不通过：R6–R10 |

执行环境为 macOS arm64、Go 1.26.3。Linux 专用代码做了交叉静态检查，未在本轮重跑真实
Linux/systemd 隔离与 Java 控制面集成。下面的模拟 helper/HTTP 测试证明调用方行为，不替代实际隔离
验证。历史 CI 与实施者自检仅保留为原有证据，没有被当作独立审查者的检查结果。

### 已确认发现

所有位置均相对本次审查的 HEAD；P1 需优先修复，P2 也须处理后才能认定本次方案完成。

| 编号 | 级别 | 位置 | 触发条件与影响 | 修复与验证方向 |
|---|---|---|---|---|
| R1 | P1 | `apps/judge-engine/sandbox/internal/backend/isolated.go:75–86` | 暂存目录删除失败时，defer 只改局部 `err`，不改变未具名的返回错误。真实 Isolated 测试返回 OK、产物引用仍可读、目录残留、池继续接单；基线会回滚产物并关闭池 | 让最终清理错误进入返回值，保留原错误链；补真实后端失败测试，同时断言产物回滚与后续拒单 |
| R2 | P1 | `apps/judge-engine/sandbox/internal/runner/sources.go:56,64–68` | 打开输入期间取消时，AfterFunc 回调可以早于 `s.stop` 赋值执行；赋值与回调读取无同步，定向 race 测试失败，基线同类取消时序通过 | 分离资源关闭与撤销回调，回调不读取尚未登记完成的停止函数；补解析输入期间取消的并发测试 |
| R3 | P2 | `apps/judge-engine/sandbox/internal/backend/devhost.go:117–121` | 父进程退出、子进程继续占用输出管道时，ErrWaitDelay 被当普通执行错误。子进程仍存活，池却继续接单；基线虽同样可能残留子进程，但会停单 | 区分启动失败与启动后回收未确认，尝试回收并在未确认时返回 CleanupError；断言后续请求被拒绝 |
| R4 | P2 | `apps/judge-engine/sandbox/internal/pool/pool.go:39,49,87` | pool 仍保存 Store，构造时要求 Store，直接调用 runner。TASK-128 的“pool 不再引用 store”已勾选，但这项约定没有实现 | 在装配层绑定执行依赖，pool 消费执行接口；用包依赖检查验证职责边界，纠正完成声明 |
| R5 | P2 | `deploy/sandbox-linux/tests/fault_batch.py:235–238` | 捕获全部 OSError，把 EACCES、EIO、EMFILE 也当作执行组消失。已有完整 UID 样本后发生这些错误，HEAD 仍通过断言，基线中止 | 按读取位置限定可容忍的消失 errno，其余重新抛出；补已有样本后的错误注入 |
| R6 | P2 | `apps/judge-engine/judge/judge.go:77–80` | Serve 异常退出后，Run 等待仍持有外部 ctx 的节点心跳协程，只有外部另行取消才返回；基线等待前主动 stop | 服务拥有自己的生命周期取消函数，先取消再等待；补监听器故障后的退出测试 |
| R7 | P2 | `apps/judge-engine/judge/internal/node/identity/identity.go:41`、`judge/internal/config/validation.go:56–61` | 已移除 sandboxTimeout 对指纹的影响，却只约束其大于编译墙钟。两份合法配置可以产生相同指纹，但同一次执行分别得到 SE 与 TLE；基线指纹不同 | 补全调用期限覆盖执行、排队及收尾预算的约束；在不能保证行为等价前，不应把相关超时认定为纯传输配置。同步核对指纹决定与 TASK-130 完成声明 |
| R8 | P2 | `apps/judge-engine/judge/internal/sandboxclient/client.go:40–43` | 复用客户端时丢失旧探测客户端的禁止重定向策略。307 夹具使 HEAD 跟随跳转并接受环境，基线拒绝且跳转目标收到零次请求 | 为探测恢复禁止跳转策略，验证 /version 和 /run；不要无意扩大对其他客户端操作的行为更改 |
| R9 | P2 | `apps/judge-engine/judge/internal/node/probe/probe.go:37`、`judge/internal/sandboxclient/client.go:97` | 复用 Run 丢失探测响应大小上限；Version 解码后未确认正文结束。1MiB 探测响应及版本 JSON 后的垃圾在 HEAD 被接受，基线均拒绝 | 恢复探测专用大小边界、超限检测与完整正文校验，补精确边界及尾随内容用例 |
| R10 | P2 | `docs/engine.md:76–84` | 文档宣称当前 Linux 原生节点的 judge 可放在另一台机器，但启动要求回环 sandbox，并读取本机部署清单、活动二进制和 cgroup。基线将远程部署写为未来能力 | 明确当前原生节点模式要求同机，区分二进制交付边界与实际部署能力，并纠正文档实走结论 |

R2 的首次审查还观察到回调 panic；已保存的重跑日志只证明数据竞争，不将该日志描述为再次复现崩溃。
R4 是未完成的设计要求，R10 是新文档错误，其余发现均对照了基线实现或行为。R6 的 HEAD 有故障注入
测试，基线“先 stop 再等待”为源码对照，不冒称跑过同一基线测试。

### 定向实验与原始证据

下方折叠附录保存复现夹具与原始输出，逐份给出 SHA-256。工作目录只允许受管理 Markdown 与
flow.json，因此证据内嵌于本文，不另增附件目录。S4 完整运行元数据另留在本机
`/tmp/cherry-work058-independent-review/archived-evidence/metadata.json`，S2/S3/S6 的执行元数据另留在
`/tmp/work058-s236-review-xrlyq0wq/evidence-s236/logs/runs.json`；本文已保存关键命令、完整提交 SHA
与结果，不依赖临时目录作为唯一证据。临时路径是当时的执行地点。复跑时将指定 SHA 的模块解包到临时目录，
把 head/baseline 测试原文分别放入对应 pool/runner 包并恢复 `.go` 后缀，再执行同名测试。
测试只在副本中注入故障；权限故障须以非 root 用户执行，socket 用例须允许本机监听。

| 定向实验 | HEAD | 基线 |
|---|---|---|
| `TestReviewRealIsolatedCleanupFailure` | 退出码 1；`status=OK residue=1 nextErr=<nil> artifactStillReadable=true` | 退出码 0；返回 InternalError、回滚引用并拒绝后续请求 |
| `TestReviewCancellationDuringSourceOpen` | 退出码 1；race 报告 sources.go:56 写、:66 读 | 退出码 0；同类取消时序循环 10,000 次通过 |
| `TestReviewDevhostWaitDelayStopsPool` | 退出码 1；`childAlive=true nextErr=<nil>` | 退出码 0；等待失败后关闭池 |
| `fault-observer.py.txt` 的 20 组 errno/位置/版本组合 | 已有完整 UID 样本后，EACCES/EIO/EMFILE 被吞掉，UID 断言通过 | 同样故障抛出异常 |
| `TestReviewServeFailureStopsRegistry` | Serve 故障后需外部取消才返回 | 未跑同一测试；源码确认先取消再等待 |
| `TestReviewTimeout*` | 合法配置的 25ms/100ms 调用期限面对 50ms TLE 响应时，指纹相同而判题结论不同 | 不同超时的指纹不同 |
| `TestReviewProbeTransport*` | 跟随 307、接受超大探测响应与版本 JSON 尾随垃圾 | 对应输入均拒绝 |

前三项均以 `go test -mod=readonly -race -count=1 -run '^测试名$' -v -timeout=30s` 执行，
HEAD 的包为 `./sandbox/internal/pool`、`./sandbox/internal/runner`，基线包前加 `internal/`。
故障脚本实验提取两个提交的身份采样原始 AST，在已采到两个槽位 UID 后分别向 cgroup/proc 读取注入
ENOENT、ENODEV、EACCES、EIO、EMFILE。`fault-observer.jsonl` 保存逐组输出；复跑原文时应将其
`ROOT` 指向分别含 `base/` 与 `head/` 两个提交副本的临时目录。

主协调另执行：现有 `go test -race ./...` 通过（部分命中缓存），
`GOOS=linux GOARCH=amd64 go vet ./...` 通过。首次受限执行因不允许监听本机 socket 失败，获得工具
执行权限后重跑通过；这类环境失败未计作代码缺陷。已有测试与新增定向反例的差异表明覆盖不足。

工作项结构校验通过（506 份，另有既存 WORK-033 提示）。全局文档链接校验仍被已有的 WORK-060
未跟踪文件阻断；该问题在本轮改动前已经存在，未为通过检查而暂存其他工作的文件。

### 处理顺序与完成条件

先修 R1/R2，再处理 R3/R5/R6；随后补齐 R7–R9 的预算与探测边界、R4 的职责收缩，最后校正文档 R10
及原有过度完成声明。各修复须补能在旧实现上失败的回归用例，并在最终候选上完成全量测试、Linux
隔离回归与独立复审。初次审查结束时以上问题均未修复；后续处置见「TASK-133 修复验证」，不回写
历史反例为通过。

## 范围检查

比较区间 `a611be3..bb2f0a7`（基线为 WORK-049 最终候选，终点为本工作的收尾提交）。

**禁止修改的路径全部为 0 个文件改动**，逐条核对：`contracts/`、`apps/server`、`apps/web`、
`apps/judge-engine/go.mod`、`apps/judge-engine/go.sum`、`docs/architecture.md`、`docs/product.md`、
`deploy/sandbox-linux/{install,rootfs,systemd,build-release.sh,probe.sh}`、
`.github/workflows/{language-diagnostic.yml,sandbox-download-cold.yml}`、`scripts/`。

**WORK-049 与 WORK-050 的既有增量未被覆盖**：区间内 `development/works/WORK-049` 与
`development/works/WORK-050` 下 0 个文件改动。

**实际改动面**：`apps/judge-engine` 228 个文件、`development` 23 个、`deploy/sandbox-linux` 11 个、
`docs` 2 个、`.github` 1 个、`compose.yaml` 1 个。

其中**模块之外的改动是实施过程中经用户逐次同意扩充的范围**，逐个登记如下——它们都落在各 TASK 的
`write_paths` 内，但比立项时设想的多，因此在这里单列：

| 路径 | 为什么动 |
|---|---|
| `deploy/sandbox-linux/ci/cases.json` 等 5 个 | 必跑用例清单随包路径同步。清单原先散在三处（`cases.json`、`prepare.py` 的 go test 参数、`results.py` 的硬编码包集合），已收敛成以 `cases.json` 为单一来源、另两处派生 |
| `deploy/sandbox-linux/ci/kernel.py` | helper socket 超时时带出 helper 自己的输出——S5 的诊断缺陷，不补它就只能靠猜 |
| `deploy/sandbox-linux/tests/fault_batch.py` | 既有缺陷：读被删除 cgroup 的 `cgroup.procs` 时只捕获 `FileNotFoundError`，而 cgroup v2 返回 `ENODEV`。留着会持续污染本工作后续每一轮回归的信号 |
| `deploy/sandbox-linux/ci/business_{journal,stack,test}.py` | 业务请求失败时导出服务端自己的结构化字段。新增 `business_journal.py`（白名单导出，不读 message 正文） |
| `deploy/sandbox-linux/tests/README.md` | 同步失效的包路径 |
| `.github/workflows/ci.yml` | 1 行：`TESTDATA_PATH` 指向移动后的测试数据目录。job 结构、触发条件、权限、步骤顺序均未动 |
| `compose.yaml` | 5 行：后端名 `trusted-host` → `devhost`，并补 `allowUnsafeBackend`。服务定义、网络、卷与健康检查均未动 |
| `docs/engine.md`、`docs/coding-standards/languages/go.md` | TASK-131 的既定产出 |

另有两份文档在 `apps/judge-engine` 内、但不在立项时的设想里：`apps/judge-engine/README.md` 与
`helperd/internal/helper/README.md`。它们的链接与类型名全部指向旧结构，属于「结构变更的影响面」
而非新增范围，已一并重写（理由见 TASK-131 执行记录）。

## 遗留问题

独立复核 R1–R10 均未解决；修复及复审前保持 WORK-058 未收束。

## 剩余风险

实施前已知的风险见 [DESIGN-051](30-design-DESIGN-051.md) 「风险与重审条件」与
[PLAN-041](50-plan-PLAN-041.md) 「风险」。验证完成后的实际剩余项如下。

1. **`ACTIVE` 环境的切换仍是人工动作**，不在本次验证范围内。在它完成之前，新结构不参与实际判题
   路由——也就是说 CI 全绿并不等于新结构已经在生产路径上跑过。
2. **环境指纹已全量轮换。** `executableDigest()` 参与指纹计算，因此本工作每个阶段的二进制变化都
   产生了新指纹，历史标定对新指纹不成立。控制面只会把新指纹记为 `REGISTERED`，不会静默替换
   `ACTIVE`，所以这是一次需要人工确认的切换，而不是风险事件——但**切换前必须重新做一次标定**。
3. **顶层 `internal/` 是绕过信任边界的潜在通道。** `contract`、`hostexec`、`platform` 三个包对三棵
   子树同时可见，这是必要的；但新增顶层共享包等于在编译器守着的边界上开一个口子。已在
   MEMORY-044 记明：新增顶层共享包必须在 DECISION-035 上追加记录。目前没有自动化手段拦住它。
4. **`independent-review` 已执行但不通过。** 2026-09-22 的发现见本文件「独立复核」，
   需要修复与复审；历史 CI 和人工签署不能消除新发现的反例。
5. **业务闭环曾观测到偶发 500**（`PATCH /api/admin/problems/{id}`），
   已另行立项为 [WORK-059](../WORK-059/00-work.md) / ISSUE-020；根因仍需证据确认。

## 结论

**当前为部分通过：已跑回归通过，独立复核不通过。** R1–R10 需要修复和复审，WORK-058 不能进入
`verified`。不重新解释或代签原有人工闸。

### 2026-09-15 的回归结论（历史）

**通过。** S1–S7 的结构重切在最终候选上一次性通过全部 12 个 CI job 与 93 项必需回归，
AC-001 至 AC-012 均已取得证据（逐条出处见上方各阶段记录与各 TASK 执行记录）。

两点需要接收方知道，它们不影响本次结论但会影响之后的动作：

1. **`ACTIVE` 环境的切换仍是人工动作**，不在本次验证范围内；在它完成之前，新结构不参与实际判题路由。
2. **本工作全程轮换环境指纹**：`executableDigest()` 参与指纹计算，因此每个阶段的二进制变化都会
   产生新指纹。控制面只会把新指纹记为 `REGISTERED`，不会静默替换 `ACTIVE`。

## 变更记录

- 2026-09-22：按用户授权恢复历史 ZIP 并补做三位独立 Agent 复核。确认两项 P1、八项 P2；保存新旧版本
  定向实验及原始材料，将当前 result 由 pass 改为 partial。人工签署记录保留，未修改业务实现。
- 2026-09-15：状态变更：draft → review。原因：S1-S7 全部完成，CI 34938772322 一次通过 12 个 job 与 93 项必需回归，等待人工复核与验收闸
- 2026-09-15：验收闸通过：review → approved。原因：CI 34938772322 一次通过，93 项必需回归全绿，AC-001 至 AC-012 均有证据。验收通过。

## 独立复核原始证据附录

以下均为临时副本中的审查夹具与输出，未安装到业务代码或正式测试中。复跑应先读取夹具，
在非 root 临时副本中使用；文件名为保存原文的标签。摘录保留测试时的排版与绝对路径，仅去掉行尾空白；下列 SHA-256 针对附录保存的文本。

<details>
<summary>baseline-pool_test.go.txt</summary>

SHA-256：`82139203791651abecfc281b5aefb83db902331a223e529260229802c86192f4`

```go
package pool_test
import (
 "context"
 "errors"
 "io"
 "net"
 "os"
 "path/filepath"
 "strconv"
 "strings"
 "syscall"
 "testing"
 "time"
 "cherry-oj/judge-engine/internal/contract"
 "cherry-oj/judge-engine/internal/sandbox/container"
"cherry-oj/judge-engine/internal/sandbox/helper"
"cherry-oj/judge-engine/internal/sandbox/launcher"
"cherry-oj/judge-engine/internal/sandbox/store"
"cherry-oj/judge-engine/internal/sandbox/pool"
)
type failCleanupStore struct { store.Store; root, ref string }
func(s *failCleanupStore) Put(r io.Reader)(string,error){
 ref,err:=s.Store.Put(r); if err!=nil{return ref,err}; s.ref=ref
 return ref,os.Chmod(s.root,0500)
}
func TestReviewRealIsolatedCleanupFailure(t *testing.T){
 if os.Geteuid()==0{t.Skip("permission fault needs non-root")}
 dir,err:=os.MkdirTemp("/tmp","s4-wire-"); if err!=nil{t.Fatal(err)}; defer os.RemoveAll(dir)
 l,err:=net.Listen("unix",filepath.Join(dir,"s")); if err!=nil{t.Fatal(err)}; defer l.Close()
 done:=make(chan error,1)
 go func(){
  c,e:=l.Accept(); if e!=nil{done<-e;return}; defer c.Close()
  c.SetDeadline(time.Now().Add(5*time.Second))
  var req launcher.Request
  if e=launcher.ReadFrame(c,&req,launcher.MaxFrameBytes);e!=nil{done<-e;return}
  if _,e=io.CopyN(io.Discard,c,req.InputBytes());e!=nil{done<-e;return}
  if e=launcher.WriteFrame(c,helper.Result{Version:1,Outputs:[]helper.Output{{Path:"out",SizeBytes:3}}},4<<20);e!=nil{done<-e;return}
  if _,e=io.WriteString(c,"elf");e!=nil{done<-e;return}
  done<-launcher.WriteFrame(c,helper.Completion{Version:1,Complete:true},1024)
 }()
 staging:=filepath.Join(t.TempDir(),"staging")
 ws,err:=container.OpenWorkspace(staging);if err!=nil{t.Fatal(err)}
 disk,err:=store.NewDiskStoreWithRoot(filepath.Join(t.TempDir(),"store"));if err!=nil{t.Fatal(err)};defer disk.Close()
 st:=&failCleanupStore{Store:disk,root:staging}
 p,err:=pool.New(st,pool.Options{Parallelism:1,QueueSize:1,Factory:func()(container.Container,error){return ws.New(filepath.Join(dir,"s"))}})
 if err!=nil{t.Fatal(err)}
 defer func(){os.Chmod(staging,0700);p.Close();entries,_:=filepath.Glob(filepath.Join(staging,"execution-*"));for _,e:=range entries{os.RemoveAll(e)};ws.Close()}()
 res,err:=p.Run(context.Background(),contract.RunSpec{Command:[]string{"true"},Artifacts:[]string{"out"}})
 if serverErr:=<-done;serverErr!=nil{t.Fatal(serverErr)}
 residue,_:=filepath.Glob(filepath.Join(staging,"execution-*"))
 _,nextErr:=p.Run(context.Background(),contract.RunSpec{})
 rc,getErr:=st.Get(st.ref);if getErr==nil{rc.Close()}
 t.Logf("status=%s runErr=%v refs=%v residue=%d nextErr=%v artifactStillReadable=%v",res.Status,err,res.Artifacts,len(residue),nextErr,getErr==nil)
 if len(residue)==0{t.Fatal("fault did not leave a directory")}
 if res.Status!=contract.StatusInternalError || len(res.Artifacts)!=0 || !errors.Is(nextErr,pool.ErrClosed) || getErr==nil {t.Fatal("cleanup failure published artifact or kept pool open")}
}
func TestReviewDevhostWaitDelayStopsPool(t *testing.T){
 disk,err:=store.NewDiskStoreWithRoot(filepath.Join(t.TempDir(),"store"));if err!=nil{t.Fatal(err)};defer disk.Close()
 p,err:=pool.New(disk,pool.Options{Parallelism:1,QueueSize:1,Factory:func()(container.Container,error){return container.NewHost()}})
 if err!=nil{t.Fatal(err)};defer p.Close()
 marker:=filepath.Join(t.TempDir(),"child.pid")
 // The shell exits successfully while its child retains stdout/stderr.
 script:="sleep 30 & echo $! > \""+marker+"\"; exit 0"
 res,err:=p.Run(context.Background(),contract.RunSpec{Command:[]string{"sh","-c",script}})
 data,e:=os.ReadFile(marker);if e!=nil{t.Fatal(e)}
 pid,e:=strconv.Atoi(strings.TrimSpace(string(data)));if e!=nil{t.Fatal(e)}
 defer syscall.Kill(pid,syscall.SIGKILL)
 alive:=syscall.Kill(pid,0)==nil
 _,nextErr:=p.Run(context.Background(),contract.RunSpec{})
 t.Logf("status=%s error=%q runErr=%v childAlive=%v nextErr=%v",res.Status,res.Error,err,alive,nextErr)
 if !alive{t.Fatal("scenario did not retain child")}
 if !errors.Is(nextErr,pool.ErrClosed){t.Fatal("pool admits work after WaitDelay left a live child")}
}
```

</details>

<details>
<summary>baseline-runner_test.go.txt</summary>

SHA-256：`1e93a321e3efb7628cffe4ffacf29bc3dae692a2f5c7217e5c16aee88ce5b686`

```go
package runner
import (
 "context"
 "io"
 "strings"
 "testing"
 "cherry-oj/judge-engine/internal/contract"
 "cherry-oj/judge-engine/internal/sandbox/container"
)
type reviewCancellingStore struct{cancel context.CancelFunc}
func(s reviewCancellingStore)Get(string)(io.ReadCloser,error){s.cancel();return io.NopCloser(strings.NewReader("input")),nil}
func(s reviewCancellingStore)Put(io.Reader)(string,error){return "",nil}
func(s reviewCancellingStore)Delete(string)error{return nil}
func TestReviewCancellationDuringSourceOpen(t *testing.T){
 for i:=0;i<10000;i++{
  ctx,cancel:=context.WithCancel(context.Background())
  c,err:=container.NewHost(); if err!=nil{t.Fatal(err)}
  Run(ctx,c,reviewCancellingStore{cancel},contract.RunSpec{Command:[]string{"true"},Stdin:&contract.FileSource{Ref:"input"}})
  c.Close()
  cancel()
 }
}
```

</details>

<details>
<summary>cancel-source-open-baseline.log</summary>

SHA-256：`15401ee4e58c1442f56010ffd083018b579a05ea6a540d464da433b9388ec934`

```text
=== RUN   TestReviewCancellationDuringSourceOpen
--- PASS: TestReviewCancellationDuringSourceOpen (2.25s)
PASS
ok  	cherry-oj/judge-engine/internal/sandbox/runner	3.537s
```

</details>

<details>
<summary>cancel-source-open-head.log</summary>

SHA-256：`99ed91af0bf35e2f35c101cc14573fee1c0d1c27a6ae7e75ef7730b27666a60d`

```text
=== RUN   TestReviewCancellationDuringSourceOpen
==================
WARNING: DATA RACE
Read at 0x00c0002e26f8 by goroutine 55:
  cherry-oj/judge-engine/sandbox/internal/runner.openSources.func1.(*sources).close.1()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/sources.go:66 +0x3c
  sync.(*Once).doSlow()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/sync/once.go:78 +0x94

Previous write at 0x00c0002e26f8 by goroutine 8:
  cherry-oj/judge-engine/sandbox/internal/runner.openSources()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/sources.go:56 +0x818
  cherry-oj/judge-engine/sandbox/internal/runner.Run()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/runner.go:21 +0x144
  cherry-oj/judge-engine/sandbox/internal/runner.TestReviewCancellationDuringSourceOpen()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/review_race_test.go:16 +0x98
  testing.tRunner()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2036 +0x164
  testing.(*T).Run.gowrap1()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2101 +0x34

Goroutine 55 (running) created at:
  context.(*afterFuncCtx).cancel.func1()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/context/context.go:358 +0x40
  sync.(*Once).doSlow()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/sync/once.go:78 +0x94
  context.(*cancelCtx).propagateCancel()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/context/context.go:486 +0x1d8
  context.AfterFunc()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/context/context.go:329 +0xcc
  cherry-oj/judge-engine/sandbox/internal/runner.openSources()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/sources.go:56 +0x808
  cherry-oj/judge-engine/sandbox/internal/runner.Run()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/runner.go:21 +0x144
  cherry-oj/judge-engine/sandbox/internal/runner.TestReviewCancellationDuringSourceOpen()
      /private/tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine/sandbox/internal/runner/review_race_test.go:16 +0x98
  testing.tRunner()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2036 +0x164
  testing.(*T).Run.gowrap1()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2101 +0x34

Goroutine 8 (running) created at:
  testing.(*T).Run()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2101 +0x7bc
  testing.runTests.func1()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2585 +0x74
  testing.tRunner()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2036 +0x164
  testing.runTests()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2583 +0x7a0
  testing.(*M).Run()
      /opt/homebrew/Cellar/go/1.26.3/libexec/src/testing/testing.go:2443 +0xb38
  main.main()
      _testmain.go:78 +0x100
==================
    testing.go:1712: race detected during execution of test
--- FAIL: TestReviewCancellationDuringSourceOpen (0.57s)
FAIL
FAIL	cherry-oj/judge-engine/sandbox/internal/runner	0.833s
FAIL
```

</details>

<details>
<summary>commands.sh.txt</summary>

SHA-256：`042d2d7d318b6d5cc1e7a7ab381c1088025d58f7241ef8e615a3d474eaa2250e`

```sh
#!/bin/sh
# Independent S4 review; commands intentionally continue after expected failing HEAD tests.

cd /tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine && env TMPDIR=/tmp GOCACHE=/tmp/cherry-work058-review-go-cache GOPROXY=off go test -mod=readonly -race -count=1 -run '^TestReviewRealIsolatedCleanupFailure$' -v -timeout=30s ./sandbox/internal/pool > /tmp/work058-s4-review-cqwqe1j7/evidence-20260922T062924Z/isolated-cleanup-head.log 2>&1

cd /tmp/work058-s4-review-cqwqe1j7/baseline/apps/judge-engine && env TMPDIR=/tmp GOCACHE=/tmp/cherry-work058-review-go-cache GOPROXY=off go test -mod=readonly -race -count=1 -run '^TestReviewRealIsolatedCleanupFailure$' -v -timeout=30s ./internal/sandbox/pool > /tmp/work058-s4-review-cqwqe1j7/evidence-20260922T062924Z/isolated-cleanup-baseline.log 2>&1

cd /tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine && env TMPDIR=/tmp GOCACHE=/tmp/cherry-work058-review-go-cache GOPROXY=off go test -mod=readonly -race -count=1 -run '^TestReviewDevhostWaitDelayStopsPool$' -v -timeout=30s ./sandbox/internal/pool > /tmp/work058-s4-review-cqwqe1j7/evidence-20260922T062924Z/devhost-waitdelay-head.log 2>&1

cd /tmp/work058-s4-review-cqwqe1j7/baseline/apps/judge-engine && env TMPDIR=/tmp GOCACHE=/tmp/cherry-work058-review-go-cache GOPROXY=off go test -mod=readonly -race -count=1 -run '^TestReviewDevhostWaitDelayStopsPool$' -v -timeout=30s ./internal/sandbox/pool > /tmp/work058-s4-review-cqwqe1j7/evidence-20260922T062924Z/devhost-waitdelay-baseline.log 2>&1

cd /tmp/work058-s4-review-cqwqe1j7/head/apps/judge-engine && env TMPDIR=/tmp GOCACHE=/tmp/cherry-work058-review-go-cache GOPROXY=off go test -mod=readonly -race -count=1 -run '^TestReviewCancellationDuringSourceOpen$' -v -timeout=30s ./sandbox/internal/runner > /tmp/work058-s4-review-cqwqe1j7/evidence-20260922T062924Z/cancel-source-open-head.log 2>&1

cd /tmp/work058-s4-review-cqwqe1j7/baseline/apps/judge-engine && env TMPDIR=/tmp GOCACHE=/tmp/cherry-work058-review-go-cache GOPROXY=off go test -mod=readonly -race -count=1 -run '^TestReviewCancellationDuringSourceOpen$' -v -timeout=30s ./internal/sandbox/runner > /tmp/work058-s4-review-cqwqe1j7/evidence-20260922T062924Z/cancel-source-open-baseline.log 2>&1
```

</details>

<details>
<summary>devhost-waitdelay-baseline.log</summary>

SHA-256：`e296f77155a16855a149c99c3e5f09ce3d4c3ba3fa41f4bb72c3f667b7f0c1b6`

```text
=== RUN   TestReviewDevhostWaitDelayStopsPool
    review_repro_test.go:70: status=InternalError error="容器清理失败\nexec: WaitDelay expired before I/O complete" runErr=<nil> childAlive=true nextErr=sandbox pool is closed
--- PASS: TestReviewDevhostWaitDelayStopsPool (2.01s)
PASS
ok  	cherry-oj/judge-engine/internal/sandbox/pool	3.294s
```

</details>

<details>
<summary>devhost-waitdelay-head.log</summary>

SHA-256：`49cdc1a4234dec03b58f7853813b983340cb41b47ea7b00b26e497dd15ba7c75`

```text
=== RUN   TestReviewDevhostWaitDelayStopsPool
    review_repro_test.go:70: status=InternalError error="exec: WaitDelay expired before I/O complete" runErr=<nil> childAlive=true nextErr=<nil>
    review_repro_test.go:72: pool admits work after WaitDelay left a live child
--- FAIL: TestReviewDevhostWaitDelayStopsPool (2.02s)
FAIL
FAIL	cherry-oj/judge-engine/sandbox/internal/pool	2.312s
FAIL
```

</details>

<details>
<summary>fault-observer.jsonl</summary>

SHA-256：`1f2b4056d107e5fd02cca21162d262e200203de4bd0ec7c95ee14332a28cf23b`

```text
{"errno": "ENOENT", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "cgroup.procs", "version": "base"}
{"errno": "ENOENT", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "cgroup.procs", "version": "head"}
{"errno": "ENODEV", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=19", "site": "cgroup.procs", "version": "base"}
{"errno": "ENODEV", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "cgroup.procs", "version": "head"}
{"errno": "EACCES", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=13", "site": "cgroup.procs", "version": "base"}
{"errno": "EACCES", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "cgroup.procs", "version": "head"}
{"errno": "EIO", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=5", "site": "cgroup.procs", "version": "base"}
{"errno": "EIO", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "cgroup.procs", "version": "head"}
{"errno": "EMFILE", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=24", "site": "cgroup.procs", "version": "base"}
{"errno": "EMFILE", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "cgroup.procs", "version": "head"}
{"errno": "ENOENT", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "proc.status", "version": "base"}
{"errno": "ENOENT", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "proc.status", "version": "head"}
{"errno": "ENODEV", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=19", "site": "proc.status", "version": "base"}
{"errno": "ENODEV", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "proc.status", "version": "head"}
{"errno": "EACCES", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=13", "site": "proc.status", "version": "base"}
{"errno": "EACCES", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "proc.status", "version": "head"}
{"errno": "EIO", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=5", "site": "proc.status", "version": "base"}
{"errno": "EIO", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "proc.status", "version": "head"}
{"errno": "EMFILE", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "ABORT errno=24", "site": "proc.status", "version": "base"}
{"errno": "EMFILE", "initUIDs": [61003, 61005], "injected": 1, "payloadUIDs": [61002, 61004], "result": "UID assertion PASSED", "site": "proc.status", "version": "head"}
```

</details>

<details>
<summary>fault-observer.py.txt</summary>

SHA-256：`b439cb0abc3ff78161861ffd1af15f5c0efa271572e0ebcb91bb6da9b56ed95b`

```python
"""Execute the committed identity-observation block with deterministic read faults."""
from __future__ import annotations
import ast
import errno
from pathlib import Path
from types import SimpleNamespace

ROOT = Path('/tmp/work058-helper-review-mwgdbniv')

def run(version: str, error_number: int, fault_site: str) -> dict:
    path = ROOT / version / 'deploy/sandbox-linux/tests/fault_batch.py'
    tree = ast.parse(path.read_text())
    fn = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'capacity_cases')
    block = next(n for n in fn.body if isinstance(n, ast.With))
    # Exact assignment, observation loop, and final UID assertion; no service setup is executed.
    nodes = block.body[1:4]
    assert isinstance(nodes[0], ast.Assign) and isinstance(nodes[1], ast.While) and isinstance(nodes[2], ast.Assert)
    code = compile(ast.Module(body=nodes, type_ignores=[]), str(path), 'exec')
    state = {'iteration': 0, 'done': False, 'injected': 0}
    class Future:
        def done(self):
            return state['done']
    class FakePath:
        def __init__(self, *parts):
            self.parts = parts
        def __truediv__(self, name):
            return FakePath(*self.parts, name)
        def read_text(self):
            site = 'cgroup.procs' if self.parts[-1] == 'cgroup.procs' else 'proc.status'
            if state['iteration'] == 2 and site == fault_site:
                state['injected'] += 1
                raise OSError(error_number, 'injected read failure', '/'.join(self.parts))
            if site == 'cgroup.procs':
                return '61002 61003 61004 61005'
            return f'Uid:\t{self.parts[1]}\t{self.parts[1]}\t{self.parts[1]}\t{self.parts[1]}\n'
    def groups():
        state['iteration'] += 1
        return [FakePath('jobs', 'run-test')]
    def sleep(_):
        if state['iteration'] >= 2:
            state['done'] = True
    ns = dict(paired=[Future(), Future()], group_paths=groups, Path=FakePath, time=SimpleNamespace(sleep=sleep))
    try:
        exec(code, ns)
        result = 'UID assertion PASSED'
    except OSError as exc:
        result = f'ABORT errno={exc.errno}'
    return dict(version=version, errno=errno.errorcode[error_number], site=fault_site, result=result,
                injected=state['injected'], payloadUIDs=sorted(ns['payload_ids']), initUIDs=sorted(ns['init_ids']))

if __name__ == '__main__':
    import json
    for site in ('cgroup.procs', 'proc.status'):
        for number in (errno.ENOENT, errno.ENODEV, errno.EACCES, errno.EIO, errno.EMFILE):
            for version in ('base', 'head'):
                print(json.dumps(run(version, number, site), sort_keys=True))
```

</details>

<details>
<summary>head-pool_test.go.txt</summary>

SHA-256：`f5add25d2601ddf9e78592e22d23e391225566c452abb8b3695760d61ea52b98`

```go
package pool_test
import (
 "context"
 "errors"
 "io"
 "net"
 "os"
 "path/filepath"
 "strconv"
 "strings"
 "syscall"
 "testing"
 "time"
 "cherry-oj/judge-engine/internal/contract"
 "cherry-oj/judge-engine/internal/hostexec"
"cherry-oj/judge-engine/sandbox/internal/backend"
"cherry-oj/judge-engine/sandbox/internal/workspace"
"cherry-oj/judge-engine/sandbox/internal/store"
"cherry-oj/judge-engine/sandbox/internal/pool"
)
type failCleanupStore struct { store.Store; root, ref string }
func(s *failCleanupStore) Put(r io.Reader)(string,error){
 ref,err:=s.Store.Put(r); if err!=nil{return ref,err}; s.ref=ref
 return ref,os.Chmod(s.root,0500)
}
func TestReviewRealIsolatedCleanupFailure(t *testing.T){
 if os.Geteuid()==0{t.Skip("permission fault needs non-root")}
 dir,err:=os.MkdirTemp("/tmp","s4-wire-"); if err!=nil{t.Fatal(err)}; defer os.RemoveAll(dir)
 l,err:=net.Listen("unix",filepath.Join(dir,"s")); if err!=nil{t.Fatal(err)}; defer l.Close()
 done:=make(chan error,1)
 go func(){
  c,e:=l.Accept(); if e!=nil{done<-e;return}; defer c.Close()
  c.SetDeadline(time.Now().Add(5*time.Second))
  var req hostexec.Request
  if e=hostexec.ReadFrame(c,&req,hostexec.MaxFrameBytes);e!=nil{done<-e;return}
  if _,e=io.CopyN(io.Discard,c,req.InputBytes());e!=nil{done<-e;return}
  if e=hostexec.WriteFrame(c,hostexec.Result{Version:1,Outputs:[]hostexec.Output{{Path:"out",SizeBytes:3}}},4<<20);e!=nil{done<-e;return}
  if _,e=io.WriteString(c,"elf");e!=nil{done<-e;return}
  done<-hostexec.WriteFrame(c,hostexec.Completion{Version:1,Complete:true},1024)
 }()
 staging:=filepath.Join(t.TempDir(),"staging")
 ws,err:=workspace.OpenWorkspace(staging);if err!=nil{t.Fatal(err)}
 disk,err:=store.NewDiskStoreWithRoot(filepath.Join(t.TempDir(),"store"));if err!=nil{t.Fatal(err)};defer disk.Close()
 st:=&failCleanupStore{Store:disk,root:staging}
 b,err:=backend.NewIsolated(filepath.Join(dir,"s"),ws);if err!=nil{t.Fatal(err)}; p,err:=pool.New(st,b,pool.Options{Parallelism:1,QueueSize:1})
 if err!=nil{t.Fatal(err)}
 defer func(){os.Chmod(staging,0700);p.Close();entries,_:=filepath.Glob(filepath.Join(staging,"execution-*"));for _,e:=range entries{os.RemoveAll(e)};ws.Close()}()
 res,err:=p.Run(context.Background(),contract.RunSpec{Command:[]string{"true"},Artifacts:[]string{"out"}})
 if serverErr:=<-done;serverErr!=nil{t.Fatal(serverErr)}
 residue,_:=filepath.Glob(filepath.Join(staging,"execution-*"))
 _,nextErr:=p.Run(context.Background(),contract.RunSpec{})
 rc,getErr:=st.Get(st.ref);if getErr==nil{rc.Close()}
 t.Logf("status=%s runErr=%v refs=%v residue=%d nextErr=%v artifactStillReadable=%v",res.Status,err,res.Artifacts,len(residue),nextErr,getErr==nil)
 if len(residue)==0{t.Fatal("fault did not leave a directory")}
 if res.Status!=contract.StatusInternalError || len(res.Artifacts)!=0 || !errors.Is(nextErr,pool.ErrClosed) || getErr==nil {t.Fatal("cleanup failure published artifact or kept pool open")}
}
func TestReviewDevhostWaitDelayStopsPool(t *testing.T){
 disk,err:=store.NewDiskStoreWithRoot(filepath.Join(t.TempDir(),"store"));if err!=nil{t.Fatal(err)};defer disk.Close()
 p,err:=pool.New(disk,backend.NewDevHost(),pool.Options{Parallelism:1,QueueSize:1})
 if err!=nil{t.Fatal(err)};defer p.Close()
 marker:=filepath.Join(t.TempDir(),"child.pid")
 // The shell exits successfully while its child retains stdout/stderr.
 script:="sleep 30 & echo $! > \""+marker+"\"; exit 0"
 res,err:=p.Run(context.Background(),contract.RunSpec{Command:[]string{"sh","-c",script}})
 data,e:=os.ReadFile(marker);if e!=nil{t.Fatal(e)}
 pid,e:=strconv.Atoi(strings.TrimSpace(string(data)));if e!=nil{t.Fatal(e)}
 defer syscall.Kill(pid,syscall.SIGKILL)
 alive:=syscall.Kill(pid,0)==nil
 _,nextErr:=p.Run(context.Background(),contract.RunSpec{})
 t.Logf("status=%s error=%q runErr=%v childAlive=%v nextErr=%v",res.Status,res.Error,err,alive,nextErr)
 if !alive{t.Fatal("scenario did not retain child")}
 if !errors.Is(nextErr,pool.ErrClosed){t.Fatal("pool admits work after WaitDelay left a live child")}
}
```

</details>

<details>
<summary>head-runner_test.go.txt</summary>

SHA-256：`9b6212e48bc91faccf30a7e3fc075c9eae46df08059e68931b4d199b68d0ffdf`

```go
package runner
import (
 "context"
 "io"
 "strings"
 "testing"
 "cherry-oj/judge-engine/internal/contract"
)
type reviewCancellingStore struct{cancel context.CancelFunc}
func(s reviewCancellingStore)Get(string)(io.ReadCloser,error){s.cancel();return io.NopCloser(strings.NewReader("input")),nil}
func(s reviewCancellingStore)Put(io.Reader)(string,error){return "",nil}
func(s reviewCancellingStore)Delete(string)error{return nil}
func TestReviewCancellationDuringSourceOpen(t *testing.T){
 for i:=0;i<10000;i++{
  ctx,cancel:=context.WithCancel(context.Background())
  Run(ctx,&lifecycleBackend{},reviewCancellingStore{cancel},contract.RunSpec{Command:[]string{"true"},Stdin:&contract.FileSource{Ref:"input"}})
  cancel()
 }
}
```

</details>

<details>
<summary>isolated-cleanup-baseline.log</summary>

SHA-256：`64dd72ae27e85d64beabc9f81514b27e627a8e56c40f65b18f2152406c29d9e0`

```text
=== RUN   TestReviewRealIsolatedCleanupFailure
    review_repro_test.go:53: status=InternalError runErr=<nil> refs=map[] residue=1 nextErr=sandbox pool is closed artifactStillReadable=false
--- PASS: TestReviewRealIsolatedCleanupFailure (0.00s)
PASS
ok  	cherry-oj/judge-engine/internal/sandbox/pool	1.268s
```

</details>

<details>
<summary>isolated-cleanup-head.log</summary>

SHA-256：`54a04242f93d88aa69a6f230cfb7e1464a9be0f1f7b865301ecf3e75a16a9b5d`

```text
=== RUN   TestReviewRealIsolatedCleanupFailure
    review_repro_test.go:53: status=OK runErr=<nil> refs=map[out:8e79056637961231e41f793d4a93ff5c] residue=1 nextErr=<nil> artifactStillReadable=true
    review_repro_test.go:55: cleanup failure published artifact or kept pool open
--- FAIL: TestReviewRealIsolatedCleanupFailure (0.00s)
FAIL
FAIL	cherry-oj/judge-engine/sandbox/internal/pool	0.287s
FAIL
```

</details>

<details>
<summary>fixtures/base/apps/judge-engine/internal/judge/node/review_timeout_test.go</summary>

SHA-256：`d5fff7c2f0326cb7130200566b84000e7bfbb44cff5c92be3a85c02ff92f2e28`

```go
package node_test
import (
 "context"
 "encoding/json"
 "net/http"
 "net/http/httptest"
 "testing"
 "time"
 "cherry-oj/judge-engine/internal/contract"
 "cherry-oj/judge-engine/internal/config"

 "cherry-oj/judge-engine/internal/judge/flow"
 "cherry-oj/judge-engine/internal/judge/client"
 "cherry-oj/judge-engine/internal/judge/node"
)
func TestReviewTimeoutCanChangeVerdictWithoutChangingFingerprint(t *testing.T) {
 sb:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {
  if r.Method==http.MethodDelete {w.WriteHeader(204);return}
  if r.URL.Path=="/blobs" {w.Write([]byte(`{"ref":"src"}`));return}
  var spec contract.RunSpec
  if e:=json.NewDecoder(r.Body).Decode(&spec); e!=nil {t.Error(e);return}
  if spec.Command[0]=="g++" {_=json.NewEncoder(w).Encode(contract.RunResult{Status:contract.StatusOK,Artifacts:map[string]string{"Main":"exe"}});return}
  select {case <-time.After(time.Duration(spec.Limits.ClockNs)):
   _=json.NewEncoder(w).Encode(contract.RunResult{Status:contract.StatusTimeLimitExceeded})
  case <-r.Context().Done():}
 }));defer sb.Close()
 fingerprints:=[]string{}; verdicts:=[]contract.Verdict{}
 for _,timeout:=range []time.Duration{25*time.Millisecond,100*time.Millisecond} {
  cfg:=config.Default();cfg.Judge.SandboxURL=sb.URL
  cfg.Judge.Compile.ClockNs=int64(20*time.Millisecond);cfg.Judge.Compile.CPUNs=int64(10*time.Millisecond)
  cfg.Judge.SandboxTimeout=config.Duration(timeout)
  if e:=cfg.Validate();e!=nil {t.Fatal(e)}
  cfg.Judge.TestdataRoot=t.TempDir();id,e:=node.New(cfg.Judge,nil);if e!=nil {t.Fatal(e)};defer id.Close();fp:=id.Registration().EnvironmentFingerprint
  req:=contract.JudgeRequest{Mode:contract.ModeTrial,LanguageID:"cpp",Source:"int main(){}",Cases:[]contract.CaseSpec{{Name:"slow",Input:""}},Limits:contract.JudgeLimits{CPUNs:1_000_000,MemoryBytes:1<<20,ClockNs:int64(50*time.Millisecond)}}
  result:=flow.Judge(context.Background(),client.New(sb.URL,timeout),cfg.Judge,req)
  fingerprints=append(fingerprints,fp);verdicts=append(verdicts,result.Verdict)
  t.Logf("timeout=%s validation=pass fingerprint=%s verdict=%s",timeout,fp,result.Verdict)
 }
 if verdicts[0]==verdicts[1] {t.Fatal("fixture did not produce distinct verdicts")}
 if fingerprints[0]==fingerprints[1] {t.Error("same fingerprint permits different execution verdicts")}
}
```

</details>

<details>
<summary>fixtures/base/apps/judge-engine/internal/judge/node/review_transport_test.go</summary>

SHA-256：`fb77fdfd6f55070896bc722f6f69c9eae49432601084dd54dce23f7aaff23fe1`

```go
package node_test
import (
 "context"
 "encoding/json"
 "fmt"
 "net/http"
 "net/http/httptest"
 "strings"
 "sync/atomic"
 "testing"
 "time"
 "cherry-oj/judge-engine/internal/config"
 "cherry-oj/judge-engine/internal/judge/node"

)
func TestReviewProbeTransport(t *testing.T) {
 for _, kind := range []string{"redirect", "oversize-run", "trailing-version"} { t.Run(kind, func(t *testing.T) {
  var hits atomic.Int32
  target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {
   hits.Add(1)
   if r.URL.Path=="/version" {
    fmt.Fprint(w, `{"name":"cherry-oj-sandbox","version":"test","isolation":"devhost"}`)
    if kind=="trailing-version" { fmt.Fprint(w, ` garbage`) }; return
   }
   metadata:=`{"Architecture":"amd64","CPUModel":"test","OSVersion":"test","KernelVersion":"test","ToolchainVersion":"test","RuntimeDigest":"test"}`
   stderr:=""; if kind=="oversize-run" { stderr=strings.Repeat("x", 1<<20) }
   _=json.NewEncoder(w).Encode(map[string]any{"status":"OK","exitCode":0,"stdout":metadata,"stderr":stderr})
  })); defer target.Close()
  redirect:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {http.Redirect(w,r,target.URL+r.URL.Path,http.StatusTemporaryRedirect)})); defer redirect.Close()
  cfg:=config.Default().Judge; cfg.SandboxURL=target.URL
  if kind=="redirect" {cfg.SandboxURL=redirect.URL}
  ctx,cancel:=context.WithTimeout(context.Background(),time.Second); defer cancel()
  _,err:=node.ProbeEnvironment(ctx,cfg)
  t.Logf("scenario=%s accepted=%v targetRequests=%d err=%v",kind,err==nil,hits.Load(),err)
  if err==nil {t.Errorf("probe accepted %s",kind)}
 }) }
}
```

</details>

<details>
<summary>fixtures/head/apps/judge-engine/judge/internal/node/identity/review_timeout_test.go</summary>

SHA-256：`79dca06bd98c2bb9e85b4c7c85e9d82b44fdd2cd580998214c36e72288c707e5`

```go
package identity_test
import (
 "context"
 "encoding/json"
 "net/http"
 "net/http/httptest"
 "testing"
 "time"
 "cherry-oj/judge-engine/internal/contract"
 "cherry-oj/judge-engine/judge/internal/config"
 platform "cherry-oj/judge-engine/internal/platform/config"
 "cherry-oj/judge-engine/judge/internal/flow"
 client "cherry-oj/judge-engine/judge/internal/sandboxclient"
 "cherry-oj/judge-engine/judge/internal/node/identity"
)
func TestReviewTimeoutCanChangeVerdictWithoutChangingFingerprint(t *testing.T) {
 sb:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {
  if r.Method==http.MethodDelete {w.WriteHeader(204);return}
  if r.URL.Path=="/blobs" {w.Write([]byte(`{"ref":"src"}`));return}
  var spec contract.RunSpec
  if e:=json.NewDecoder(r.Body).Decode(&spec); e!=nil {t.Error(e);return}
  if spec.Command[0]=="g++" {_=json.NewEncoder(w).Encode(contract.RunResult{Status:contract.StatusOK,Artifacts:map[string]string{"Main":"exe"}});return}
  select {case <-time.After(time.Duration(spec.Limits.ClockNs)):
   _=json.NewEncoder(w).Encode(contract.RunResult{Status:contract.StatusTimeLimitExceeded})
  case <-r.Context().Done():}
 }));defer sb.Close()
 fingerprints:=[]string{}; verdicts:=[]contract.Verdict{}
 for _,timeout:=range []time.Duration{25*time.Millisecond,100*time.Millisecond} {
  cfg:=config.Default();cfg.Judge.SandboxURL=sb.URL
  cfg.Judge.Compile.ClockNs=int64(20*time.Millisecond);cfg.Judge.Compile.CPUNs=int64(10*time.Millisecond)
  cfg.Judge.SandboxTimeout=platform.Duration(timeout)
  if e:=cfg.Validate();e!=nil {t.Fatal(e)}
  id,e:=identity.New(cfg.Judge,identity.Declared(cfg.Judge));if e!=nil {t.Fatal(e)};fp:=id.Registration().EnvironmentFingerprint
  req:=contract.JudgeRequest{Mode:contract.ModeTrial,LanguageID:"cpp",Source:"int main(){}",Cases:[]contract.CaseSpec{{Name:"slow",Input:""}},Limits:contract.JudgeLimits{CPUNs:1_000_000,MemoryBytes:1<<20,ClockNs:int64(50*time.Millisecond)}}
  result:=flow.Judge(context.Background(),client.New(sb.URL,timeout),cfg.Judge,req)
  fingerprints=append(fingerprints,fp);verdicts=append(verdicts,result.Verdict)
  t.Logf("timeout=%s validation=pass fingerprint=%s verdict=%s",timeout,fp,result.Verdict)
 }
 if verdicts[0]==verdicts[1] {t.Fatal("fixture did not produce distinct verdicts")}
 if fingerprints[0]==fingerprints[1] {t.Error("same fingerprint permits different execution verdicts")}
}
```

</details>

<details>
<summary>fixtures/head/apps/judge-engine/judge/internal/node/probe/review_transport_test.go</summary>

SHA-256：`afa5b2f1914446de1808b371b0d557bff9a11d8af7518c4f7fe9c0155e5e8815`

```go
package probe_test
import (
 "context"
 "encoding/json"
 "fmt"
 "net/http"
 "net/http/httptest"
 "strings"
 "sync/atomic"
 "testing"
 "time"
 "cherry-oj/judge-engine/judge/internal/config"
 "cherry-oj/judge-engine/judge/internal/node/probe"
 "cherry-oj/judge-engine/judge/internal/sandboxclient"
)
func TestReviewProbeTransport(t *testing.T) {
 for _, kind := range []string{"redirect", "oversize-run", "trailing-version"} { t.Run(kind, func(t *testing.T) {
  var hits atomic.Int32
  target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {
   hits.Add(1)
   if r.URL.Path=="/version" {
    fmt.Fprint(w, `{"name":"cherry-oj-sandbox","version":"test","isolation":"devhost"}`)
    if kind=="trailing-version" { fmt.Fprint(w, ` garbage`) }; return
   }
   metadata:=`{"Architecture":"amd64","CPUModel":"test","OSVersion":"test","KernelVersion":"test","ToolchainVersion":"test","RuntimeDigest":"test"}`
   stderr:=""; if kind=="oversize-run" { stderr=strings.Repeat("x", 1<<20) }
   _=json.NewEncoder(w).Encode(map[string]any{"status":"OK","exitCode":0,"stdout":metadata,"stderr":stderr})
  })); defer target.Close()
  redirect:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {http.Redirect(w,r,target.URL+r.URL.Path,http.StatusTemporaryRedirect)})); defer redirect.Close()
  cfg:=config.Default().Judge; cfg.SandboxURL=target.URL
  if kind=="redirect" {cfg.SandboxURL=redirect.URL}
  ctx,cancel:=context.WithTimeout(context.Background(),time.Second); defer cancel()
  _,err:=probe.Environment(ctx,cfg,sandboxclient.New(cfg.SandboxURL,time.Second))
  t.Logf("scenario=%s accepted=%v targetRequests=%d err=%v",kind,err==nil,hits.Load(),err)
  if err==nil {t.Errorf("probe accepted %s",kind)}
 }) }
}
```

</details>

<details>
<summary>fixtures/head/apps/judge-engine/judge/review_lifecycle_test.go</summary>

SHA-256：`98a48c94446480ab628ae6457aa2f6a49791f541353e4009973bce5cca6c14e4`

```go
package judge

import (
 "context"
 "encoding/json"
 "io"
 "log/slog"
 "net"
 "net/http"
 "net/http/httptest"
 "os"
 "strconv"
 "syscall"
 "testing"
 "time"
)
type reviewLog struct { slog.Handler; onRecord func(slog.Record) }
func (h reviewLog) Handle(ctx context.Context, r slog.Record) error { h.onRecord(r); return nil }
func TestReviewServeFailureStopsRegistry(t *testing.T) {
 reserved,err:=net.Listen("tcp4","127.0.0.1:0"); if err!=nil {t.Fatal(err)}
 addr:=reserved.Addr().String(); _,p,_:=net.SplitHostPort(addr); port,_:=strconv.Atoi(p); reserved.Close()
 sb:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {
  if r.URL.Path=="/version" {io.WriteString(w,`{"name":"cherry-oj-sandbox","version":"test","isolation":"devhost"}`);return}
  _=json.NewEncoder(w).Encode(map[string]any{"status":"OK","exitCode":0,"stdout":`{"Architecture":"amd64","CPUModel":"test","OSVersion":"test","KernelVersion":"test","ToolchainVersion":"test","RuntimeDigest":"test"}`})
 })); defer sb.Close()
 control:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request) {w.WriteHeader(503)})); defer control.Close()
 cfg:=DefaultConfig(); cfg.Judge.HTTPAddr=addr; cfg.Judge.SandboxURL=sb.URL; cfg.Judge.TestdataRoot=t.TempDir()
 cfg.Judge.Node.Enabled=true; cfg.Judge.Node.ControlToken="test"; cfg.Judge.Node.ControlPlaneURL=control.URL
 stopped:=make(chan struct{}); fault:=make(chan error,1)
 devNull,e:=os.Open("/dev/null");if e!=nil {t.Fatal(e)};defer devNull.Close()
 logger:=slog.New(reviewLog{Handler:slog.NewTextHandler(io.Discard,nil),onRecord:func(r slog.Record) {
  if r.Message=="process.started" {
   for fd:=0;fd<1024;fd++ { sa,e:=syscall.Getsockname(fd); if e!=nil {continue}; if v,ok:=sa.(*syscall.SockaddrInet4);ok&&v.Port==port {fault<-syscall.Dup2(int(devNull.Fd()),fd);return} }
   fault<-io.EOF
  }
  if r.Message=="process.serve.failed" {t.Log(r.Message);r.Attrs(func(a slog.Attr)bool{t.Log(a.String());return true})}
  if r.Message=="process.stopping" {close(stopped)}
 }})
 ctx,cancel:=context.WithCancel(context.Background());defer cancel()
 done:=make(chan error,1);go func(){done<-Run(ctx,cfg,logger)}()
 select {case e:=<-fault: if e!=nil {t.Fatalf("fault injection failed: %v",e)};case <-time.After(3*time.Second):t.Fatal("no startup")}
 select {case <-stopped:case <-time.After(3*time.Second):t.Fatal("Serve did not fail")}
 select {case e:=<-done:t.Logf("Run returned: %v",e);case <-time.After(100*time.Millisecond):
  t.Error("Run is blocked after Serve failure until its caller cancels context")
  cancel();select {case e:=<-done:t.Logf("after external cancellation: %v",e);case <-time.After(time.Second):t.Fatal("Run stuck after cancellation")}
 }
}
```

</details>

<details>
<summary>logs/base-probe.log</summary>

SHA-256：`f108053ee4e6fb74a545689228742f1140ef1364175042b7e6422a91c49ae5f8`

```text
=== RUN   TestReviewProbeTransport
=== RUN   TestReviewProbeTransport/redirect
    review_transport_test.go:34: scenario=redirect accepted=false targetRequests=0 err=sandbox environment probe rejected
=== RUN   TestReviewProbeTransport/oversize-run
    review_transport_test.go:34: scenario=oversize-run accepted=false targetRequests=2 err=sandbox environment probe response invalid
=== RUN   TestReviewProbeTransport/trailing-version
    review_transport_test.go:34: scenario=trailing-version accepted=false targetRequests=1 err=invalid character 'g' after top-level value
--- PASS: TestReviewProbeTransport (0.02s)
    --- PASS: TestReviewProbeTransport/redirect (0.00s)
    --- PASS: TestReviewProbeTransport/oversize-run (0.01s)
    --- PASS: TestReviewProbeTransport/trailing-version (0.00s)
PASS
ok  	cherry-oj/judge-engine/internal/judge/node	1.340s
```

</details>

<details>
<summary>logs/base-timeout.log</summary>

SHA-256：`f83181248530294f61fccef340f40456608a255ea8b1956ad9572ce85a603233`

```text
=== RUN   TestReviewTimeoutCanChangeVerdictWithoutChangingFingerprint
    review_timeout_test.go:37: timeout=25ms validation=pass fingerprint=98b9688ce84591eac51cb80d958be7d3b1ca18d9131c859b3248c1623ab364c3 verdict=SE
    review_timeout_test.go:37: timeout=100ms validation=pass fingerprint=fddff7a68bf9886f399f33aa819ff6b591cbef04963dac272c679c033a1e2548 verdict=TLE
--- PASS: TestReviewTimeoutCanChangeVerdictWithoutChangingFingerprint (0.10s)
PASS
ok  	cherry-oj/judge-engine/internal/judge/node	1.413s
```

</details>

<details>
<summary>logs/head-probe.log</summary>

SHA-256：`a6bb44c53bda36ee7202ec05cd45a7ac19ac46d39bdf92657ce1bfde3f3f8b65`

```text
=== RUN   TestReviewProbeTransport
=== RUN   TestReviewProbeTransport/redirect
    review_transport_test.go:34: scenario=redirect accepted=true targetRequests=2 err=<nil>
    review_transport_test.go:35: probe accepted redirect
=== RUN   TestReviewProbeTransport/oversize-run
    review_transport_test.go:34: scenario=oversize-run accepted=true targetRequests=2 err=<nil>
    review_transport_test.go:35: probe accepted oversize-run
=== RUN   TestReviewProbeTransport/trailing-version
    review_transport_test.go:34: scenario=trailing-version accepted=true targetRequests=2 err=<nil>
    review_transport_test.go:35: probe accepted trailing-version
--- FAIL: TestReviewProbeTransport (0.10s)
    --- FAIL: TestReviewProbeTransport/redirect (0.01s)
    --- FAIL: TestReviewProbeTransport/oversize-run (0.09s)
    --- FAIL: TestReviewProbeTransport/trailing-version (0.00s)
FAIL
FAIL	cherry-oj/judge-engine/judge/internal/node/probe	0.777s
FAIL
```

</details>

<details>
<summary>logs/head-serve.log</summary>

SHA-256：`deb0fc7feebe922a000a9bd2d18569afc91cba024e07ffb929cc52d465a00a89`

```text
=== RUN   TestReviewServeFailureStopsRegistry
    review_lifecycle_test.go:36: process.serve.failed
    review_lifecycle_test.go:36: event=process.serve.failed
    review_lifecycle_test.go:36: error=accept tcp 127.0.0.1:51778: accept: socket operation on non-socket
    review_lifecycle_test.go:44: Run is blocked after Serve failure until its caller cancels context
    review_lifecycle_test.go:45: after external cancellation: accept tcp 127.0.0.1:51778: accept: socket operation on non-socket
--- FAIL: TestReviewServeFailureStopsRegistry (0.11s)
FAIL
FAIL	cherry-oj/judge-engine/judge	0.689s
FAIL
```

</details>

<details>
<summary>logs/head-timeout.log</summary>

SHA-256：`adf1a92f040524169bc90a6b60f44c5e4b4ac1324e118e45703786b51ee5d68d`

```text
=== RUN   TestReviewTimeoutCanChangeVerdictWithoutChangingFingerprint
    review_timeout_test.go:37: timeout=25ms validation=pass fingerprint=ff23743a18cff8ee269b68f445fee4824a51170777f3825d09c16b23510670e0 verdict=SE
    review_timeout_test.go:37: timeout=100ms validation=pass fingerprint=ff23743a18cff8ee269b68f445fee4824a51170777f3825d09c16b23510670e0 verdict=TLE
    review_timeout_test.go:40: same fingerprint permits different execution verdicts
--- FAIL: TestReviewTimeoutCanChangeVerdictWithoutChangingFingerprint (0.11s)
FAIL
FAIL	cherry-oj/judge-engine/judge/internal/node/identity	0.629s
FAIL
```

</details>
