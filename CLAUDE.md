# cherry-oj

学习型 Online Judge。**先跑通端到端 MVP，再逐层硬化**——每一步都要能解释「为什么这么写」，
所以本仓库里的注释和文档密度高于一般项目，改代码时请一并维护。

## 系统拓扑

```
浏览器 → apps/web (TS)
           │ REST
         apps/server (Java, SpringBoot)   业务：用户 / 题目 / 提交 / 鉴权 / 数据库
           │ POST /judge          跨语言 HTTP，契约以 contracts/*.json 为准
         judge  (Go)                      判题编排：编译 → 逐测试点 → 比对 → 汇总 verdict
           │ POST /run, /blobs    同为 Go，共享 internal/contract 的类型
         sandbox (Go)                     安全执行：跑一条命令，报资源用量和输出
```

**最重要的一条边界**：sandbox 只回答「安全地跑一段程序，给我资源用量和输出」，
**完全不懂判题**。编译、比对、verdict 全在 judge。这条守不住，整个分层就没意义了。

## 仓库结构

顶层每个目录 = 一套构建工具 / 一种技术栈，互不侵入；跨语言唯一的耦合点是 `contracts/`。

```
cherry-oj/
├── contracts/          ★ 跨语言契约（JSON Schema），唯一真源
├── apps/
│   ├── judge-engine/   Go 单模块，产出 judge + sandbox 两个二进制
│   │   ├── cmd/{judge,sandbox}/
│   │   ├── internal/{config,contract,judge,sandbox}/
│   │   └── config.example.yaml
│   ├── server/         Java / SpringBoot —— 尚未开工
│   └── web/            TypeScript —— 尚未开工
├── docs/               设计文档（architecture / engine / data-model / backlog）
└── tutorial/           分阶段动手教程
```

> ⚠️ `docs/`、`tutorial/`、`notes/`、`test/`、`draft/`、`dev-dependency/` 都在 `.gitignore` 里，
> **不进版本库**。新克隆的仓库看不到它们；本地有就读，没有别去找。

## 当前进度

| 部分 | 状态 |
|---|---|
| `contracts/` | ✅ run / judge / verdict / submission |
| sandbox（store, container, runner, pool, api, cmd） | ✅ 可独立 `curl` |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client` | ✅ |
| judge：`flow`、`api`、`cmd/judge` | 未开始 |
| `apps/server`、`apps/web` | 未开始 |

---

# 编码规范

本文只写**跨语言通用**的约定。**各语言的具体规范另放一处**：

| 语言 | 规范 |
|---|---|
| Go（`apps/judge-engine`） | [`.claude/rules/go.md`](./.claude/rules/go.md) |
| Java（`apps/server`） | 见下方 §二（尚未开工，先立约定） |
| TypeScript（`apps/web`） | 见下方 §三（尚未开工） |

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
| 例子 | 源码、语言、题目 id、时空限制 | 测试数据根目录、比对方式、是否回传标准答案、各类大小上限、监听地址 |
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
- **外部字符串拼进路径前先用正则关死。** `problemId`、`ref` 都来自 HTTP 请求，
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

## 二、Java（`apps/server`，尚未开工）

先立约定，开工时补充：

- SpringBoot + Maven，独立构建，不与 Go 侧共享构建产物。
- 与 judge 之间的 DTO **照着 `contracts/judge.schema.json` 写**，字段名、单位、
  `omitempty` 语义都以 schema 为准；同样要有契约对齐测试。
- 时间 ns、内存 bytes，字段名自带单位——不要在 Java 侧改成 `timeoutMs` 之类。
- 「结果入不入库」是 server 的决定，不要试图让 judge 关心。
- 题目元信息（时空限制、题面样例、比对方式）的真源在 server 的数据库里，
  判题时随请求下发；**判题机磁盘上只有测试数据文件本身**。

## 三、TypeScript（`apps/web`，尚未开工）

- npm / vite，独立构建。
- 面向 server 的 REST，不直接调 judge / sandbox。
- 展示 verdict 时注意：`OLE`、`RAN`、`SE` 都是合法状态，别只处理 AC/WA。

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
| `pre-commit` | 暂存的 Go 文件 gofmt 格式 + `go vet` + `contracts/*.json` 语法 | ~0.4s |
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
| `contracts` | `contracts/*.json` 可解析 | 跨语言唯一耦合点，坏了同时影响 Go 和 Java 侧；且一直是手改的 |
| `go` | gofmt / vet / build / `test -race` | 跑在 ubuntu-latest —— sandbox 的目标平台就是 Linux，不做 macOS 矩阵 |
| `tidy` | `go mod tidy` 后无改动 | 不同步会让别人 clone 下来跑不起来，而本地察觉不到（hook 没管这条） |

`go` job 末尾会打印 g++ / python3 / java 版本：`language` 的集成测试缺工具链时会
`t.Skip`，不打印的话某天镜像变了、测试静默跳过也没人发现。

### 4.4 CI 检查不到的，只能靠人

**`docs/`、`tutorial/`、`notes/` 在 `.gitignore` 里，不进版本库。**
改了不会出现在 `git status`，CI 也不会碰。这是目前流程里最容易漏的一环：

- 设计变了 → 同步 `docs/`（architecture / engine / data-model）
- 操作步骤变了 → 同步 `tutorial/`
- 欠下技术债 → 记进 `docs/backlog.md`，做完就删条目

**契约先行**：改 `contracts/*.json` → 再改各语言类型 → 再改实现。反过来做必然漂移。

---

## 给 AI 助手的额外说明

- **改动前先问清楚。** 本项目的很多设计（命名、契约字段、职责边界）是反复讨论定下来的，
  不要顺手「优化」。拿不准就先问，别先改。
- **设计变更写 `docs/`，操作步骤写 `tutorial/`**，两边都要跟着代码更新。
- 已知技术债记在 `docs/backlog.md`，格式是「位置 · 现状 · 目标 · 何时该做」，做完就删条目。
- 教程里的代码是给人照着写的，保留 `// TODO(你来写)`，别直接把答案填满。
