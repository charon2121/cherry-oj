---
id: "EXPERIENCE-003"
type: "experience"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["CAPABILITY-002"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# EXPERIENCE-003：建立统一的 Web REST 交换协议与请求基建

## 体验类型

以跨前后端开发体验与故障定位体验为主，同时规定最终用户可见错误的安全、可恢复和无障碍基线。

## 入口与主流程

新增一个业务 API 时，开发者按固定顺序工作：先补 OpenAPI path/schema/examples，再生成或校验类型，
实现 Gateway BFF，最后在 Web feature 中声明 Query/Mutation。请求层自动注入公共 header、Cookie、
CSRF、request ID 处理和 JSON 解析；feature 只关心业务 input/data 和已分类 ApiError。

正常运行时，Web 发出 endpoint DTO，Gateway 校验并调用拥有服务，映射 public DTO 后返回
`data/meta`。Web 校验公共 envelope 与关键业务边界，把 data 交给 Query cache，把 request ID 留给
错误展示与支持定位。

## 异常状态

- 网络不可达：`kind=network`，保留页面已有数据并允许安全重试。
- 浏览器取消或 Query 取消：`kind=aborted`，通常不展示错误提示。
- 超时：`kind=timeout`，只在 endpoint 声明可重试时引导重试。
- 4xx/5xx Problem：`kind=http`，携带 status、code、requestId 和安全文案。
- 非 JSON、错误 content type、schema 不匹配：`kind=contract`，视为系统故障并显示 request ID。
- 401：清理/失效 session Query 并引导登录；403：保留登录态并显示无权限；两者不合并。
- 422：把 violations 映射到表单字段，无法定位的 violation 进入表单级错误。
- 429/503：读取 `Retry-After`，UI 不自行高频循环。
- 空集合是 success + `items: []`，页面进入 empty，不进入 error。

## 交互与文案

界面不直接展示后端堆栈或任意 detail。Problem 的 title/detail 只有在 contract 标记为 user-safe 时
才能显示；否则使用前端稳定文案并附“请求编号：requestId”。机器 `code` 不直接当中文文案。Mutation
失败保留用户输入；相同主动操作重试复用 Idempotency-Key，新操作生成新 key。

## 可访问性

错误摘要使用可感知的 alert/aria-live，字段 violation 通过 label、description 与 focus 关联；成功、
警告和失败不能只靠颜色。重试使用原生按钮并支持键盘，request ID 可选择复制但不抢夺焦点。

## 调试与恢复

浏览器 Network、响应 `X-Request-Id`、body `meta.requestId` 与 Gateway 日志必须一致。开发者先按
`network/http/contract` 分类，再用 request ID 查 Gateway 和下游 trace。OpenAPI example 可作为 curl
样本；生成物漂移由 CI 直接指出。迁移失败时可按 endpoint 回退，不改变数据库或 Kafka 数据。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：开发、错误恢复和可访问体验已形成草案，等待人工审阅
- 2026-08-25：状态变更：review → approved。原因：人工确认开发、错误恢复与安全展示体验
