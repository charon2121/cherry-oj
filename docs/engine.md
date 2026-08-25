# Engine 设计文档（详解版）

> 面向：**刚会 Go 基本语法、准备手写判题引擎** 的读者。
> 系统级拓扑见 [architecture.md](./architecture.md)。
> **全链路数据结构**见 [data-model.md](./data-model.md)（字段真源在 `contracts/`）。
> **动手步骤（分阶段教程）**见 [`../tutorial/`](../tutorial/README.md)。
>
> 本文是**设计文档**：讲清楚「是什么 / 为什么 / 边界在哪」。不写安装命令与逐步验收清单。
> 学习约束：对照 `dev-dependency/go-judge`、`go-sandbox` **学思路**，自己写实现；**命名按场景自拟，不照搬**。

---

## 0. 先建立直觉：OJ 判题在干什么？

用户在网页上交一份 C++ 代码，OJ 要回答一句话：**对不对、超时了没、爆内存了没**。

拆开看，其实是两件完全不同的事：

1. **安全地跑程序**
   把用户代码编译成可执行文件，在限制时间/内存的环境里跑起来，收集：退出码、跑了多久、用了多少内存、标准输出是什么。
   → 这叫 **sandbox（沙箱）**。它像实验室里的通风柜：只负责「安全地执行实验」，不管实验结果算不算对。

2. **根据跑的结果判分**
   把标准输出和标准答案比对；把「超时」映射成 TLE；编译失败映射成 CE……
   → 这叫 **judge（判题编排）**。它像阅卷老师：看实验记录，给出分数和评语。

**为什么必须拆开？**

- 沙箱以后可能要加固（namespace、cgroup、seccomp），阅卷逻辑不该跟着一起改。
- 沙箱可以被很多场景复用（不只是 OJ，也可以跑任意受限命令）。
- 参考项目 go-judge 只做沙箱；我们的 judge 是在沙箱之上自己加的一层。

一句话记住边界：

> **Sandbox 只说「发生了什么」；Judge 才说「算不算对」。**

---

## 1. Engine 在整个 cherry-oj 里站哪？

整个产品从上到下是：

```
浏览器 → server(Java) → judge(Go) → sandbox(Go)
```

| 组件 | 语言 | 干什么 | 默认端口（MVP） |
|---|---|---|---|
| server | Java | 用户、题目、提交入库；把源码丢给 judge | 自己定 |
| **judge** | Go | 编译 → 逐测试点跑 → 比对 → 汇总 AC/WA/… | `127.0.0.1:5051` |
| **sandbox** | Go | 隔离执行一条命令，返回时间/内存/输出 | `127.0.0.1:5050` |

**Engine = judge + sandbox 这两个 Go 程序合在一个代码仓库模块里。**

注意：

- 「合在一个模块」= 共享文件夹、共享类型定义、一条命令能编译两个程序。
- 「两个进程」= 运行时仍然是两个独立服务，用 HTTP 互相打电话，**不是** judge 直接 `import` sandbox 的内部函数来跑用户代码。

为什么运行时还要 HTTP，而不是函数调用？

1. 以后 sandbox 可以单独部署在带特权的 Linux 机器上，judge 跑在普通机器上。
2. 边界清晰：协议就是 JSON，方便用 `curl` 单独测 sandbox。

---

## 2. 一个 Go 模块、两个二进制 —— 目录怎么摆？

### 2.1 你需要认识的 Go 概念（和本项目相关）

| 概念 | 白话 | 在本项目里 |
|---|---|---|
| **module** | 一个有 `go.mod` 的代码单元 | `module cherry-oj/judge-engine` |
| **package** | 一个文件夹里的一组 `.go` 文件 | 如 `store`、`flow` |
| **cmd/** | 惯例：放 `main` 包，每个子目录一个可执行文件 | `cmd/judge`、`cmd/sandbox` |
| **internal/** | 惯例：只有本模块能 import | 几乎所有业务代码 |
| **接口 interface** | 「能做什么」的约定 | `Container`：能 Start / 读写工作目录 |

### 2.2 目录树（每个文件夹一句话）

```
apps/judge-engine/
├── go.mod
├── cmd/
│   ├── judge/main.go      # 入口：HTTP 5051
│   └── sandbox/main.go    # 入口：HTTP 5050
└── internal/
    ├── contract/          # 共用「信封」：请求/响应、状态枚举
    ├── judge/
    │   ├── api/           # 前台：POST /judge
    │   ├── flow/          # 判题流水：编译→跑点→比对→汇总
    │   ├── languages/     # C++/Python 怎么编译、怎么运行
    │   ├── problem/       # 从磁盘读题目和测试数据
    │   ├── checker/       # 比对 stdout 和标准答案
    │   └── client/        # 给 sandbox 打电话的 HTTP 客户端
    └── sandbox/
        ├── api/           # 前台：POST /run、/blobs
        ├── pool/          # 工位池：排队、限流
        ├── runner/        # 一道工序的完整生命周期
        ├── container/     # 真正「起进程、隔离」的实现
        ├── cgroup/        # 限制/读取 CPU、内存（Linux）
        └── store/         # 存储：ref ↔ 磁盘文件
```

### 2.3 为什么叫 api / pool / runner / store？

按**实验室场景**起名，而不是照搬 go-judge：

| 层 | 类比 | 只关心什么 |
|---|---|---|
| `api` | 前台接待 | HTTP、JSON 解析，不懂怎么跑程序 |
| `pool` | 工位管理员 | 有几个工位、谁空闲、从 store 取文件 |
| `runner` | 操作规程 | inputs → 启动 → 等结束 → outputs / artifacts |
| `container` | 具体工作间 | namespace/chroot/cgroup 怎么落地 |
| `store` | 临时存储 | `ref` ↔ 磁盘上的一个文件 |

**上层不知道下层细节**：`runner` 不应该写死「必须用 chroot」；它只调用 `Container` 接口。以后换隔离方式，只改 `container`。

命名对照表见 [architecture.md §2.1](./architecture.md)。

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

**设计禁令**：不要把 `WA` 放进 sandbox 的 Status 里。沙箱不懂标准答案。

---

## 4. 契约（contracts）：两边怎么约定「信封长什么样」？

服务之间传的是 JSON。完整讲解见 **[data-model.md](./data-model.md)**。

| 文件 | 谁跟谁说话 | 谁说了算 |
|---|---|---|
| `contracts/submission.json` | 浏览器 ↔ server（DB） | server 持久化模型 |
| `contracts/judge.schema.json` | server(Java) ↔ judge(Go) | **这份 schema 是唯一真源** |
| `contracts/run.schema.json` | judge ↔ sandbox | 实现以 `internal/contract` 为准，schema 当文档 |
| `contracts/verdict.json` | 全链路 | 已有 |

约定：JSON **camelCase**；时间 **ns**；内存 **bytes**。改跨语言接口时：**先改 `contracts/`**。

---

## 5. Sandbox 详解

### 5.1 它对外长什么样？

**端点划分原则**：动作用 RPC 动词，资源用 REST 名词——别把不可检索的动作硬凑成资源。

| 方法 & 路径 | 类型 | 说明 |
|---|---|---|
| `POST /run` | 动作 | 执行一条命令，返回 `RunResult`（同步、无 `GET /runs/{id}`，所以是动词） |
| `POST /blobs` | 资源 create | 上传字节 → `{ "ref": "..." }` |
| `GET /blobs/{ref}` | 资源 read | 取回字节（调试用，判题主链路不走） |
| `DELETE /blobs/{ref}` | 资源 delete | 清理 |
| `GET /version` | 探测 | 版本 + 隔离能力 |

> `blob` = 一袋不带名字的字节。文件名不在存储时决定，而在 `/run` 的 `inputs` 里由 key 决定——所以 store 只认 `ref`，不带文件名。包名叫 `store`，HTTP 资源叫 `/blobs`（URL 命名它承载的资源）。

#### `POST /blobs` —— 存进 store，拿回引用（ref）

请求体：原始字节（源码或二进制）。
响应：JSON `{ "ref": "a1b2c3..." }`（跟 `/run` 一致，全程 JSON 出）。

为什么不直接把源码塞进 `/run` 的 JSON？

- 可执行文件可能很大，JSON 里塞 base64 又丑又慢。
- 编译产物要给「下一个 /run」接着用：编译输出 `main`，再用 `inputs` 引用同一个 `ref` 去跑测试点。

#### `POST /run` —— 跑一条命令

设计原则：**字段名就是人话**，不要 fd 下标数组。

「跑 `/bin/echo hello`」：

```json
{
  "command": ["/bin/echo", "hello"],
  "env": ["PATH=/usr/bin:/bin"],
  "limits": {
    "cpuNs": 1000000000,
    "clockNs": 2000000000,
    "memoryBytes": 268435456,
    "maxProcesses": 50,
    "stdoutMaxBytes": 65536,
    "stderrMaxBytes": 65536
  }
}
```

「跑用户程序，喂测试输入」：

```json
{
  "command": ["main"],
  "stdin": "1 2\n",
  "inputs": { "main": { "ref": "exe-ref" } },
  "limits": {
    "cpuNs": 1000000000,
    "clockNs": 2000000000,
    "memoryBytes": 268435456,
    "stdoutMaxBytes": 65536,
    "stderrMaxBytes": 65536
  }
}
```

字段白话：

| 字段 | 意思 |
|---|---|
| `command` | 要执行的命令，等同终端里按空格拆开 |
| `stdin` | 喂给程序的标准输入。小：直接字符串；大：`{"ref":"..."}`（先存 store） |
| `inputs` | 开跑前写入工作目录的文件（可用 `ref`） |
| `outputs` | 跑完后把工作目录文件内容内联放进响应 `outputs` |
| `artifacts` | 跑完后存进 store，响应 `artifacts` 里给新 ref |
| `limits` | 资源上限；字段名自带单位（`cpuNs`、`memoryBytes`…） |

响应（直接就是结果，不套 `results[]`）：

```json
{
  "status": "OK",
  "exitCode": 0,
  "cpuNs": 700000,
  "clockNs": 2500000,
  "memoryBytes": 1048576,
  "stdout": "hello\n",
  "stderr": ""
}
```

为什么不用 go-judge 那种 `stdio: [fd0, fd1, fd2]`？
因为判题场景里，你永远关心的是「输入字符串 / 标准输出 / 标准错误」三件事，写成 `stdin` + 响应里的 `stdout`/`stderr` 一眼能懂；fd 数组是给通用沙箱留的灵活性，学习型 OJ 不需要。

### 5.2 一次 `/run` 在内部怎么走？

用「编译一份 C++」当例子：

```
1. api 收到 JSON，转成内部 Request
2. pool 从队列里取出任务（同时最多 N 个，N≈CPU 核数）
3. pool 看 inputs：若写了 ref，去 store 打开真实文件
4. pool 向 ContainerPool 借一个 Container（一间「工作间」）
5. runner 按规程操作：
   a. PutFile("main.cpp", 源码)         ← inputs
   b. Start(["g++","main.cpp","-o","main",...])   ← 真正起进程
   c. Wait：等进程结束（或 clockNs 到了就 SIGKILL）
   d. 读 cgroup，得到 cpuNs / memoryBytes
   e. 收集 stdout/stderr（带上限的 writer）
   f. GetFile("main") 读出可执行文件，交给 pool 存 store ← artifacts
6. pool 归还 Container（Reset 清工作目录，或 Close）
7. api 把 Result 写成 JSON 返回
```

### 5.3 三个内部接口 —— 职责怎么切？

Go 惯例：**接口小、入参收接口、出参给结构体、流用 `io.Reader/Writer`、取消用 `context`**。

```go
// store：一袋字节 <-> ref
type Store interface {
    Put(r io.Reader) (ref string, err error)
    Get(ref string) (io.ReadCloser, error)
    Delete(ref string) error
}

// container：★ 可替换的隔离边界
type Container interface {
    Start(ctx context.Context, s Spec) (Process, error) // 起进程；ctx 取消 = SIGKILL
    PutFile(name string, r io.Reader, mode fs.FileMode) error // 供 inputs
    GetFile(name string) (io.ReadCloser, error)               // 供 outputs / artifacts
    Reset() error // 清工作目录、池化复用（阶段 C）
    Close() error // 拆掉工作间
}

type Spec struct {
    Command        []string
    Env            []string
    Stdin          io.Reader // nil = 无输入
    Stdout, Stderr io.Writer // runner 传入带上限的 writer
    Limits         contract.Limits
}
type Process interface {
    Wait(ctx context.Context) (Usage, error) // 阻塞到退出
}
type Usage struct {
    ExitCode, Signal   int
    CPUNs, MemoryBytes int64 // 见 §5.5：权威的 CPU/内存来自 cgroup，host 版才用这里的粗略值兜底
}

// executor：HTTP 层唯一看得见的门面（pool 实现它，包住并发上限）
type Executor interface {
    Run(ctx context.Context, spec contract.RunSpec) (contract.RunResult, error)
}
```

**关键分工**：`Container` 只回答「在隔离环境里跑这条命令、fd 接到这些 reader/writer、进程**怎么退出的（退出码/信号）**」。**它不懂 TLE/MLE/OLE**，也不是权威的资源计量器（那是 `cgroup`，见 §5.5）——把「stdout 写超没、墙钟到没、内存爆没」翻译成 `status`，是 `runner` 的策略。好处：

- 今天实现「只建临时目录、不隔离」的 host 版（方便你在 Mac 上学习）。
- 明天换成「namespace + chroot + cgroup」的 Linux 版。
- `runner` / `pool` / `api` / `judge` **一行都不用改**。

这就是设计里说的「替换边界」。

### 5.4 隔离怎么分阶段学？（由易到难）

| 阶段 | 你得到什么 | 难度 |
|---|---|---|
| **A-lite** | 临时工作目录 + `os/exec`；可选 cgroup | 低，Mac 也能做 |
| **A** | namespace + bind mount + chroot + 每 run 建 cgroup | 中，需 Linux |
| **B** | user namespace、pivot_root、可选 seccomp | 高 |
| **C** | 预 fork 工作间池、socket 传 fd | 高 |

**建议**：先把 A-lite + 整条 judge 链路跑到 A+B 题 AC，再回过头啃 A/B/C。

### 5.5 container 与 cgroup —— 两条正交的轴

初学最容易把「隔离」和「限量」当成一个东西（runc 把它们打包成「容器」，更强化了这个错觉）。其实内核给的是**两条互不依赖的能力**：

| 轴 | 回答的问题 | 内核机制 | 归谁管 |
|---|---|---|---|
| **隔离 Isolation** | 进程能**看见/碰到**什么？ | namespaces、chroot/pivot_root、seccomp、capabilities | **`container`** |
| **限量 Resource control** | 能**用多少** CPU/内存/进程，实际**用了多少**？ | cgroup v2、（弱）rlimits | **`cgroup`** |

**它俩可以单独存在**，这是理解边界的关键：

- 只隔离不限量：进程看不见宿主文件/网络，但内部 fork 炸弹照样拖垮宿主。
- 只限量不隔离：内存/CPU 被卡住，但能读你的 `/etc/passwd`、能联网。
- 都不做（**host 版**）：就是临时目录里一个普通子进程。

#### 隔离这条轴拆成什么（都归 `container`，都是硬化阶段的活）

| 机制 | 干什么 |
|---|---|
| mount namespace + **pivot_root** | 自己的文件系统视图、换根、工作目录挂 tmpfs/只读 rootfs（**文件系统隔离**） |
| pid namespace | 内部成 PID 1，看不见宿主进程 |
| net namespace | 空网络栈＝断网（最便宜的安全收益） |
| ipc / uts namespace | 独立共享内存 / hostname |
| user namespace | 宿主普通用户在内部当 root → rootless，不必真 root 跑沙箱 |
| **seccomp-bpf** | 过滤/禁用系统调用（`ptrace`/`mount`/`reboot`…） |
| capabilities drop | 即使内部 uid 0，也砍掉 `CAP_SYS_ADMIN` 等特权 |

这些**全和 cgroup 无关**——它们回答「能看见/能干什么」，不是「能用多少」。

#### 限量这条轴（归 `cgroup`）

cgroup v2 在**一组进程**层面控制 + 计量：`memory.max`/`memory.peak`（算 MLE）、`cpu.max`/`cpu.stat`（算 TLE）、`pids.max`/`pids.peak`（挡 fork 炸弹）。
MVP：每次 `/run` 建一个子 cgroup → 起进程时把它塞进去 → 跑完读 peak → 删掉。

> `rlimits`（`setrlimit`）是更弱的进程级老式限量，mac 上也能用，可作 host 版没 cgroup 时的兜底；真限量在 Linux 上靠 cgroup。

#### 三个包怎么配合（`runner` 是协调者）

`container` 和 `cgroup` **不是上下层，是并排两条轴**，谁都不 import 谁：

```
runner.Run(spec):
  cg    := cgroup.Create(limits)       // 建子 cgroup、写上限（host 上是 stub，空操作）
  box   := pool.borrow()               // 一个 Container
  box.PutFile(inputs...)               // ← container：铺文件
  proc  := box.Start(ctx, Spec{argv, env, stdio, cgroup: cg.handle})
             ├─ ns 版：clone(CLONE_NEWNS|NEWPID|NEWNET|..., 直接 clone 进 cg)
             └─ host 版：exec.Command, Dir=workDir（无 ns、cg 空操作）
  exit  := proc.Wait(ctx)              // ← container：退出码/信号（墙钟超时→ctx 取消→杀）
  usage := cg.Read()                   // ← cgroup：memory.peak / cpu.stat  ★权威用量来自这里
  status := classify(exit, usage, stdoutOverflow, ctx.Err())   // ← runner：定 status
  box.GetFile(artifacts...) → store    // ← container：取产物
  cg.Destroy(); pool.giveBack(box)
```

- **container**：起进程、隔离、读写工作目录、报**退出码/信号**。
- **cgroup**：卡上限、读**内存/CPU/进程数用量**。
- **runner**：把两边事实拼成 `status`。

**一个必须点破的耦合**：进程只在被 `clone` 创建的那一刻能同时进 namespace 和进 cgroup（`CLONE_INTO_CGROUP`）。所以 `container.Start` 得拿着 cgroup 句柄，在孩子 exec 用户代码**之前**就把它关进笼子，否则有竞态窗口让它先 fork 炸弹。但 cgroup 的**建立/写上限/读峰值**仍在 `cgroup` 包——`container` 只是「起进程时把孩子塞进已建好的笼子」。

> 因此 §5.3 的 `Usage.CPUNs/MemoryBytes` 只是 host 版用 `wait4` rusage 填的**粗略兜底**；Linux 上权威值走 `cgroup.Read()` 的 `memory.peak`。「measurement 归 cgroup」这条边界要守住。

### 5.5.1 `hostContainer` 到底是什么

`hostContainer` 是 `Container` 接口的**「零隔离」实现**——隔离那条轴上什么都不做，只给工作目录这一层最弱边界（`cmd.Dir = workDir`，无 namespace/chroot/seccomp/cgroup）。它存在的两个理由：

1. **让你在 mac 上开发整条链路**：namespace/cgroup 是 Linux-only，mac 做不了真隔离，但你要开发 `runner`/`pool`/`judge` 并跑通「编译→测点→比对→AC」。
2. **硬化的第一级台阶（A-lite）**：先用它把管道跑绿，再换 `nsContainer`——两者同接口，`runner`/`pool`/`judge` 一行不改。

⚠️ **它不安全**：跑的程序能读宿主任意文件、能联网、能 fork 炸弹。**只适合开发期跑你信任的代码，绝不能生产跑用户提交**——生产要 `nsContainer`（阶段 B）。`hostContainer` 是脚手架，不是终点。

### 5.6 store 是干什么的？

就是一个带过期时间的「文件字典」：

```
ref "abc123" → /dev/shm/cherry-oj/abc123  （内容是 main.cpp 或可执行文件）
```

优先放 `/dev/shm`（内存文件系统，快）；没有就放临时目录。

### 5.7 为什么没有 `/compile`？

从 sandbox 的视角，**编译就是「跑一条命令」**：

```
编译： command=["g++","main.cpp","-o","main"]   inputs={main.cpp}  artifacts=[main]
跑点： command=["main"]                          inputs={main}      stdin=input
```

`g++` 只是一个「读文件、写文件、受时间/内存限制」的程序，和用户的 `main` 没有本质区别。若拆出 `/compile`，sandbox 就被迫懂「哪个编译器、什么 flag、产物叫什么、失败要判 CE」——这些是 **judge 域的知识**，会把 `languages` 那套往下漏进沙箱，破坏「sandbox 不懂判题」的边界。

**编译 vs 运行的区别属于 judge，用不同的 `RunSpec` 表达**（编译 limits 更宽、失败短路成 CE、产物 `artifacts` 出 ref 给跑点 `inputs`）。见 §6.4。go-judge、IOI `isolate` 也都是这么做的：沙箱只有一个「跑命令」的动词。

---

## 6. Judge 详解

### 6.1 它对外长什么样？

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

### 6.2 测试数据在磁盘上长什么样？（MVP）

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

### 6.3 语言配置

```go
SourceName: "main.cpp"           // inputs 时文件叫这个名
Compile: g++ main.cpp -o main ...
CompiledArtifact: "main"         // 编译成功后作为 artifacts 存起来
Run: ["main"]                    // 运行时工作目录里的 ./main
```

Python 没有 Compile，直接 `python3 main.py`。

### 6.4 judge ↔ sandbox 交互流程（重点）

先记住三句话：

1. **测试点只存在于 judge**：从磁盘读 `*.in` / `*.out`；sandbox 从不打开题目目录。
2. **一点一 `/run`**：每个测试点单独一次运行请求；编译也是单独一次 `/run`。
3. **标准答案不进 sandbox**：只把「本题输入」当 stdin 喂进去；比对在 judge 本地做。

#### 谁持有什么

| 数据 | 谁持有 | 怎么到对方 |
|---|---|---|
| 源码字符串 | judge（来自 JudgeRequest） | `POST /blobs` → `srcRef` |
| 题目限制、`1.in`/`1.out` | judge（`problem` 包读盘） | 输入进 `/run` 的 `stdin`；输出留在 judge 做 checker |
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

#### 逐步剧本（C++，2 个测试点）

```text
【0】judge 本地准备（不访问 sandbox）
    Load problem → timeLimit, memoryLimit, cases[{input, expected}, ...]
    Load language → compile 命令、run 命令、产物名 "main"

【1】上传源码
    judge → sandbox:  POST /blobs
                      body = 源码字节（octet-stream，不带文件名）
    sandbox → judge:  { "ref": srcRef }

【2】编译（1 次 /run）
    judge → sandbox:  POST /run
      {
        "command": ["g++", "main.cpp", "-o", "main", "-O2", "-std=c++17"],
        "inputs": { "main.cpp": { "ref": srcRef } },
        "artifacts": ["main"],
        "limits": { ...编译用，可比运行更宽... }
      }
    sandbox → judge:  RunResult
      若 status≠OK 或 exitCode≠0 → 整题 CE（message≈stderr），结束
      若成功 → artifacts["main"] = exeRef

【3】测试点 1（第 1 次运行 /run）
    judge → sandbox:  POST /run
      {
        "command": ["main"],
        "stdin": cases[0].input,          // ← 来自 1.in
        "inputs": { "main": { "ref": exeRef } },
        "limits": { "cpuNs": timeLimit, "memoryBytes": memoryLimit, ... }
      }
    sandbox → judge:  RunResult{ status, cpuNs, memoryBytes, stdout }
    judge 本地:
      status 非 OK → 该点 TLE/MLE/RE/SE
      否则 checker(stdout, cases[0].expected) → AC/WA/PE

【4】测试点 2（第 2 次运行 /run）
    同【3】，stdin = cases[1].input，和 cases[1].expected 比对
    （ICPC 可在首个非 AC 后短路）

【5】汇总（只在 judge）
    整体 verdict = 各点最差；cpuNs/memoryBytes = 各点最大
    返回 JudgeResult（含 cases[]）给 server
```

#### 时序图

```text
judge                         sandbox
  |                              |
  | POST /blobs (源码)           |
  |----------------------------->|
  |         srcRef               |
  |<-----------------------------|
  |                              |
  | POST /run 编译               |
  |  inputs: main.cpp=srcRef     |
  |  artifacts: [main]           |
  |----------------------------->|
  |  OK + artifacts.main         |
  |<-----------------------------|
  |                              |
  | POST /run 跑点1              |
  |  inputs: main=exeRef         |
  |  stdin=1.in 内容             |
  |----------------------------->|
  |  OK + stdout                 |
  |<-----------------------------|
  |  (本地 vs 1.out → case1)     |
  |                              |
  | POST /run 跑点2              |
  |  stdin=2.in 内容             |
  |----------------------------->|
  |  OK + stdout                 |
  |<-----------------------------|
  |  (本地 vs 2.out → case2)     |
  |  汇总 JudgeResult            |
```

#### 和「读测试点」相关的误解

| 误解 | 实际 |
|---|---|
| sandbox 按 testDataVersionId 读测例 | 否；sandbox 无题目概念 |
| 一次 `/run` 跑完全部测试点 | 否；MVP 一点一 `/run` |
| 标准答案要 inputs 进沙箱 | 否；答案只在 judge 做 checker |

#### 伪代码（测试点 vs inputs）

```go
srcRef := sandbox.Upload(source) // POST /blobs

compile := sandbox.Run(RunSpec{
    Command:   []string{"g++", "main.cpp", "-o", "main"},
    Inputs:    map[string]FileSource{"main.cpp": {Ref: srcRef}},
    Artifacts: []string{"main"},
})
exeRef := compile.Artifacts["main"]

for _, tc := range problem.LoadCases() {
    run := sandbox.Run(RunSpec{
        Command: []string{"main"},
        Stdin:   tc.Input, // 测试点输入 → stdin，不是 inputs
        Inputs:  map[string]FileSource{"main": {Ref: exeRef}},
        Limits:  Limits{CPUNs: timeLimit, MemoryBytes: memLimit, StdoutMaxBytes: 64 << 20},
    })
    verdict := checker.Compare(run.Stdout, tc.Expected) // .out 不出沙箱
}
```

---

### 6.5 flow 在代码里对应什么

`flow` 包按上面剧本调 `client`（HTTP 客户端）+ `checker` + `problem` + `languages`。
对外仍是一次 `POST /judge`；对内是多次 `/blobs` + `/run`。

**checker 默认策略**：逐行去掉行尾空格，再忽略末尾空行，然后比字符串。

---

## 7. 加上 server 后的端到端时间线

```
server                    judge                      sandbox
  |                         |                          |
  |-- POST /judge --------->|                          |
  |                         |-- POST /blobs (源码) ---->|
  |                         |<----- srcRef ------------|
  |                         |-- POST /run (编译) ------>|
  |                         |<-- OK + exeRef -----------|
  |                         |-- POST /run (测点1) ----->|
  |                         |<-- OK + stdout -----------|
  |                         |  checker → case AC       |
  |                         |-- POST /run (测点2) ----->|
  |                         |<-- OK + stdout -----------|
  |                         |  汇总 verdict=AC         |
  |<-- JudgeResult ---------|                          |
```

单独测 sandbox 时，用 `curl` 打 `/blobs`、`/run` 即可。

---

## 8. 设计原则

1. **进程边界**：judge 编排，sandbox 执行；彼此只认 HTTP+JSON。
2. **模块边界**：一个 `go.mod` 方便共享 `contract`；不等于可以进程内乱调隔离实现。
3. **替换边界**：隔离细节藏在 `Container` 后面，便于 A→B→C 升级。
4. **学习边界**：复用参考项目的**思路**，命名与实现按场景自己写；先打通，再硬化。

---

## 9. 刻意不做

- Windows/macOS 上的「真沙箱」
- gRPC、WebSocket、FFI
- sandbox 里出现 WA
- MVP 的多程序管道、交互题、special judge
- 直接依赖 go-judge/go-sandbox 库

---

## 10. 和教程的关系

| 文档 | 回答什么问题 |
|---|---|
| [architecture.md](./architecture.md) | 整个 cherry-oj 系统怎么拼 |
| [data-model.md](./data-model.md) | PRD 对应的产品领域模型、版本关系与 Submission 快照 |
| **本文 engine.md** | engine 内部为什么这样设计、每层干什么；含一次判题的文件模型 |
| [`../tutorial/`](../tutorial/README.md) | 怎么搭建、按 M0→M2 分阶段实现与验收 |

先读设计建立地图；动手时只打开 `tutorial/` 对应阶段。
