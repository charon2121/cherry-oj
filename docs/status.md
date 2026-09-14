# 各模块当前成熟度

本文只回答一个问题：**哪些部分已经能跑、哪些还是骨架。** 动手前用它判断自己要接的是「在既有实现
上改」还是「从零写第一版」——这两种活的做法完全不同。

规范怎么写见 [`coding-standards/README.md`](./coding-standards/README.md)，架构为什么这样分见
[`architecture.md`](./architecture.md)。

| 部分 | 状态 |
|---|---|
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events；run / verdict 保持稳定 |
| sandbox（store, container, runner, pool, api, cmd） | ✅ 可独立 `curl` |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，可与 sandbox 双进程联调 |
| Docker 部署 | ✅ judge / sandbox 双容器 Compose（开发与 MVP） |
| `apps/server`（五个 Java 服务） | ✅ Maven 聚合、独立端口、健康检查与基础测试；业务 API 待实现 |
| `apps/web` | ✅ React、Router、Query、样式、组件与测试工具骨架；业务页面待实现 |

状态变化跟着实现走：某个模块从骨架变成可用时，在对应 WORK 的 VERIFY 里记下证据，再回来改这张表。
不要凭印象更新——「✅」在这里的含义是「有人跑通过并留下了证据」。
