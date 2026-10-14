# 攻击样本清单

攻击 benchmark 的样本清单：每个样本检验沙箱的哪一条限制、从两个入口分别预期什么、跑完要检查什么。
本文**只写样本要检验什么和预期是什么，不含攻击代码**；样本源码从现有测试里搬来（见「迁移来源」一栏），不新写。
格式见 [benchmark 协议](../../../docs/protocols/benchmark.md) §5、§6，判题部分的清单见 [judge-cases.md](./judge-cases.md)。

## 通用规则

- **两个入口：** `judge` 入口经 `POST /judge` 提交，看 verdict；`sandbox` 入口直接调用执行器，看事实 JSON。
  下表「judge 预期」「sandbox 预期」两列分别对应，空白表示该入口不测。
- **judge 入口的判据是具体 verdict 或「不是 AC」**（`verdictNot: AC`）。样本共用语料根下的 `attack-testdata/`（一个测试点，输入为空，期望 `OK`），样本绝不会输出它。
- **回收检查（每个样本、每个入口都做）：** 样本跑完后，被测节点上不能残留任务进程、`jobs/` 下的执行 cgroup、box 里的工作区文件。
- **都要求 `backend:linux`**；`sandbox` 入口还要求 `entry:sandbox`（运行者独占 box）。没有真实隔离的后端上一律跳过。
- **语言：** judge 目前只登记了 `cpp`，所以样本都是 `.cpp`；源码尽量写成 C 与 C++ 都能编译的子集。
- 样本源码、名字、限制都放在 `attack/<名>/` 下，随语料入库。

## A. 资源耗尽类

检验**每次执行的限制**是否生效、超限后是否整组回收。

| 编号 | 样本 | 检验的限制 | judge 预期 | sandbox 预期（事实） | 迁移来源 |
|---|---|---|---|---|---|
| A1 | `cpu_spin`：死循环 | CPU 时间 | TLE | `reason=cpu`；`cpuNs ≥ 限制`；`clockNs` 远小于墙钟 | `run_tests.py::test_cpu_limit`，`judge_program` 的 `cpu`，`acceptance/cpu_limit.cpp` |
| A2 | `wall_sleep`：长睡眠，不占 CPU | 墙钟 | TLE | `reason=wall`；`cpuNs` 很小 | `test_wall_limit`，`judge_program` 的 `sleep` |
| A3 | `memory_hog`：不断申请并写满内存 | 内存上限 | MLE | `signal=9`、`reason=""`、`oom>0`、`oomKill>0` | `test_memory_limit_is_task_oom`，`judge_program` 的 `memory`，`acceptance/memory_limit.cpp` |
| A4 | `stdout_flood`：无限输出到 stdout | stdout 上限 | OLE | `reason=output`、`outputExceeded=true`、`stdoutBytes` = 上限 | `test_output_limit`，`judge_program` 的 `output`，`acceptance/output_limit.cpp` |
| A5 | `stderr_flood`：无限输出到 stderr | stderr 上限 | OLE | `reason=output`、`outputExceeded=true` | 新增，参照 A4 |
| A6 | `fork_storm`：不断 `fork` | 进程数上限 | 不是 AC | `pidsMaxEvents>0`；`stdout` 以拒绝标记开头 | `test_process_and_thread_limits`，`judge_program` 的 `tree` |
| A7 | `thread_storm`：不断创建线程 | 进程与线程数上限 | 不是 AC | `pidsMaxEvents>0`；`stdout` 以拒绝标记开头 | `test_process_and_thread_limits`，`judge_program` 的 `threads`，`cpp_limits.py` |
| A8 | `orphan_spinner`：父进程先退出，留下后台子进程 | 整组回收 | 不要求 verdict | `exitCode=0`、`reason=""`；`elapsedMsLt`：小于后代自行退出的时间 | `test_background_processes_are_reclaimed`，`judge_program` 的 `background`，`cpp_limits.py`，`verify-*.py` 的内嵌源码 |
| A9 | `ignore_signals`：忽略 `SIGTERM`/`SIGINT`，死循环 | 超限时不能靠信号躲开 | TLE | `reason=cpu` | 新增 |
| A10 | `workspace_fill`：往工作区写超过 128 MiB | tmpfs 容量 | 不是 AC | 新增，事实待实测 | 新增 |
| A11 | `inode_exhaust`：创建超过 4096 个文件 | tmpfs inode | 不是 AC | 新增，事实待实测 | 新增 |
| A12 | `fd_exhaust`：不断 `open`，直到失败 | 打开文件数 256 | 不是 AC，且不影响后续判题 | 新增，事实待实测 | 新增 |
| A13 | `big_file`：写单个超过 64 MiB 的文件 | 单文件大小 64 MiB | 不是 AC | 新增，事实待实测 | 新增 |
| A14 | `kill_self`：向自己发 `SIGKILL` | 信号不能被误判成 CPU 或内存超限 | RE | `signal=9`、`reason=""`、`oom=0` | `acceptance/signal_kill.cpp`，`judge_program` 的 `kill` |

## B. 隔离探测类

检验**用户程序越不过边界**：看不到、改不了、联不上。

| 编号 | 检验的边界 | judge 预期 | sandbox 预期（事实） | 迁移来源 |
|---|---|---|---|---|
| B1 | 网络：`socket()` 被 seccomp 禁止 | RE | `signal=31` | `test_network_is_killed_by_seccomp`，`judge_program` 的 `network` |
| B2 | x32 ABI 调用被拒 | RE | `signal=31` | `test_x32_abi_is_killed` |
| B3 | 提权：`setuid(0)` 不能成功 | 不是 AC，或程序报告被拒 | `anyOf`：`signal=31`，或 `exitCode=0` 且 `stdout` 为 `denied` | `test_privilege_and_peer_isolation`，`judge_program` 的 `privilege` |
| B4 | 身份：无 capability、`no_new_privs=1`、seccomp 生效、宿主根不可见、根只读、工作目录是 `/work` | 样本按约定打印证据，由 `stdout` 断言 | `exitCode=0`；`stdout` 满足身份约定 | `test_identity_and_filesystem`，`judge_program` 的 `identity`、`hostfile` |
| B5 | 不能创建符号链接和硬链接 | RE | `signal=31` | `test_links_cannot_be_created`，`judge_program` 的 `symlink`、`magiclink`、`hardlink` |
| B6 | 危险系统调用：`mount` | RE | `signal=31` | `judge_program` 的 `mount` |
| B7 | 危险系统调用：`ptrace` | RE | `signal=31`（待实测确认） | `judge_program` 的 `ptrace` |
| B8 | 读宿主文件（`/proc/1`、`/sys`、`/etc/passwd`） | 不是 AC | 新增，事实待实测 | 部分覆盖于 `test_identity_and_filesystem` |
| B9 | 直接设备访问（`/dev/sda`、`/dev/mem`） | 不是 AC | 新增，事实待实测 | 新增 |
| B10 | 向其他 uid 的进程发信号 | 不是 AC | 新增，事实待实测 | 新增 |
| B11 | 并发的两次执行互相不可见（各自工作区的数据、宿主 PID） | 不测（judge 入口串行提交） | `left` 与 `right` 用 `concurrent` 同时运行，各自 `stdout` 为 `private <名> peers=0 host=hidden\n` | `test_privilege_and_peer_isolation` 的后半，`judge_program` 的 `isolation` |

B11 是唯一需要**同时运行两次**的样本，用运行说明的 `id` 与 `concurrent` 字段表达（协议 §6、§7.2）：两个运行说明各占一个 box，运行者记录起止时间，没有重叠就判失败。样本从 `stdin` 读自己的标记和 `${runnerPid}`（宿主上运行者的进程号）。

## C. 不进 benchmark 的

下面这些测的是**平台自己**，不是用户提交的行为，留在原位（它们要用的简单程序由各自的夹具自带）：

| 场景 | 现位置 |
|---|---|
| 非法请求被拒绝、同一 box 并发被拒、遗留的执行组被回收、取消、命令不存在、产物规则、多次运行不泄漏 | `apps/sandbox/tests/run_tests.py` 里对应的用例 |
| 安装、布局、卸载恢复、执行器与 judge 的崩溃恢复 | `deploy/sandbox-linux/install/verify-*.py`、`deploy/sandbox-linux/tests/fault_batch.py` |

## 汇总

- 样本共 **25** 个：资源耗尽类 14（A1–A14），隔离探测类 11（B1–B11）。
- **有现成源码可搬的**：A1–A4、A6–A8、A14、B1–B7、B11，共 16 个；A5、A9–A13、B8–B10 共 9 个需要新增，其中 B8–B10 由用户提供。
- 首批：A1–A4、A6–A8、A14、B1–B7，共 15 个，全部有现成的预期事实。

## 待确认

- B4 的身份约定：样本把哪些字段打印成什么格式，`stdout` 如何断言，需要在迁移时定下来（现在的探针打印 JSON，`run_tests.py` 里逐字段断言）。
- A6 在 `judge` 入口的预期是「不是 AC」还是更具体的 TLE：fork 被拒后程序是否退出取决于样本怎么写，样本迁过来后实测再定。
- A10–A13、B8–B10 的 `sandbox` 事实需要实测，实测前先不写进 `manifest.json`。
