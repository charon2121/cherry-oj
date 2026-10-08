# cherry-oj 系统架构

> 状态：MVP 目标架构，2026-08-19
> 产品范围：[`product.md`](./product.md)
> 后端技术基线：[backend.md](./backend.md)
> 数据与一致性：[data-model.md](./data-model.md)
> judge、执行层与 sandbox 执行器内部设计：[engine.md](./engine.md)

本文描述 Cherry OJ 当前唯一有效的系统拓扑。早期的“单体 `apps/server` 同步调用 judge”方案已经
废止；业务后端采用五个 Java 微服务，正式提交通过 Kafka 异步推进。判题编排与执行层的职责边界
保持不变（执行层已并入 judge 进程，见 engine.md）。

---

## 1. 架构原则

1. **产品事实与执行事实分开。** 题面、版本、模板和测试数据元信息属于 problem-service；判题节点、
   数据部署状态和标定后的绝对限制属于 judging-service。
2. **一次提交先冻结，再异步执行。** submission-service 在自己的事务中保存 Submission、不可变
   JudgeInput 和 Outbox；Kafka 消息不携带源码或隐藏数据。
3. **每个服务只写自己的数据库。** 跨服务不连表、不共享 Mapper、不直接读取对方 schema。
4. **长流程使用至少一次消息。** Outbox、Inbox、条件更新和 fencing token 保证重复投递与迟到结果
   不破坏状态，不声称 MySQL + Kafka 能“恰好一次”。
5. **跨边界契约先行。** 浏览器公开 REST 以 `contracts/web-api.openapi.json` 为唯一真源；内部服务、
   Kafka 和 Java/Go DTO 使用各自的 `contracts/*.json`，不能把公开 BFF envelope 扩散到内部协议。
6. **执行层只执行命令。** 编译、测试点编排、checker 和 verdict 都在判题编排；执行层与 sandbox
   执行器不知道题目、Submission、ACM 或 CORE。
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
                                      Go judge（判题编排 + 进程内执行层）
                                            │ 每次执行 exec 一次
                                            ▼
                                      sandbox 执行器（C, setuid-root）

judging-service ── Kafka judge.lifecycle.v1 ──► submission-service
submission-service：Pending → Judging → Done + verdict
```

基础设施：

- MySQL 8.4：每个有状态 Java 服务独立 schema 和账号。
- Redis：Gateway Session；不作为业务事实真源。
- Kafka：正式提交的判题请求和生命周期事件。
- 测试数据目录：problem-service 按[测试数据协议](./testdata-protocol.md)写出的目录（`testdata.json` + 成对 `.in/.out`），
  本地路径或 http(s) 地址；judge 节点按请求里的地址读取，不预先安装。

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

- Problem（题面、模式、难度、标签、公开状态都在题目本身，**没有版本**）、ProblemSample、ProblemLanguage，包括 CORE `starterCode` 与 `judgeTemplate`。
- 测试数据：只存目录**地址**（`test_data_location`）；指纹、测试点数和文件清单都从地址下的 `testdata.json` 读，不在库里抄一份。
- 题目审计。

提供两类接口：

- 面向用户/管理员的题库、详情、编辑、公开 API；测试数据上传即整体替换。
- 面向 submission-service 的 `ProblemJudgeSnapshot`：题目、标题、语言和代码模式（CORE 另含模板），不含测试数据地址。
- 面向 judging-service 的内部读取 `GET /internal/judging/problems/{id}/test-data`：当前测试数据的地址、指纹、测试点数（只认 judging-service 的服务令牌）。

problem-service 不保存 Submission，不选择判题节点，不保存标定后的绝对限制，不执行判题。

### 3.4 submission-service

唯一写入：

- Submission：用户可见生命周期与原始源码。
- JudgeInput：创建提交时冻结的一次完整判题输入。
- SubmissionRequest：创建接口幂等记录。
- Outbox / Inbox。
- 最终 JudgeResult 和用户可见测试点结果。

它是正式提交的业务编排者：

1. 从 problem-service 取得题目快照（题目必须公开、有可读的测试数据）。
2. 从 judging-service 取得该题目×语言的 ExecutionProfile。
3. ACM 直接使用用户源码；CORE 将用户源码替换进唯一 `{{USER_CODE}}`。
4. 在一个本地事务内写 Submission、JudgeInput、幂等记录和 `JudgeRequested` Outbox。
5. 消费生命周期事件，以条件更新推进用户可见状态。

JudgeInput 只能通过受服务身份保护的内部接口提供给 judging-service，不通过浏览器 API 返回。

### 3.5 judging-service

唯一写入：

- JudgeNode：节点身份、租约、声明的语言（没有逐节点的数据安装记录）。
- LanguageCalibration：按「题目 × 语言」的绝对限制，并记录标定时的测试数据指纹。
- JudgeTask、JudgeAttempt、租约和重试状态。
- Outbox / Inbox 与执行侧审计。

它提供：

- `ResolveExecutionProfile`：输入 `problemId + languageId`，向 problem-service 取当前测试数据的指纹和测试点数，
  确认有在线节点声明了该语言、且标定的数据指纹等于当前指纹，解析出绝对限制和执行预算。
- 正式判题 Worker：消费 JudgeRequested、拉取 JudgeInput，**判题时**再向 problem-service 取最新的测试数据地址，
  任选一个在线节点调用 Go judge，并保存判题实际读取的数据指纹。
- 自定义测试内部接口：接收已经准备好的完整源码和文本 testcases，同步调用 Go judge 的 trial 模式。
- 节点自注册/心跳和标定能力（标定请求带 problem-service 给出的地址与指纹，判题结果指纹不一致则标定作废）。

节点控制协议以 `contracts/judge-node.schema.json` 为准。节点只带身份：nodeId、每次进程启动新生成的
sessionId、访问地址和能判的语言，不上报机器信息，也不归并成「判题环境」。同一 nodeId 的新进程以
新 sessionId 注册即接替旧进程；已被接替的旧 sessionId 不能再夺回 nodeId。租约过期只停止路由，不删除节点或
历史事实。节点不需要交付任何数据：它按请求里的地址自己读取。

可用节点 = 租约未过期 + 声明了该语言。标定、自测和正式判题都路由给可用节点，不把任务绑定到某一台机器；
换机器或升级判题机不会让已有标定失效，需要时对题目重新标定即可。题目的测试数据换了（指纹变了），旧标定
才会过期。

judging-service 不读取 problem-service 或 submission-service 数据库；需要的内容来自版本化 HTTP
响应或 Kafka 事件。

### 3.6 Go judge

输入一个完整 JudgeRequest，负责：

- 按请求里的 `testDataLocation` 读 `testdata.json`，复制并校验成对 `.in/.out`（任何失败都是 SE，不会误判为答案错误）。
- 根据 language registry 编译完整源码。
- 逐测试点调用进程内执行层。
- 使用 checker 比对 stdout 与标准答案。
- 汇总 AC/WA/TLE/MLE/CE/SE 等 verdict。

judge 不读 Java 服务数据库，不解析 CORE 模板，不决定哪套限制生效。

### 3.7 执行层与 sandbox 执行器

执行层是 judge 进程内的库（`apps/judge-engine/execution`），不是独立服务：

- 准备文件、排队、调用执行器执行不可信进程。隔离由每次执行调用一次的 setuid-root C 执行器
  （`apps/sandbox`）完成，judge 进程本身非 root。
- 返回退出事实、CPU、墙钟、内存和受限 stdout/stderr。
- 不加载题目数据，不读取标准答案，不产生 OJ verdict；「执行层不懂判题」由包级依赖检查守住。

---

## 4. 同步 HTTP 边界

### 4.1 浏览器 API

浏览器只访问 Gateway：

- `/api/auth/**` → user-service
- `/api/problems/**` → problem-service
- `/api/submissions/**` → submission-service
- `/api/admin/problems/**` → problem-service
- `/api/admin/judging/**` → judging-service（Gateway 做前置检查，服务自身仍验权）

前端不能直接访问 Kafka、内部微服务或 judge。

浏览器请求 body 直接使用 endpoint DTO，不增加通用 wrapper。普通 JSON 成功响应统一为
`{ data, meta: { requestId, pagination? } }`；失败使用 RFC 9457 `application/problem+json`，在标准
字段之外携带稳定 `code`、`meta.requestId` 和可选 `violations`。Gateway 返回的 `X-Request-Id`
必须与 body 相同，并对内部异常和下游 5xx 脱敏。HTTP status 保留协议语义，不能把错误统一伪装为
200；`204`、二进制下载和 SSE 是不带 JSON envelope 的明确例外。

公开响应允许增加未知可选字段；删除、改名、改类型、收紧 enum 或改变 status/code 语义属于破坏性
变更。初期保持 `/api`，只有无法兼容迁移时才启用 `/api/v2`。

### 4.2 可判题题目快照（不含测试数据地址）

submission-service 调用 problem-service：

```text
ResolveProblemJudgeSnapshot(problemId, languageId)
  → problemId / title
  → languageId / codeMode
  → starterCode（用户查询需要时返回）
  → judgeTemplate（仅 CORE，内部接口返回）
```

题目没有版本，快照只是校验题目此刻公开、语言可用、数据可读。客户端不能提交 `judgeTemplate` 来覆盖它。

### 4.3 执行配置解析

submission-service 调用 judging-service：

```text
ResolveExecutionProfile(problemId, languageId, purpose?)
  → languageCalibrationId
  → effectiveLimits { cpuNs, memoryBytes, clockNs? }
  → executionBudgetNs   （按题目此刻的测试点数算出的传输预算）
```

只有同时满足以下条件才成功：

- 有在线节点声明了目标语言。
- 题目有可读的测试数据（judging-service 向 problem-service 取到当前指纹）。
- `problemId + languageId` 存在 VALID 标定，且标定时的数据指纹等于当前指纹（数据换了就过期，需重新标定）。

失败时不创建 Submission，不用默认限制降级。

### 4.4 JudgeInput 内部读取

judging-service 收到 JudgeRequested 后，使用服务身份调用 submission-service：

```text
GetJudgeInput(submissionId)
  → 完整、不可变 JudgeRequest 所需字段
```

源码不进入 Kafka。内部接口必须有超时、鉴权、大小上限和安全日志策略；不得记录源码正文。

#### 为什么源码不进 Kafka

这条规则在 2026-08-19 把正式判题改为「先冻结 JudgeInput、再经 Kafka 异步判题」时一并确定
（提交 891f0b8），当时只写下了规则，没有写理由。下面把理由补全：前两条在当时的文档里有明确
依据，后两条是事后根据上下文整理的，标注为推断。

1. **消息只推进流程，不搬运数据（当时明确）。** Kafka 只传推进流程所需的标识符和有大小上限的
   小型结果；事件 schema 用封闭字段和 1 MiB 总消息语义禁止源码与隐藏数据（见 `data-model.md`
   §13.2）。源码与合并模板后的完整输入可能很大，塞进消息会拖累 broker 与所有消费者。
2. **读取源码必须可控（当时明确）。** 源码只能经受服务身份保护的内部接口读取，接口有超时、
   鉴权、大小上限，且不得记录源码正文。Kafka 消息会被保留、复制，任何有 topic 读权限的消费者、
   运维工具和排障时的消息导出都能看到，做不到同样的控制；比赛期间的源码属于敏感数据。
3. **唯一真源、只冻结一次（推断）。** JudgeInput 只在 submission-service 的本地事务里冻结一次。
   消息只带 `submissionId`，重试、重判和迟到消费拿到的永远是同一份数据；需要删除或清理源码时
   也只有一处要处理，不必追查散落在消息日志里的副本。
4. **数据归属（推断）。** 源码归 submission-service 所有，与「每个服务只写自己的数据库」一致：
   其他服务只通过它的接口读取，不在消息流里保留副本。

这条规则同样适用于将来的任何任务分发方式：无论判题任务经 Kafka 还是经 HTTP 租约下发给判题机，
任务里只放引用，源码始终按引用从 submission-service 拉取。

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
4. HTTP → problem-service：解析 ProblemJudgeSnapshot
5. HTTP → judging-service：解析 ExecutionProfile
6. submission-service 准备完整源码
     ACM  = source
     CORE = judgeTemplate.replaceExactlyOnce("{{USER_CODE}}", source)
7. 本地事务：Submission(PENDING) + JudgeInput + Outbox(JudgeRequested)
8. 返回 202 Accepted + Location
9. judging-service Inbox 去重，创建 JudgeTask(READY)
10. Worker 领取租约，发布 JudgeStarted
11. Worker HTTP 拉取 JudgeInput，向 problem-service 取题目此刻的测试数据地址，选一个可用节点调用 Go judge
12. Go judge 按 testDataLocation 读取数据，用冻结的限制判题，结果带回读取的数据指纹
13. 保存 attempt（含数据指纹），发布 JudgeCompleted 或 JudgeFailed
14. submission-service Inbox 去重并条件更新 Submission
15. web 轮询看到 PENDING → JUDGING → DONE + verdict
```

步骤 4、5 失败时不创建提交。步骤 7 成功后，即使 Kafka、Worker 或 judge 暂时不可用，也由 Outbox、
租约和重试继续推进，不让 HTTP 请求持有长事务。

---

## 7. CORE 模式

CORE 不是一种新的 judge 协议，只是一种用户源码准备方式。

`ProblemLanguage.judgeTemplate` 保存完整源码，其中必须恰有一个字面量 `{{USER_CODE}}`。例如：

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

- 模板、starterCode 与语言属于题目本身，修改立即生效；已提交的 JudgeInput 已冻结合并后的完整源码，不受影响。
- submission-service 只对模板执行一次非递归字面量替换。
- 用户源码原文保存在 Submission；合并后的完整源码保存在 JudgeInput。
- judgeTemplate 不返回普通用户 API。
- 发布检查必须用参考核心代码合并模板，并在判题节点上通过样例和正式数据。
- judge、执行层、测试数据格式和 checker 不区分 ACM/CORE；两者都使用 stdin/stdout `.in/.out`。

---

## 8. 测试数据、节点与标定

### 8.1 测试数据（协议目录）

题目只有一份测试数据，上传 ZIP 即整体替换。problem-service 把它写成[测试数据协议](./testdata-protocol.md)规定的目录：

```text
<root>/<problemId>  →（相对符号链接）.store/<problemId>-<digest 前 16 位>/
  testdata.json          schemaVersion、testcaseCount、totalBytes、digest、testcases[]（含每个文件的大小与 SHA-256）
  1.in  1.out
  2.in  2.out
```

写入内容寻址的真实目录后再原子切换符号链接，读取方任何时刻都读到一份完整的数据。数据库只存目录地址，
指纹、测试点数、文件清单都从 `testdata.json` 读。

### 8.2 节点读取

节点不预先安装数据：每次判题请求带 `testDataLocation`（本地绝对路径或 http(s) 地址），Go judge 读 `testdata.json`，
把文件复制到私有工作目录并核对大小与 SHA-256（撞上写入方替换时重读重试一次），`.out` 不进入沙箱，`.in` 作为标准输入。
本地路径要求节点能读到同一个路径（Compose 用必填的 `CHERRY_TEST_DATA_ROOT` 按同一绝对路径只读挂载）。
安全（地址白名单、隐藏数据的访问控制）暂未作为约束，是已知缺口。

### 8.3 LanguageCalibration

由 judging-service 保存，唯一对应：

```text
problemId + languageId
  → cpuNs + memoryBytes + clockNs?
  → testDataDigest（标定时所用测试数据的指纹）
```

标定不绑定机器，但绑定数据：题目的测试数据换了，旧标定过期，新提交被挡住，直到按新数据重新标定。重新标定产生
新的 VALID 记录并替换旧记录；已经冻结进 JudgeInput 的限制不受影响。

---

## 9. 自定义测试

自定义测试不创建正式 Submission，也不进入 Kafka 和通过状态统计：

1. submission-service 解析当前 ProblemJudgeSnapshot 和 ExecutionProfile（trial 用单测试点预算）。
2. CORE 使用相同规则合并模板。
3. submission-service 同步调用 judging-service 的内部 trial API。
4. judging-service 调用 Go judge，使用请求内文本 testcases。
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
│   ├── judge-engine/                  Go module
│   │   ├── cmd/judge/
│   │   ├── judge/                     判题编排
│   │   └── execution/                 进程内执行层
│   └── sandbox/                       C 执行器（setuid-root，每次执行一个进程）
└── compose.yaml
```

每个 Java 服务独立构建、独立容器、独立数据库账号。可以共享父 POM/BOM 和纯技术测试工具，但不得
共享业务实体、Mapper 或数据库表。

本地 Compose 最终需要：web、五个 Java 服务、judge、MySQL、Redis、Kafka。开发早期可按
纵向切片只启动所需服务，但不能因此改变服务所有权。

---

## 11. 安全与可观测性

- 浏览器只持有 Session Cookie，不接触内部 JWT。
- 每个资源服务自行验证 JWT，不信任裸 `X-User-Id`。
- 源码、密码、Cookie、JWT、隐藏数据和标准答案不得进入日志或 Kafka。
- 日志统一包含 traceId；判题链路包含 submissionId、taskId、attemptNo 和 nodeId。
- public request ID 由 Gateway 生成，只关联一次同步 HTTP 支持请求；内部 Trace 使用 W3C
  `traceparent`/`tracestate`。未来实现必须由 Gateway 丢弃外部 trace/baggage 上下文并新建内部 root；baseline
  不传播 baggage。request ID、Trace ID、幂等键和业务 ID 不得互换。
- HTTP/Kafka 的 Trace 父子传播只认 transport header。`judge-events.traceId` 是当前 32 位小写十六进制
  Trace ID 的可查询副本，不能单凭它重建 parent；JudgeRequest、RunSpec 等业务 body 不增加 Trace 字段。
- Java/Go 已实现统一 JSON 日志和 HTTP W3C Trace 传播，具体字段与文件滚动见
  [`logging.md`](./logging.md)。Kafka 传播仍是待业务链实现的契约；仓库没有 Trace exporter、Metrics、
  日志平台或 collector，不能把日志关联误认为已有完整观测后端。
- 用户代码和 Agent 生成代码都只在 sandbox 执行器的隔离环境里执行。
- 当前 host container 只适合开发和内部 MVP；公网不可信执行前必须完成 namespace/cgroup 硬化。
- Gateway、内部 HTTP、Kafka consumer 和 judge 调用都必须有界超时与大小限制。

---

## 12. 实现顺序

```text
1. [x] 更新 contracts：Submission / Judge / Kafka 事件 / 内部快照
2. [x] 同步 Go contract 与 judge 按地址读取测试数据（testDataLocation）
3. 初始化 Java 父工程、基础设施和服务骨架
4. 实现 problem snapshot + execution profile
5. 实现 Submission + JudgeInput + Outbox/Inbox
6. 实现 judging task、租约、重试和 Go judge 调用
7. 跑通 C++ ACM A+B
8. 加入 CORE 模板合并
9. 接入 web 登录、题库、提交和轮询
10. 补题目生产和标定工作台
```

不得先在某种语言实现私有 DTO 再反推 schema；不得为了跑通 demo 让服务跨库读写；不得把正式判题
改回同步 HTTP 长请求。
