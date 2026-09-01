# cherry-oj

学习型 Online Judge。**先跑通端到端 MVP，再逐层硬化**——每一步都要能解释「为什么这么写」，
所以本仓库里的注释和文档密度高于一般项目，改代码时请一并维护。

## 系统拓扑

```text
浏览器 → apps/web (TS)
           │ REST / Session Cookie
         gateway-service (Java)
           ├─ user-service               用户、密码、角色、内部 JWT
           ├─ problem-service            题目版本、语言模板、测试数据元信息
           └─ submission-service         Submission、JudgeInput、Outbox/Inbox
                    │ Kafka judge.requests.v1
                    ▼
              judging-service            环境、部署、标定、任务租约与重试
                    │ POST /judge         跨语言 HTTP，契约以 contracts/*.json 为准
                    ▼
                 judge (Go)              编译 → 逐测试点 → 比对 → 汇总 verdict
                    │ POST /run, /blobs
                    ▼
                sandbox (Go)             安全执行一条命令，返回资源事实

judging-service ── Kafka judge.lifecycle.v1 ──► submission-service
```

五个 Java 服务位于 `apps/server` Maven 聚合工程中。每个有状态服务独立数据库，只写自己的表；
正式提交先由 submission-service 冻结不可变 JudgeInput，再通过 Kafka 异步判题。源码不进 Kafka，
judging-service 按 submissionId 从受保护的内部 API 拉取 JudgeInput。

**最重要的一条边界**：sandbox 只回答「安全地跑一段程序，给我资源用量和输出」，
**完全不懂判题**。编译、比对、verdict 全在 judge。这条守不住，整个分层就没意义了。

## 仓库结构

顶层每个目录 = 一套构建工具 / 一种技术栈，互不侵入；跨服务与跨语言的共享 DTO 只在
`contracts/` 定义。

```
cherry-oj/
├── contracts/          ★ 跨服务 / 跨语言契约（JSON Schema），唯一真源
├── apps/
│   ├── judge-engine/   Go 单模块，产出 judge + sandbox 两个二进制
│   │   ├── cmd/{judge,sandbox}/
│   │   ├── internal/{config,contract,judge,sandbox}/
│   │   └── config.example.yaml
│   ├── server/         Java / Spring Boot Maven 聚合工程 —— 五服务基础骨架已建立
│   │   ├── gateway-service/
│   │   ├── user-service/
│   │   ├── problem-service/
│   │   ├── submission-service/
│   │   └── judging-service/
│   └── web/            React / TypeScript —— 前端基础骨架已建立
├── docs/               ★ 已确认、跨工作项长期有效的全局产品与技术文档
├── development/        ★ 具体工作的定义、设计、计划、任务、验证与项目记忆
└── tutorial/           分阶段动手教程
```

`docs/` 与 `development/` 都进入 Git。前者只接收已经确认的全局事实，后者以 WORK 为入口保存功能
开发过程。`tutorial/`、`notes/`、`test/`、`draft/`、`dev-dependency/` 仍在 `.gitignore` 中，
是新克隆不保证存在的本地材料。

## 动手之前先读哪一份

**本文只保留每次会话都必须遵守的部分。** 展开的规范放在 [`docs/engineering/`](./docs/engineering/README.md)，
按需读——不相关的规范不该占用每次对话的上下文。

| 你要做什么 | 先读 |
|---|---|
| 写 Go（judge / sandbox） | [`docs/engineering/go.md`](./docs/engineering/go.md) |
| 写 Java（`apps/server`） | [`docs/engineering/java.md`](./docs/engineering/java.md) + [`apps/server/TOOLCHAIN.md`](./apps/server/TOOLCHAIN.md) |
| 写 TypeScript | [`docs/engineering/typescript.md`](./docs/engineering/typescript.md) + [`apps/web/TOOLCHAIN.md`](./apps/web/TOOLCHAIN.md) |
| 动任何 Web UI、组件、样式或主题 | 上一行，**外加** [`docs/design-system.md`](./docs/design-system.md) |
| 命名、错误、资源、依赖方向、测试的通用约定 | [`docs/engineering/conventions.md`](./docs/engineering/conventions.md) |
| 提交、hooks、CI 细节 | [`docs/engineering/git-workflow.md`](./docs/engineering/git-workflow.md) |
| 开发流程、工作项、两道闸 | [`development/README.md`](./development/README.md) |
| 各模块当前成熟度 | [`docs/engineering/README.md`](./docs/engineering/README.md) |

这些不是「有空看看」。写 Go 之前没读 `go.md`，大概率会踩进那里已经记录过的坑。

## 跨语言铁律

违反下面几条写出来的不是风格问题，是错误。展开的理由和案例见
[`conventions.md`](./docs/engineering/conventions.md)。

- **`contracts/*.json` 是唯一真源。** 各语言的类型照着它写，别自己发明字段；改动顺序永远是
  契约 → 各语言类型 → 实现。
- **时间一律 ns，内存一律 bytes，字段名自带单位**：`cpuNs`、`memoryBytes`、`stdoutMaxBytes`。
- **「没配置」和「限制为 0」必须在入口掰开。** `cpuNs=0` 是秒 TLE，`stdoutMaxBytes=0` 是必定 OLE，
  `parallelism=0` 是第一个请求就永久阻塞。宁可起不来，也别悄悄跑错。
- **未知情况往严格的方向倒。** `worse()` 查不到的 verdict 当成最严重——反过来写，某天加了新
  verdict 忘了进表，后果是**错题判成 AC**。
- **区分「这次对话成不成」和「那个程序跑得怎么样」。** TLE、WA、段错误一律 HTTP 200；只有 JSON
  解不开、缺必填字段才 400。
- **外部字符串拼进路径前先用正则关死。** 已经踩过三次。
- **sandbox 完全不懂判题。** 编译、比对、verdict 全在 judge。这条守不住，整个分层就没意义了。

## 提交

完整流程（hooks 做什么、CI 检查什么、为什么这样取舍）见
[`git-workflow.md`](./docs/engineering/git-workflow.md)。每次都要遵守的是：

- 新克隆先跑一次 `sh scripts/setup-hooks.sh`——它**不能**自动生效。
- **一个 commit 一件事**，且每个 commit 都要能独立编译。
- 标题用 Conventional Commits，正文用中文写**为什么**——「改了什么」diff 里有。重点写清楚
  「不改会怎样」和「当时在两个方案间怎么权衡」。
- **只在用户要求时提交或推送。** CI 红了先修，别在红的基础上叠新提交。

## 给 AI 助手的额外说明

- **改动前先问清楚。** 本项目的很多设计（命名、契约字段、职责边界）是反复讨论定下来的，
  不要顺手「优化」。拿不准就先问，别先改。
- **文档与实施必须分成两个回合。** 用户第一次说明意图时，只整理或修改 WORK、定义、体验、设计、
  计划和 TASK 文档，完成必要的只读检查与文档校验，然后停止并请用户审核。初始的“完成这个功能”
  不能同时视为文档通过和实施授权；只有用户在看到文档后，于后续消息中明确表示通过并允许执行，
  才能开始改业务代码、迁移数据、部署或执行其他实施任务。用户只要求改文档时，交付文档后停止。
- **不能代签文档审批。** AI 新写或修改的上游文档只能保持 `draft` / `review`。人工确认收拢到每个
  工作仅有的两道闸——开工前的**意图闸**与收束时的**验收闸**，由用户执行
  `scripts/work gate <WORK> intent|acceptance`，一次签署覆盖该闸下全部文档。决定类文档与 VERIFY
  不能再逐份 `set-status ... approved`（工具会拒绝）；DESIGN / PLAN / MEMORY 属于记录类，由
  `scripts/work refresh` 校验通过后自动置为 `checked`，不占用人的判断。格式检查、测试通过或 AI
  自己认为内容完整，都不能替代人的审核与执行授权。
- **定义层使用通俗语言。** 产品面入口是定义层文档（FEATURE / CAPABILITY / ISSUE / CHANGE /
  IMPROVEMENT），它们的第一节面向产品经理和非技术读者：能用日常语言说清楚时不用专业词；必须
  使用时先解释它对使用者意味着什么。报错原文、依赖坐标、字段、类、框架、协议、表名、代码路径
  和命令从第二节起才出现，实现细节放到 DESIGN、PLAN 或 TASK。
- **`00-work.md` 是控制面入口，不写产品面内容。** 它只有三节：工具生成的「流程」视图、「待确认项」
  和「变更记录」。为什么做、怎样算完成、有什么风险、影响哪里都归定义层——同一个问题在两处各自
  表述一定会漂移，而 WORK 不在信息优先级链上、也不携带 REQ / AC 锚点，冲突时无法判定以谁为准。
  「流程」由 `scripts/work` 渲染，控制面状态存在同目录的 `flow.json`；两者都不要手工编辑。
- **开发前读取上下文。** 先读 `development/README.md` 和 `development/WORKS.md`，运行
  `scripts/work list --type work`；要了解某个工作的全貌用 `scripts/work board WORK-001`（闸、流程、
  要求覆盖、任务、下一步），有对应 TASK 时用 `scripts/work context TASK-001` 获取上游依据和
  代码边界。没有工作项时先创建 WORK。
- **只执行 ready TASK。** TASK 的依赖、`read_paths`、`write_paths`、`forbidden_paths` 与完成标准必须
  明确。需要越界时先升级计划或设计，不直接扩大实现。
- **产品行为先看全局基线与 FEATURE。** 涉及用户能力、流程、权限或可见信息时，先读
  `docs/product.md` 和关联 FEATURE；存在 blocking 未知或定义未确认时，不把假设固化进代码。
- **开发后记录证据。** 用 `scripts/work set-status` 记录有理由的状态变化，在 VERIFY 写实际命令、环境、
  结果、遗留问题和剩余风险，再用 `scripts/work refresh WORK-001` 按事实刷新流程阶段和工作项状态。
- **结论不再成立时用 `outcome`，不要改 status。** 工作被后续工作取代或前提被证伪时，运行
  `scripts/work outcome <WORK> superseded --by <WORK> --reason ...`（或 `invalidated`，需留 MEMORY）。
  它确实走完过流程、通过过验证，把 status 改成 `cancelled` 是篡改历史；没有产出的工作才用 `cancelled`。
- **技术完成不等于产品确认。** TASK done 只表示实现完成；Agent 不能根据测试全绿自动代签人工产品
  判断或关键风险确认。
- **设计变更先写当前 WORK。** 经确认且长期跨工作有效时再同步 `docs/`；操作步骤变化同步 `tutorial/`。
  Web 设计系统的真实变更必须让同一 WORK/TASK 同时覆盖 `apps/web/design-system/` 与
  `docs/design-system/`，但普通 Web 命令不得用跨树 drift、copy 或 symlink 代替这项显式维护责任。
- 已知技术债在所属 `development/works/WORK-xxx/` 中建立 `60-task-TASK-xxx.md`，代码锚点使用
  `TODO(TASK-001): ...`。
- 教程里的代码是给人照着写的，保留 `// TODO(你来写)`，别直接把答案填满。
