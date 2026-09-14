# AGENTS.md

本文件是智能体与人共用的入口规则；根目录的 `CLAUDE.md` 只是指回这里的一句话。

## 一、授权边界

下面几条没有任何程序能拦住，全靠自觉，违反的代价也最不可逆。

1. **不能代替人做决定。** 人工确认收拢在每个工作的两道闸——开工前的**意图闸**、收束时的
   **验收闸**，只能由人执行 `scripts/work gate <WORK> intent|acceptance`。格式检查通过、测试
   全绿、你自己认为内容完整，都不构成授权。智能体可以准备材料、说明前置条件已满足、列出待
   确认项，但不能签闸。
2. **文档与实施是两个回合。** 用户第一次说明意图时，只整理 WORK、定义、体验、设计、计划和
   TASK 文档，做完只读检查后**停下来请人审核**。「完成这个功能」不能同时充当文档通过和实施
   授权；只有用户在看到文档后、于后续消息中明确表示通过并允许执行，才能改业务代码、迁移数据
   或部署。用户只要求改文档时，交付文档后停止。
3. **不越过任务边界。** TASK 的 `read_paths` / `write_paths` / `forbidden_paths` 是硬边界。
   需要越界时先升级上游计划或设计、写明理由，不要先动文件——扩大路径列表是改变范围，不是
   实现细节。
4. **改动前先问清楚。** 本项目的很多设计（命名、契约字段、职责边界）是反复讨论定下来的，
   不是随手写成这样的。拿不准就先问，别先改。
5. **技术完成不等于产品确认。** TASK `done` 只表示实现完成；测试全绿不能自动代签人工产品判断
   或关键风险确认。`scripts/work refresh` 同样只按已有事实推导状态，不代替人做判断。

规则全文、闸的前置条件与撤回方式见 [`development/README.md`](./development/README.md)
§文档审核与执行授权、§人工确认只有两个点。

## 二、动手之前必须先读

动手之前先在下表里找到对应行，把它指向的文档读完，再写第一行代码或第一句文档。读之前不要开始改，也不要一边改一边回头查。

| 你要做什么                                 | 先读                                                                                                                      |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| 开发、修复、重构                           | [`development/README.md`](./development/README.md)，并运行 `scripts/work overview`                                        |
| 承接某个任务                               | `scripts/work context TASK-xxx`，它给出上游依据与代码读写边界                                                             |
| 了解某个工作的全貌                         | `scripts/work board WORK-xxx`（闸、流程、要求覆盖、任务、下一步）                                                         |
| 改动涉及用户能力、流程、权限或可见信息     | [`docs/product.md`](./docs/product.md) 与关联 FEATURE；存在 blocking 未知或定义未确认时，**不要把假设固化进代码**          |
| 需要知道服务职责、数据所有权、消息与接口边界 | [`docs/architecture.md`](./docs/architecture.md)：拓扑图、逐服务数据所有权、Kafka 与 HTTP 契约                            |
| 写 Go（judge / sandbox）                   | [`coding-standards/languages/go.md`](./docs/coding-standards/languages/go.md)                                                       |
| 写 Java（`apps/server`）                   | [`coding-standards/languages/java.md`](./docs/coding-standards/languages/java.md) + [`coding-standards/frameworks/spring.md`](./docs/coding-standards/frameworks/spring.md) + [`apps/server/TOOLCHAIN.md`](./apps/server/TOOLCHAIN.md) |
| 写 TypeScript                              | [`coding-standards/languages/typescript.md`](./docs/coding-standards/languages/typescript.md) + [`apps/web/TOOLCHAIN.md`](./apps/web/TOOLCHAIN.md) |
| 写 Python（`scripts/`、`deploy/`）         | [`coding-standards/languages/python.md`](./docs/coding-standards/languages/python.md)                                               |
| 动任何 Web UI、组件、样式或主题            | 上一行，**外加** [`coding-standards/frameworks/react.md`](./docs/coding-standards/frameworks/react.md) 和 [`docs/design-system/PROMPT.md`](./docs/design-system/PROMPT.md)：先选页面模板，再按页面语法写，交付前逐条回答自检七问。规则全文见 [`docs/design-system.md`](./docs/design-system.md)；设计值只在 `apps/web/design-system/` 手写一次，禁止把来源 demo 直接当生产代码 |
| 写 WORK / 定义 / DESIGN / TASK 等过程文档  | [`development/README.md`](./development/README.md)：层级、状态机、证据要求、信息优先级，以及定义层第一节的通俗语言要求和 `00-work.md` 只有三节的边界 |
| 写任何代码前                               | [`coding-standards/general.md`](./docs/coding-standards/general.md)：通用编码指令与优先级规则                              |
| 本项目自己踩出来的跨语言约定               | [`coding-standards/project-conventions.md`](./docs/coding-standards/project-conventions.md)：命名、单位与契约、零值陷阱、错误边界、资源、依赖方向、待办锚点、测试 |
| 提交、hooks、CI 细节                       | [`docs/coding-standards/git-workflow.md`](./docs/git-workflow.md)                                                  |
| 各模块当前成熟度                           | [`docs/status.md`](./docs/status.md)：哪些部分已经能跑、哪些还是骨架                                                      |

顶层每个目录 = 一套构建工具 / 一种技术栈，互不侵入。跨服务与跨语言的共享 DTO 只在 `contracts/`
定义；`docs/` 只接收已确认、跨工作长期有效的全局事实，`development/` 以 WORK 为入口保存过程。
`tutorial/`、`notes/`、`test/`、`draft/`、`dev-dependency/` 在 `.gitignore` 中，新克隆不保证存在。

## 三、跨语言铁律

违反下面几条写出来的不是风格问题，是错误，且它们跨语言生效。
展开的理由、案例和踩坑来源见 [`project-conventions.md`](./docs/coding-standards/project-conventions.md) §1.2、§1.4、
§1.5 与 [`architecture.md`](./docs/architecture.md) §1。

- **`contracts/*.json` 是唯一真源。** 别自己发明字段；改动顺序永远是契约 → 各语言类型 → 实现。
- **时间一律 ns，内存一律 bytes，字段名自带单位**：`cpuNs`、`memoryBytes`、`stdoutMaxBytes`。
- **「没配置」和「限制为 0」必须在入口掰开。** 宁可起不来，也别悄悄跑错。
- **未知情况往严格的方向倒。** `worse()` 查不到的 verdict 当成最严重，否则后果是错题判成 AC。
- **区分「这次对话成不成」和「那个程序跑得怎么样」。** TLE、WA、段错误一律 HTTP 200；只有 JSON
  解不开、缺必填字段才 400。
- **外部字符串拼进路径前先用正则关死。** 已经踩过三次。
- **sandbox 完全不懂判题。** 编译、比对、verdict 全在 judge。这条守不住，整个分层就没意义了。
- **每个服务只写自己的数据库。** 跨服务不连表、不共享 Mapper、不直接读对方 schema。
- **源码不进 Kafka。** submission-service 先冻结不可变 JudgeInput，judging-service 再按
  submissionId 从受保护的内部 API 拉取。

## 四、提交

完整流程（hooks 做什么、CI 检查什么、为什么这样取舍）见
[`git-workflow.md`](./docs/git-workflow.md)。每次都要遵守的是：

- 新克隆先跑一次 `sh scripts/setup-hooks.sh`——它**不能**自动生效。
- **一个 commit 一件事**，且每个 commit 都要能独立编译。
- 标题用 Conventional Commits，正文用中文写**为什么**——「改了什么」diff 里有。重点写清楚
  「不改会怎样」和「当时在两个方案间怎么权衡」。
- **只在用户要求时提交或推送。** CI 红了先修，别在红的基础上叠新提交。
