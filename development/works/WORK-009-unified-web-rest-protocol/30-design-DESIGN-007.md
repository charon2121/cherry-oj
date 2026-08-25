---
id: "DESIGN-007"
type: "design"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["CAPABILITY-002", "EXPERIENCE-003"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# DESIGN-007：建立统一的 Web REST 交换协议与请求基建

## 背景

CAPABILITY-002 要求的不是某个 endpoint DTO，而是浏览器公开 API 的稳定协议。WORK-008 的
`{"service":"gateway-service","status":"ready"}` 与 `getJson()` 只证明网络连通，没有说明未来
成功、失败、分页、幂等、鉴权和演进怎样组合，因此不能继续扩展为公共层。

本设计遵循 [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html) 的状态与媒体类型
语义、[RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html) 的标准错误模型，并以
[OpenAPI 3.1](https://spec.openapis.org/oas/) 表达 Gateway browser-facing 契约。

## 目标与限制

目标是让所有普通 Web JSON endpoint 共用一套 wire format、错误分类、request ID、分页、幂等、契约
生成和测试方法，同时保持业务 DTO 强类型。限制是协议仅作用于浏览器 ↔ Gateway；内部服务、Kafka、
judge/sandbox 和二进制/流式数据不能为了“统一”被错误包装。

## 整体方案

协议分为四层：

1. HTTP 层：method、URI、status、Content-Type、Location、Retry-After、Idempotency-Key 和缓存语义。
2. 公共 wire 层：成功 `ApiSuccess<T>`、失败 `ApiProblem`、`ApiMeta`、pagination 与 violation。
3. endpoint 层：每个业务 request/data/error code 的 OpenAPI schema。
4. 实现层：Gateway public adapter 与 Web client/parser；Query、表单、路由和业务服务不进入公共层。

统一意味着每层只有一个判据，不意味着所有 HTTP 消息必须长成同一个 JSON object。

## 模块与数据

- `contracts/web-api.openapi.json`：拟新增的 OpenAPI 3.1.x 唯一真源，包含公共 components、paths、
  headers、examples 和 error code 说明；具体 patch 版本在实施前用生成器兼容性测试冻结。
- `gateway-service`：拥有 browser-facing adapter、request context、成功 meta、Problem mapper 和下游
  错误脱敏；业务事实仍由拥有服务决定。
- `apps/web/src/lib/api`：只处理 HTTP/wire，不 import feature/app/routes。
- `apps/web/src/generated/api`：由 OpenAPI 生成、禁止手改；feature 只 import 所需类型。
- `features/*/api`：声明 Query key、调用公共 client、执行 endpoint 关键运行时校验。

不新增数据库或持久化格式。Gateway 不保存业务 data，也不把内部 DTO 直接作为 public DTO 返回。

## 接口与状态

### 请求格式

请求 body 不套 envelope。例如创建资源：

```http
POST /api/problems HTTP/1.1
Content-Type: application/json
Accept: application/json, application/problem+json
Idempotency-Key: 7b5c...
X-CSRF-Token: ...

{"title":"A+B","codeMode":"ACM"}
```

这使 endpoint schema、Bean Validation 和 OpenAPI 直接描述真实 input；`data` wrapper 不提供额外语义。

### 单资源成功

```json
{
  "data": {
    "id": "019c...",
    "title": "A+B"
  },
  "meta": {
    "requestId": "req_01k..."
  }
}
```

### 集合与 cursor 分页

```json
{
  "data": {
    "items": []
  },
  "meta": {
    "requestId": "req_01k...",
    "pagination": {
      "kind": "cursor",
      "nextCursor": null,
      "hasMore": false
    }
  }
}
```

支持跳页的管理列表可使用 `kind: "page"`，并携带从 1 开始的 `page`、`size`、`totalElements` 与
`totalPages`。两种结构是封闭可辨识联合；cursor 是 opaque string。

### 失败

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json
X-Request-Id: req_01k...
```

```json
{
  "type": "urn:cherry-oj:problem:validation-failed",
  "title": "请求参数校验失败",
  "status": 422,
  "detail": "请检查标记字段。",
  "instance": "urn:cherry-oj:request:req_01k...",
  "code": "VALIDATION_FAILED",
  "meta": {
    "requestId": "req_01k..."
  },
  "violations": [
    {
      "path": "title",
      "code": "SIZE",
      "message": "标题长度必须在 1 到 120 之间。"
    }
  ]
}
```

`type` 与 `code` 稳定；前端分支使用 status/code，不能匹配 title/detail。`violations` 不包含用户提交的
实际值。未知 code 按 HTTP status class 安全降级。

### HTTP 映射

- 200：成功读取或有响应 data 的幂等操作。
- 201 + `Location`：同步创建完成。
- 202 + `Location`：已持久化并异步推进，例如正式 Submission。
- 204：成功且无 body，是 JSON envelope 的明确例外。
- 400：JSON/参数语法或协议格式错误；401：未认证；403：无权限；404：资源不存在。
- 409：资源状态冲突或 Idempotency-Key 与不同请求冲突；412：明确的条件请求失败。
- 413/415：body 太大或媒体类型不支持；422：结构可解析但字段/业务输入校验失败。
- 429：限流并尽量提供 `Retry-After`；500：未分类服务端故障；502/503/504：Gateway/下游故障。

AC、WA、CE、TLE 等 verdict 永远是成功 data 中的业务事实，不映射为 4xx/5xx。

## 安全与失败

Gateway 生成 public request ID，并将内部 trace 与之关联；不信任任意长或含控制字符的来访 ID。
request ID、Idempotency-Key、Session/CSRF 和 trace ID 是四种不同语义，禁止互换。

Gateway 对自身错误、下游超时、连接失败、非 JSON 和非法下游响应分别映射安全 Problem。4xx 只在
明确允许时保留 domain code；5xx 的 detail 不透传。日志不记录密码、Cookie、token、源码、隐藏数据、
完整请求/响应体或 violation rejected value。Problem 的 `instance` 使用 occurrence URN，不复制可能
含敏感 query 的原始 URL。

前端错误模型至少区分 `http | network | timeout | aborted | contract`。公共 client 不把非 HTTP 故障
伪造成 status=500，也不自动导航、toast 或重试。Query/Mutation 按 method、status、code 与
Idempotency-Key 决定恢复策略。

## 监控与部署

响应头与 JSON 的 request ID 必须一致，Gateway 日志、指标和下游 trace 可按它关联。观测至少区分
status class、problem code、endpoint、延迟与 contract error；不得将 userId、源码或任意 URL query
作为高基数指标标签。部署前以真实 Vite → Gateway → fake downstream 做跨模块回归。

## 迁移与兼容

公共响应 decoder 校验必需字段并允许未知 optional 字段，以支持 additive evolution；请求 schema
默认拒绝未知字段，尽早暴露拼写与版本错误。新增 optional 响应字段和新 problem code 为兼容变化，
客户端必须有 unknown fallback。删除、改名、改类型、收紧 enum、改变 nullability/status/code 语义为
破坏性变化。

推荐初期保持 `/api`，在 OpenAPI `info.version` 记录契约版本；只有发生无法双写/兼容迁移的破坏性变化
才新增 `/api/v2`。WORK-008 endpoint 尚未发布，获批后直接迁移，不承担旧 wire 兼容。

## 备选方案

DECISION-006 比较三种方案：推荐的成功 envelope + RFC Problem、所有响应统一
`code/message/data`、以及成功返回裸 DTO + RFC Problem。当前内容只是提案，人工确认前不得实现。

## 风险与重审条件

协议会约束所有未来公开 API，过早加入字段比缺字段更难撤销。因此公共 meta 只保留 requestId 与确有
跨 endpoint 价值的 pagination，不加入 timestamp、server、trace、locale 或 arbitrary extensions。
如果未来引入公开第三方 API、多区域网关、GraphQL、SSE 大规模推送或独立 API 管理平台，应重新审查
版本、媒体类型、错误文档 URI 与生成链路。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：wire format、模块边界、安全和兼容方案已形成草案，等待人工审阅
- 2026-08-25：状态变更：review → approved。原因：人工确认成功 envelope、RFC 9457、Gateway 边界与兼容设计
