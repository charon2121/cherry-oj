# AGENTS.md

本文件是智能体与人共用的入口规则；根目录的 `CLAUDE.md` 只是指回这里的一句话。

## 一、授权边界

1. **文档流程默认关闭。** 只有用户明确调用 `$dev-work` 或明确要求启用这套流程时，才读取并执行
   [仓库 skill](./.agents/skills/dev-work/SKILL.md)。普通 feature、bug、基建和重构请求不自动触发，
   不要求创建 WORK、阶段表、编号 TASK 或签闸。关闭的是过程管理，工程规范和必要验证继续生效。
2. **按用户实际授权做事。** 不越过已确认目标和范围；关键取舍改变、范围扩大或存在影响决定的未知时，
   先说明并解决对应问题。只要求文档时只交付文档，不延伸到业务实施。
3. **不能代替人接受结果。** 技术完成、测试全绿不能写成用户已接受，也不能伪造授权记录。
4. **尊重已确认设计。** 命名、契约字段和职责边界有讨论依据；改动前查依据，拿不准且影响决定时先澄清。

启用 skill 后的说明、确认和交付方式见 [`development/README.md`](./development/README.md)。
历史 WORK 的状态、闸和路径列表保存供查证，当前范围以用户现有授权为准，不因阅读旧记录自动恢复旧流程。

## 二、动手之前必须先读

动手之前先在下表里找到对应行，把它指向的文档读完，再写第一行代码或第一句文档。读之前不要开始改，也不要一边改一边回头查。

| 你要做什么                                 | 先读                                                                                                                      |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| 明确启用开发文档流程                       | [dev-work/SKILL.md](./.agents/skills/dev-work/SKILL.md)，再按当前任务读取必要参考                                         |
| 继续已有工作                               | 关联工作的短说明与必要依据；当前目标与边界以用户现有授权为准                                                            |
| 改动涉及用户能力、流程、权限或可见信息     | [`docs/product.md`](./docs/product.md) 与关联已确认设计；存在影响决定的未知时，**不要把假设固化进代码**                    |
| 需要知道服务职责、数据所有权、消息与接口边界 | [`docs/architecture.md`](./docs/architecture.md)：拓扑图、逐服务数据所有权、Kafka 与 HTTP 契约                            |
| 写 Go（judge / 执行层）                    | [`coding-standards/languages/go.md`](./docs/coding-standards/languages/go.md)                                                       |
| 写 Java（`apps/server`）                   | [`coding-standards/languages/java.md`](./docs/coding-standards/languages/java.md) + [`coding-standards/frameworks/spring.md`](./docs/coding-standards/frameworks/spring.md) + [`apps/server/TOOLCHAIN.md`](./apps/server/TOOLCHAIN.md) |
| 写 TypeScript                              | [`coding-standards/languages/typescript.md`](./docs/coding-standards/languages/typescript.md) + [`apps/web/TOOLCHAIN.md`](./apps/web/TOOLCHAIN.md) |
| 写 Python（`scripts/`、`deploy/`）         | [`coding-standards/languages/python.md`](./docs/coding-standards/languages/python.md)                                               |
| 动任何 Web UI、组件、样式或主题            | 上一行，**外加** [`coding-standards/frameworks/react.md`](./docs/coding-standards/frameworks/react.md) 和 [`docs/design-system/PROMPT.md`](./docs/design-system/PROMPT.md)：先选页面模板，再按页面语法写，交付前逐条回答自检七问。规则全文见 [`docs/design-system.md`](./docs/design-system.md)；设计值只在 `apps/web/design-system/` 手写一次，禁止把来源 demo 直接当生产代码 |
| 已启用 skill，写工作说明或附件             | [`development/README.md`](./development/README.md)：短说明、五类流程、按需附件和历史记录                                  |
| 写任何代码前                               | [`coding-standards/general.md`](./docs/coding-standards/general.md)：通用编码指令与优先级规则                              |
| 本项目自己踩出来的跨语言约定               | [`coding-standards/project-conventions.md`](./docs/coding-standards/project-conventions.md)：命名、单位与契约、零值陷阱、错误边界、资源、依赖方向、待办锚点、测试 |
| 提交、hooks、CI 细节                       | [`docs/coding-standards/git-workflow.md`](./docs/git-workflow.md)                                                  |
| 各模块当前成熟度                           | [`docs/status.md`](./docs/status.md)：哪些部分已经能跑、哪些还是骨架                                                      |

顶层每个目录 = 一套构建工具 / 一种技术栈，互不侵入。跨服务与跨语言的共享 DTO 只在 `contracts/`
定义；`docs/` 只接收已确认、跨工作长期有效的全局事实，`development/` 保存明确启用流程的工作与历史资料。
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
- **执行层完全不懂判题。** 编译、比对、verdict 全在判题编排；执行层（judge 进程内）与 sandbox
  执行器只报告执行事实。这条守不住，整个分层就没意义了。
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
