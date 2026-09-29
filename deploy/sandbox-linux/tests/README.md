# Linux 隔离测试夹具

本目录是内核套件（`ci/kernel.py`）使用的测试驱动：在一次性 Linux 机器上，用静态探针与锁定的
C++ rootfs 验证 sandbox 执行器（`apps/sandbox`）及 sandbox HTTP 服务整条隔离链。它们会创建
systemd 单元、cgroup 与 setuid 文件，只能在 `/var/lib/cherry-sandbox-test/` 下的本次独占目录与
`cherry-sandbox-test-*` 单元中运行，不得用作正式节点 rootfs，也不得对已有服务运行。

## 夹具

`ci/prepare.py` 构建测试版执行器（受信配置编译为 `/var/lib/cherry-sandbox-test/executor.conf`）、
静态探针（`apps/sandbox/tests/probe.c`）与锁定的 C++ rootfs。`kernel.py` 为每批建独占目录：
复制 Go 的 sandbox 服务、以 `root:61001 4754` 安装测试版执行器，`prepare_fixture.py` 用探针生成
最小 rootfs 与 manifest（探针另以 `true` 的名字放一份），C++ 批次再复制锁定 rootfs。
数字身份：服务 61001，payload 61002 起，init 61006 起（box N 取基数加 N）；不创建系统账号。

## 直接调用执行器

- `holder.py <目录> <单元> [cpp]`：在 Delegate 单元内把自己移进 supervisor 叶子、建 jobs 并启用
  cpu/memory/pids，写测试配置后保持运行。与生产的 `install/sandbox-start.py` 做的事相同。
- `executor_client.py`：以服务身份准备 box、调用执行器、读回事实与产物；stdin 上接取消管道。
- `inspect_threads.py`：观察执行组内 init 与 payload 的全部线程身份、capability、namespace、
  只读挂载与组限额。
- `smoke.py`：身份、CPU、内存、输出与网络。
- `extended.py`：后台后代、进程与线程扩张、墙钟、低 pids 与取消。
- `cpp_limits.py`：隔离编译 C++ 后逐项验证多进程 CPU、普通 SIGKILL、后台后代、线程上限与 OOM。
- `cpp.py`：手动冒烟，隔离编译、取回产物、在新执行组中运行。

执行器自己的真实内核测试（`apps/sandbox/tests/run_tests.py`）也在独立委派单元内运行一遍。

## HTTP 整链与故障

`http_service.py` 按生产单元的方式启动 sandbox HTTP 服务：服务身份非 root、无有效能力，边界集只
保留执行器需要的 8 项，Delegate 委派；由 `sandbox-start.py` 核对 rootfs、建组后 exec 服务。

- `chain_batch.py <目录> smoke|repeat|concurrency <单元>` 启动 HTTP 服务与驱动 `http_chain.py`：
  隔离编译 C++、产物引用、状态映射、资源、后台后代、断连与链接系统调用拒绝；repeat 连续 1000 次；
  concurrency 验证两个执行组实际重叠。每批对比 FD、执行组、box、blob、任务进程与挂载快照。
- `fault_batch.py <目录> [capacity]`：命令缺失、错误可执行格式、排队断连、init 强杀、HTTP 强杀后
  重启、执行器强杀后重启与正常停服；capacity 批次验证队列饱和、并发 box 的身份与文件隔离、handler
  饱和、祖先 OOM 与任务自身 OOM。发送信号前用 pidfd 核验 UID、可执行文件与 cgroup。

每批结束停止本批单元，核对无任务进程、挂载与单元组残留后才清理目录；未知条目不得自动清除。

## 取证工具

`acceptance/` 为业务验收的只读取证工具，见其 README。
