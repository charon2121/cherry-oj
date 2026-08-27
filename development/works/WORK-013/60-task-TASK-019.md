---
id: "TASK-019"
type: "task"
title: "交付 Web 登录与账号管理体验"
status: "done"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["CAPABILITY-004", "EXPERIENCE-005", "DESIGN-010", "DECISION-009", "PLAN-010", "TASK-017"]
related: []
implements: ["CAPABILITY-004#REQ-007", "CAPABILITY-004#REQ-008", "CAPABILITY-004#REQ-010", "CAPABILITY-004#REQ-012"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts", "docs/frontend.md", "apps/web", "apps/server/gateway-service", "development/works/WORK-009", "development/works/WORK-013"]
write_paths: ["apps/web", "development/works/WORK-013"]
forbidden_paths: ["apps/server", "apps/judge-engine", "docs", "contracts"]
created_at: "2026-08-26"
updated_at: "2026-08-26"
---




# TASK-019：交付 Web 登录与账号管理体验

## 任务目标

基于已生成的公开 OpenAPI 类型和 Gateway 行为，交付 Session 初始化、登录、首次改密、退出、受保护
路由与管理员用户管理页面，完整表达 loading/错误/未认证/无权限/恢复状态。

## 依据

实现 CAPABILITY-004 REQ-007、REQ-008、REQ-010、REQ-012 的 Web 部分和 EXPERIENCE-005；以 approved
的 DESIGN-010、DECISION-009、PLAN-010 及已完成 TASK-017 为前置，不修改生成契约或后端。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

auth/admin feature、Session Query、CSRF 获取与 mutation、登录/改密/管理页面、路由保护、错误与恢复 UI、
无障碍单测、MSW 场景、Playwright E2E 和本 WORK 执行记录。

## 完成标准

- [x] 首屏 Session 检查不闪出受保护内容，未登录能保留且只接受安全站内 return path。
- [x] 密码/JWT/登录授权不进入 URL、localStorage、sessionStorage、持久 Query cache、日志或错误文案。
- [x] 登录、限速、must-change-password、退出、401、403、503 与恢复状态符合 EXPERIENCE-005。
- [x] 管理员可分页查看、创建、停用/恢复和重置 USER；临时密码只展示一次，危险操作有明确确认。
- [x] 表单键盘、焦点、label、错误关联、live region、窄屏和非颜色状态测试通过。
- [x] 生成类型无漂移，format/lint/typecheck/unit/build/E2E 全绿，未修改 contracts、docs 或后端。

## 验证

运行 `cd apps/web && npm run generate:api:check && npm run format:check && npm run lint && npm run typecheck &&
npm run test:run && npm run build && npm run test:e2e`，并运行 `scripts/work check`、`git diff --check`。MSW
与 E2E 覆盖 success、错误、刷新恢复、重复提交、键盘和敏感数据负例。

## 风险

不得把 JWT 暴露给 Web、自己解析 Cookie、手改 generated types 或用前端路由保护替代服务端授权。若需要
开放注册、找回密码、更多角色/资料字段或新的公开错误语义，回到上游文档，不在页面中脑补。

## 执行记录

- 2026-08-26：创建任务。
- 2026-08-26：完成内存 CSRF、Session Query、安全 return path、登录/首次改密/退出、路由保护、
  403 页面以及管理员用户分页、创建、状态变更和密码重置界面。
- 2026-08-26：生成类型检查、format、lint、typecheck、36 个单测、生产构建和 5 条 Chromium E2E
  全部通过；E2E 覆盖限速恢复、重复提交、敏感信息负例、首次改密、窄屏与一次性临时密码。
- 2026-08-26：状态变更：todo → ready。原因：TASK-017 已完成，Web 任务前置和边界均满足
- 2026-08-26：状态变更：ready → doing。原因：开始交付 Web Session、登录、路由保护和管理员用户管理体验
- 2026-08-26：状态变更：doing → done。原因：Web 登录、首次改密、路由保护、管理员用户管理及全部前端验收测试已完成
- 2026-08-26：独立复核补充密码显隐与 Caps Lock 提示、401 本地清理、管理员空状态、危险操作后果
  文案和 Retry-After 展示；使用项目声明的 Node 24.19 再次全量通过。
