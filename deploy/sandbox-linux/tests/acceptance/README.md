# TASK-100 业务验收夹具

仅通过已登录的 Cherry OJ 管理页/题目页执行。程序为本项目静态测试夹具，不含隐藏测试数据。
资源类夹具只交给已核验的 Linux 后端；本机只做语法检查。切换对象、审批与回退步骤见
[`TASK-100`](../../../../development/works/WORK-048/60-task-TASK-100.md)。

## 前置条件

- 当前 ACTIVE 为受审的新 Linux 环境，指纹与 TASK-099 最终记录一致。
- 新修订通过既有入口部署6组数据、独立校准并发布；记录新版本、环境、部署及校准ID。
- 校准使用 `calibration.cpp`，CPU 1 s、内存256 MiB、墙钟留空沿用系统策略；读回实际限制。
- 单并发串行执行；每个自定义请求设60 s观察期限，超时停止后续测试并检查后端回收。
  浏览器取消不是清理成功证据。不要自动重试正式提交，先核对已有记录。

## 顺序与断言

| 顺序 | 入口 / 夹具 | 输入 | 预期 |
|---|---|---|---|
| 1 | 自定义 / io.cpp | `1 2` + 换行 | COMPLETED；stdout为`3\n`，stderr为`work048-stderr\n` |
| 2 | 自定义 / compile_error.cpp | 空 | COMPILE_ERROR，诊断包含预设编译错误标记 |
| 3 | 自定义 / runtime_error.cpp | 空 | RUNTIME_ERROR |
| 4 | 自定义 / signal_kill.cpp | 空 | RUNTIME_ERROR，不能映射成TLE/MLE |
| 5 | 自定义 / cpu_limit.cpp | 空 | TIME_LIMIT_EXCEEDED；核对实际1 s CPU预算和超限差值 |
| 6 | 自定义 / memory_limit.cpp | 空 | MEMORY_LIMIT_EXCEEDED；对应执行组OOM事实，不是节点总组OOM |
| 7 | 自定义 / output_limit.cpp | 空 | OUTPUT_LIMIT_EXCEEDED；页面/响应输出有界 |
| 8 | 紧接上例，自定义 / empty.cpp | 空 | COMPLETED；低内存，不继承上次峰值；中间不重启服务 |
| 9 | 正式提交 / sum.cpp | 使用部署数据 | AC、6/6；记录submissionId并验证代码回看 |
| 10 | 正式提交 / wrong_answer.cpp | 使用部署数据 | WA；记录submissionId并验证归属新版本 |

CPU监测粒度目前为5 ms，采样/调度/杀组都有额外延迟，不能承诺5 ms硬误差界。
记录实际cpuNs、`cpuNs - effectiveLimits.cpuNs`及运行阶段墙钟；HTTP耗时另列，包含编译、
SSH与服务开销，不能冒充用户程序墙钟。若公开响应没有运行墙钟，从同次后端执行证据关联，
不得从页面总耗时推算。旧约10 s才停止的问题应由CPU终止事实和独立墙钟实测共同证明已解决。

memoryBytes来自每次独立cgroup的memory.peak，不是RSS。OOM边缘可能超过memory.max；
不以简单的峰值比较替代memory.events及终止事实。输出摘要只记录长度/截断标记，避免保存大段输出。

每例记录请求标识、终态、资源及当前节点身份；正式提交保留原记录，不删除验收审计。
结束检查专用UID任务、执行子组、工作区与产物引用，按既有生命周期规则处理项目拥有的资源。
本次真实结果见 WORK-048/VERIFY-049 的“TASK-100 实际部署与业务闭环”。

## 取证工具

`observe_run.py` 通过SSH标准输入在目标服务器运行，60 s自动结束，只读观察专用payload UID的
`Main`进程及其cgroup。5 ms采样得到可见生存区间，受调度延迟影响；不包含编译时间，也不等于
精确的exec/wait时间。首轮错误匹配小写main而未捕获，修正后只补测了一次CPU用例，共9次自定义运行。

`sh deploy/sandbox-linux/tests/acceptance/read-evidence.sh` 在仓库根执行，复用judging-service已构建JAR
与既有配置加载方式；静态Java取证器限定本次v4/新环境/数据ID，只读事务、每次查询10 s/最多2行，
只输出环境、校准、部署及切换审计摘要。没有任意SQL入口，不启动应用、不执行迁移、不输出秘密；
临时解包目录退出时删除。它不能用于部署、校准或切换。缺JAR时先使用项目正常构建流程，不自动重建后端。
