# 现有「用户提交行为」案例清单

仓库里已经存在的、以「用户提交了这样的代码」为内容的测试案例。每个案例写明**做了什么**和**为什么这样写、目的是什么**。
目的来自代码里的注释、测试名和验收文档，没有的我没有编造；写不出来的标「未写明」。
本清单只盘点，不涉及迁移；各案例对应的 benchmark 清单编号见 [attack-cases.md](./attack-cases.md) 与 [judge-cases.md](./judge-cases.md)。

案例按来源分组。**入口**一列：`E` 直接对执行器，`J` 经 `POST /judge`，`S` 经服务层/全栈。

## 1. 执行器探针 `apps/sandbox/tests/probe.c`

一个静态链接的程序，放进测试 rootfs，按第一个参数扮演不同的用户程序，供执行器自己的测试和内核套件共用。

| 编号 | 模式 | 做了什么 | 目的 / 为什么这样写 |
|---|---|---|---|
| P1 | `identity` | 打印身份（Uid/Gid）、五组 capability、`NoNewPrivs`、`Seccomp`、`NSpid`；检查 `/etc/hostname` 是否不存在；尝试往 `/usr/bin` 写文件；列出打开的 fd 及指向；检查 `/.oldroot` 是否可见；读 `/work/data/in.txt` | 一次性取得「用户程序眼里的世界」，证明：身份已降权、没有任何能力、过滤器已生效、宿主文件看不到、根只读、没有多余 fd 泄漏进来。**先数继承来的 fd，再做会产生新 fd 的 open**，否则分不清哪些 fd 是继承的 |
| P2 | `isolation <left\|right> <宿主PID>` | 读自己的输入文件；写自己的私有文件；睡 200 ms；检查对方的私有文件是否可见；检查宿主 PID 能否被 `kill(pid,0)` 探到 | 两个并发执行互相看不到对方的工作区，也看不到宿主进程；输入没有串到别的执行。用不同的退出码（3、4、5、6）区分是哪一类隔离失效 |
| P3 | `echo` | 把 stdin 原样写到 stdout，再往 stderr 写一行 `probe-stderr` | 证明 stdin、stdout、stderr 是三条独立的流，各自原样送达 |
| P4 | `exit N` | 以 N 为退出码 | 用户程序的退出码要原样到达 |
| P5 | `segv` | `raise(SIGSEGV)` | 被信号终止时，退出码为 −1、信号号为 11 |
| P6 | `privilege` | 尝试 `setuid(0)`；成功或有效 uid 为 0 就报错 | 用户程序不能提权；`setuid` 通常在过滤器里被拒（SIGSYS），即使放行也只能得到拒绝 |
| P7 | `sleep` | 睡 4 秒 | 一个「不占 CPU 但占墙钟」的程序：用来测墙钟超时、取消、并发 |
| P8 | `background` | 派生一个 `setsid` 的睡眠子进程，**故意不等待**，自己立刻退出 | 主进程退出后，后代也必须由执行器整组回收，不能等它自己退出 |
| P9 | `processes` | 反复派生睡眠子进程，最多 80 个，被拒就报告 | 进程数上限生效：扩张要被有界地拒绝，而不是无限增长 |
| P10 | `threads` | 反复创建睡眠线程，最多 80 个 | 线程也计入同一个数量上限 |
| P11 | `cpu` | 空转死循环 | CPU 时间上限生效 |
| P12 | `memory` | 反复 `malloc` 1 MiB 并**每页写一个字节** | 只申请地址空间不算占内存，必须真的触碰物理页，内存上限才会被触发 |
| P13 | `output` | 无限向 stdout 写 4 KiB 块 | 输出上限生效 |
| P14 | `network` | 创建 `AF_INET` socket | 网络被 seccomp 禁止 |
| P15 | `x32` | 用 x32 ABI 的方式调用 `getpid` | 过滤器不能被「换一种系统调用 ABI」绕开 |
| P16 | `symlink` | 创建指向 `/etc/passwd` 的符号链接 | 过滤器不允许创建链接，走到返回就说明策略放行了 |
| P17 | `hardlink` | 对 `/usr/bin/probe` 创建硬链接 | 同上 |
| P18 | `write` | 写 `out.txt` 和 `sub/deep.txt` | 提供可取回的产物（属于产物规则，不是提交行为，见第 9 节） |
| P19 | 以 `true` 的名字运行 | 直接返回 0 | 构造一个最小的合法命令（夹具，不是案例） |

## 2. 执行器测试 `apps/sandbox/tests/run_tests.py`

用上面的探针直接调用执行器，断言执行器的原始事实。

| 编号 | 测试 | 断言了什么 | 目的 / 为什么这样写 |
|---|---|---|---|
| R1 | `test_echo_streams_and_exit` | `exitCode=0`、`signal=0`、`reason=""`、`error=""`；stdout 与 stdin 一致；stderr 为 `probe-stderr`；`memoryBytes>0` | 最基本的一次成功执行：流和事实都对 |
| R2 | `test_exit_code_and_signal` | `exit 7` 的退出码为 7；`segv` 的信号为 11、`reason` 为空 | 退出码与信号要区分开 |
| R3 | `test_identity_and_filesystem` | Uid/Gid 为 payload 身份；`ppid=1`；五组 capability 全 0；`NoNewPrivs=1`、`Seccomp=2`；宿主文件不存在；根写被拒；`cwd=/work`；宿主根不可见；输入文件可读；`/tmp` 可写；无额外 fd；fd 只指向管道或 `.stdin` | 用户程序运行环境的完整体检 |
| R4 | `test_command_resolves_from_workspace` | 把探针以别的名字放进工作区并设为可执行，用裸名称运行得到预期退出码 | 用户自己的（编译产物）可执行文件可以在工作区里按裸名称运行 |
| R5 | `test_cpu_limit` | 限制 500 ms，`reason=cpu`、`cpuNs≥500 ms`、`clockNs<2 s` | CPU 超限由 CPU 用量触发，而不是等墙钟 |
| R6 | `test_wall_limit` | 限制 500 ms，`reason=wall`、`clockNs≥500 ms`、`cpuNs<100 ms` | 睡眠程序不占 CPU，必须由墙钟终止 |
| R7 | `test_memory_limit_is_task_oom` | 64 MiB，`reason=""`、`signal=9`、`oom>0`、`oomKill>0` | 内存超限看 OOM 事实，不能只看信号 9；`reason` 必须为空 |
| R8 | `test_output_limit` | stdout 上限 4096，`reason=output`、`outputExceeded=true`、stdout 恰好 4096 字节 | 超限即停止，保留的字节数等于上限 |
| R9 | `test_network_is_killed_by_seccomp` | `signal=31` | 联网被 SIGSYS 杀死 |
| R10 | `test_x32_abi_is_killed` | `signal=31` | x32 ABI 同样被杀 |
| R11 | `test_process_and_thread_limits` | 进程数上限 8 时，`pidsMaxEvents>0`，stdout 以 `spawn-denied` / `threads-denied` 开头 | 扩张被拒，且程序自己能观察到被拒 |
| R12 | `test_background_processes_are_reclaimed` | `exitCode=0`、`reason=""`、stdout 以 `background-started` 开头、总耗时小于 2 秒 | 后代自己要睡 4 秒，**执行必须在它自行退出之前完成整组回收**，否则回收是假的 |
| R13 | `test_privilege_and_peer_isolation` | `privilege`：`signal=31`，或退出码 0 且输出 `denied`；`isolation`：两个 box 并发，各自输出 `private <名>` | 提权不成功；并发执行互相隔离 |
| R14 | `test_links_cannot_be_created` | 符号链接和硬链接都是 `signal=31` | 不能创建链接 |
| R15 | `test_parallel_boxes` | 两个 box 并发各跑 `cpu`，限制 300 ms，两个都是 `reason=cpu` | 两个 box 互不影响，各自独立计量 |
| R16 | `test_repeated_runs_do_not_leak` | 连续 200 次 `exit 0` | 反复执行不泄漏资源（组、挂载、fd） |

## 3. 内核套件的执行器层脚本 `deploy/sandbox-linux/tests/`

| 编号 | 脚本与案例 | 做了什么 | 目的 / 为什么这样写 |
|---|---|---|---|
| K1 | `smoke.py` | 顺序运行 `identity → cpu → memory → output → identity → network → identity`，内存上限 64 MiB、stdout 8192、stderr 4096；每次后检查 `jobs/` 下没有遗留组 | **夹在中间的 `identity` 证明前面恶意程序没有污染后面**；每次执行结束组必须已删 |
| K2 | `extended.py` 的 `background`、`processes`、`threads` | 同 P8–P10，断言总耗时小于 2 秒、`pidsMaxEvents>0` | 回收要及时；扩张要被拒 |
| K3 | `extended.py` 的 `sleep` | 墙钟 150 ms | `reason=wall`，观测耗时小于 1 秒：墙钟终止要及时 |
| K4 | `cpp_limits.py` 的 `cpu-tree` | 编译真实 C++，运行：`fork` 一个子进程，然后父子都死循环 | **整组累计 CPU** 的判定：多进程不能靠分摊绕过 CPU 上限；`reason=cpu`、`cpuNs≥1 s`、`clockNs<2 s` |
| K5 | `cpp_limits.py` 的 `kill` | 向自己 `raise(SIGKILL)` | **SIGKILL 本身不是 CPU 或内存超限的证据**：`signal=9`、`reason=""`、`oomKill=0` |
| K6 | `cpp_limits.py` 的 `background` | `setsid` 的后代睡 4 秒，父进程立刻退出 | 同 P8；断言 `clockNs<1 s` |
| K7 | `cpp_limits.py` 的 `threads` | 创建 80 个线程直到被拒 | `pidsMaxEvents>0`，输出以 `denied ` 开头 |
| K8 | `cpp_limits.py` 的 `memory` | 64 MiB 限制下不断申请并触碰内存 | `oomKill>0`、`reason=""`、`signal=9` |
| K9 | `cpp.py` | 真实 `g++` 编译一个打印 `cherry-linux-cpp` 的程序，取回产物，在**新的执行组**里运行 | 编译产物要能跨执行取回、再运行；输入和产物都经过执行器的 box |

## 4. 经 judge 的探针 `deploy/sandbox-linux/tests/judge_program.py`

一份 C++ 源码，从 stdin 第一行读「模式」，一次 trial 里的多个测试点各选一种模式，**编译一次、多次执行**。注释写明：judge 的 trial 只接受源码，运行时不带参数，所以模式只能走 stdin。

| 编号 | 模式 | 做了什么 | 目的 / 为什么这样写 |
|---|---|---|---|
| J1 | `echo` | 把输入后面的内容原样输出 | 基线：整条链路能得到 AC |
| J2 | `cpu` | 死循环 | CPU 超限 → `TLE` |
| J3 | `tree` | `fork()` 后父子都死循环 | **按整组累计 CPU 判定**：断言 `cpuNs` 落在 1 s 到 1.2 s 之间，不是墙钟 |
| J4 | `memory` | 不断申请 1 MiB 并写满 | `MLE` |
| J5 | `output` | 无限输出 | `OLE` |
| J6 | `kill` | `raise(SIGKILL)` | 判 `RE`，不能误判成 TLE 或 MLE |
| J7 | `nonzero` | `return 7` | 判 `RE` |
| J8 | `sleep` | 睡 2 秒 | 墙钟 150 ms 时判 `TLE`；正常时结论为运行成功（`RAN`） |
| J9 | `background` | `fork`，子进程 `setsid` 后睡 4 秒，父进程退出 | **墙钟上限设 1 秒，后代睡 4 秒**：如果判题机在等后代，就会 `TLE`；结论是 `RAN` 说明后代被整组回收 |
| J10 | `threads` | 创建 80 个线程直到被拒，打印 `denied` | 期望输出 `denied\n`，判 `AC` |
| J11 | `network` | `socket(AF_INET, …)` | 判 `RE` |
| J12 | `mount` | `mount("none","/tmp","tmpfs",…)` | 判 `RE` |
| J13 | `ptrace` | `ptrace(PTRACE_TRACEME,…)` | 判 `RE` |
| J14 | `hostfile` | 检查 `/etc/hostname` 是否可见，打印 `visible`/`hidden` | 期望 `hidden\n`，判 `AC` |
| J15 | `symlink` | 创建指向 `/etc/passwd` 的符号链接 | 判 `RE` |
| J16 | `magiclink` | 创建指向 `/proc/self/fd/0` 的符号链接 | 魔法链接同样不能创建，判 `RE` |
| J17 | `hardlink` | 创建文件后对它创建硬链接 | 判 `RE` |
| J18 | `identity` | 打印 `Uid`、`Seccomp`、`NoNewPrivs`、`CapEff` 行 | 取回用户程序眼里的身份（未写明由谁断言） |
| J19 | `hold` | 申请并写满 60 MiB，睡 3 秒 | **「低于自己的上限、但让总量超限」**的程序：用来区分任务自己的 OOM 与祖先 cgroup 的 OOM，见 F3 |
| J20 | `privilege` | `setuid(0)` 成功就打印 `escalated` 并返回 9，否则打印 `denied` | 期望 `denied\n`；允许 `AC` 或 `RE`，**但绝不能提权成功** |
| J21 | `isolation <标记> <宿主PID>` | 写自己的私有文件，睡 1 秒，检查对方文件和宿主 PID；打印 `private <名> peers=<n> host=<visible\|hidden>` | 并发执行互相不可见，期望 `private <名> peers=0 host=hidden` |
| J22 | `empty`（任何未登记的模式） | 直接返回 0 | 一个什么都不做的合法程序：用作基线、测内存不继承峰值、做 1000 次重复执行 |

## 5. 经 judge 的链路与故障测试

| 编号 | 位置 | 做了什么 | 目的 / 为什么这样写 |
|---|---|---|---|
| C1 | `judge_chain.py::smoke` | 一次提交，16 个测试点分别用 J1–J17 的模式，逐点断言 verdict | 「一次编译，多个测试点：每个测试点经执行层单独执行一次，结论由 judge 按执行事实映射」。期望：`echo` AC、`cpu`/`tree` TLE、`memory` MLE、`output` OLE、`empty` RAN、`kill`/`nonzero`/`network`/`mount`/`ptrace`/`symlink`/`magiclink`/`hardlink` RE、`threads`/`hostfile` AC |
| C2 | `judge_chain.py::smoke` 的 `empty` | 空程序，断言内存小于 16 MiB | 不继承前面程序的内存峰值 |
| C3 | `judge_chain.py::smoke` 的 `background` | 墙钟 1 s | 同 J9 |
| C4 | `judge_chain.py::smoke` 的 `wall` | `sleep`，墙钟 150 ms | 判 `TLE` |
| C5 | `judge_chain.py::smoke` 的取消 | 发起请求后，**调用方断开 HTTP 连接**；等执行组被回收；再提交一个空程序 | 调用方放弃后，执行被取消并回收，之后的判题正常 |
| C6 | `judge_chain.py::repeat` | 10 次 trial，每次 100 个空测试点，共 1000 次执行 | 长序列重复执行不泄漏，记录 CPU 中位数和峰值内存 |
| C7 | `judge_chain.py::concurrency` | 两个并发请求各跑 `sleep` | 峰值同时存在 2 个执行组，总耗时小于 7 秒：并行是真的并行 |

## 6. 故障与恢复里涉及提交行为的部分 `fault_batch.py`

| 编号 | 案例 | 做了什么 | 目的 / 为什么这样写 |
|---|---|---|---|
| F1 | `missing-command` | 用 `python` 提交 `print(1)`，而 rootfs 只有 C++ 工具链 | 命令不存在是平台错误：判 `SE`，原因里带 `errno=2` |
| F2 | `peer-isolation-and-privilege` | 两个并发请求各跑 `isolation left/right`；再跑 `privilege`；同时采样 payload 与 init 身份 | 并发时各用各的身份（61002、61003 与 61006、61007）；提权结果只能是 `AC`（被拒）或 `RE`，**绝不能成功** |
| F3 | `aggregate-memory` | 两个请求各用 `hold`（60 MiB，远低于各自 128 MiB 限额），运行时把 `jobs` 的总内存压到 96 MiB | 祖先 cgroup 的 OOM 杀掉的是**平台问题**，不能判成用户超内存：期望 `SE`，信息含 `without task-local OOM`。**注：总量上限已经删除，这个案例需要重新评估** |
| F4 | `task-local-memory` | `memory` 模式 | 任务自己的 OOM 仍判 `MLE`，不受 F3 影响 |

故障与恢复的其余案例（排队时断开、init 被杀、judge 或执行器崩溃重启、优雅停机）测的是平台自身，运行中的程序只是夹具，不在本清单内。

## 7. 业务验收夹具 `deploy/sandbox-linux/tests/acceptance/`

11 个独立的 C++ 程序，用于已部署的业务节点验收（管理页/题目页）。`README.md` 写明了顺序和断言。

| 编号 | 文件 | 做了什么 | 目的 / 为什么这样写 | 预期 |
|---|---|---|---|---|
| A1 | `io.cpp` | 读两个数，输出和，往 stderr 写 `work048-stderr` | stdout 与 stderr 分别到达 | 完成；stdout `3\n`，stderr 含标记 |
| A2 | `compile_error.cpp` | `#error WORK048_EXPECTED_COMPILE_ERROR` | 故意的编译错误，诊断必须带出预设标记，**绝不能进入执行阶段** | `COMPILE_ERROR` |
| A3 | `runtime_error.cpp` | `return 7` | 非零退出 | `RUNTIME_ERROR` |
| A4 | `signal_kill.cpp` | `raise(SIGKILL)` | 「SIGKILL 本身不是 CPU 耗尽或 OOM 的证据」 | `RUNTIME_ERROR`，**不能映射成 TLE 或 MLE** |
| A5 | `cpu_limit.cpp` | 在 8 秒内持续空转，若跑到头返回 8 | 8 秒的守护界限同时限制了「限制器坏了」的情形；功能正常的 1 秒 CPU 预算必须在此之前终止它 | `TIME_LIMIT_EXCEEDED`；核对实际预算和超限差值 |
| A6 | `memory_limit.cpp` | 申请并触碰 320 MiB，若完成返回 10 | 只预留地址空间不是内存测试，必须触碰页；256 MiB 的组限制必须阻止它完成 | `MEMORY_LIMIT_EXCEEDED`；是该执行组的 OOM，不是节点总组的 OOM |
| A7 | `output_limit.cpp` | 向 stdout 写 512 个 4 KiB 块（2 MiB） | 输出超限 | `OUTPUT_LIMIT_EXCEEDED`；页面和响应的输出有界 |
| A8 | `empty.cpp` | 空程序 | **紧跟在 A7 之后运行**，验证低内存、不继承上一次的峰值，中间不重启服务 | 完成 |
| A9 | `sum.cpp` | 公开的 A+B 参考程序 | 正式提交基线，不含隐藏数据 | AC、6/6 |
| A10 | `wrong_answer.cpp` | 输出固定标记 `WORK048_WRONG_ANSWER` | 正式提交的 WA | WA |
| A11 | `calibration.cpp` | 读两个数输出和，读不到返回 2 | 校准用：CPU 1 s、内存 256 MiB、墙钟留空沿用系统策略 | 校准时使用 |

## 8. 其他层里内嵌的提交

| 编号 | 位置 | 内容 | 目的 / 为什么这样写 |
|---|---|---|---|
| L1 | `apps/judge-engine/judge/language/languages_e2e_test.go`：`TestEndToEndCpp` | 读两个数输出和（C++） | 注册表里的命令**真的能跑**：编译 → 收产物 → **换一个干净工作间** → 运行 |
| L2 | 同上：`TestEndToEndPython` | Python 的 A+B | 同上；本机缺解释器就跳过 |
| L3 | 同上：`TestEndToEndJavaWithInnerClass` | 带内部类的 Java A+B | **只收 `Main.class` 的话会 `NoClassDefFoundError`**，所以编译后打成 jar；A+B 这种简单题测不出来，等有人写内部类才会炸 |
| L4 | `apps/judge-engine/judge/api/trial_isolation_test.go` | 先跑空程序，再跑「输出约 94 MiB」的程序，再跑空程序 | 断言 `RAN`、`OLE`、`RAN`：**前一次的大输出不能影响后一次**（复现过宿主 sandbox 跨请求内存统计污染）。只在显式指向独立 Linux judge 时执行，不要指向承载真实提交的节点 |
| L5 | `apps/server/.../RealLinuxJudgeIntegrationTests.java` | A+B（AC）、`a-b`（WA）、`int main( {`（CE） | 经生产用的 HTTP 客户端连接真实 Linux judge，拿到 AC/WA/CE，且 `cpuNs`、`memoryBytes` 非负 |
| L6 | `scripts/work-002-e2e.py` | AC：A+B；WA：`a-b`；CE：`int main( {`；TLE：`while(true){x=x+1;}`；WA：回显第一行输入 | 全栈（Java 服务、MySQL、Kafka、真实 judge）里端到端拿到 AC/WA/CE/TLE；**回显隐藏输入的程序**用来证明 Java 服务的结果投影不泄漏隐藏数据（服务层行为，不是 judge 的） |
| L7 | 同上：worker 崩溃 | 提交一个死循环，运行中杀掉 judging worker，起一个后继 | 租约被后继接管后仍得到 `TLE`，且 `attempt_no≥2`（属于平台恢复，程序只是夹具） |
| L8 | `deploy/sandbox-linux/install/verify-native.py` | `puts("native isolation OK"); sleep(2)` | 给「观察所有任务线程的身份」提供一个运行 2 秒的程序（夹具，不是案例） |
| L9 | `deploy/sandbox-linux/install/verify-capabilities.py` | 在工作区写 `proof` 文件，`fork` 一个睡 10 秒的后代，输出 `cap-test` | 能力被删减后，写工作区和整组回收仍然正常：墙钟远小于后代的睡眠，说明后代被回收 |
| L10 | `deploy/sandbox-linux/install/verify-faults.py` | `fork(); sleep(15)`、`return 0` | 崩溃恢复要有一个运行中的 payload；睡眠超过观察窗口，**自然退出就不能伪装成清理成功**（夹具，不是案例） |

## 9. 不在本清单内的

以下测的是平台自己，不是「用户提交的行为」，只列名字：
- `run_tests.py`：`test_artifacts_are_bounded_regular_files`、`test_missing_command_is_platform_failure`、`test_cancel_by_closing_stdin`、`test_invalid_requests_are_refused`、`test_busy_box_is_refused`、`test_stale_group_is_reclaimed`。
- `extended.py`：`max_processes=1`（init 无法 fork，平台启动失败）、取消、ENOEXEC（可执行位但内容不是可执行格式）。
- `fault_batch.py`：排队时断开、init 被杀、judge 或执行器崩溃重启、优雅停机。
- `verify-*.py` 的安装、卸载、生命周期、能力边界集检查。
- Go 与 Java 单元测试里使用假后端的内嵌源码。

## 汇总

- 探针模式 19 个（P1–P19），执行器测试 16 个（R1–R16），内核套件执行器层 9 个（K1–K9）。
- 经 judge 的探针模式 22 个（J1–J22），链路与故障 11 个（C1–C7、F1–F4）。
- 验收夹具 11 个（A1–A11），其他层 10 个（L1–L10）。
- 其中**同一种行为在多处重复**的有：死循环（P11、J2、A5）、内存（P12、J4、A6、K8）、输出（P13、J5、A7）、后代回收（P8、J9、K6）、SIGKILL（J6、K5、A4）、提权（P6、J20）、隔离（P2、J21）、网络（P14、J11）。
- 需要重新评估的：F3（总量上限已删除）。

## 与目标清单的对照

对照 [judge-cases.md](./judge-cases.md) 与 [attack-cases.md](./attack-cases.md)，原先没被覆盖的案例已补进目标清单：

| 现有案例 | 补在哪里 |
|---|---|
| K4、J3（多进程累计 CPU） | 攻击样本 A15 `cpu_tree`；A6 的来源里去掉了误挂的 `tree` |
| K1、C2、A8、L4（污染检查） | 通用的「干净运行」：每个攻击样本之后运行 `attack-common/probe.cpp`（协议 §8） |
| R16、C6（长序列重复） | 攻击样本 A17 `leak_series`，用 manifest 的 `repeat` |
| R15、C7（并发各自计量） | 攻击样本 A16 `concurrent_cpu_spin`；C7 的平台并发部分留在原位 |
| P3、R1、A1（stderr 独立到达） | `a-plus-b` 的 `ac_stderr_note`；协议运行说明新增 `stderr` 断言 |
| L6（回显隐藏输入） | judge 一侧补「WA 的 `diff` 不含 `want`」通用检查；服务层投影不属于 benchmark，留在 Java 与全栈测试 |
| F1（语言不可用） | `a-plus-b` 的请求级用例 `unknown_language`、`language_not_installed` |
| J 系列、C 系列的 trial | 协议新增试运行用例（§3.5），`a-plus-b` 的 `trial_ac`、`trial_wa`、`trial_ran` |
| A2（CE 诊断内容） | `a-plus-b` 的 `ce_marker`；协议 `expect.json` 新增 `message` 断言 |
| P1/R3（身份与环境细节） | B4 补全 |
| P2（输入没串） | B11 的样本自检 |
| A5（超限差值） | A1、A15 的 `cpuNs` 上界断言 |
