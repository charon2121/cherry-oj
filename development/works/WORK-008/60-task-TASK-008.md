---
id: "TASK-008"
type: "task"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "done"
work: "WORK-008"
owners: ["codex/root"]
depends_on: ["CAPABILITY-001", "DESIGN-006", "PLAN-006"]
related: []
implements: ["CAPABILITY-001"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/frontend.md", "apps/server", "apps/web", "development/works/WORK-008"]
write_paths: ["apps/server/gateway-service", "apps/server/TOOLCHAIN.md", "apps/web/src/lib/api", "apps/web/src/features/system-status", "apps/web/src/routes/index.tsx", "apps/web/e2e/smoke.spec.ts", "development/works/WORK-008"]
forbidden_paths: ["contracts", "apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "development/works/WORK-002"]
created_at: "2026-08-25"
updated_at: "2026-08-25"
---




# TASK-008：建立 Web 到 Gateway 的 REST 基础连通模块

## 任务目标

实现 Gateway 状态资源、Web 通用 JSON GET 边界和首页 Query 状态面板，形成第一条可测试的 REST
纵向链路。

## 依据

实现 CAPABILITY-001 的 REQ-001 至 REQ-005，具体模块边界与失败语义采用 DESIGN-006。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

Gateway controller/response/test，Web API client、system-status feature、首页接线、MSW 组件测试与
smoke 更新，以及 server 工具链和 VERIFY-008 的实际证据。

## 完成标准

- [x] `GET /api/status` 的 status、媒体类型和 JSON 字段通过接口测试。
- [x] 前端不会把未校验 JSON 直接传入 UI。
- [x] 页面明确覆盖 loading、error、success，错误可键盘触发重试。
- [x] Maven verify、Web check/build/E2E 和真实 curl 均通过。

## 验证

执行 `cd apps/server && ./mvnw clean verify`；`cd apps/web && npm run check && npm run build &&
npm run test:e2e`；启动 Gateway 后执行 `curl -i http://127.0.0.1:8080/api/status`；仓库根执行
`scripts/work check`。

## 风险

不得因联调方便修改其它业务服务或 WORK-002；若需要身份、业务 DTO、下游聚合或数据库，立即停止并
升级设计。公开响应字段变化必须先同步 CAPABILITY/DESIGN 和双侧测试。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-25：冻结 `/api/status` 最小契约和允许修改范围。
- 2026-08-25：状态变更：todo → ready。原因：上游文档已批准且读写边界与完成标准完整
- 2026-08-25：实现 Gateway controller、前端请求/校验/Query/UI 与双侧自动化测试。
- 2026-08-25：五服务 Maven、Web check/build/E2E、Gateway 直连和 Vite proxy 联调全部通过。
- 2026-08-25：状态变更：ready → doing。原因：按批准的 REST 契约开始实现 Gateway 与 Web 纵向链路
- 2026-08-25：状态变更：doing → done。原因：实现、自动化检查与真实 REST 联调均满足完成标准
