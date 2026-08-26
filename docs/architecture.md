# cherry-oj 系统架构

> 状态：MVP 目标架构，2026-08-19
> 产品范围：[`product.md`](./product.md)
> 后端技术基线：[backend.md](./backend.md)
> 数据与一致性：[data-model.md](./data-model.md)
> judge / sandbox 内部设计：[engine.md](./engine.md)

本文描述 Cherry OJ 当前唯一有效的系统拓扑。早期的“单体 `apps/server` 同步调用 judge”方案已经
废止；业务后端采用五个 Java 微服务，正式提交通过 Kafka 异步推进。Go judge 与 sandbox 的职责边界
保持不变。

---

## 1. 架构原则

1. **产品事实与执行事实分开。** 题面、版本、模板和测试数据元信息属于 problem-service；判题环境、
   数据部署状态和环境相关限制属于 judging-service。
2. **一次提交先冻结，再异步执行。** submission-service 在自己的事务中保存 Submission、不可变
   JudgeInput 和 Outbox；Kafka 消息不携带源码或隐藏数据。
3. **每个服务只写自己的数据库。** 跨服务不连表、不共享 Mapper、不直接读取对方 schema。
4. **长流程使用至少一次消息。** Outbox、Inbox、条件更新和 fencing token 保证重复投递与迟到结果
   不破坏状态，不声称 MySQL + Kafka 能“恰好一次”。
5. **跨边界契约先行。** 浏览器公开 REST 以 `contracts/web-api.openapi.json` 为唯一真源；内部服务、
   Kafka 和 Java/Go DTO 使用各自的 `contracts/*.json`，不能把公开 BFF envelope 扩散到内部协议。
6. **sandbox 只执行命令。** 编译、测试点编排、checker 和 verdict 都在 judge；sandbox 不知道题目、
   Submission、ACM 或 CORE。
7. **源码模式在进入 judge 前消失。** ACM 源码直接冻结；CORE 用户源码在 submission-service 中与
   题目语言模板合并，JudgeRequest 始终携带完整、可编译源码。

---

## 2. 系统拓扑

```text
浏览器
  │ HTTPS / REST / Session Cookie
  ▼
apps/web                         React + TypeScript + Vite
  │ /api
  ▼
gateway-service                 BFF、Session、CSRF、路由、统一错误
  ├──────── HTTP ───────► user-service
  ├──────── HTTP ───────► problem-service
  └──────── HTTP ───────► submission-service
                              │
                              ├─ HTTP 读取 problem-service 的可判题快照
                              ├─ HTTP 读取 judging-service 的执行配置
                              └─ 本地事务：Submission + JudgeInput + Outbox
                                            │
                                            ▼
                                      Kafka judge.requests.v1
                                            │
                                            ▼
                                      judging-service
                                            │ 内部 HTTP 拉取 JudgeInput
                                            │ HTTP /judge
                                            ▼
                                      Go judge
                                            │ HTTP /run, /blobs
                                            ▼
                                      Go sandbox

judging-service ── Kafka judge.lifecycle.v1 ──► submission-service
submission-service：Pending → Judging → Done + verdict
```

基础设施：

- MySQL 8.4：每个有状态 Java 服务独立 schema 和账号。
- Redis：Gateway Session；不作为业务事实真源。
- Kafka：正式提交的判题请求和生命周期事件。
- 文件/对象存储：测试数据包和后续题目资产；具体供应商暂不锁定。
- judge 节点本地只读测试数据目录：按 `testDataVersionId` 定位已部署测例。

---

## 3. 服务职责与数据所有权

### 3.1 gateway-service

负责：

- 浏览器唯一后端入口和 BFF 路由。
- Redis Session、Cookie、CSRF、CORS、基础限流。
- 将 user-service 签发的短期内部 JWT 转发给资源服务。
- 生成公开 request ID、关联内部 trace、统一外部错误格式并隐藏内部服务拓扑。

不负责：密码校验、签发用户身份、业务表写入、聚合跨服务事务。

### 3.2 user-service

唯一写入：

- User、密码摘要、账号状态、角色。
- 内部 JWT 密钥与会话版本。
- 用户安全审计。

MVP 角色为 `USER | ADMIN`。单管理员是部署策略，不用数据库约束限制管理员记录数。

### 3.3 problem-service

唯一写入：

- Problem、ProblemVersion、ProblemSample。
- ProblemVersionLanguage，包括 CORE `starterCode` 与 `judgeTemplate`。
- TestDataVersion 元信息、manifest、内容 hash 和长期资产引用。
- 题目发布审计。

提供两类接口：

- 面向用户/管理员的题库、详情、草稿、发布 API。
- 面向 submission-service 的不可变 `ProblemJudgeSnapshot`：明确版本、语言、代码模式、模板和
  `testDataVersionId`。

problem-service 不保存 Submission，不选择判题环境，不保存环境相关绝对限制，不执行判题。

### 3.4 submission-service

唯一写入：

- Submission：用户可见生命周期与原始源码。
- JudgeInput：创建提交时冻结的一次完整判题输入。
- SubmissionRequest：创建接口幂等记录。
- Outbox / Inbox。
- 最终 JudgeResult 和用户可见测试点结果。

它是正式提交的业务编排者：

1. 从 problem-service 取得不可变题目快照。
2. 从 judging-service 取得与该快照匹配的 ExecutionProfile。
3. ACM 直接使用用户源码；CORE 将用户源码替换进唯一 `{{USER_CODE}}`。
4. 在一个本地事务内写 Submission、JudgeInput、幂等记录和 `JudgeRequested` Outbox。
5. 消费生命周期事件，以条件更新推进用户可见状态。

JudgeInput 只能通过受服务身份保护的内部接口提供给 judging-service，不通过浏览器 API 返回。

### 3.5 judging-service

唯一写入：

- JudgeEnvironment、JudgeEnvironmentLanguage。
- TestDataDeployment。
- LanguageCalibration。
- JudgeTask、JudgeAttempt、租约和重试状态。
- Outbox / Inbox 与执行侧审计。

它提供：

- `ResolveExecutionProfile`：输入明确的 `problemVersionId + testDataVersionId +
  testDataContentSha256 + languageId`，从当前 ACTIVE 环境解析出环境、部署回执、有效标定和绝对限制。
- 正式判题 Worker：消费 JudgeRequested、拉取 JudgeInput、调用指定环境的 Go judge。
- 自定义测试内部接口：接收已经准备好的完整源码和文本 cases，同步调用 Go judge 的 trial 模式。
- 环境注册、测试数据部署、标定和环境切换能力。

judging-service 不读取 problem-service 或 submission-service 数据库；需要的内容来自版本化 HTTP
响应或 Kafka 事件。

### 3.6 Go judge

输入一个完整 JudgeRequest，负责：

- 按 `testDataVersionId` 加载正式 `.in/.out`。
- 根据 language registry 编译完整源码。
- 逐测试点调用 sandbox。
- 使用 checker 比对 stdout 与标准答案。
- 汇总 AC/WA/TLE/MLE/CE/SE 等 verdict。
- 回传实际 `environmentFingerprint`。

judge 不读 Java 服务数据库，不解析 CORE 模板，不决定哪套限制生效。

### 3.7 Go sandbox

只提供 `/blobs` 与 `/run`：

- 准备文件、启动不可信进程、执行资源隔离。
- 返回退出事实、CPU、墙钟、内存和受限 stdout/stderr。
- 不加载题目数据，不读取标准答案，不产生 OJ verdict。

---

## 4. 同步 HTTP 边界

### 4.1 浏览器 API

浏览器只访问 Gateway：

- `/api/auth/**` → user-service
- `/api/problems/**` → problem-service
- `/api/submissions/**` → submission-service
- `/api/admin/problems/**` → problem-service
- `/api/admin/judging/**` → judging-service（Gateway 做前置检查，服务自身仍验权）

前端不能直接访问 Kafka、内部微服务、judge 或 sandbox。

浏览器请求 body 直接使用 endpoint DTO，不增加通用 wrapper。普通 JSON 成功响应统一为
`{ data, meta: { requestId, pagination? } }`；失败使用 RFC 9457 `application/problem+json`，在标准
字段之外携带稳定 `code`、`meta.requestId` 和可选 `violations`。Gateway 返回的 `X-Request-Id`
必须与 body 相同，并对内部异常和下游 5xx 脱敏。HTTP status 保留协议语义，不能把错误统一伪装为
200；`204`、二进制下载和 SSE 是不带 JSON envelope 的明确例外。

公开响应允许增加未知可选字段；删除、改名、改类型、收紧 enum 或改变 status/code 语义属于破坏性
变更。初期保持 `/api`，只有无法兼容迁移时才启用 `/api/v2`。

### 4.2 可判题题目快照

submission-service 调用 problem-service：

```text
ResolveProblemJudgeSnapshot(problemId, languageId)
  → problemId
  → problemVersionId / versionNo / title
  → testDataVersionId
  → languageId / codeMode
  → starterCode（用户查询需要时返回）
  → judgeTemplate（仅 CORE，内部接口返回）
```

这个响应指向已经发布且不可变的记录。客户端不能提交 `problemVersionId` 或 `judgeTemplate` 来覆盖它。

### 4.3 执行配置解析

submission-service 调用 judging-service：

```text
ResolveExecutionProfile(
  problemVersionId,
  testDataVersionId,
  testDataContentSha256,
  languageId
)
  → judgeEnvironmentId / environmentFingerprint
  → languageCalibrationId
  → effectiveLimits { cpuNs, memoryBytes, clockNs? }
```

只有同时满足以下条件才成功：

- 存在唯一 ACTIVE 环境。
- 环境启用了目标语言。
- `testDataVersionId + environmentId` 部署回执为 READY 且 hash 一致。
- `problemVersionId + languageId + environmentId` 存在当前 VALID 标定。

失败时不创建 Submission，不用默认限制降级。

### 4.4 JudgeInput 内部读取

judging-service 收到 JudgeRequested 后，使用服务身份调用 submission-service：

```text
GetJudgeInput(submissionId)
  → 完整、不可变 JudgeRequest 所需字段
```

源码不进入 Kafka。内部接口必须有超时、鉴权、大小上限和安全日志策略；不得记录源码正文。

---

## 5. Kafka 边界

### 5.1 Topic

- `judge.requests.v1`：submission-service → judging-service。
- `judge.lifecycle.v1`：judging-service → submission-service。
- `judge.lifecycle.dlt`：无法解析或无法处理的生命周期毒消息。

所有事件以 `submissionId` 为 Kafka key。

### 5.2 事件信封

```text
EventEnvelope {
  eventId,
  eventType,
  eventVersion,
  occurredAt,
  traceId,
  aggregateId,   // submissionId
  payload
}
```

`JudgeRequested` 只携带 `submissionId` 和必要的路由/对账标识，不携带源码、模板、测试数据或 JWT。

`JudgeStarted` 携带 submissionId、taskId、attemptNo 和开始时间。

`JudgeCompleted` 携带受契约大小限制的 JudgeResult。结果只包含 verdict、资源用量、受控诊断和测试点
摘要，不包含隐藏输入或标准答案全文。

`JudgeFailed` 携带稳定错误码和安全摘要；submission-service 将最终失败映射为 `DONE + SE`。

### 5.3 可靠性

- 生产者在业务事务中写 Outbox，Relay 独立发布。
- 消费者在本地事务中先写 Inbox，再推进业务状态。
- `eventId` 唯一约束负责去重。
- JudgeTask 使用 `leaseToken + leaseUntil + attemptNo`；只有当前 token 能落结果。
- 网络超时与 5xx 有界重试，契约错误和不可重试 4xx 直接失败。
- Submission 的 DONE 状态不可回退；重复完成事件不覆盖已经接受的终态。

---

## 6. 正式提交流程

```text
1. 浏览器 POST /api/submissions { problemId, languageId, source }
2. Gateway 验证 Session，转发内部 JWT
3. submission-service 从 JWT 取得 userId，校验幂等键
4. HTTP → problem-service：解析不可变 ProblemJudgeSnapshot
5. HTTP → judging-service：解析 ExecutionProfile
6. submission-service 准备完整源码
     ACM  = source
     CORE = judgeTemplate.replaceExactlyOnce("{{USER_CODE}}", source)
7. 本地事务：Submission(PENDING) + JudgeInput + Outbox(JudgeRequested)
8. 返回 202 Accepted + Location
9. judging-service Inbox 去重，创建 JudgeTask(READY)
10. Worker 领取租约，发布 JudgeStarted
11. Worker HTTP 拉取 JudgeInput，按 judgeEnvironmentId 路由 Go judge
12. Go judge 按 testDataVersionId 判题并返回 environmentFingerprint
13. judging-service 校验 fingerprint 与 JudgeInput 一致
14. 保存 attempt，发布 JudgeCompleted 或 JudgeFailed
15. submission-service Inbox 去重并条件更新 Submission
16. web 轮询看到 PENDING → JUDGING → DONE + verdict
```

步骤 4、5 失败时不创建提交。步骤 7 成功后，即使 Kafka、Worker 或 judge 暂时不可用，也由 Outbox、
租约和重试继续推进，不让 HTTP 请求持有长事务。

---

## 7. CORE 模式

CORE 不是一种新的 judge 协议，只是一种用户源码准备方式。

`ProblemVersionLanguage.judgeTemplate` 保存完整源码，其中必须恰有一个字面量 `{{USER_CODE}}`。例如：

```cpp
#include <iostream>
using namespace std;

{{USER_CODE}}

int main() {
    int a, b;
    cin >> a >> b;
    cout << add(a, b) << '\n';
}
```

规则：

- 模板、starterCode 与语言都随 ProblemVersion 发布后冻结。
- submission-service 只对模板执行一次非递归字面量替换。
- 用户源码原文保存在 Submission；合并后的完整源码保存在 JudgeInput。
- judgeTemplate 不返回普通用户 API。
- 发布检查必须用参考核心代码合并模板，并在目标环境通过样例和正式数据。
- judge、sandbox、测试数据格式和 checker 不区分 ACM/CORE；两者都使用 stdin/stdout `.in/.out`。

---

## 8. 测试数据、环境与标定

### 8.1 TestDataVersion

problem-service 保存不可变数据包元信息和长期资产引用。judge 节点的正式目录为：

```text
<testdata-root>/<testDataVersionId>/
  1.in  1.out
  2.in  2.out
```

### 8.2 TestDataDeployment

judging-service 保存数据送达某 JudgeEnvironment 的回执。READY 表示目标 judge 节点能够按
`testDataVersionId` 读取数据，且部署 hash 与 problem-service 提供的内容 hash 一致。

### 8.3 JudgeEnvironment

代表一套长期执行基线，而不是一次 sandbox 工作间。MVP 同时只有一个 ACTIVE 环境；旧环境可以
RETIRED，但必须允许已经冻结到它的在途 JudgeTask 完成或明确失败后重试。

### 8.4 LanguageCalibration

由 judging-service 保存，唯一对应：

```text
problemVersionId + languageId + judgeEnvironmentId
  → cpuNs + memoryBytes + clockNs?
```

环境切换不复制 ProblemVersion。新环境只有在准备好所需 TestDataDeployment 和 LanguageCalibration
后才能激活；新 Submission 使用新环境，旧 JudgeInput 仍保留旧环境和旧限制。

---

## 9. 自定义测试

自定义测试不创建正式 Submission，也不进入 Kafka 和通过状态统计：

1. submission-service 解析当前 ProblemJudgeSnapshot 和 ExecutionProfile。
2. CORE 使用相同规则合并模板。
3. submission-service 同步调用 judging-service 的内部 trial API。
4. judging-service 调用 Go judge，使用请求内文本 cases。
5. 返回 stdout/stderr、资源用量和运行状态。

自定义测试需要独立限流和更短超时，不能挤占正式判题 Worker。

---

## 10. 仓库与部署单元

目标结构：

```text
cherry-oj/
├── contracts/                         跨服务、跨语言与事件契约
├── apps/
│   ├── web/                           React SPA
│   ├── server/                        Java Maven 聚合工程
│   │   ├── gateway-service/
│   │   ├── user-service/
│   │   ├── problem-service/
│   │   ├── submission-service/
│   │   └── judging-service/
│   └── judge-engine/                  Go module
│       ├── cmd/judge/
│       ├── cmd/sandbox/
│       └── internal/{judge,sandbox}/
└── compose.yaml
```

每个 Java 服务独立构建、独立容器、独立数据库账号。可以共享父 POM/BOM 和纯技术测试工具，但不得
共享业务实体、Mapper 或数据库表。

本地 Compose 最终需要：web、五个 Java 服务、judge、sandbox、MySQL、Redis、Kafka。开发早期可按
纵向切片只启动所需服务，但不能因此改变服务所有权。

---

## 11. 安全与可观测性

- 浏览器只持有 Session Cookie，不接触内部 JWT。
- 每个资源服务自行验证 JWT，不信任裸 `X-User-Id`。
- 源码、密码、Cookie、JWT、隐藏数据和标准答案不得进入日志或 Kafka。
- 日志统一包含 traceId；判题链路包含 submissionId、taskId、attemptNo 和 environmentId。
- public request ID 由 Gateway 生成，只关联一次同步 HTTP 支持请求；内部 Trace 使用 W3C
  `traceparent`/`tracestate`。未来实现必须由 Gateway 丢弃外部 trace/baggage 上下文并新建内部 root；baseline
  不传播 baggage。request ID、Trace ID、幂等键和业务 ID 不得互换。
- HTTP/Kafka 的 Trace 父子传播只认 transport header。`judge-events.traceId` 是当前 32 位小写十六进制
  Trace ID 的可查询副本，不能单凭它重建 parent；JudgeRequest、RunSpec 等业务 body 不增加 Trace 字段。
- 上述 Request/Trace 规则当前是追溯契约，不代表仓库已经接入 Trace SDK、exporter、Metrics、日志平台或
  collector；运行时实现必须在独立工作项中重新设计并确认侵入边界。
- 用户代码和 Agent 生成代码都只在 sandbox 执行。
- 当前 host container 只适合开发和内部 MVP；公网不可信执行前必须完成 namespace/cgroup 硬化。
- Gateway、内部 HTTP、Kafka consumer 和 judge 调用都必须有界超时与大小限制。

---

## 12. 实现顺序

```text
1. [x] 更新 contracts：Submission / Judge / Kafka 事件 / 内部快照
2. [x] 同步 Go contract 与 judge 的 testDataVersionId 加载
3. 初始化 Java 父工程、基础设施和服务骨架
4. 实现 problem snapshot + execution profile
5. 实现 Submission + JudgeInput + Outbox/Inbox
6. 实现 judging task、租约、重试和 Go judge 调用
7. 跑通 C++ ACM A+B
8. 加入 CORE 模板合并
9. 接入 web 登录、题库、提交和轮询
10. 补题目生产、环境迁移和标定工作台
```

不得先在某种语言实现私有 DTO 再反推 schema；不得为了跑通 demo 让服务跨库读写；不得把正式判题
改回同步 HTTP 长请求。
