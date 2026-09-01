# Java 编码规范（`apps/server`）

> 本文从 [`CLAUDE.md`](../../CLAUDE.md) 拆出，按需阅读；根目录只保留每次都必须遵守的部分。

五服务基础工程已经建立，业务实现继续遵循以下约定：

- Java 21 LTS + Spring Boot 4.1 + Maven 聚合工程；五个服务独立构建和部署，不与 Go 侧共享构建产物。
- 服务只写自己的 MySQL schema；禁止跨库 JOIN、共享 Mapper、共享业务实体和分布式 XA 事务。
- Gateway 使用 WebFlux；user/problem/submission/judging 使用 Spring MVC。MyBatis + Flyway 负责持久化。
- 浏览器公开 REST 契约以 `contracts/web-api.openapi.json` 为唯一真源。请求 body 使用 endpoint DTO，
  普通 JSON 成功响应统一为 `{ data, meta: { requestId, pagination? } }`，失败统一为 RFC 9457
  `application/problem+json`，并携带稳定 `code` 与相同的 `meta.requestId`。
- Gateway 为每次公开请求生成 `X-Request-Id`，且 header 必须与响应 body 一致；不能把客户端传入值、
  Session、Idempotency-Key 或内部 trace ID 当作 public request ID。`204`、二进制和流式响应是明确例外，
  不能为了形式统一强行包装。
- 正式提交使用 Kafka + Outbox/Inbox 异步推进；不能改回 HTTP 请求线程同步等待 judge。
- problem-service 拥有题目版本与 CORE 模板；judging-service 拥有环境、数据部署和语言标定；
  submission-service 拥有 Submission 与不可变 JudgeInput。
- CORE 模板在 submission-service 创建 JudgeInput 时合并；Go judge 收到的始终是完整源码。
- Kafka 不传源码、模板、测试数据、密码或 JWT；judging-service 通过内部 API 拉取 JudgeInput。
- 服务间 DTO 分别照着 `problem-judge-snapshot`、`execution-profile`、`judge-input`、`judge-events`
  schema 写；与 Go judge 之间的 DTO 照着 `judge.schema.json` 写。字段名、单位、可选性都以 schema
  为准，并用 schema 示例做契约对齐测试。
- Go judge 的 `environmentFingerprint` 来自部署配置并出现在所有结果中；不能从请求回显。
- 时间 ns、内存 bytes，字段名自带单位——不要在 Java 侧改成 `timeoutMs` 之类。
- 「结果入不入库」是 submission-service 的决定，不要试图让 judge 关心。
- 题目元信息真源在 problem-service；环境相关绝对限制真源在 judging-service；判题时解析并冻结到
  JudgeInput。**判题机磁盘上只有按 testDataVersionId 定位的测试数据文件本身**。
