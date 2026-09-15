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
result: "pass"
created_at: "2026-09-14"
updated_at: "2026-09-15"
---

# VERIFY-059：判题引擎结构重切的回归验证

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

暂无。

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

暂无。

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
4. **`independent-review` 未执行。** 本工作的复核阶段只完成了 `impact-analysis`；
   跨越 228 个文件的结构重切没有第二个人独立看过，这一项的缺失由签闸人自行承担或另行安排。
5. **业务闭环曾观测到一次与本工作无关的偶发 500**（`PATCH /api/admin/problems/{id}`），
   已另行立项为 [WORK-059](../WORK-059/00-work.md) / ISSUE-020，不阻塞本工作验收。

## 结论

**通过。** S1–S7 的结构重切在最终候选上一次性通过全部 12 个 CI job 与 93 项必需回归，
AC-001 至 AC-012 均已取得证据（逐条出处见上方各阶段记录与各 TASK 执行记录）。

两点需要接收方知道，它们不影响本次结论但会影响之后的动作：

1. **`ACTIVE` 环境的切换仍是人工动作**，不在本次验证范围内；在它完成之前，新结构不参与实际判题路由。
2. **本工作全程轮换环境指纹**：`executableDigest()` 参与指纹计算，因此每个阶段的二进制变化都会
   产生新指纹。控制面只会把新指纹记为 `REGISTERED`，不会静默替换 `ACTIVE`。

## 变更记录

- 2026-09-15：状态变更：draft → review。原因：S1-S7 全部完成，CI 34938772322 一次通过 12 个 job 与 93 项必需回归，等待人工复核与验收闸
- 2026-09-15：验收闸通过：review → approved。原因：CI 34938772322 一次通过，93 项必需回归全绿，AC-001 至 AC-012 均有证据。验收通过。
