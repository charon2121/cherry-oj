# 攻击样本清单

攻击 benchmark 的样本清单：每个样本检验沙箱的哪一条限制、预期得到什么结论、跑完要检查什么。
本文**只写样本要检验什么和预期是什么，不含攻击代码**。格式见[判题 benchmark 协议](../../../docs/protocols/benchmark.md) §5，
判题部分的清单见 [judge-cases.md](./judge-cases.md)。只用 C。

## 通用规则

- **样本的提交方式与判题部分相同**：经 `POST /judge` 提交，同一份运行流程、退出码和报告格式。
- **结论判据是「不是 AC」**（`verdictNot: AC`）或一个具体 verdict。攻击样本从不产生标准答案，所以共用一个最小测试数据：
  一个测试点，输入为空，期望输出是固定的一行 `OK`。样本绝不会输出它。
- **回收检查（每个样本都要做）：** 样本跑完后，被测节点上不能残留 ① 任务进程（payload 与 init 身份的进程）② `jobs/` 下的执行 cgroup
  ③ box 目录里的工作区文件。三项都为 0 才算通过。
- **判定要求运行者声明 `backend:linux`**：没有真实隔离的后端上，这些样本的结论没有意义，一律跳过。
- 样本源码、名字、限制都放在 `attack/<名>/` 下，随语料入库。

## 来源与状态

| 状态 | 含义 |
|---|---|
| 自写 | 资源耗尽类，是 OJ 的常规测试程序，由我写 |
| 映射 | 执行器内核测试 `apps/sandbox/tests/run_tests.py` 里已有同一场景，这里只写判题层的对应项，样本源码由用户提供，或直接对照内核测试的判据 |
| 待提供 | 内核测试里还没有，需要用户给样本 |

## A. 资源耗尽类（自写）

这一类检验的是**每次执行的限制**是否生效、超限后是否整组回收。

| 编号 | 样本 | 检验的限制 | 预期结论 | 回收检查额外关注 | 对应的内核测试 |
|---|---|---|---|---|---|
| A1 | `cpu_spin`：死循环 | CPU 时间 | TLE | 无残留进程 | `test_cpu_limit` |
| A2 | `wall_sleep`：长睡眠，不占 CPU | 墙钟 | TLE | 无残留进程 | `test_wall_limit` |
| A3 | `memory_hog`：不断申请并写满内存 | 内存上限 | MLE | `jobs/` 下无遗留组；不能是平台错误 | `test_memory_limit_is_task_oom` |
| A4 | `stdout_flood`：无限输出到 stdout | stdout 上限 | OLE | 无残留进程 | `test_output_limit` |
| A5 | `stderr_flood`：无限输出到 stderr | stderr 上限 | OLE | 无残留进程 | `test_output_limit` |
| A6 | `fork_storm`：不断 `fork` | 进程数上限 | 不是 AC（超限后等到墙钟，预期 TLE） | 所有子进程被回收 | `test_process_and_thread_limits` |
| A7 | `thread_storm`：不断创建线程 | 进程与线程数上限 | 不是 AC | 所有线程被回收 | `test_process_and_thread_limits` |
| A8 | `orphan_spinner`：父进程先退出，留下后台死循环的子进程 | 整组回收 | 不要求 verdict（可能是 AC 或 TLE） | **后代必须在判题结束时已被杀，不能等它自己退出** | `test_background_processes_are_reclaimed` |
| A9 | `ignore_signals`：忽略 `SIGTERM`/`SIGINT`，死循环 | 超限时不能靠信号躲开 | TLE | 无残留进程 | 无，待补 |
| A10 | `workspace_fill`：往工作区写超过 128 MiB | tmpfs 容量 128 MiB | 不是 AC | 工作区文件被清理 | 无，待补 |
| A11 | `inode_exhaust`：创建超过 4096 个文件 | tmpfs inode 4096 | 不是 AC | 工作区文件被清理 | 无，待补 |
| A12 | `fd_exhaust`：不断 `open`，直到失败 | 打开文件数 256 | 不是 AC，且不影响后续判题 | 无残留 | 无，待补 |
| A13 | `big_file`：写单个超过 64 MiB 的文件 | 单文件大小 64 MiB | 不是 AC | 工作区文件被清理 | 无，待补 |

## B. 隔离探测类

这一类检验的是**用户程序越不过边界**：看不到、改不了、联不上。每个样本都是「试一下，然后原样报告或被杀」。

| 编号 | 检验的边界 | 预期结论 | 状态 | 对应的内核测试 |
|---|---|---|---|---|
| B1 | 网络：`socket()` 被 seccomp 禁止 | RE（SIGSYS） | 映射 | `test_network_is_killed_by_seccomp` |
| B2 | x32 ABI 调用被拒 | RE（SIGSYS） | 映射 | `test_x32_abi_is_killed` |
| B3 | 提权：`setuid` 不能成功 | RE，或程序报告被拒 | 映射 | `test_privilege_and_peer_isolation` |
| B4 | 同一节点其他 box 的数据不可见 | 并发的两次执行各只看到自己的 | 映射 | `test_privilege_and_peer_isolation` |
| B5 | 宿主进程不可见（`/proc` 为 `hidepid=2`、PID namespace） | 不是 AC | 映射 | `test_identity_and_filesystem` |
| B6 | 根文件系统只读，写 `/usr`、`/etc` 被拒 | 不是 AC | 映射 | `test_identity_and_filesystem` |
| B7 | 身份：无 capability、`no_new_privs`、seccomp 生效 | 不是 AC | 映射 | `test_identity_and_filesystem` |
| B8 | 不能创建符号链接和硬链接 | RE（SIGSYS） | 映射 | `test_links_cannot_be_created` |
| B9 | 危险系统调用：`mount`、`ptrace`、`unshare` 等 | RE（SIGSYS） | 待提供 | 无 |
| B10 | 读宿主文件（`/proc/1`、`/sys`、`/etc/passwd`） | 不是 AC | 待提供 | 部分覆盖于 `test_identity_and_filesystem` |
| B11 | 直接设备访问（`/dev/sda`、`/dev/mem`） | 不是 AC | 待提供 | 无 |
| B12 | 向其他 uid 的进程发信号 | 不是 AC | 待提供 | 无 |

「映射」项的做法：判题层样本和内核测试里的探针做同一件事，这里的价值是**证明经过 judge 的整条链路（编译、编译产物放进新 box、执行、判定）
之后，边界仍然生效**。

## C. 执行器协议层（不进攻击 benchmark）

下面这些是执行器协议本身的行为，应当留在内核测试里，不需要经 judge 提交：

| 场景 | 内核测试 |
|---|---|
| 非法请求被拒绝 | `test_invalid_requests_are_refused` |
| 同一 box 并发被拒 | `test_busy_box_is_refused` |
| 遗留的执行组被回收 | `test_stale_group_is_reclaimed` |
| 取消 | `test_cancel_by_closing_stdin` |
| 命令不存在是平台错误 | `test_missing_command_is_platform_failure` |
| 产物只接受有界普通文件 | `test_artifacts_are_bounded_regular_files` |
| 多次运行不泄漏 | `test_repeated_runs_do_not_leak` |

报告里把内核测试的最近一次结果与攻击 benchmark 的结果并排列出，不合并。

## 汇总

- 样本共 **25** 个：资源耗尽类 13（自写），隔离探测类 12（映射 8，待提供 4）。
- 首批（可立即写）：A1–A8，共 8 个，全部对应已有内核测试，判据现成。
- 第二批：A9–A13，需要补内核测试。
- 第三批：B1–B8 的判题层样本源码，由用户提供或对照内核测试的探针；B9–B12 由用户提供。

## 待确认

- 共用测试数据（一个测试点，输入为空，期望 `OK`）需要写进协议：目前协议只有 `manifest.json`，没有指明攻击样本用哪份测试数据。
- A6 的预期是「不是 AC」还是更具体的 TLE：fork 被拒后程序是否退出取决于样本怎么写，需要样本写好后实测再定。
- B 类里「映射」的样本源码由谁出：我可以对照 `probe` 的各个模式写判题层版本，但这一类我不保证写得出来，你需要时可以直接提供。
