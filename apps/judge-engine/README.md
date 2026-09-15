# judge-engine 源码阅读入口

三个二进制、两条硬边界；为什么这样切见 [docs/engine.md](../../docs/engine.md)，
各子树可以被谁引用见各自的 `doc.go`。

先读一次 `POST /run`。判题、节点注册和安装分别有自己的入口；单次命令执行不依赖读者先理解它们。

## 一条命令从进入到返回

| 阶段 | 下一步读哪里 | 返回或交接意味着什么 |
|---|---|---|
| 接收请求 | [api/run.go](sandbox/internal/api/run.go) `handleRun` → [pool/pool.go](sandbox/internal/pool/pool.go) `Run` | 校验并等待执行名额，尚未启动命令 |
| 准备输入 | [runner/runner.go](sandbox/internal/runner/runner.go) `Run` → [request.go](sandbox/internal/runner/request.go) / [sources.go](sandbox/internal/runner/sources.go) | 归一化限额、打开输入源，组装成一个 `backend.Job` |
| 请求隔离执行 | [backend/isolated.go](sandbox/internal/backend/isolated.go) `Execute` → [hostexec/client/client.go](internal/hostexec/client/client.go) `Call` | 一次调用覆盖整次执行；返回即代表回收完成 |
| 接收本机请求 | [helper/server_linux_amd64.go](helperd/internal/helper/server_linux_amd64.go) `Serve` → `service.run` → `service.serveConn` | 认证对端（文件权限 + SO_PEERCRED）、取得槽位并读取命令 |
| 启动隔离环境 | [helper/execution.go](helperd/internal/helper/execution.go) `execution.Run` → [process_linux_amd64.go](helperd/internal/helper/process_linux_amd64.go) `isolatedProcess.Start` | 建立 cgroup、启动可信 init 进程以及输入、输出、控制消息任务 |
| 启动 payload | [launcher/dispatch_linux_amd64.go](helperd/internal/launcher/dispatch_linux_amd64.go) `Dispatch` → [init_linux_amd64.go](helperd/internal/launcher/init_linux_amd64.go) `initSession.run` → [payload_linux_amd64.go](helperd/internal/launcher/payload_linux_amd64.go) `startPayload` | init 准备文件环境，启动同一二进制的 exec 角色；用户命令仍未放行 |
| 最终执行 | [launcher/exec_linux_amd64.go](helperd/internal/launcher/exec_linux_amd64.go) `RunExecStage` | 设置权限和过滤策略，完成握手后 execve 用户命令 |
| 等待和监督 | init 的 `reportExit`；[helper/supervise.go](helperd/internal/helper/supervise.go) `supervise` | 命令退出，或达到超时、取消、资源终止条件 |
| 判定结论 | [helper/conclusion.go](helperd/internal/helper/conclusion.go) `conclude`；转移表在 [state.go](helperd/internal/helper/state.go) | 纯函数：一组事实进去，一个结论出来，不掺 I/O |
| 回收执行环境 | [helper/cleanup.go](helperd/internal/helper/cleanup.go) `finish` → `isolatedProcess.Wait` → 关闭环境 | 停止整组、等待 init 和 I/O、持有受控产物 FD、释放环境；失败不能发布成功 |
| 交付结果 | helper 的 `serveConn` → [delivery.go](helperd/internal/helper/delivery.go) `WriteFiles` / `Close` | 写元数据和文件流，关闭产物 FD 后发 Completion；归还槽位后关闭连接 |
| 返回 HTTP | `Call` → `Isolated.Execute` 的 `deliver` → runner 的 [collector.go](sandbox/internal/runner/collector.go) → `handleRun` | Call 还要等 Completion 之后的正常 EOF；产物在「回收已完成」之后才逐个交付 |

主线代码保留每层的正常完成步骤。runner 的请求校验在 `request.go`，输入在 `sources.go`，
执行事实归类在 `result.go`，产物收集在 `collector.go`，输出截断在 `capwriter.go`。

**`backend.Execute` 是一次性的**：没有可复用的执行环境对象，因此也没有 `Reset()`、没有容器池。
「只能执行一次」由「不存在可复用对象」保证，而不是由一组状态字段守护——
接口形状与理由写在 [backend/backend.go](sandbox/internal/backend/backend.go) 的包注释里。

先读 [execution.go](helperd/internal/helper/execution.go) 的类型和 `Run`：它拥有本次资源组、预算、
生命周期与最终结论。`service.serveConn` 只创建执行、调用 Run、交付结果；进程通信和 I/O 由
`isolatedProcess` 拥有，产物移交给 `executionResult` 后不再归 execution 关闭。

[isolation_plan.go](helperd/internal/helper/isolation_plan.go) 是固定策略与请求的只读快照。
P3 的 `isolatedProcess.startInit` 在创建 P4 时设置 namespace 和 cgroup FD；
P4 的 [rootFilesystem.Prepare](helperd/internal/launcher/rootfs_linux_amd64.go) 完成只读根、工作区、
proc/dev 挂载和 pivot_root，再由 initSession 启动 P5。P5 的最终 execve 替换自身，不增加一个进程。
正常执行对应三个常驻服务与两个临时进程，用户程序的后代另计。

正常完成时，沿表格读到 HTTP 返回。墙钟超时时，从 `supervise` 的 wall 分支进入同一个 `finish`，
`conclude` 判成超时，runner 归类为 TLE。请求取消时，客户端会主动断开连接并等待本地上传结束；
helper 感知断连后使用独立清理期限停止执行组，槽位要等 `serveInSlot` 收尾后才归还。取消的客户端
不保证还能收到 Completion，不能用客户端的错误返回推断远端回收已完成。

> `finish` 里有一处**顺序**不能动：`ctx.Err()` 必须在 `CancelInput()` **之前**读。
> `CancelInput` 的实现可以取消调用方自己的上下文（启动冒烟正是这样接线的），
> 读晚一步会让每次正常执行都被判成已取消。回归用例是
> `TestCancelInputMustNotMakeNormalRunLookCancelled`。

## 三个执行角色为什么来回握手

HTTP sandbox 使用非特权身份；helperd 承担建立隔离环境所需的特权操作。init 与 exec 是 helper 二进制
重新启动后的不同进程角色，入口由 `Dispatch` 选择。这个进程边界承担权限隔离，Go 包本身不提供该隔离
——但**包边界提供另一半**：`sandbox/` 子树在编译期就 import 不到 `helperd/internal/`。

实际顺序是：exec 写 `READY` → init 等到它并完成自身权限设置 → init 写 `ready` 事件 →
helper 检查状态和预算并写 `GO` → init 转发 `GO` → execve。最终 exec 线程持续锁定，不能在过滤器安装后随意加入 Go 调用。

FD、握手字节和失败阶段的两端映射见
[startup_protocol.go](helperd/internal/launcher/startup_protocol.go)。
请求预算、响应帧与会话期限都在
[internal/hostexec](internal/hostexec)：请求侧见 [protocol.go](internal/hostexec/protocol.go)，
响应侧见 [result.go](internal/hostexec/result.go)。同值不代表同一约束；实际 rlimit、验证上界与缺省预算分别归属。
线格式由 [wire_test.go](internal/hostexec/wire_test.go) 的黄金用例钉住：字段名即协议，改名等于改协议。

## 从判题进入这条执行链

[judge/api/judge.go](judge/internal/api/judge.go) `handleJudge` →
[flow/flow.go](judge/internal/flow/flow.go) `Judge`：
每次 Judge 创建独立的 `judgment`：`prepare` 加载测例和上传源码 →
[compile.go](judge/internal/flow/compile.go) 的 `compile` →
[case.go](judge/internal/flow/case.go) 的 `runCase` →
[result.go](judge/internal/flow/result.go) 比较和汇总 → `close` 按编译产物、源码的顺序清理引用。

`flow.Sandbox` 这个接口声明在 flow 自己这里，实现是
[sandboxclient](judge/internal/sandboxclient)，通过 HTTP 调用上面的 `/run`。
flow 无需处理 helper 的 FD 或权限，判题逻辑的单测也不需要启动任何沙箱。

## 节点与配置的旁路

[node/node.go](judge/internal/node/node.go) 只做装配，具体职责分在五个包里：

| 包 | 负责什么 |
|---|---|
| [identity](judge/internal/node/identity) | 环境指纹怎么算。输入是一份**显式结构体**，不是整份配置的摘要 |
| [probe](judge/internal/node/probe) | 探测执行环境；原生部署的合格标准在 [deployment_spec.go](judge/internal/node/probe/deployment_spec.go) |
| [registry](judge/internal/node/registry) | 向 judging-service 注册与心跳 |
| [install](judge/internal/node/install) | 持有数据根独占锁，staging、归档验证与原子提交 |
| [wire](judge/internal/node/wire) | 严格 JSON 解码（拒绝未知字段与尾随内容） |

各服务的配置跟着服务走：[judge/internal/config](judge/internal/config)、
[sandbox/config.go](sandbox/config.go)、[helperd/config.go](helperd/config.go)；
共用的加载机制（默认值 → YAML → 环境变量）在
[internal/platform/config](internal/platform/config)。
[contract/limits.go](internal/contract/limits.go) 按具名字段同步值和显式提供位，缺省与显式零仍分别处理。

跨层期限的顺序断言在 [sandbox/budget.go](sandbox/budget.go)：
HTTP 写期限 > 会话期限 > 单次执行墙钟硬界。顺序错了症状会极具误导性，理由见 docs/engine.md §8。
