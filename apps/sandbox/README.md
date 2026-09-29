# sandbox 执行器

一次性的 C 程序，每次调用在隔离环境里执行一条命令，输出一行 JSON 事实后退出。
以 setuid-root 安装（`root:cherry-sandbox 4754`），只有 sandbox HTTP 服务所在的组能执行；其他人只能读（judge 启动自检要核对它的摘要）。
它不理解判题：编译、比对与 verdict 都在 judge。

```text
sandbox --box N
```

## 进程结构

```text
sandbox（宿主侧，root）   校验配置与请求 → 锁定 box → 建执行组 → clone(6 个 namespace)
  └─ init（PID 1）        挂载 rootfs、/work、/tmp、/proc、/dev → pivot_root → 复制输入 → fork → 降权
       └─ 用户程序        rlimit → 降到 payload 身份 → seccomp → execve
sandbox 监督墙钟、CPU、输出与取消 → 整组 kill → 最终计量 → 取回产物 → 输出一行 JSON
```

进程之间没有握手，只有三样东西：execve 前失败时写入的 CLOEXEC 错误管道（execve 成功即关闭，
这就是计时起点）、init 报告用户程序退出的管道，以及退出码。

## 能力

setuid 取得的能力不超过调用方（sandbox 服务单元）的能力边界集。执行器需要其中 8 项：
`SYS_ADMIN`、`SETUID`、`SETGID`、`SETPCAP`、`CHOWN`、`DAC_OVERRIDE`、`MKNOD`、`KILL`。

`KILL` 不用于正常执行，而是兜底：init 设了 `PDEATHSIG`，执行器意外死亡时整个 namespace 随之终止。
内核按普通 kill 的权限投递这个信号，发送方（root）与 init 身份不匹配，没有 `CAP_KILL` 信号会被静默
丢弃。所以边界集缺少 `KILL` 时执行器拒绝执行。内核在凭据变化时会清掉 `PDEATHSIG`，init 降权后会
重新设置。

## 受信配置

编译进二进制的路径 `/etc/cherry-sandbox/executor.conf`，文件与每一级祖先都必须属于 root 且不可被
组或其他人写入。每行 `key=value`：

| 键 | 含义 |
|---|---|
| `rootfs` | 只读根文件系统，需预建 `work`、`tmp`、`proc`、`dev`、`.oldroot` 目录 |
| `boxes` | box 根目录，属于服务身份 |
| `cgroup` | 委派给服务的 jobs 子树，已启用 cpu、memory、pids |
| `box_count` | box 数量，1 到 4 |
| `service_uid` / `service_gid` | 调用方身份 |
| `payload_uid` / `payload_gid` | box N 的用户程序身份为基数加 N |
| `init_uid` / `init_gid` | box N 的 init 身份为基数加 N |

## box 目录

调用方在 `<boxes>/<N>/` 下准备（目录与文件都属于服务身份）：

| 路径 | 内容 |
|---|---|
| `spec` | 请求，见下 |
| `stdin` | 标准输入 |
| `in/<序号>` | 输入文件，序号对应 spec 中 `input` 的顺序 |
| `out/` | 必须为空；执行器写回 `stdout`、`stderr` 与 `artifact-<序号>` |

执行器以 root 身份操作这个目录：路径中不跟随任何符号链接，只读取单链接、属于服务的普通文件，
写回只用 `O_EXCL` 新建并把所有者交还服务。同一 box 同时只允许一次执行（`flock`）。

## 请求

一串以 NUL 结尾的 `key=value` 记录。不用 JSON：setuid-root 程序里少一个解析器，就少一块攻击面。

| 键 | 数量 | 含义 |
|---|---|---|
| `arg` | 1..256 | argv；argv[0] 必须是裸命令名，先在 `/work`，再在 `/usr/bin`、`/bin` 解析 |
| `env` | 0..128 | `KEY=VALUE`，追加在固定的 `PATH`、`HOME`、`TMPDIR`、`LANG` 之后 |
| `input` | 0..128 | `<0\|1>:<工作区相对路径>`，1 表示可执行 |
| `output` | 0..128 | 执行后要取回的工作区相对路径 |
| `cpu_ns` `clock_ns` `memory_bytes` `max_processes` `stdout_max_bytes` `stderr_max_bytes` | 各 1 | 绝对限制；执行器只校验，不补默认值 |

## 输出与退出码

stdout 输出一行 JSON：`exitCode`、`signal`、`cpuNs`、`memoryBytes`、`clockNs`、`reason`
（`cpu`、`wall`、`output`、`cancelled`、`platform` 或空）、`oom`、`oomKill`、`memoryMaxEvents`、
`pidsMaxEvents`、`cancelled`、`outputExceeded`、`stdoutBytes`、`stderrBytes`、`outputs`、`error`。

| 退出码 | 含义 |
|---|---|
| 0 | 事实已输出，执行组已回收。`error` 非空表示平台故障，此时不交付产物 |
| 1 | 拒绝执行（配置、调用方、box 或请求不合格），什么都没有启动 |
| 2 | 执行组回收未确认，调用方必须停止接单 |

取消：调用方把一根管道接在 stdin 上，执行期间保持写端打开；关闭它（或调用方崩溃）即取消。

## 构建与测试

```text
make                                   # 需要 libseccomp-dev，静态链接
sudo python3 tests/run_tests.py --binary build/sandbox --probe build/probe
```

测试需要真实的 x86_64 Linux 内核并以 root 运行，会改动机器的用户、`/etc` 与 cgroup，
只在 CI 的一次性虚拟机上跑（`.github/workflows/sandbox-executor.yml`）。
