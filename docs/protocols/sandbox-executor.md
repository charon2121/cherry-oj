# 沙箱执行器协议

规定调用方如何让 `sandbox` 执行器在隔离环境里运行**一条命令**，以及如何读取结果。执行器不理解判题：它只报告命令怎么结束、用了多少资源。

## 快速使用

```sh
# 运行一条命令，stdin 来自文件
sudo python3 apps/sandbox/tools/sandbox_run.py --stdin in.txt -- cat

# 编译：源码放进工作区，取回产物 Main
sudo python3 apps/sandbox/tools/sandbox_run.py --put main.cpp --get Main --cpu 20s --wall 60s --mem 768m --procs 64 \
     -- g++ main.cpp -o Main -O2 -std=c++17

# 运行产物：Main:Main:x 表示放进工作区并设为可执行
sudo python3 apps/sandbox/tools/sandbox_run.py --put Main:Main:x --stdin in.txt --cpu 1s -- Main
```

工具退出码：`0` 正常结束，`1` 没有正常结束，`2` 请求被拒或环境有问题。它占用 box 0，judge 运行时不要同时用。
自己写调用方，按下面的协议做。

## 1. 参与方与传输

- 执行器是**一次性**的 C 程序：调用方 `exec sandbox --box N`，执行器执行一条命令，向 stdout 输出**一行 JSON 事实**后退出。
  没有常驻进程、socket 或网络。
- 调用方的约定只有四件事：`exec`、box 目录、一根保持打开的 stdin 管道、读一行 JSON。
- 「先编译再运行」是调用两次：第一次产出**产物**，第二次把它作为**输入**。
- 执行器不编译、不比对、不产生 verdict。

```text
调用方（服务身份）                         执行器（setuid-root）
 ├─ 写 box 目录：spec / stdin / in/<i>
 ├─ exec sandbox --box N，stdin 接管道  ──►  校验配置、身份、请求 → 隔离 → 执行 → 监督 → 整组回收
 ├─ 读 stdout 的一行 JSON               ◄──
 └─ 读 box 的 out/
```

平台：仅 Linux x86_64，需要 cgroup v2。

## 2. 命令行

```text
sandbox --box N
```

- 只有这一种形式。`N` 是整数，`0 ≤ N < box_count`；超出范围拒绝。
- 真实 uid 必须是服务身份或 root。执行器必须以 setuid-root 安装，且只有服务组可执行。
- **stdin 必须是管道，并在整个执行期间保持写端打开**（取消协议，§6）。
- 执行器不信任调用方的进程环境：清空环境变量、`umask 077`、关闭 0/1/2 以外的 fd。调用方以空环境启动它即可。

## 3. 受信配置

编译进执行器的固定路径 `/opt/cherry-oj/etc/executor.conf`，不能被环境变量或参数改变。
文件及其每级祖先目录必须属于 root、不被组或其他用户写入，路径中没有符号链接，否则拒绝使用。

每行 `key=value`，`#` 开头为注释，出现未知键即拒绝：

| 键 | 含义 |
|---|---|
| `rootfs` | 只读根文件系统目录；须预建 `work`、`tmp`、`proc`、`dev`、`.oldroot`，且不是符号链接 |
| `boxes` | box 根目录，属于服务身份 |
| `cgroup` | 委派给服务的 cgroup v2 子树，已启用 `cpu memory pids` |
| `box_count` | 同时执行的数量，1–256，由配置决定；每个 box 对应一个编号 N，同一时刻最多 `box_count` 个执行 |
| `service_uid` / `service_gid` | 服务身份 |
| `payload_uid` / `payload_gid` | 基数；box N 的用户程序身份是「基数 + N」 |
| `init_uid` / `init_gid` | 基数；box N 的隔离环境 init 身份是「基数 + N」 |

- uid/gid 为正整数；服务、payload、init 三类身份互不重叠，都不是 root。
- payload 和 init 各需要 `box_count` 个连续身份（基数 + 0 … 基数 + `box_count` − 1），配置时要保证这两段不越界、不互相重叠。
- 路径以 `/` 开头，不含 `//`、`/./`、`/../`。
- 文件和账号的安装位置、属主和权限见[安装布局协议](./install-layout.md)。
- 命令按**裸名称**在 `/work`、`/usr/bin`、`/bin` 中依次解析，请求里的 `PATH` 不参与。
- 执行器不核对 rootfs 的内容；rootfs 的真实性由部署层在启动前按清单核对。

执行器所在服务单元的要求：
- 能力边界集包含 `CAP_SYS_ADMIN CAP_SETUID CAP_SETGID CAP_SETPCAP CAP_CHOWN CAP_DAC_OVERRIDE CAP_MKNOD CAP_KILL`；缺 `CAP_KILL` 时执行器拒绝执行。
- 不能设置 `NoNewPrivileges`，也不能设置隐含它的选项，不能设置 `ProtectControlGroups`。

## 4. box 目录

调用方在 `<boxes>/<N>/` 下准备，**所有目录和文件都属于服务身份**：

```text
<boxes>/<N>/
├── spec         请求（§5），必需
├── stdin        标准输入的内容，必需，可以为空
├── in/          输入文件目录，必需
│   └── <i>      第 i 个输入的内容，文件名是十进制序号，对应 spec 中第 i 条 input=
└── out/         必须存在且为空，执行器把结果写进来
```

- `stdin` 成为用户程序的 fd 0，没有文件名；`in/<i>` 成为 `/work` 下有名字的文件，名字和可执行位由 `input=` 决定。
  跑同一个产物的多个测试点时，`in/` 不变，只换 `stdin`。
- 要求，违反即拒绝（退出码 1）：

| 要求 | 值 |
|---|---|
| 目录 | `boxes`、`<N>`、`in`、`out` 是目录，属于服务身份，组和其他人不可写 |
| 文件 | `spec`、`stdin`、`in/<i>` 是普通文件，属于服务身份，链接数为 1，组和其他人不可写 |
| 路径解析 | 不跟随符号链接，不跨挂载点 |
| `out/` | 必须为空 |
| 并发 | 同一 box 同一时刻只能有一次执行（`flock`），否则 `box N is busy` |
| 大小 | `spec` ≤ 256 KiB；`stdin` 与所有 `in/<i>` **合计** ≤ 64 MiB |

- **每次调用前，调用方把 box 复位**：删掉上次的 `spec`、`stdin`、`in/*`、`out/*`，重建空的 `in/` 和 `out/`。执行器不替你清理。
- 执行器写回时只用 `O_EXCL` 新建文件，并把属主交还服务身份。

## 5. 请求 spec

一串以 **NUL（`\0`）结尾**的 `key=value` 记录，不是 JSON。整个文件必须以 NUL 结尾；空记录和没有 `=` 的记录都是错误。
未知键、重复的单值键、缺少必需键，一律拒绝。

| 键 | 数量 | 值 |
|---|---|---|
| `arg` | 1–256 | argv 的一项，按出现顺序；第一条是命令名，必须是裸名称 |
| `env` | 0–128 | `KEY=VALUE`，追加在固定环境变量之后；`KEY` 非空 |
| `input` | 0–128 | `<0\|1>:<工作区相对路径>`；`1` 为可执行（0755），`0` 为 0644；内容来自 `in/<序号>`，序号即这条 `input` 的出现顺序（从 0 起） |
| `output` | 0–128 | 执行结束后要取回的工作区相对路径 |
| `cpu_ns` `clock_ns` `memory_bytes` `max_processes` `stdout_max_bytes` `stderr_max_bytes` | 各恰好 1 | 十进制整数 |

取值规则：
- **裸命令名**：≤ 128 个 ASCII 字节，首字符为字母或数字，其余为字母数字或 `_ . + -`。不能带目录（`/bin/cat` 被拒）。
- **工作区相对路径**（`input`、`output`）：以 `/` 分成 ≤ 8 段，每段 ≤ 128 个 ASCII 字节，首字符为字母或数字，其余为字母数字或 `_ . -`。
- `arg` 与 `env` 的字符串合计 ≤ 32 KiB（含结尾 NUL）。`input` 路径互不重复，`output` 路径互不重复。

**六项限制都必须给，执行器不补默认值**。`cpu_ns`、`clock_ns`、`memory_bytes`、`max_processes` 必须大于 0。

| 限制 | 上限 | 含义 |
|---|---|---|
| `cpu_ns` | 60 s | 整组的 CPU 时间预算，从 `execve` 成功起算 |
| `clock_ns` | 120 s | 墙钟预算，从 `execve` 成功起算 |
| `memory_bytes` | 1 GiB | 整组内存上限（`memory.max`，swap 为 0） |
| `max_processes` | 256 | 整组进程和线程数上限（`pids.max`） |
| `stdout_max_bytes` | 1 MiB | stdout 保留上限，超过即停止执行 |
| `stderr_max_bytes` | 1 MiB | stderr 保留上限，规则同上 |

最小例子（`\0` 代表 NUL 字节）：

```text
arg=cat\0cpu_ns=1000000000\0clock_ns=5000000000\0memory_bytes=268435456\0max_processes=32\0stdout_max_bytes=1048576\0stderr_max_bytes=65536\0
```

编译并取回产物：

```text
arg=g++\0arg=Main.cpp\0arg=-o\0arg=Main\0arg=-O2\0arg=-std=c++17\0input=0:Main.cpp\0output=Main\0
cpu_ns=20000000000\0clock_ns=60000000000\0memory_bytes=805306368\0max_processes=64\0stdout_max_bytes=1048576\0stderr_max_bytes=1048576\0
```

## 6. 取消

关闭执行器 stdin 的写端（或调用方崩溃）即表示**取消**：执行器立刻整组 kill、回收，并报告 `reason":"cancelled"`。

- 取消优先于其他事件。
- 只有 stdin 是管道时才监听取消；stdin 是 `/dev/null` 或文件时既不监听也不会取消。
- 写完（空）输入就立刻关闭写端等于一启动就取消；保持管道打开直到执行器退出。
- 不要用信号杀执行器来取消：它来不及回收，退出码不再是约定值，调用方必须按「回收未确认」处理。

## 7. 结果

### 7.1 退出码

执行器自身的退出码，不是用户程序的：

| 码 | 含义 | 调用方动作 |
|---|---|---|
| `0` | 事实已输出，执行组已回收。`error` 非空表示平台故障，此时不交付产物 | 读 stdout 的 JSON |
| `1` | **拒绝执行**（配置、调用方、box 或请求不合格），什么都没启动。原因在 stderr，一行 `sandbox: …` | 修正后可重试，该 box 仍可用 |
| `2` | **回收未确认**：执行组杀不干净或删不掉 | 停止使用这个 box，缩容并告警，不当作普通失败重试 |

stdout 在退出码 0 时恰好是一行 JSON（以换行结尾）；stderr 只在非 0 时有内容。其他退出码（如被信号杀死）一律按「回收未确认」处理。

### 7.2 事实 JSON

| 字段 | 类型 | 含义 |
|---|---|---|
| `version` | int | 恒为 1 |
| `exitCode` | int | 用户程序的退出码；被信号终止时为 `-1`；命令没能启动时为 0，看 `error` |
| `signal` | int | 终止用户程序的信号编号，没有则 0。段错误 11，abort 6，违反 seccomp 为 31（SIGSYS） |
| `cpuNs` | int | 整组 CPU 时间（ns），从 `execve` 成功起算 |
| `memoryBytes` | int | 整组内存峰值（`memory.peak`，不是 RSS） |
| `clockNs` | int | 墙钟（ns），从 `execve` 成功起算；没走到 `execve` 为 0 |
| `reason` | string | 执行器**主动**终止的原因：`""`、`cpu`、`wall`、`output`、`cancelled`、`platform` |
| `oom` / `oomKill` | int | 内存 cgroup 的 `oom` / `oom_kill` 事件数 |
| `memoryMaxEvents` | int | 触及 `memory.max` 的次数 |
| `pidsMaxEvents` | int | 触及 `pids.max` 的次数（fork 被拒） |
| `cancelled` | bool | 是否被调用方取消 |
| `outputExceeded` | bool | stdout 或 stderr 是否超限 |
| `stdoutBytes` / `stderrBytes` | int | 保留的字节数，超限时等于上限 |
| `outputs` | array | 取回的产物 `{"index": i, "path": "<output 路径>", "sizeBytes": n}`，`index` 是该 `output=` 的序号 |
| `error` | string | 非空即平台故障（不是用户程序的问题），如 `init failed: phase=6 errno=2` |

`error` 里的 `phase`：1 根文件系统、2 工作区、3 系统视图、4 pivot_root、5 复制输入、6 解析命令、7 fork、8 降权与过滤器、9 等待；`errno` 是系统错误号。

### 7.3 out/ 写回的文件

均属于服务身份，权限 0600：

| 文件 | 内容 |
|---|---|
| `out/stdout` | stdout 的前 `stdoutBytes` 字节，总会写，可能为空 |
| `out/stderr` | stderr，规则同上 |
| `out/artifact-<i>` | 第 i 条 `output=` 对应的文件；只有 `error` 为空且文件存在时才有 |

产物规则：
- 只在整个执行组清空**之后**收集；路径必须在工作区内，不跟随链接、不跨挂载。
- 只接受单链接的普通文件；所有产物合计 ≤ 64 MiB。
- **声明了但没生成的产物被静默跳过**（`outputs` 里没有它），是否要紧由调用方判断。
- 产物不合格（是目录、多链接、过大）则整次按平台故障处理。

### 7.4 从事实读出结论

执行器不下结论。调用方判定时必须按下面的顺序（先匹配先用）：

| 条件 | 结论 |
|---|---|
| 调用方已取消，或 `reason=platform` | 平台错误 |
| `oom > 0 && oomKill > 0` | 内存超限 |
| `reason` 是 `cpu` 或 `wall` | 超时 |
| `reason=output` 或 `outputExceeded` | 输出超限 |
| 其他非空 `reason` | 平台错误 |
| `cpuNs` 超过自己的预算 | 超时（采样可能错过最后一小段） |
| `signal != 0` | 被信号终止（运行错误） |
| `exitCode == 0` | 正常结束 |
| 否则 | 非零退出（运行错误） |

- **内存超限看 `oom` 与 `oomKill`，不看 `signal=9`**。
- **违反 seccomp 不是 `reason`**：`reason` 为空、`exitCode=-1`、`signal=31`。
- **命令不存在是平台错误，不是退出码 127**：得到 `reason":"platform"` 和 `error":"init failed: phase=6 errno=2"`。用户代码没跑，不算用户的错。

## 8. 用户程序看到的世界

| 方面 | 行为 |
|---|---|
| 进程 | 新 PID namespace，用户程序的 pid 是 2；`/proc` 只读且 `hidepid=2` |
| 身份 | `uid=gid=payload 基数+N`；没有 capability，`no_new_privs=1` |
| 网络 | 新 network namespace，没有网卡；`socket()` 被 seccomp 禁止 |
| 其他 namespace | mount、IPC、UTS（主机名 `cherry-sandbox`）、cgroup，共 6 个 |
| 文件系统 | 根是只读 rootfs；`/work`（工作目录、`HOME`）与 `/tmp` 是**同一块** tmpfs，128 MiB / 4096 inode，二者共用；`/dev` 只有 `null`、`zero`、`random`、`urandom` |
| 环境变量 | 固定 `PATH=/usr/bin:/bin`、`HOME=/work`、`TMPDIR=/tmp`、`LANG=C`，再追加请求的 `env=` |
| rlimit | core = 0；打开文件数 256；单文件大小 64 MiB |
| 写入 | rootfs 不可写；`/work`、`/tmp` 可写 |
| 输入 | `input=` 的文件在工作区相对路径下，属于用户程序身份；stdin 是 fd 0，没有可改写的文件名 |

**seccomp：** 允许清单之外的系统调用杀死整个进程（`SIGSYS`）。清单覆盖普通 I/O、动态链接、进程与线程、信号、时钟；
不含网络、提权、挂载、`ptrace`、内核管理。`clone` 只允许普通 fork 和线程需要的标志；`clone3` 与 `pidfd_open` 返回 `ENOSYS`；
所有 `ioctl` 返回 `ENOTTY`。编译器和普通命令使用同一份策略。

## 9. 计量与终止

- **起算点**：墙钟和 CPU 都从用户程序 `execve` 成功起算；之前的挂载、复制输入、降权不计入预算。
- **CPU**：取整组用量，每 5 ms 采样一次，减去基线；达到 `cpu_ns` 即整组 kill，`reason=cpu`。不承诺 5 ms 的硬误差界。
  组的 `cpu.max` 限为一颗 CPU 的速率，多线程不能并行占满多核。
- **墙钟**：达到 `clock_ns` 即整组 kill，`reason=wall`，睡眠也一样。
- **内存**：`memory.max = memory_bytes`、`memory.swap.max = 0`、`memory.oom.group = 1`（OOM 时整组被杀）。`memoryBytes` 报峰值，OOM 边缘可能略超 `memory.max`。
- **进程数**：`pids.max = max_processes`；被拒的 fork 计入 `pidsMaxEvents`，不会因此终止程序。
- **输出**：stdout/stderr 各自保留到上限，多出的字节被丢弃；超限即整组 kill，`reason=output`。
- **启动超时 3 s**：从 `clone3` 到 `execve` 成功超过 3 s，报平台故障 `isolated startup timed out`。
- **回收超时 10 s**：整组 kill 后最多等 10 s 看它清空；超时即退出码 2。
- **最终计量**：只有确认执行组已清空之后读到的计量才算最终事实。

## 10. 失败与恢复

| 情形 | 表现 | 说明 |
|---|---|---|
| 请求或环境不合格 | 退出码 1 + stderr 一行 | 什么都没启动；该 box 可继续用 |
| 平台故障 | 退出码 0，`reason=platform`，`error` 非空，不交付产物 | `out/stdout`、`out/stderr` 仍会写回供诊断 |
| 上次遗留的执行组 | 本次开始前先杀空并删除 | 删不掉则退出码 2 |
| 执行器进程被杀 | 隔离环境的 PID 1 带着整个 namespace 退出 | 残留的组在该 box 下次使用时清理 |
| 回收未确认 | 退出码 2 | 调用方停止接单、缩小容量，不复用该 box |

## 11. 版本与兼容

- 事实 JSON 的 `version` 目前恒为 1。调用方必须拒绝不认识的 `version`、未知字段和未知 `reason`。
- `spec` 和受信配置没有版本字段，靠键名约束：出现未知键即拒绝。
- 执行器和调用方装在同一个发布里，一起构建、一起升级，不承诺跨发布兼容。
  改动请求键、事实字段、退出码含义或配置键，必须同时修改执行器、所有调用方、本文和两端的测试。
  不兼容地改事实格式时，`version` 加一，调用方只接受新版本。
- 本协议没有机读 schema，本文就是真源。

## 12. 实测记录

2026-10-10，Ubuntu 24.04 / Linux 6.8 / x86_64（节点 `cherry-linux-3`，`box_count=1`，1 GiB 内存）：

| 场景 | 事实 |
|---|---|
| 回显 `cat` | `exitCode=0`，stdout 与 stdin 一致 |
| 编译 C++ 并取回产物 | `exitCode=0`，产物 16376 字节；多声明一个不存在的 `output` 被静默跳过 |
| 语法错误的源码 | `exitCode=1`，诊断在 `out/stderr`，不是平台错误 |
| CPU 超限（300 ms，死循环） | `reason=cpu`，`cpuNs≈304 ms` |
| 墙钟超限（1 s，`sleep(30)`） | `reason=wall`，`clockNs≈1003 ms` |
| 取消（1 s 后关闭 stdin） | `reason=cancelled`，`cancelled=true` |
| 内存超限（64 MiB，写满 512 MiB） | `signal=9`，`exitCode=-1`，`oom=1`，`oomKill=3`，`reason=""` |
| 输出超限（上限 1 MiB，输出 3 MB） | `reason=output`，`stdoutBytes=1048576` |
| 段错误 / abort | `signal=11` / `signal=6` |
| `socket()` / `mount()` | `signal=31`，`reason=""` |
| 进程数（上限 8，fork 64 个） | `pidsMaxEvents=58`，程序未被终止，等到 `reason=wall` |
| 命令不存在 | `reason=platform`，`error="init failed: phase=6 errno=2"` |
| 缺 `spec` / `cpu_ns=0` / `/bin/cat` / 缺 `memory_bytes` / 路径含 `..` | 退出码 1，各给出对应的拒绝原因 |
| `--box 9`、`--box 1`（`box_count=1`）、无参数 | 退出码 1 |
| 同一 box 并发 | 第二次退出码 1：`box 0 is busy`；第一次不受影响 |
| 非服务组用户调用 | `exec` 阶段 `Permission denied` |
| 边界集缺 `CAP_KILL` | 退出码 1：`CAP_KILL is required to reclaim the namespace if the executor dies` |
| 用户程序环境 | `pid=2`，uid 为 payload 身份，写 `/usr/bin` 被拒，写 `/work`、`/tmp` 可以，`/proc/1/status` 不可读，`/etc/passwd` 不存在 |
| 结束后 | `jobs/` 下没有残留的 `box-*` 组；box 目录可复位 |
