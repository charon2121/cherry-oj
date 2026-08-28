---
id: "TASK-028"
type: "task"
title: "搭建用户端与管理端应用布局"
status: "done"
work: "WORK-020"
owners: ["codex/root"]
depends_on: ["FEATURE-003", "EXPERIENCE-009", "DESIGN-016"]
related: []
implements: ["FEATURE-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/frontend.md", "docs/design-system.md", "apps/web/TOOLCHAIN.md", "apps/web/components.json", "apps/web/package.json", "apps/web/design-system", "apps/web/src/app", "apps/web/src/components/ui", "apps/web/src/features/auth", "apps/web/src/routes", "apps/web/src/styles/globals.css", "apps/web/e2e", "development/works/WORK-015", "development/works/WORK-019", "development/works/WORK-020"]
write_paths: ["apps/web/package.json", "apps/web/package-lock.json", "apps/web/src/app", "apps/web/src/components/ui", "apps/web/src/hooks", "apps/web/src/routes", "apps/web/src/routeTree.gen.ts", "apps/web/e2e", "development/works/WORK-020"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/generated/design-system", "apps/web/public/generated", "apps/server", "apps/judge-engine", "contracts", "docs", "development/works（WORK-020 除外）"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# TASK-028：搭建用户端与管理端应用布局

## 任务目标

在用户明确批准上游文档并允许执行后，为现有 Web 建立用户端与管理端应用壳层，接入可展开二级菜单、
320px 移动导航和完整语义 landmark，把现有页面迁入正确壳层，同时保持身份、权限和业务行为不变。

## 依据

实现 FEATURE-003 的 REQ-001～REQ-017 和 AC-001～AC-008；交互以 EXPERIENCE-009 为准，路由、组件、
依赖与迁移以 DESIGN-016 为准。WORK-015 是不可绕过的设计系统基线；WORK-019 只提供主页内容参考，其
用户端侧栏不进入本任务实现。

## 可查看范围

以 front matter 的 `read_paths` 为准。实施前先用 `scripts/work context TASK-028` 读取已批准上下文，
并检查 shadcn CLI 对 `base-nova` Sidebar/Collapsible 的实际变更；不要从在线示例手抄另一套主题值。

## 可修改范围

以 front matter 的 `write_paths` 为准。`routeTree.gen.ts` 只能通过 Router 插件生成；`package.json` 与锁文件
只在 shadcn 组件确实需要新增/调整依赖时修改。WORK-020 只记录状态和验证证据。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。特别禁止修改设计系统 token、主题、manifest、生成主题文件、
API 契约、服务端和其它工作项，也禁止为通过源码扫描扩大 allowlist。

## 依赖

FEATURE-003、EXPERIENCE-009、DESIGN-016 必须由用户从 review 明确批准，TASK-028 再进入 ready/doing。
初始“搭建整体页面结构”不视为文档审批或编码授权。

## 产出

- 经 Cherry token 覆盖的 shadcn/ui Sidebar、Collapsible、移动 Sheet 及必要纯 UI 依赖组件、story 和测试。
- `src/app/shells` 下的用户端/管理端 Shell、Header/Footer、账号动作与类型化管理导航。
- `_site` pathless 路由、`admin` layout 路由、`/admin` 与 `/admin/dashborad` 共用的最小 Dashboard，以及
  对应 not-found 归属。
- 迁移后的首页、登录、改密、403、用户管理页面与重新生成的 route tree。
- 更新后的组件测试、Storybook 场景和 Playwright 双端/权限/320px 验收。

## 完成标准

- [x] 用户端只渲染 Header/Main/Footer，管理端只渲染 Header/Main(Sidebar+Content)/Footer；无双壳层。
- [x] `/admin` 与 `/admin/dashborad` 复用同一个 Dashboard 页面组件，只显示页面标题且不请求业务数据。
- [x] 管理端“账号管理 → 用户账号”支持鼠标和键盘展开、当前项、自动展开父项与移动端关闭。
- [x] Header/Footer 横跨管理端整页；桌面两列、320px 单列抽屉均无横向溢出或内容遮挡。
- [x] skip link、唯一 main、导航 label、`aria-current`、`aria-expanded`、Escape 与焦点恢复可被测试观察。
- [x] Session、登录、首次改密、403、ADMIN guard、退出与用户管理数据行为保持原有结果。
- [x] shadcn 源码只消费现有 semantic token/Tailwind alias，不含 raw 色值、theme id 分支或 Radix import。
- [x] 两主题、长中文、reduced-motion、forced-colors 与 320px story/E2E 检查通过。
- [x] `npm run check`、`npm run build`、`npm run storybook:build` 和 `npm run test:e2e` 通过。

## 验证

在 `apps/web` 执行并把实际结果写入 VERIFY-020：

1. `npm run format`，再检查 diff 只包含允许范围。
2. `npm run check`：设计系统源码门禁、生成物、格式、lint、类型和组件测试全部通过。
3. `npm run build` 与 `npm run storybook:build`：路由树、生产产物和 story 可构建。
4. `npm run test:e2e`：普通壳层、管理壳层、身份/权限、子菜单和 320px 无溢出场景通过。
5. 在 1440px 与 320px、`cherry-black` 与 `pure-white` 中人工检查 Header/Main/Footer、全宽管理 Header/
   Footer、Sidebar/Sheet、长中文、焦点、当前项和表格内容；截图仅作为证据，不替代行为断言。

## 风险

shadcn Sidebar 的默认定位可能与全宽 Header/Footer 冲突，先在独立 story 中验证组合，不通过时只调整
Shell 布局包装，不删除无障碍行为。若需要修改 `apps/web/design-system`、新增主题语义、引入 Radix、
改变身份行为或创建新业务路由，立即停止并升级 DESIGN/任务范围，不在 TASK-028 内顺手扩大。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：补全代码边界、交付物、完成标准和验证矩阵；等待人工审核，尚未修改 Web 实现。
- 2026-08-28：用户批准补充 Dashboard 路由后的文档并明确允许直接执行 TASK-028。
- 2026-08-28：状态变更：todo → ready。原因：上游文档已获用户批准，任务范围与验证标准完整
- 2026-08-28：状态变更：ready → doing。原因：用户明确授权文档更新后直接进行前端开发
- 2026-08-28：完成 `_site` 与 `admin` 两套路由壳层、Dashboard 双入口、管理子菜单和 320px Sheet；
  shadcn 组件采用 Base UI 原语并映射既有 Cherry semantic token，未改设计系统真源或新增依赖。
- 2026-08-28：新增 Sidebar/Sheet Storybook 场景与组件测试，扩展 Playwright 的双入口、当前项、移动导航、
  焦点恢复和双主题 320px 回归；全量命令证据见 VERIFY-020。
- 2026-08-28：状态变更：doing → done。原因：用户端与管理端布局、Dashboard 双入口、组件测试、Storybook 和浏览器验收均已完成
