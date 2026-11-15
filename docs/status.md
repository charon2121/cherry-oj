# 各模块当前成熟度

本文只回答一个问题：**哪些部分已经能跑、哪些还是骨架。** 动手前用它判断自己要接的是「在既有实现
上改」还是「从零写第一版」——这两种活的做法完全不同。

规范怎么写见 [`coding-standards/README.md`](./coding-standards/README.md)，架构为什么这样分见
[`architecture.md`](./architecture.md)。

**完整 MVP（PRD §3.4 六条基线）2026-10-19 起已交付**，证据见 [`product.md`](./product.md) §3.4；
测试服务器节点 2026-11-02 按 `a80fd173` 重装为每门语言独立工具链的新布局（`cherry-home-v6`，C 与 C++ 两套 rootfs，
启动冒烟两门都通过），随后演练原地升级：升级到 v7、回滚到 v6、再回滚到 v7，每次 judge 停顿约 2.5–8 秒；升级到 rootfs
被改过的发布时启动脚本拒绝启动、自动切回 v7（健康检查改为单元退出即失败后，停顿约 5.5 秒）；`prune` 只删掉了失败的发布。
当前运行 `cherry-home-v7`（上一个发布 v6）：`verify-native` 通过，benchmark 判题 72 项与攻击样本 56 项全过。

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

| 部分 | 状态 |
|---|---|
| 同题多语言与限制标定 | ✅ C/C++ 的 ACM、CORE 同题配置；基础、推导与覆盖限制；至少一门就绪即可公开；作答只列就绪语言、按语言恢复草稿。Java 相关服务 verify 与本机隔离全栈 `scripts/e2e.py` 通过；2026-11-11 已部署到测试服务器并通过上述原生节点与真实浏览器验证。模型及本机实测范围见 [运行限制协议](./protocols/limits.md#11-实测记录) |
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events / problem-test-data；题目没有版本；verdict 保持稳定 |
| 执行层（store, runner, pool, backend）与 C 执行器 | ✅ judge 进程内使用；真实内核隔离测试最后一次随旧 CI 于 2026-10-04 通过（`5f2dea7`），日常 CI 现只编译执行器，内核测试需按需在 Linux 机器上运行 |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ `testcase` 按请求里的 `testDataLocation` 读[协议](./protocols/testdata.md)目录（本地路径与 HTTP 都实测过） |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，执行层在进程内；可带 CORE 的用户文件（`solutionSource`），文件名按 `contracts/languages.json` 的 `files`；benchmark 的 `core-add` 在本机 judge 镜像（devhost）上 7 项全过（2026-10-17） |
| Docker 部署 | ✅ 单 judge 容器 Compose（开发与 MVP，devhost 后端） |
| `apps/server`（五个 Java 服务） | ✅ 业务链路已实现；2026-10-07 起题目无版本、测试数据写成协议目录；2026-10-18 起支持 C++ 核心代码模式（驱动文件 + 语言前缀 + judging-service 换算报错行号）；`./mvnw clean verify` 312 项测试通过 |
| 五服务 + judge 全链路 | ✅ `scripts/e2e.py` 在隔离栈（MySQL/Redis/Kafka + 真实 judge 容器）通过：上传→校准→公开→AC/WA/CE/TLE、Kafka 与节点故障恢复、测试数据替换后旧校准过期并重新校准、公开题原地修改与取消公开（2026-10-07）；C++ 核心代码 A+B：公开检查拦住没有驱动代码的题、标定、公开、AC/WA、用户文件编译错误报在编辑器行号 `solution.cpp:4`、签名错误报在 `Main.cpp:6`、自测运行错误的 stderr 同样换算、冻结输入含驱动文件与前缀 + 用户代码、驱动代码不出现在公开接口/Kafka/服务日志、改驱动代码后提示重新标定（2026-10-18，提交 `5c8562c`）；测试服务器上的原生节点已按新发布重装并实测（真实 systemd 单元与 setuid 执行器下按地址判题、篡改得 SE、崩溃恢复，2026-10-07）；**Linux 业务闭环**（一次性 GitHub VM 上正式安装原生节点 + Kafka + 五服务 + 真实页面 `e2e-live`）15 项全过，手动触发 `.github/workflows/business.yml`（2026-10-19，run 39196126198，提交 `946ba8f`）；原生节点上的 CORE 由 Benchmark 工作流验证（`core-add` 7 项，2026-10-17） |
| `apps/web` | ✅ 题库、答题、提交、自测与管理工作台（含核心代码题的创建、驱动代码编辑与作答）；`npm run check` 与 Playwright（mock 后端）通过；`judge-node` 对隔离真实栈通过、`e2e-live` 在业务闭环 CI 中通过（2026-10-19） |

状态变化跟着实现走：某个模块从骨架变成可用时，把证据（提交号或 CI run）写进提交说明，再回来改这张表。
不要凭印象更新——「✅」在这里的含义是「有人跑通过并留下了证据」。
