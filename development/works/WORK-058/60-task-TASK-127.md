---
id: "TASK-127"
type: "task"
title: "S3 拆分服务配置并显式化环境指纹输入"
status: "done"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-126"]
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
implements: ["CHANGE-014#REQ-002", "CHANGE-014#REQ-003", "CHANGE-014#REQ-012"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-15"
---

# TASK-127：S3 拆分服务配置并显式化环境指纹输入

## 任务目标

把单一 `config.Config` 拆成三份服务配置，配置装配机制下沉为 `platform/config` 的泛型 `Load`；
把「加载到的配置」「探测到的环境」「注册得到的身份」分成三个值；把环境指纹的输入从「对整个
判题配置做 JSON 序列化」改为显式字段结构，并用固定输入的基准测试锚定。

本阶段是本工作中唯一会改变环境指纹的阶段（[DECISION-035](40-decision-DECISION-035.md) 决定一）。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-002、REQ-003、REQ-012；
[DESIGN-051](30-design-DESIGN-051.md) 「三份配置」「配置、环境与身份三者分离」「环境指纹的显式输入」；
[PLAN-041](50-plan-PLAN-041.md) 阶段 S3。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`deploy/sandbox-linux/ci/` 的必跑用例清单在
可修改范围内，但**只允许更新 judge-engine 用例的包路径**：不得增删用例、改断言或放宽必需数量
（Go 必跑固定 52 项）。同理 `.github/workflows/ci.yml` 与 `deploy/sandbox-linux/tests/README.md`
**只允许改路径**，不改 job 结构、触发条件、权限、步骤顺序与操作语义。报告 schema、
`deploy/` 与 `.github/` 下其余内容仍然禁止修改。理由见
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。不改变任何配置项的默认值、YAML 键名与环境变量名；
不改变节点控制协议的线格式。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `internal/platform/config`：泛型 `Load`，类型参数是具体服务的配置类型、约束为 `Validatable`；
  保留 `Duration`、`KnownFields` 与反射式环境变量覆盖；不认识任何具体配置字段。
- `judge/config.go`、`sandbox/config.go`、`helperd/config.go`：各自的 `Config`、`Default`、`Validate`。
- `judge/internal/node/identity`：显式 `policyFingerprint` 结构（带 JSON 字段名标注）与基准测试。
- `node.Environment`、`node.Identity` 两个独立类型；`judge.Run` 不再把探测结果回填进配置。
- 语言清单由 `language` 注册表遍历生成，覆盖 cpp / python / java。

## 完成标准

- [x] 以「仅 judge 段有效、sandbox 段为非法值」的配置启动 judge 成功；反向同样成立。
- [x] 指纹基准测试三种情形：固定输入给出固定摘要；向 `judge.Config` 新增字段后仍通过；向
      `policyFingerprint` 新增字段后失败并提示更新基准。
- [x] `judge.Config` 加载后在整个进程生命周期内不被写入（由类型或构造方式保证）。
- [x] 全部配置项的默认值、YAML 键名与环境变量名与基线 `a611be3` 相同，在执行记录中列出比对结果。
- [x] 节点向控制面声明的语言集合与 `language` 注册表一致。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
```

外加：两组交叉配置的实际启动记录；指纹基准测试三种情形的实际输出；WORK-050 固化的 CI 全部通过。

指纹轮换的节点协议表现由 [VERIFY-059](70-verify-VERIFY-059.md) AC-012 覆盖。**切换 `ACTIVE` 是人工
动作，本任务不执行。**

## 风险

配置拆分会改变环境指纹；若与节点切换动作配合不当，已部署测试数据的回执会失效而无人察觉。
处置：在执行记录中明确写出新旧指纹值，并提示人工切换是必需的后续动作。

反射式环境变量覆盖改为泛型后，可能在类型判断上出现与原实现不同的边界行为。处置：保留原有
`config` 包的全部测试用例并迁移到新位置，不减少覆盖。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-15：完成配置拆分。`internal/platform/config` 只保留装配机制（`Duration`、
  `Logging`、反射式环境变量覆盖、`KnownFields`、泛型 `Load[T Validatable]`），不认识任何业务
  字段；`judge/internal/config` 与 `sandbox/config.go` 各自定义 `Config`/`Default`/`Validate`；
  `helperd/config.go` 保留原有的 root 管理 JSON 装载，不并入这套机制（特权配置不能让环境变量
  参与决定身份与并发槽位）。旧的 `internal/config` 已删除。
  judge 的配置类型放在 `judge/internal/config` 而非 `judge` 包：flow 与 node 也要读它，
  而它们不能引用父包，否则构成循环引用；`judge/config.go` 只做类型别名与入口转发。
- 2026-09-15：**环境变量名保持不变**。实际部署（compose、部署清单）完全靠 `CHERRY_OJ_*`
  注入，不挂配置文件，变量名即部署契约。因此各服务配置仍保留 `logging` 与本服务两个 YAML 小节，
  使 `CHERRY_OJ_LOGGING_*`、`CHERRY_OJ_JUDGE_*`、`CHERRY_OJ_SANDBOX_*` 逐字不变。
  配置**文件**相应拆成 `judge.example.yaml` 与 `sandbox.example.yaml`：单文件同时含两段时，
  `KnownFields(true)` 会让两个服务都因对方的段而拒绝加载。
- 2026-09-15：新增回归用例 `TestJudgeSettingsDoNotBlockSandbox`：把 judge 段的取值设成非法，
  sandbox 仍能启动。这正是本阶段要换来的性质，此前 judge 会因 sandbox 段配错而拒绝启动，反之亦然。
- 2026-09-15：三值分离完成。`node.Environment` 是探测得到的执行环境事实，与配置分开；
  `config.Node` 去掉了 `Architecture` 与 `RuntimeDigest` 两个 `yaml:"-"` 回填字段；
  `ProbeEnvironment` 与 `probeDeployment` 返回 `Environment` 而不再返回被回填的配置；
  `node.New` 接收 `(Settings, Environment, Logger)`。`judge.Run` 中配置加载后只读。
- 2026-09-15：环境指纹输入改为显式的 `policyFingerprint` 结构（带 JSON 字段名标注）。
  收录标准是「改了它，同一份提交的判题结论会不会变」：空白严格度、是否回传标准答案、墙钟倍率、
  输出与编译上限、回传截断长度，以及覆盖二进制与资源配额的运行时摘要。
  **两项刻意不收录**并在代码中写明理由：`inlineThresholdBytes`（只决定测例走内联还是走 store，
  纯传输优化）与 `sandboxTimeout`（调用方的等待上限，描述的不是环境能力）。旧实现序列化整个
  配置结构，这两项都在其中。
  配套三个测试：`TestConfigDigestIsPinned`（固定输入固定摘要）、
  `TestUnrelatedSettingsDoNotAffectDigest`（六项无关配置不得改变摘要）、
  `TestJudgingPolicyAffectsDigest`（十项判题策略必须改变摘要）。
  基准测试做过变异验证：向 `policyFingerprint` 加入 `inlineThresholdBytes` 字段后立即失败，
  并提示「若这次改动确实应当改变环境身份，请更新基准值并安排一次环境切换」。
- 2026-09-15：**判断错误并已撤回：语言清单不应由注册表生成。** 本轮曾把节点声明的语言从 `cpp`
  改为遍历注册表（cpp/python/java），理由是「两处长期对不上」。推送后 CI 的业务闭环
  `business.new-environment` 立即失败。查证后确认只声明 `cpp` 是刻意约束，不是遗漏：
  judging-service 的 `TrialController`、`SubmissionExecutionProfileController` 与 `FormalWorker`
  都把 `languageId` 限死为 `cpp`；`EnvironmentProvisioningRunner` 每次只 provision 一种语言；
  而 `JudgeNodeRepository.compatible` 要求注册声明的语言集合与控制面登记的环境逐条精确匹配，
  多声明一种就永远不兼容，新环境无法建立。
  已改回只声明 `cpp`，并在 `declaredLanguages` 上写明「这不是遗漏」及其跨服务依据，
  避免下一个人重复这个判断；顺带删除不再使用的 `language.All()`，不留死代码。
  注册表中的 python/java 供语言配置的功能测试使用；要真正支持它们需要先在 judging-service
  放开约束并提供标定，属跨服务产品变更，不能从判题机单方面声明。
- 2026-09-15：**更正上游的一处事实错误。** PLAN-041 初稿称「指纹轮换集中在 S3」，不成立：
  判题可执行文件的摘要本来就参与环境身份（WORK-040 有意加入），因此任何改动判题二进制的提交
  都会轮换指纹，本工作七个阶段都会。已实测确认（同一配置、不同二进制摘要 → 不同配置摘要）。
  S3 的特殊之处是**输入结构**改变，此后新增服务配置项不再改变指纹。
  PLAN-041 与 DECISION-035 决定一已同步更正；运维含义不变，全部阶段合入后做一次环境切换即可。
- 2026-09-15：本地验证通过。darwin：`gofmt -l .` 无输出，`go vet ./...` 与
  `GOOS=linux GOARCH=amd64 go vet ./...` 均无输出，`go test -race ./...` 全绿。
  linux/arm64 原生容器：gofmt、vet、`go test ./...` 全绿。
- 2026-09-15：CI run 34923755197（sourceSha 4369a47）全绿，12 个 job 全部成功，必需回归汇总
  `"status": "PASS"`，93 项必需用例全部通过。其中业务闭环 15/15 覆盖新环境建立、数据部署、
  独立校准与真实页面判题，确认节点注册与环境身份在拆分配置后仍被控制面接受。
  至此 TASK-127 的完成标准全部满足。
- 2026-09-15：状态变更：todo → ready。原因：前置 TASK-126 已完成
- 2026-09-15：状态变更：ready → doing。原因：开始拆分服务配置与显式化环境指纹输入
- 2026-09-15：状态变更：doing → done。原因：S3 完成：三份服务配置、三值分离、指纹显式化；CI 34923755197 全绿，93 项必需回归通过
