---
id: "TASK-009"
type: "task"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "done"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["CAPABILITY-002", "DESIGN-007", "DECISION-006", "PLAN-007"]
related: []
implements: ["CAPABILITY-002"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts", "docs/architecture.md", "docs/backend.md", "docs/frontend.md", "apps/server/gateway-service", "apps/web", "scripts/contracts_test.py", "development/works/WORK-008", "development/works/WORK-009"]
write_paths: ["contracts", "scripts/contracts_test.py", "apps/server/gateway-service", "apps/web", ".github/workflows", "CLAUDE.md", "docs/architecture.md", "docs/backend.md", "docs/frontend.md", "development/works/WORK-008", "development/works/WORK-009"]
forbidden_paths: ["apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "docs/data-model.md", "docs/database-design.md", "development/works/WORK-002"]
created_at: "2026-08-25"
updated_at: "2026-08-25"
---




# TASK-009：建立统一的 Web REST 交换协议与请求基建

## 任务目标

在人工批准 DECISION-006 后，实现 CAPABILITY-002 的公共契约、Gateway Web 响应/错误基建与 Web
请求/parser 基建，并迁移 WORK-008 status endpoint 作为第一个真实消费者。

## 依据

只允许依据 approved 的 CAPABILITY-002、DESIGN-007、DECISION-006 和 PLAN-007 实施。2026-08-25
人工审阅已确认推荐方案与安全边界，可以按任务范围开始编码。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

已产出 OpenAPI/contract tests、Gateway request context/envelope/pagination/problem mapper、Web generated
types/client/error/parser、status 迁移、跨模块测试与确认后的全局文档。

## 完成标准

- [x] DECISION-006 checklist 与安全人工确认完成，所有上游文档 approved。
- [x] OpenAPI examples、Java response、TypeScript generated types 与运行时 parser 对齐。
- [x] 普通成功、集合/分页、204 和规定的 4xx/5xx 均符合 wire 与 HTTP 规则。
- [x] request ID 头/body/log 一致；Idempotency-Key、CSRF、Session 不混用。
- [x] network/timeout/aborted/http/contract 错误分类和未知字段兼容行为通过测试。
- [x] 5xx、validation 与 upstream 非法响应无敏感信息泄漏。
- [x] 聚合后端验证、Web check/build/E2E、contract drift 与真实 proxy 全部通过。

## 验证

已按 VERIFY-009 的矩阵执行。生成命令为 `npm run generate:api`，漂移门禁为
`npm run generate:api:check`，并已纳入 `npm run check` 与 CI。

## 风险

任何需要更改成功 envelope、Problem 字段、status/code 映射、Gateway 所有权、API version 或例外
范围的发现都必须回到 DECISION-006；不得扩大到业务服务、数据库、Kafka 或 WORK-002。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-25：根据人工要求设置“文档审阅后再编码”门禁；当前没有开始实现。
- 2026-08-25：人工批准推荐方案，解除门禁并准备进入实现。
- 2026-08-25：状态变更：todo → ready。原因：上游方案已批准、人工门禁已解除且任务范围完整
- 2026-08-25：状态变更：ready → doing。原因：开始实现统一 Web REST 交换协议与请求基建
- 2026-08-25：OpenAPI 3.1.2、Gateway success/problem/request ID/pagination、Web client/生成类型与
  status 首个消费者完成；初选 openapi-typescript 因 TypeScript 6 peer 不兼容，改用精确锁定且实测
  兼容的 @hey-api/openapi-ts 0.99.0，仅启用类型生成插件。
- 2026-08-25：契约、Gateway、Web、浏览器和真实 Vite proxy 验证通过，完成标准全部满足。
- 2026-08-25：状态变更：doing → done。原因：实现、测试、跨模块联调与全局文档同步完成
