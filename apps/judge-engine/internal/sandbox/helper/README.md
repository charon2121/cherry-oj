# 特权 helper 的本地接口

此组件属于 WORK-048 / TASK-096，尚未通过 Linux 隔离验收。现有 sandbox/runner 未接入它。

`cmd/sandbox-helper` 以 `CGO_ENABLED=0 GOOS=linux GOARCH=amd64` 构建，使用 `--config /absolute/path/helper.json`。配置文件及祖先路径必须 root 所有且非 root 不可写；不支持 setuid 安装。示意配置（账号、摘要及 cgroup 路径必须来自独立节点部署，不能直接启动）：

```json
{
  "SocketPath": "/run/cherry-sandbox-helper/helper.sock",
  "StateDir": "/run/cherry-sandbox-helper",
  "JobsDir": "/sys/fs/cgroup/cherry-sandbox-helper.service/jobs",
  "RootFS": "/opt/cherry-sandbox/rootfs-version/rootfs",
  "ManifestPath": "/opt/cherry-sandbox/rootfs-version/manifest.json",
  "ManifestSHA256": "由实际构建输出填写的64位摘要",
  "ServiceUID": 61001,
  "ServiceGID": 61001,
  "PayloadUID": 61002,
  "PayloadGID": 61002,
  "InitUID": 61003,
  "InitGID": 61003,
  "Parallelism": 1
}
```

部署须先准备 root 所有、不可被其他用户写入的 state 和已开启 cpu/memory/pids 的空 jobs 内部节点，helper 本身位于兄弟 supervisor 叶子。账号数字只是示意，须核对不存在冲突。外层 systemd 必须限制总内存、CPU、任务数量，并使用 KillMode=control-group；具体安装及服务单元属于 TASK-099。

启动依次核验 rootfs 清单、取得进程独占锁、核对资源所有权标记、清理遗留资源，再使用真实隔离链运行只读 rootfs 中的 `true`。冒烟未通过不创建监听 socket。socket 为 root:ServiceGID / 0660，连接在解析请求前核验 SO_PEERCRED 的固定 ServiceUID；连接数受 Parallelism 限制，无内部排队。不得将此 Unix socket 暴露为公网代理。

内部 Go 协议由 launcher.Request 定义：4 字节大端长度 + 最多 64 KiB JSON 元数据，随后按 Inputs 顺序及 StdinBytes 交付原始字节流。文件总输入上限 64 MiB，文件条目最多 128，拒绝宿主路径、UID、策略、挂载参数和用户传入 FD。参数使用已归一化的 Limits；CPU/墙钟/内存/进程零预算由上层 TASK-097 处理。元数据 5 秒期限；后续连接总期限 150 秒。客户端不主动半关闭写端，断线或多余数据取消执行。

返回有界 Result 事实帧，随后按 Outputs 顺序流式交付最多 64 MiB 产物。产物 FD 全部关闭后才发送 Completion 尾帧；缺尾帧不能认定交付成功。helper.Call 接管输入 ReadCloser，其 Close 必须可解除读取阻塞；产物消费回调同步处理受限 reader。没有 RunResult 判题/状态映射、blob 持久化或旧 pool 复用。

接线层应先暂存产物，Call 成功后发布；返回错误时清理暂存内容。分发 helper 时须携带 Go 标准库及 golang.org/x/sys 的相应许可，具体交付包由 TASK-099 完成。

可信 init 的固定 FD：3 为 seqpacket 控制通道，4 为配置和输入管道，5 为 helper 存活管道的读端。外部 helper 是存活管道唯一写端，断开使 PID 1 退出。工作目录 FD 仅通过控制通道传给 helper，并设置接收端 CLOEXEC。payload re-exec 的 FD 3/4/5 分别重映射为配置、READY/GO、执行错误管道；最终 exec 仅保留标准输入输出。PID 1 与 payload 使用不同身份，准备、降权、过滤失败均退出。

结束顺序：停止 cgroup → 等待 populated=0/最终计量 → reap 可信直接子进程并结束 I/O → 基于保留目录 FD 读取产物 → 关闭目录/控制 FD → 删除 cgroup 和宿主空挂载目录 → 交付产物并关闭其 FD → Completion。所有挂载只存在于私有 namespace，helper 不进入该 namespace；进程结束和最后一个 FD 关闭后由内核释放。任何不能确认的回收错误都停止接单，保留无法确认归属或安全删除的资源以便检查；不递归清空系统目录。

启动恢复只认识独占 state 目录中的 owner-v1/lock/socket 和 run-随机名空目录，以及专用 jobs 下的同类叶子。无标记而存在旧组、未知目录、嵌套组、错误所有权或清理失败均拒绝启动。SIGKILL/机器故障仍需 systemd 与重启恢复托底；应用代码不能保证内核永久阻塞时的即时回收。

本地 tests 验证协议、输入输出边界、取消与尾帧；Linux 文件/SCM_RIGHTS/失败清理测试需在 Linux 运行。1000 次回收、并发、故障注入、真实 C++ 和资源误差统一由 TASK-098 提供运行证据。

## 结构与所有权

原有 sandbox 仍使用 `api → pool → runner → Container`，Store 独立管理文件引用。此处只实现特权执行部分。当前一次性输入/指定输出协议尚未对齐 Container 的文件生命周期；TASK-097 接线前必须调整，不能要求 runner 再实现一套流程。

- `execute_linux_amd64.go` 创建 Linux 进程、启动 I/O、监督资源和处理握手事件。
- `execution.go` 持有一次执行的状态，显式完成 Stop、等待、受控产物打开和最终释放。执行失败保留为本次平台错误；回收失败额外返回 error，让服务停止接单。
- `file.go` 管理 FD 的一次性关闭；重复/并发收尾返回首次关闭结果，后台读写不依赖可变的裸指针。
- `delivery.go` 独占产物 FD。公开 `Result` 只有事实数据，客户端不取得宿主文件句柄。
- launcher 的 `initSession` 管理配置、payload 启动、降权后的放行和退出报告；传递阶段与 errno。控制和存活通道保持到 PID 1 退出，其余文件显式登记/释放。

单次执行的固定监督策略集中在 execution.go；请求中的资源预算与这些安全策略分开。部署侧可调参数仍须在 TASK-099 明确范围，不能让客户端修改特权策略。

本地生命周期故障测试可直接在 macOS 上运行，覆盖独立清理上下文、停止/等待/关闭失败、部分产物打开失败、输入取消及 FD 所有权。它们测试控制逻辑，不取代 Linux 内核行为验证。
