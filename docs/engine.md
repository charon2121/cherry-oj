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
   namespace、pivot_root、cgroup、seccomp——这些动作**需要 root**。
   → 这叫 **sandbox 执行器**（`apps/sandbox`，一个 setuid-root 的 C 程序）。它像只有主管才有钥匙的
   那间通风柜，而且每次用完就锁上：一次执行一个进程，执行完就退出。

**为什么第 2、3 件事要分开？**

它们看起来是一件事，但**需要的权限完全不同**：排队、限流、管临时文件不需要任何特权；建 namespace
和写 cgroup 需要 root。放进同一个进程，就等于让那个进程整体以 root 运行——于是排队逻辑里的一个
越界写，后果是 root 权限下的越界写。拆开之后，特权代码小到可以逐行读完，非特权服务再大也只是个
普通用户。

特权部分还刻意做成**一次性的**：没有常驻的特权进程，也没有它和 sandbox 之间的会话协议。每次执行
sandbox 服务把请求写进一个 box 目录、exec 一次执行器、读回一行 JSON。执行器退出，特权随之消失。

一句话记住边界：

> **执行器只说「进程怎么退出的、用了多少资源」；sandbox 只说「这条命令执行完了」；
> judge 才说「算不算对」。**

---

## 1. 两个服务、一个执行器，两条硬边界

### 1.1 各自回答什么

| 程序 | 身份 | 回答的问题 | 默认端口/地址 |
|---|---|---|---|
| **judge** | 普通用户 | 这份提交是 AC 还是 WA？ | `127.0.0.1:5051`（HTTP） |
| **sandbox** | 普通用户，**非 root** | 这条命令跑完了吗？产物在哪？ | `127.0.0.1:5050`（HTTP） |
| **sandbox 执行器** | **setuid-root**，每次执行一个进程 | 进程退出码是多少？整组用了多少 CPU/内存？ | 无；只由 sandbox exec |

产品从上到下是：

```
浏览器 → server(Java) → judge(Go) → sandbox(Go) → sandbox 执行器(C, setuid-root)
```

`server` 侧的职责见 [architecture.md](./architecture.md)；本文只讲后三个。

### 1.2 信任边界：root 与非 root 之间

执行器是唯一持有特权的程序。它和 sandbox 服务之间的那条线是**信任边界**：

- 执行器**不信任**调用方给的任何东西。请求里不接受宿主路径、不接受 uid/gid；rootfs、box 根目录、
  cgroup 子树和各身份都来自 root 管理的受信配置（`/etc/cherry-sandbox/executor.conf`，路径编译进
  二进制）。box 目录里的文件按「不跟随链接、只读单链接的服务所有普通文件」打开。
- 谁能调用是**双重约束**：文件模式只让服务组执行（`root:cherry-sandbox 4754`），执行器启动后再
  核对真实 UID 必须是配置里的服务身份（或 root）。
- 执行器能拿到的能力不超过 sandbox 服务单元的**能力边界集**（8 项，见 §7.1）。服务进程本身
  非 root、没有任何有效能力；setuid 取得的能力在执行器里用完即丢，init 与用户程序都拿不到。
- 两端的共享词汇只有 box 目录约定与一行 JSON 事实，写在 [apps/sandbox/README.md](../apps/sandbox/README.md)。
  Go 侧的调用方是 `execution/backend/executor.go`，C 侧与它互相看不见实现。

### 1.3 部署边界：独立交付

judge、sandbox 两个 Go 二进制和执行器分别构建，放进同一个 release；两个服务各自拥有服务生命周期。
部署位置还受当前节点探测方式约束：

- 当前生产的 Linux 原生节点模式要求 judge 和 sandbox **在同一台机器**。judge 要求 sandbox 地址为
  回环地址，并核验本机部署清单、二进制（含执行器）摘要和 cgroup 事实。将 judge 移到远端会使这些
  检查失败。
- 开发机上可以只跑 judge + sandbox（`devhost` 后端），完全不需要执行器。

服务之间通过协议交互，各自实现保持私有；同机部署也不改变这个边界。独立二进制让它们能分别
启动、退出与排障，不能据此推导出当前节点模式支持跨主机部署。

### 1.4 边界由测试把守，不是由约定

两棵服务子树 `judge/`、`execution/` 都不放在 `internal/` 下，少一层目录，读代码时路径更短；只有模块
顶层的 `internal/`（协议定义与平台设施）保留，给两个服务共用。

代价是编译器不再拦「一个服务引用另一个服务的实现」。这条边界改由模块根 `layout_test.go` 的
`TestServiceBinariesLinkOnlyTheirOwnSubtree` 守住：它用 `go list -deps` 取出每个服务二进制的
完整依赖闭包（间接引用也算），逐个核对：

| 二进制 | 不得链接 | 为什么 |
|---|---|---|
| `cmd/judge` | `execution/…` | judge 只能通过 HTTP 用 sandbox |
| `cmd/sandbox` | `judge/…` | sandbox 不理解判题 |

测试同时断言每个二进制确实依赖自己的子树，防止查询本身失效、返回空集而「永远通过」。
它按 linux/amd64 解析依赖，在 macOS 上也能跑，并登记在 CI 的必跑清单里。

特权实现是另一种语言写的独立程序，Go 模块里根本没有可链接的特权代码——「非特权进程不能链接特权
实现」由此在结构上成立，不再需要一条检查。

`cmd/judge`、`cmd/sandbox` 只做**解析 flag、读配置、建 logger、接信号**，然后调用
`judge.Run` / `sandbox.Run`。「服务的装配顺序」留在各自的根包里，不渗进最没人读的入口文件。

> **这一条是整份设计的地基**：边界写在注释里靠自觉，写成可执行的检查才靠得住。

### 1.5 为什么按服务切，而不是按「代码种类」切

早先的目录是按「这是什么种类的代码」切的——`internal/judge/`、`internal/sandbox/`、
`internal/sandbox/container/`、`internal/config/`……问题不在于名字难听，而在于**它切错了轴**：

- 一个 `internal/config` 同时装着各个服务的配置，于是「加一个 judge 的配置项」会让 sandbox 的包
  重新编译。
- `container` 这个名字暗示「有个容器对象，可以复用」，于是接口长成了「放输入 → 启动 → 等待 →
  取产物 → 关闭」四个阶段，时序只能写在运行时错误里（「Container 只能执行一次」
  「工作区不再接受输入」），两个实现各自拿一组状态字段守护它。
- 最要命的是：**没有任何机制阻止 judge 直接 import sandbox 的内部实现**，因为它们是同一个
  `internal/` 下的兄弟目录。

按服务切之后，这三类问题一起消失：配置跟着服务走，`container` 变成一次性的
`backend.Execute`（见 §6.3），跨服务引用由依赖检查拦下（§1.4）。

---

## 2. 目录树

```
apps/judge-engine/
├── go.mod                      # module cherry-oj/judge-engine
├── cmd/
│   ├── judge/main.go           # 只做 flag + 配置 + logger + 信号，然后 judge.Run
│   └── sandbox/main.go         #   同上 → sandbox.Run
│
├── internal/                   # ★ 整个 module 共享：只放「协议与平台设施」
│   ├── contract/               #   judge ↔ sandbox 的 HTTP DTO、Limits、Verdict/Status
│   └── platform/
│       ├── config/             #   泛型配置加载：默认值 → YAML → 环境变量
│       ├── logging/            #   slog 装配
│       └── tracing/            #   trace 传播
│
├── layout_test.go              # 两棵子树之间的引用边界（见 §1.4）
│
├── judge/                      # ★ 服务一：判题编排
│   ├── doc.go                  #   本子树的职责与引用边界
│   ├── judge.go                #   Run(ctx, Config, *slog.Logger) error
│   ├── config.go
│   ├── api/                    #   POST /judge、GET /version
│   ├── flow/                   #   一次判题：编译 → 逐点跑 → 比对 → 汇总
│   ├── checker/                #   单遍流式比对选手输出与标准答案
│   ├── language/               #   某语言怎么编译、产物叫什么、怎么运行
│   ├── testcase/               #   从磁盘读某个 testDataVersionId 的测试点
│   ├── sandboxclient/          #   给 sandbox 打电话的 HTTP 客户端
│   ├── config/                 #   judge 自己的配置与校验
│   └── node/                   #   节点身份与数据交付（见 §5.5）
│       ├── identity/           #     节点身份：nodeId、会话号、语言
│       ├── registry/           #     向 judging-service 注册与心跳
│       ├── install/            #     接收测试数据并原子落盘
│       ├── preflight/          #     注册前自检：确认 sandbox、校验原生部署
│       └── wire/               #     严格 JSON 解码
│
└── execution/                  # ★ 服务二：执行服务（非 root）
    ├── doc.go
    ├── sandbox.go              #   Run(...)
    ├── config.go
    ├── budget.go               #   跨层期限断言（见 §8）
    ├── api/                    #   POST /run、/blobs、GET /version
    ├── pool/                   #   并发上限、排队、回收未确认时停止接单
    ├── runner/                 #   一次执行的完整生命周期与限额归一化
    ├── backend/                #   ★ 可替换边界：linux（调用执行器）/ devhost
    └── store/                  #   ref ↔ 磁盘文件

apps/sandbox/                   # ★ 执行器（C，setuid-root），不在 Go 模块里
├── src/
│   ├── main.c                  #   入口：受信配置、调用方核对、提权、锁 box、输出事实
│   ├── config.c / spec.c       #   受信配置与请求解析
│   ├── box.c                   #   box 目录的安全打开与写回
│   ├── cgroup.c                #   执行组：建组、写限额并读回、计量、整组 kill
│   ├── supervise.c             #   clone3、监督墙钟/CPU/输出/取消、结论
│   ├── child.c                 #   namespace 内：init（PID 1）与用户程序
│   ├── privilege.c / filter.c  #   降权步骤 / seccomp 策略
│   └── facts.c                 #   一行 JSON 事实
└── tests/                      #   需要真实内核与 root 的测试
```

### 2.1 顶层 `internal/` 放什么、不放什么

顶层 `internal/` 是唯一能被两棵子树同时引用的地方，所以它的准入条件要比别处严：

**可以放**：两个服务之间的**协议定义**（`contract`），以及和业务无关的**平台设施**
（配置加载、日志、trace）。

**不可以放**：任何一个服务的业务逻辑。一旦某个服务的实现搬进顶层 `internal/`，它就同时对另一个
服务可见——§1.4 的边界检查只看服务子树之间的引用，顶层 `internal/` 恰好是它放行的地方。

判断方法很简单：**问「另一个服务读它是合理的吗」**。`contract` 是两边的共同词汇；而 `flow` 如果
搬上去，sandbox 就能 import 判题逻辑了。

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

**设计禁令**：不要把 `WA` 放进 sandbox 的 Status 里，也不要把 Status 放进执行器。
执行器连 Status 都不判——它只报退出码、信号、cgroup 读数和主动终止原因，
「这算不算超时」是 `runner` 的策略。

---

## 4. 契约：三层各自约定「信封长什么样」

| 边界 | 定义在哪 | 谁说了算 |
|---|---|---|
| 浏览器 ↔ server | `contracts/submission.json` | server 持久化模型 |
| server(Java) ↔ judge(Go) | `contracts/judge.schema.json` | **这份 schema 是唯一真源** |
| judging-service ↔ judge 节点 | `contracts/judge-node.schema.json` | schema 是真源 |
| judge ↔ sandbox | `contracts/run.schema.json` | 实现以 `internal/contract` 为准，schema 当文档 |
| sandbox ↔ 执行器 | [apps/sandbox/README.md](../apps/sandbox/README.md) | **box 目录约定与一行 JSON，没有 schema** |
| 全链路 verdict | `contracts/verdict.json` | 已有 |

sandbox ↔ 执行器那一行没有 schema，是因为两端**同一个 release、同批部署**，没有第三方消费者。
约定写在执行器的 README 里；Go 侧对事实 JSON 严格解码（未知字段、尾随内容、未知原因、未请求的
产物一律拒绝），执行器改了输出格式而 Go 侧没跟上，会立刻变成平台错误，而不是悄悄丢字段。

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

时空限制由 judging-service 根据题目版本和语言的标定解析成绝对值，冻结进 JudgeInput 后随
`JudgeRequest` 下发。测试数据部署用 content hash 校验，节点回执绑定本次进程的会话号。

### 5.3 语言配置

```go
SourceName:       "Main.cpp"   // inputs 时文件叫这个名
Compile:          g++ Main.cpp -o Main -O2 -std=c++17
CompiledArtifact: "Main"       // 编译成功后作为 artifacts 存起来
Run:              ["Main"]     // 工作区里的可执行文件，不带 "./"
```

Python 没有 Compile，直接 `python3 Main.py`；解释器/编译器一律走 `PATH`，不写死绝对路径。

**注意 `language` 包里有 python，但节点对外只声明 `cpp`。** 这不是遗漏：整条平台链路当前是
cpp-only——Java 侧的控制器用 `@Pattern(regexp="cpp")` 卡住语言，也只有 cpp 的标定流程。
控制面按节点声明的语言路由，节点多声明一种走不通的语言，只会让它看起来能判这种语言。
要放开语言，得从 Java 侧的约束和标定开始改，不是从这里。

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
// judge/flow
type Sandbox interface {
    Upload(context.Context, io.Reader) (string, error)
    Run(context.Context, contract.RunSpec) (contract.RunResult, error)
    Delete(context.Context, string) error
}
```

这个接口声明在 `flow` 里，而不是在 `sandboxclient` 里。差别在于：**实现不依赖 flow**，
于是判题流程的测试不需要启动任何沙箱，给一个假的三方法实现就够了。同样的写法也用在
`node/preflight` 的 `Sandbox` 接口上——自检消费的是 judge 已有的 HTTP 客户端能力，
不再自建第三个 HTTP 客户端。

### 5.5 节点身份与启动自检

judge 作为判题节点时，只向控制面报身份：`nodeId`（配置）、`sessionId`（每次进程启动新生成）、
访问地址和能判的语言。它**不回答「你是什么样的执行环境」**——不上报 CPU、内核、工具链，
也不把判题策略算成指纹。

这曾经是另一套设计：机器事实、判题策略和 judge 二进制摘要被揉成一个「环境指纹」，控制面按指纹把
节点归并成「判题环境」，标定也挂在环境上。结果是任何一次发版都会轮换指纹，新节点只能以 REGISTERED
出现，必须人工切换 ACTIVE 环境并重新部署、重新标定。现在控制面按节点路由（在线、声明了该语言、
本会话持有数据），标定按「题目版本 × 语言」记录；换机器需要重新标定时，由管理员对题目版本显式重做。

`node/identity` 只负责生成这份身份。`sessionId` 让控制面区分同一 nodeId 的新旧进程：进程重启后旧的
数据安装回执失效，必须重新安装证明持有。

`node/preflight` 在注册前自检，任何一项不满足都拒绝上线：`GET /version` 必须表明对端是 cherry-oj 的
sandbox；sandbox 报告 linux 隔离与配置了部署清单必须同时成立。

原生部署还要多一道：`node/preflight/deployment_spec.go` 用命名常量写清「一个合格的原生部署长什么样」
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
真的是隔离部署」，自检不过就在 `net.Listen` **之前**返回错误——端口都不会被占上，
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
// execution/backend
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
| `linux` | 全套：namespace、只读 rootfs、cgroup、seccomp，**由 setuid 执行器完成** | 生产 |
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

### 6.5 `store` 与 box

两个都管文件，但管的是不同的东西：

- **`store`**：跨执行的「文件字典」，`ref` ↔ 磁盘上的一个文件，带容量与保留期。源码上传进来、
  编译产物存进去、下一次执行再取出来，走的都是它。目录必须是服务私有的 0700，加独占锁，
  拒绝任何非独占的普通文件。
- **box**：**单次执行**的请求与结果目录，是 sandbox 和执行器之间的交接处（§7.2）。每个并发名额
  一个，执行前后都被重置为只剩空的 `in/` 与 `out/`。用户程序真正的工作区不在这里，而是执行器为
  每次执行新建的一块 tmpfs，执行结束随 namespace 一起消失。

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

## 7. sandbox 执行器详解

### 7.1 它是唯一持有特权的程序

执行器（`apps/sandbox`）是一个 C 程序，以 setuid-root 安装。它没有常驻进程、没有 socket、没有网络
端口：sandbox 服务每执行一条命令就 exec 它一次，它建出隔离环境、跑完命令、回收干净、在 stdout 输出
一行 JSON 事实，然后退出。

```
sandbox（宿主侧，root）   校验配置与请求 → 锁定 box → 建执行组 → clone3(6 个 namespace)
  └─ init（PID 1）        挂载 rootfs、/work、/tmp、/proc、/dev → pivot_root → 复制输入 → fork → 降权
       └─ 用户程序        rlimit → 降到 payload 身份 → seccomp → execve
sandbox 监督墙钟、CPU、输出与取消 → 整组 kill → 最终计量 → 取回产物 → 输出一行 JSON
```

它对自己的调用条件很苛刻：受信配置和它的每一级祖先目录都必须属于 root 且不可被他人写入；调用方的
真实 UID 必须是配置里的服务身份；服务、init、payload 三类身份必须互不重叠且都不是 root。任何一条
不满足，退出码 1，什么都不启动。

setuid 取得的能力不会超出 sandbox 服务单元的能力边界集。边界集只保留执行器必需的 8 项：
`SYS_ADMIN`、`SETUID`、`SETGID`、`SETPCAP`、`CHOWN`、`DAC_OVERRIDE`、`MKNOD`、`KILL`。前七项用于
建 namespace、挂载、降权、交还文件所有权和建最小 `/dev`；`KILL` 见 §7.4。部署验证逐项删掉其中
一项，每次服务都必须在启动自检时失败——证明没有一项是多余的。

为此 sandbox 服务单元**不能**设置 `NoNewPrivileges`，也不能设置任何对非 root 服务隐含它的加固项
（`RestrictAddressFamilies`、`LockPersonality`、`ProtectKernel*`、`RestrictSUIDSGID`、
`RestrictNamespaces` 等），否则 setuid 失效；`ProtectControlGroups` 也不能设，执行器要写 cgroup。
这是用「没有常驻特权进程」换来的代价，服务进程本身仍然非 root、没有任何有效能力。

### 7.2 box：执行器和 sandbox 服务之间唯一的约定

每个并发名额对应一个 box 目录 `<boxes>/<N>/`，属于服务身份：

```
spec        请求：NUL 结尾的 key=value 记录（arg / env / input / output / 六项限额）
stdin       标准输入
in/<i>      输入文件，序号对应 spec 里 input 的顺序
out/        必须为空；执行器写回 stdout、stderr、artifact-<i>
```

几个值得解释的决定：

- **请求不用 JSON。** setuid-root 程序里少一个解析器，就少一块攻击面；NUL 分隔的记录几十行就能
  解析完，而且不存在转义。
- **文件放在磁盘上，不走管道。** 输入可能有 64 MiB，放进目录之后执行器按需复制进工作区，两端都不用
  全量缓冲。
- **执行器以 root 操作服务所有的目录**，所以打开 box 里的任何东西都用 `openat2` 加
  `RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS | RESOLVE_NO_XDEV`，只接受单链接、
  属于服务的普通文件；写回只用 `O_EXCL` 新建，再把所有者交还服务。同一 box 同时只允许一次执行
  （`flock`）。
- **取消走 stdin。** sandbox 服务把一根管道接在执行器的 stdin 上并保持写端打开；关闭它——或者服务
  进程崩溃——就是取消。不需要另一个信号通道。
- **退出码分三类**：0 事实已输出、执行组已回收；1 拒绝执行，什么都没启动；2 回收未确认。Go 侧把
  2、被信号杀死和未知退出码都当成回收未确认（§6.6），并且不再把这个 box 交给下一次执行。

每次执行前后，Go 侧都把 box 重置为只剩空的 `in/` 与 `out/`：源码、stdin 和输出不在磁盘上过夜。

### 7.3 隔离与限量 —— 两条正交的轴

初学最容易把「隔离」和「限量」当成一个东西（runc 把它们打包成「容器」，更强化了这个错觉）。
其实内核给的是**两条互不依赖的能力**：

| 轴 | 回答的问题 | 内核机制 | 在哪 |
|---|---|---|---|
| **隔离 Isolation** | 进程能**看见/碰到**什么？ | namespaces、pivot_root、seccomp、capabilities | `child.c`、`privilege.c`、`filter.c` |
| **限量 Resource control** | 能**用多少** CPU/内存/进程，实际**用了多少**？ | cgroup v2、（弱）rlimits | `cgroup.c` |

**它俩可以单独存在**，这是理解边界的关键：

- 只隔离不限量：进程看不见宿主文件/网络，但内部 fork 炸弹照样拖垮宿主。
- 只限量不隔离：内存/CPU 被卡住，但能读你的 `/etc/passwd`、能联网。
- 都不做：就是临时目录里一个普通子进程，也就是 `devhost`。

#### 隔离这条轴拆成什么

| 机制 | 干什么 |
|---|---|
| mount namespace + **pivot_root** | 自己的文件系统视图、换根、只读 rootfs；`/work` 是独立的 tmpfs |
| pid namespace | init 成为 PID 1，看不见宿主进程；`/proc` 以 `hidepid=2,subset=pid` 挂载 |
| net namespace | 空网络栈＝断网（最便宜的安全收益） |
| ipc / uts namespace | 独立共享内存 / hostname |
| cgroup namespace | 进程看到的 cgroup 根是自己的执行组，看不到宿主的 cgroup 树 |
| **seccomp-bpf** | 白名单系统调用，其余直接杀进程；非 x86_64 ABI（含 x32）同样杀掉 |
| 降权 | 清空全部 capability、切到每个 box 专用的非 root UID/GID、设置 no_new_privs |

**不使用 user namespace。** 用户程序从来不以 uid 0 运行，而是直接以宿主上真实存在的专用非 root
身份运行（每个 box 一组，互不重叠），所以不需要靠 user namespace 把「内部 root」映射成宿主普通用户。
init 也降到自己的专用身份，并装上不含 `execve` 的过滤器，只负责等待、上报和回收孤儿。

这些**全和 cgroup 无关**——它们回答「能看见/能干什么」，不是「能用多少」。

#### 限量这条轴

cgroup v2 在**一组进程**层面控制 + 计量：`memory.max`/`memory.peak`（算 MLE）、
`cpu.max`/`cpu.stat`（算 TLE）、`pids.max`（挡 fork 炸弹）、`memory.oom.group`（OOM 杀整组）。
每次执行建一个子 cgroup `jobs/box-N` → clone 时把 init 直接放进去 → 结束时 `cgroup.kill` 整组 →
等 `populated` 变 0 → 读最终计量 → 删掉。

写完限额之后会**读回来比对**：写进去和读出来不一致就拒绝，而不是假设写成功了。
新建的 cgroup 如果已经有进程或历史计量，也直接拒绝——那说明它不是新建的。上一次执行意外中断
留下的同名组，会先被杀空、删除，失败就以退出码 2 报告回收未确认。

> `rlimits`（`setrlimit`）是更弱的进程级老式限量，可作兜底；真限量在 Linux 上靠 cgroup。
> 因此**权威的 CPU/内存用量一律来自 cgroup 快照**，不用单进程 `wait4` 的 rusage 推断整组峰值；
> `Facts.GroupAccounting` 就是用来告诉调用方「这是整组计量」的。

### 7.4 两个必须点破的耦合

**进 namespace 与进 cgroup 必须是同一个动作。** 进程只在被 `clone` 创建的那一刻能同时进
namespace 和进 cgroup（`CLONE_INTO_CGROUP`）。所以执行器先建好执行组、拿着它的目录句柄
clone3，孩子在 exec 用户代码**之前**就已经在笼子里，没有先 fork 炸弹的竞态窗口。

**执行器死了，namespace 必须跟着死。** 执行器是唯一在监督执行的进程；它若被杀，没有谁会再去
`cgroup.kill` 那个执行组。所以 init 设置了 `PDEATHSIG=SIGKILL`：父进程一死，PID 1 被杀，内核随之
终止整个 pid namespace。这里有两个坑，都曾让「杀掉执行器」的故障测试挂住：

- **内核在进程凭据变化时会清掉 PDEATHSIG。** init 降到自己的身份之后必须重新设置；重新设置之前
  父进程可能已经死了，所以设置后立刻检查报告管道的写端是否已经没有读者，没有就自己退出。
- **PDEATHSIG 按普通 kill 的权限投递。** 发送方是执行器（root 身份），接收方是 init 身份，两者身份
  不匹配时需要 `CAP_KILL`，否则信号被静默丢弃。这就是能力边界集里有 `KILL` 的原因；执行器在边界集
  缺少它时拒绝执行，而不是带着一个失效的兜底去跑。

### 7.5 结论是纯函数

一次执行结束后要回答：这是超时、OOM、被信号杀、正常退出，还是平台故障？

这个判断**不掺任何 I/O**，集中在 `facts.c` 的 `conclude`：给定监督结果、是否取消、最终计量和
CPU 预算，输出唯一的原因与信号。几条规则值得记住：

- `oom_kill > 0` 而本任务 `oom == 0`：受害进程来自祖先或全局 OOM，**判平台故障**，不能把平台内存
  压力算成用户超限。
- init 没来得及报告就消失：若同时有本任务 OOM 证据，按 SIGKILL 处理（OOM 连 init 一起杀了）；
  否则是平台故障。
- 采样可能恰好错过最后一段 CPU，没有其他原因时用最终计量兜底判 CPU 超限。
- 执行组没能确认清空：平台故障，并以退出码 2 要求调用方停止接单。

监督本身（`supervise.c`）用一个 `poll` 循环同时看 stdout/stderr、exec 错误管道、init 报告、
pidfd、取消管道和 5ms 的采样定时器。**计时从 execve 成功那一刻开始**：exec 错误管道是 CLOEXEC 的，
它的 EOF 就是「用户程序已经开始运行」，CPU 用量也从这一刻的基线开始算，不把建环境的开销算进
用户的时间。

---

## 8. 跨层预算：三道期限必须有序

一次执行同时被三道期限约束，它们必须**从外到内严格递减**：

```
HTTP 写期限(160s)  >  执行器调用上界(ExecutorBound)  >  单次执行墙钟硬界(MaxClockNs, 120s)
```

执行器调用上界不是另设的一道超时，而是执行器内部三段期限之和：启动 3s + 墙钟 120s + 回收 10s。
三段各自有独立的超时，任何一段到期都会结束执行，所以一次调用不会超过它们的和。

顺序错了，症状会极具误导性：

- 写期限 ≤ 执行器调用上界：连接先被切断，**调用方看到的是传输失败，而不是执行结论**。
- 执行器调用上界 ≤ 墙钟硬界：跑满墙钟的命令没有留给启动和回收的时间，**一次正常的 TLE 被报成
  平台错误**。

judge 侧还有一条同源的断言：`judge.sandboxTimeout` 必须大于 `judge.compile.clockNs`。
测例墙钟可能比编译更长，因此 flow 在上传源码前还会检查每次请求的有效墙钟
（显式 `clockNs` 或 `cpuNs × clockRatio`）严格小于 `judge.sandboxTimeout`，冲突时返回带原因的 SE。

这些是必要条件，**不能证明调用期限覆盖了整个 sandbox 操作**。排队、输入输出传输和回收都会
消耗时间；当前 judge 配置不能推导这些耗时的总上界，部署时仍须为它们留出余量。
`sandboxTimeout` 会影响调用是得到执行结论还是超时成为 SE，调整它时要按上面的不等式复核。

sandbox 侧的三道期限由 `execution/budget.go` 的 `checkBudget` 在启动时检查；judge 的编译期限由
配置校验检查，测例期限由 flow 在请求时检查。测试分别覆盖合法、相等和越界值。

---

## 9. 端到端时间线

```
server              judge                sandbox              执行器(setuid-root)
  |                   |                     |                      |
  |-- POST /judge --->|                     |                      |
  |                   |-- POST /blobs ----->|                      |
  |                   |<---- srcRef --------|                      |
  |                   |-- POST /run(编译) ->|                      |
  |                   |                     |-- 写 box，exec ----->|
  |                   |                     |                      | 建隔离环境
  |                   |                     |                      | 起进程、读 cgroup
  |                   |                     |<- 一行 JSON，out/ ---|
  |                   |                     |   （执行器已退出）    |
  |                   |-- POST /run(测点1)->|        …（同上）…     |
  |                   |<-- OK + stdout -----|                      |
  |                   |  checker → case AC  |                      |
  |<-- JudgeResult ---|                     |                      |
```

单独测 sandbox 时，用 `curl` 打 `/blobs`、`/run` 即可；执行器不对外暴露任何端口，
要单独验证它得走 `apps/sandbox/tests` 那组需要真实内核和 root 的测试。

---

## 10. 设计原则

1. **信任边界**：特权只在一次性的执行器里，没有常驻的特权进程；sandbox 和 judge 都是普通用户进程。
2. **部署边界**：各个程序独立交付，彼此只认协议，不认对方的实现。
3. **可执行的边界**：服务之间的边界写成依赖检查（`layout_test.go`），越界就测试失败——不靠 code review 把关。
4. **一次性执行**：没有可复用的执行环境对象，「只能执行一次」由「没有对象可复用」保证。
5. **结论与事实分离**：执行器给事实，runner 给 status，judge 给 verdict；三层谁都不越权。
6. **接口由消费方定义**：`flow.Sandbox`、`preflight.Sandbox` 声明在使用方，实现不反向依赖。
7. **学习边界**：复用参考项目的**思路**，命名与实现按场景自己写；先打通，再硬化。

---

## 11. 刻意不做

- Windows/macOS 上的「真沙箱」
- gRPC、WebSocket、FFI
- sandbox 里出现 WA，执行器里出现 status
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

节点控制协议以 `contracts/judge-node.schema.json` 为准。Judge 注册只带节点身份：稳定的 nodeId、
每次进程启动新生成的 sessionId、访问地址和能判的语言；不上报机器信息，也没有「判题环境」分组。
注册前先做启动自检：对端必须是 cherry-oj 的 sandbox，原生部署还要核对部署清单。后台心跳失败时
重试且不关闭健康入口；控制面租约过期后停止部署和路由。安装接口通过独立共享 token 保护，使用有界
multipart 流、摘要和 manifest 二次校验、节点私有目录和原子 rename。数据回执绑定 nodeId、sessionId、
版本、hash 与文件数。重启后旧回执不可直接调度，再次部署会幂等检查本地文件并恢复当前会话的可用性。

本地 Compose 的 Judge 使用私有 `judge-testdata` 卷，Java 不挂载该目录。生产使用相同链路；
具体参数见 `apps/server/README.md`。
