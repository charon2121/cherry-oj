# sandbox 执行器：安装与调用协议

> **依据：** `apps/sandbox` 当前源码，以及 2026-10-10 在测试服务器（Ubuntu 24.04、内核 6.8、x86_64、节点 `cherry-linux-3`）
> 上对已安装二进制的实测。本文是执行器的**接口真源**：以前这份约定只散落在 `apps/sandbox/README.md` 和源码注释里
> （`engine.md` 里写的是「没有 schema」）。改动执行器的请求格式、事实字段、退出码或安装要求时，先改本文。
> 设计动机和进程结构见 [engine.md §7](./engine.md)；这里只讲**怎么装、怎么调、怎么读结果**。

## 0. 快速使用

装好之后，直接用工具 `apps/sandbox/tools/sandbox_run.py`（只用标准库，拷到服务器上即可）。它替你准备 box、拼请求、
保持取消管道、读结果、清理现场。需要 root 或服务用户（例如 `sudo`）。**占用 box 0，请在 judge 空闲时用。**

```sh
# 运行一条命令，stdin 来自文件
sudo python3 sandbox_run.py --stdin in.txt -- cat

# 编译：把源码放进工作区，取回产物 Main
sudo python3 sandbox_run.py --put main.cpp --get Main --cpu 20s --wall 60s --mem 768m --procs 64 \
     -- g++ main.cpp -o Main -O2 -std=c++17

# 运行刚编译的程序：Main:Main:x 表示放进工作区并设为可执行
sudo python3 sandbox_run.py --put Main:Main:x --stdin in.txt --cpu 1s -- Main
```

- 程序的 stdout / stderr 原样输出，最后一行摘要写到 stderr，例如 `[sandbox] 正常结束  cpu=1.3ms  mem=0.8MiB  wall=1ms`。
- 退出码：`0` 正常结束；`1` 没有正常结束（非零退出、信号、超限、平台故障）；`2` 请求被拒或工具/环境有问题。
- 常用选项：`--cpu 2s`（CPU）、`--wall 10s`（墙钟）、`--mem 256m`、`--procs 32`、`--out 1m`、`--err 64k`、`--env K=V`、
  `--json`（另外打印原始事实）。`sandbox_run.py --help` 看全部。
- **命令必须是裸名称**（`Main`、`g++`、`cat`），不能写 `./Main` 或 `/bin/cat`；自己的程序先用 `--put` 放进工作区。
- 想自己写调用方（比如 Go 或别的语言）才需要读下面的协议；只是用，到这里就够了。

## 1. 它是什么，不是什么

`sandbox` 是一个**一次性**的 C 程序：每次调用在隔离环境里执行**一条命令**，最后向 stdout 输出**一行 JSON 事实**后退出。

- 没有常驻进程、没有 socket、没有网络；谁要用它，谁就 `exec` 它。
- 以 **setuid-root** 安装，只有一个系统组（judge 服务的组）能执行它。
- **不理解判题。** 它不编译、不比对、不产生 verdict（AC/WA/TLE…）；它只报告「这条命令怎么结束的、用了多少资源」，
  verdict 在 judge（Go）里由事实推出（见 §7）。
- 一次调用只执行一条命令。「编译再运行」是调用方调用两次：第一次产出可执行文件作为**产物**，第二次把它作为**输入**。

```text
调用方（非 root，服务身份）                     sandbox（setuid-root）
 ├─ 写 box 目录：spec / stdin / in/N              ├─ 校验受信配置、调用方身份、请求
 ├─ 执行 sandbox --box N（stdin 接一根管道）  ──► ├─ 建执行组（cgroup）+ 6 个 namespace + 只读 rootfs
 │                                                ├─ 复制输入 → 降权 → seccomp → execve 用户命令
 ├─ 读 stdout 的一行 JSON                    ◄── ├─ 监督墙钟 / CPU / 输出 / 取消 → 整组 kill → 最终计量
 └─ 读 box 的 out/（stdout、stderr、产物）        └─ 取回产物 → 输出一行 JSON → 退出
```

## 2. 平台与构建

| 项 | 要求 |
|---|---|
| 平台 | **仅 Linux x86_64**（seccomp 策略按 x86_64 系统调用表写，其它架构编译即报错） |
| 内核 | 需要 cgroup v2、`clone3`（`CLONE_INTO_CGROUP`）、`openat2`、新挂载 API（`fsopen`/`fsmount`/`move_mount`）；Ubuntu 24.04 / 6.8 实测可用 |
| 构建 | `gcc` + `libseccomp-dev`；libseccomp **静态链接**，安装后不依赖系统的 libseccomp |
| 命令 | `make -C apps/sandbox`，产物 `apps/sandbox/build/sandbox`（`BUILD=` / `OUT=` 可改输出位置） |

**受信配置路径是编译进二进制的**：默认 `/opt/cherry-oj/etc/executor.conf`，`make CONFIG=/path` 可改（只给测试构建用）。
调用方无法在运行时改它——这是有意的，配置决定 rootfs、身份和 cgroup，不能由调用方选择。

## 3. 安装到服务器：必须满足的清单

执行器启动时逐项校验下面这些；任何一项不满足都**拒绝执行**（退出码 1，什么都不会启动）。

### 3.1 二进制

```text
-rwsr-xr--  1  root  <service-group>   sandbox          # 模式 4754
```

- 所有者 `root`，组为 judge 服务的组，模式 `4754`（setuid-root；组可执行；其他人只读，读是为了让部署清单核对它的摘要）。
- **不在该组的用户连 `exec` 都会得到 `Permission denied`**（实测）。
- 执行器自己不关心安装位置；Go 调用方要求用**绝对路径**，并在启动时检查它是 setuid-root 的普通文件。官方安装把它放在 `/opt/cherry-oj/releases/<发布>/libexec/sandbox`，
  再用 `current` 符号链接指向当前发布。

### 3.1.1 家目录

本项目在服务器上的全部持久文件都在一个根目录 **`/opt/cherry-oj`** 下（root 所有，755）：

```text
/opt/cherry-oj/
  etc/                  配置（root 所有）：executor.conf、judge.json、judge-start.json、deployment.json …
  releases/<版本>/      一个发布：bin/judge、libexec/sandbox、rootfs/、manifest.json、packages.lock.json
  current               指向当前发布的符号链接
  judge/                judge 的工作区（服务用户所有）：boxes/、blobs/、testdata/、logs/
  operations/           安装器保存的运维脚本（manage.py、verify-*.py）
  installation.json     安装回执
```

只有两处在它之外，且都由系统规则决定位置：systemd 单元（`/etc/systemd/system/cherry-sandbox-judge.service`、
`cherry-sandbox.slice`）和 cgroup（`/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/…`）。路径在 `deploy/sandbox-linux/install/layout.py`
一处定义；执行器的受信配置路径 `/opt/cherry-oj/etc/executor.conf` 编译进二进制。

### 3.2 账号

三类身份，**互不重叠、都不能是 root**（配置校验会拒绝）：

| 身份 | 数量 | 作用 |
|---|---|---|
| 服务身份（service） | 1 | 调用方；拥有 box 目录。执行器只接受它（或 root）调用 |
| payload | `box_count` 个，编号 `payload_uid + N` | 用户程序运行时的身份（box N 用第 N 个） |
| init | `box_count` 个，编号 `init_uid + N` | 隔离环境里 PID 1 的身份（降权后只负责回收孤儿） |

官方安装使用：服务 `cherry-judge` = 61010；payload 61002–61005；init 61006–61009；均 `nologin`、无家目录。
gid 与 uid 同号。

### 3.3 受信配置 `executor.conf`

文件与它的**每一级祖先目录**都必须属于 root、不能被组或其他人写入，路径中不能有符号链接；否则拒绝使用。
每行 `key=value`，`#` 开头为注释，**键必须全部是下列名字，出现未知键即拒绝**。

| 键 | 含义 |
|---|---|
| `rootfs` | 只读根文件系统目录（绝对路径）。需预建 `work`、`tmp`、`proc`、`dev`、`.oldroot` 五个目录 |
| `boxes` | box 根目录（绝对路径），属于服务身份 |
| `cgroup` | 委派给服务的 `jobs` 子树（cgroup v2 路径），已启用 `cpu`、`memory`、`pids` |
| `box_count` | box 数量，1–4。同一时刻最多 `box_count` 个并发执行 |
| `service_uid` / `service_gid` | 服务身份 |
| `payload_uid` / `payload_gid` | box N 的用户程序身份为「基数 + N」 |
| `init_uid` / `init_gid` | box N 的 init 身份为「基数 + N」 |

所有 uid/gid 必须是正整数；路径必须以 `/` 开头且不含 `//`、`/./`、`/../`。测试服务器上的实际内容：

```text
rootfs=/opt/cherry-oj/releases/cherry-testdata-v2/rootfs
boxes=/opt/cherry-oj/judge/boxes
cgroup=/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/cherry-sandbox-judge.service/jobs
box_count=1
service_uid=61010
service_gid=61010
payload_uid=61002
payload_gid=61002
init_uid=61006
init_gid=61006
```

### 3.4 rootfs

一棵**只读**的目录树，里面是用户程序（和编译器）能看到的整个文件系统。官方 rootfs 由锁定的 Ubuntu 软件包组装
（见 `deploy/sandbox-linux/rootfs/README.md`），**只含 C/C++ 工具链**，没有 shell、Python、Java，也没有 `/etc/passwd`。

- 命令按**裸名称**解析：先 `/work`，再 `/usr/bin`，再 `/bin`；请求里的 `PATH` 不参与解析。
- 目录必须预建 `work`、`tmp`、`proc`、`dev`、`.oldroot`，且不能是符号链接。
- rootfs 的真实性（有没有被改过）由部署层的启动脚本按清单逐文件核对，**执行器本身不核对内容**。

### 3.5 box 根目录

`boxes` 目录属于服务身份，官方安装是 `0700`。每个 box 一个子目录 `boxes/<N>/`（N 从 0 开始，小于 `box_count`），
由**调用方**创建和维护（§4.3）。

### 3.6 cgroup 与 systemd 单元

- 系统使用 **cgroup v2**；`cgroup=` 指向的子树必须是 cgroup2 文件系统，且 `cgroup.subtree_control` 已启用 `cpu memory pids`。
  执行器以 root 身份在其中为每次执行建一个组 `box-<N>`，组内文件属于 root，服务身份无法改限额或搬进程。
- 调用方（judge）运行在一个 systemd 单元里，需要：

  ```ini
  User=cherry-judge
  Group=cherry-judge
  Delegate=cpu memory pids
  # 边界集必须包含执行器需要的 8 项，且包含 CAP_KILL：
  CapabilityBoundingSet=CAP_SYS_ADMIN CAP_SETUID CAP_SETGID CAP_SETPCAP CAP_CHOWN CAP_DAC_OVERRIDE CAP_MKNOD CAP_KILL
  ```

- **单元不能设置 `NoNewPrivileges`，也不能设置会隐含它的选项**（`RestrictAddressFamilies`、`LockPersonality`、
  `ProtectKernel*`、`RestrictSUIDSGID`、`RestrictNamespaces` 等基于 seccomp 的硬化），否则 setuid 不生效；也不能设置
  `ProtectControlGroups`（cgroupfs 必须可写）。这是已接受的代价，见 [engine.md](./engine.md) 与
  `deploy/sandbox-linux/install/README.md`。
- 为什么必须有 `CAP_KILL`：执行器意外死亡时，隔离环境的 PID 1 靠 `PDEATHSIG` 带着整个 namespace 退出；内核按普通 kill 的权限
  投递这个信号，发送方（root）与 init 身份不同，**没有 `CAP_KILL` 信号会被静默丢弃**，执行组就在无人监督下继续运行。
  所以边界集里没有它时，执行器直接拒绝（实测：`sandbox: CAP_KILL is required to reclaim the namespace if the executor dies`）。

完整的、可复现的安装由 `deploy/sandbox-linux/install/manage.py` 完成（渲染审核材料 → 校验 → 建账号 → 拷贝发布 → 装单元），
不要手工拼装；本文只说明它装出来的东西必须长什么样。

### 3.7 装完后的自查

```sh
stat -c '%a %U:%G %n' /opt/cherry-oj/current/libexec/sandbox     # 4754 root:cherry-judge
cat /opt/cherry-oj/etc/executor.conf                                      # 键齐全、路径存在
stat -c '%U:%G %a' /opt/cherry-oj/judge/boxes                     # cherry-judge:cherry-judge 700
cat /sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/cherry-sandbox-judge.service/jobs/cgroup.subtree_control
systemctl show cherry-sandbox-judge.service -p CapabilityBoundingSet -p Delegate
python3 /opt/cherry-oj/operations/verify-native.py                # 端到端冒烟：隔离、限额、清理
```

## 4. 调用协议

### 4.1 命令行

```text
sandbox --box N
```

- 只有这一种形式。参数个数或形式不对：退出码 1，`sandbox: usage: sandbox --box N`。
- `N` 必须是 0–3 的整数，并且小于配置里的 `box_count`（否则 `box index is outside the configured range`）。
- **调用方必须把 stdin 接成一根管道并在整个执行期间保持写端打开**（取消协议，§4.5）。

### 4.2 调用方身份与进程环境

- 真实 uid 必须是 `service_uid` 或 `0`（root）；其他身份：`caller is not the sandbox service`。
  （非 root 且不在服务组的用户在 `exec` 阶段就被内核拒绝。）
- 执行器没有以 setuid-root 安装时：`not installed setuid root`。
- 执行器启动时**不信任调用方的进程环境**：清空环境变量、`umask 077`、关闭除 0/1/2 外的所有 fd、`PR_SET_DUMPABLE=0`。
  调用方传环境变量没有意义（用户程序的环境只来自请求里的 `env=`，见 §4.4）；建议以**空环境**启动它。
- 调用方可以是任何语言写的程序，约定只有：`exec` + box 目录 + 一根 stdin 管道 + 读一行 JSON。Go 的参考实现是
  `apps/judge-engine/execution/backend/executor.go`。

### 4.3 box 目录

调用方在 `<boxes>/<N>/` 下准备（**所有目录与文件都属于服务身份**）：

```text
<boxes>/<N>/
  spec              请求（§4.4）。必需
  stdin             标准输入的内容。必需（可以是空文件）
  in/               输入文件目录。必需
    0 1 2 …           第 i 个输入的内容，文件名是十进制序号，对应 spec 中第 i 条 input=
  out/              必须存在且为空。执行器把结果写进来
```

执行器是 root，却在读一个**由非 root 控制**的目录，所以对它的要求很严：

| 要求 | 违反时 |
|---|---|
| `boxes` 与 `<N>`、`in`、`out` 都是目录，属于服务身份，且组/其他人不可写（`mode & 022 == 0`） | 拒绝：`box … missing or not owned by the service` |
| `spec`、`stdin`、`in/<i>` 是**普通文件**、属于服务身份、**链接数为 1**、组/其他人不可写 | 拒绝 |
| 路径解析**不跟随任何符号链接**，不跨挂载点 | 拒绝 |
| `out/` 必须是空的 | 拒绝：`out directory is not empty` |
| 同一 box 同时只能有一次执行（`flock`） | 拒绝：`box N is busy` |
| `spec` 不超过 256 KiB；输入文件与 stdin **合计**不超过 64 MiB | 拒绝 |

写回同样谨慎：执行器只用 `O_EXCL` 新建文件并把所有者**交还**服务身份，所以读到的、写出的都是服务本来就能碰的文件。
**每次调用前，调用方应把 box 重置成干净状态**（删掉上次的 `spec`、`stdin`、`in/*`、`out/*`，重建空的 `in/`、`out/`）；
执行器不替你清理。

### 4.4 请求 `spec`

一串**以 NUL（`\0`）结尾**的 `key=value` 记录。**不是 JSON**：setuid-root 程序里少一个解析器，就少一块攻击面。
整个文件必须以 NUL 结尾；空记录、没有 `=` 的记录都是错误。

| 键 | 数量 | 值 |
|---|---|---|
| `arg` | 1–256 | argv 的一项，按出现顺序。`arg=…` 的第一条是命令名，**必须是裸名称**（见下） |
| `env` | 0–128 | `KEY=VALUE`，追加在固定的 `PATH`、`HOME`、`TMPDIR`、`LANG` 之后；`KEY` 不能为空 |
| `input` | 0–128 | `<0\|1>:<工作区相对路径>`；`1` 表示该文件可执行（0755），`0` 为 0644。内容来自 `in/<序号>`，序号 = 这条 `input` 在 spec 里的出现顺序（从 0 起） |
| `output` | 0–128 | 执行结束后要取回的工作区相对路径 |
| `cpu_ns` `clock_ns` `memory_bytes` `max_processes` `stdout_max_bytes` `stderr_max_bytes` | 各恰好 1 | 十进制整数，见下表 |

**取值规则：**

- **裸命令名**：最多 128 个 ASCII 字节，首字符为字母或数字，其余可含字母数字与 `_ . + -`。
- **工作区相对路径**（`input`、`output`）：按 `/` 分成最多 8 段，每段最多 128 个 ASCII 字节，首字符为字母或数字（因此不能有空段、`.`、`..`，
  也不能以点开头——避免碰到 `.tmp`、`.stdin` 等内部文件），其余可含字母数字与 `_ . -`。
  `argv[0]` 因此不能带目录，例如 `/bin/cat` 会被拒绝（`command must be a bare name`）。
- `arg` 与 `env` 的字符串**合计**不超过 32 KiB（含各自的结尾 NUL）。
- `input` 路径之间不能重复，`output` 路径之间不能重复。

**限制：执行器只校验上限、不补默认值**，六项都必须给；`cpu_ns`、`clock_ns`、`memory_bytes`、`max_processes` 必须 > 0
（零预算由调用方直接给出资源结论，不应走到执行器）。

| 限制 | 单位 | 上限 | 含义 |
|---|---|---|---|
| `cpu_ns` | ns | 60 s | 用户命令的 **CPU 时间**预算（整组合计，从 `execve` 成功起算） |
| `clock_ns` | ns | 120 s | **墙钟**预算（同样从 `execve` 成功起算） |
| `memory_bytes` | bytes | 1 GiB | 整个执行组的内存上限（cgroup `memory.max`，swap 为 0） |
| `max_processes` | 个 | 256 | 整个执行组的进程/线程数上限（`pids.max`） |
| `stdout_max_bytes` | bytes | 1 MiB | stdout 保留上限；超过即停止执行并报告 `output` |
| `stderr_max_bytes` | bytes | 1 MiB | stderr 保留上限，规则同上 |

一个最小的 spec（可见字符里的 `\0` 代表 NUL 字节）：

```text
arg=cat\0cpu_ns=1000000000\0clock_ns=5000000000\0memory_bytes=268435456\0max_processes=32\0stdout_max_bytes=1048576\0stderr_max_bytes=65536\0
```

编译一个 C++ 文件、取回可执行文件（对应 judge 的真实编译请求）：

```text
arg=g++\0arg=Main.cpp\0arg=-o\0arg=Main\0arg=-O2\0arg=-std=c++17\0
input=0:Main.cpp\0output=Main\0
cpu_ns=20000000000\0clock_ns=60000000000\0memory_bytes=805306368\0max_processes=64\0stdout_max_bytes=1048576\0stderr_max_bytes=1048576\0
```

运行刚才的产物：把它放进下一次调用的 `in/0`，`input=1:Main`（可执行），`arg=Main`。

### 4.5 取消

调用方把一根管道接在执行器的 **stdin** 上，**执行期间保持写端打开**。关闭写端（或调用方崩溃）即表示**取消**：
执行器立刻整组 kill、回收，并如实报告 `reason":"cancelled"`。

- 取消优先于其他事件：调用方已经放弃，这次结果不该被当成有效结果。
- 只有 stdin 是 **FIFO（管道）** 才监听取消；stdin 是 `/dev/null` 或文件时（例如人工调试）不监听，也不会取消（实测）。
- **常见错误**：用某些库的 `communicate()` 之类接口，会在写完（空）输入后**立刻关闭**写端，等于一启动就取消。
  请保持管道打开直到执行器退出。
- 不要用信号杀执行器来取消：它来不及回收执行组。杀死执行器会让退出码不再是约定值，调用方必须按「回收未确认」处理（§4.6）。

### 4.6 输出：退出码与事实

**退出码**（执行器自身，不是用户程序的）：

| 码 | 含义 | 调用方该怎么办 |
|---|---|---|
| `0` | 事实已输出，执行组已回收。`error` 非空表示平台故障（此时不交付产物） | 读 stdout 的 JSON |
| `1` | **拒绝执行**（配置、调用方、box 或请求不合格），什么都没有启动。原因在 stderr，一行 `sandbox: …` | 修正请求/环境后可重试；该 box 仍可用 |
| `2` | **回收未确认**：执行组杀不干净或删不掉 | **必须停止使用这个 box**（缩容），不要继续接单 |

stderr 只在退出码非 0 时有内容；stdout 在退出码 0 时恰好一行 JSON（以换行结尾）。其它退出码（被信号杀死等）一律按「回收未确认」处理。

**事实 JSON**（`version` 目前恒为 1；字段名即约定，改名等于改接口，调用方应拒绝未知字段和未知 `version`）：

| 字段 | 类型 | 含义 |
|---|---|---|
| `version` | int | 恒为 1 |
| `exitCode` | int | 用户程序的退出码；**被信号终止时为 `-1`**（信号见 `signal`）。命令没能启动时为 0，看 `error` |
| `signal` | int | 终止用户程序的信号编号，没有则 0。例：段错误 11，abort 6，被 OOM 杀死 9，**违反 seccomp 为 31（SIGSYS）** |
| `cpuNs` | int | 整组消耗的 CPU 时间（ns），从 `execve` 成功起算（扣除启动阶段的基线） |
| `memoryBytes` | int | 整组内存峰值（cgroup `memory.peak`，不是 RSS） |
| `clockNs` | int | 墙钟（ns），从 `execve` 成功起算到停止；没走到 `execve` 则为 0 |
| `reason` | string | 执行器**主动**终止的原因：`""`（没有）、`cpu`、`wall`、`output`、`cancelled`、`platform` |
| `oom` | int | 该组内存 cgroup 的 `oom` 事件数 |
| `oomKill` | int | `oom_kill` 事件数（受害进程数） |
| `memoryMaxEvents` | int | 触及 `memory.max` 的次数 |
| `pidsMaxEvents` | int | 触及 `pids.max` 的次数（fork 被拒） |
| `cancelled` | bool | 是否被调用方取消 |
| `outputExceeded` | bool | stdout 或 stderr 是否超过上限 |
| `stdoutBytes` / `stderrBytes` | int | 保留下来的字节数（超限时等于上限，多出的部分被丢弃） |
| `outputs` | array | 取回的产物：`{"index": i, "path": "<output 路径>", "sizeBytes": n}`，`index` 是该 `output=` 在 spec 里的序号 |
| `error` | string | 非空即**平台故障**（不是用户程序的问题），如 `init failed: phase=6 errno=2` |

**box 的 `out/` 里写回什么**（均属于服务身份，权限 0600）：

| 文件 | 内容 |
|---|---|
| `out/stdout` | 用户程序 stdout 的前 `stdoutBytes` 字节（**总会写**，可能为空） |
| `out/stderr` | stderr，规则同上 |
| `out/artifact-<i>` | 第 i 条 `output=` 对应的文件。只有 `error` 为空、且文件存在时才有 |

**产物规则：** 只在整个执行组清空**之后**收集（用户进程不能边写边交付）；路径必须在工作区内，不跟随链接、不跨挂载；
只接受**单链接的普通文件**；所有产物合计不超过 64 MiB。**声明了但没生成的产物被静默跳过**（`outputs` 里没有它），
是否要紧由调用方判断（例如编译没产出可执行文件）。产物不合格（是目录、多链接、太大）则整次按平台故障处理。

### 4.7 怎样从事实读出结论

执行器不下结论，下面是 judge 的做法（`execution/runner/result.go`），其它调用方可以照用：

| 条件（自上而下，先匹配先用） | 结论 |
|---|---|
| 调用方的 ctx 已取消，或 `reason=platform` | 平台错误（InternalError） |
| `oom > 0 && oomKill > 0` | **内存超限** |
| `reason` 是 `cpu` 或 `wall` | **超时** |
| `reason=output`（或 stdout/stderr 超限） | **输出超限** |
| 其它非空 `reason` | 平台错误 |
| `cpuNs` 超过自己的预算 | 超时（采样可能错过最后一小段） |
| `signal != 0` | 被信号终止（运行错误；SIGSYS 即违反 seccomp） |
| `exitCode == 0` | 正常结束 |
| 否则 | 非零退出（运行错误） |

几条要点：

- **内存超限看 `oom` 与 `oomKill`，不看 `signal=9`**：SIGKILL 可能来自别的原因；执行器自己也只在「本任务有 OOM 证据」时才把它当用户的问题，
  否则报 `error: OOM victim without task-local OOM evidence`。
- **违反 seccomp 不是 `reason`**：`reason` 为空、`exitCode=-1`、`signal=31`。
- **命令不存在是平台错误，不是退出码 127**：因为命令解析发生在隔离环境内部、用户程序启动之前，所以得到 `reason":"platform"`、
  `error":"init failed: phase=6 errno=2"`。这是执行器故意的区分：用户代码没跑，不能算用户的错。
- `error` 里 `phase` 的含义：1 根文件系统、2 工作区、3 系统视图（`/proc`、`/dev`）、4 pivot_root、5 复制输入、
  6 解析命令、7 fork、8 降权/过滤器、9 等待；`errno` 是对应系统错误号。

## 5. 用户程序看到的世界

| 方面 | 行为（实测） |
|---|---|
| 进程 | 在新的 PID namespace 里，用户程序的 `pid` 是 **2**（1 是 init）；`/proc` 只读且 `hidepid=2` |
| 身份 | `uid=gid=payload_uid+N`（官方安装下 box 0 是 61002）；没有任何 capability，`no_new_privs=1` |
| 网络 | 新的网络 namespace，**没有任何网卡**；而且 `socket()` 本身被 seccomp 禁止 |
| 文件系统 | 根是只读的 rootfs；`/work`（工作目录，`HOME`）与 `/tmp` 是**同一块 tmpfs**，上限 **128 MiB / 4096 个 inode**，二者共用；`/dev` 只有 `null`、`zero`、`random`、`urandom` |
| 环境变量 | 固定 `PATH=/usr/bin:/bin`、`HOME=/work`、`TMPDIR=/tmp`、`LANG=C`，再追加请求的 `env=` |
| 其他 namespace | 另有 mount、IPC、UTS（主机名 `cherry-sandbox`）、cgroup namespace，共 6 个 |
| 单进程 rlimit | core = 0；打开文件数 256；单文件大小 64 MiB（这些不替代 cgroup 的整组计量） |
| 写 rootfs | 被拒（`/usr/bin/…` 写入失败）；写 `/work`、`/tmp` 可以 |
| 输入 | `input=` 的文件被放在工作区相对路径下，属于用户程序身份；stdin 作为 fd 0 提供，**没有可改写的文件名** |

**seccomp：** 允许清单之外的系统调用一律杀死整个进程（`SIGSYS`）。清单覆盖普通 I/O、动态链接、进程与线程、信号、时钟；
不含网络、提权、挂载、`ptrace`、内核管理。`clone` 只允许普通 fork/线程需要的标志（不能创建 namespace）；`clone3` 与
`pidfd_open` 返回 `ENOSYS`（让运行时回退到受检查的路径）；所有 `ioctl` 返回 `ENOTTY`（不向设备透传）。
编译器和普通命令目前用**同一份**策略。非 x86_64 原生 ABI（含 x32）的调用按错误架构处理，直接杀死。

## 6. 计量与终止规则

- **起算点：** 墙钟和 CPU 都从用户程序 `execve` 成功起算（内部用一根 CLOEXEC 管道感知：`execve` 成功即关闭）。
  之前的挂载、复制输入、降权都不计入用户的预算。
- **CPU：** 取整个执行组的 CPU 用量，每 **5 ms** 采样一次，减去 `execve` 时的基线；达到 `cpu_ns` 即整组 kill，`reason=cpu`。
  采样有粒度和调度延迟，**不承诺 5 ms 的硬误差界**；结束时还用最终计量兜底。
  另外组的 `cpu.max` 被限制为**一颗 CPU 的速率**，多线程程序不能并行占满多核。
- **墙钟：** 达到 `clock_ns` 即整组 kill，`reason=wall`（程序在睡觉也一样，实测 1003 ms）。
- **内存：** `memory.max = memory_bytes`，`memory.swap.max = 0`，`memory.oom.group = 1`（OOM 时整组被杀，而不是只杀一个进程）。
  `memoryBytes` 报的是 `memory.peak`；OOM 边缘处可能略超 `memory.max`。
- **进程数：** `pids.max = max_processes`，被拒的 fork 计入 `pidsMaxEvents`（不会因此终止程序）。
- **输出：** stdout/stderr 各自保留到上限，多出来的字节只用来判定「超限」并被丢弃；超限即整组 kill，`reason=output`。
- **启动超时 3 s：** 从 `clone3` 到 `execve` 成功超过 3 s，报平台故障 `isolated startup timed out`。
- **回收超时 10 s：** 整组 kill 后最多等 10 s 看它清空；超时即退出码 2。
- **最终计量：** 只有确认执行组**已清空**之后读到的计量才算最终事实。

## 7. 失败与恢复

| 情形 | 表现 | 说明 |
|---|---|---|
| 请求/环境不合格 | 退出码 1 + stderr 一行 | 什么都没启动；该 box 可继续用 |
| 平台故障（隔离环境没搭起来、命令没解析到、产物不合格…） | 退出码 0，`reason=platform`，`error` 非空，**不交付产物** | stdout/stderr 仍会写回供诊断 |
| 上次执行遗留的组 | 本次开始前先杀空并删除 | 删不掉：退出码 2，该 box 不能再用 |
| 执行器进程被杀 | 隔离环境的 PID 1 因 `PDEATHSIG` 带着整个 namespace 退出 | 残留的组由该 box 下一次使用时清理 |
| 回收未确认 | 退出码 2 | 调用方**停止接单**，缩小容量；不要复用该 box |

## 8. 手工调用

参考实现有两份，照着写即可：`apps/sandbox/tools/sandbox_run.py`（Python，约 300 行，见 `prepare_box`、`build_spec`、`run_executor`），
以及生产用的 `apps/judge-engine/execution/backend/executor.go`（Go）。

## 9. 实测记录（2026-10-10，`cherry-linux-3`）

以下是在测试服务器上对安装好的执行器逐项实测的结果（`box_count=1`，1 GiB 内存的虚拟机）。单位：时间 ms，内存 MiB。

| 场景 | 请求要点 | 事实 |
|---|---|---|
| 回显 | `cat`，stdin `hello\n` | `exitCode=0`，stdout `hello\n`，cpu 0.2 |
| 编译 | `g++ Main.cpp -o Main -O2 -std=c++17`，`output=Main` | `exitCode=0`，产物 16376 字节，cpu ≈ 354，内存 ≈ 43；多声明一个不存在的 `output` 被静默跳过 |
| 编译错误 | 语法错误的源码 | `exitCode=1`，`stderrBytes=418`，诊断在 `out/stderr`，不是平台错误 |
| 运行产物 | `Main`，stdin `1 2\n` | `exitCode=0`，stdout `3\n` |
| 环境 | 探针程序 | `pid=2 uid=61002 gid=61002 cwd=/work`；环境变量 4 个固定项 + `MY_VAR=1`；写 `/usr/bin` 被拒、写 `/work`、`/tmp` 可以；`/proc/1/status` 不可读；`/etc/passwd` 不存在；`/dev/null`、`/dev/zero` 可用，`/dev/sda` 没有 |
| CPU 超限 | `cpu_ns=300ms`，死循环 | `reason=cpu`，`cpuNs≈304 ms`，`clockNs≈303` |
| 墙钟超限 | `clock_ns=1s`，程序 `sleep(30)` | `reason=wall`，`clockNs≈1003`，cpu 仅 1.3 |
| 取消 | 1 s 后关闭 stdin 管道 | `reason=cancelled`，`cancelled=true`，`clockNs≈991` |
| 内存超限 | `memory_bytes=64MiB`，写满 512 MiB | `signal=9`，`exitCode=-1`，`oom=1`，`oomKill=3`，`memoryMaxEvents=18`，`memoryBytes=64.0`，`reason=""` |
| 输出超限 | `stdout_max_bytes=1MiB`，输出 3 MB | `reason=output`，`outputExceeded=true`，`stdoutBytes=1048576` |
| 退出码 | `return 3` | `exitCode=3` |
| 段错误 / abort | 空指针解引用 / `abort()` | `exitCode=-1`，`signal=11` / `signal=6` |
| 网络 | `socket(AF_INET, …)` | `exitCode=-1`，`signal=31`（SIGSYS），`reason=""` |
| 危险系统调用 | `mount()` | 同上，`signal=31` |
| 进程数 | `max_processes=8`，fork 64 个 | `pidsMaxEvents=58`，程序没有被终止，等到墙钟 `reason=wall` |
| stdout / stderr | 各写一行 | 分别写回 `out/stdout`、`out/stderr`，各 10 字节 |
| 命令不存在 | `arg=nosuchcmd` | `reason=platform`，`error="init failed: phase=6 errno=2"` |
| 缺 `spec` | | 退出码 1：`box spec missing or not a bounded regular file` |
| `cpu_ns=0` | | 退出码 1：`execution limits are not normalized` |
| `argv[0]` 带路径 | `/bin/cat` | 退出码 1：`command must be a bare name` |
| 缺少 `memory_bytes` | | 退出码 1：`missing limit memory_bytes` |
| `input` 路径含 `..` | | 退出码 1：`invalid input path` |
| 非法 box | `--box 9` / `--box 1`（`box_count=1`）/ 无参数 | 退出码 1：`box must be an integer 0..3` / `box index is outside the configured range` / `usage: sandbox --box N` |
| 同一 box 并发 | 第一次运行中再次调用 | 第二次退出码 1：`box 0 is busy`；第一次不受影响 |
| 其他用户调用 | `nobody` | `exec` 阶段 `Permission denied`（不在服务组） |
| 服务身份在单元之外调用 | `cherry-judge` | 成功（执行器不要求从 systemd 单元内调用，只看身份与边界集） |
| 边界集缺 `CAP_KILL` | `capsh --drop=cap_kill` | 退出码 1：`CAP_KILL is required to reclaim the namespace if the executor dies` |
| 结束后 | | `jobs/` 下没有残留的 `box-*` 组；box 目录可复位 |

## 10. 注意事项

1. **stdin 必须是保持打开的管道**，否则要么一启动就被取消，要么根本没有取消能力（§4.5）。
2. **每次调用前复位 box**；`out/` 非空会被拒。同一 box 不能并发。
3. **命令只能是裸名称**，且只在 `/work`、`/usr/bin`、`/bin` 里找。编译产物要通过 `input=1:` 放进工作区再运行。
4. **「命令不存在」「rootfs 缺文件」是平台故障**，不是用户的运行错误。
5. 判断内存超限用 **`oom` 与 `oomKill`**，不要靠信号 9 猜。
6. 违反 seccomp 的程序 `reason` 为空、`signal=31`，调用方自己决定怎么归类（judge 归为运行错误）。
7. 工作区 128 MiB / 4096 inode 是**固定**的，请求里不能调；输入合计 64 MiB、产物合计 64 MiB 也是固定上限。
8. 退出码 2 表示这个 box 作废，**调用方要缩容并告警**，不能当成普通失败重试。
9. 执行器不核对 rootfs 内容；rootfs 被篡改等于编译器被篡改，所以部署层的启动脚本必须在启动前按清单逐文件核对。
10. 编译进二进制的配置路径是**安装契约**的一部分：换路径要重新编译，不能靠环境变量或参数。

## 11. 测试

- `apps/sandbox/tests/run_tests.py`：在**真实 x86_64 Linux 内核**上、以 root 运行，覆盖隔离、计量、回收与拒绝路径。
  它会改动机器的用户、`/etc` 与 cgroup，**只能在一次性、独占的虚拟机上按需运行**。
- `deploy/sandbox-linux/install/verify-native.py`、`verify-faults.py` 等：已安装节点的冒烟与崩溃恢复（用于测试服务器）。
- 日常 CI 只**编译**执行器；编译通过不代表隔离、计量和回收已验证。

## 12. 相关文档

| 文档 | 作用 |
|---|---|
| [`engine.md`](./engine.md) §7 | 执行器的设计动机、进程结构与取舍 |
| [`apps/sandbox/README.md`](../apps/sandbox/README.md) | 执行器目录的简要说明 |
| `deploy/sandbox-linux/install/README.md` | 原生节点的安装、审核材料和回退 |
| `deploy/sandbox-linux/rootfs/README.md` | rootfs 的构建与锁定 |
| `apps/judge-engine/execution/backend/executor.go` | Go 调用方的参考实现 |
