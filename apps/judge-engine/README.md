# judge-engine 源码阅读入口

先读一次 `POST /run`。判题、节点注册和安装分别有自己的入口；单次命令执行不依赖读者先理解它们。

## 一条命令从进入到返回

| 阶段 | 下一步读哪里 | 返回或交接意味着什么 |
|---|---|---|
| 接收请求 | [api/run.go](internal/sandbox/api/run.go) `handleRun` → [pool/pool.go](internal/sandbox/pool/pool.go) `Run` | 校验并等待执行名额，尚未启动命令 |
| 准备输入 | [runner/runner.go](internal/sandbox/runner/runner.go) `Run` | validateRequest → prepareInputs，准备输入源后调用 Container.Start |
| 请求隔离执行 | [container/isolated.go](internal/sandbox/container/isolated.go) `Start` → `execute` → [helper/client.go](internal/sandbox/helper/client.go) `Call` | Start 返回在途句柄；输入上传和响应读取并行 |
| 接收本机请求 | [helper/server_linux_amd64.go](internal/sandbox/helper/server_linux_amd64.go) `Serve` → `service.run` → `service.serveConn` | 认证对端、取得 helper 槽位并读取命令 |
| 启动隔离环境 | [helper/execution.go](internal/sandbox/helper/execution.go) `execution.Run` → [process_linux_amd64.go](internal/sandbox/helper/process_linux_amd64.go) `isolatedProcess.Start` | 建立 cgroup、启动可信 init 进程以及输入、输出、控制消息任务 |
| 启动 payload | [launcher/dispatch_linux_amd64.go](internal/sandbox/launcher/dispatch_linux_amd64.go) `Dispatch` → [init_linux_amd64.go](internal/sandbox/launcher/init_linux_amd64.go) `initSession.run` → [payload_linux_amd64.go](internal/sandbox/launcher/payload_linux_amd64.go) `startPayload` | init 准备文件环境，启动同一二进制的 exec 角色；用户命令仍未放行 |
| 最终执行 | [launcher/exec_linux_amd64.go](internal/sandbox/launcher/exec_linux_amd64.go) `RunExecStage` | 设置权限和过滤策略，完成握手后 execve 用户命令 |
| 等待和监督 | init 的 `reportExit`；[helper/supervise.go](internal/sandbox/helper/supervise.go) `supervise` | 命令退出，或达到超时、取消、资源终止条件 |
| 回收执行环境 | [helper/cleanup.go](internal/sandbox/helper/cleanup.go) `finish` → `isolatedProcess.Wait` → `collectArtifacts` → 关闭环境 | 停止整组、等待 init 和 I/O、持有受控产物 FD、释放环境；失败不能发布成功 |
| 交付结果 | helper 的 `serveConn` → [delivery.go](internal/sandbox/helper/delivery.go) `WriteFiles` / `Close` | 写元数据和文件流，关闭产物 FD 后发 Completion；归还槽位后关闭连接 |
| 返回 HTTP | `Call` → isolated 的 `Wait` → runner 的 `collect` / `input.close` → Pool 的 `closeContainer` → `handleRun` | Call 还要等正常 EOF；Pool 确认 Container.Close，必要时回滚新 ref，最后写响应 |

主线代码保留每层的正常完成步骤。runner 的请求校验在 `request.go`，输入在 `input.go`，执行事实归类在 `result.go`，产物收集和回滚在 `output.go`。容器的 `isolated.go` 保留 Start → execute → Wait → Close，文件暂存与读取放在 `isolated_files.go`。

先读 [execution.go](internal/sandbox/helper/execution.go) 的类型和 `Run`：它拥有本次资源组、预算、生命周期与最终结论。`service.serveConn` 只创建执行、调用 Run、交付结果；进程通信和 I/O 由 `isolatedProcess` 拥有，产物移交给 `executionResult` 后不再归 execution 关闭。

[isolation_plan.go](internal/sandbox/helper/isolation_plan.go) 是固定策略与请求的只读快照。P3 的 `isolatedProcess.startInit` 在创建 P4 时设置 namespace 和 cgroup FD；P4 的 [rootFilesystem.Prepare](internal/sandbox/launcher/rootfs_linux_amd64.go) 完成只读根、工作区、proc/dev 挂载和 pivot_root，再由 initSession 启动 P5。P5 的最终 execve 替换自身，不增加一个进程。正常执行对应三个常驻服务与两个临时进程，用户程序的后代另计。

正常完成时，沿表格读到 HTTP 返回。墙钟超时时，从 `supervise` 的 wall 分支进入同一个 `finish`，runner 归类为 TLE，跳过产物收集，再关闭输入和容器。请求取消时，客户端会主动断开连接并等待本地上传结束；helper 感知断连后使用独立清理期限停止执行组，槽位要等 `serveInSlot` 收尾后才归还。取消的客户端不保证还能收到 Completion，不能用客户端的错误返回推断远端回收已完成。

## 三个执行角色为什么来回握手

HTTP sandbox 使用非特权身份；helper 承担建立隔离环境所需的特权操作。init 与 exec 是 helper 二进制
重新启动后的不同进程角色，入口由 `Dispatch` 选择。这个进程边界承担权限隔离，Go 包本身不提供该隔离。

实际顺序是：exec 写 `READY` → init 等到它并完成自身权限设置 → init 写 `ready` 事件 →
helper 检查状态和预算并写 `GO` → init 转发 `GO` → execve。最终 exec 线程持续锁定，不能在过滤器安装后随意加入 Go 调用。

FD、握手字节和失败阶段的两端映射见 [startup_protocol.go](internal/sandbox/launcher/startup_protocol.go)。
请求预算见 [launcher/protocol.go](internal/sandbox/launcher/protocol.go)，响应帧和会话期限见
[helper/protocol.go](internal/sandbox/helper/protocol.go)。同值不代表同一约束；实际 rlimit、验证上界与缺省预算分别归属。

## 从判题进入这条执行链

[judge/api/judge.go](internal/judge/api/judge.go) `handleJudge` → [flow/flow.go](internal/judge/flow/flow.go) `Judge`：
每次 Judge 创建独立的 `judgment`：`prepare` 加载测例和上传源码 → [compile.go](internal/judge/flow/compile.go) 的 `compile` → [case.go](internal/judge/flow/case.go) 的 `runCase` → [result.go](internal/judge/flow/result.go) 比较和汇总 → `close` 按编译产物、源码的顺序清理引用。
其中 `Sandbox.Run` 通过 HTTP client 调用上面的 `/run`，flow 无需处理 helper 的 FD 或权限。


## 节点与配置的旁路

[node/node.go](internal/judge/node/node.go) 持有服务锁与注册信息；[identity.go](internal/judge/node/identity.go) 计算身份，[control.go](internal/judge/node/control.go) 管理注册和心跳，[api.go](internal/judge/node/api.go) 校验安装请求。每次 `Install` 在节点锁内创建独立的 [installation](internal/judge/node/install.go)，负责 staging、归档验证和原子提交。环境身份仍从原 environment/deployment 分支取得。

配置值定义与加载入口在 [config.go](internal/config/config.go)，默认策略在 [defaults.go](internal/config/defaults.go)，原有启动校验在 [validation.go](internal/config/validation.go)。[contract/limits.go](internal/contract/limits.go) 按具名字段同步值和显式提供位，缺省与显式零仍分别处理。
