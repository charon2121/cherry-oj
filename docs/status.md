# 各模块当前成熟度

本文只回答一个问题：**哪些部分已经能跑、哪些还是骨架。** 动手前用它判断自己要接的是「在既有实现
上改」还是「从零写第一版」——这两种活的做法完全不同。

规范怎么写见 [`coding-standards/README.md`](./coding-standards/README.md)，架构为什么这样分见
[`architecture.md`](./architecture.md)。

**完整 MVP（PRD §3.4 六条基线）2026-10-19 起已交付**，证据见 [`product.md`](./product.md) §3.4；
测试服务器节点 2026-11-02 按 `a80fd173` 重装为每门语言独立工具链的新布局（`cherry-home-v6`，C 与 C++ 两套 rootfs，
启动冒烟两门都通过），随后演练原地升级：升级到 v7、回滚到 v6、再回滚到 v7，每次 judge 停顿约 2.5–8 秒；升级到 rootfs
被改过的发布时启动脚本拒绝启动、自动切回 v7（健康检查改为单元退出即失败后，停顿约 5.5 秒）；`prune` 只删掉了失败的发布。
当时运行 `cherry-home-v7`（上一个发布 v6）：`verify-native` 通过，benchmark 判题 72 项与攻击样本 56 项全过。

2026-11-11，测试服务器补齐五个 Java 服务与 Web，应用发布为 `129d200c`（`/opt/cherry-app/current`），
复用上述原生判题节点；MySQL 8.4、Redis 7.4、Kafka Native 3.8.0 使用独立持久数据，应用服务由
`cherry-app-*.service` 管理并启用自启。Web 只监听 `127.0.0.1:18080`，通过 SSH 转发访问；业务服务与依赖也仅监听回环。
真实接口验证通过 C/C++ 同题 ACM 与 CORE、自测与正式提交、基础/推导/覆盖标定、切换和关闭基础语言入口后继续推导、
历史输入冻结及 C 的编辑器行号换算；真实浏览器 3 项通过，覆盖管理页、普通用户两门语言的草稿/自测/提交、CORE C 和 320px。
五服务健康、依赖无 OOM 与原生隔离冒烟均通过。另确认既有自测的 45 秒总预算保护：超预算返回
`422 RUN_LIMIT_UNSUPPORTED`，正式提交仍可执行；浏览器正向验证使用预算内标定。

2026-11-15，本机实现自测预算统一：编译阶段直接读取打包的语言契约，默认 180 秒预算上限可调低，
各层期限与取消清理按 [自定义运行协议](./protocols/custom-run.md) 对齐。隔离五服务全链路已验证 C 的
2 秒 CPU 自测、C/C++ 同题 ACM/CORE、标定切换、历史冻结与崩溃恢复；Java 共 338 项测试通过
（含另行连接原生节点补跑的客户端测试），Go 竞态测试、Web 176 项测试及自测浏览器 5 项通过。
同日已部署应用发布 `budget-20261115-2453f5311226`（对应代码 `1e207811`），保留旧发布 `129d200c` 与数据库快照。
真实接口通过 C/C++ 的 ACM、CORE、2 秒 CPU 自测及正式提交、标定切换与旧历史输入冻结；
真实浏览器的 C 自测持续约 65.2 秒后正常返回，后续运行和 CORE 两门语言提交仍成功，320px 页面通过。
五服务健康、依赖无 OOM、测试题已转为私有；原生节点进程保持不变。
隔离探针已修复只识别首个运行槽位的误报，第二槽位的完整隔离检查及资源回收通过，安装回执同步更新探针摘要。
本轮代码的 [CI](https://github.com/charon2121/cherry-oj/actions/runs/43694260533) 与
[Benchmark](https://github.com/charon2121/cherry-oj/actions/runs/43694260579) 通过；
[Business](https://github.com/charon2121/cherry-oj/actions/runs/43694294933) 共 16 项通过，
包括新增的原生 C/C++ 同题 ACM/CORE、2 秒 CPU 自测、标定切换与历史冻结，资源清理通过。

2026-11-15，测试环境新增 PyPy 8.0.1 / Python 3.11.17；当时应用发布为
`pypy-20261115-b1b5d1691dd0`，原生节点发布为 `cherry-home-pypy-gc-20261115`，安装了 C、C++、PyPy
三棵独立 rootfs。Java 的语言文件名、编译阶段声明与限制参数统一读取打包的 `contracts/languages.json`；
Web 源码下载也读取该契约，ACM 下载驱动文件名，CORE 下载用户文件名。PyPy 的运行命令、固定 GC 环境与错误语义
见[语言运行约定](./protocols/languages.md)；时间模型为 4.5 倍加 150ms、内存为 1.1 倍加 64MiB，
22 个原生样本点、132 次 C++/PyPy 对照运行支持保留这一保守参数，适用范围见[限制协议](./protocols/limits.md#11-实测记录)。
PyPy 原生运行时 9 项、既有判题 72 项、攻击样本 56 项及隔离、资源回收探针通过；节点回滚到上一发布再切回后，
运行时与隔离检查仍通过。真实接口覆盖三门语言的 ACM/CORE、基础/推导/覆盖标定、语法错误与历史冻结；
普通用户的 12 次自测/提交通过，真实浏览器另验证 PyPy 两种题型的自测、AC 提交、下载文件名和 CORE 编辑器行号。
五服务健康，回归题已恢复私有，临时转发已关闭。前一应用发布、节点发布与部署前数据库快照均保留。
本机 Java verify 共 342 项（默认跳过的原生客户端测试已另行连接节点补跑），Go 竞态测试与 vet、
rootfs 52 项、CI 工具 120 项、Web 181 项、浏览器多语言 5 项与缩放回归 1 项通过。

同日，应用发布工具已纳入 `deploy/app`，文件与恢复语义见[应用发布协议](./protocols/app-release.md)。
工具从指定提交导出独立源码，完成 Java verify 与 Web check/build，生成带完整源码身份及文件摘要的发布包；
新工具30项隔离测试在 macOS 与目标 Ubuntu 上均通过，脚本24项测试通过。测试服务器当前应用为 `app-31e60df07ee0-209b5d088b57`
（源码提交 `31e60df0`），上一发布为 `pypy-20261115-b1b5d1691dd0`。
真实升级、旧格式发布兼容、双向回滚，以及 current 已切换但日志尚未更新时的中断恢复通过；
五服务恢复健康，原生节点仍为 `cherry-home-pypy-gc-20261115`，主进程未变化。
恢复后真实浏览器通过 PyPy 的 ACM/CORE 自测、AC 提交、下载文件名与编辑器报错行号检查；回归题已恢复私有。

2026-11-16，新增 Java 21（Temurin 21.0.13+9 / HotSpot）的 ACM 与 CORE：编译后完整打包内部类和匿名类，
运行只接收 Main.jar；JVM 堆上限跟随阶段实际内存限制，整组资源统一计量。测试服务器原生节点升级到
`cherry-home-java-20261116-r3`，保留上一发布 `cherry-home-pypy-gc-20261115`，安装 C、C++、PyPy、Java
四棵独立 rootfs。Java 原生运行时 12 项、执行器 12 项、PyPy 9 项、既有判题 72 项与攻击样本 56 项通过；
性能对照后的隔离与资源回收复查通过，无任务进程、执行 cgroup 或工作文件残留。
22 个样本点、132 次 C++/Java 对照后，Java 参数为 3.2 倍加 280ms、内存 1.25 倍加 40MiB，
依据和适用范围见[限制协议](./protocols/limits.md#11-实测记录)。
本机最终判题程序与五服务隔离栈通过 `scripts/e2e.py`：Java 两种题型的基础/推导/覆盖限制、自测、
正式提交、用户行号、关闭基础语言入口及历史冻结均通过；真实浏览器另通过两种题型的自测与 AC 提交、
Main.java / Solution.java 下载及 CORE 运行错误行号和 320px 页面。
服务端 verify 共 343 项，342 项通过、1 项需要另行连接原生节点的客户端测试默认跳过；
Go 竞态测试与 vet、Web 183 项测试和构建、多语言浏览器 7 项通过。
本轮只升级原生节点，测试环境应用仍为 `app-31e60df07ee0-209b5d088b57`，尚未发布 Java 的应用迁移与 Web。
应用回执已使用新格式，不再把旧 `judgeRelease` 当作实际判题版本；旧发布清单、备份与操作记录均保留。
数据库迁移后拒绝自动回退由隔离故障测试覆盖；数据库与完整系统恢复仍需单独演练。

| 部分 | 状态 |
|---|---|
| 应用发布与升级 | ✅ [仓库工具](../deploy/app/README.md)支持指定提交构建、包核验、升级、回滚与中断恢复；测试服务器实际演练通过。仅接管已有应用安装，不创建基础设施或开放公网；数据库恢复未演练 |
| 同题多语言与限制标定 | ✅ C/C++/PyPy/Java 的 ACM、CORE 同题配置；基础、推导与覆盖限制；至少一门就绪即可公开；作答只列就绪语言、按语言恢复草稿。前三门已部署应用；Java 的原生节点、隔离业务栈与真实浏览器验证通过，应用发布边界见上文。模型及实测范围见 [运行限制协议](./protocols/limits.md#11-实测记录) |
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events / problem-test-data；题目没有版本；verdict 保持稳定 |
| 执行层（store, runner, pool, backend）与 C 执行器 | ✅ judge 进程内使用；真实内核隔离测试最后一次随旧 CI 于 2026-10-04 通过（`5f2dea7`），日常 CI 现只编译执行器，内核测试需按需在 Linux 机器上运行 |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ `testcase` 按请求里的 `testDataLocation` 读[协议](./protocols/testdata.md)目录（本地路径与 HTTP 都实测过） |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，执行层在进程内；可带 CORE 的用户文件（`solutionSource`），文件名按 `contracts/languages.json` 的 `files`；benchmark 的 `core-add` 在本机 judge 镜像（devhost）上 7 项全过（2026-10-17） |
| Docker 部署 | ✅ 单 judge 容器 Compose（开发与 MVP，devhost 后端） |
| `apps/server`（五个 Java 服务） | ✅ 业务链路已实现；2026-10-07 起题目无版本、测试数据写成协议目录；支持 C/C++/PyPy/Java 的核心代码文件组织与用户行号换算。本轮 verify 共 343 项，342 项通过、1 项连接原生节点的客户端测试默认跳过 |
| 五服务 + judge 全链路 | ✅ `scripts/e2e.py` 在隔离栈（MySQL/Redis/Kafka + 真实 judge 容器）通过：上传→校准→公开→AC/WA/CE/TLE、Kafka 与节点故障恢复、测试数据替换后旧校准过期并重新校准、公开题原地修改与取消公开（2026-10-07）；C++ 核心代码 A+B：公开检查拦住没有驱动代码的题、标定、公开、AC/WA、用户文件编译错误报在编辑器行号 `solution.cpp:4`、签名错误报在 `Main.cpp:6`、自测运行错误的 stderr 同样换算、冻结输入含驱动文件与前缀 + 用户代码、驱动代码不出现在公开接口/Kafka/服务日志、改驱动代码后提示重新标定（2026-10-18，提交 `5c8562c`）；测试服务器上的原生节点已按新发布重装并实测（真实 systemd 单元与 setuid 执行器下按地址判题、篡改得 SE、崩溃恢复，2026-10-07）；**Linux 业务闭环**（一次性 GitHub VM 上正式安装原生节点 + Kafka + 五服务 + 真实页面 `e2e-live`）15 项全过，手动触发 `.github/workflows/business.yml`（2026-10-19，run 39196126198，提交 `946ba8f`）；原生节点上的 CORE 由 Benchmark 工作流验证（`core-add` 7 项，2026-10-17） |
| `apps/web` | ✅ 题库、答题、提交、自测与管理工作台（含核心代码题的创建、驱动代码编辑与作答）；`npm run check` 与 Playwright（mock 后端）通过；`judge-node` 对隔离真实栈通过、`e2e-live` 在业务闭环 CI 中通过（2026-10-19） |

状态变化跟着实现走：某个模块从骨架变成可用时，把证据（提交号或 CI run）写进提交说明，再回来改这张表。
不要凭印象更新——「✅」在这里的含义是「有人跑通过并留下了证据」。
