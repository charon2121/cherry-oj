# 各模块当前成熟度

本文只回答一个问题：**哪些部分已经能跑、哪些还是骨架。** 动手前用它判断自己要接的是「在既有实现
上改」还是「从零写第一版」——这两种活的做法完全不同。

规范怎么写见 [`coding-standards/README.md`](./coding-standards/README.md)，架构为什么这样分见
[`architecture.md`](./architecture.md)。

| 部分 | 状态 |
|---|---|
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events / problem-test-data；题目没有版本；verdict 保持稳定 |
| 执行层（store, runner, pool, backend）与 C 执行器 | ✅ judge 进程内使用；真实内核隔离测试最后一次随旧 CI 于 2026-10-04 通过（`5f2dea7`），日常 CI 现只编译执行器，内核测试需按需在 Linux 机器上运行 |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ `testcase` 按请求里的 `testDataLocation` 读[协议](./testdata-protocol.md)目录（本地路径与 HTTP 都实测过） |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，执行层在进程内 |
| Docker 部署 | ✅ 单 judge 容器 Compose（开发与 MVP，devhost 后端） |
| `apps/server`（五个 Java 服务） | ✅ 业务链路已实现；2026-10-07 起题目无版本、测试数据写成协议目录；单元与 Testcontainers 测试通过 |
| 五服务 + judge 全链路 | ✅ `scripts/work-002-e2e.py` 在隔离栈（MySQL/Redis/Kafka + 真实 judge 容器）通过：上传→校准→公开→AC/WA/CE/TLE、Kafka 与节点故障恢复、测试数据替换后旧校准过期并重新校准、公开题原地修改与取消公开（2026-10-07）；Linux 原生部署路径（`deploy/sandbox-linux/ci`）已改写但未在 Linux 机器上运行 |
| `apps/web` | ✅ 题库、答题、提交、自测与管理工作台；`npm run check` 与 Playwright（mock 后端）通过；`judge-node` 与 `e2e-live` 需要真实栈，尚未对新栈运行 |

状态变化跟着实现走：某个模块从骨架变成可用时，在对应 WORK 的 VERIFY 里记下证据，再回来改这张表。
不要凭印象更新——「✅」在这里的含义是「有人跑通过并留下了证据」。
