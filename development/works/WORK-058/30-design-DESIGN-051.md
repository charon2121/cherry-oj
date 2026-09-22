---
id: "DESIGN-051"
type: "design"
title: "按信任与部署边界重切判题引擎模块结构"
status: "checked"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["CHANGE-014"]
related: ["DESIGN-043", "DECISION-035"]
implements: ["CHANGE-014"]
verifies: []
tags: []
created_at: "2026-09-14"
updated_at: "2026-09-22"
---

# DESIGN-051：按信任与部署边界重切判题引擎模块结构

## 背景

上游定义见 [CHANGE-014](10-change-CHANGE-014.md)。前序工作 [DESIGN-043](../WORK-049/30-design-DESIGN-043.md)
已按命令执行顺序整理了阅读主线与命名，本设计不重复那一层，只处理它显式排除的结构问题。

判断当前结构问题的根源：**现有包树按「这段代码是什么类型」切分**（api / pool / runner / container /
helper / launcher / store），这在 MVP 时期是合适的——那时只有「在宿主里起一个子进程」，不存在其他
维度。随后模块长出了三进程特权分离、节点控制面与部署清单校验，**包树没有跟着重切**，于是系统真正
的两条硬边界在包树中不可见：

1. **信任边界**：root 权限的 helper 服务端 与 非 root 的 sandbox 服务。
2. **部署边界**：三个独立部署、独立配置、独立启动的二进制。

本设计的核心动作只有一个：**按边界重切，并让编译器负责守边界**。

## 目标与限制

目标即 CHANGE-014 的 REQ-001 至 REQ-012。

限制：

- 不改变权限模型、隔离强度与进程数量（REQ-014）。
- 不改变对外 HTTP 契约与协议字节（REQ-013）。
- 保持单 Go module（REQ-015），理由见 [DECISION-035](40-decision-DECISION-035.md) 决定四。
- 每个提交必须能独立编译，且 WORK-050 固化的 CI 回归保持通过。

## 整体方案

Go 的 `internal/` 可见性规则是编译期强制的：`a/b/internal/c` 只能被以 `a/b` 为根的子树引用。把三个
二进制各自放进一棵子树，其实现细节放进该子树的 `internal/`，信任边界与部署边界就成为构建约束。

每个服务对外只导出一个入口函数，`cmd/*` 退化为参数解析：

```text
apps/judge-engine/
├── cmd/
│   ├── judge/main.go            # flag 解析 → judge.Run(ctx, cfg)
│   ├── sandbox/main.go          # flag 解析 → sandbox.Run(ctx, cfg)
│   └── sandbox-helper/main.go   # launcher.Dispatch() → helperd.Run(ctx, cfg)
│
├── internal/                    # 全模块共享；不含判题或执行的业务判断
│   ├── contract/                # 跨进程 DTO，对应 contracts/*.json
│   ├── hostexec/                # 本机执行协议：帧编解码、Request、Result、Reason
│   │   └── client/              # Call：非特权侧客户端，只依赖 hostexec
│   └── platform/                # slog、tracecontext、配置装配原语
│       ├── logging/
│       ├── tracing/
│       └── config/              # 泛型 Load，不认识任何具体配置字段
│
├── judge/
│   ├── judge.go                 # package judge：func Run(ctx, Config) error
│   ├── config.go                # judge 自己的 Config / Default / Validate
│   ├── doc.go                   # 职责与引用边界
│   └── internal/{api,flow,checker,language,testcase,sandboxclient,node/*}
│
├── sandbox/
│   ├── sandbox.go               # func Run(ctx, Config) error
│   ├── config.go
│   ├── doc.go
│   └── internal/{api,pool,runner,workspace,backend,store}
│
└── helperd/
    ├── helperd.go               # func Run(ctx, Config) error
    ├── config.go
    ├── doc.go
    └── internal/{server,execution,cgroup,launcher,policy,trust,recovery}
```

由此获得的编译期保证（对应 REQ-001）：

| 约束 | 当前依靠 | 重切后依靠 |
|---|---|---|
| 非特权 sandbox 不得链接特权服务端实现 | README 中的一段说明 | `sandbox` 引用 `helperd/internal/*` 即编译失败 |
| judge 不得进程内调用 sandbox 实现 | `engine.md` §1 的一段说明 | `judge` 引用 `sandbox/internal/*` 即编译失败 |
| helper 不得理解判题 | 铁律第七条 | `helperd` 引用 `judge/*` 即编译失败 |

`cmd/*` 位于服务子树之外，因此也无法引用服务内部包，服务生命周期编排必须收进服务包本身。当前
`cmd/sandbox/main.go` 的 151 行 `run()` 随之迁入 `sandbox.Run`。

## 模块与数据

### 依赖方向

```text
cmd/judge   → judge   → internal/{contract, platform}
cmd/sandbox → sandbox → internal/{contract, hostexec/client, platform}
cmd/sandbox-helper → helperd → internal/{contract, hostexec, platform}
                             → helperd/internal/launcher（进程角色分流，供 cmd 调用 Dispatch）
```

`internal/hostexec` 同时被 `sandbox`（经 `hostexec/client`）与 `helperd` 引用，是两端唯一的共享词汇，
承担 REQ-004：终止原因、请求与响应结构、帧编解码只在此定义一次。`helperd` 不引用 `hostexec/client`。

`launcher` 的归属需要说明：它包含 `Dispatch()`（必须由 `cmd/sandbox-helper` 在建立任何 goroutine 前
调用）与 init / exec 两个进程角色的实现。`Dispatch` 从 `helperd` 顶层再导出一层，使 `cmd` 不需要引用
`helperd/internal/launcher`。

### 三份配置（REQ-002）

`internal/platform/config` 只保留装配机制，不认识任何具体字段：

```go
type Validatable interface{ Validate() error }

// Load 按 默认值 → YAML → 环境变量 装配并校验。
// 文件不存在不是错误；YAML 中的未知字段是错误。
func Load[T Validatable](
	path, envPrefix string,
	defaults T,
) (T, error)
```

`judge.Config` / `sandbox.Config` / `helperd.Config` 各自定义字段、`Default()` 与 `Validate()`，与服务
实现同目录。现有 `Duration` 类型、反射式环境变量覆盖与 `KnownFields` 行为整体保留，只是从「认识
三段业务配置的包」变成「不认识任何业务配置的机制」。

### 配置、环境与身份三者分离（REQ-002 的读性部分）

当前 `cmd/judge` 在启动过程中把探测结果与注册身份回填进 `config.JudgeConfig`，`NodeConfig` 为此保留
两个 `yaml:"-"` 字段。重切后三者是三个值：

```go
cfg      judge.Config        // 加载后只读
env      node.Environment    // 探测得到的执行环境事实
identity node.Identity       // 由 cfg 与 env 推出的身份与指纹
```

`flow.Judge` 接收 `(cfg, identity.Fingerprint)`，不再接收一个可能已被回填过的配置值。

### 环境指纹的显式输入（REQ-003）

当前指纹输入是「`JudgeConfig` 整体挖掉五个字段后 JSON 序列化」，而该结构没有 JSON 字段名标注，摘要
实际依赖 Go 字段名与声明顺序。改为显式结构：

```go
// judge/internal/node/identity
// 只有此处列出的字段进入环境身份。新增判题策略需显式加入并更新基准测试。
type policyFingerprint struct {
    StrictWhitespace bool  `json:"strictWhitespace"`
    RevealExpected   bool  `json:"revealExpected"`
    ClockRatio       int64 `json:"clockRatio"`
    CompileCPUNs     int64 `json:"compileCpuNs"`
    CompileMemBytes  int64 `json:"compileMemoryBytes"`
    CompileClockNs   int64 `json:"compileClockNs"`
    StdoutMaxBytes   int64 `json:"stdoutMaxBytes"`
    StderrMaxBytes   int64 `json:"stderrMaxBytes"`
}
```

配固定输入的基准测试锚定摘要值。语言清单由 `language` 注册表遍历生成，不再只登记 `cpp`——当前
注册表含 cpp / python / java，而节点只向控制面声明 cpp。

### 执行后端：一次性调用（REQ-005）

现有 `container.Container` 用运行时错误表达四阶段时序协议，两个实现各自维护 `attempted`、`closed`、
`closeOnce`、`process` 等状态字段来守护它。改为单次调用，使「只能执行一次」由「不存在可复用对象」
保证：

```go
// sandbox/internal/backend
type Job struct {
    Command []string
    Env     []string
    Stdin   Source
    Inputs  []NamedSource
    Outputs []string
    Limits  contract.Limits
}

// Execute 执行一次命令，并在返回前完成全部资源回收。
// 产物经 sink 同步交付；返回 nil 即表示结果可以对外发布。
type Backend interface {
    Execute(ctx context.Context, j Job, sink OutputSink) (Facts, error)
}
```

连带影响：`pool` 回归纯容量管理。当前 `pool.Run` 内部在容器关闭失败时删除 store 中的产物引用——由
容量管理器承担产物回滚是职责错位，其存在原因是「容器关闭是最终回收边界」。该前提由 `Execute`
在内部满足后，回滚回到 `runner`，产物发布本来就在那里。

`workspace`（暂存根的独占锁与启动恢复）与 `backend`（执行）分成两个包：前者不理解执行，后者不管理
暂存根生命周期。

### 结论推导：事实与回收分离（REQ-006）

当前单次执行的结论散布在 `supervise` 与 `finish` 对同一批字段（`result.Reason`、`runErr`、
`initReportLost`、`cleanupErr`、`state`）的顺序敏感读写中，其中包含撤销先前结论的分支。资源回收必须
是命令式的（顺序由内核决定），但判断不必：

```go
// helperd/internal/execution

// Facts 是一次执行结束后收集到的全部客观事实，写完即不可变。
type Facts struct {
    Handshake HandshakeOutcome // ready / 启动超时 / 协议阶段错
    Payload   ExitFacts        // exitCode、signal、execFailed
    Init      InitFacts        // 是否报告退出事实、是否提前失联
    Group     cgroup.Snapshot  // 停组确认后的最终计量
    Budget    contract.Limits
    Wall      time.Duration
    Output    OutputFacts
    Cancelled bool
    Platform  error            // 平台故障，非用户程序原因
}

// Conclude 不做输入输出、不改写任何状态、不依赖调用顺序。
func Conclude(f Facts) (hostexec.Reason, error)
```

`finish` 收缩为：停组 → 等待 → 收集 Facts → `Conclude` → 交付。撤销先前结论的那一段成为 `Conclude`
内部一个有名字的分支（本任务 OOM 杀死 init，最终计量可解释丢失的退出报告）。`executionState` 的六个
状态配一张显式转移表，非法转移直接报错。

该分离的主要收益是可测性：当前这段判断只能通过整台执行机器间接触发且需要 Linux，之后是一张
`Facts → Reason` 的表驱动测试，可在 macOS 上运行。Linux 上的内核行为验证不被替代。

### 节点能力拆分（REQ-007）

```text
judge/internal/node/
├── registry/   # 控制面注册与心跳
├── install/    # 安装 HTTP 端点与测例安装事务
├── identity/   # 身份与指纹
└── probe/      # 环境探测，复用 judge/internal/sandboxclient
```

`probe` 复用既有 sandbox 客户端，删除模块内第三个 HTTP 客户端。

部署清单校验改变思路：**代码校验「清单声明与现实是否一致」，不硬编码「清单应该长什么样」**。
宿主路径与期望值从清单本身读取（它已经是 root 管理的可信文件）；`len(manifest.Limits) != 24` 这类
数量断言换成覆盖性断言——清单声明的每条 required group 都必须被校验，每条被校验的都必须在清单中
有声明，不一致时指出是哪一条；sandbox 端点的字面量比较换成配置项加「必须是回环地址」的校验。

### 后端命名与安全默认（REQ-011）

`trusted-host` 更名 `devhost` 并移入 `sandbox/internal/backend/devhost`。它在隔离轴上什么都不做，
`Limits` 只被事后比较而非强制，当前命名与 `Spec.Limits` 的存在会让人误以为它执行了限额。`sandbox.Run`
在配置未显式声明允许不安全后端时拒绝启动。

## 接口与状态

对外 HTTP 契约（`/judge`、`/run`、`/blobs`、`/version`、节点安装与控制面协议）与本机执行协议的线格式
保持不变（REQ-013）。变化的是模块内部接口：

| 接口 | 变化 |
|---|---|
| `container.Container` | 由四阶段时序协议改为 `backend.Backend` 的一次性 `Execute` |
| `container.Reason` | 删除，统一使用 `hostexec.Reason` |
| `pool.Pool` | 不再承担产物回滚；仍负责名额、排队与关闭 |
| `config.Config` | 拆为三份服务配置 + 泛型 `Load` |
| `helper.Call` | 迁至 `internal/hostexec/client`，签名不变 |
| `helper.Serve` | 迁至 `helperd`，由 `helperd.Run` 承接 |
| `node.ProbeEnvironment` | 不再返回被回填的配置，改为返回 `node.Environment` |

执行状态机由隐式字段赋值改为显式转移表，取值集合不变。

## 安全与失败

信任边界从文档约定升级为构建约束，这是本设计的主要安全收益，但它**不改变运行期的权限模型**：
helper 仍以 root 运行并持有全部特权操作，sandbox 仍非 root，socket 的 `SO_PEERCRED` 与文件权限双重
约束不变，seccomp 策略与身份分槽不变（REQ-014）。

失败处理不变量：

- 回收不确认即不发布产物；`Execute` 返回 nil 是发布前提。
- 无法确认回收时停止接单，保留无法判定归属的资源以供检查，不递归删除。
- 清理动作使用独立期限，不复用已取消的请求上下文。
- 未知终止原因按最严重处理；REQ-004 的覆盖测试保证新增取值不会落入默认分支而无人察觉。

跨层预算断言（REQ-008）属于失败预防：当前 `WriteTimeout 160s > sessionTimeout 150s`、
`judge.sandboxTimeout > sandbox 端单次 /run 最长耗时` 这两条关系只写在 README 段落里，配置校验不检查。
配错的表现是「沙箱正常执行、judge 自己先超时」，报出来是 SE 且难以定位。改为启动时拒绝。

## 监控与部署

日志字段与 trace 传播不变。错误消息统一为英文（REQ-009）后，沙箱与判题两侧可用同一套检索表达式；
既有针对中文消息的检索表达式会失效，需在切换时同步更新。

部署上只有一个动作：本次结构调整会改变环境指纹（配置结构变化 + 判题二进制摘要变化）。按
`docs/architecture.md` §3.5 的节点协议，新指纹只会以 `REGISTERED` 出现，必须由人显式切换 `ACTIVE`，
旧回执按既有规则保留（REQ-012）。用户已确认接受一次全量轮换，见 DECISION-035 决定一。

## 迁移与兼容

分阶段顺序、每阶段的验证与回退方式见 [PLAN-041](50-plan-PLAN-041.md)。原则：

- 每个提交独立可编译，CI 回归保持通过；不存在「中间态不可构建」的提交。
- 先做纯移动与去重（协议归位），再做行为相关变更（配置拆分），最后做接口重写与逻辑重组。
- helper 客户端与服务端始终在同一提交内保持配套，不出现跨版本混用（REQ-015）。
- 环境指纹轮换集中在配置拆分那一个阶段发生，便于与节点切换动作对齐，不在多个阶段反复轮换。

## 备选方案

**备选一：拆成多个 Go module**（`judge-engine/judge`、`judge-engine/sandbox`、`judge-engine/helperd`）。
能得到同样的引用隔离，且隔离更强（跨 module 需显式 require）。不采用：README 已明确 helper 客户端与
服务端必须按同一构建批次交付，多 module 会引入版本偏斜这一真实风险，而 `internal/` 已提供同等的
编译期保证，代价小得多。详见 DECISION-035 决定四。

**备选二：保留现有包树，仅靠 lint 或架构测试拦截跨边界引用。** 改动量最小。不采用：这仍然是「靠
额外机制记住规则」，与现状的区别只是把人换成脚本；且无法解决配置耦合、接口时序协议、结论散布这些
与目录无关的问题——那些才是「读起来困难」的直接来源。

**备选三：只做可读性改造（拆 `helper`、拆 `node`、提取 `Conclude`），不动目录结构。** 能解决大部分
阅读成本。不采用：它恰好不解决 CHANGE-014 认定的根因——信任边界不可见。拆过的包仍可被非特权服务
引用，下一次增长会重新长回来。

## 风险与重审条件

| 风险 | 表现 | 处置 |
|---|---|---|
| 大范围移动误改 FD 顺序、协议映射或释放时机 | macOS 上不可见，Linux 回归才暴露 | 阶段一为纯移动+去重，逐包搬运并在每步跑 WORK-050 的 CI |
| 一次性调用接口重写取消与交付时序 | 残留进程、挂载或临时文件 | AC-005 的四条路径回归；保留现有取消路径的测试 |
| 提取 `Conclude` 时丢失未被测试覆盖的分支 | 某类失败被判成错误结论 | 先补齐 `Facts → Reason` 表驱动测试再搬运，以重构前行为为基准 |
| 指纹轮换与节点协议配合不当 | 已部署测试数据回执失效而无人察觉 | AC-012 验证新指纹为 `REGISTERED`，切换由人执行 |
| 只做机械移动、不收敛职责 | 同样难读的代码 + 一次无收益的大 diff | AC-011 要求按重写后的文档实走源码 |

重审条件：

- 若未来需要 sandbox 与 helper 分别发版（不再同批次交付），备选一（多 module）应重新评估。
- 若增加第二种隔离后端（非 helper 链路），`backend.Backend` 的一次性调用形态需重新确认是否仍合适。
- 若节点支持的语言超出单一工具链，指纹输入结构需重新划定范围。

## 变更记录

- 2026-09-22：按独立复核追加修正：pool 消费 runner 的一次性 Run 接口，执行依赖由装配层绑定；
  输入资源关闭与取消回调撤销分离；后端的最终回收失败必须通过返回错误通知 runner/pool。
  探测复用客户端同时保留禁止重定向、16KiB 响应边界和完整 JSON 校验。调用超时仍能影响结果，
  因此显式加入 policyFingerprint；校验编译与请求墙钟冲突，但不宣称可静态证明跨进程总耗时。
  以上为用户明确授权的 R1–R10 修复，不改变外部协议、语言声明或权限模型。
- 2026-09-14：状态变更：draft → review。原因：完成目标结构、依赖方向、接口与迁移方案，提交人工审核
- 2026-09-14：结构与内容校验通过，由工具置为 checked。
