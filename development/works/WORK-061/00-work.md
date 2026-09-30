---
id: "WORK-061"
type: "work"
title: "judge 进程内调用 C 执行器并移除 sandbox HTTP 服务"
status: "todo"
work: null
owners: ["team/judge-engine"]
risk: "high"
impact: "system"
concerns: ["security", "reliability"]
depends_on: []
related: ["VERIFY-062"]
implements: []
verifies: []
tags: []
required_documents: ["verify"]
required_checks: ["definition", "scope", "impact-analysis", "independent-review", "rollback", "automated-tests", "cross-module-regression", "reliability", "security"]
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
read_paths: ["README.md", "apps/judge-engine", "apps/sandbox", "deploy/sandbox-linux", ".github/workflows", "contracts", "docs", "compose.yaml", "compose.standalone.yaml", "AGENTS.md", "scripts/contracts_test.py"]
write_paths: ["apps/judge-engine", "apps/sandbox/README.md", "deploy/sandbox-linux", ".github/workflows", "contracts/run.schema.json", "scripts/contracts_test.py", "docs", "compose.yaml", "compose.standalone.yaml", "AGENTS.md", "README.md"]
forbidden_paths: ["apps/server", "apps/web", "apps/sandbox/src"]
created_at: "2026-09-29"
updated_at: "2026-09-30"
format: "compact"
work_type: "maintenance"
---

# WORK-061：judge 进程内调用 C 执行器并移除 sandbox HTTP 服务

<!-- 前五节是人工审核的唯一主文，普通工作以一屏为目标，不复制一份摘要。
用日常语言说明变化、代价与验收；路径、命令和实现步骤写在执行方案与元数据。
新产品写使用场景；修复写实际/预期和复现；维护写不变量；改进写基线/目标。
附件的关键取舍必须回到这里；人确认这些内容，不代表审查全部实现细节。 -->

## 变化

隔离已经交给每次执行调用一次的 C 执行器，但 judge 仍要先经过一层独立的 sandbox HTTP 服务：
judge 把源码上传成 blob，再用 HTTP 请求 sandbox，sandbox 才去调用执行器。这层服务原本是为
「非特权前端 + 常驻特权守护进程」设计的；守护进程删掉之后，它只剩排队、存临时文件、把执行事实
翻译成状态这几件事，都不需要单独一个进程。

完成后，judge 在自己的进程里直接写 box、调用执行器。节点上只剩一个服务（judge）加一个执行器，
少一个服务单元、一段 HTTP 往返、一套 blob 接口与它的契约，也少一道要和其他期限对齐的超时。
判题结果、对外接口（judging-service ↔ judge）与隔离强度都不变。

## 边界

做：
- judge 用进程内的执行层替代 HTTP 客户端，删除 sandbox 服务的入口、HTTP 接口与相关配置。
- 原生部署与 CI 改为只有 judge 服务：权限、cgroup 委派、rootfs 核对、健康检查、部署自检随之迁移。
- 本地 Compose 只保留 judge 容器（devhost 后端，仍只用于可信代码开发）。
- 同步设计文档、契约与项目规则中的相应表述。

不做：
- 不改 C 执行器的代码与 box 约定（只改它的受信配置里的服务身份）。
- 不改判题逻辑、verdict 映射、judging-service 与节点协议（`judge-node.schema.json`、`judge.schema.json`）。
- 不改 Java 服务与前端。
- 不借机简化 store、runner、pool 的内部实现；它们作为库原样搬进 judge 的装配。

不能破坏：
- 「执行层不懂判题」仍然成立：它从进程边界变为包边界，由 `layout_test.go` 检查
  执行层的包不得引用 judge 的判题代码。
- 回收未确认时停止接单、失败时不发布产物、限额硬界与三类退出码的处理，行为完全不变。
- 原有 93 项必需回归覆盖的隔离、计量、故障恢复与业务闭环能力，一项不少地继续覆盖。

## 取舍

推荐：合并为一个 judge 服务，执行层作为 judge 进程内的库。

需要接受的代价：

1. **judge 服务单元的加固变弱。** 执行器靠 setuid 提权，调用它的服务单元不能开启
   `NoNewPrivileges` 及其隐含项（`RestrictAddressFamilies`、`LockPersonality`、`ProtectKernel*`、
   `RestrictSUIDSGID`、`RestrictNamespaces`、`ProtectControlGroups`），能力边界集要放开执行器需要的
   8 项。今天这些让步只落在一个不联网的小服务上；合并后落在 judge 上——它要连控制面、接收测试
   数据安装、持有控制面 token。judge 进程本身仍然非 root、没有任何有效能力；网络仍由
   `IPAddressDeny/Allow` 限制在本机与控制面。
2. **judge 能直接调用执行器。** 今天 judge 被攻破也能通过 `/run` 让 sandbox 执行任意命令，
   所以攻击面没有实质扩大；真正的防线一直是执行器与隔离本身。
3. **一个进程出问题影响全部。** 执行层 panic 或回收未确认会让 judge 停止接单，而不是只让
   sandbox 停。这与今天的实际效果一致（sandbox 停了 judge 也判不了），只是更直接。
4. **CI 要重写一大块。** 内核套件里所有经由 sandbox HTTP 的链路与故障用例（约二十余项）要改为
   经由 judge 的 `/judge` 或直接驱动执行器；必需回归的用例编号会随之改名或合并。

不推荐的备选：保留 sandbox 服务但只做本机 Unix socket——省不掉服务单元与协议，收益太小。
另一个备选是把 judge 拆出一个只负责调用执行器的小进程来保住加固项，但那就是换个名字的
sandbox 服务，与本工作目的相反。

## 未知

- 没有阻塞开工的未知。
- 命名已确认：judge 模块里的 `sandbox/` 目录改名为 `execution/`（执行层），避免与 C 执行器
  `apps/sandbox` 同名。节点目录 `/var/lib/cherry-sandbox`、slice 名 `cherry-sandbox.slice` 与
  judge 单元名保持不变。
- 合并后的 judge 服务资源上界按两者之和估算（见执行方案），需要在业务闭环 CI 中确认不因
  内存或任务数上限误报平台错误。
- judge 的 Go 进程会与执行器共处同一个 supervisor 组；执行器单次运行的内存很小，但并发 4 个时的
  峰值需要在内核套件中实测一次。

## 验收

- AC-001：原生部署只有 `cherry-sandbox-judge.service` 一个服务单元（外加 slice），节点能完成注册、
  接收测试数据并判题；仓库中不再有 sandbox HTTP 服务的入口、接口、配置与 `run.schema.json`。
- AC-002：judge 服务身份非 root、无有效能力；能力边界集恰为执行器需要的 8 项，逐项删除任意一项时
  judge 在启动自检阶段失败、不注册上线。
- AC-003：原有必需回归覆盖的能力全部有对应用例并通过：执行器真实内核测试、隔离与计量、
  执行器被杀与 judge 被杀/停止后的回收与恢复、原生安装十项、真实页面到 Kafka 的业务闭环。
- AC-004：judge 的判题代码不能引用执行层的实现细节、执行层不能引用判题代码，由依赖检查测试把守；
  回收未确认时 judge 停止接单并拒绝注册新的判题。
- AC-005：本地 Compose 只启动 judge（devhost），容器冒烟 CI 通过；设计文档、架构文档、
  AGENTS.md 与部署 README 描述新结构，文档链接校验通过。

## 执行方案

**进程内执行层。** judge/flow 只依赖三个方法的接口 `Sandbox{Upload, Run, Delete}`。新增一个进程内
实现，组合现有的 `sandbox/store`（blob）、`sandbox/pool`（并发与排队）、`sandbox/runner`（限额归一化、
事实到 Status）与 `sandbox/backend`（executor / devhost），替换 `judge/sandboxclient`。flow 与 checker
不改。`sandbox/` 目录更名为 `execution/`（执行层），避免与 C 执行器 `apps/sandbox` 同名；
`judge/node/preflight` 的「对端是 cherry-oj sandbox」检查改为「进程内后端是 linux 且启动冒烟通过」。

**删除。** `cmd/sandbox`、`sandbox/api`（`/run`、`/blobs`、`/version`）、`judge/sandboxclient`、
sandbox 的配置加载与 `sandbox.example.yaml`、`budget.go` 中的 HTTP 层期限、`contracts/run.schema.json`
及其契约测试。`internal/contract` 中 `RunSpec`/`RunResult` 仍作为 flow ↔ 执行层的 Go 类型保留。
judge 的 `sandboxURL`、`sandboxTimeout` 删除（`inlineThresholdBytes` 保留，见变更记录）；执行层配置（backend、
executorPath、boxesRoot、parallelism、queueSize、store）并入 judge 配置的 `execution` 段。
每次执行的期限由执行器调用上界（启动 3s + 墙钟 + 回收 10s）给出，flow 原有的
「有效墙钟 < sandboxTimeout」检查改为「≤ 墙钟硬界」。

**依赖边界。** `layout_test.go` 改为检查包之间：`execution/...` 不得引用 `judge/...`；
`judge/flow`、`judge/checker` 只能引用 `execution` 的接口类型，不得引用 `execution/backend`、
`execution/store` 等实现包，只有 judge 的装配包可以。

**原生部署。**
- 服务身份：执行器受信配置的 `service_uid/gid` 改为 cherry-judge（61010）；执行器文件改为
  `root:cherry-judge 4754`。cherry-sandbox 账户（61001）不再需要，安装时不再创建，已有的保留不删。
- 单元：删除 `cherry-sandbox.service`；`cherry-sandbox-judge.service` 获得 `Delegate=cpu memory pids`、
  8 项能力边界集，去掉与 setuid 冲突的加固项，保留 `ProtectSystem=strict`、`ProtectHome`、
  `PrivateTmp`、`IPAddressDeny/Allow`、`UMask=0077`；启动改为 `judge-start.py`
  （由 `sandbox-start.py` 改名：核对 rootfs、建 supervisor/jobs 并写限额，再 exec judge）。
- 资源上界：slice 不变（1536M/384/200%）；judge.service 1280M/320/200%；supervisor 640M/160/100%；
  jobs 640M/160/100%。部署清单与 judge 的 `deployment_spec.go` 同步为 4 个组（含 slice）× 4 项 = 16 项上界。
- 发布：release 只含 `bin/judge` 与 `libexec/sandbox`；`health.py` 只检查 judge。

**CI。** 内核套件：执行器真实内核测试不变；原 HTTP 链路（smoke/repeat/concurrency）改为经 judge
的 `/judge` 判一道 C++ 题；故障组改为 judge-SIGKILL、judge 平滑停止、执行器 SIGKILL、init SIGKILL、
容量与并发隔离。原生套件与业务套件随部署变化调整（单元数、账户、上界数、能力删减对象）。
`cases.json` 的用例改名或合并在同一提交中完成，保持「每项能力有且只有一处必需回归」。

**分步提交（每步 CI 全绿后再下一步）：**
1. judge 引入进程内执行层并以配置开关选择（HTTP 客户端仍在），单元测试覆盖两种装配。
2. 部署、Compose 与 CI 切换到只有 judge；执行层目录更名。
3. 删除 sandbox 服务入口、HTTP 接口、客户端、契约与旧配置。
4. 文档与项目规则。

**回退。** 每一步是独立提交，按逆序 `git revert` 即可恢复；项目没有已部署的节点，不涉及线上迁移。

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 确认目标与边界 | ○ 就绪 | 必需 | WORK-061 `todo` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 实施 | · 未开始 | 必需 | WORK-061 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证与验收 | ▶ 进行中 | 必需 | VERIFY-062 `review` | 用可复现的证据确认要求逐条满足 |

## 变更记录

- 2026-09-29：创建。签署与重要变更在此记录，测试证据只写 VERIFY。
- 2026-09-30：用户审阅方案后明确「可以，改名为 execution，开始执行」：接受 judge 服务单元加固变弱的
  代价，确认目录改名。据此开始实施；意图闸仍由人签署，本记录不代签。
- 2026-09-30：实施中对方案的调整（均不改变已确认的目标、边界与代价）：
  - 分步：改名单独成提交；第二步拆成 Compose、原生部署、内核套件三个提交，每个提交 CI 通过后再继续。
  - 上界数：原方案写「3 个组 × 4 项 = 12 项」有误，实际受管组含 slice 共 4 个，为 16 项。
  - `inlineThresholdBytes` 保留：进程内同样要在「内联文本」与「先存 store 走 ref」之间取舍，删除它等于
    改判题编排的输入传递方式，超出本工作「不改判题逻辑」的边界。
  - 必需回归由 93 项变为 91 项：`native.kill-sandbox` 与 `kernel.handler-saturation` 随 sandbox 服务删除；
    judge 不暴露原始执行事实的几项改到能直接观察它们的地方（显式零限额、零输出预算、队列饱和改为
    执行层单元测试并列入必跑 Go 测试；错误可执行格式改由 extended.py 直接驱动执行器）；
    `kernel.http-kill` 改名 `kernel.judge-kill`。
  - 两次提交误带了下一步已暂存的改名与删除（4b1f1fb、3687486），各自的 CI 因此失败，下一步的提交补齐；
    此后改为按显式路径提交。
- 2026-09-30：待确认：根目录 README.md 仍描述 Compose 的 sandbox 容器、`SANDBOX_*` 变量与
  `docker compose logs judge sandbox` 命令（其中 logs 命令已失效）。该文件不在本工作的可写范围内，
  需用户确认是否扩大范围后更新。
- 2026-09-30：用户确认扩大范围：根目录 README.md 纳入可写范围，同步 Compose 只运行 judge 后的说明
  （服务构成、日志命令、配额变量与安全说明）。Compose 的 `SANDBOX_*` 变量名保持不变，只改说明。
