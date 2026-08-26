---
id: "CAPABILITY-001"
type: "capability"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "approved"
work: "WORK-008"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# CAPABILITY-001：建立 Web 到 Gateway 的 REST 基础连通模块

## 为什么需要

现有前后端只有独立骨架，尚未证明浏览器到 Gateway 的 REST/JSON 边界可运行。需要先建立一条没有
数据库和业务前置条件的连通链路，作为后续业务 feature 复用的请求、校验和错误处理基线。

## 使用者

前端开发者使用统一请求函数接入 Gateway；后端开发者使用同一 URI 与响应约定验证公开 API；本地
联调人员通过首页或 `curl` 判断 Web 与 Gateway 是否已正确连接。

## 能力

- REQ-001：Gateway 提供只读 `GET /api/status`，成功时返回 JSON 对象
  `{"service":"gateway-service","status":"ready"}`。
- REQ-002：Web 提供统一 JSON GET 边界，默认携带同源 Cookie、声明 JSON Accept、传递
  `AbortSignal`，非 2xx 响应转换为包含 HTTP 状态的应用错误。
- REQ-003：Web 在运行时校验状态响应；字段缺失、类型错误或未知状态均按接口失败处理，不能只靠
  TypeScript 类型断言。
- REQ-004：首页通过 TanStack Query 管理请求，并分别呈现 loading、error、success；失败状态可由
  用户主动重试。
- REQ-005：后端接口契约与前端成功、失败路径必须有自动化测试，且两侧构建均通过。

## 接入方式

浏览器始终请求同源相对路径 `/api/status`。本地 Vite 开发服务器沿用已有代理转发至
`http://127.0.0.1:8080`；部署环境由 Gateway 或同源反向代理提供 `/api`。

## 输入与输出

请求无 path、query 或 body。成功响应为 UTF-8 JSON，只有 `service` 与 `status` 两个字符串字段；
当前 `status` 唯一合法值为 `ready`。该响应不包含时间、主机、版本、依赖拓扑或凭证，避免缓存抖动
和内部信息暴露。

## 限制与失败

端点只证明 Gateway 可以处理请求，不聚合其它服务、数据库、Kafka、judge 或 sandbox 的健康状态。
网络错误、非 2xx、非 JSON 或响应校验失败都进入前端 error 状态；重试是显式且幂等的 GET，不会
产生数据变更。

## 质量要求

响应不执行阻塞 I/O，测试固定字段和媒体类型。页面错误使用 `role=alert`，加载使用 `role=status`，
成功同时使用文字与视觉标记。请求可取消，组件卸载或查询失效时不遗留无意义请求。

## 升级与迁移

这是首个公开资源，无存量数据迁移。未来若要提供依赖级健康信息，应新增独立运维能力或扩展契约并
同步前端校验，不能悄悄改变 `ready` 的含义。

## 不做什么

不实现登录、题目、提交、数据库、OpenAPI 生成、统一 CSRF、服务聚合健康检查或生产监控；这些由
后续已确认的工作项分别交付。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：REST 基础能力的范围、契约和非目标已明确
