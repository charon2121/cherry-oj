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

## 当前进度

| 部分 | 状态 |
|---|---|
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events；run / verdict 保持稳定 |
| sandbox（store, container, runner, pool, api, cmd） | ✅ 可独立 `curl` |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，可与 sandbox 双进程联调 |
| Docker 部署 | ✅ judge / sandbox 双容器 Compose（开发与 MVP） |
| `apps/server`（五个 Java 服务） | ✅ Maven 聚合、独立端口、健康检查与基础测试；业务 API 待实现 |
| `apps/web` | ✅ React、Router、Query、样式、组件与测试工具骨架；业务页面待实现 |

---

# 编码规范

本文只写**跨语言通用**的约定。**各语言的具体规范另放一处**：

| 语言 | 规范 |
|---|---|
| Go（`apps/judge-engine`） | [`.claude/rules/go.md`](./.claude/rules/go.md) |
| Java（`apps/server`） | 见下方 §二和 [`apps/server/TOOLCHAIN.md`](./apps/server/TOOLCHAIN.md) |
| TypeScript（`apps/web`） | 见下方 §三和 [`apps/web/TOOLCHAIN.md`](./apps/web/TOOLCHAIN.md) |

写 Go 代码前请先读 `.claude/rules/go.md`——包与文件组织、命名、接口与依赖方向、
错误处理、资源与生命周期、并发、日志、子进程、测试写法都在那里。

以下多数条款是从这个项目实际踩过的坑里提炼的，括号里给了对应的代码位置。

## 一、通用（不分语言）

### 1.1 命名

- **名字要能被「主语 = 接收者」读通。** `pool.borrow()` 是错的——池子是出借方，
  借东西的是调用方；应为 `pool.get()` / `pool.put()`。
- **动词成对，且全仓统一。** 取/存一律 `Get` / `Put`：`store.Get/Put`、
  `container.GetFile/PutFile`、`pool.get/put`。少记一对词，读代码不用切换语感。
  如果一个名字的天然反义词恰好用不了（`borrow` 的反义是关键字 `return`），
  说明该换的是**整对**，而不是给它配个替补。
- **限定词只在存在对立面时才有信息量。** 池子里的容器天然就是闲置的
  （在用的已经被取走了），字段就叫 `containers` 而不是 `idle` ——
  写上 `idle` 等于把一条本就成立的不变量又抄了一遍。
- **别让名字结巴。** `pool.New()` 而不是 `pool.NewPool()`；`container` 包里的文件
  叫 `host.go` 而不是 `host_container.go`。
- **给意图起个名字，别把判断散出去。** `lang.NeedsCompile()` 而不是到处
  `len(lang.Compile) > 0`；`mode.UsesProblemTestdata()` 而不是到处比较枚举值。

### 1.2 单位与契约

- **时间一律 ns，内存一律 bytes，字段名自带单位**：`cpuNs`、`clockNs`、`memoryBytes`、
  `stdoutMaxBytes`。数字长，但永远不会有单位歧义。
  例外：给人编辑的配置文件里时长写 `60s`（受众不同，规则可以不同）。
- **`contracts/*.json` 是唯一真源**，各语言的类型照着它写，别自己发明字段。
- **契约会和实现漂移**，所以要有对齐测试：把 schema 里的示例 JSON 解到本语言类型，
  断言关键字段落位（`internal/contract/judge_test.go`）。
  这条是从「schema 说 stdin 可以是裸字符串、Go 那边是结构体、直到 curl 第一下才发现」
  学来的。

### 1.3 什么进配置，什么进请求

| | 进请求（`JudgeRequest` 等） | 进配置（`internal/config`） |
|---|---|---|
| 判据 | **每次请求都可能不同** | **进程生命周期内不变** |
| 例子 | 完整源码、languageId、版本 id、绝对时空限制 | 环境指纹、测试数据根目录、比对方式、输出上限、监听地址 |
| 谁定的 | 出题人 / 提交者 | 部署方 |

推论：**判题机的自保策略（输出上限、墙钟倍率、编译资源）不塞进跨语言契约**——
那等于逼调用方去理解判题机的内部机制。

### 1.4 零值陷阱（本项目最高频的 bug 来源）

结构体字段不填就是零值，而 `0` 往往是一个**合法但极端**的取值：

| 字段 | 直接用零值的后果 |
|---|---|
| `cpuNs` | 每个测试点秒 TLE，而结论看着完全合理 |
| `stdoutMaxBytes` | 一个字节都不收，且必定 OLE |
| `ClockNs` | `WithTimeout(ctx, 0)` 立刻超时，进程还没起就 TLE |
| `parallelism` | 无缓冲 channel，第一个请求就永久阻塞 |

规矩：**「没配置」和「限制为 0」必须在入口掰开**。构造函数兜底（`newCapWriter`、
`pool.New`、`api.New`），或显式 `Validate()` 并拒绝启动/拒绝请求。
**宁可起不来，也别悄悄跑错**——伪装成合理结论的错误最难查。

### 1.5 错误与边界

- **区分「这次对话成不成」和「那个程序跑得怎么样」。** HTTP 状态码描述前者，
  业务状态字段（`RunResult.Status` / `JudgeResult.Verdict`）描述后者。
  TLE、WA、段错误一律 **200**——沙箱工作正常，只是被跑的程序失败了。
  只有 JSON 解不开、缺必填字段才 400。
- **未知情况往严格的方向倒。** `worse()` 查不到的 verdict 当成最严重——
  写成「查不到返回 a」的话，某天加了新 verdict 忘了进表，结果是**错题判成 AC**。
- **错误信息要能定位。** 带上路径、字段名、对方返回的 body 片段。
  `unexpected status 400` 会让人调试到怀疑人生。
- **外部字符串拼进路径前先用正则关死。** `testDataVersionId`、`ref` 都来自 HTTP 请求，
  `filepath.Join(root, "../../etc")` 会老老实实跳出去。
  已出现三次：`container.resolve`、`store.refPattern`、`testcase.idPattern`。
- **不返回恒为 nil 的 error**——只会让每个调用点白写一次 `if err != nil`。

### 1.6 资源

- **能流式就别攒全量。** 几十 MB 的测例 / 编译产物边读边写，别整份读进内存。
  只存「怎么打开」而不是内容（`testcase.Blob`）。
- **循环内的资源循环内释放**，别攒到函数返回。
- **清理动作别用那个正在被取消的上下文**——请求一取消，清理立刻失败，资源永久泄漏。
- **网络客户端必须设超时**，且要大于对端最慢的一次操作。

> 这几条在 Go 里的具体写法（`defer` 是函数级、`context.WithoutCancel`、
> `http.Client` 零值永不超时）见 [`.claude/rules/go.md`](./.claude/rules/go.md) §6。

### 1.7 依赖方向

- **接口由消费方定义。** `api.Executor`、`flow.Sandbox`、`api.Judger` 都声明在使用方，
  实现方完全不知道它们存在。好处：依赖单向、不成环，且测试能塞一个十几行的假替身。
- **接受接口，返回结构体。** `api.New(exec Executor, ...)` 收接口，`pool.New(...) *Pool` 返回具体类型。
- **一段逻辑该挂在数据类型上还是放在别处，看它依不依赖配置。** `JudgeLimits.Validate()`
  只看数据本身，挂类型上正好；算墙钟要读 `clockRatio`（配置），就不能挂在 `contract` 上，
  否则纯数据包要去 import `config`。

### 1.8 测试

- **测设计意图，而不只是测输出。** `testcase` 的 `Load` 之后删掉文件、断言 `Open` 必须失败——
  若哪天被改成加载时就读进内存，其它用例照样全绿，只有这条会响。
- **两个方向都要断言。** 并发上限的测试只断言「不超过 2」是不够的——写成完全串行也能过。
  加上「峰值 ≥ 2」才证明并发确实发生了。
- **不变量用测试钉住，而不是靠记性。** 例：响应里不得出现标准答案（泄题防线）、
  从磁盘读答案的模式不得回传答案。
- **表驱动 + `t.Run`**，用例是数据、断言只有一份。
- **假替身优先于真环境**：`httptest` 假装 sandbox、`fakeSandbox` 假装整个执行层。
  判题逻辑的单测**不该需要真起一个沙箱**。
- **并发代码必须 `-race`**。
- 边界用例要专门写：集成用例的限额通常离边界很远，off-by-one 撞不出来。

## 二、Java（`apps/server`）

五服务基础工程已经建立，业务实现继续遵循以下约定：

- Java 21 LTS + Spring Boot 4.1 + Maven 聚合工程；五个服务独立构建和部署，不与 Go 侧共享构建产物。
- 服务只写自己的 MySQL schema；禁止跨库 JOIN、共享 Mapper、共享业务实体和分布式 XA 事务。
- Gateway 使用 WebFlux；user/problem/submission/judging 使用 Spring MVC。MyBatis + Flyway 负责持久化。
- 浏览器公开 REST 契约以 `contracts/web-api.openapi.json` 为唯一真源。请求 body 使用 endpoint DTO，
  普通 JSON 成功响应统一为 `{ data, meta: { requestId, pagination? } }`，失败统一为 RFC 9457
  `application/problem+json`，并携带稳定 `code` 与相同的 `meta.requestId`。
- Gateway 为每次公开请求生成 `X-Request-Id`，且 header 必须与响应 body 一致；不能把客户端传入值、
  Session、Idempotency-Key 或内部 trace ID 当作 public request ID。`204`、二进制和流式响应是明确例外，
  不能为了形式统一强行包装。
- 正式提交使用 Kafka + Outbox/Inbox 异步推进；不能改回 HTTP 请求线程同步等待 judge。
- problem-service 拥有题目版本与 CORE 模板；judging-service 拥有环境、数据部署和语言标定；
  submission-service 拥有 Submission 与不可变 JudgeInput。
- CORE 模板在 submission-service 创建 JudgeInput 时合并；Go judge 收到的始终是完整源码。
- Kafka 不传源码、模板、测试数据、密码或 JWT；judging-service 通过内部 API 拉取 JudgeInput。
- 服务间 DTO 分别照着 `problem-judge-snapshot`、`execution-profile`、`judge-input`、`judge-events`
  schema 写；与 Go judge 之间的 DTO 照着 `judge.schema.json` 写。字段名、单位、可选性都以 schema
  为准，并用 schema 示例做契约对齐测试。
- Go judge 的 `environmentFingerprint` 来自部署配置并出现在所有结果中；不能从请求回显。
- 时间 ns、内存 bytes，字段名自带单位——不要在 Java 侧改成 `timeoutMs` 之类。
- 「结果入不入库」是 submission-service 的决定，不要试图让 judge 关心。
- 题目元信息真源在 problem-service；环境相关绝对限制真源在 judging-service；判题时解析并冻结到
  JudgeInput。**判题机磁盘上只有按 testDataVersionId 定位的测试数据文件本身**。

## 三、TypeScript（`apps/web`）

开发或评审任何 Web UI、组件、样式或主题前，必须先读 [`docs/design-system.md`](./docs/design-system.md)
及其 [`docs/design-system/README.md`](./docs/design-system/README.md)，理解设计意图、组件合同与评审规则；
不要在本文件复制 token 值。Web 的可执行设计系统真源位于 `apps/web/design-system/`，安装、开发、检查、
构建、Storybook 与 E2E 均只消费代码侧资产，不读取设计说明目录。删除 `docs/design-system/` 不得影响
Web；普通 CI 也不做两棵目录间的漂移比较、复制或符号链接。只有真正修改设计系统时，才在同一
WORK/TASK 中同时更新代码与设计说明，并分别验证两侧。

前端采用 **TanStack-first、按需引入**，不是无条件安装 TanStack 全家桶：

- 基础：React 19 + TypeScript strict + npm + Vite，独立构建为静态站。
- 路由：TanStack Router；分页、筛选、关键字等可分享状态放类型安全的 search params。
- 服务端状态：TanStack Query；请求、缓存、失效、Mutation 和判题结果轮询都归它。
- 业务组件：TanStack Table、TanStack Form + Zod；TanStack Virtual 只在长列表确有需要时引入。
- UI：Tailwind CSS + shadcn/ui + Lucide React；代码编辑用 Monaco Editor。
- 题面：react-markdown + remark-gfm + rehype-sanitize，默认不执行原始 HTML。
- HTTP：原生 `fetch` 的项目级薄封装，不引入 Axios；前端只调用 Gateway `/api`，不直接调内部服务、
  judge 或 sandbox。公共 client 校验 ApiSuccess / ApiProblem 和 request ID，将失败区分为
  `http | network | timeout | aborted | contract`，但不负责缓存、导航、toast 或自动重试。
- OpenAPI 只生成 `src/generated/api` 的 TypeScript 类型，禁止手改；实际网络响应仍由 Zod 在边界校验。
  响应 decoder 接受新增的未知可选字段，业务分支只能依赖已声明的必需字段、HTTP status 和稳定 code。
- 状态边界：URL 状态归 Router，服务端状态归 Query，表单归 Form，表格归 Table，
  局部交互用 `useState` / `useReducer`。初期不引入 Zustand、Redux Toolkit 或 TanStack Store。
- 测试：Vitest + React Testing Library + MSW；关键用户链路用 Playwright。
- 质量：ESLint Flat Config + typescript-eslint 管正确性，Prettier + Tailwind 插件管格式，
  TypeScript 管类型；三者不配置重复规则。稳定主版本优先，不把 alpha/beta/RC 依赖放进主链路。
- TypeScript：除 `strict` 外启用 `noUncheckedIndexedAccess`、`exactOptionalPropertyTypes`、
  `noFallthroughCasesInSwitch` 和 `verbatimModuleSyntax`；边界输入用 `unknown` + Zod，禁用无理由
  `any`、非空断言和 `enum`，纯类型使用 `import type`。
- 代码组织：文件/目录 `kebab-case`，组件/类型 `PascalCase`，变量/函数 `camelCase`；
  默认具名导出，组件不用 `React.FC`。依赖方向固定为 `app/routes → features → components/lib`。
- React：不拿 Effect 做派生状态或常规请求；请求归 Query，可恢复页面状态归 Router；
  loading / empty / error / unauthorized / success 都要有明确 UI。
- 样式与可访问性：条件 class 统一走 `cn()`，优先设计 token 和语义 HTML；交互必须支持键盘，
  verdict 不能只靠颜色表达。
- 前端已经统一提供 `format(:check)`、`lint(:fix)`、`typecheck`、`test(:run)`、
  `test:e2e`、`build`、`check` 脚本；继续保持 hook 只检查、不改写，CI 使用
  `npm ci` 后跑 check、build 和 E2E。
- 当前不使用 TanStack Start / Next.js / TanStack DB：已有独立 Spring Boot 后端，
  MVP 不需要 SSR/RSC 或客户端关系数据层。
- 展示 verdict 时注意：`OLE`、`RAN`、`SE` 都是合法状态，别只处理 AC/WA。

直接依赖、开发工具和验收入口见 [`apps/web/TOOLCHAIN.md`](./apps/web/TOOLCHAIN.md)；更细的
分阶段引入顺序见已纳入版本管理的 [`docs/frontend.md`](./docs/frontend.md)。`docs/` 保存跨工作项长期
有效的全局基线，本节只保留所有开发者开始工作前都必须看到的核心约定。

## 四、提交流程

### 4.0 先启用 git hooks（每个新克隆跑一次）

```bash
sh scripts/setup-hooks.sh
```

它做的事只有一行 `git config core.hooksPath .githooks`。**不能自动生效**——
`.git/hooks/` 不进版本库，而 git 也不会自动信任仓库里的可执行脚本
（否则 clone 一个陌生仓库就等于给了它任意代码执行权限）。

| hook | 检查 | 耗时 |
|---|---|---|
| `pre-commit` | Go 格式/vet、contracts JSON、全局/开发文档系统 | ~0.5s |
| `commit-msg` | 标题符合 Conventional Commits | 瞬时 |
| `pre-push` | `go test -race -count=1 ./...`（无 Go 改动时自动跳过） | ~6s |

两条设计取舍：

- **pre-commit 只检查、不改写。** 自动 `gofmt -w` 再 `git add` 回去看着省事，
  但 `git add -p` 只暂存一半时，它会把你没打算提交的另一半也带进去。
  「提交的内容 == 你亲手暂存的内容」不该被 hook 破坏，所以只报错并打印该跑的命令。
- **测试放 pre-push 不放 pre-commit。** 6 秒 × 一天十几次提交，人会开始用
  `--no-verify` 绕过，hook 就形同虚设。

临时跳过：`git commit --no-verify` / `git push --no-verify`。
**hook 不是执行边界，CI 才是**——它只是把反馈从 2 分钟提前到 1 秒。

### 4.1 五步

**① 本地自检。** 装了 hooks 的话这步基本自动完成，剩下要手动的只有动过依赖时：

```bash
cd apps/judge-engine && go mod tidy   # 之后确认 git diff 无输出
```

hook 和 CI 跑的是同一套命令，所以本地绿了推上去基本不会红。

**② 分 commit，一个 commit 一件事。** 跨越多个关注点就拆开
（例：一次改动拆成「契约」「配置模块」「sandbox 接线」三个）。

**每个 commit 都要能独立编译。** 拆的时候检查一下：后面 commit 才引入的符号，
前面的 commit 有没有引用到。

**③ 写 commit message**（格式见 §4.2）。

**④ `git push origin main`。**

**⑤ 看 CI 结果。** 红了先修再继续，**别在红的基础上叠新提交**——
第二个人来看时分不清是谁弄红的。

### 4.2 commit message

- Conventional Commits + 中文正文：`feat(scope): 摘要`、`fix(runner): …`、
  `refactor(sandbox): …`、`test(container): …`、`ci: …`、`docs: …`、`chore: …`。
- **正文写「为什么」，不写「改了什么」**——改了什么 diff 里有。
  重点记两件事：**这个问题不改会怎样**，以及**当时在两个方案间是怎么权衡的**。
  三个月后 blame 到这一行时，需要的正是这两样。

### 4.3 CI 会检查什么

`.github/workflows/ci.yml`，push 到 main 和所有 PR 都跑：

| job | 检查 | 为什么单独一条 |
|---|---|---|
| `development` | 工作项工具测试；元数据、流程、状态、引用、依赖与验证证据 | 开发输入和智能体执行边界不能漂移 |
| `contracts` | JSON 可解析 + `$ref` / v2 字段 / 事件安全边界 | 共享 DTO 坏了会同时影响多个服务与 Go；且一直是手改的 |
| `go` | gofmt / vet / build / `test -race` | 跑在 ubuntu-latest —— sandbox 的目标平台就是 Linux，不做 macOS 矩阵 |
| `tidy` | `go mod tidy` 后无改动 | 不同步会让别人 clone 下来跑不起来，而本地察觉不到（hook 没管这条） |

`go` job 末尾会打印 g++ / python3 / java 版本：`language` 的集成测试缺工具链时会
`t.Skip`，不打印的话某天镜像变了、测试静默跳过也没人发现。

### 4.4 文档与开发状态

`docs/` 是已经确认的全局事实，`development/` 是具体工作的过程。开发中产生的未知、体验、方案、计划、
任务和验证先留在对应 WORK；只有已经确认且会约束多个未来工作项的结论才整理进 `docs/`。

- 开始一项工作 → 先读 `development/README.md` 和 `development/WORKS.md`，查找或创建 WORK；
- WORK Type 决定主流程，风险、影响面和 concern 只追加阶段与门禁；TASK 继承所属 WORK，不自行选择
  产品、基建、修复或重构流程；
- 工作项目录只使用永久编号，例如 `development/works/WORK-001/`；标题统一从 `WORKS.md` 和
  `00-work.md` 阅读，不在目录名后添加 slug；
- 用户行为变化 → 先完成 FEATURE/PRODUCT，解决 blocking 待确认项；
- 技术路线变化 → 更新 DESIGN/DECISION 和影响面，不在 TASK 中偷偷改变；
- 出现可执行工作或技术债 → 建立关联 TASK，代码锚点使用 `TODO(TASK-001): ...`；
- 声明完成 → 记录实际 VERIFY；implemented 不等于 verified。MVP 阶段没有生产环境，流程里没有上线与
  线上观察阶段，`verified` 就是终态。

**契约先行**：改 `contracts/*.json` → 再改各语言类型 → 再改实现。反过来做必然漂移。

---

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
- **技术完成不等于产品确认。** TASK done 只表示实现完成；Agent 不能根据测试全绿自动代签人工产品
  判断或关键风险确认。
- **设计变更先写当前 WORK。** 经确认且长期跨工作有效时再同步 `docs/`；操作步骤变化同步 `tutorial/`。
  Web 设计系统的真实变更必须让同一 WORK/TASK 同时覆盖 `apps/web/design-system/` 与
  `docs/design-system/`，但普通 Web 命令不得用跨树 drift、copy 或 symlink 代替这项显式维护责任。
- 已知技术债在所属 `development/works/WORK-xxx/` 中建立 `60-task-TASK-xxx.md`，代码锚点使用
  `TODO(TASK-001): ...`。
- 教程里的代码是给人照着写的，保留 `// TODO(你来写)`，别直接把答案填满。
