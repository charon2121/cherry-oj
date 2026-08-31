---
id: "TASK-033"
type: "task"
title: "冻结题库与题目管理公开契约"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "DESIGN-019", "DECISION-014", "PLAN-015"]
related: []
implements: ["FEATURE-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/data-model.md", "docs/frontend.md", "contracts/web-api.openapi.json", "apps/web/openapi-ts.config.mjs", "apps/web/scripts/check-generated-api.mjs", "apps/web/src/generated/api", "development/works/WORK-025"]
write_paths: ["contracts/web-api.openapi.json", "apps/web/src/generated/api", "development/works/WORK-025"]
forbidden_paths: ["apps/server", "apps/web/src/app", "apps/web/src/components", "apps/web/src/features", "apps/web/src/routes", "apps/web/e2e", "apps/web/design-system", "apps/judge-engine", "docs/design-system"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-033：冻结题库与题目管理公开契约

## 任务目标

冻结 public 题库与 ADMIN 题目/测试数据/部署/发布 API，并更新生成类型，使所有后续任务在同一字段、
权限、幂等、multipart/binary 和错误语义上实现。

## 依据

实现 `FEATURE-007` 的 REQ-001～REQ-028 契约边界，遵守 `DESIGN-019` 与待批准的 `DECISION-014`。本任务
不定义服务间 DTO、不实现网络/文件/业务逻辑。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `GET /api/problems` 与 `GET /api/problems/{slug}` 的参数、成功响应、pagination、错误及示例。
- `ProblemSummary`、`ProblemDetail`、样例、允许语言、难度、代码模式、排序等封闭 schema。
- ADMIN Problem/Version/Sample/Language/TestDataVersion/Manifest/Deployment/Calibration/PublishCheck DTO。
- create/revision/save/delete/archive/upload/download/deploy/calibrate/publish 的权限、CSRF、rowVersion、
  rowVersion/资源状态、multipart/form-data、binary 和稳定错误。
- 由固定生成器产生的 `apps/web/src/generated/api`，不手改生成物。
- 契约示例明确不出现 judgeTemplate、测试数据、审计和作者字段。

## 完成标准

- [x] 列表和详情 schema 精确覆盖 FEATURE-007 的公开字段、optional/required 与大小上限。
- [x] cursor pagination 复用公共 `CursorPagination`，不新增页码总数或客户端可解析语义。
- [x] 公开路径无 cookie security 要求，非法参数/游标、404 与 Gateway 下游故障具有稳定 code。
- [x] ADMIN 路径声明 cookieAuth；写操作声明 CSRF/rowVersion 或明确资源状态；下载明确为 ZIP 二进制。
- [x] 上传 schema/响应表达大小上限、201 和 413；测试正文、storageRef、参考源码不出现在响应 schema。
- [x] OpenAPI 示例能被 schema 验证，敏感字段 canary 不能通过公开 DTO。
- [x] TypeScript 生成检查无漂移，现有 auth/status/admin 类型兼容。

## 验证

运行仓库 contracts JSON/OpenAPI 检查；在 `apps/web` 执行 `npm run generate:api`、
`npm run generate:api:check`、`npm run typecheck`。检查生成差异只包含预期题库类型，并使用契约示例验证
新增 schema。记录命令与结果到 `VERIFY-025`，但在后续实现完成前不标记整体验证通过。

## 风险

最大风险是把 internal snapshot/storageRef/参考源码写进 public/admin schema，或让 multipart/binary 被
普通 JSON 包装规则误处理。若需要 CORE、多语言、环境管理、submission 或新敏感字段，停止并升级上游。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全公开契约任务的读写边界、产出、完成标准和安全升级条件，等待人工批准。
- 2026-08-30：根据范围反馈扩展 public/admin、测试数据、部署、校准和发布契约。
- 2026-08-30：状态变更：todo → ready。原因：上游文档均已批准，开始冻结公开与管理 API 契约
- 2026-08-30：完成 OpenAPI 0.2.0 与固定生成类型；公开 DTO canary、contracts、生成漂移和 TypeScript
  检查通过。
- 2026-08-30：状态变更：ready → doing。原因：按已批准范围实施公开与管理 API 契约
- 2026-08-30：状态变更：doing → done。原因：OpenAPI 0.2.0、公开/管理 DTO、multipart/binary 与生成类型完成，契约和类型检查通过
