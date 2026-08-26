# 日志与执行链路规范

Java 五个服务、Go judge 和 Go sandbox 共享本规范。目标是让同一条请求在不同运行时中仍可用同一个
Trace ID 查询，同时让日志可以直接被 JSON 日志采集器读取；日志平台、指标、告警和 Trace 导出后端
不属于当前能力。

## 统一 JSON 字段

每条日志独占一行，且必须是一个完整 JSON object。稳定字段如下：

| 字段 | 必需性 | 含义 |
|---|---|---|
| `@timestamp` | 必需 | RFC 3339 时间；允许 `Z` 或明确时区偏移 |
| `level` | 必需 | `DEBUG`、`INFO`、`WARN` 或 `ERROR` |
| `message` | 必需 | 简短、可读的事件说明，不拼接源码或请求正文 |
| `service` | 必需 | 固定部署服务名，例如 `submission-service`、`judge`、`sandbox` |
| `trace_id` | 活动 Trace 时必需 | 32 位小写十六进制 W3C Trace ID |
| `span_id` | 活动 Trace 时必需 | 当前服务边界的 16 位小写十六进制 Span ID |
| `request_id` | 有同步请求 ID 时必需 | Gateway 生成的 `req_...`；不承担 Trace 或幂等语义 |
| `event` | 边界/领域事件时必需 | 稳定事件名，例如 `http.server.completed` |

Java 运行时还会提供 `logger`、`thread` 和受限的 `stack_trace`；Go 可按事件增加类型明确的结构化字段。
HTTP 完成事件统一使用 `http_method`、`http_route`（路由模板而非原始 URL）、`http_status` 和
`duration_ms`。业务 ID 使用独立字段，禁止塞进 `message` 后再靠文本切割。

日志不得包含源码、stdin/stdout/stderr 正文、测试数据、标准答案、Cookie、Authorization/JWT、密码、
完整请求/响应 body、命令环境变量或未限制长度的外部字符串。新增字段应显式选择，不得反射序列化整个
DTO、异常对象或请求对象。

## Trace 与跨服务传播

- HTTP 只使用 W3C `traceparent` 和 `tracestate`；不传播 `baggage`，Trace 字段不进入 JudgeRequest、
  RunSpec 或业务响应 body。
- Gateway 是公开信任边界：忽略浏览器传入的 Trace header，生成新的内部 root；public
  `X-Request-Id` 仍由 Gateway 覆盖生成，并放入下游同步请求。
- Java MVC/WebFlux 入站请求自动建立或继续 Trace。使用 Spring 注入的 `RestClient.Builder`、
  `WebClient.Builder` 或 Gateway route 发起出站请求时，框架自动注入 W3C header；不要手工 new 一个
  未受 Spring 管理的客户端后期待它自动传播。
- Go judge/sandbox 的 HTTP middleware 建立或继续 Trace；judge 的 sandbox client 通过统一 Transport
  自动透传 `traceparent`、`tracestate` 和合法的 `X-Request-Id`，并主动删除 baggage。
- Kafka 业务链尚未实现。后续 producer/consumer 必须把 `traceparent`/`tracestate` 放入 Kafka header，
  开启 Spring Kafka Observation；event envelope 的 `traceId` 只保存当前 32-hex 查询副本，不能代替
  header 构造 parent。

日志只保证按 Trace ID 关联执行链，不在当前应用内向远端推送完整 Trace。采集、存储、查询和留存平台
可在部署层独立选择，不能成为业务健康检查或请求成功的前置条件。

## 文件位置与按日拆分

Java 默认同时输出 console 和 `./logs/<service>.log`。环境变量 `CHERRY_LOG_PATH` 可覆盖目录；午夜按
UTC 滚动为 `<service>.log.YYYY-MM-DD.<index>.gz`，单文件上限 10 MB、保留 30 天。日志级别继续使用
Spring 标准配置，例如 `LOGGING_LEVEL_ROOT=DEBUG`。

Go 默认同时输出 stdout 和 `./logs/<service>.YYYY-MM-DD.log`，在每次写入时按 UTC 日期切换文件。
YAML 配置为：

```yaml
logging:
  path: ./logs
  level: INFO
```

环境变量分别是 `CHERRY_OJ_LOGGING_PATH` 与 `CHERRY_OJ_LOGGING_LEVEL`。日志目录不可创建或文件不可写时
进程拒绝启动，避免服务看似正常但日志已经丢失。Compose 将两个 Go 服务的目录配置为
`/var/log/cherry-oj`，并挂载持久化的 `engine-logs` volume；stdout 仍可由容器运行时采集。
