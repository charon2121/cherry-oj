---
id: "CAPABILITY-002"
type: "capability"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# CAPABILITY-002：建立统一的 Web REST 交换协议与请求基建

## 为什么需要

未来的登录、题库、提交和管理 API 需要共享同一组 HTTP/JSON 规则。公共基建必须让开发者不用在每个
feature 重新决定成功结构、错误结构、状态码、request ID、分页、幂等、取消和契约校验，同时不能
牺牲 REST/HTTP 语义、类型安全或敏感信息边界。

## 使用者

- Web 开发者：通过一个类型安全的请求入口获得业务 data、meta 或结构化 ApiError。
- Gateway 开发者：用统一成功构造器、请求上下文和异常映射暴露 browser-facing API。
- 业务服务开发者：继续维护本服务内部/领域 DTO，由 Gateway BFF 映射，不依赖前端实现细节。
- 测试与运维人员：使用 request ID、稳定错误码和 OpenAPI 示例复现、定位与对账。

## 能力

- REQ-001（协议范围）：统一协议只约束“浏览器 ↔ Gateway”的普通 REST/JSON 边界；服务间 HTTP、
  Kafka、judge/sandbox、文件和流式协议继续使用各自契约。
- REQ-002（请求）：JSON 请求体直接使用资源或命令 DTO，不增加通用 `data`/`payload` wrapper；所有
  body 必须是 object，使用 `application/json`，并由 endpoint schema 校验。
- REQ-003（成功）：有 JSON body 的 2xx 响应统一为 `ApiSuccess<T> = { data: T, meta: ApiMeta }`；
  `meta.requestId` 必填，业务 DTO 不重复 success、code、message 或 requestId。
- REQ-004（集合）：集合成功响应的 `data` 为 `{ items: T[] }`，分页信息只放
  `meta.pagination`；空集合返回 `items: []`，不是 404、null 或缺失字段。
- REQ-005（失败）：所有 Gateway 可控的 4xx/5xx JSON 失败使用 RFC 9457
  `application/problem+json`，并扩展稳定 `code`、`meta.requestId` 和可选 `violations`。
- REQ-006（单一判据）：HTTP status 是成功/失败和通用重试语义的唯一真源；不得用 HTTP 200 包装
  业务失败，也不得让 body 字段与 HTTP status 表达相反结论。
- REQ-007（关联）：Gateway 为每个请求生成或规范化不可猜测的 request ID，同时写入
  `X-Request-Id` 响应头和 JSON `meta.requestId`；request ID 只用于定位，不承担幂等或身份语义。
- REQ-008（JSON 约定）：字段名 camelCase；ID 为字符串；时间为 UTC RFC 3339；枚举为字符串；单位
  写入字段名；optional 默认省略，只有领域明确存在“空值”时才发送 null。
- REQ-009（数字安全）：可能超过 JavaScript 安全整数范围的值必须使用十进制字符串；其它整数在
  OpenAPI 中声明上下界，不能把 Java long 无条件作为 JSON number 暴露。
- REQ-010（分页）：普通前台列表优先 opaque cursor；确需跳页的后台列表可用受限 offset/page。
  两种 pagination 使用 `kind` 区分，不允许客户端解析 cursor 内部结构。
- REQ-011（幂等）：需要安全重试的创建/命令端点显式要求 `Idempotency-Key`；同一 key + 同一规范化
  请求返回同一结果，同一 key + 不同请求返回 409，不能把 request ID 当幂等键。
- REQ-012（鉴权）：401 表示缺少/失效会话，403 表示身份有效但无权限；Cookie 写请求另行携带
  CSRF token，前端不能因统一错误层而合并这三类安全语义。
- REQ-013（前端）：公共 client 统一处理 base URL、Accept/Content-Type、Cookie、CSRF、
  AbortSignal、204、JSON 解析、Problem Details、request ID 和网络/超时/契约错误；它不做缓存、
  自动业务重试、路由跳转或 toast。
- REQ-014（后端）：Gateway 统一生成 request context、成功 meta、Problem Details 和基础设施错误；
  业务 BFF mapper 负责 domain DTO → public DTO，禁止透明泄漏内部响应或重复包 envelope。
- REQ-015（契约真源）：browser-facing OpenAPI 3.1.x 文档进入 `contracts/`，公共 schema、path、状态码、
  header 和 example 以它为唯一真源；TypeScript 类型由它生成，Java 用契约测试对齐。
- REQ-016（兼容）：新增 optional 响应字段属于兼容变更；删除/改名/改类型/收紧枚举或改变 status
  语义属于破坏性变更，必须新版本或迁移期。公共 decoder 默认容忍未知响应字段。
- REQ-017（例外）：204、HEAD、下载/上传、SSE/流式响应不强制 JSON envelope，但仍使用 HTTP status、
  request ID、鉴权和错误协议；例外必须写入 endpoint OpenAPI，不能由实现临时决定。
- REQ-018（安全）：任何失败响应不得包含堆栈、类名、SQL、内部主机、token、Cookie、密码、源码、
  隐藏测试输入/答案或未经批准的 rejected value；5xx detail 使用安全通用文案。
- REQ-019（验证）：CI 必须检查 OpenAPI 可解析、示例符合 schema、生成物无漂移、Java/TS 关键类型
  对齐，以及成功/失败/request ID/兼容/脱敏的跨模块契约测试。

## 接入方式

业务 feature 先在 Gateway OpenAPI 中定义 endpoint request、success data 和可能的 problem code，再
实现 Gateway BFF 与前端 Query/Mutation。Web 只通过项目级 client 调用相对 `/api` URI；Gateway
只返回协议允许的 public DTO。未经契约评审的裸 `fetch`、裸 `Map`、裸下游响应均不进入业务代码。

## 输入与输出

请求由 method、URI、headers、query/path parameters 和 endpoint body 共同构成，不人为压缩成一个
JSON wrapper。成功与失败结构、分页形态、status/header 映射和 JSON 示例详见 DESIGN-007。

## 限制与失败

公共 client 只能规范 Gateway 实际返回的 HTTP 响应，无法把 DNS、离线、浏览器取消、代理 HTML 错误
自动解释为业务 Problem；这些情况必须保留 `network | aborted | timeout | contract` 分类。未知 problem
code 按其 HTTP status class 安全降级，不因客户端未升级而崩溃。

## 质量要求

公共 envelope 不复制业务数据，不添加每次变化但无消费价值的 timestamp。Gateway 不为包 envelope
进行无界 body buffering。所有请求都有 request ID；错误码是稳定机器契约，title/detail 是安全人类
文案而不是分支条件。新增可选字段不得破坏旧 Web。

## 升级与迁移

WORK-008 未发布的 `/api/status` 是首个迁移样本。协议获批后先建立契约与两侧基础层，再迁移 status
并删除旧 helper；之后新业务 endpoint 必须直接接入新协议。只有完成实际实现验证后，确认结论才同步
到全局 docs。

## 不做什么

本工作不实现登录、题目、提交或数据库；不统一服务间 DTO/Kafka/judge 契约；不选择 API 管理平台；
不让 client 自动弹提示、导航、缓存或重试；不预先定义所有未来 domain error code。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：统一协议能力、范围和非目标已形成草案，等待人工审阅
- 2026-08-25：状态变更：review → approved。原因：人工确认统一协议范围与十九项能力要求
