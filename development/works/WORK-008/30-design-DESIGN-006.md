---
id: "DESIGN-006"
type: "design"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "approved"
work: "WORK-008"
owners: ["codex/root"]
depends_on: ["CAPABILITY-001", "EXPERIENCE-002"]
related: ["DESIGN-007"]
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# DESIGN-006：建立 Web 到 Gateway 的 REST 基础连通模块

## 背景

依据 CAPABILITY-001，当前缺口不是完整业务 API，而是 Web 到 Gateway 的第一条可执行边界。现有
Gateway 已使用 WebFlux，Web 已具备 TanStack Query、MSW 和 `/api` 开发代理，可在不新增依赖的
前提下完成最小纵向切片。

## 目标与限制

目标是提供稳定只读资源、可复用请求函数、运行时校验、Query 接线和三态 UI。限制是不接数据库、
不调用其它服务、不设计业务 DTO、不修改 `contracts/`，也不把 Actuator 运维响应直接暴露给页面。

## 整体方案

Gateway 中增加 `SystemStatusController` 和不可变响应 record，处理 `GET /api/status`。Web 的
`lib/api` 提供只返回 `unknown` 的 JSON GET 函数和带 HTTP 状态的错误；`features/system-status`
负责把 unknown 校验为领域类型、声明 Query key/options 并渲染状态面板；首页只负责组合 feature。

## 模块与数据

依赖方向保持 `routes → features → lib/components`。共享请求层不知道 system status；feature 不依赖
app 或 route。后端响应不持久化、不读取配置和其它服务，因此没有数据所有权变化。

## 接口与状态

`GET /api/status` 返回 200、`application/json` 和固定对象
`{"service":"gateway-service","status":"ready"}`。前端 decoder 要求值为普通对象、服务名为
非空字符串、状态严格等于 `ready`。Query key 固定为 `['system-status']`，请求函数接收 Query
传入的 `AbortSignal`。

## 安全与失败

响应不包含主机、环境变量、版本或下游依赖，匿名可读且只读。共享客户端对非 2xx 优先读取
Problem Details 的 title/detail；无法解析时保留 HTTP status 与通用信息。成功响应无法解析或校验
失败也视为接口错误，避免错误数据进入组件。

## 监控与部署

本工作不把端点替代 Actuator，也不新增生产监控。自动测试覆盖接口和 UI；本地联调用实际 Gateway
加 `curl`。开发使用 Vite proxy，生产要求 `/api` 与静态站同源。

## 迁移与兼容

无数据或配置迁移。新增 URI 不影响现有 `/actuator`。响应作为公开边界后需兼容演进；破坏性修改前
必须建立新工作项并同步后端测试与前端 decoder。

## 备选方案

备选一是前端直接调用 `/actuator/health`，但会把运维接口及其可变结构耦合到产品 Web。备选二是在
problem-service 建立硬编码题目接口并由 Gateway 转发，但会绕过未确认的 WORK-002 产品与数据设计。
因此选择由 Gateway 拥有最小 browser-facing 状态资源。

## 风险与重审条件

最大风险是使用者把连通状态理解为全系统健康，使用明确命名与文案限制语义。若 Gateway 开始需要
聚合下游 readiness、API 版本协商或 OpenAPI 生成，应重新设计资源而不是继续扩展固定 record。

## 适用范围修订

人工复核确认本设计只能证明 `/api/status` 的局部连通，不能充当未来业务请求基建。DESIGN-007 已经
获批并实现系统级 wire protocol、错误模型、分页、幂等、契约生成与兼容策略，旧裸 DTO 与 GET helper
也已迁移。本文件只保留 WORK-008 的历史纵向切片依据，任何新 browser-facing API 必须使用 DESIGN-007。

工作项工具要求已经被 TASK-008 依赖的 approved 上游文档保持可满足状态，因此本文件不强制改为
`superseded`；这不表示两套协议并存，长期技术真源只有 DESIGN-007 与
`contracts/web-api.openapi.json`。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：模块边界、接口形状与失败策略已完成技术复核
