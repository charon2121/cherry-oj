# 各模块当前成熟度

本文只回答一个问题：**哪些部分已经能跑、哪些还是骨架。** 动手前用它判断自己要接的是「在既有实现
上改」还是「从零写第一版」——这两种活的做法完全不同。

规范怎么写见 [`coding-standards/README.md`](./coding-standards/README.md)，架构为什么这样分见
[`architecture.md`](./architecture.md)。

**完整 MVP（PRD §3.4 六条基线）2026-10-19 起已交付**，证据见 [MVP 收尾验证](../development/works/2026-10-19-mvp-closure/00-work.md)；
尚未部署到现有使用环境，测试服务器节点仍是不支持 CORE 的 2026-10-14 版本。

| 部分 | 状态 |
|---|---|
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events / problem-test-data；题目没有版本；verdict 保持稳定 |
| 执行层（store, runner, pool, backend）与 C 执行器 | ✅ judge 进程内使用；真实内核隔离测试最后一次随旧 CI 于 2026-10-04 通过（`5f2dea7`），日常 CI 现只编译执行器，内核测试需按需在 Linux 机器上运行 |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ `testcase` 按请求里的 `testDataLocation` 读[协议](./protocols/testdata.md)目录（本地路径与 HTTP 都实测过） |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，执行层在进程内；可带 CORE 的用户文件（`solutionSource`），文件名按契约 `x-language-files`；benchmark 的 `core-add` 在本机 judge 镜像（devhost）上 7 项全过（2026-10-17） |
| Docker 部署 | ✅ 单 judge 容器 Compose（开发与 MVP，devhost 后端） |
| `apps/server`（五个 Java 服务） | ✅ 业务链路已实现；2026-10-07 起题目无版本、测试数据写成协议目录；2026-10-18 起支持 C++ 核心代码模式（驱动文件 + 语言前缀 + judging-service 换算报错行号）；`./mvnw clean verify` 312 项测试通过 |
| 五服务 + judge 全链路 | ✅ `scripts/work-002-e2e.py` 在隔离栈（MySQL/Redis/Kafka + 真实 judge 容器）通过：上传→校准→公开→AC/WA/CE/TLE、Kafka 与节点故障恢复、测试数据替换后旧校准过期并重新校准、公开题原地修改与取消公开（2026-10-07）；C++ 核心代码 A+B：公开检查拦住没有驱动代码的题、标定、公开、AC/WA、用户文件编译错误报在编辑器行号 `solution.cpp:4`、签名错误报在 `Main.cpp:6`、自测运行错误的 stderr 同样换算、冻结输入含驱动文件与前缀 + 用户代码、驱动代码不出现在公开接口/Kafka/服务日志、改驱动代码后提示重新标定（2026-10-18，证据 `development/works/2026-10-17-core-mode/e2e-evidence-2026-10-18.json`）；测试服务器上的原生节点已按新发布重装并实测（真实 systemd 单元与 setuid 执行器下按地址判题、篡改得 SE、崩溃恢复，2026-10-07）；**Linux 业务闭环**（一次性 GitHub VM 上正式安装原生节点 + Kafka + 五服务 + 真实页面 `e2e-live`）15 项全过，手动触发 `.github/workflows/business.yml`（2026-10-19，run 39196126198，证据 `development/works/2026-10-19-mvp-closure/`）；原生节点上的 CORE 由 Benchmark 工作流验证（`core-add` 7 项，2026-10-17） |
| `apps/web` | ✅ 题库、答题、提交、自测与管理工作台（含核心代码题的创建、驱动代码编辑与作答）；`npm run check` 与 Playwright（mock 后端）通过；`judge-node` 对隔离真实栈通过、`e2e-live` 在业务闭环 CI 中通过（2026-10-19） |

状态变化跟着实现走：某个模块从骨架变成可用时，在对应 WORK 的 VERIFY 里记下证据，再回来改这张表。
不要凭印象更新——「✅」在这里的含义是「有人跑通过并留下了证据」。
