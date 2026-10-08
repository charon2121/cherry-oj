# cherry-oj 微服务数据模型

> 状态：MVP 目标设计，contracts v2 已对齐；2026-10-07 起题目没有版本、测试数据按[协议](./testdata-protocol.md)交付
> 产品需求真源：[`product.md`](./product.md)
> 系统拓扑：[architecture.md](./architecture.md)
> 后端技术基线：[backend.md](./backend.md)
> MySQL 物理模型：[database-design.md](./database-design.md)
> 契约字段真源：[`../contracts/`](../contracts/)——contracts v2 已按 §13 对齐本模型。

本文把 PRD 的领域模型落到五个 Java 微服务和 Kafka 异步判题链路中。它同时回答三件事：一条事实由
哪个服务拥有、一次提交如何冻结可复现输入、跨服务在没有共享数据库和分布式事务时如何保持正确。

---

## 0. 已确认边界

- 单工作空间；MVP 不建立 Workspace / Tenant。
- 普通答题角色统一叫 `USER`，管理员叫 `ADMIN`。
- 支持 `ACM | CORE`；CORE 使用题目语言级源码模板，不建立通用函数类型系统。
- C++ 优先。
- 题目**没有版本**：题面、样例、语言、测试数据都直接属于 Problem，改了就是改了；只有 PRIVATE/PUBLIC 与 ACTIVE/ARCHIVED 两组状态。
- 限制按「题目 × 语言」保存绝对值，并记录标定时的测试数据指纹；判题节点只带身份，不归并成「判题环境」。
- 正式提交通过 Kafka 异步判题；web 轮询 Submission。
- Kafka 至少一次投递；Outbox、Inbox、条件更新和租约负责幂等。
- 每个服务独立 MySQL schema；不跨库 JOIN，不共享 Mapper，不建立跨服务数据库外键。
- 大测试数据不进数据库或 Kafka（数据库只存目录地址）；源码不进 Kafka。
- Agent 不属于 MVP。

命名约定：

- JSON 字段 `camelCase`，数据库列 `snake_case`。
- ID 使用 UUIDv7；Java/API 使用标准 UUID 字符串，MySQL 使用 `BINARY(16)`。
- 时间点使用 UTC，运行时长使用 ns，内存使用 bytes，字段名必须包含单位。
- 本文中的“外部引用”只保存另一个服务生成的 UUID，不代表数据库外键。

---

## 1. 数据所有权总图

```text
user-service
  └─ User / 用户安全审计

problem-service
  ├─ Problem ──► ProblemSample[]
  │            ├─ ProblemLanguage[]
  │            └─ test_data_location（协议目录的地址；指纹等在目录里的 testdata.json）
  └─ 题目审计

judging-service
  ├─ JudgeNode ──► JudgeNodeSession[]
  ├─ Problem(ref) + Language ──► LanguageCalibration（带 testDataDigest）
  └─ JudgeTask ──► JudgeAttempt[]（带实际读取的 testDataDigest）

submission-service
  ├─ Submission ──1:1──► JudgeInput
  ├─ SubmissionRequest（创建幂等）
  ├─ Outbox / Inbox
  └─ 用户可见 JudgeResult
```

唯一写入者：

| 事实 | 唯一写入服务 | 其它服务如何读取 |
|---|---|---|
| 用户、密码、角色、账号状态 | user-service | 内部 JWT / 受权用户接口 |
| 题目、样例、CORE 模板 | problem-service | ProblemJudgeSnapshot HTTP |
| 测试数据（协议目录）与它的地址 | problem-service | 内部 `GET /internal/judging/problems/{id}/test-data`（地址、指纹、测试点数） |
| 判题节点、语言标定 | judging-service | ExecutionProfile HTTP |
| 用户原始源码与 Submission 状态 | submission-service | Gateway API；内部 JudgeInput API |
| 完整送判源码与限制快照 | submission-service | judging-service 内部拉取 |
| 调度、租约、尝试与重试 | judging-service | 管理 API / 生命周期事件 |
| verdict 与用户可见测试点结果 | submission-service | lifecycle event 写入后查询 |

跨服务 UUID 不设置数据库外键。完整性由创建时同步校验、不可变快照、事件幂等和巡检保证。

---

## 2. 题目没有版本：快照与删除规则

### 2.1 题目就是它此刻的样子

```text
Problem p-a-plus-b  （PUBLIC / ACTIVE）
  ├─ 题面、样例、语言、模板
  └─ test_data_location → <root>/p-a-plus-b → .store/p-a-plus-b-<digest 前缀>/
```

管理员改了题目，改动立即生效；公开题目不能被改成空题面或没有样例。测试数据上传即整体替换：判题时读到的是此刻
地址下的那份数据，判题结果记录它读取的数据指纹（`testDataDigest`），用于追溯「这次拿哪份数据判的」。
标定绑定数据指纹：数据换了，旧标定过期，新提交被挡住，直到按新数据重新标定。

### 2.2 Submission 与 JudgeInput

Submission 是用户可见事实，JudgeInput 是内部执行事实：

```text
Submission
  ├─ 用户原始 source
  ├─ 题目标识与提交时的标题快照
  └─ PENDING / JUDGING / DONE + result

JudgeInput
  ├─ 完整 completeSource（CORE 已合并）
  ├─ problemId / languageId（不含测试数据地址：判题时才向 problem-service 取）
  ├─ languageCalibrationId / effectiveLimits
  └─ 创建后永久不可修改
```

JudgeInput 与 Submission 在同一个 submission-service 本地事务创建。修改题目、替换测试数据或重新标定
都不会改变已有 JudgeInput。

### 2.3 删除规则

- 从未公开过的题目（`published_at` 为空）可以删除：它不可能有提交；题面、样例、语言、审计事件和测试数据目录一并删除。
- 公开过的题目只能归档，不得物理删除。
- 被 JudgeInput 引用的 LanguageCalibration 不得物理删除，只能被新的标定替代（SUPERSEDED）。
- Submission、JudgeInput、JudgeTask、JudgeAttempt 和审计事件默认不级联删除。
- 跨服务删除不依赖数据库 cascade；将来实现用户数据删除时单独设计保留与匿名化流程。

---

## 3. user-service

### 3.1 User

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `username` | 是 | 登录名，全局唯一 |
| `passwordHash` | 是 | 自适应密码摘要，不通过 API 返回 |
| `role` | 是 | `USER | ADMIN` |
| `status` | 是 | `ACTIVE | DISABLED` |
| `sessionVersion` | 是 | 密码修改、封禁等事件使旧内部 JWT/Session 失效 |
| `createdAt` | 是 | 创建时间 |
| `updatedAt` | 是 | 修改时间 |
| `rowVersion` | 是 | 乐观锁 |

单管理员是部署策略，不用“全表只能有一个 ADMIN”的脆弱约束。注册接口只能创建 USER；管理员由初始化
流程或受控管理操作创建。

Gateway 的浏览器 Session 存在 Redis，不复制到 user-service 业务表。user-service 通过 sessionVersion
和安全事件支持失效已有会话。

### 3.2 用户服务表

```text
user_account
user_audit_event
```

其它服务只保存 `userId` 和必要展示快照，不对 user_account 建数据库外键。

---

## 4. problem-service

### 4.1 Problem

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | 稳定题目 UUIDv7 |
| `slug` | 是 | 全局唯一短名 |
| `visibility` | 是 | `PRIVATE | PUBLIC` |
| `status` | 是 | `ACTIVE | ARCHIVED` |
| `codeMode` | 是 | `ACM | CORE` |
| `title` | 是 | 标题 |
| `statementMarkdown` | 是 | 题面 |
| `inputDescriptionMarkdown` | 是 | 输入说明 |
| `outputDescriptionMarkdown` | 是 | 输出说明 |
| `constraintsMarkdown` | 否 | 约束 |
| `hintMarkdown` | 否 | 提示 |
| `difficulty` | 是 | `UNRATED | EASY | MEDIUM | HARD` |
| `tags` | 是 | JSON 字符串数组 |
| `checkerType` | 是 | MVP 固定 DEFAULT |
| `testDataLocation` | 否 | 测试数据目录的地址（本地路径或 http(s)）；公开前必填。指纹、测试点数、清单都在地址下的 `testdata.json` |
| `testDataUpdatedAt` | 否 | 最近一次替换测试数据的时间，与地址同有同无 |
| `createdBy` | 是 | user-service 的 userId，仅作外部引用 |
| `publishedAt` | 否 | **首次**公开的时间；为空表示从未公开过（只有这样的题目允许删除） |
| `createdAt` / `updatedAt` | 是 | 创建与最近修改时间 |
| `rowVersion` | 是 | 乐观锁（与「题目版本」无关；测试数据上传不递增它） |

不变量：

- PUBLIC Problem 必须有测试数据地址和 `publishedAt`（数据库约束）。
- 公开题目的修改立即生效，但不能被改成空题面或没有样例。
- 归档后只读。

### 4.2 ProblemSample

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `problemId` | 是 | 本库外键 |
| `ordinal` | 是 | 从 1 开始的展示顺序 |
| `inputText` | 是 | stdin 文本 |
| `expectedOutputText` | 是 | 期望 stdout |
| `explanationMarkdown` | 否 | 样例解释 |

`(problemId, ordinal)` 唯一。ACM 与 CORE 都使用文本 stdin/stdout。

### 4.3 ProblemLanguage

| 字段 | 必填 | 含义 |
|---|---:|---|
| `problemId` | 是 | 本库外键 |
| `languageId` | 是 | 稳定 token，例如 cpp |
| `displayOrder` | 是 | 前端顺序 |
| `starterCode` | 是 | 用户编辑器起始内容 |
| `judgeTemplate` | 条件 | CORE 必填；ACM 必须为空 |

`(problemId, languageId)` 唯一。

CORE judgeTemplate 必须包含且只包含一个字面量 `{{USER_CODE}}`。平台不解析函数签名、参数和返回值；
模板负责 include/import、输入解析、函数调用和 stdout 输出。

### 4.4 测试数据

没有单独的表：一道题只有一份测试数据，就是 `Problem.testDataLocation` 指向的目录。目录格式由
[测试数据协议](./testdata-protocol.md)规定：`testdata.json`（`schemaVersion`、`testcaseCount`、`totalBytes`、`digest`、
`testcases[]`）加成对的 `<name>.in/.out`。problem-service 把上传的 ZIP 校验后写成内容寻址的真实目录
`<root>/.store/<problemId>-<digest 前 16 位>/`，再把相对符号链接 `<root>/<problemId>` 原子切换过去；
只保留当前与上一代目录。数据库不抄指纹、测试点数或清单，避免两处不一致。

### 4.6 ProblemJudgeSnapshot

这是 problem-service 提供给 submission-service 的内部只读 DTO，不单独建表：

```text
ProblemJudgeSnapshot {
  problemId,
  problemTitle,
  languageId,
  codeMode,
  judgeTemplate?       // CORE 必填；内部接口字段
}
```

解析条件：Problem ACTIVE/PUBLIC、语言已允许、测试数据此刻可读。响应不含测试数据地址（地址由 judging-service
判题时向 problem-service 取最新的值）。

### 4.7 公开流程与跨服务检查

problem-service 在公开前完成本库检查，并调用 judging-service 的只读 readiness API：

1. 校验题面、样例、语言、模板和测试数据（`CONTENT / SAMPLES / LANGUAGE / TEST_DATA`）。
2. 以 `problemId + languageId + testDataDigest` 检查在线节点和标定是否齐全且标定对应当前数据
   （`ONLINE_JUDGE_NODE / CALIBRATION`）。
3. 检查成功后，仅在 problem-service 本地事务写 PUBLIC、首次公开时间和审计事件；若检查之后测试数据被替换，拒绝公开。

没有跨服务事务。readiness 是公开前置证据，最终创建 Submission 时仍会重新解析 ExecutionProfile。取消公开把题目变回
PRIVATE，已有提交保留。

### 4.8 problem-service 表

```text
problem
problem_sample
problem_language
problem_audit_event
```

---

## 5. judging-service：节点、标定与调度

### 5.1 JudgeNode

判题节点只带身份，不记录机器事实，也不归并成「判题环境」。

| 字段 | 必填 | 含义 |
|---|---:|---|
| `nodeId` | 是 | 部署配置给出的稳定标识 |
| `sessionId` | 是 | 节点进程每次启动新生成；新会话注册即接替旧进程 |
| `endpoint` | 是 | 节点对控制面的 HTTP(S) 源地址，不含凭证 |
| `languages` | 是 | 节点能判的语言，如 `["cpp"]` |
| `leaseExpiresAt` | 是 | 租约到期时间；在线状态由它派生 |
| `createdAt` / `updatedAt` | 是 | 首次注册与最近注册/心跳时间 |

已接受过的会话记在 `judge_node_session`，已被接替的旧会话不能再夺回 nodeId。

### 5.2 LanguageCalibration

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `problemId` | 是 | problem-service 外部引用 |
| `languageId` | 是 | 如 cpp |
| `testDataDigest` | 是 | 标定时所用测试数据的指纹；与题目当前指纹不同即已过期 |
| `status` | 是 | `DRAFT | RUNNING | VALID | FAILED | SUPERSEDED` |
| `sourceType` | 是 | `MANUAL | BENCHMARK` |
| `cpuNs` | 条件 | VALID 时正整数 |
| `memoryBytes` | 条件 | VALID 时正整数 |
| `clockNs` | 否 | 显式墙钟限制 |
| `benchmarkSummary` | 否 | 参考程序的判题摘要 |
| `approvedBy` | 否 | VALID 时外部 userId |
| `approvedAt` | 否 | VALID 时必填 |
| `createdAt` | 是 | 创建时间 |
| `supersedesId` | 否 | 本库旧标定 |

同一 `(problemId, languageId)` 同时最多一个当前 VALID。标定不绑定机器，但绑定数据指纹；重新标定产生新的 VALID
记录并把旧记录转为 SUPERSEDED，已冻结进 JudgeInput 的限制不受影响。标定请求带 problem-service 给出的地址与指纹，
判题结果里的指纹与之不一致说明标定中途数据被替换，这次标定作废。

### 5.3 可用节点

标定、自测和正式判题都路由给「可用节点」：租约未过期、声明了该语言。节点不持有数据，按请求里的地址自己读取。
任务不绑定某台机器，派发时再选节点。

### 5.4 ExecutionProfile

judging-service 向 submission-service 返回的只读 DTO：

```text
ExecutionProfile {
  problemId,
  languageId,
  languageCalibrationId,
  effectiveLimits { cpuNs, memoryBytes, clockNs? },
  executionBudgetNs      // 按题目此刻的测试点数算出的传输预算
}
```

解析必须同时验证：存在支持该语言的在线节点、题目有可读的测试数据、该语言存在 VALID 标定且其数据指纹等于
题目当前指纹。任何一项缺失都返回明确不可提交原因，不生成默认值。

### 5.5 JudgeTask

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | task UUIDv7 |
| `submissionId` | 是 | submission-service 外部引用，全局唯一 |
| `status` | 是 | `READY | RUNNING | RETRY_WAITING | SUCCEEDED | DEAD` |
| `attemptNo` | 是 | 已开始尝试次数 |
| `leaseToken` | 否 | 当前租约 fencing token |
| `leaseUntil` | 否 | 租约到期时间 |
| `nextAttemptAt` | 否 | 退避后可领取时间 |
| `lastErrorCode` | 否 | 稳定错误码 |
| `lastErrorMessage` | 否 | 安全摘要 |
| `createdAt` | 是 | 创建时间 |
| `updatedAt` | 是 | 修改时间 |
| `finishedAt` | 否 | SUCCEEDED/DEAD 时间 |
| `rowVersion` | 是 | 条件更新 |

JudgeRequested 重复投递时依靠 Inbox.eventId 和 `judge_task(submission_id)` unique 去重。

### 5.6 JudgeAttempt

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | attempt UUIDv7 |
| `taskId` | 是 | 本库外键 |
| `attemptNo` | 是 | 题内递增 |
| `leaseToken` | 是 | 本次 fencing token |
| `nodeId` | 否 | 实际派发的节点 |
| `testDataDigest` | 否 | 这次判题实际读取的测试数据指纹（judge 随结果返回） |
| `startedAt` | 是 | 开始时间 |
| `finishedAt` | 否 | 结束时间 |
| `outcome` | 否 | `COMPLETED | RETRYABLE_FAILURE | TERMINAL_FAILURE | STALE` |
| `judgeResult` | 否 | 受 schema 和大小限制的 JSON |
| `errorCode` | 否 | 稳定错误码 |
| `errorMessage` | 否 | 安全摘要 |

`(taskId, attemptNo)` 唯一。只有 task 当前 leaseToken 与本 attempt 一致时才能把结果落为有效；迟到
Worker 记录 STALE 或直接丢弃，不能发布完成事件。

### 5.7 judging-service 表

节点与标定互相独立：`judge_node` 以稳定 nodeId 记录节点身份、语言与租约，已接受会话由 `judge_node_session` 留存，
拒绝旧进程抢回身份；`language_calibration` 按题目 × 语言保存绝对限制与数据指纹。


```text
judge_node_registry_lock
judge_node
judge_node_session
language_calibration
judge_task
judge_attempt
outbox_event
inbox_event
judging_audit_event
```

---

## 6. submission-service

### 6.1 Submission

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `userId` | 是 | user-service 外部引用 |
| `problemId` | 是 | problem-service 外部引用 |
| `problemTitle` | 是 | 展示快照，避免历史页面显示新标题 |
| `languageId` | 是 | 如 cpp |
| `codeMode` | 是 | `ACM | CORE` 快照 |
| `languageCalibrationId` | 是 | judging-service 外部引用 |
| `effectiveLimits` | 是 | `{cpuNs, memoryBytes, clockNs?}` JSON 快照 |
| `source` | 是 | 用户原始源码；CORE 不含模板 |
| `status` | 是 | `PENDING | JUDGING | DONE` |
| `verdict` | 否 | DONE 后必填 |
| `cpuNs` | 否 | 所有测试点最大 CPU 时间 |
| `memoryBytes` | 否 | 所有测试点峰值最大值 |
| `score` | 否 | MVP AC=100，其它=0 |
| `message` | 否 | 受限安全摘要 |
| `testcaseResults` | 否 | 受 schema 约束 JSON |
| `createdAt` | 是 | 创建时间 |
| `startedAt` | 否 | 首次 JudgeStarted 时间 |
| `finishedAt` | 否 | DONE 时间 |
| `rowVersion` | 是 | 条件更新，防止状态回退 |

### 6.2 JudgeInput

JudgeInput 与 Submission 一对一，只能由 submission-service 内部读取：

| 字段 | 必填 | 含义 |
|---|---:|---|
| `submissionId` | 是 | 主键、本库外键 |
| `contractVersion` | 是 | JudgeRequest 契约版本 |
| `problemId` | 是 | 日志与对账；判题时凭它向 problem-service 取测试数据地址 |
| `languageId` | 是 | language registry token |
| `completeSource` | 是 | ACM 原源码；CORE 已合并完整源码 |
| `sourceSha256` | 是 | completeSource 完整性摘要 |
| `languageCalibrationId` | 是 | 本次限制的来源标定 |
| `effectiveLimits` | 是 | 绝对限制快照 |
| `createdAt` | 是 | 冻结时间 |

JudgeInput 创建后禁止 UPDATE。它不包含密码、JWT、题面、隐藏输入或标准答案。

### 6.3 SubmissionRequest

创建接口要求 Idempotency-Key：

| 字段 | 必填 | 含义 |
|---|---:|---|
| `userId` | 是 | 外部 userId |
| `idempotencyKey` | 是 | 客户端请求键 |
| `requestDigest` | 是 | problemId + languageId + source 的摘要 |
| `submissionId` | 是 | 已创建 Submission |
| `createdAt` | 是 | 创建时间 |

`(userId, idempotencyKey)` 唯一。同键同摘要返回原 Submission；同键不同摘要返回冲突。

### 6.4 TestcaseResult

MVP 保存在 Submission.testcaseResults JSON：

```text
TestcaseResult {
  idx,
  name?,
  verdict,
  cpuNs?,
  memoryBytes?,
  message?,
  output?,     // 受 reveal 和大小策略控制
  diff?
}
```

不得保存或返回正式隐藏输入、标准答案全文。以后只有明确查询需求出现时才正规化子表。

### 6.5 Outbox / Inbox

Outbox 至少包含 eventId、topic、messageKey、eventType、eventVersion、payload、状态、尝试次数和时间。
Inbox 以 eventId 唯一，消费事件时与业务更新在同一个本地事务提交。

### 6.6 submission-service 表

```text
submission
judge_input
submission_request
outbox_event
inbox_event
```

---

## 7. Kafka 事件模型

### 7.1 EventEnvelope

```text
EventEnvelope {
  eventId,
  eventType,
  eventVersion,
  occurredAt,
  traceId,
  aggregateId,  // submissionId
  payload
}
```

所有事件用 submissionId 作 Kafka key。

### 7.2 judge.requests.v1

```text
JudgeRequested {
  submissionId
}
```

可以携带 contractVersion 等小型路由信息，但不得携带 source、completeSource、judgeTemplate、测试数据、
密码或 JWT。judging-service 通过内部 HTTP 拉取 JudgeInput。

### 7.3 judge.lifecycle.v1

```text
JudgeStarted {
  submissionId,
  taskId,
  attemptNo,
  startedAt
}

JudgeCompleted {
  submissionId,
  taskId,
  attemptNo,
  finishedAt,
  result { verdict, cpuNs?, memoryBytes?, score?, message?, testcaseResults? }
}

JudgeFailed {
  submissionId,
  taskId,
  attemptNo,
  finishedAt,
  errorCode,
  message
}
```

JudgeCompleted 只能携带受大小限制、可进入用户结果的数据；不携带隐藏输入或完整标准答案。

### 7.4 Submission 状态投影

```text
PENDING ──JudgeStarted──► JUDGING ──JudgeCompleted──► DONE + verdict
    │                         └──────JudgeFailed────► DONE + SE
    └────────────最终 JudgeFailed──────────────────► DONE + SE
```

条件更新规则：

- DONE 永不回退。
- 重复 JudgeStarted 不重复写 startedAt。
- 重复 JudgeCompleted 返回幂等成功，不覆盖已接受的终态。
- 失败重试由 judging-service 内部 JudgeTask 表达，不让 Submission 在 PENDING/JUDGING 间抖动。

---

## 8. 核心流程

### 8.1 创建正式提交

浏览器只提交：

```text
CreateSubmissionRequest { problemId, languageId, source }
```

流程：

1. Gateway 验证 Session；submission-service 从已验证 JWT 取得 userId。
2. 校验 Idempotency-Key 和 source 大小。
3. HTTP 调 problem-service 获取 ProblemJudgeSnapshot。
4. HTTP 调 judging-service，按题目和语言解析 ExecutionProfile。
5. ACM 令 completeSource=source；CORE 校验模板唯一占位符并做一次非递归字面量替换。
6. 校验 completeSource 大小并计算 sha256。
7. 本地事务插入 Submission、JudgeInput、SubmissionRequest 和 JudgeRequested Outbox。
8. 返回 `202 Accepted`、Submission id 和 Location。

步骤 3/4/5 任一失败都不创建 Submission。步骤 7 成功后不再依赖请求线程完成判题。

### 8.2 judging-service 执行

1. Inbox 去重 JudgeRequested，并以 submissionId unique 创建 JudgeTask(READY)。
2. Worker 通过条件更新领取 leaseToken，提交事务后执行外部调用。
3. 发布 JudgeStarted。
4. 使用服务身份从 submission-service 拉取 JudgeInput；校验 source sha256。
5. 向 problem-service 取题目此刻的测试数据地址与指纹（取不到按可重试故障处理），按当前测试点数重算执行预算；
   选一个可用节点（在线、支持该语言），构造带 `testDataLocation` 的 JudgeRequest；没有可用节点按可重试故障处理。
6. Go judge 按地址读取数据并按冻结的限制返回 JudgeResult（含实际读取的数据指纹）。
7. 当前 leaseToken 匹配时保存 Attempt（含数据指纹）并发布 JudgeCompleted；可重试故障进入 RETRY_WAITING。
8. 重试耗尽或不可重试错误进入 DEAD 并发布 JudgeFailed。

### 8.3 自定义测试

自定义测试不创建 Submission、JudgeTask 或 Kafka 事件：

```text
CustomRunRequest { problemId, languageId, source, inputText }
```

submission-service 复用 ProblemJudgeSnapshot、ExecutionProfile 和 CORE 合并逻辑，然后同步调用
judging-service trial API。judging-service 使用独立限流调用 Go judge `mode=trial`，返回 stdout/stderr、
资源用量和运行状态。结果不影响通过状态。

### 8.4 公开题目

1. problem-service 做本库检查（内容、样例、语言、模板、测试数据）。
2. 调 judging-service 验证 readiness（在线节点、语言、对应当前数据指纹的有效标定）。
3. 本地事务写 PUBLIC、首次公开时间、追加审计；检查之后数据被替换则拒绝。

### 8.5 更换测试数据

管理员上传 ZIP：problem-service 校验后写成新的协议目录并原子切换地址，立即生效。旧标定因指纹不同而过期，
新提交被挡住（`JUDGING_NOT_READY`）直到重新标定；已冻结的 JudgeInput 不变，进行中的判题读到的是完整的旧数据或新数据，
不会读到半份。

### 8.6 更换或升级判题节点

新节点以自己的 nodeId 注册后即可参与路由，不需要环境切换，也不需要交付任何数据（它按地址自己读）。标定不绑定机器，
只有性能差异需要重新标定时，才对题目重做一次标定。已有 JudgeInput 冻结的限制不受影响。

---

## 9. Go judge 边界

目标 JudgeRequest：

```text
JudgeRequest {
  submissionId,
  problemId,             // 日志/对账
  testDataLocation,      // 测试数据目录的地址（submit 必填，trial 不用）
  languageId,
  source,                // 始终为完整源码
  limits { cpuNs, memoryBytes, clockNs? },
  mode?,                 // submit | trial
  testcases?             // trial 文本 testcases
}
```

Go judge 不知道：userId、codeMode、judgeTemplate、LanguageCalibration、Submission 状态、Kafka 或 Java
数据库。它只按请求执行；JudgeResult 带回 `testDataDigest`（这次读取的数据指纹）。

执行层（judge 进程内）与 sandbox 执行器不需要因微服务、测试数据交付方式或 CORE 改变。

---

## 10. 面向前端的读模型

### 10.1 ProblemSummary

problem-service 返回题目事实：

```text
problemId, slug,
title, difficulty, tags, codeMode, allowedLanguages
```

用户 solveStatus 属于 submission-service。Gateway 需要时通过批量接口组合，不让 problem-service 跨库
查询 Submission。

### 10.2 ProblemDetail

```text
problemId, slug, codeMode,
title, statementMarkdown,
inputDescriptionMarkdown, outputDescriptionMarkdown,
constraintsMarkdown?, hintMarkdown?, samples[],
allowedLanguages[] { languageId, starterCode }
```

judgeTemplate、测试数据地址与指纹、manifest、标定和隐藏数据不返回普通用户。

### 10.3 SubmissionDetail

submission-service 可独立返回：

```text
id, userId,
problemId, problemTitle,
languageId, codeMode,
status, verdict?, cpuNs?, memoryBytes?, score?, message?, testcaseResults?,
createdAt, startedAt?, finishedAt?
```

因为标题已在创建时快照，查询不需要跨服务 JOIN。用户默认只能读取自己的 source。

### 10.4 管理读模型

管理页面可以由 Gateway/BFF 组合：

- problem-service：题目、语言、模板、测试数据指纹。
- judging-service：节点、标定、任务和 Attempt。
- submission-service：结果与失败分布。

组合查询不是跨服务写事务；各响应必须保留 source service。

---

## 11. 数据库与索引清单

### user-service schema

```text
user_account(username) unique
user_audit_event(actor_user_id, created_at) index
```

### problem-service schema

```text
problem(slug) unique
problem(visibility, status, updated_at, id) index
problem_sample(problem_id, ordinal) unique
problem_language(problem_id, language_id) unique
```

### submission-service schema

```text
submission(user_id, created_at) index
submission(problem_id, created_at) index
submission(status, created_at) index
judge_input(submission_id) primary key
submission_request(user_id, idempotency_key) unique
outbox_event(status, next_attempt_at) index
inbox_event(event_id) unique
```

### judging-service schema

```text
judge_node(node_id) primary key
judge_node_session(node_id, session_id) primary key
同一 problem/language 最多一个当前 VALID calibration
judge_task(submission_id) unique
judge_task(status, next_attempt_at, lease_until) index
judge_attempt(task_id, attempt_no) unique
outbox_event(status, next_attempt_at) index
inbox_event(event_id) unique
```

服务内外键默认 RESTRICT。跨服务引用不建立外键，也不把 UUID 重新编号。

---

## 12. ACM 与 CORE 示例

### 12.1 ACM A+B

```text
problem-service:
  p-1(codeMode=ACM, testDataLocation=<root>/p-1)
  p-1/cpp(starterCode 含 main, judgeTemplate=null)

judging-service:
  p-1 + cpp → cal-1 VALID(1s, 256MB, testDataDigest=d-1)

submission-service:
  source = 完整 C++
  completeSource = source
  Submission + JudgeInput + JudgeRequested

judging-service → Go judge（判题时才向 problem-service 取地址）:
  source=完整 C++，testDataLocation=<root>/p-1
```

### 12.2 CORE A+B

```text
problem-service:
  p-core-1(codeMode=CORE, testDataLocation=<root>/p-core-1)
  p-core-1/cpp:
    starterCode = "int add(int a, int b) { ... }"
    judgeTemplate = "#include ... {{USER_CODE}} ... int main(){...}"

用户 source:
  "int add(int a, int b) { return a + b; }"

submission-service:
  completeSource = judgeTemplate.replaceExactlyOnce("{{USER_CODE}}", source)
  原 source 存 Submission，completeSource 存 JudgeInput

judging-service → Go judge:
  与 ACM 完全相同的完整源码 + 文本 .in/.out
```

模板修改立即对新提交生效；旧 Submission 的 JudgeInput 已保留实际完整源码，因此不会被新模板重新解释。

---

## 13. contracts v2 基线

实现顺序：

```text
contracts → Go contract/实现 → Java DTO/服务 → Gateway OpenAPI → web
```

### 13.1 已更新契约

`contracts/submission.json`：

- 新增 languageCalibrationId。题目没有版本，不含 problemVersionId 或 testDataVersionId。
- 新增 codeMode、effectiveLimits。
- language 统一为 languageId。
- time/memory 统一为 cpuNs/memoryBytes。
- 测试点结果统一为 testcaseResults。
- CreateSubmissionRequest 仍只有 problemId、languageId、source。

`contracts/judge.schema.json`：

- submit 使用 testDataLocation 定位测试数据目录（协议见 testdata-protocol.md）；problemId 只作日志。
- JudgeResult 新增可选 testDataDigest。
- source 对 ACM/CORE 都是完整源码。
- time/memory 迁移到 cpuNs/memoryBytes。

### 13.2 已新增契约

- `problem-judge-snapshot.schema.json`
- `execution-profile.schema.json`
- `judge-events.schema.json`
- `judge-input.schema.json`（submission-service 内部读取）
- `problem-test-data.schema.json`（problem-service → judging-service：测试数据的地址、指纹、测试点数）

事件 schema 通过封闭 payload 字段、诊断长度/结果数量约束和 1 MiB 总消息语义明确禁止源码与隐藏数据；
总序列化字节上限由 producer 和 broker 执行。

### 13.3 不变化

- 执行层的 `RunSpec`（`internal/contract`）不知道题目或 CORE。
- `contracts/verdict.json` 保持 verdict 集合真源。
- 执行层 Status 与 judge Verdict 继续分离。

---

## 14. MVP 明确不做

- 多工作空间与完整 RBAC。
- SPJ、交互题、部分分、子任务和 hack 数据。
- 通用 CORE 函数签名和值编解码框架。
- 自动重判和面向用户的重判任务。
- WebSocket/SSE 推送；web 先轮询。
- Agent、模型供应商和提示词模型。
- 语言倍率作为最终限制。
- testcaseResults 正规化子表。
- Kafka 携带源码、模板、测试数据或完整标准答案。
- 服务共享数据库、跨服务 JOIN 或 XA/2PC。

---

## 15. 验收清单

- [ ] 每个核心实体只有一个写入服务。
- [ ] role 枚举严格使用 USER/ADMIN。
- [ ] 公开题目的修改立即生效，但不能被改成空题面或没有样例；只有从未公开过的题目可以删除。
- [ ] ProblemJudgeSnapshot 只在题目公开且测试数据可读时返回，不含测试数据地址。
- [ ] ExecutionProfile 同时验证在线节点、可读测试数据和对应当前数据指纹的有效标定。
- [ ] 创建提交失败时不留下半条 Submission。
- [ ] Submission、JudgeInput、幂等记录和 JudgeRequested Outbox 同事务创建。
- [ ] CORE 只替换唯一占位符，完整源码冻结在 JudgeInput。
- [ ] Kafka 不包含源码、模板、JWT 或隐藏数据。
- [ ] 重复 JudgeRequested 只产生一个 JudgeTask。
- [ ] 租约过期 Worker 的迟到结果不能覆盖当前结果。
- [ ] Submission DONE 不回退，最终基础设施失败映射为 DONE + SE。
- [x] judge 按 testDataLocation 读取数据，结果带回数据指纹。
- [ ] 重新标定不修改旧 JudgeInput，旧任务仍按冻结的限制判题。
- [ ] 普通用户 API 不返回 judgeTemplate、隐藏数据或完整标准答案。
- [x] contracts 先于 Go/Java/web 实现迁移。

---

## 16. 相关文档

| 文档 | 作用 |
|---|---|
| [`product.md`](./product.md) | 产品范围、优先级和全局验收基线 |
| [architecture.md](./architecture.md) | 服务拓扑和通信边界 |
| [backend.md](./backend.md) | Java 技术栈、可靠消息和安全基线 |
| [database-design.md](./database-design.md) | MySQL 表、列、约束、索引、事务与 Flyway 迁移基线 |
| [engine.md](./engine.md) | Go judge / sandbox 内部执行模型 |
| [`../contracts/`](../contracts/) | 已迁移的跨服务与跨语言字段真源 |
