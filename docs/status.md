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

| 部分 | 状态 |
|---|---|
| 同题多语言与限制标定 | ✅ 2026-11-10：C/C++ 的 ACM、CORE 同题配置；基础、推导与覆盖限制；至少一门就绪即可公开；作答只列就绪语言、按语言恢复草稿。Java 相关服务 verify 与本机隔离全栈 `scripts/e2e.py` 通过；实测范围见 [运行限制协议](./protocols/limits.md#11-实测记录)。测试服务器部署记录仍以上方 2026-11-02 为准 |
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
