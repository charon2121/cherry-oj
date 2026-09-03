---
id: "TASK-054"
type: "task"
title: "迁移双主题运行时、全站 Shell 与基础页面"
status: "done"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["TASK-053"]
related: ["WORK-015", "WORK-019", "WORK-020", "WORK-031", "WORK-033"]
implements: ["IMPROVEMENT-002#REQ-005", "IMPROVEMENT-002#REQ-006", "IMPROVEMENT-002#REQ-007", "IMPROVEMENT-002#REQ-009", "IMPROVEMENT-002#REQ-010", "IMPROVEMENT-002#REQ-011", "IMPROVEMENT-002#REQ-012", "IMPROVEMENT-002#REQ-013", "IMPROVEMENT-002#REQ-014", "IMPROVEMENT-002#REQ-015", "IMPROVEMENT-002#REQ-016", "IMPROVEMENT-002#AC-005", "IMPROVEMENT-002#AC-006", "IMPROVEMENT-002#AC-007", "IMPROVEMENT-002#AC-009", "IMPROVEMENT-002#AC-010", "IMPROVEMENT-002#AC-011", "IMPROVEMENT-002#AC-012", "IMPROVEMENT-002#AC-015"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/design-system.md", "docs/design-system", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/design-system", "apps/web/scripts/check-design-system.mjs", "apps/web/src/styles", "apps/web/src/components/ui", "apps/web/src/lib/theme", "apps/web/src/app/pages", "apps/web/src/app/shells", "apps/web/src/features/auth/components", "apps/web/src/routes/__root.tsx", "apps/web/src/routes/_site.account.password.tsx", "apps/web/src/routes/_site.forbidden.tsx", "apps/web/src/routes/_site.index.tsx", "apps/web/src/routes/_site.login.tsx", "apps/web/src/routes/admin.index.tsx", "apps/web/src/routes/admin.dashborad.tsx", "apps/web/src/routes/admin.users.tsx", "apps/web/e2e/smoke.spec.ts", "apps/web/e2e/design-system.spec.ts", "/Users/charon/Downloads/Cherry OJ Design System/ui_kits", "development/works/WORK-031", "development/works/WORK-033", "development/works/WORK-034"]
write_paths: ["apps/web/index.html", "apps/web/scripts/check-design-system.mjs", "apps/web/src/lib/theme", "apps/web/src/app/pages", "apps/web/src/app/shells", "apps/web/src/features/auth/components", "apps/web/src/routes/__root.tsx", "apps/web/src/routes/_site.account.password.tsx", "apps/web/src/routes/_site.forbidden.tsx", "apps/web/src/routes/_site.index.tsx", "apps/web/src/routes/_site.login.tsx", "apps/web/src/routes/admin.index.tsx", "apps/web/src/routes/admin.dashborad.tsx", "apps/web/src/routes/admin.users.tsx", "apps/web/e2e/smoke.spec.ts", "apps/web/e2e/design-system.spec.ts", "development/works/WORK-034"]
forbidden_paths: ["apps/web/design-system", "docs/design-system.md", "docs/design-system", "apps/web/src/components/ui", "apps/web/src/features/problems", "apps/web/src/routes/_site.problems", "apps/web/src/routes/admin.problems", "apps/web/src/api", "apps/web/src/generated", "contracts", "apps/server", "apps/judge-engine", "database migrations", "development/works（WORK-034 除外）"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-054：迁移双主题运行时、全站 Shell 与基础页面

## 任务目标

用新 Foundation 和共享组件迁移站点/管理 Shell、双主题启动链、认证与基础管理页面，保留暗色/浅色切换、
偏好和首屏一致性，但不在本任务修改题目业务工作台。

## 依据

依赖 TASK-053 的生产组件层。页面构图以下载版 Judge app/marketing UI kit 为参考，主题删除、生产适配与
导航规则以 DESIGN-028、DECISION-019 和 IMPROVEMENT-002 为准。

## 可查看范围

以 front matter 为准。实施前运行 `scripts/work context TASK-054`，盘点 ThemeSwitcher、主题 storage/bootstrap、
两个 Shell、基础路由、管理导航和 E2E 中所有主题消费者与绕过语义 token 的分支。

## 可修改范围

允许重构但不得删除主题切换、偏好持久化和首屏双主题逻辑；迁移站点与管理 Shell、首页、登录、密码修改、
403、Dashboard、用户账号页及相关测试；管理侧栏顺序在此调整为 Dashboard、账号管理、题目管理。

## 禁止修改

Foundation、设计文档、共享组件实现、题目业务组件/路由、API、生成客户端、后端、contracts、数据库和其它
WORK 禁止修改。若共享组件不足，应回到 TASK-053，而不是在页面新增私有设计变体。

## 依赖

TASK-053 完成且共享组件兼容性、Storybook 与可访问性检查通过后执行。

## 产出

- 暗色/浅色共用结构的首屏、站点 Shell 与管理 Shell。
- 只注册两个新主题合同的 ThemeSwitcher、偏好持久化和无闪烁 bootstrap。
- 首页、认证、错误页、Dashboard 与用户账号页的新设计实现。
- 管理导航新顺序及对应键盘、刷新、登录跳转、桌面/320px 回归证据。

## 完成标准

- [x] 暗色/浅色入口可达且选择能持久化，刷新、登录跳转和跨 Shell 导航不闪烁、不回退主题。
- [x] 未知/损坏偏好安全回退暗色，不引起报错、认证回归或影响其它存储。
- [x] Shell 精确使用 220px sidebar、56px header、hairline 和新层级 token，滚动边界保持正确。
- [x] 两主题的基础页面不残留旧 variant、raw visual 值、主题专属 DOM 或页面私有组件替代品。
- [x] 管理导航顺序和 active/focus/折叠行为符合要求，题目管理位于账号管理之后。
- [x] smoke/design-system E2E、typecheck、build、320px、reduced-motion 与范围检查通过。

## 验证

执行旧 token/主题分叉负向扫描、切换/持久化/刷新与登录跳转用例、双主题 Shell 几何和导航顺序断言、
键盘/焦点、320px、forced-colors、reduced-motion、Web check/build 和范围检查，并记录首屏体积。

## 风险

主题运行时和 Shell 调整位于所有页面入口，任何首屏脚本、hydration、主题持久化或滚动容器错误都会全站
放大。发生题目业务行为变化、需要改共享组件或任一主题对比度失败时立即阻断，不借页面迁移扩大任务边界。

## 执行记录

- 2026-09-03：创建任务；随后纳入下载版设计系统的系统级迁移链。
- 2026-09-03：将源码检查器纳入范围，以便在迁移三个 Shell 后删除 TASK-053 留下的精确 transform 临时
  allowlist；这只关闭已记录退出条件，不修改 Foundation 或共享组件。
- 2026-09-03：状态变更：todo → ready。原因：TASK-053 已完成共享组件、Storybook、E2E 与兼容性验证
- 2026-09-03：状态变更：ready → doing。原因：开始迁移双主题运行时、全站 Shell 与基础页面
- 2026-09-03：完成 Site/Admin Shell、首页、登录、改密、403/404、Dashboard 与用户账号页迁移；桌面
  Admin 使用固定 220px 侧栏、56px hairline 顶栏和仅主内容滚动，手机继续使用 Sheet。管理导航顺序固定为
  Dashboard → 账号管理 → 题目管理，同时删除 TASK-053 的 transform 临时 allowlist。
- 2026-09-03：以 1280×760 同视口对照下载版 marketing/app UI kit 与暗色首页、管理 Dashboard；再次并排
  检查暗色/浅色首页和登录页。暗色 surface、hairline、密度、字重与来源一致，浅色保持同构层级；任务入口
  仍遵守 WORK-031，不复制来源中不存在于当前产品的注册、赛事或虚构指标。
- 2026-09-03：`npm run check` 通过 32 个文件/116 项测试，生产 build 通过；设计系统与 smoke Chromium
  共 27 项通过，覆盖首帧主题、损坏偏好、跨标签同步、320px、forced-colors、reduced-motion、登录/改密、
  管理滚动、导航与用户管理行为。
- 2026-09-03：状态变更：doing → done。原因：双主题运行时、Site/Admin Shell、基础页面、导航顺序及浏览器矩阵均已迁移并通过验证
