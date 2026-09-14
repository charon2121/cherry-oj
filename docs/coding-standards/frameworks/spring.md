# Spring 编码规范（`apps/server`）

> 框架层规范。语言层约定见 [`languages/java.md`](../languages/java.md)，本项目跨语言约定见
> [`project-conventions.md`](../project-conventions.md)，服务边界与数据所有权见
> [`architecture.md`](../../architecture.md)。

## 1. 模块基线

具体版本号与选型理由见 [`backend.md`](../../backend.md) §2.1，这里只写写代码时要守的规矩：

- **Spring MVC 用于 user / problem / submission / judging；WebFlux 只用于 gateway-service。**
  不要把响应式编程扩散到普通业务服务——混用两套并发模型调试成本翻倍，而这些服务并没有需要
  响应式的吞吐压力。
- **版本放父 POM / BOM，子服务不单独声明 Spring 组件版本。** 稳定正式版优先，主链路不用
  alpha / beta / RC 依赖。
- **Jakarta Validation 只做 HTTP DTO 的结构校验**；业务不变量仍由应用层显式检查——别指望注解
  能表达「这个版本必须已发布」这类规则。
- 每个服务可独立构建、测试和出镜像，**业务代码和 Mapper 不做跨服务源码共享**。

## 2. HTTP 接口

- `contracts/web-api.openapi.json` 是浏览器 ↔ Gateway 的唯一真源。内部服务 DTO 不直接暴露，
  也不套用浏览器 envelope。
- 请求 body 由 endpoint schema 直接描述，**不要为了复用造一个万能请求对象**。
- 普通 JSON 成功响应统一为 `{ data: T, meta: { requestId, pagination? } }`；无 body 的成功用 204。
- 失败统一为 RFC 9457 `application/problem+json` + 正确的 4xx/5xx，扩展稳定 `code`、
  `meta.requestId` 和可选 `violations`。Bean Validation 失败是 **422**，请求解析不了是 **400**，
  未知 5xx 只返回安全摘要。
- `204`、二进制和流式响应是明确例外，不要为了形式统一强行包装。
- **Gateway 忽略客户端传入的 request ID**，每次公开请求自己生成 opaque `X-Request-Id`；响应
  header、成功 `meta` 和 Problem `meta` 三处必须一致。request ID、内部 trace ID、Session 和
  Idempotency-Key 各自独立，不能互相顶替。
- 响应 schema 新增可选字段属于兼容演进；破坏性修改必须给迁移窗口或开 `/api/v2`。
- **HTTP 状态码描述协议交互，verdict 描述用户程序**：AC、WA、TLE 都不是 HTTP 错误。

## 3. 数据访问与 SQL

组件清单与「为什么是 MyBatis 而不是 JPA / jOOQ」见 [`backend.md`](../../backend.md) §5.1。
落到写法上：**不建通用 Repository 抽象**——选 MyBatis 的目的就是让 SQL 和性能边界保持明确，
再叠一层 DSL 或 ORM 心智模型就白选了。

- 表名、列名、索引名用 `lower_snake_case`，表名用**单数**；Java 字段用 `camelCase`。
- SQL 关键字大写，主要子句换行，复杂条件按逻辑层级缩进。
- `SELECT` 必须显式列出列，**禁止 `SELECT *`**；写操作必须显式列出列名，不依赖数据库列顺序。
- 参数一律用 `#{}` 绑定；除受控的固定枚举映射外不用 `${}` 拼接外部输入。
- Mapper 方法只表达一个清晰的数据操作，不在 XML 里堆业务分支。
- 分页和批处理必须有稳定排序；排序字段可能同值时追加主键兜底，否则翻页会漏记录或重复。
- 高频查询由真实查询条件反推联合索引并遵守最左前缀，**不为每列机械建单列索引**。
- 不用存储过程和触发器承载业务；不用 `ddl-auto`，所有 DDL 走 Flyway。
- **跨服务禁止 JOIN**；同库内为清晰查询可以 JOIN，避免人为制造 N+1。
- 事务只覆盖本服务数据，跨服务一致性靠事件和补偿，不用 XA。

## 4. 配置与运行约束

- 各服务用自己的 `application.yaml`，敏感信息只从环境变量或部署 Secret 注入。
- 健康检查至少区分存活与就绪；依赖暂时不可用不能导致无界快速重试。
- **所有网络客户端必须配置连接、读取和总调用超时**；重试只用于明确可重试且幂等的操作。
- 时间字段统一 UTC；资源限制沿用跨语言契约——时间 ns、内存 bytes，单位写进字段名，
  **不要在 Java 侧改成 `timeoutMs` 之类**。
- 日志必须带 `traceId`，判题链路另带 `submissionId`、`taskId`、`attemptNo`；
  **不得记录源码、Cookie、内部 JWT、密码、密码摘要或完整敏感请求体**。字段规则见
  [`logging.md`](../../logging.md)。
- 内部 Trace 只用 W3C `traceparent` / `tracestate`，baseline 禁用 baggage。public `X-Request-Id`
  可以在同步内部 HTTP 上传播，但**不写入 Kafka 或数据库**，也不承担 trace、身份、幂等或业务主键语义。

## 5. 测试

- 单元与组件测试使用 Spring Boot Test 的 JUnit 栈。
- **Mapper、Flyway migration、事务、锁和索引行为使用 Testcontainers + MySQL 8.4，不用 H2 模拟**——
  H2 的方言差异会让「测试全绿但线上报错」成为常态。
- Kafka 的序列化、Inbox 去重、重复投递和消费提交边界必须有集成测试。
- 服务间 HTTP 在单元测试里用假客户端；跨服务关键路径在集成测试中启动真实依赖。
- JSON Schema 示例必须能被 Java DTO 正确反序列化，并断言字段单位与可选语义。
- 关键故障必须可重复测试：Relay 重发、Kafka 重投、Worker 租约过期、旧 token 迟到、Judge 超时、毒消息。

通用测试观念（测设计意图、两个方向都断言、假替身优先）见
[`project-conventions.md`](../project-conventions.md) §1.9。
