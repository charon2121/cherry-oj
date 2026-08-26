---
id: "DECISION-006"
type: "decision"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["DESIGN-007"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# DECISION-006：建立统一的 Web REST 交换协议与请求基建

## 要决定什么

确认浏览器 ↔ Gateway 的统一 wire format、错误模型、公开契约所有权和版本策略。这是 TASK-009 的
人工门禁；本文只能提出推荐，不能由 Agent 自行批准。

## 背景

WORK-008 用固定 status JSON 验证了连通，但用户复核认为它不是未来通用请求基建。全局文档已经确定
HTTP status、RFC 9457、Request ID、Idempotency-Key 与 Gateway BFF，只缺一份把这些要求组合成可
执行 wire protocol 的明确决定。

## 候选方案

### 方案 A：成功 `data/meta`，失败 RFC 9457（推荐）

- 请求 body 使用 endpoint DTO，不包 envelope。
- 2xx JSON 使用 `{data, meta}`；4xx/5xx 使用 `application/problem+json`。
- HTTP status 是成功/失败真源；Problem 扩展 `code`、`meta.requestId`、`violations`。
- 优点：遵循 HTTP/标准工具语义，成功结构统一，错误可机器处理，OpenAPI 表达自然。
- 代价：成功与失败不是完全相同的 JSON shape；前端 client 需要按 status/content-type 归一成内部联合。

### 方案 B：所有响应统一 `code/message/data`

示例：`{"code":"OK","message":"success","data":{...},"requestId":"..."}`，失败也返回同形结构。

- 若失败仍使用正确 4xx/5xx，可保留部分 HTTP 语义，但 code/status/message 形成重复真源。
- 若所有失败返回 200，代理、缓存、监控、浏览器、Query retry 和通用客户端都会误判，明确不可接受。
- `data: null`、成功 message、通用 code 对多数 endpoint 没有信息量；字段校验与标准 Problem 仍需另扩展。
- 优点是表面 shape 单一、部分团队熟悉；代价是长期漂移测试和标准工具兼容成本更高。

### 方案 C：成功裸 domain DTO，失败 RFC 9457

- HTTP 最简洁，endpoint DTO 没有 envelope。
- 但 request ID、pagination 和未来通用元数据没有稳定位置，各列表容易自行发明结构。
- 适合很小的 API，不满足本项目未来多个业务域的统一元数据需求。

公开契约所有权另有两种实现：Gateway BFF 显式映射 public DTO，或透明代理业务服务的外部 DTO。
推荐前者，因为它能隐藏内部拓扑、集中脱敏和保持 browser contract 独立；代价是需要 BFF adapter。

## 决定

采用方案 A，并由 Gateway BFF 拥有 browser-facing OpenAPI 与 public DTO；内部服务继续使用自己的
领域/内部契约。初期 URI 保持 `/api`，兼容演进；真正破坏性变化才引入 `/api/v2`。

**状态：已于 2026-08-25 人工确认，可以按 PLAN-007 进入实现。**

## 理由

方案 A 在“统一公共字段”与“尊重 HTTP 已有协议”之间边界最清晰。前端仍可把两种 wire response
归一成 `success | http error | transport error` 类型，不需要服务端制造 `ok/code/status` 多重判据。
Gateway 显式 public adapter 符合现有 BFF 定位，也避免 user/problem/submission 的内部 DTO 被前端
锁死。选择不是为了减少几行 JSON，而是为了使监控、代理、生成器、测试和错误恢复共享同一语义。

## 影响与风险

一旦批准，它会成为所有 future Web API 的上游约束，需要更新全局 docs、contracts 测试、Gateway、
Web 工具链和 CI。主要风险是 envelope 泛化、Gateway 映射膨胀和版本过早冻结；通过最小 meta、BFF 只
做适配、OpenAPI contract test 和 WORK-008 单 endpoint 试迁移控制。

请人工审阅并逐项确认：

- [x] 采用方案 A，而不是方案 B/C。
- [x] 请求 body 不增加通用 wrapper。
- [x] `meta` 必含 requestId，pagination 可选，不加入 timestamp。
- [x] Problem 扩展 code/meta/violations，5xx detail 脱敏。
- [x] Gateway BFF 拥有公开 DTO，内部服务契约不套 Web envelope。
- [x] 初期使用 `/api` 兼容演进，破坏性变化才新增 `/api/v2`。
- [x] 二进制、流式、204 和内部协议按 DESIGN-007 作为显式例外。
- [x] 安全边界与权限影响已由负责人确认。

## 重新考虑条件

公开 API 开放给第三方、Gateway 不再承担 BFF、引入 GraphQL/大量 SSE、需要多语言/多租户错误文案，
或 OpenAPI 生成链无法可靠支持选定 schema 时重新决策。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：三种交换格式与推荐方案已列明，等待人工决策
- 2026-08-25：人工审阅确认采用推荐方案 A，并确认全部安全与兼容检查项。
- 2026-08-25：状态变更：review → approved。原因：人工选择推荐方案 A 并确认全部决策检查项
