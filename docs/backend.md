# Cherry OJ 后端技术栈与架构基线

> 本文记录截至 2026 年 8 月已经确认的后端技术选型，是后端初始化和后续实现的基线。
> 服务拓扑和数据所有权已与 [architecture.md](./architecture.md)、[data-model.md](./data-model.md)
> 统一；三者发生偏差时必须先更新设计，不允许实现自行选择其中一套。

## 1. 设计目标

Cherry OJ 是学习型项目，后端直接采用微服务，不以单体作为最终形态。拆分的目的不是追求服务数量，
而是完整实践服务边界、独立数据所有权、身份传递、异步消息、幂等消费、故障恢复和可观测性。

落地遵循以下原则：

- 服务按业务能力拆分，不按 Controller、Service、DAO 等技术层拆分。
- 每个服务只写自己的数据库；跨服务不连表、不共享 Mapper、不直接访问对方数据库。
- 用户请求优先走同步 HTTP，跨事务状态推进走 Kafka 事件。
- 不承诺分布式“恰好一次”；采用至少一次投递，并通过 Outbox、Inbox 和条件更新保证业务正确。
- 数据库事务必须短小，事务内不执行长时间 HTTP、Kafka 发送或判题。
- 跨服务与跨语言的共享 DTO 继续以 `contracts/*.json` 为唯一真源。

## 2. 总体技术栈

### 2.1 Java 业务后端

- **Java 21 LTS**：业务微服务的运行时与语言版本。该版本在 Spring Boot 4.1 的支持范围内，
  同时兼容当前开发使用的 IntelliJ IDEA；父 POM、IDE Project SDK、Maven Runner 和部署环境统一为 21。
- **Spring Boot 4.1**：服务基础框架、配置、Web、数据访问和生产能力。
- **Spring Cloud 2025.1.2**：Gateway、服务间调用及微服务基础设施的统一版本基线。
- **Spring MVC**：identity、problem、submission、judging 等普通业务服务的 HTTP 栈。
- **Spring Cloud Gateway / WebFlux**：只用于 gateway-service；不把响应式编程扩散到普通业务服务。
- **Maven**：Java 服务统一构建工具。每个服务可独立构建、测试和生成容器镜像。
- **Jakarta Validation**：HTTP DTO 的结构校验；业务不变量仍由应用层显式检查。

版本统一放在父 POM / BOM 中管理，子服务不得分别声明 Spring 组件版本。稳定正式版优先，
主链路不使用 alpha、beta 或 RC 依赖。

### 2.2 Go 判题后端

- **Go 1.26.3**：`apps/judge-engine` 当前模块版本。
- 同一个 Go module 产出 **judge** 与 **sandbox** 两个独立进程。
- **judge** 负责编译、逐测试点运行、checker 和 verdict 汇总。
- **sandbox** 只负责安全执行命令以及返回时间、内存、退出状态和输出，不理解题目与 verdict。
- judge 与 sandbox 通过 HTTP/JSON 通信，并共享 Go 内部契约类型。
- sandbox 目标环境为 Linux，隔离基础为 namespace、cgroup v2；本地开发可使用 host container 实现。

Go 侧继续保持标准库优先。目前外部运行时依赖只有 `gopkg.in/yaml.v3`，用于配置解析。

### 2.3 基础设施

- **MySQL 8.4 LTS**：业务数据的唯一关系型数据库。
- **Redis**：Gateway 登录 Session 等短生命周期共享状态；不作为业务事实的真源。
- **Apache Kafka**：提交判题等跨服务异步事件。
- **Docker / Docker Compose**：本地开发与多服务联调。
- **Linux + cgroup v2**：生产判题环境；sandbox 是唯一需要特权能力的服务。

## 3. 微服务边界

初始后端拆为五个 Java 服务，并保留两个 Go 执行服务。

### 3.1 gateway-service

职责：

- 浏览器访问后端的唯一入口，提供 BFF 边界。
- 路由、统一错误格式、请求 ID / trace ID、基础限流和跨域处理。
- 接收注册、登录和退出请求，但不在 Gateway 内实现密码校验；认证逻辑交给自建 user-service。
- 登录成功后持有浏览器 Session，并向下游转发自建用户服务签发的内部短期 JWT。
- 隐藏内部微服务拓扑；前端不直接访问任何内部服务。

技术：Spring Cloud Gateway、WebFlux、Spring Security、Spring Session Redis。

### 3.2 user-service

职责：

- 自建用户注册、登录、退出、密码修改和账号状态管理。
- 持有用户、密码摘要、角色、权限以及登录会话的唯一真源。
- 校验账号密码，向 Gateway 返回认证结果，并签发只用于 Cherry OJ 内部调用的短期 JWT。
- 管理内部令牌的签名密钥、轮换和公开验证密钥；私钥不得离开 user-service。
- 不接入外部身份提供商，不依赖第三方用户系统，也不提供社交账号登录。

技术：Spring MVC、Spring Security 7、Spring Security JWT/JOSE、MyBatis、MySQL、Flyway。

### 3.3 problem-service

职责：

- 题目、不可变题目版本、样例、checker 配置和测试数据版本元信息。
- 保存每个题目版本允许的语言，以及 CORE 的 starterCode 和 judgeTemplate。
- 发布题目修订版本；向 submission-service 提供不可变 `ProblemJudgeSnapshot`。
- 不保存用户提交，不选择判题环境，不保存环境相关绝对限制，不执行判题。

技术：Spring MVC、MyBatis、MySQL、Flyway。

### 3.4 submission-service

职责：

- 接收提交、请求幂等、保存用户原始源码和冻结后的 JudgeInput。
- 调用 problem-service 解析不可变题目快照，调用 judging-service 解析 ExecutionProfile。
- ACM 直接冻结用户源码；CORE 将源码合并进题目语言模板后冻结完整源码。
- 持有用户可见的 Submission 状态和逐测试点结果。
- 通过 Outbox 发布 `JudgeRequested`，消费判题生命周期事件并更新投影。
- 对前端暴露提交创建与结果查询 API。
- 通过受服务身份保护的内部 API 向 judging-service 提供 JudgeInput；源码不进入 Kafka。

技术：Spring MVC、MyBatis、MySQL、Flyway、Kafka、Outbox / Inbox。

### 3.5 judging-service

职责：

- 消费判题请求，创建和调度 JudgeTask / JudgeAttempt。
- 持有 JudgeEnvironment、JudgeEnvironmentLanguage、TestDataDeployment 和 LanguageCalibration。
- 根据明确的题目版本、数据版本和语言解析当前可用 ExecutionProfile。
- 通过租约领取任务，调用 Go judge，并处理超时、退避重试和死任务。
- Worker 根据 submissionId 从 submission-service 内部 API 拉取不可变 JudgeInput。
- 通过 fencing token 拒绝旧 Worker 的迟到结果。
- 发布 `JudgeStarted`、`JudgeCompleted`、`JudgeFailed`。

技术：Spring MVC、MyBatis、MySQL、Flyway、Kafka、Outbox / Inbox、租约调度。

### 3.6 judge 与 sandbox

- **judge（Go）**：从 judging-service 取得一次判题请求，完成编译、运行、比对和汇总。
- **sandbox（Go）**：被 judge 调用，安全执行一条命令并返回执行事实。
- Java 与 Go 之间使用 HTTP/JSON；请求字段、单位和可选语义由 JSON Schema 约束。

## 4. API、鉴权与身份传递

用户系统和认证能力均由 Cherry OJ 自建。这里使用 JWT 作为内部身份信封，但不采用第三方 IdP、
OIDC 登录跳转或独立的 OAuth 授权服务器。

### 4.1 登录链路

1. 浏览器向 Gateway 的 `/api/auth/login` 提交账号和密码。
2. Gateway 将凭据通过内部 HTTPS 转发给 user-service；不得记录密码或完整登录请求体。
3. user-service 从自己的 MySQL 读取用户和密码摘要，使用 Spring Security `PasswordEncoder` 校验。
4. 认证成功后，user-service 返回最小用户主体，并签发短期内部 JWT；失败统一返回模糊错误，避免泄露账号是否存在。
5. Gateway 在 Redis 创建登录 Session，浏览器只收到随机 Session Cookie，不接触 JWT。
6. 后续请求由 Gateway 从 Session 取得内部 JWT 并转发给目标服务。
7. 退出登录时 Gateway 销毁 Redis Session；user-service 同步失效对应登录会话。密码修改、封禁等安全事件
   必须使已有会话失效。

### 4.2 密码与会话边界

- 密码只在注册、登录和修改密码时进入 user-service，不进入 Kafka，也不传播给其他业务服务。
- 密码只保存带算法标识的自适应哈希摘要；使用 Spring Security `PasswordEncoder`，禁止自制加密或明文可逆存储。
- Gateway Session 存在 Redis。Cookie 至少启用 `HttpOnly`、`Secure` 和合适的 `SameSite`，并对基于 Cookie
  的写请求启用 CSRF 防护。
- 登录失败需要按账号和来源做限速；连续失败、账号锁定和安全审计由 user-service 负责。
- Session Cookie 是浏览器认证凭据；内部 JWT 只是 Gateway 和微服务之间的身份传递格式，两者不能混用。

### 4.3 内部 JWT

- JWT 由 user-service 使用非对称密钥签发，私钥只存在 user-service，其他服务只持有公钥。
- claim 至少包含 `sub`（userId）、`roles` / `permissions`、`iat`、`exp`、`iss`、`aud`、`jti` 和会话版本。
- JWT 必须短期有效；Gateway 在自己的 Redis Session 存续期间，通过 user-service 的内部接口续签，
  续签凭据不得下发给浏览器。
- 各资源服务必须自行验证签名、issuer、audience、有效期和权限，不能只相信 Gateway 请求头。
- 服务间身份不使用可伪造的 `X-User-Id` 作为安全依据；业务 userId 只能从验证后的 `sub` 读取。
- 密钥通过 `kid` 支持轮换；轮换期间同时保留当前与上一把公钥，避免在途请求瞬间失效。

外部 API 使用 HTTPS + REST/JSON。普通查询和立即需要结果的命令使用同步 HTTP；判题这种长流程使用
Kafka 异步推进。HTTP 状态码描述本次协议交互是否成功，verdict 描述用户程序的运行结果：
AC、WA、TLE 等正常判题结论不是 HTTP 错误。

## 5. 数据访问与 MySQL

### 5.1 数据访问组件

- **MyBatis Spring Boot Starter 4**：Java 服务的数据访问入口。
- **Mapper XML**：SQL 的主要承载方式，SQL 保持可见、可审查、可直接复制到数据库执行。
- **Flyway**：数据库结构和基础数据迁移的唯一方式。
- **MySQL Connector/J**：MySQL JDBC 驱动。
- **HikariCP**：由 Spring Boot 管理的连接池。
- **Testcontainers MySQL**：验证 Mapper、事务、索引和 MySQL 方言的集成测试。

初期不引入 MyBatis-Plus、JPA / Hibernate、jOOQ，也不建立通用 Repository 抽象。选择 MyBatis 的目的
就是让 SQL 与性能边界保持明确，不再叠加另一套查询 DSL 或 ORM 心智模型。

### 5.2 SQL 规范

- 表名、列名和索引名统一使用 `lower_snake_case`；表名统一使用单数形式，禁止混用单复数命名风格。
  Java 字段使用 `camelCase`。
- SQL 关键字大写；主要子句换行；复杂条件按逻辑层级缩进。
- `SELECT` 必须显式列出需要的列，禁止 `SELECT *`。
- Mapper 方法只表达一个清晰的数据操作；不在 XML 中堆叠大段业务分支。
- 参数一律使用 `#{}` 绑定；除受控的固定枚举映射外，不使用 `${}` 拼接外部输入。
- 写操作必须显式列出列名；禁止依赖数据库列顺序。
- 分页和批处理查询必须有稳定排序；可能同值的排序字段追加主键作为最终顺序。
- 高频查询必须由真实查询条件反推联合索引，遵守最左前缀；不为每列机械建单列索引。
- 不使用存储过程、触发器承载业务逻辑；业务规则留在应用服务，数据库负责约束与持久化。
- 不使用 Hibernate `ddl-auto` 等运行时自动改表能力；所有 DDL 经 Flyway 审查和迁移。
- 跨服务禁止 JOIN；同一服务、同一数据库内允许为清晰查询使用 JOIN，避免人为制造 N+1。
- 数据库事务只覆盖本服务数据；跨服务一致性通过事件和补偿实现，不使用分布式 XA 事务。

### 5.3 数据所有权

每个微服务拥有独立 schema / database 和独立数据库账号。账号只获得本服务库的权限。即使本地开发阶段
多个 schema 共用一个 MySQL 实例，也不能跨库查询。共享数据通过 API 或带版本的事件传播。

## 6. ID 方案

业务聚合根和所有会跨服务、进入 URL、Kafka 或日志关联的数据使用 **UUIDv7**：

- Java 领域模型/API 表示为 UUID；JSON 使用标准带连字符字符串。
- MySQL 使用 `BINARY(16)` 保存，不使用 `CHAR(36)`。
- 统一提供 MyBatis `TypeHandler` 负责 UUID 与 16 字节之间的转换。
- UUIDv7 的时间有序特性比完全随机 UUID 更适合 B-Tree 聚簇索引，同时不依赖中心发号服务。
- 生成发生在创建聚合的服务内部；ID 一旦生成，全链路原样传递，其他服务不得重新编号。

Outbox 事件的 `eventId` 同样使用 UUIDv7。只在完全不出服务、没有外部引用的小型内部表中，才允许使用
MySQL `BIGINT AUTO_INCREMENT`，例如纯内部流水号或字典明细。不要把两种 ID 同时用于同一个业务身份。

## 7. Kafka 与可靠消息

### 7.1 Topic 基线

- `judge.requests.v1`：submission-service 生产，judging-service 消费；事件为 `JudgeRequested`。
- `judge.lifecycle.v1`：judging-service 生产，submission-service 消费；事件为
  `JudgeStarted`、`JudgeCompleted`、`JudgeFailed`。
- 两个 Topic 都使用 `submissionId` 作为消息 key，保证同一提交进入同一分区。
- 无法解析或无法处理的生命周期毒消息进入 `judge.lifecycle.dlt`，保留原始载荷并告警。

Topic 名和事件名带显式版本。兼容性变更升级 `eventVersion`；破坏性变更创建新 Topic，不原地改变旧消费者语义。

### 7.2 事件信封

每个事件至少包含：

- `eventId`：UUIDv7，用于 Inbox 去重。
- `eventType` 和 `eventVersion`：事件类型与契约版本。
- `occurredAt`：UTC 时间。
- `traceId`：全链路追踪关联。
- `aggregateId`：本链路固定为 submissionId。
- `payload`：事件自身数据。

Kafka 只传递推进流程所需的标识符和有大小上限的小型结果，不传源码、题目模板、测试数据或 JWT。
源码及合并后的完整 JudgeInput 保存在 submission-service 的 MySQL 中，由 judging-service 使用内部
服务身份按 submissionId 拉取；测试数据仍由判题侧文件存储管理。

### 7.3 Outbox / Inbox

- 业务写入与 Outbox 事件在同一个 MySQL 本地事务中提交。
- Relay 使用 `FOR UPDATE SKIP LOCKED` 领取待发布事件。
- Kafka 发布成功但 Outbox 未标记时允许重复发布，消费者必须按 `eventId` 幂等。
- 消费者在同一事务写 Inbox 和业务结果，事务提交后再确认 Kafka offset。
- Inbox 的 `eventId` 必须有唯一约束，不能只在应用内先查再插。
- 不追求 Kafka 与 MySQL 的跨系统恰好一次，也不引入 XA / 2PC。

## 8. 提交判题可靠性模型

完整交互设计见 [submission-judging-chain.html](./submission-judging-chain.html)。核心边界如下：

1. submission-service 向 problem-service 读取当前不可变 ProblemJudgeSnapshot；失败不创建提交。
2. submission-service 向 judging-service 解析匹配的 ExecutionProfile；数据未部署或缺少有效标定时不
   创建提交，也不使用默认限制降级。
3. submission-service 准备完整源码：ACM 直接使用用户源码，CORE 对 judgeTemplate 的唯一
   `{{USER_CODE}}` 做一次非递归替换。
4. 本地事务同时保存 Submission、冻结的 JudgeInput、幂等记录与 JudgeRequested Outbox，然后返回
   `202 Accepted` 和 `Location`。
5. judging-service 消费事件并创建 `JudgeTask(Ready)`；Kafka 消费线程不执行长时间判题。
6. Worker 通过 `leaseToken`、`leaseUntil` 和 `attemptNo` 领取任务，再从 submission-service 内部
   API 拉取 JudgeInput，提交事务后调用目标环境的 Go judge。
7. Go judge 返回的 environmentFingerprint 必须与 JudgeInput 冻结值一致，否则本次 Attempt 按系统
   故障处理。
8. 网络超时和 5xx 采用 1s / 10s / 60s 退避，默认最多 3 次；4xx、契约错误或重试耗尽进入 Dead。
9. 只有匹配当前 `leaseToken` 的 Worker 可以落结果和发布完成事件，旧 Worker 的迟到结果必须丢弃。
10. submission-service 幂等消费生命周期事件，使用条件更新保证 `Done` 永不回退。

用户只观察 `Pending → Judging → Done + verdict`。`Ready`、`Running`、`RetryWaiting`、`Dead` 属于内部
JudgeTask 状态；基础设施重试耗尽时，对用户映射为 `Done + SE`。

## 9. 测试策略

- Java 单元与组件测试使用 Spring Boot Test 提供的 JUnit 测试栈。
- Mapper、Flyway migration、事务、锁和索引行为使用 **Testcontainers + MySQL 8.4**，不使用 H2 模拟。
- Kafka 的序列化、Inbox 去重、重复投递和消费提交边界必须有集成测试。
- 服务间 HTTP 在单元测试中使用假客户端；跨服务关键路径在集成测试中启动真实服务依赖。
- JSON Schema 示例必须能被 Java 和 Go DTO 正确反序列化，并断言字段单位和可选语义。
- Go 侧继续使用标准 `testing`、`httptest` 和 `go test -race`。
- 关键故障必须可重复测试：Relay 重发、Kafka 重投、Worker 租约过期、旧 token 迟到、Judge 超时与毒消息。

## 10. 配置、部署与工程约束

- Java 服务使用各自的 `application.yaml`，敏感信息只从环境变量或部署 Secret 注入。
- 公共依赖版本通过 Maven BOM 管理；业务代码和 Mapper 不做跨服务源码共享。
- Docker Compose 用于本地拉起 MySQL、Redis、Kafka 和各服务；生产部署方案暂不在本文锁定。
- 健康检查至少区分存活与就绪；依赖暂时不可用不应导致无界快速重试。
- 所有网络客户端必须配置连接、读取和总调用超时，重试只用于明确可重试且具备幂等性的操作。
- 时间字段统一使用 UTC；资源限制字段延续现有契约：时间为 ns，内存为 bytes，并在字段名中写出单位。
- 日志必须包含 `traceId`，判题链路同时包含 `submissionId`、`taskId` 和 `attemptNo`；不得记录源码、Cookie、
  内部 JWT、密码、密码摘要或完整敏感请求体。

## 11. 当前明确不采用

- 单体业务后端作为最终架构。
- JPA / Hibernate、jOOQ、MyBatis-Plus。
- PostgreSQL；业务关系库统一为 MySQL 8.4 LTS。
- 数据库触发器、存储过程承载业务流程。
- 服务共享数据库、跨服务 JOIN、共享 Mapper。
- XA / 2PC 和对 MySQL + Kafka “恰好一次”的错误承诺。
- 在 Kafka 消息中传输源码或测试数据。
- 浏览器直接保存 JWT 或直接访问内部微服务。
- 第三方身份平台、外部 IdP、社交登录，以及 OIDC / OAuth 授权服务器式登录链路。
- 由 Gateway 自行读取用户表、校验密码或签发用户令牌；认证真源只在 user-service。
- 为了“以后可能用到”提前引入多个缓存、搜索或工作流引擎。

## 12. 尚未敲定的选型

以下能力需要，但具体产品尚未最终确认，初始化时不要擅自锁定：

- 生产环境编排与发布平台（Kubernetes 或其他方案）。
- 服务注册与集中配置方案；本地阶段可以使用 Compose 服务名和环境变量。
- 可观测性后端的具体组合，包括指标、日志、链路追踪和告警平台。
- API 文档生成工具和外部接口版本管理细节。
- 对象存储、题目搜索和长期归档方案。
- Redis 除 Gateway Session 之外的缓存范围与失效策略。

这些选型需要单独形成 ADR；在有真实需求和容量数据之前，不进入核心链路。
