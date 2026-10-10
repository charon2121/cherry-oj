# 全局项目文档

`docs/` 只保存已经确认、跨多个工作项长期有效的项目事实。新克隆必须能取得这些文档，它们与代码
一起进入 Git，并作为后续开发的稳定上游依据。

这里适合放：

- 全局产品定位、角色、路线与已确认产品规则；
- 系统拓扑、模块职责、数据模型和数据库边界；
- 判题引擎、前端和后端的长期设计约束；
- 会被多个未来工作复用的图、视觉合同和背景材料。

这里不放：

- 某个功能仍在讨论的需求或待确认项；
- 尚未确认的技术方案和备选比较；
- 开发计划、可认领任务、执行日志和验证结果；
- backlog、临时调查记录或为了当前改动生成的上下文。

明确启用 `$dev-work` 时，这些内容进入 [`development/`](../development/README.md)，每项工作有简短说明，
其他材料按需保存。普通开发不强制创建过程文档。开发过程中形成的结论
只有同时满足“已经确认”和“会长期约束多个工作项”时才整理进 `docs/`；迁入时保留来源 WORK 或
DECISION，避免历史原因丢失。

## 入口

- [`product.md`](./product.md)：全局产品定位、范围、路线和已确认决定；
- [`architecture.md`](./architecture.md)：系统拓扑、服务职责和通信边界；
- [`data-model.md`](./data-model.md)：跨服务领域模型和所有权；
- [`database-design.md`](./database-design.md)：数据库级设计；
- [`engine.md`](./engine.md)：judge 与 sandbox 引擎设计；
- [`sandbox-executor.md`](./sandbox-executor.md)：sandbox 执行器的安装要求、调用协议、事实字段与实测行为；
- [`testdata-protocol.md`](./testdata-protocol.md)：测试数据协议：地址、`testdata.json` 与读写双方的责任；
- [`backend.md`](./backend.md)：Java 服务工程与实现边界；
- [`logging.md`](./logging.md)：Java/Go 统一日志字段、Trace 传播与文件滚动规范；
- [`frontend.md`](./frontend.md)：Web 架构、状态、组件和工程规则；
- [`design-system.md`](./design-system.md)：Web 视觉、主题合同、组件规则、可访问性与例外流程；
- [`prd-background.md`](./prd-background.md)：产品缘起与长期背景；
- [`coding-standards/`](./coding-standards/README.md)：编码规范，分通用 / 语言 / 框架三层；
- [`git-workflow.md`](./git-workflow.md)：提交拆分、hooks 与 CI 门禁；
- [`status.md`](./status.md)：各模块当前是可用实现还是骨架。
  根目录 `AGENTS.md` 只留每次都必须遵守的部分，展开的规范在这里按需读。

[`design-system/`](./design-system/) 保存设计系统的可执行文档包。其中 Foundation 与主题 CSS 是数值
真源，theme contract 是语义和对比合同；机器快照、组件 HTML 和 preview 是派生或评审材料。

## 协议总览

项目里不同进程、不同语言之间靠下面这些约定对话。**改任何一条之前先看它的真源**；有机读 schema 的以
[`contracts/`](../contracts/) 为准，先改契约、再改类型、再改实现。

### 接口协议

| # | 协议 | 两端 | 传输与形态 | 真源 |
|---|---|---|---|---|
| 1 | Web API | 浏览器 ↔ Gateway | REST；成功用 `ApiSuccess` 信封，失败用 RFC 9457 | [`web-api.openapi.json`](../contracts/web-api.openapi.json) |
| 2 | Submission 内部 API | judging-service → submission-service（拉取 JudgeInput 等） | HTTP，服务令牌 | [`submission-internal.openapi.json`](../contracts/submission-internal.openapi.json) |
| 3 | 题目快照 | submission-service ← problem-service | HTTP，服务令牌 | [`problem-judge-snapshot.schema.json`](../contracts/problem-judge-snapshot.schema.json) |
| 4 | 执行配置 | submission-service ← judging-service | HTTP，服务令牌 | [`execution-profile.schema.json`](../contracts/execution-profile.schema.json) |
| 5 | 自定义运行 | submission-service → judging-service | HTTP，服务令牌 | [`custom-run-internal.schema.json`](../contracts/custom-run-internal.schema.json) |
| 6 | 测试数据读取 | judging-service ← problem-service | HTTP，服务令牌；只返回地址、指纹、测试点数 | [`problem-test-data.schema.json`](../contracts/problem-test-data.schema.json) |
| 7 | 判题请求 | judging-service → judge（Go） | `POST /judge` | [`judge.schema.json`](../contracts/judge.schema.json) |
| 8 | 节点控制 | judge → judging-service（注册、心跳） | HTTP，共享控制令牌 | [`judge-node.schema.json`](../contracts/judge-node.schema.json) |
| 9 | 判题事件 | submission-service ↔ judging-service | Kafka：`judge.requests.v1`、`judge.lifecycle.v1` | [`judge-events.schema.json`](../contracts/judge-events.schema.json) |
| 10 | 测试数据协议 | problem-service 写、judge 读 | 目录 + `testdata.json`，本地路径或 HTTP(S) | [`testdata-protocol.md`](./testdata-protocol.md) |
| 11 | 执行器协议 | judge 进程 → sandbox 执行器 | `exec` + box 目录 + NUL 分隔的请求 + 一行 JSON 事实 | [`sandbox-executor.md`](./sandbox-executor.md) |
| 12 | 身份传递 | Gateway → 各业务服务 | 委派的内部 JWT；user-service 提供 JWKS 与 metadata | [`backend.md`](./backend.md) |
| 13 | 服务间鉴权 | submission↔problem、submission↔judging、judging→submission、judging→problem | Bearer 服务令牌；发送方的值须属于接收方的列表 | [`apps/server/CONFIGURATION.md`](../apps/server/CONFIGURATION.md) |
| 14 | 部署配置格式 | 安装器 ↔ 执行器与 judge | `executor.conf`、`judge-start.json`、`deployment.json`（部署清单）、`plan.json` | [`deploy/sandbox-linux/install/README.md`](../deploy/sandbox-linux/install/README.md) |

`contracts/` 里另有三份**数据模型与词表**，不是有两端的接口，但同样是跨服务的真源：
[`submission.json`](../contracts/submission.json)（用户可见的提交读模型）、
[`judge-input.schema.json`](../contracts/judge-input.schema.json)（提交时冻结的判题输入）、
[`verdict.json`](../contracts/verdict.json)（verdict 集合）。

### 横切约定

| 约定 | 内容 | 真源 |
|---|---|---|
| 追踪 | HTTP 只传 W3C `traceparent` / `tracestate`，不传 baggage，追踪字段不进入请求体；`X-Request-Id` 只关联同一次同步调用 | [`logging.md`](./logging.md)、[`architecture.md`](./architecture.md) |
| 错误格式 | RFC 9457 `application/problem+json`，带稳定的 `code` 与 `meta.requestId` | [`architecture.md`](./architecture.md)、[`backend.md`](./backend.md) |
| 幂等与账号前置 | 创建提交要 `Idempotency-Key`；`X-Expected-User-Id` 防止编辑器账号与登录账号错位 | [`data-model.md`](./data-model.md)、[`frontend.md`](./frontend.md) |
| 日志字段 | Java 与 Go 统一的字段、Trace 传播与文件滚动 | [`logging.md`](./logging.md) |
| 命名 | 同一个东西只有一个名字（写法可随层变）：testdata 是一堆 testcase，digest 叫「指纹」 | [`testdata-protocol.md`](./testdata-protocol.md) 的「术语」 |

### 哪些没有机读 schema

接口协议 1–9 有契约文件，测试可以机械地比对；**10–14 是文字约定**，靠文档和测试保证：其中 10（`testdata.json`）在 Go、Java、
Python 里各有一份解析/校验实现，靠同一个黄金指纹的测试对齐；11 的请求不是 JSON，是 NUL 分隔的 `key=value` 记录（setuid 程序里少一个解析器就少一块攻击面）；
12、13 的细节还分散在代码和配置说明里。新增或修改它们时，要同时更新对应的真源文档和所有实现方的测试。

修改长期事实前，说明规则为何改变、哪些消费者受影响，并验证代码与其他文档已同步。启用
`$dev-work` 时将这些内容写进工作短说明；普通修正不强制建立工作项。已有来源链接保留供查证。
