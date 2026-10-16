# 协议

这里集中放**跨进程、跨语言的约定**：两端各由不同的代码实现，只靠这份约定对齐。它与 `docs/` 里的设计说明不同：
设计说明解释「为什么这样做」，可以讨论取舍；协议规定「两端必须怎样」，是接口真源，改它等于改接口。

## 每份协议文档必须具备

1. **参与方与传输：** 谁和谁通信，经过什么（HTTP、Kafka、`exec`、文件目录），谁发起；
2. **报文格式：** 每个字段的名字、类型、取值范围与上限；有机读 schema 的指向 schema，没有的写全；
3. **成功与失败：** 退出码或状态码、拒绝的原因与消息、哪些失败可重试、哪些要停止使用资源；
4. **怎么调用：** 一个可以直接照做的最小例子（命令或请求），写在文档开头；
5. **版本与兼容：** 版本号放在哪、未知字段和未知版本怎么处理、改动时哪些消费者受影响；
6. **真源：** 以契约文件还是本文档为准。不列各个实现的位置：实现会移动，文档里的位置会悄悄过期；
7. **实测记录：** 在真实环境验证过的行为，注明日期与环境。

有契约文件的协议以 [`contracts/`](../../contracts/) 为真源，这里只给索引，不重复字段表。

## 改动规则

- 先改真源（契约或本目录文档），再改类型与实现；两端的实现和测试必须在同一次改动里对齐。
- 新增协议时，在下面的总览里加一行，并按上面的结构写文档。
- 修改协议之前先说明为什么变、哪些消费者受影响。

## 协议总览

项目里不同进程、不同语言之间靠下面这些约定对话。**改任何一条之前先看它的真源**；有机读 schema 的以
[`contracts/`](../../contracts/) 为准，先改契约、再改类型、再改实现。

### 接口协议

| # | 协议 | 两端 | 传输与形态 | 真源 |
|---|---|---|---|---|
| 1 | Web API | 浏览器 ↔ Gateway | REST；成功用 `ApiSuccess` 信封，失败用 RFC 9457 | [`web-api.openapi.json`](../../contracts/web-api.openapi.json) |
| 2 | Submission 内部 API | judging-service → submission-service（拉取 JudgeInput 等） | HTTP，服务令牌 | [`submission-internal.openapi.json`](../../contracts/submission-internal.openapi.json) |
| 3 | 题目快照 | submission-service ← problem-service | HTTP，服务令牌 | [`problem-judge-snapshot.schema.json`](../../contracts/problem-judge-snapshot.schema.json) |
| 4 | 执行配置 | submission-service ← judging-service | HTTP，服务令牌 | [`execution-profile.schema.json`](../../contracts/execution-profile.schema.json) |
| 5 | 自定义运行 | submission-service → judging-service | HTTP，服务令牌 | [`custom-run-internal.schema.json`](../../contracts/custom-run-internal.schema.json) |
| 6 | 测试数据读取 | judging-service ← problem-service | HTTP，服务令牌；只返回地址、指纹、测试点数 | [`problem-test-data.schema.json`](../../contracts/problem-test-data.schema.json) |
| 7 | 判题请求 | judging-service → judge（Go） | `POST /judge`，同步返回；HTTP 状态不表示判题结论 | 字段：[`judge.schema.json`](../../contracts/judge.schema.json)；调用语义：[`judge-api.md`](./judge-api.md) |
| 8 | 节点控制 | judge → judging-service（注册、心跳） | HTTP，共享控制令牌 | [`judge-node.schema.json`](../../contracts/judge-node.schema.json) |
| 9 | 判题事件 | submission-service ↔ judging-service | Kafka：`judge.requests.v1`、`judge.lifecycle.v1` | [`judge-events.schema.json`](../../contracts/judge-events.schema.json) |
| 10 | 测试数据协议 | problem-service 写、judge 读 | 目录 + `testdata.json`，本地路径或 HTTP(S) | [`testdata.md`](./testdata.md) |
| 11 | 执行器协议 | judge 进程 → sandbox 执行器 | `exec` + box 目录 + NUL 分隔的请求 + 一行 JSON 事实 | [`sandbox-executor.md`](./sandbox-executor.md) |
| 12 | 身份传递 | Gateway → 各业务服务 | 委派的内部 JWT；user-service 提供 JWKS 与 metadata | [`backend.md`](../backend.md) |
| 13 | 服务间鉴权 | gateway→user、submission→problem、submission→judging、judging→submission、judging→problem | Bearer 服务令牌：调用方用自己的私钥向 user-service 换取，`aud` 为被调方、`sub` 须在被调端点的允许名单里 | [`apps/server/CONFIGURATION.md`](../../apps/server/CONFIGURATION.md) |
| 14 | 部署配置格式 | 安装器 ↔ 执行器与 judge | `executor.conf`、`judge-start.json`、`deployment.json`（部署清单）、`plan.json` | [`deploy/sandbox-linux/install/README.md`](../../deploy/sandbox-linux/install/README.md) |
| 15 | 安装布局 | 安装器、运维 ↔ 执行器与 judge | `/opt/cherry-oj` 的目录结构、属主、权限，以及家目录之外的系统位置 | [`install-layout.md`](./install-layout.md) |
| 16 | benchmark | 语料 / 运行者 ↔ 被测 judge 与 sandbox | 两个入口（`POST /judge`、直接调用执行器）；语料目录、`expect.json`、运行说明、报告 JSON、退出码 | [`benchmark.md`](./benchmark.md) |

`contracts/` 里另有三份**数据模型与词表**，不是有两端的接口，但同样是跨服务的真源：
[`submission.json`](../../contracts/submission.json)（用户可见的提交读模型）、
[`judge-input.schema.json`](../../contracts/judge-input.schema.json)（提交时冻结的判题输入）、
[`verdict.json`](../../contracts/verdict.json)（verdict 集合）。

### 横切约定

| 约定 | 内容 | 真源 |
|---|---|---|
| 追踪 | HTTP 只传 W3C `traceparent` / `tracestate`，不传 baggage，追踪字段不进入请求体；`X-Request-Id` 只关联同一次同步调用 | [`logging.md`](../logging.md)、[`architecture.md`](../architecture.md) |
| 错误格式 | RFC 9457 `application/problem+json`，带稳定的 `code` 与 `meta.requestId` | [`architecture.md`](../architecture.md)、[`backend.md`](../backend.md) |
| 幂等与账号前置 | 创建提交要 `Idempotency-Key`；`X-Expected-User-Id` 防止编辑器账号与登录账号错位 | [`data-model.md`](../data-model.md)、[`frontend.md`](../frontend.md) |
| 日志字段 | Java 与 Go 统一的字段、Trace 传播与文件滚动 | [`logging.md`](../logging.md) |
| 命名 | 同一个东西只有一个名字（写法可随层变）：testdata 是一堆 testcase，digest 叫「指纹」 | [`testdata.md`](./testdata.md) 的「术语」 |

### 哪些没有机读 schema

接口协议 1–9 有契约文件，测试可以机械地比对；**10–16 是文字约定**，靠文档和测试保证：其中 10（`testdata.json`）在 Go、Java、
Python 里各有一份解析/校验实现，靠同一个黄金指纹的测试对齐；11 的请求不是 JSON，是 NUL 分隔的 `key=value` 记录（setuid 程序里少一个解析器就少一块攻击面）；
12、13 的细节还分散在代码和配置说明里。新增或修改它们时，要同时更新对应的真源文档和所有实现方的测试。
