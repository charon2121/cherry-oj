---
id: "TASK-030"
type: "task"
title: "微调双端应用布局页脚"
status: "done"
work: "WORK-022"
owners: ["codex/root"]
depends_on: ["FEATURE-004", "EXPERIENCE-010", "TASK-028"]
related: ["WORK-020", "VERIFY-020"]
implements: ["FEATURE-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "apps/web/TOOLCHAIN.md", "apps/web/src/app/shells", "apps/web/e2e", "development/works/WORK-020", "development/works/WORK-022"]
write_paths: ["apps/web/src/app/shells/admin-app-shell.tsx", "apps/web/src/app/shells/site-app-shell.tsx", "apps/web/e2e", "development/works/WORK-022"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/generated/design-system", "apps/web/public/generated", "apps/web/src/components", "apps/web/src/features", "apps/web/src/routes", "apps/server", "apps/judge-engine", "contracts", "docs", "development/works（WORK-022 除外）"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# TASK-030：微调双端应用布局页脚

## 任务目标

在不改变 WORK-020 其它结构和行为的前提下，移除管理端 Footer 及其网格占位，并让用户端 Footer 与 Main
使用连续页面表面，同时补齐针对结构、样式和响应式的浏览器断言。

## 依据

实现 FEATURE-004 的 REQ-001～REQ-009、AC-001～AC-005，交互以 EXPERIENCE-010 为准；复用 WORK-020
已有 Shell 与测试，不修改设计系统或业务组件。

## 可查看范围

以 front matter 的 `read_paths` 为准。实施前使用 `scripts/work context TASK-030` 读取已批准范围，并先
确认工作树中 WORK-020 的未提交实现仍是本任务基线。

## 可修改范围

以 front matter 的 `write_paths` 为准。Shell 只做 Footer 与网格行相关调整；E2E 只补充或更新对应
landmark、计算样式、底部占位和回归断言。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。特别禁止借机调整 Header、Sidebar、路由、业务页面、主题、
token、依赖或服务端，也不得修改 WORK-020 的历史实现定义来掩盖本次差异。

## 依赖

FEATURE-004 与 EXPERIENCE-010 必须获得用户批准；TASK-028 提供已实现的基线。TASK-030 在明确执行授权
前保持 `todo`。

## 产出

- 管理端 Shell：移除 Footer 元素，将根网格收敛为 Header + 中间区域。
- 用户端 Shell：保留 Footer 内容和语义，移除与 Main 分区的背景、边框或抬升样式。
- Playwright：新增管理端无 `contentinfo`/无底部占位、用户端 Footer 同表面和 320px 回归断言。
- VERIFY-022：记录实际命令、结果、范围和人工视觉复核证据。

## 完成标准

- [x] 管理端所有路由不渲染 Footer/`contentinfo`，中间区域填满剩余高度。
- [x] 用户端保留唯一 Footer/`contentinfo`，其外层没有独立背景、顶边框或阴影。
- [x] 用户端短页 Footer 贴底，长页不覆盖内容；两个主题和 320px 无横向溢出。
- [x] Dashboard 双入口、侧栏子菜单、移动 Sheet、身份与用户管理行为不回退。
- [x] 修改严格位于 `write_paths`，设计系统、路由、业务组件和后端无变化。
- [x] `npm run check`、`npm run build`、`npm run storybook:build`、`npm run test:e2e` 和文档校验通过。

## 验证

在 `apps/web` 执行 `npm run format`、`npm run check`、`npm run build`、`npm run storybook:build` 和
`npm run test:e2e`。浏览器验收至少覆盖：

1. 用户端 1440px/320px、双主题的 Footer 位置、背景、边框和横向溢出；
2. `/admin`、`/admin/dashborad`、`/admin/users` 的无 Footer、底部无占位和导航行为；
3. 既有 forced-colors、reduced-motion、权限、登录、改密和用户管理回归。

仓库根目录执行 `scripts/work check` 与文档链接校验，所有结果按事实写入 VERIFY-022。

## 风险

风险集中在 CSS Grid 第三行残留、误删用户端 Footer 语义，以及测试仍按 WORK-020 旧预期断言管理 Footer。
若需要修改组件、路由或设计系统，立即暂停并升级 TASK 范围；不以扩大 allowlist 解决。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：补齐只修改两个 Shell 与对应 E2E 的执行边界；等待上游文档审核和执行授权。
- 2026-08-28：用户明确批准 FEATURE-004 与 EXPERIENCE-010，并授权执行 TASK-030。
- 2026-08-28：管理端根网格收敛为 Header + Main，Footer 完全移除；用户端 Footer 保留语义与内容，
  移除背景、顶边框和独立表面样式。
- 2026-08-28：Playwright 增加管理端无 `contentinfo`、两行网格、视口填充，以及用户 Footer 同表面、
  无边框/阴影和双主题 320px 断言；全量结果见 VERIFY-022。
- 2026-08-28：状态变更：todo → ready。原因：上游文档已获用户批准，任务边界和验收标准完整
- 2026-08-28：状态变更：ready → doing。原因：用户明确授权按照已审核文档执行前端微调
- 2026-08-28：状态变更：doing → done。原因：双端 Footer 微调、浏览器断言和全量验证均已完成
