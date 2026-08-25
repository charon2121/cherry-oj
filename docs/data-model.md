# cherry-oj 微服务数据模型

> 状态：MVP 目标设计，contracts v2 已对齐，2026-08-20
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
- 支持 `ACM | CORE`；CORE 使用题目版本语言级源码模板，不建立通用函数类型系统。
- C++ 优先。
- Problem 与不可变 ProblemVersion 分离。
- 环境相关限制按「题目版本 × 语言 × 判题环境」保存绝对值。
- 正式提交通过 Kafka 异步判题；web 轮询 Submission。
- Kafka 至少一次投递；Outbox、Inbox、条件更新和租约负责幂等。
- 每个服务独立 MySQL schema；不跨库 JOIN，不共享 Mapper，不建立跨服务数据库外键。
- 大测试数据不进数据库或 Kafka；源码不进 Kafka。
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
  ├─ Problem ──current──► ProblemVersion
  │                         ├─ ProblemSample[]
  │                         ├─ ProblemVersionLanguage[]
  │                         └─ TestDataVersion
  └─ 题目发布审计

judging-service
  ├─ JudgeEnvironment ──► JudgeEnvironmentLanguage[]
  ├─ TestDataVersion(ref) + JudgeEnvironment ──► TestDataDeployment
  ├─ ProblemVersion(ref) + Language + Environment ──► LanguageCalibration
  └─ JudgeTask ──► JudgeAttempt[]

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
| 题目、版本、样例、CORE 模板 | problem-service | ProblemJudgeSnapshot HTTP |
| 测试数据版本元信息与内容 hash | problem-service | 快照 / 管理内部 API |
| 环境、数据部署、语言标定 | judging-service | ExecutionProfile HTTP |
| 用户原始源码与 Submission 状态 | submission-service | Gateway API；内部 JudgeInput API |
| 完整送判源码与限制快照 | submission-service | judging-service 内部拉取 |
| 调度、租约、尝试与重试 | judging-service | 管理 API / 生命周期事件 |
| verdict 与用户可见测试点结果 | submission-service | lifecycle event 写入后查询 |

跨服务 UUID 不设置数据库外键。完整性由创建时同步校验、不可变快照、事件幂等和巡检保证。

---

## 2. 版本、快照与删除规则

### 2.1 ProblemVersion 是内容快照

```text
Problem p-a-plus-b
  ├─ pv-1 (PUBLISHED)
  ├─ pv-2 (DRAFT)
  └─ currentPublishedVersionId = pv-1
```

发布后冻结：题面、codeMode、样例、checker、testDataVersionId、允许语言、starterCode 和 judgeTemplate。
修改任一项都创建新版本。服务器迁移或重新标定只新增 judging-service 数据，不复制 ProblemVersion。

### 2.2 Submission 与 JudgeInput

Submission 是用户可见事实，JudgeInput 是内部执行事实：

```text
Submission
  ├─ 用户原始 source
  ├─ 历史题目/版本/环境标识
  └─ PENDING / JUDGING / DONE + result

JudgeInput
  ├─ 完整 completeSource（CORE 已合并）
  ├─ problemVersionId / testDataVersionId
  ├─ judgeEnvironmentId / environmentFingerprint
  ├─ languageCalibrationId / effectiveLimits
  └─ 创建后永久不可修改
```

JudgeInput 与 Submission 在同一个 submission-service 本地事务创建。发布新题目版本、切换环境或替换
标定都不会改变已有 JudgeInput。

### 2.3 删除规则

- 未发布且未被引用的草稿可以删除。
- 已发布 ProblemVersion、READY TestDataVersion、已生效 LanguageCalibration、被 JudgeInput 引用的
  JudgeEnvironment 均不得物理删除，只能归档、停用或退休。
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
| `currentPublishedVersionId` | 否 | 当前发布版本；本库外键 |
| `createdBy` | 是 | user-service 的 userId，仅作外部引用 |
| `createdAt` | 是 | 创建时间 |
| `updatedAt` | 是 | 容器属性更新时间 |
| `rowVersion` | 是 | 乐观锁 |

不变量：

- currentPublishedVersionId 必须属于同一 Problem，且状态为 PUBLISHED。
- PUBLIC Problem 必须存在当前发布版本。
- 归档不删除历史版本；当前版本指针切走后旧版本才能归档。

### 4.2 ProblemVersion

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | 版本 UUIDv7 |
| `problemId` | 是 | 本库外键 |
| `versionNo` | 是 | 题内递增，从 1 开始 |
| `status` | 是 | `DRAFT | VALIDATING | READY_FOR_REVIEW | PUBLISHED | ARCHIVED` |
| `codeMode` | 是 | `ACM | CORE` |
| `title` | 是 | 历史标题 |
| `statementMarkdown` | 是 | 题面 |
| `inputDescriptionMarkdown` | 是 | 输入说明 |
| `outputDescriptionMarkdown` | 是 | 输出说明 |
| `constraintsMarkdown` | 否 | 约束 |
| `hintMarkdown` | 否 | 提示 |
| `difficulty` | 是 | `UNRATED | EASY | MEDIUM | HARD` |
| `tags` | 是 | JSON 字符串数组 |
| `checkerType` | 是 | MVP 固定 DEFAULT |
| `testDataVersionId` | 否 | 本库 TestDataVersion；审核前必填 |
| `changeSummary` | 否 | 版本说明 |
| `createdBy` | 是 | 外部 userId |
| `publishedBy` | 否 | 外部 userId |
| `createdAt` | 是 | 创建时间 |
| `updatedAt` | 是 | 草稿修改时间 |
| `publishedAt` | 否 | 发布时间 |
| `rowVersion` | 是 | 乐观锁 |

`(problemId, versionNo)` 唯一。PUBLISHED 后业务字段与所有子记录不可修改。

### 4.3 ProblemSample

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `problemVersionId` | 是 | 本库外键 |
| `ordinal` | 是 | 从 1 开始的展示顺序 |
| `inputText` | 是 | stdin 文本 |
| `expectedOutputText` | 是 | 期望 stdout |
| `explanationMarkdown` | 否 | 样例解释 |

`(problemVersionId, ordinal)` 唯一。ACM 与 CORE 都使用文本 stdin/stdout。

### 4.4 ProblemVersionLanguage

| 字段 | 必填 | 含义 |
|---|---:|---|
| `problemVersionId` | 是 | 本库外键 |
| `languageId` | 是 | 稳定 token，例如 cpp |
| `displayOrder` | 是 | 前端顺序 |
| `starterCode` | 是 | 用户编辑器起始内容 |
| `judgeTemplate` | 条件 | CORE 必填；ACM 必须为空 |

`(problemVersionId, languageId)` 唯一。

CORE judgeTemplate 必须包含且只包含一个字面量 `{{USER_CODE}}`。平台不解析函数签名、参数和返回值；
模板负责 include/import、输入解析、函数调用和 stdout 输出。starterCode 与模板发布后一起冻结。

### 4.5 TestDataVersion

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7，也是 judge 测例目录名 |
| `problemId` | 是 | 本库外键；允许同题版本复用 |
| `status` | 是 | `UPLOADING | READY | FAILED` |
| `sourceType` | 是 | MVP `MANUAL_UPLOAD`；后续 GENERATED |
| `storageRef` | 是 | 长期资产私有引用 |
| `contentSha256` | 否 | READY 后必填 |
| `caseCount` | 否 | READY 后必填 |
| `totalBytes` | 否 | READY 后必填 |
| `manifest` | 否 | READY 后必填的受 schema 约束 JSON |
| `createdBy` | 是 | 外部 userId |
| `createdAt` | 是 | 创建时间 |
| `readyAt` | 否 | 封存时间 |
| `errorMessage` | 否 | 失败摘要 |

manifest 至少记录每个 `.in/.out` 的 name、bytes 和 sha256。READY 后内容不可覆盖；新数据生成新 id。

### 4.6 ProblemJudgeSnapshot

这是 problem-service 提供给 submission-service 的内部只读 DTO，不单独建表：

```text
ProblemJudgeSnapshot {
  problemId,
  problemVersionId,
  problemVersionNo,
  problemTitle,
  testDataVersionId,
  testDataContentSha256,
  languageId,
  codeMode,
  judgeTemplate?       // CORE 必填；内部接口字段
}
```

解析条件：Problem ACTIVE/PUBLIC、当前版本 PUBLISHED、语言已允许、测试数据 READY。这个响应只引用
不可变记录；返回后即使 currentPublishedVersionId 改变，响应内容仍有稳定含义。

### 4.7 发布流程与跨服务检查

problem-service 在发布前完成本库检查，并调用 judging-service 的只读 readiness API：

1. 校验题面、样例、测试数据、语言和模板。
2. CORE 用参考核心代码替换模板，调用 judging-service trial/validation 证明可编译并通过样例。
3. 以明确 `problemVersionId + testDataVersionId + languageId + contentSha256` 检查当前环境的数据部署和
   标定是否齐全。
4. 检查成功后，仅在 problem-service 本地事务写 PUBLISHED、更新当前指针和审计事件。

没有跨服务事务。readiness 是发布前置证据，最终创建 Submission 时仍会重新解析 ExecutionProfile。

### 4.8 problem-service 表

```text
problem
problem_version
problem_sample
problem_version_language
test_data_version
problem_audit_event
```

---

## 5. judging-service：环境与调度

### 5.1 JudgeEnvironment

JudgeEnvironment 是长期执行基线，不是一次 sandbox 工作间。

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `name` | 是 | 显示名 |
| `fingerprint` | 是 | 不可变全局唯一摘要 |
| `status` | 是 | `REGISTERED | ACTIVE | RETIRED` |
| `architecture` | 是 | 如 amd64 |
| `cpuModel` | 是 | CPU 标识 |
| `osVersion` | 是 | OS 版本 |
| `kernelVersion` | 是 | 内核版本 |
| `judgeVersion` | 是 | judge 构建摘要 |
| `sandboxVersion` | 是 | sandbox 构建摘要 |
| `configDigest` | 是 | 影响执行的配置摘要 |
| `endpointRef` | 是 | 受控路由标识，不把凭证写入业务数据 |
| `createdAt` | 是 | 注册时间 |
| `activatedAt` | 否 | 激活时间 |
| `retiredAt` | 否 | 退休时间 |

MVP 同时最多一个 ACTIVE。RETIRED 不接收新 ExecutionProfile，但必须能处理已经冻结到它的在途任务，
或由运维明确将这些任务终止为系统故障。

### 5.2 JudgeEnvironmentLanguage

| 字段 | 必填 | 含义 |
|---|---:|---|
| `judgeEnvironmentId` | 是 | 本库外键 |
| `languageId` | 是 | 如 cpp |
| `toolchainVersion` | 是 | 编译器/运行时版本 |
| `languageConfigDigest` | 是 | 编译运行配置摘要 |
| `enabled` | 是 | 是否接受该语言 |

`(judgeEnvironmentId, languageId)` 唯一。

### 5.3 TestDataDeployment

| 字段 | 必填 | 含义 |
|---|---:|---|
| `testDataVersionId` | 是 | problem-service 外部引用 |
| `judgeEnvironmentId` | 是 | 本库外键 |
| `expectedSha256` | 是 | 部署任务接收的内容 hash 快照 |
| `status` | 是 | `PENDING | DEPLOYING | READY | FAILED` |
| `deployedSha256` | 否 | READY 后必填，必须等于 expectedSha256 |
| `deployedAt` | 否 | 完成时间 |
| `errorMessage` | 否 | 失败摘要 |

`(testDataVersionId, judgeEnvironmentId)` 唯一。它只是一张部署回执，不保存测例内容。

### 5.4 LanguageCalibration

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | UUIDv7 |
| `problemVersionId` | 是 | problem-service 外部引用 |
| `languageId` | 是 | 必须被环境启用 |
| `judgeEnvironmentId` | 是 | 本库外键 |
| `status` | 是 | `DRAFT | RUNNING | VALID | FAILED | SUPERSEDED` |
| `sourceType` | 是 | `MANUAL | BENCHMARK`；MVP MANUAL |
| `cpuNs` | 条件 | VALID 时正整数 |
| `memoryBytes` | 条件 | VALID 时正整数 |
| `clockNs` | 否 | 显式墙钟限制 |
| `benchmarkSummary` | 否 | 后续统计摘要 |
| `approvedBy` | 否 | VALID 时外部 userId |
| `approvedAt` | 否 | VALID 时必填 |
| `createdAt` | 是 | 创建时间 |
| `supersedesId` | 否 | 本库旧标定 |

同一 `(problemVersionId, languageId, judgeEnvironmentId)` 同时最多一个当前 VALID。换环境不会篡改旧
标定；相对于新 ACTIVE 环境缺卡时，运营查询派生为 STALE。

### 5.5 ExecutionProfile

judging-service 向 submission-service 返回的只读 DTO：

```text
ExecutionProfile {
  problemVersionId,
  testDataVersionId,
  languageId,
  judgeEnvironmentId,
  environmentFingerprint,
  languageCalibrationId,
  effectiveLimits { cpuNs, memoryBytes, clockNs? }
}
```

解析必须同时验证：唯一 ACTIVE 环境、语言启用、部署 READY 且 hash 与 problem snapshot 一致、标定
VALID。任何一项缺失都返回明确不可提交原因，不生成默认值。

### 5.6 JudgeTask

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

### 5.7 JudgeAttempt

| 字段 | 必填 | 含义 |
|---|---:|---|
| `id` | 是 | attempt UUIDv7 |
| `taskId` | 是 | 本库外键 |
| `attemptNo` | 是 | 题内递增 |
| `leaseToken` | 是 | 本次 fencing token |
| `judgeEnvironmentId` | 是 | 实际目标环境 |
| `startedAt` | 是 | 开始时间 |
| `finishedAt` | 否 | 结束时间 |
| `outcome` | 否 | `COMPLETED | RETRYABLE_FAILURE | TERMINAL_FAILURE | STALE` |
| `judgeResult` | 否 | 受 schema 和大小限制的 JSON |
| `errorCode` | 否 | 稳定错误码 |
| `errorMessage` | 否 | 安全摘要 |

`(taskId, attemptNo)` 唯一。只有 task 当前 leaseToken 与本 attempt 一致时才能把结果落为有效；迟到
Worker 记录 STALE 或直接丢弃，不能发布完成事件。

### 5.8 judging-service 表

```text
judge_environment
judge_environment_language
test_data_deployment
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
| `problemVersionId` | 是 | 不可变版本外部引用 |
| `problemVersionNo` | 是 | 展示快照 |
| `problemTitle` | 是 | 展示快照，避免历史页面显示新标题 |
| `testDataVersionId` | 是 | 实际数据版本 |
| `languageId` | 是 | 如 cpp |
| `codeMode` | 是 | `ACM | CORE` 快照 |
| `languageCalibrationId` | 是 | judging-service 外部引用 |
| `judgeEnvironmentId` | 是 | judging-service 外部引用 |
| `environmentFingerprint` | 是 | 目标环境指纹快照 |
| `effectiveLimits` | 是 | `{cpuNs, memoryBytes, clockNs?}` JSON 快照 |
| `source` | 是 | 用户原始源码；CORE 不含模板 |
| `status` | 是 | `PENDING | JUDGING | DONE` |
| `verdict` | 否 | DONE 后必填 |
| `cpuNs` | 否 | 所有测试点最大 CPU 时间 |
| `memoryBytes` | 否 | 所有测试点峰值最大值 |
| `score` | 否 | MVP AC=100，其它=0 |
| `message` | 否 | 受限安全摘要 |
| `caseResults` | 否 | 受 schema 约束 JSON |
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
| `problemId` | 是 | 日志与对账 |
| `problemVersionId` | 是 | 日志与对账 |
| `testDataVersionId` | 是 | judge 正式测例定位键 |
| `languageId` | 是 | language registry token |
| `completeSource` | 是 | ACM 原源码；CORE 已合并完整源码 |
| `sourceSha256` | 是 | completeSource 完整性摘要 |
| `judgeEnvironmentId` | 是 | 路由目标 |
| `environmentFingerprint` | 是 | 结果校验 |
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

### 6.4 CaseResult

MVP 保存在 Submission.caseResults JSON：

```text
CaseResult {
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
  result { verdict, cpuNs?, memoryBytes?, score?, message?, caseResults? }
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
4. HTTP 调 judging-service，使用明确版本、数据、语言和内容 hash 解析 ExecutionProfile。
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
5. 根据 judgeEnvironmentId 路由目标 Go judge，构造 JudgeRequest。
6. Go judge 按 testDataVersionId 加载数据并返回 JudgeResult + environmentFingerprint。
7. fingerprint 不一致视为可定位系统故障，不接受 verdict。
8. 当前 leaseToken 匹配时保存 Attempt 并发布 JudgeCompleted；可重试故障进入 RETRY_WAITING。
9. 重试耗尽或不可重试错误进入 DEAD 并发布 JudgeFailed。

### 8.3 自定义测试

自定义测试不创建 Submission、JudgeTask 或 Kafka 事件：

```text
CustomRunRequest { problemId, languageId, source, inputText }
```

submission-service 复用 ProblemJudgeSnapshot、ExecutionProfile 和 CORE 合并逻辑，然后同步调用
judging-service trial API。judging-service 使用独立限流调用 Go judge `mode=trial`，返回 stdout/stderr、
资源用量和运行状态。结果不影响通过状态。

### 8.4 发布题目版本

1. problem-service 锁定 READY_FOR_REVIEW 草稿。
2. 完成本库字段、样例、语言、模板和 TestDataVersion READY 检查。
3. 调 judging-service 验证模板/参考代码和当前环境 readiness。
4. 本地事务写 PUBLISHED、更新 Problem.currentPublishedVersionId、追加审计。

环境在发布后切换不会改变题目版本；环境激活流程必须确保计划承接的已发布版本在新环境有部署回执
和有效标定。

### 8.5 切换判题环境

1. judging-service 注册 env-B 为 REGISTERED。
2. 为所需 TestDataVersion 建立 READY deployment。
3. 为所需 ProblemVersionLanguage 建立 VALID calibration。
4. 执行覆盖检查。
5. 本地事务将 env-A RETIRED、env-B ACTIVE。

新 ExecutionProfile 使用 env-B；旧 JudgeInput 仍指向 env-A。环境路由必须允许在途旧任务完成。

---

## 9. Go judge 边界

目标 JudgeRequest：

```text
JudgeRequest {
  submissionId,
  problemId,             // 日志/对账
  problemVersionId,      // 日志/对账
  testDataVersionId,     // 正式测例目录
  languageId,
  source,                // 始终为完整源码
  limits { cpuNs, memoryBytes, clockNs? },
  mode?,                 // submit | trial
  cases?                 // trial 文本 cases
}
```

Go judge 不知道：userId、codeMode、judgeTemplate、LanguageCalibration、Submission 状态、Kafka 或 Java
数据库。它只按请求执行，并回传实际环境指纹。

`contracts/run.schema.json` 和 sandbox 不需要因微服务、题目版本或 CORE 改变。

---

## 10. 面向前端的读模型

### 10.1 ProblemSummary

problem-service 返回题目事实：

```text
problemId, slug, currentVersionId, versionNo,
title, difficulty, tags, codeMode, allowedLanguages
```

用户 solveStatus 属于 submission-service。Gateway 需要时通过批量接口组合，不让 problem-service 跨库
查询 Submission。

### 10.2 ProblemDetail

```text
problemId, problemVersionId, versionNo, codeMode,
title, statementMarkdown,
inputDescriptionMarkdown, outputDescriptionMarkdown,
constraintsMarkdown?, hintMarkdown?, samples[],
allowedLanguages[] { languageId, starterCode }
```

judgeTemplate、TestDataVersion 内部标识、manifest、标定和隐藏数据不返回普通用户。

### 10.3 SubmissionDetail

submission-service 可独立返回：

```text
id, userId,
problemId, problemVersionId, problemVersionNo, problemTitle,
languageId, codeMode,
status, verdict?, cpuNs?, memoryBytes?, score?, message?, caseResults?,
createdAt, startedAt?, finishedAt?
```

因为标题和版本号已在创建时快照，查询不需要跨服务 JOIN。用户默认只能读取自己的 source。

### 10.4 管理读模型

管理页面可以由 Gateway/BFF 组合：

- problem-service：版本、语言、模板、TestDataVersion。
- judging-service：部署、环境、标定、任务和 Attempt。
- submission-service：结果与失败分布。

组合查询不是跨服务写事务；各响应必须保留 source service 和版本语义。

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
problem_version(problem_id, version_no) unique
problem_sample(problem_version_id, ordinal) unique
problem_version_language(problem_version_id, language_id) unique
test_data_version(problem_id, created_at) index
```

### submission-service schema

```text
submission(user_id, created_at) index
submission(problem_version_id, created_at) index
submission(status, created_at) index
judge_input(submission_id) primary key
submission_request(user_id, idempotency_key) unique
outbox_event(status, next_attempt_at) index
inbox_event(event_id) unique
```

### judging-service schema

```text
judge_environment(fingerprint) unique
最多一个 ACTIVE environment（应用事务 + 锁保证）
judge_environment_language(judge_environment_id, language_id) unique
test_data_deployment(test_data_version_id, judge_environment_id) unique
同一 problemVersion/language/environment 最多一个当前 VALID calibration
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
  pv-1(codeMode=ACM, testDataVersionId=td-1)
  pv-1/cpp(starterCode 含 main, judgeTemplate=null)

judging-service:
  td-1 + env-1 → deployment READY
  pv-1 + cpp + env-1 → cal-1 VALID(1s, 256MB)

submission-service:
  source = 完整 C++
  completeSource = source
  Submission + JudgeInput + JudgeRequested

judging-service → Go judge:
  source=完整 C++，testDataVersionId=td-1
```

### 12.2 CORE A+B

```text
problem-service:
  pv-core-1(codeMode=CORE, testDataVersionId=td-core-1)
  pv-core-1/cpp:
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

模板发布后不可修改；旧 Submission 通过 problemVersionId + languageId 追溯模板，同时 JudgeInput 已保留
实际完整源码，因此不会被新模板重新解释。

---

## 13. contracts v2 基线

实现顺序：

```text
contracts → Go contract/实现 → Java DTO/服务 → Gateway OpenAPI → web
```

### 13.1 已更新契约

`contracts/submission.json`：

- 新增 problemVersionId、testDataVersionId、languageCalibrationId、judgeEnvironmentId。
- 新增 environmentFingerprint、codeMode、effectiveLimits。
- language 统一为 languageId。
- time/memory 统一为 cpuNs/memoryBytes。
- cases 统一为 caseResults。
- CreateSubmissionRequest 仍只有 problemId、languageId、source。

`contracts/judge.schema.json`：

- submit 使用 testDataVersionId 定位目录；problemId 只作日志。
- 新增 problemVersionId、environmentFingerprint 返回值。
- source 对 ACM/CORE 都是完整源码。
- time/memory 迁移到 cpuNs/memoryBytes。

### 13.2 已新增契约

- `problem-judge-snapshot.schema.json`
- `execution-profile.schema.json`
- `judge-events.schema.json`
- `judge-input.schema.json`（submission-service 内部读取）

事件 schema 通过封闭 payload 字段、诊断长度/结果数量约束和 1 MiB 总消息语义明确禁止源码与隐藏数据；
总序列化字节上限由 producer 和 broker 执行。

### 13.3 不变化

- `contracts/run.schema.json` 不知道题目版本或 CORE。
- `contracts/verdict.json` 保持 verdict 集合真源。
- sandbox Status 与 judge Verdict 继续分离。

---

## 14. MVP 明确不做

- 多工作空间与完整 RBAC。
- SPJ、交互题、部分分、子任务和 hack 数据。
- 通用 CORE 函数签名和值编解码框架。
- 自动重判和面向用户的重判任务。
- WebSocket/SSE 推送；web 先轮询。
- Agent、模型供应商和提示词模型。
- 语言倍率作为最终限制。
- caseResults 正规化子表。
- Kafka 携带源码、模板、测试数据或完整标准答案。
- 服务共享数据库、跨服务 JOIN 或 XA/2PC。

---

## 15. 验收清单

- [ ] 每个核心实体只有一个写入服务。
- [ ] role 枚举严格使用 USER/ADMIN。
- [ ] ProblemVersion 发布后内容、语言和 CORE 模板不可修改。
- [ ] ProblemJudgeSnapshot 只返回已发布不可变版本。
- [ ] ExecutionProfile 同时验证环境、部署、hash 和有效标定。
- [ ] 创建提交失败时不留下半条 Submission。
- [ ] Submission、JudgeInput、幂等记录和 JudgeRequested Outbox 同事务创建。
- [ ] CORE 只替换唯一占位符，完整源码冻结在 JudgeInput。
- [ ] Kafka 不包含源码、模板、JWT 或隐藏数据。
- [ ] 重复 JudgeRequested 只产生一个 JudgeTask。
- [ ] 租约过期 Worker 的迟到结果不能覆盖当前结果。
- [ ] Submission DONE 不回退，最终基础设施失败映射为 DONE + SE。
- [x] judge 按 testDataVersionId 加载数据并回传 environmentFingerprint。
- [ ] 环境切换不修改旧 JudgeInput，旧任务仍能定位旧环境。
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
