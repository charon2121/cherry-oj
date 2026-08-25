---
id: "VERIFY-008"
type: "verify"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "approved"
work: "WORK-008"
owners: ["codex/root"]
depends_on: ["TASK-008"]
related: []
implements: []
verifies: ["CAPABILITY-001", "TASK-008"]
tags: []
result: "pass"
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# VERIFY-008：建立 Web 到 Gateway 的 REST 基础连通模块

## 验证对象

TASK-008 交付的 Gateway 状态 API、Web 请求/校验/Query/UI 链路及其范围约束。

## 对应要求

验证 CAPABILITY-001 的 REQ-001 至 REQ-005，以及 TASK-008 的四项完成标准。

## 检查与结果

验证日期为 2026-08-25。本地构建使用 OpenJDK 21.0.12.1、Node 26.3.0、npm 12.0.2；沙箱外启动
打包产物时系统 Java 为 25.0.4.1，JAR 仍按 Maven 的 Java 21 release 编译。

- `cd apps/server && ./mvnw clean verify`：通过，Reactor 6/6 成功；Gateway 2 个测试、其余四服务
  各 1 个上下文测试，合计 6 个测试，无失败、错误或跳过。
- `cd apps/web && npm run check`：通过；Prettier、ESLint、TypeScript 与 Vitest 全绿，Vitest
  2 个测试文件共 4 个测试通过。
- `cd apps/web && npm run build`：通过；Vite 生产构建完成，1974 个模块转换成功。
- `cd apps/web && npm run test:e2e`：通过；Chromium smoke 1/1，验证应用壳与 REST 成功态。
- 启动打包后的 Gateway 并请求 `http://127.0.0.1:8080/api/status`：返回 `HTTP/1.1 200 OK`、
  `Content-Type: application/json` 和 `{"service":"gateway-service","status":"ready"}`。
- 同时启动 Gateway 与 Vite，向 `http://127.0.0.1:5173/api/status` 发请求：经 Vite proxy 返回同一
  200 JSON，证明 Web 开发入口到 Gateway 的实际 REST 链路可用；验证后两个进程均已优雅停止。
- `git diff --check`：通过，无空白错误。

最初使用 `RANDOM_PORT` 的后端测试在受限沙箱中因 `SocketException: Operation not permitted` 无法
绑定端口。按仓库“单测优先假替身”的约定改为 `WebTestClient.bindToController` 无端口测试；真实
端口、打包产物和 HTTP 媒体类型另由上述沙箱外 curl 验证，因此没有用降低断言规避环境限制。

## 未通过项

暂无。过程中仅有已解决的沙箱端口限制，不是代码或产品失败。

## 范围检查

`git status` 与 diff 显示改动仅位于 TASK-008 允许的 Gateway、server 工具链、Web API/feature/首页/
smoke 与 WORK-008 文档；`development/index.json` 是创建工作项时由工具维护的永久编号索引。未修改
`contracts/`、四个业务服务、judge-engine 或被阻塞的 WORK-002，没有实现偏差。

## 遗留问题

仓库实现无遗留失败；生产环境的同源路由、发布和观察属于后续部署阶段。

## 剩余风险

端点只证明 Gateway 可响应，不代表业务服务与基础设施整体健康；页面和文档已明确该语义。生产
同源 `/api` 路由尚未部署验证，本任务不代签上线与线上可靠性观察。

## 后续适用性

WORK-009 已将本验证中的裸 status DTO 和临时 GET helper 迁移到统一 ApiSuccess / ApiProblem 协议。
因此本文件保留的有效证据是“Web、Vite proxy 与 Gateway 的纵向连通曾经通过”；旧 JSON shape、测试
数量和临时 helper 不再描述当前实现。当前协议与最新验证以 DESIGN-007、VERIFY-009 和
`contracts/web-api.openapi.json` 为准。

## 结论

CAPABILITY-001 的 REQ-001 至 REQ-005 与 TASK-008 完成标准全部通过，可以确认仓库实现与本地纵向
链路已验证；release 与 observe 阶段仍待真实部署后推进。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：五服务构建、Web 全量检查、浏览器测试及双进程代理联调全部通过
