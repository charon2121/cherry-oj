---
id: "TASK-131"
type: "task"
title: "S7 统一错误消息语言并重写结构文档"
status: "done"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-128", "TASK-129", "TASK-130"]
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
implements: ["CHANGE-014#REQ-009", "CHANGE-014#REQ-010"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs", "development/README.md", "development/works/WORK-049", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md", "compose.yaml", "deploy/sandbox-linux/tests"]
write_paths: ["apps/judge-engine", "docs/engine.md", "docs/coding-standards/languages/go.md", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md", "compose.yaml", "deploy/sandbox-linux/tests"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "docs/architecture.md", "docs/product.md", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml"]
created_at: "2026-09-14"
updated_at: "2026-09-15"
---

# TASK-131：S7 统一错误消息语言并重写结构文档

## 任务目标

把全模块的错误消息统一为英文（[DECISION-035](40-decision-DECISION-035.md) 决定三）；为三个服务补齐
说明职责与引用边界的包文档；按新结构重写 `docs/engine.md`（决定五）；同步 Go 编码规范中引用旧包
路径的条目。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-009、REQ-010；[DECISION-035](40-decision-DECISION-035.md)
决定三与决定五；[PLAN-041](50-plan-PLAN-041.md) 阶段 S7。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`deploy/sandbox-linux/ci/` 的必跑用例清单在
可修改范围内，但**只允许更新 judge-engine 用例的包路径**：不得增删用例、改断言或放宽必需数量
（Go 必跑固定 52 项）。同理 `.github/workflows/ci.yml`、`deploy/sandbox-linux/tests/README.md`
与 `compose.yaml` **只允许改因本工作而失效的路径或配置取值**，不改 job 结构、触发条件、
权限、步骤顺序、服务定义、网络、卷与健康检查。报告 schema、
`deploy/` 与 `.github/` 下其余内容仍然禁止修改。理由见
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。改写错误消息时只改字面量，不改 `%w` 包装结构、
`errors.Is`/`errors.As` 判定条件与错误值身份；不改变 `docs/architecture.md` 的系统级结论。

## 依赖

以 front matter 的 `depends_on` 为准。必须在结构调整全部完成后进行，使文档描述最终结构。

## 产出

- 全模块英文错误消息。
- `judge/doc.go`、`sandbox/doc.go`、`helperd/doc.go`：职责、引用边界、谁可以引用本子树。
- 重写后的 `docs/engine.md`：按三棵服务子树与两条硬边界组织；保留其中仍然成立的判断（sandbox 不
  理解判题、隔离与限量是两条正交的轴、为什么没有 `/compile`），删除已不存在的结构描述
  （容器复用、`Container.Reset()`、cgroup 与 container 互不依赖）。
- `docs/coding-standards/languages/go.md` 中引用旧包路径的条目更新（规则本身不变）。
- 受错误消息改写影响的日志检索表达式清单，写入执行记录供运维更新。

## 完成标准

- [x] 模块内 `fmt.Errorf` / `errors.New` 的字面量全部为英文，由检查命令给出证据。
- [x] 错误包装结构与判定条件未变：`errors.Is` / `errors.As` 的既有用例全部通过。
- [x] 三个服务各有 `doc.go`，说明本子树可被谁引用、不可引用什么。
- [x] 按重写后的 `docs/engine.md` 实走一次源码，不出现文档描述与实现不符之处；实走记录写入
      [VERIFY-059](70-verify-VERIFY-059.md)。
- [x] `docs/engine.md` 中不再出现 `Container.Reset()`、容器池化复用等已不存在的结构。
- [x] 受影响的日志检索表达式清单完整。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
grep -rnE '(fmt\.Errorf|errors\.New)\("' --include='*.go' . | grep -P '[\x{4e00}-\x{9fff}]'   # 应无输出
```

外加 WORK-050 固化的 CI 全部通过；文档实走由人或独立复核执行。

## 风险

批量改写错误字面量时可能误改判定条件（例如把用于比较的字符串一并改掉）。处置：只替换传给
`fmt.Errorf` / `errors.New` 的字面量，不触碰任何字符串比较；改写后完整跑一遍测试。

`docs/engine.md` 重写可能丢失其中仍然成立的设计判断。处置：先列出需要保留的判断清单，重写后
逐条核对。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-15：状态变更：todo → ready。原因：前置 TASK-128/129/130 已全部完成
- 2026-09-15：错误消息统一为英文，共改写 238 处 `fmt.Errorf` / `errors.New` 的消息字面量，
  外加 11 处 `wrapError` 的操作名前缀、`CleanupError` 的前缀、`cmd/sandbox-helper` 缺 `--config`
  的 stderr 提示，以及 `testcase` 里那条同样会被运维检索的 `log.Warn`。
  **未改**：`cmd/*/main.go` 的 flag 说明文本——那是 CLI 文档而非错误消息，与本决定的范围无关；
  这条边界写在这里以免下次有人以为是漏改。
- 2026-09-15：**改写方式本身是这次的关键。** 早前在 S3 用正则替换标识符时，
  改坏过 backtick 字符串里的 JSON 字面量（`{"Version":1}` → `{"hostexec.Version":1}`），
  而那个文件是 `_linux_test.go`，本地根本不编译，直到 CI 才暴露。
  这次改用 `go/ast`：只替换 `fmt.Errorf` / `errors.New` **第一个实参**的字面量
  （含 `"..." + "..."` 跨行拼接，这类有 3 处），替换前比对该字节区间与解析出的字面量是否一致，
  替换后拒绝仍含汉字的结果。于是字符串比较、JSON 夹具、结构体标签**在原理上**不可能被误伤，
  而不是「检查过应该没事」。
- 2026-09-15：改写前后逐条比对**格式动词序列**（`%w`/`%s`/`%d`/`%q`/`%+v`），238 处全部未变。
  `go vet` 只能查类型是否匹配，查不出 `%q` 被写成 `%s`；动词序列相等是更强且免费的不变量。
  另外核对整份 diff：删除行 241 行**全部含汉字**，没有一行是别的东西。
- 2026-09-15：包装结构与判定条件未动——`%w` 链、`errors.Is` / `errors.As` 条件、错误值身份都不变。
  受影响的是 5 处**测试断言里的期望子串**（`conclusion_test`、`judge/internal/api/server_test`、
  `sandbox/budget_test`、`hostexec/client/client_test`、`backend/devhost_test`），
  它们断言的是「错误信息有没有点明是哪一项」，意图不变，只把期望子串换成英文。
- 2026-09-15：`judge/doc.go`、`sandbox/doc.go`、`helperd/doc.go` 在 S2 已建立并写明引用边界，
  本阶段复核内容仍然成立，未改动。
- 2026-09-15：`docs/engine.md` 按三棵服务子树与两条硬边界重写（739 → 约 795 行）。
  **保留**：sandbox 不理解判题、隔离与限量是两条正交的轴、`CLONE_INTO_CGROUP` 那处必须点破的耦合、
  为什么没有 `/compile`、Status 与 Verdict 的区别、测试数据的磁盘布局与宽松配对规则。
  **删除**：`Container` 接口与 `Container.Reset()`、容器池化复用、`hostContainer`、
  「container 与 cgroup 互不 import」这类已不存在的结构描述，以及 A/A-lite/B/C 分阶段表
  （隔离已经落地，分阶段学习表描述的是一条不再走的路径）。
  **新增**：信任边界与部署边界各自是什么、`internal/` 如何把它们变成编译期错误、
  顶层 `internal/` 的准入条件、一次性 `backend.Execute` 的形状与理由、
  「回收未确认」为什么要停整个池、`hostexec` 的线格式与 `Completion`/EOF 的分工、
  纯函数 `conclude` 与显式转移表、跨层三道期限的顺序。
- 2026-09-15：`docs/coding-standards/languages/go.md` 同步 5 处旧包路径引用
  （`container` → `backend`/`launcher`，`client.Run` → `sandboxclient.Run`，
  `container.resolve` → `hostexec.ValidPath`），**规则本身一字未改**。
- 2026-09-15：**范围之外但必须一并修的两份文档。** `apps/judge-engine/README.md` 是「源码阅读入口」，
  它的每一条链接都指向 `internal/sandbox/...`、`internal/judge/...` 这些已经不存在的路径——
  一份链接全断的阅读入口比没有更糟。同理 `helperd/internal/helper/README.md` 仍在说
  `launcher.Request`、`helper.Call` 和 `Container` 接口。两份都在 `write_paths` 内，已按现结构重写；
  `scripts/docs_test.py` 校验 573 份文档的入口与本地链接全部有效。
- 2026-09-15：**按重写后的 `docs/engine.md` 实走源码时发现两处文档与实现不符，已改文档。**
  （一）`/run` 的内部顺序写反了：归一化限额发生在**排队之后**、在 `runner` 里，
  而不是排队之前；打开 store 与调用 `Execute` 的也是 `runner` 而非 `pool`。
  （二）`SO_PEERCRED` 写成了「不靠文件权限」，实际是**双重约束**——socket 权限先放行服务专用组，
  建立连接后再核对对端 UID。两处都按实现改了文档，并补上一条实走中确认的细节：
  输出超限会取消 `runCtx`，但判定用原始 `ctx`，避免把 OLE 报成平台错误。实走记录见
  [VERIFY-059](70-verify-VERIFY-059.md)。
- 2026-09-15：**受影响的日志检索表达式（供运维）。** 结论先行：
  **结构化日志的 `event` 字段名一个都没变**，全部是 `process.backend.probe.failed`、
  `judge.node.install.failed` 这类英文点分名；按 `event` 建的告警规则**不受影响**。
  变的只有 `error` 字段的**取值**，入口是 22 处 `logger.*(..., "error", err)`
  以及两处 HTTP 响应体 `{"error": err.Error()}`（`judge/internal/api/server.go:46`、
  `sandbox/internal/api/server.go:89`）。
  因此需要更新的，只有**按中文正文做子串匹配**的检索表达式；完整新旧对照见下方附录。
  另有一条不在错误链上但同样会被检索的日志消息：
  `测试点缺少对应的 .out，已跳过` → `test case has no matching .out, skipped`。
  Java 侧不受影响：`HttpJudgingClient` / `ProblemApiErrors` 的用户可见文案一律按错误码
  （`NO_ONLINE_JUDGE_NODE` 等）分支，不匹配判题引擎的错误正文。
  唯一会把英文正文带到人眼前的路径是 `language_calibration.error_message`——
  标定失败时管理台显示的是引擎原文，切换后为英文。
- 2026-09-15：CI run **34938772322**（sourceSha `66a0a35`，attempt 1）一次通过，12 个 job 全部 success，
  必需回归汇总 `"status": "PASS"`，93 项必需用例全部通过：basic 5/5、kernel 63/63、native 10/10、
  business 15/15。business 全绿同时印证了执行记录里那条分析——Java 侧确实不匹配引擎的错误正文，
  否则真实页面那条闭环会先炸。
- 2026-09-15：**一处对完成标准的判断，写在这里供复核推翻。** 标准写的是
  「`docs/engine.md` 中不再出现 `Container.Reset()`、容器池化复用等已不存在的结构」。
  重写后 `Container.Reset()` 这个字面已不存在，但 §6.3 留了一句**否定式**的话：
  「正因为没有可复用对象，也就没有『容器池化复用』『`Reset()` 清工作目录』这类东西」。
  按字面读这条标准应该把它也删掉；我判断**留着更好**：整节在讲一次性 `Execute` 为什么取代了
  四阶段接口，读过旧文档的人需要知道那两样东西是**被取消了**而不是被漏写了。
  「不再描述成设计的一部分」这个意图已经满足，因此勾选该项。
- 2026-09-15：状态变更：ready → doing。原因：开始统一错误消息语言并按新结构重写文档
- 2026-09-15：状态变更：doing → done。原因：S7 完成：238 处错误消息统一英文、engine.md 按新结构重写、两份阅读入口同步；CI 34938772322 一次通过

## 附录：错误消息新旧对照（供运维更新日志检索表达式）

改写范围是 `fmt.Errorf` / `errors.New` 的消息字面量，按文件与行号排列。
另有不在此表内、但同样进入错误链的前缀：`wrapError` 的操作名
（`等待 init` → `wait for init`、`关闭控制通道` → `close control channel`、
`等待输入结束` → `wait for input to finish`、`等待输出结束` → `wait for output to finish`、
`读取输出` → `read output`、`等待控制接收结束` → `wait for control receive to finish`、
`删除空挂载目录` → `remove empty mount directory`、`停止资源组` → `stop resource group`、
`删除资源组` → `remove resource group`），以及 `CleanupError` 的前缀
`回收未确认: ` → `reclaim unconfirmed: `。
  `helperd/internal/cgroup/cgroup.go`
  - `cgroup memoryBytes/maxProcesses 必须为允许范围内的正数` → `cgroup memoryBytes/maxProcesses must be positive and within the allowed range`
  - `cgroup CPU 配额/周期必须为整微秒，配额至少 1ms，周期 1ms～1s` → `cgroup CPU quota/period must be whole microseconds, quota at least 1ms, period 1ms to 1s`
  - `读取 %s: %w` → `read %s: %w`
  - `%s 缺 %s` → `%s is missing %s`
  - `委派内部节点存在进程，监督进程必须位于独立叶子` → `delegated inner node has processes; the supervisor must live in its own leaf`
  - `只支持 domain cgroup` → `only domain cgroups are supported`
  - `cgroup 管理器已隔离: %w` → `cgroup manager is quarantined: %w`
  - `任务组回收失败，拒绝新任务: %w` → `job group reclaim failed; refusing new jobs: %w`
  - `独占创建 cgroup %s: %w` → `exclusively create cgroup %s: %w`
  - `配置 cgroup %s: %w` → `configure cgroup %s: %w`
  - `写入 %s: %w` → `write %s: %w`
  - `%s 限额读回不一致` → `%s limit read back differently than it was written`
  - `新建 cgroup 已有进程或历史计量` → `new cgroup already has processes or accounting history`
  - `终止整组: %w` → `kill whole group: %w`
  - `无效 populated: %d` → `invalid populated: %d`

  `helperd/internal/cgroup/cgroup_test.go`
  - `fake 没有内核 FD` → `fake has no kernel FD`

  `helperd/internal/cgroup/filesystem.go`
  - `cgroup 文件 %s 超过控制面读取上限` → `cgroup file %s exceeds the control-plane read limit`

  `helperd/internal/cgroup/filesystem_linux.go`
  - `cgroup 委派目录必须是绝对路径` → `cgroup delegation directory must be an absolute path`
  - `目录不在 cgroup v2 文件系统中` → `directory is not on a cgroup v2 filesystem`

  `helperd/internal/cgroup/filesystem_other.go`
  - `cgroup v2 后端只支持 Linux，不允许回退 host` → `the cgroup v2 backend only supports Linux; falling back to the host is not allowed`

  `helperd/internal/cgroup/usage.go`
  - `无效 cgroup 统计行 %q` → `invalid cgroup statistics line %q`
  - `重复 cgroup 统计项 %q` → `duplicate cgroup statistics entry %q`
  - `无效 %s 计量: %w` → `invalid %s measurement: %w`
  - `缺少 cgroup 统计项 %s` → `missing cgroup statistics entry %s`
  - `CPU 微秒换纳秒溢出` → `CPU microsecond to nanosecond conversion overflowed`
  - `无效 memory.peak %q` → `invalid memory.peak %q`
  - `无效 populated: %d` → `invalid populated: %d`

  `helperd/internal/helper/conclusion.go`
  - `init 提前退出: %w` → `init exited early: %w`

  `helperd/internal/helper/config.go`
  - `helper 路径必须为明确的绝对路径` → `helper paths must be explicit absolute paths`
  - `socket 必须位于独占 state 目录` → `the socket must live in the exclusive state directory`
  - `服务、init、payload 必须使用分离的非 root 身份` → `service, init and payload must use separate non-root identities`
  - `身份越界` → `identity out of range`
  - `并发必须为 1～4` → `concurrency must be 1 to 4`
  - `必须固定 rootfs manifest 摘要` → `the rootfs manifest digest must be pinned`
  - `配置需要绝对路径` → `configuration requires an absolute path`
  - `配置不是有界普通文件` → `configuration is not a bounded regular file`
  - `配置有尾随内容` → `configuration has trailing content`

  `helperd/internal/helper/config_other.go`
  - `helper 配置仅可在 Linux 上加载` → `helper configuration can only be loaded on Linux`

  `helperd/internal/helper/delivery.go`
  - `打开产物 %s: %w` → `open artifact %s: %w`
  - `产物总量超限: %s` → `artifact total exceeds the limit: %s`
  - `产物句柄与元数据不一致` → `artifact handle disagrees with its metadata`
  - `交付产物 %s: %w` → `deliver artifact %s: %w`

  `helperd/internal/helper/execution.go`
  - `init 未报告退出事实` → `init did not report the exit facts`
  - `execution Run 已在进行` → `execution Run is already in progress`
  - `execution 只能 Run 一次` → `execution can only Run once`
  - `执行流程异常中断` → `the execution flow was interrupted unexpectedly`

  `helperd/internal/helper/identity.go`
  - `槽位身份重叠或越界: slot=%d id=%d` → `slot identities overlap or are out of range: slot=%d id=%d`

  `helperd/internal/helper/installation_linux_amd64.go`
  - `helper 必须使用 CGO_ENABLED=0 构建` → `helper must be built with CGO_ENABLED=0`
  - `helper 必须由 root 托管` → `helper must be owned by root`
  - `helper 必须为不带 setuid/setgid 的普通可执行文件` → `helper must be a regular executable without setuid/setgid`
  - `隔离启动能力冒烟失败: reason=%s error=%s stderr=%q` → `isolated startup capability smoke test failed: reason=%s error=%s stderr=%q`

  `helperd/internal/helper/process.go`
  - `可信启动器在放行前产生超限输出` → `the trusted launcher produced oversized output before handing over`
  - `输入交付: %w` → `deliver input: %w`
  - `启动控制通道提前关闭` → `the startup control channel closed early`
  - `意外控制 FD` → `unexpected control FD`
  - `可信 init 阶段失败: %s errno=%d` → `trusted init stage failed: %s errno=%d`
  - `payload exec 启动失败: stage=%d errno=%d` → `payload exec failed to start: stage=%d errno=%d`
  - `启动协议阶段错误` → `startup protocol stage error`
  - `启动协议阶段错误` → `startup protocol stage error`

  `helperd/internal/helper/process_linux_amd64.go`
  - `isolatedProcess 只能 Start 一次` → `isolatedProcess can only Start once`
  - `启动器交付的工作目录无效` → `the launcher delivered an invalid working directory`

  `helperd/internal/helper/process_other.go`
  - `隔离执行仅支持 Linux/amd64` → `isolated execution is only supported on Linux/amd64`
  - `隔离工作区仅支持 Linux/amd64` → `isolated workspaces are only supported on Linux/amd64`
  - `隔离产物仅支持 Linux/amd64` → `isolated artifacts are only supported on Linux/amd64`

  `helperd/internal/helper/recovery_linux.go`
  - `无所有权标记但 jobs 非空` → `no ownership marker but jobs is not empty`
  - `所有权标记与 jobs 不符` → `the ownership marker disagrees with jobs`
  - `jobs 存在未知组 %s` → `jobs contains unknown group %s`
  - `旧 socket 类型/所有权错误` → `stale socket has the wrong type or ownership`
  - `state 存在未知条目 %s` → `state contains unknown entry %s`
  - `遗留任务组出现未知嵌套组` → `a leftover job group contains an unknown nested group`
  - `events 过大` → `events is too large`
  - `重复 populated` → `duplicate populated`
  - `populated 无效` → `invalid populated`

  `helperd/internal/helper/server_linux_amd64.go`
  - `helper 已运行: %w` → `helper is already running: %w`
  - `槽位%d启动探测: %w` → `slot %d startup probe: %w`
  - `helper 对端 UID 不匹配` → `helper peer UID mismatch`

  `helperd/internal/helper/server_other.go`
  - `特权 helper 首版仅支持 Linux amd64，禁止回退 host` → `the privileged helper only supports Linux amd64 in this version; falling back to the host is forbidden`

  `helperd/internal/helper/state.go`
  - `执行状态非法转移: %s → %s` → `illegal execution state transition: %s -> %s`

  `helperd/internal/helper/supervise.go`
  - `隔离启动握手超时` → `isolated startup handshake timed out`
  - `可信 init 在报告退出事实前终止: %w` → `trusted init died before reporting the exit facts: %w`

  `helperd/internal/helper/trust_linux.go`
  - `路径必须 root 所有且非 root 不可写: %s` → `path must be owned by root and not writable by non-root: %s`
  - `需要目录: %s` → `a directory is required: %s`
  - `manifest 过大` → `manifest is too large`
  - `manifest 摘要不匹配` → `manifest digest mismatch`
  - `manifest 缺版本/来源/文件` → `manifest is missing version, source or files`
  - `manifest 路径错误` → `invalid manifest path`
  - `rootfs 出现未登记文件 %s` → `rootfs contains unlisted file %s`
  - `rootfs 权限错误 %s` → `wrong rootfs permissions %s`
  - `rootfs mode 不匹配 %s` → `rootfs mode mismatch %s`
  - `目录 manifest 错误` → `invalid directory manifest`
  - `链接不匹配` → `link mismatch`
  - `rootfs 拒绝硬链接` → `rootfs refuses hard links`
  - `rootfs 文件摘要错误 %s` → `wrong rootfs file digest %s`
  - `rootfs 拒绝特殊文件` → `rootfs refuses special files`
  - `rootfs 缺 manifest 文件` → `rootfs is missing manifest file`
  - `挂载点不是目录` → `mount point is not a directory`
  - `启动器挂载点必须普通文件` → `launcher mount point must be a regular file`
  - `.sandbox 目录必须 root 0700` → `.sandbox directory must be root 0700`

  `helperd/internal/launcher/channel_linux.go`
  - `控制消息短写` → `short write on control message`
  - `控制通道关闭或消息/FD 无效` → `control channel closed, or invalid message/FD`
  - `控制消息版本错误` → `wrong control message version`

  `helperd/internal/launcher/exec.go`
  - `payload 必须使用有效的专用非 root UID/GID` → `payload must use a valid dedicated non-root UID/GID`
  - `payload 路径必须是隔离根内已解析的绝对路径` → `payload path must be a resolved absolute path inside the isolation root`
  - `payload 参数/环境条目数无效` → `invalid payload argument/environment entry count`
  - `payload 参数/环境不能包含 NUL` → `payload arguments/environment must not contain NUL`
  - `payload 参数/环境超过 64KiB` → `payload arguments/environment exceed 64KiB`
  - `payload rlimit 超过节点启动器边界` → `payload rlimit exceeds the node launcher boundary`
  - `payload 错误 FD 无效` → `invalid payload error FD`
  - `payload READY FD 无效` → `invalid payload READY FD`
  - `payload 策略无效` → `invalid payload policy`

  `helperd/internal/launcher/files_linux.go`
  - `非法产物路径` → `illegal artifact path`
  - `产物不是唯一链接的有界普通文件` → `artifact is not a bounded regular file with exactly one link`
  - `非法输入路径` → `illegal input path`

  `helperd/internal/launcher/init_linux_amd64.go`
  - `无效的受信启动配置` → `invalid trusted startup configuration`
  - `回收 namespace 后代: %w` → `reclaim namespace descendants: %w`

  `helperd/internal/launcher/payload_linux_amd64.go`
  - `无效握手字节` → `invalid handshake byte`

  `helperd/internal/launcher/resolve_linux.go`
  - `隔离根中没有可执行命令` → `no executable command in the isolation root`

  `helperd/internal/launcher/rootfs_linux_amd64.go`
  - `rootfs 只能准备一次` → `rootfs can only be prepared once`
  - `init 必须为新 PID namespace 中的 root PID 1` → `init must be root PID 1 in a new PID namespace`
  - `rootfs 挂载点不能为链接` → `rootfs mount point must not be a link`

  `helperd/internal/policy/install_linux_amd64.go`
  - `加载 seccomp: %w` → `load seccomp: %w`
  - `seccomp TSYNC 未覆盖线程 %d` → `seccomp TSYNC did not cover thread %d`

  `helperd/internal/policy/install_other.go`
  - `seccomp 首版仅实现 Linux/amd64 原生 ABI` → `seccomp in this version only implements the Linux/amd64 native ABI`

  `helperd/internal/policy/policy.go`
  - `未知 seccomp 策略 %q` → `unknown seccomp policy %q`

  `internal/contract/judge.go`
  - `limits.cpuNs 必须为正，得到 %d` → `limits.cpuNs must be positive, got %d`
  - `limits.memoryBytes 必须为正，得到 %d` → `limits.memoryBytes must be positive, got %d`
  - `limits.clockNs 不能为负，得到 %d` → `limits.clockNs must not be negative, got %d`

  `internal/contract/limits.go`
  - `limits.%s 超出允许范围: %d` → `limits.%s is out of the allowed range: %d`
  - `默认限额无效: %w` → `invalid default limits: %w`
  - `limits 必须是对象` → `limits must be an object`
  - `读取 limits 字段: %w` → `read limits field: %w`
  - `未知 limits 字段: %v` → `unknown limits field: %v`
  - `重复 limits 字段: %v` → `duplicate limits field: %v`
  - `limits.%s 必须是允许范围内的非负整数` → `limits.%s must be a non-negative integer within the allowed range`
  - `limits 对象未结束: %w` → `limits object was not terminated: %w`

  `internal/contract/run.go`
  - `stdin/inputs 必须是字符串或 {ref|text} 对象` → `stdin/inputs must be a string or a {ref|text} object`
  - `stdin/inputs 必须是字符串或 {ref|text} 对象: %w` → `stdin/inputs must be a string or a {ref|text} object: %w`
  - `stdin/inputs 对象必须且只能提供 ref 或 text 之一` → `a stdin/inputs object must provide exactly one of ref or text`

  `internal/hostexec/client/client.go`
  - `输入流不能为空` → `the input stream must not be nil`
  - `helper 响应版本/产物数无效` → `invalid helper response version or artifact count`
  - `helper 返回未授权或超大产物` → `helper returned an unauthorized or oversized artifact`
  - `helper 没有确认完整回收与交付` → `helper did not confirm complete reclaim and delivery`
  - `helper 完成帧后存在多余数据` → `extra data after the helper completion frame`
  - `等待 helper 连接收尾: %w` → `wait for the helper to close the connection: %w`

  `internal/hostexec/protocol.go`
  - `无效版本或条目数` → `invalid version or entry count`
  - `命令必须为裸名称` → `command must be a bare name`
  - `参数包含 NUL` → `arguments contain NUL`
  - `参数/环境过大` → `arguments/environment are too large`
  - `环境变量格式错误` → `malformed environment variable`
  - `stdin 大小无效` → `invalid stdin size`
  - `输入路径/大小无效` → `invalid input path or size`
  - `产物路径无效` → `invalid artifact path`
  - `执行限额未归一化或超出节点硬边界` → `execution limits are not normalized or exceed the node hard boundary`
  - `控制帧大小无效` → `invalid control frame size`
  - `控制帧有尾随内容` → `control frame has trailing content`
  - `控制帧过大` → `control frame is too large`

  `internal/platform/config/config.go`
  - `时长应当写成字符串如 "60s": %w` → `a duration should be written as a string such as "60s": %w`
  - `解析时长 %q: %w` → `parse duration %q: %w`
  - `logging.path 不能为空` → `logging.path must not be empty`
  - `logging.level 必须是 DEBUG、INFO、WARN 或 ERROR，得到 %q` → `logging.level must be DEBUG, INFO, WARN or ERROR, got %q`
  - `解析配置文件 %s: %w` → `parse configuration file %s: %w`
  - `读取配置文件 %s: %w` → `read configuration file %s: %w`

  `internal/platform/config/env.go`
  - `环境变量 %s=%q: %w` → `environment variable %s=%q: %w`
  - `应当是时长如 "60s": %w` → `should be a duration such as "60s": %w`
  - `应当是 true/false: %w` → `should be true/false: %w`
  - `应当是整数: %w` → `should be an integer: %w`
  - `超出 %s 的范围` → `out of range for %s`
  - `不支持的字段类型 %s` → `unsupported field type %s`

  `judge/internal/api/judge.go`
  - `解析 JudgeRequest: %w` → `parse JudgeRequest: %w`
  - `JudgeRequest 后还有多余的 JSON 值` → `extra JSON value after JudgeRequest`
  - `解析 JudgeRequest 尾部: %w` → `parse the tail after JudgeRequest: %w`
  - `缺少必填字段 %s` → `missing required field %s`

  `judge/internal/config/validation.go`
  - `judge.httpAddr 不能为空` → `judge.httpAddr must not be empty`
  - `judge.sandboxURL 不能为空` → `judge.sandboxURL must not be empty`
  - `judge.sandboxTimeout 必须为正，得到 %s` → `judge.sandboxTimeout must be positive, got %s`
  - `judge.environmentFingerprint 不能为空` → `judge.environmentFingerprint must not be empty`
  - `judge.testdataRoot 不能为空` → `judge.testdataRoot must not be empty`
  - `judge.clockRatio 必须为正，得到 %d` → `judge.clockRatio must be positive, got %d`
  - `judge.inlineThresholdBytes 不能为负，得到 %d` → `judge.inlineThresholdBytes must not be negative, got %d`
  - `judge.outputExcerptBytes 不能为负，得到 %d` → `judge.outputExcerptBytes must not be negative, got %d`
  - `judge.messageExcerptBytes 不能为负，得到 %d` → `judge.messageExcerptBytes must not be negative, got %d`
  - `judge.output.stdoutMaxBytes 必须为正，得到 %d` → `judge.output.stdoutMaxBytes must be positive, got %d`
  - `judge.output.stderrMaxBytes 必须为正，得到 %d` → `judge.output.stderrMaxBytes must be positive, got %d`
  - `judge.compile 的三项都必须为正，得到 %+v` → `all three judge.compile values must be positive, got %+v`
  - `judge.sandboxTimeout（%s）必须大于 judge.compile.clockNs（%s）：否则编译刚到墙钟上限，judge 这边已经先超时，结果被报成系统错误` → `judge.sandboxTimeout (%s) must be greater than judge.compile.clockNs (%s): otherwise judge times out first when a compile reaches its wall-clock limit, and the result is reported as a system error`

  `judge/internal/testcase/testcase.go`
  - `非法 testDataVersionId: %q` → `illegal testDataVersionId: %q`
  - `读测试数据目录 %q: %w` → `read test data directory %q: %w`
  - `测试数据版本 %q 没有配对的测试点` → `test data version %q has no paired test cases`

  `sandbox/budget.go`
  - `HTTP 写期限（%s）必须大于本机会话期限（%s）：否则连接会先被切断，调用方看到的是传输失败而不是执行结论` → `the HTTP write deadline (%s) must be greater than the local session deadline (%s): otherwise the connection is cut first and the caller sees a transport failure instead of an execution conclusion`
  - `本机会话期限（%s）必须大于单次执行墙钟硬界（%s）：否则达到墙钟上限的命令会先被会话期限打断，超时被报成平台错误` → `the local session deadline (%s) must be greater than the single-execution wall-clock hard limit (%s): otherwise a command that reaches its wall-clock limit is interrupted by the session deadline first, and the timeout is reported as a platform error`

  `sandbox/config.go`
  - `sandbox.httpAddr 不能为空` → `sandbox.httpAddr must not be empty`
  - `sandbox.parallelism 必须为1～256，得到 %d` → `sandbox.parallelism must be 1 to 256, got %d`
  - `sandbox.store.maxBlobBytes 必须为1～64MiB，得到 %d` → `sandbox.store.maxBlobBytes must be 1 to 64MiB, got %d`
  - `sandbox.backend必须为%s或%s` → `sandbox.backend must be %s or %s`
  - `%s 后端不提供任何隔离，启用它必须显式设置 sandbox.allowUnsafeBackend` → `the %s backend provides no isolation; enabling it requires setting sandbox.allowUnsafeBackend explicitly`
  - `linux后端需要helperSocket、workspaceRoot和store.root` → `the linux backend requires helperSocket, workspaceRoot and store.root`
  - `sandbox排队或请求体上限无效` → `invalid sandbox queue or request body limit`
  - `sandbox.store总量/条目/保留期无效` → `invalid sandbox.store total/entry/retention`

  `sandbox/internal/api/run.go`
  - `JSON有尾随内容` → `JSON has trailing content`
  - `command 不能为空` → `command must not be empty`

  `sandbox/internal/api/server.go`
  - `sandbox HTTP容量已满` → `sandbox HTTP capacity is full`

  `sandbox/internal/backend/devhost.go`
  - `命令不能为空` → `command must not be empty`
  - `墙钟上限必须为正，得到 %d` → `the wall-clock limit must be positive, got %d`

  `sandbox/internal/backend/isolated.go`
  - `helper socket/暂存根不能为空` → `helper socket and staging root must not be empty`
  - `输入流为空` → `the input stream is nil`
  - `文件总量超过 %d bytes` → `total file size exceeds %d bytes`
  - `输入路径无效或重复: %q` → `invalid or duplicate input path: %q`
  - `helper返回负资源事实` → `helper returned negative resource facts`
  - `helper返回未清空的执行组` → `helper returned an execution group that was not emptied`
  - `helper输出超过请求上限` → `helper output exceeds the requested limit`

  `sandbox/internal/pool/pool.go`
  - `pool需要store、后端及有界正数parallelism/queueSize` → `pool requires a store, a backend and bounded positive parallelism/queueSize`

  `sandbox/internal/runner/collector.go`
  - `内联产物总量超过%d bytes` → `total inline artifact size exceeds %d bytes`
  - `未交付产物: %q` → `artifact not delivered: %q`
  - `未交付产物: %q` → `artifact not delivered: %q`

  `sandbox/internal/runner/request.go`
  - `命令或文件数量无效` → `invalid command or file count`
  - `maxProcesses=0无法启动` → `maxProcesses=0 cannot start anything`
  - `输出预算超过服务硬界` → `the output budget exceeds the service hard boundary`

  `sandbox/internal/runner/sources.go`
  - `输入总量超限` → `total input size exceeds the limit`
  - `file source: ref & text只能二选一` → `file source: ref and text are mutually exclusive`

  `sandbox/internal/store/disk.go`
  - `store目录/容量/保留期无效` → `invalid store directory/capacity/retention`
  - `store必须是私有目录: %s` → `store must be a private directory: %s`
  - `store独占锁: %w` → `store exclusive lock: %w`
  - `store拒绝非独占普通文件: %s` → `store refuses a non-exclusive regular file: %s`
  - `store未知条目: %q` → `unknown store entry: %q`
  - `store目标ref冲突或不可检查: %s: %v` → `store target ref conflicts or cannot be checked: %s: %v`
  - `store条目大小改变: %s` → `store entry changed size: %s`
  - `store仍有在途文件操作` → `the store still has file operations in flight`

  `sandbox/internal/workspace/workspace.go`
  - `暂存根不能为空` → `the staging root must not be empty`
  - `暂存根必须是服务所有的0700目录` → `the staging root must be a 0700 directory owned by the service`
  - `暂存锁文件不安全` → `the staging lock file is not safe`
  - `暂存根存在未知条目: %q` → `unknown entry in the staging root: %q`
  - `工作区存在未知文件: %q` → `unknown file in the workspace: %q`
  - `暂存根仍有未回收工作区` → `the staging root still has unreclaimed workspaces`

  `sandbox/sandbox.go`
  - `隔离后端启动探测失败: status=%s` → `isolated backend startup probe failed: status=%s`
  - `%s 后端不提供任何隔离，需显式设置 allowUnsafeBackend` → `the %s backend provides no isolation; it requires setting allowUnsafeBackend explicitly`
  - `当前平台不支持Linux隔离后端` → `this platform does not support the Linux isolation backend`
  - `sandbox服务必须非root运行，特权仅由helper持有` → `the sandbox service must run as non-root; privilege is held only by the helper`
  - `未知后端: %s` → `unknown backend: %s`
