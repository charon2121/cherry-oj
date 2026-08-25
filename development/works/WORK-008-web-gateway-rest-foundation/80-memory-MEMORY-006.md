---
id: "MEMORY-006"
type: "memory"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "approved"
work: "WORK-008"
owners: ["codex/root"]
depends_on: ["VERIFY-008"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# MEMORY-006：建立 Web 到 Gateway 的 REST 基础连通模块

## 背景

Web 与 Gateway 的第一条 REST 链路刻意不依赖 WORK-002 的登录、题目和提交产品决策。

## 决定与原因

Gateway 拥有 `/api/status` browser-facing 单例资源；Actuator 继续服务运维。共享前端请求层只负责
HTTP/JSON，具体响应校验留在 feature，保持依赖方向和错误边界清晰。

## 尝试与教训

不能用前端静态 mock 声称端到端已连通，也不能为了演示在 problem-service 硬编码业务数据。后端
接口测试、前端 MSW 测试和实际 curl 分别证明契约、UI 状态和真实进程响应。

受限沙箱不允许测试进程绑定随机端口；Controller 契约用无端口 WebTestClient 保持单测稳定，打包
进程与 Vite proxy 的端口链路另在允许绑定 localhost 的验证环境执行。两类证据不能互相替代。

## 已知问题

该资源只表示 Gateway 可响应，不聚合四个业务服务或基础设施健康；生产同源路由仍需部署阶段确认。

## 重新考虑条件

引入 OpenAPI 生成、统一 Problem Details/CSRF、下游 readiness 聚合或 API 版本策略时，重新评估
请求层和 status 资源，不在当前固定响应上无限追加字段。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：已沉淀状态端点边界与沙箱端口验证经验
