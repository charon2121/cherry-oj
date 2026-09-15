# Engine 设计文档（详解版）

> 面向：**刚会 Go 基本语法、准备手写判题引擎** 的读者。
> 系统级拓扑见 [architecture.md](./architecture.md)。
> **全链路数据结构**见 [data-model.md](./data-model.md)（字段真源在 `contracts/`）。
> **动手步骤（分阶段教程）**位于本地 `tutorial/`；该目录不进入 Git，新克隆不保证存在。
>
> 本文是**设计文档**：讲清楚「是什么 / 为什么 / 边界在哪」。不写安装命令与逐步验收清单。
> 学习约束：对照 `dev-dependency/go-judge`、`go-sandbox` **学思路**，自己写实现；**命名按场景自拟，不照搬**。

---

## 0. 先建立直觉：OJ 判题在干什么？

用户在网页上交一份 C++ 代码，OJ 要回答一句话：**对不对、超时了没、爆内存了没**。

拆开看，是三件性质完全不同的事：

1. **根据结果判分**
   把标准输出和标准答案比对；把「超时」映射成 TLE；编译失败映射成 CE……
   → 这叫 **judge（判题编排）**。它像阅卷老师：看实验记录，给出分数和评语。

2. **安排谁来跑、跑几条、排多少队**
   收编译和测试点两类命令，控制并发、管临时文件、把结果整理成统一形状。
   → 这叫 **sandbox（执行服务）**。它像实验室的调度台：不碰试剂，只管排班和器材。

3. **真的建出一个受限环境并把命令关进去**
   namespace、chroot、cgroup、seccomp——这些动作**需要 root**。
   → 这叫 **helperd（特权执行助手）**。它像只有主管才有钥匙的那间通风柜。

**为什么必须拆成三个而不是两个？**

第 2 件事和第 3 件事看起来是一件事，但它们**需要的权限完全不同**：排队、限流、管临时文件不需要任何
特权；建 namespace 和写 cgroup 需要 root。把它们放进同一个进程，就等于让那个进程整体以 root 运行——
于是排队逻辑里的一个越界写，后果是 root 权限下的越界写。拆开之后，特权进程小到可以逐行读完，
非特权进程再大也只是个普通用户。

一句话记住边界：

> **helperd 只说「进程怎么退出的、用了多少资源」；sandbox 只说「这条命令执行完了」；
> judge 才说「算不算对」。**

---

## 1. 三个进程，两条硬边界

### 1.1 三个进程各自回答什么

| 进程 | 身份 | 回答的问题 | 默认端口/地址 |
|---|---|---|---|
| **judge** | 普通用户 | 这份提交是 AC 还是 WA？ | `127.0.0.1:5051`（HTTP） |
| **sandbox** | 普通用户，**非 root** | 这条命令跑完了吗？产物在哪？ | `127.0.0.1:5050`（HTTP） |
| **helperd** | **root** | 进程退出码是多少？整组用了多少 CPU/内存？ | Unix socket（无网络端口） |

产品从上到下是：

```
浏览器 → server(Java) → judge(Go) → sandbox(Go) → helperd(Go, root)
```

`server` 侧的职责见 [architecture.md](./architecture.md)；本文只讲后三个。

### 1.2 信任边界：root 与非 root 之间

helperd 是三个进程里唯一持有特权的。它和 sandbox 之间的那条线是**信任边界**：

- helperd **不信任** sandbox 发来的任何东西。请求里不接受宿主路径、不接受 uid/gid、不接受
  rlimit 越界值；所有路径都是工作区内的逻辑路径，由 helperd 自己解析。
- 谁能连是**双重约束**：socket 文件权限只放行服务专用组，建立连接后再用 `SO_PEERCRED`
  核对对端的 UID。只靠文件权限等于假设「目录权限从来没被改过」。
- 两端唯一的共享词汇是 `internal/hostexec` 里的协议定义。**helperd 的服务端实现和 sandbox 的
  客户端实现互相看不见对方**，只对同一份协议编程。

### 1.3 部署边界：三个独立交付的二进制

三个程序可以装在不同机器上，也可以只装其中一个：

- 生产的 Linux 原生部署把 sandbox 和 helperd 装在同一台机器（它们之间是本机 socket，
  不能跨主机），judge 可以在别处。
- 开发机上可以只跑 judge + sandbox（`devhost` 后端），完全没有 helperd。

所以**它们之间只能通过协议说话，不能互相 import 实现**。这不是风格偏好：一旦 judge 里出现
`sandbox/internal/...` 的 import，"judge 可以单独部署"这句话就不再成立了，而且没有任何东西会
提醒你——直到某天有人想把 judge 挪走。

### 1.4 边界由编译器把守，不是由约定

Go 有一条规则：**`internal/` 目录下的包，只有它的父目录子树能 import。**

这条规则把上面两条边界变成**编译期错误**：

```
judge/internal/...     ← 只有 judge/ 子树能 import
sandbox/internal/...   ← 只有 sandbox/ 子树能 import
helperd/internal/...   ← 只有 helperd/ 子树能 import
internal/...           ← 整个 module 都能 import（顶层共享）
```

于是：

| 想写的 import | 结果 |
|---|---|
| sandbox 里 import `helperd/internal/helper` | **编译失败**。非特权进程够不到特权实现 |
| judge 里 import `sandbox/internal/pool` | **编译失败**。judge 只能通过 HTTP 用 sandbox |
| helperd 里 import `judge/internal/flow` | **编译失败**。特权进程不理解判题 |
| `cmd/judge` 里 import `judge/internal/api` | **编译失败**。入口只能调 `judge.Run` |

最后一条容易被忽略但同样重要：`cmd/` 下的三个 `main` 只做**解析 flag、读配置、建 logger、
接信号**，然后调用 `judge.Run` / `sandbox.Run` / `helperd.Run`。一旦 `main` 能直接摸到内部包，
「服务的装配顺序」就会慢慢渗进入口文件，而入口文件是最没人读的地方。

> **这一条是整份设计的地基**：边界写在注释里靠自觉，写成目录结构靠编译器。

### 1.5 为什么按服务切，而不是按「代码种类」切

早先的目录是按「这是什么种类的代码」切的——`internal/judge/`、`internal/sandbox/`、
`internal/sandbox/container/`、`internal/config/`……问题不在于名字难听，而在于**它切错了轴**：

- 一个 `internal/config` 同时装着 judge 的配置、sandbox 的配置和 helper 的配置，于是
  「加一个 judge 的配置项」会让 sandbox 的包重新编译，也会让**每个节点的环境指纹发生轮换**
  （指纹曾经把整份配置摘要算进去）。
- `container` 这个名字暗示「有个容器对象，可以复用」，于是接口长成了「放输入 → 启动 → 等待 →
  取产物 → 关闭」四个阶段，时序只能写在运行时错误里（「Container 只能执行一次」
  「工作区不再接受输入」），两个实现各自拿一组状态字段守护它。
- 最要命的是：**没有任何机制阻止 judge 直接 import sandbox 的内部实现**，因为它们是同一个
  `internal/` 下的兄弟目录。

按服务切之后，这三类问题一起消失：配置跟着服务走，`container` 变成一次性的
`backend.Execute`（见 §6.3），跨服务 import 变成编译错误。

---

## 2. 目录树

```
apps/judge-engine/
├── go.mod                      # module cherry-oj/judge-engine
├── cmd/
│   ├── judge/main.go           # 只做 flag + 配置 + logger + 信号，然后 judge.Run
│   ├── sandbox/main.go         #   同上 → sandbox.Run
│   └── sandbox-helper/main.go  #   同上 → helperd.Dispatch / Run
│
├── internal/                   # ★ 整个 module 共享：只放「协议与平台设施」
│   ├── contract/               #   judge ↔ sandbox 的 HTTP DTO、Limits、Verdict/Status
│   ├── hostexec/               #   sandbox ↔ helperd 的本机执行协议（帧、请求、结果）
│   │   └── client/             #     该协议的客户端实现
│   └── platform/
│       ├── config/             #   泛型配置加载：默认值 → YAML → 环境变量
│       ├── logging/            #   slog 装配
│       └── tracing/            #   trace 传播
│
├── judge/                      # ★ 服务一：判题编排
│   ├── doc.go                  #   本子树的职责与引用边界
│   ├── judge.go                #   Run(ctx, Config, *slog.Logger) error
│   ├── config.go
│   └── internal/
│       ├── api/                #   POST /judge、GET /version
│       ├── flow/               #   一次判题：编译 → 逐点跑 → 比对 → 汇总
│       ├── checker/            #   单遍流式比对选手输出与标准答案
│       ├── language/           #   某语言怎么编译、产物叫什么、怎么运行
│       ├── testcase/           #   从磁盘读某个 testDataVersionId 的测试点
│       ├── sandboxclient/      #   给 sandbox 打电话的 HTTP 客户端
│       ├── config/             #   judge 自己的配置与校验
│       └── node/               #   节点身份与数据交付（见 §5.5）
│           ├── identity/       #     环境指纹怎么算
│           ├── registry/       #     向 judging-service 注册与心跳
│           ├── install/        #     接收测试数据并原子落盘
│           ├── probe/          #     探测执行环境、校验原生部署
│           └── wire/           #     严格 JSON 解码
│
├── sandbox/                    # ★ 服务二：执行服务（非 root）
│   ├── doc.go
│   ├── sandbox.go              #   Run(...)
│   ├── config.go
│   ├── budget.go               #   跨层期限断言（见 §8）
│   └── internal/
│       ├── api/                #   POST /run、/blobs、GET /version
│       ├── pool/               #   并发上限、排队、回收未确认时停止接单
│       ├── runner/             #   一次执行的完整生命周期与限额归一化
│       ├── backend/            #   ★ 可替换边界：linux / devhost
│       ├── workspace/          #   每次执行独占的临时工作区
│       └── store/              #   ref ↔ 磁盘文件
│
└── helperd/                    # ★ 服务三：特权执行助手（root）
    ├── doc.go
    ├── helperd.go              #   Dispatch / LoadConfig / Run
    ├── config.go
    ├── internal/
    │   ├── helper/             #   会话、槽位、监督、结论、回收
    │   ├── launcher/           #   建隔离环境、把命令 exec 起来（隔离轴）
    │   ├── cgroup/             #   限额与计量（限量轴）
    │   └── policy/             #   seccomp 策略
    └── tests/boundary/         #   需要真实内核的边界测试
```

### 2.1 顶层 `internal/` 放什么、不放什么

顶层 `internal/` 是唯一能被三棵子树同时引用的地方，所以它的准入条件要比别处严：

**可以放**：两个服务之间的**协议定义**（`contract`、`hostexec`），以及和业务无关的**平台设施**
（配置加载、日志、trace）。

**不可以放**：任何一个服务的业务逻辑。一旦某个服务的实现搬进顶层 `internal/`，它就同时对另外两个
服务可见——§1.4 建立的编译期边界，就是从这里被绕过去的。

判断方法很简单：**问「另外两个服务读它是合理的吗」**。`hostexec` 定义 sandbox 和 helperd 的共同
词汇，judge 读它没意义但也无害；而 `flow` 如果搬上去，helperd 就能 import 判题逻辑了。

---

## 3. 两个核心名词：Status 和 Verdict

初学者最容易混的一点。

### 3.1 Sandbox 的 Status = 「执行事实」

程序跑完后，沙箱只描述客观事实，例如：

| Status | 含义 |
|---|---|
| `OK` | 进程正常退出，exit code = 0（**注意：不是 OJ 的 AC！**） |
| `TimeLimitExceeded` | CPU 或墙钟时间超了 |
| `MemoryLimitExceeded` | 内存超了 |
| `OutputLimitExceeded` | 输出写太长 |
| `NonzeroExitStatus` | 退出码非 0 |
| `Signalled` | 被信号杀掉（SIGSEGV、超时 SIGKILL 等） |
| `WorkspaceError` | inputs / outputs / artifacts 文件出错 |
| `InternalError` | 沙箱自己挂了 |

**`OK` 的意思是「跑完了且 exit 0」，不是「答案对」。**
答案对不对，sandbox **根本不知道**，因为它没看标准答案。

### 3.2 Judge 的 Verdict = 「OJ 判分」

| Verdict | 含义 |
|---|---|
| `AC` | 通过 |
| `WA` | 答案错 |
| `PE` | 格式不对（空白之类，看题目策略） |
| `TLE` / `MLE` / `RE` | 超时 / 超内存 / 运行时错误 |
| `CE` | 编译错误 |
| `SE` | 判题系统故障（不是用户代码的问题） |
| `RAN` | 已运行但未比对（自定义测试无 expected） |

### 3.3 怎么映射？（举例）

```
sandbox 返回 OK + stdout="3\n"，标准答案是 "3\n"
  → judge 的 checker 说相等 → Verdict = AC

sandbox 返回 OK + stdout="4\n"，标准答案是 "3\n"
  → checker 不相等 → WA

sandbox 返回 TimeLimitExceeded
  → 直接 TLE（不用看输出）

编译时 sandbox 返回 NonzeroExitStatus，stderr 里是 g++ 报错
  → CE
```

**设计禁令**：不要把 `WA` 放进 sandbox 的 Status 里，也不要把 Status 放进 helperd。
helperd 连 Status 都不判——它只报退出码、信号、cgroup 读数和主动终止原因，
「这算不算超时」是 `runner` 的策略。

---

## 4. 契约：三层各自约定「信封长什么样」

| 边界 | 定义在哪 | 谁说了算 |
|---|---|---|
| 浏览器 ↔ server | `contracts/submission.json` | server 持久化模型 |
| server(Java) ↔ judge(Go) | `contracts/judge.schema.json` | **这份 schema 是唯一真源** |
| judging-service ↔ judge 节点 | `contracts/judge-node.schema.json` | schema 是真源 |
| judge ↔ sandbox | `contracts/run.schema.json` | 实现以 `internal/contract` 为准，schema 当文档 |
| sandbox ↔ helperd | `internal/hostexec` | **只有 Go 定义，没有 schema** |
| 全链路 verdict | `contracts/verdict.json` | 已有 |

最后两行的差别值得说明：`hostexec` 是**本机的、同一份代码同批部署的两端**之间的协议，没有跨语言
消费者，因此把 Go 类型当真源比再维护一份 schema 更可靠——字段名即线格式，改名等于改协议，
这一点直接写在类型的注释里，并由 `wire_test.go` 的黄金用例把字节输出钉住。

约定：JSON **camelCase**；时间 **ns**；内存 **bytes**。改跨语言接口时：**先改 `contracts/`**。

---

## 5. judge 详解

### 5.1 它对外长什么样？

| 方法 & 路径 | 说明 |
|---|---|
| `POST /judge` | 判一次提交，同步返回 `JudgeResult` |
| `GET /version` | 自报名字与版本 |
| `POST /internal/judge-node/v1/install` | 控制面下发测试数据（仅在节点模式启用时挂载） |

```json
// 请求 POST /judge
{
  "submissionId": "s1",
  "problemId": "p-a-plus-b",
  "problemVersionId": "pv-a-plus-b-v1",
  "testDataVersionId": "tdv-a-plus-b-v1",
  "languageId": "cpp",
  "source": "#include <iostream>\n...",
  "limits": { "cpuNs": 1000000000, "memoryBytes": 268435456 }
}

// 响应
{
  "verdict": "AC",
  "environmentFingerprint": "sha256:judge-env-amd64-v1",
  "cpuNs": 1447000,
  "memoryBytes": 1048576,
  "score": 100,
  "caseResults": [
    { "idx": 1, "verdict": "AC", "cpuNs": 1200000, "memoryBytes": 1000000 },
    { "idx": 2, "verdict": "AC", "cpuNs": 1447000, "memoryBytes": 1048576 }
  ]
}
```

### 5.2 测试数据在磁盘上长什么样？

```
<testdata-root>/tdv-a-plus-b-v1/
├── 1.in
├── 1.out
├── 2.in
└── 2.out
```

**只放测试数据，不放元信息。** 目录名是不可变 `testDataVersionId`；`problemId` 和
`problemVersionId` 只用于日志、追踪与对账，不能用于定位正式测例。这样同一道题的新旧版本可以并存，
历史提交不会因为题目当前版本变化而读到另一份数据。

文件名规约**宽松匹配**：任何 `X.in`，只要同目录下有 `X.out`，就配成一对，`X` 即测试点名。排序时 `X` 能转整数的按数值排（否则 `1, 10, 2` 会乱），转不成的按字符串排在后面。落单的 `.in` 跳过并记 warning。

时空限制由 judging-service 根据题目版本、语言和环境标定解析成绝对值，冻结进 JudgeInput 后随
`JudgeRequest` 下发。测试数据部署用 content hash 校验，Go judge 再从自身配置返回实际
`environmentFingerprint`，judging-service 必须与 JudgeInput 对比后才能接受结果。

### 5.3 语言配置

```go
SourceName:       "Main.cpp"   // inputs 时文件叫这个名
Compile:          g++ Main.cpp -o Main -O2 -std=c++17
CompiledArtifact: "Main"       // 编译成功后作为 artifacts 存起来
Run:              ["Main"]     // 工作区里的可执行文件，不带 "./"
```

Python 没有 Compile，直接 `python3 Main.py`；解释器/编译器一律走 `PATH`，不写死绝对路径。

**注意 `language` 包里有 python，但节点对外只声明 `cpp`。** 这不是遗漏：整条平台链路当前是
cpp-only——Java 侧的控制器用 `@Pattern(regexp="cpp")` 卡住语言，环境开通只开通一种语言，
节点匹配要求语言集合**完全相等**。节点多声明一种语言，结果不是「多支持了 Python」，而是
**没有任何环境能匹配上这个节点**。要放开语言，得从 Java 侧的约束开始改，不是从这里。

### 5.4 judge ↔ sandbox 交互流程

先记住三句话：

1. **测试点只存在于 judge**：从磁盘读 `*.in` / `*.out`；sandbox 从不打开题目目录。
2. **一点一 `/run`**：每个测试点单独一次运行请求；编译也是单独一次 `/run`。
3. **标准答案不进 sandbox**：只把「本题输入」当 stdin 喂进去；比对在 judge 本地做。

#### 谁持有什么

| 数据 | 谁持有 | 怎么到对方 |
|---|---|---|
| 源码字符串 | judge（来自 JudgeRequest） | `POST /blobs` → `srcRef` |
| 题目限制、`1.in`/`1.out` | judge（`testcase` 包读盘） | 输入进 `/run` 的 `stdin`；输出留在 judge 做 checker |
| 可执行文件 | sandbox store | 编译 `artifacts` → `exeRef`；跑点时 `inputs` |
| stdout | sandbox 返回字段 `stdout` | judge 拿去和 `expected` 比 |

#### 调用次数（C++，N 个测试点）

```text
1 × POST /blobs         上传源码
1 × POST /run           编译
N × POST /run           每个测试点各一次
（可选）DELETE /blobs   清理 ref
```

Python 等解释型：通常 **没有** 编译那一次 `/run`，上传后直接 N 次运行。

#### checker 默认策略

逐行去掉行尾空格，再忽略末尾空行，然后比字符串。实现是**单遍、逐字节、流式**的：两个流并排推进，
内存恒定，不受文件大小限制。

#### `flow` 的接口由消费方定义

```go
// judge/internal/flow
type Sandbox interface {
    Upload(context.Context, io.Reader) (string, error)
    Run(context.Context, contract.RunSpec) (contract.RunResult, error)
    Delete(context.Context, string) error
}
```

这个接口声明在 `flow` 里，而不是在 `sandboxclient` 里。差别在于：**实现不依赖 flow**，
于是判题流程的测试不需要启动任何沙箱，给一个假的三方法实现就够了。同样的写法也用在
`node/probe` 的 `Sandbox` 接口上——探测消费的是 judge 已有的 HTTP 客户端能力，
不再自建第三个 HTTP 客户端。

### 5.5 节点身份与环境指纹

judge 作为判题节点时，要先回答控制面一个问题：**「你是什么样的执行环境？」**

`node/probe` 通过 sandbox 已有的有界接口探测：架构、CPU 型号与特性、OS、内核、工具链版本、
sandbox 版本，以及运行时摘要（cgroup 配额与 sandbox 二进制摘要）。`node/identity` 把这些
**加上 judge 自己的判题策略**（严格空白、时钟倍率、输出截断上界、编译限额……）算成一个指纹。

为什么策略也要计入：同一台机器上，把「严格空白」从关改成开，同一份提交的结论会变。指纹代表
「这个环境下的判题结论可复现」，策略变了就不再是同一个环境。

关键是**指纹的输入是一份显式的结构体**，字段一条条列出来。它曾经是「把整份配置序列化后取摘要」，
那样写的后果是：**加一个与判题无关的配置项，比如日志路径，也会让全网节点的指纹轮换**，
历史标定全部失效。显式列举让「什么会影响结论」变成一个可以读、可以审的清单。

原生部署还要多一道：`node/probe/deployment_spec.go` 用命名常量写清「一个合格的原生部署长什么样」
——哪些文件、哪些 cgroup 组、哪些限额。校验是**双向覆盖**的：清单里声明的每一条都必须被核对，
被核对的每一条都必须在清单里。只比数量的话，「少一条」和「多一条从不核对的」会互相抵消。

---

## 6. sandbox 详解

### 6.1 它对外长什么样？

**端点划分原则**：动作用 RPC 动词，资源用 REST 名词——别把不可检索的动作硬凑成资源。

| 方法 & 路径 | 类型 | 说明 |
|---|---|---|
| `POST /run` | 动作 | 执行一条命令，返回 `RunResult`（同步、无 `GET /runs/{id}`，所以是动词） |
| `POST /blobs` | 资源 | 上传一袋字节，返回 `ref` |
| `GET /blobs/{ref}` | 资源 | 取回 |
| `DELETE /blobs/{ref}` | 资源 | 删除 |
| `GET /version` | 资源 | 自报名字、版本与**当前隔离后端** |

`GET /version` 里的隔离后端字段不是装饰：judge 以节点模式启动时，先靠它确认「我连上的这台
真的是隔离部署」，探测不过就在 `net.Listen` **之前**返回错误——端口都不会被占上，
自然也不会有一个上线的节点（见 §6.4）。

### 6.2 一次 `/run` 在内部怎么走？

```
1. api      收到 JSON，转成内部 RunSpec；拒绝尾随内容、拒绝空命令
2. pool     准入 + 排队（同时最多 N 个）；池被 poison 过就直接拒收
3. runner   归一化限额：套用默认值，再按服务硬界收敛；越界直接拒绝
4. runner   向 store 打开 ref 对应的真实文件，组装成一个 backend.Job
5. backend  Execute(ctx, job, sink) —— 一次调用覆盖铺输入、跑命令、交付产物、回收资源
6. sink     在「资源回收已完成」之后被逐个调用；runner 决定内联还是入 store
7. runner   把 Facts 翻译成 status；Execute 若返回 CleanupError，pool 停止接单
8. api      把 Result 写成 JSON 返回
```

几个容易看反的地方：

- **归一化在排队之后**，发生在 `runner` 里而不是 `api` 里。`pool` 只管名额，不看限额内容。
- **stdout 写超了会取消 `runCtx`**，好让被执行的程序尽快停下；但**判定用的是原始 `ctx`**——
  否则自己发出的这次取消会把一次 OLE 报成平台错误。
- 第 5、6 步的先后是刻意的，见 §6.3 和 §6.6。

### 6.3 `Backend`：一次性执行接口

```go
// sandbox/internal/backend
type Job struct {
    Command []string
    Env     []string
    Stdin   *Source
    Inputs  []NamedSource
    Outputs []string          // 声明要取回哪些文件，限制后端可交付的范围
    Limits  contract.Limits
    Stdout, Stderr io.Writer  // 调用方给带上限的 writer，后端不自定截断策略
}

type OutputSink func(facts Facts, name string, r io.Reader) error

type Backend interface {
    Execute(ctx context.Context, j Job, sink OutputSink) (Facts, error)
}
```

**一次 `Execute` 就是一次完整执行**：铺输入、跑命令、交付产物、回收资源。**返回即代表回收完成**，
调用方据此决定是否对外发布结果，不需要再调用什么关闭方法。

这个形状是从教训里长出来的。此前的接口是四个阶段——「放输入 → 启动一次 → 等待 → 取产物 → 关闭」
——而这个时序**只写在运行时错误里**（「只能执行一次」「工作区不再接受输入」），两个实现各自维护
一组状态字段来守护它。改成一次性调用之后，「只能执行一次」由「**不存在可复用对象**」保证，
那些状态字段随之消失。

> 顺带一提：正因为没有可复用对象，也就没有「容器池化复用」「`Reset()` 清工作目录」这类东西。
> 每次执行独占自己的工作区和资源组，用完就拆。复用一个已经跑过用户代码的环境，
> 省下的那点建环境时间，要用「上一次到底留下了什么」的不确定性去换。

`Facts` 只有客观事实——退出码、信号、CPU、内存、墙钟、主动终止原因、是否本任务 OOM、
是否整组计量——**没有 verdict，也没有 status**。

`sink` 拿到 `facts` 才交付产物，是为了让调用方**在交付当场**就能判断这次结果值不值得保留：
一次超时或非零退出的执行照样可能留下产物，把它们写进 store 再删掉既多一次落盘，
也可能在容量紧张时把一次超时变成平台错误。

### 6.4 两个后端：`linux` 与 `devhost`

| 后端 | 隔离 | 用在哪 |
|---|---|---|
| `linux` | 全套：namespace、只读 rootfs、cgroup、seccomp，**由 helperd 执行** | 生产 |
| `devhost` | **零隔离**：临时工作目录里一个普通子进程 | 只在开发机 |

`devhost` 存在的理由只有一个：namespace/cgroup 是 Linux-only，但你要在 mac 上开发
`runner`/`pool`/`judge` 并跑通「编译 → 测点 → 比对 → AC」。

⚠️ **它不安全**：跑的程序能读宿主任意文件、能联网、能 fork 炸弹。所以它**默认拒绝启动**——
必须显式设置 `sandbox.allowUnsafeBackend` 才会起来，错误消息直接写明「这个后端不提供任何隔离」。
名字也从早先的 `trusted-host` 改成了 `devhost`：`trusted` 读起来像一种安全属性，
而它恰恰是**没有**安全属性的那个。

反向也有一道闸：配成 `linux` 后端时，sandbox 会在**开 HTTP 端口之前**做一次启动冒烟，
探测失败就退出。这条闸曾经因为一个错误处理 bug（返回了外层那个为 nil 的 `err`）
而形同虚设——探测失败也照样开端口。现在它返回的是真实错误，并且局部变量不再遮蔽外层的名字。

### 6.5 `store` 与 `workspace`

两个都管文件，但管的是不同的东西：

- **`store`**：跨执行的「文件字典」，`ref` ↔ 磁盘上的一个文件，带容量与保留期。源码上传进来、
  编译产物存进去、下一次执行再取出来，走的都是它。目录必须是服务私有的 0700，加独占锁，
  拒绝任何非独占的普通文件。
- **`workspace`**：**单次执行**独占的临时工作区，执行结束就回收。暂存根同样是服务所有的 0700 目录，
  启动时如果发现里面有未知条目或未回收的工作区，直接拒绝启动——不去猜那是什么。

### 6.6 「回收未确认」为什么要停掉整个池

`backend` 定义了一个专门的错误类型：

```go
type CleanupError struct{ Err error }
func (e *CleanupError) Error() string { return "reclaim unconfirmed: " + e.Err.Error() }
```

它和普通执行失败**是两回事**：

- 普通失败：这次执行失败了，下一条命令照跑。
- 回收未确认：**残留的进程或挂载可能与后续执行重叠**，而我们无法判定残留了什么。

容量能不能归还给下一条命令，取决于上一条命令的资源是不是真的没了。判定不了，就不能归还。
所以 `pool` 遇到 `CleanupError` 会 poison 整个池、停止接单，并把原因原样带出来——
让它只表现为「一次失败的执行」，等于用一次静默的资源泄漏换一条好看的日志。

### 6.7 为什么没有 `/compile`？

从 sandbox 的视角，**编译就是「跑一条命令」**：

```
编译： command=["g++","Main.cpp","-o","Main"]   inputs={Main.cpp}  outputs=[Main]
跑点： command=["Main"]                          inputs={Main}      stdin=input
```

`g++` 只是一个「读文件、写文件、受时间/内存限制」的程序，和用户的 `Main` 没有本质区别。
若拆出 `/compile`，sandbox 就被迫懂「哪个编译器、什么 flag、产物叫什么、失败要判 CE」——
这些是 **judge 域的知识**，会把 `language` 那套往下漏进沙箱，破坏「sandbox 不懂判题」的边界。

**编译 vs 运行的区别属于 judge，用不同的 `RunSpec` 表达**（编译 limits 更宽、失败短路成 CE、
产物 `outputs` 出 ref 给跑点 `inputs`）。见 §5.4。go-judge、IOI `isolate` 也都是这么做的：
沙箱只有一个「跑命令」的动词。

---

## 7. helperd 详解

### 7.1 它是唯一持有特权的进程

helperd 以 root 运行，监听一个 Unix socket，**没有网络端口**。它做的事只有一件：
按协议收一个请求，建出隔离环境，跑一条命令，把进程与资源事实还回去。

它启动时对自己也很苛刻：必须由 root 托管、必须是不带 setuid/setgid 的普通可执行文件、
必须用 `CGO_ENABLED=0` 构建、rootfs manifest 摘要必须被钉住；服务、init、payload
三个身份必须分离且都不是 root 身份复用。启动最后会做一次**隔离启动能力冒烟**——
真的建一次隔离环境跑一条命令——冒烟不过就不开 socket。

崩溃重启后还有一道恢复检查：state 目录里出现未知条目、jobs 里出现未知组、
所有权标记和 jobs 对不上，一律拒绝启动。**不去清理自己不认识的东西。**

### 7.2 `hostexec`：本机执行协议

一次调用的线格式：

```
客户端 →  [4 字节大端长度][Request JSON]
          [Inputs[0] 的 SizeBytes 字节][Inputs[1] ...]     ← 文件不进 JSON
          [StdinBytes 字节 stdin]
服务端 →  [4 字节长度][Result JSON]                        ← 事实 + stdout/stderr
          [Outputs[0] 的 SizeBytes 字节][Outputs[1] ...]   ← 产物字节流
          [4 字节长度][Completion JSON]                    ← 「回收与交付都完成了」
          正常 EOF                                        ← 「连接槽位已归还」
```

几个值得解释的决定：

- **文件走连续字节流，不放进 JSON。** base64 进 JSON 会强制两端全量内存缓冲；
  用 `SizeBytes` 划分流之后，64 MiB 的输入预算不等于 64 MiB 的内存占用。
- **`Completion` 和 EOF 是两件事。** `Completion` 只确认「执行资源回收完毕、产物 FD 已关闭」；
  连接槽位有没有归还，要靠服务端关闭连接产生的**正常 EOF** 来确认。少等这一步，
  客户端就会在槽位还没回来的时候认为可以发下一条。收到 `Completion` 之后多出来的任何字节，
  都不能当成功。
- **请求先限条目数、再限累计字节**，输入与产物是两个独立预算，避免用小控制帧触发无界分配。
- **字段名即线格式**，改名等于改协议；黄金用例把 `Request`/`Result`/`Completion`
  的字节输出钉死，防止「只是重命名一个字段」悄悄变成一次协议变更。

### 7.3 隔离与限量 —— 两条正交的轴

初学最容易把「隔离」和「限量」当成一个东西（runc 把它们打包成「容器」，更强化了这个错觉）。
其实内核给的是**两条互不依赖的能力**：

| 轴 | 回答的问题 | 内核机制 | 归谁管 |
|---|---|---|---|
| **隔离 Isolation** | 进程能**看见/碰到**什么？ | namespaces、pivot_root、seccomp、capabilities | **`launcher`** + **`policy`** |
| **限量 Resource control** | 能**用多少** CPU/内存/进程，实际**用了多少**？ | cgroup v2、（弱）rlimits | **`cgroup`** |

**它俩可以单独存在**，这是理解边界的关键：

- 只隔离不限量：进程看不见宿主文件/网络，但内部 fork 炸弹照样拖垮宿主。
- 只限量不隔离：内存/CPU 被卡住，但能读你的 `/etc/passwd`、能联网。
- 都不做：就是临时目录里一个普通子进程，也就是 `devhost`。

#### 隔离这条轴拆成什么

| 机制 | 干什么 |
|---|---|
| mount namespace + **pivot_root** | 自己的文件系统视图、换根、只读 rootfs |
| pid namespace | 内部成 PID 1，看不见宿主进程 |
| net namespace | 空网络栈＝断网（最便宜的安全收益） |
| ipc / uts namespace | 独立共享内存 / hostname |
| user namespace | 宿主普通用户在内部当 root → 内部 uid 0 也不是宿主 root |
| **seccomp-bpf** | 过滤/禁用系统调用（`ptrace`/`mount`/`reboot`…），装载后用 TSYNC 覆盖全部线程 |
| capabilities drop | 即使内部 uid 0，也砍掉 `CAP_SYS_ADMIN` 等特权 |

这些**全和 cgroup 无关**——它们回答「能看见/能干什么」，不是「能用多少」。

#### 限量这条轴

cgroup v2 在**一组进程**层面控制 + 计量：`memory.max`/`memory.peak`（算 MLE）、
`cpu.max`/`cpu.stat`（算 TLE）、`pids.max`/`pids.peak`（挡 fork 炸弹）。
每次执行建一个子 cgroup → 起进程时把它塞进去 → 跑完读 peak → 删掉。

写完限额之后会**读回来比对**：写进去和读出来不一致就拒绝，而不是假设写成功了。
新建的 cgroup 如果已经有进程或历史计量，也直接拒绝——那说明它不是新建的。

> `rlimits`（`setrlimit`）是更弱的进程级老式限量，可作兜底；真限量在 Linux 上靠 cgroup。
> 因此**权威的 CPU/内存用量一律来自 cgroup 快照**，不用单进程 `wait4` 的 rusage 推断整组峰值；
> `Facts.GroupAccounting` 就是用来告诉调用方「这是整组计量」的。

### 7.4 一个必须点破的耦合

进程只在被 `clone` 创建的那一刻能同时进 namespace 和进 cgroup（`CLONE_INTO_CGROUP`）。
所以启动器得**拿着已经建好的 cgroup 句柄**，在孩子 exec 用户代码**之前**就把它关进笼子，
否则有竞态窗口让它先 fork 炸弹。

但 cgroup 的**建立 / 写上限 / 读峰值**仍然全在 `cgroup` 包——`launcher` 只是「起进程时把孩子
塞进已建好的笼子」。这是两条轴唯一交汇的地方，也仅此一处。

### 7.5 结论是纯函数，状态转移是一张表

一次执行结束后要回答：这是超时、OOM、被信号杀、正常退出，还是平台故障？

这个判断**不掺任何 I/O**：

```go
// helperd/internal/helper
type executionFacts struct {
    supervision supervisionOutcome  // 进程怎么结束的
    cancelled   bool                // 请求是否已被取消
    group       cgroup.Snapshot     // cgroup 读数
    groupErr    error
    budget      contract.Limits
    completion  processCompletion
}

func conclude(f executionFacts) conclusion   // 纯函数
```

好处是它可以被穷举测试：给定一组事实，结论必须唯一且可重复。同样地，执行的状态机是一张
**显式的转移表**（`state.go` 里的 `executionTransitions`），非法转移直接报错而不是走到一个
没人想过的分支。表的价值在缺一条时立刻显现——`starting → cleanup-failed` 曾经漏掉，
测试当场指出来了。

这里有一条**顺序上的陷阱**，值得单独记住：

```go
// 「请求是否已被取消」必须在解除输入阻塞之前读取。
facts := executionFacts{..., cancelled: ctx.Err() != nil, ...}
x.process.CancelInput()
```

`CancelInput` 的实现可以取消调用方自己的上下文——启动冒烟正是这样接线的。
读晚一步，**每次正常执行都会被判成已取消**，于是冒烟永远失败，helper 永远不开 socket，
而外部看到的现象只是一句「helper socket did not become ready」。

---

## 8. 跨层预算：三道期限必须有序

一次执行同时被三道期限约束，它们必须**从外到内严格递减**：

```
HTTP 写期限  >  本机会话期限(SessionTimeout)  >  单次执行墙钟硬界(MaxClockNs)
```

顺序错了，症状会极具误导性：

- 写期限 ≤ 会话期限：连接先被切断，**调用方看到的是传输失败，而不是执行结论**。
- 会话期限 ≤ 墙钟硬界：达到墙钟上限的命令先被会话期限打断，**一次正常的 TLE 被报成平台错误**。

judge 侧还有一条同源的断言：`judge.sandboxTimeout` 必须大于 `judge.compile.clockNs`。
设小了的表现是「沙箱正常跑着，judge 自己先超时」，报出来是 SE，查半天查不到原因。

这些关系都写成了**参数化的断言函数**（`sandbox/budget.go` 的 `checkBudget`），
既在服务启动时用真实取值跑一遍，也在测试里直接喂冲突取值验证它确实会拒绝——
只在正确取值下跑一遍等于没测。

---

## 9. 端到端时间线

```
server              judge                sandbox              helperd(root)
  |                   |                     |                      |
  |-- POST /judge --->|                     |                      |
  |                   |-- POST /blobs ----->|                      |
  |                   |<---- srcRef --------|                      |
  |                   |-- POST /run(编译) ->|                      |
  |                   |                     |-- hostexec Request ->|
  |                   |                     |                      | 建隔离环境
  |                   |                     |                      | 起进程、读 cgroup
  |                   |                     |<- Result + 产物 -----|
  |                   |                     |<- Completion, EOF ---|
  |                   |<-- OK + exeRef -----|                      |
  |                   |-- POST /run(测点1)->|        …（同上）…     |
  |                   |<-- OK + stdout -----|                      |
  |                   |  checker → case AC  |                      |
  |<-- JudgeResult ---|                     |                      |
```

单独测 sandbox 时，用 `curl` 打 `/blobs`、`/run` 即可；helperd 不对外暴露端口，
要单独验证它得走 `helperd/tests/boundary` 那组需要真实内核的测试。

---

## 10. 设计原则

1. **信任边界**：特权只在 helperd 里；sandbox 和 judge 都是普通用户进程。
2. **部署边界**：三个二进制各自独立交付，彼此只认协议，不认对方的实现。
3. **编译期强制**：上面两条写成 `internal/` 目录结构，越界就编译失败——不靠 code review 把关。
4. **一次性执行**：没有可复用的执行环境对象，「只能执行一次」由「没有对象可复用」保证。
5. **结论与事实分离**：helperd 给事实，runner 给 status，judge 给 verdict；三层谁都不越权。
6. **接口由消费方定义**：`flow.Sandbox`、`probe.Sandbox` 声明在使用方，实现不反向依赖。
7. **学习边界**：复用参考项目的**思路**，命名与实现按场景自己写；先打通，再硬化。

---

## 11. 刻意不做

- Windows/macOS 上的「真沙箱」
- gRPC、WebSocket、FFI
- sandbox 里出现 WA，helperd 里出现 status
- 复用跑过用户代码的隔离环境
- MVP 的多程序管道、交互题、special judge
- 直接依赖 go-judge/go-sandbox 库

---

## 12. 和其他文档的关系

| 文档 | 回答什么问题 |
|---|---|
| [architecture.md](./architecture.md) | 整个 cherry-oj 系统怎么拼 |
| [data-model.md](./data-model.md) | PRD 对应的产品领域模型、版本关系与 Submission 快照 |
| **本文 engine.md** | engine 内部为什么这样设计、每层干什么；含一次判题的文件模型 |
| 各服务的 `doc.go` | 该子树的职责、谁可以引用它、它不可以引用什么 |
| 本地 `tutorial/` | 怎么搭建、按 M0→M2 分阶段实现与验收；该目录不进入 Git |

先读设计建立地图；动手时只打开 `tutorial/` 对应阶段。

## WORK-040 节点生命周期与数据交付

节点控制协议以 `contracts/judge-node.schema.json` 为准。Judge 使用稳定 nodeId 和进程 sessionId
先经 sandbox /run、/version 探测实际环境，并将两端二进制、资源配额、工具链和 Judge 策略计入指纹。
随后向 judging-service 注册真实环境能力，后台心跳失败时重试且不关闭健康入口；控制面租约过期后停止
部署和路由。安装接口通过独立共享 token 保护，使用有界 multipart 流、摘要和 manifest 二次校验、
节点私有目录和原子 rename。数据回执绑定 nodeId、环境指纹、sessionId、版本、hash 与文件数。
重启后旧回执不可直接调度，再次部署会幂等检查本地文件并恢复当前会话的可用性。

本地 Compose 的 Judge 使用私有 `judge-testdata` 卷，Java 不挂载该目录。生产使用相同链路，
只 REGISTERED 新指纹，不能静默替换 ACTIVE。显式回退使用 `legacy-local` 与
`compose.legacy.yaml`，保留 migration、历史校准和 JudgeInput；具体参数见 `apps/server/README.md`。

改变环境时使用新 nodeId 与新的 `JUDGE_TESTDATA_VOLUME`，保留旧卷供回退。sandbox 独立升级后须重启 Judge 重新探测。
