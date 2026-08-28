---
id: "DESIGN-016"
type: "design"
title: "用户端与管理端应用壳层方案"
status: "approved"
work: "WORK-020"
owners: ["codex/root"]
depends_on: ["FEATURE-003", "EXPERIENCE-009"]
related: ["DESIGN-015"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# DESIGN-016：用户端与管理端应用壳层方案

## 背景

[`FEATURE-003`](./10-feature-FEATURE-003.md) 和 [`EXPERIENCE-009`](./20-experience-EXPERIENCE-009.md)
要求把当前根路由中的临时全局 Header 拆成互斥的用户端与管理端应用壳层。技术与视觉边界来自
[`CLAUDE.md`](../../../CLAUDE.md)、[`docs/frontend.md`](../../../docs/frontend.md) 和
[`docs/design-system.md`](../../../docs/design-system.md)。

WORK-019 的已交付 Figma 主页使用用户端桌面侧栏；本次用户明确指定传统 `header/main/footer`，因此
DESIGN-015 仍可作为主页内容与视觉参考，但其用户端侧栏不作为 Web 路由壳层依据。管理端则采用设计
系统 `app-shell-navigation` 已允许的 sidebar 变体。

## 目标与限制

目标是建立可复用、路由驱动、语义正确的两套 Shell，并迁移现有页面而不改变业务行为。限制如下：

- 只修改 `apps/web`，不改服务端、OpenAPI、身份规则或设计系统真源。
- 新增一个最小 Dashboard 页面；`/admin` 与 `/admin/dashborad` 复用同一组件，页面除标题外不创建业务内容。
- 子菜单能力继续用 `/admin/users` 验证，不创建其它空后台页面。
- 优先使用当前 `components.json` 的 `base-nova` shadcn/ui Sidebar、Collapsible 与移动 Sheet 组合；生成
  源码是项目代码，必须覆盖为 Cherry semantic token，继续基于现有 Base UI。
- 不引入 Radix、第二套 UI 库、全局状态库、主题 id 分支或新的持久化偏好。
- `routeTree.gen.ts` 只由 TanStack Router 插件生成，禁止手改。

## 整体方案

### 路由树

采用 TanStack Router 的 pathless layout 与 layout route：

```text
__root.tsx                         只保留 Router context、全局 fallback 与 Outlet
├─ _site.tsx                      无 URL 段的 UserAppShell
│  ├─ _site.index.tsx             /
│  ├─ _site.login.tsx             /login
│  ├─ _site.account.password.tsx  /account/password
│  └─ _site.forbidden.tsx         /forbidden
└─ admin.tsx                      /admin，ADMIN guard + AdminAppShell
   ├─ admin.index.tsx             /admin → AdminDashboardPage
   ├─ admin.dashborad.tsx         /admin/dashborad → AdminDashboardPage
   └─ admin.users.tsx             /admin/users
```

`_site` 不出现在 URL，只负责用户端壳层；`admin` 父路由集中权限和后台壳层。根 404 使用用户端恢复页；
`/admin/*` 的未匹配子路由由 admin route 的 not-found 内容处理，从而保留管理上下文。

### 用户端 Shell

```text
UserAppShell (min-height: 100svh; rows: auto / 1fr / auto)
├─ skip link
├─ SiteHeader
│  ├─ brand
│  ├─ desktop primary navigation
│  ├─ mobile navigation trigger/sheet
│  └─ session actions
├─ main#site-main
│  └─ Outlet
└─ SiteFooter
```

Header 使用 `panel`/`border` 语义，Main 自然增长，Footer 在短页贴底、长页随文档流后移。业务页面继续
自行使用 `Container` 与 `Section`，Shell 不为所有内容强制相同最大宽度。

### 管理端 Shell

```text
AdminAppShell (min-height: 100svh; rows: auto / minmax(0,1fr) / auto)
├─ skip link
├─ AdminHeader
│  ├─ brand + 管理中心
│  ├─ SidebarTrigger（窄屏始终可见）
│  ├─ 返回用户端
│  └─ session actions
├─ main admin workspace
│  ├─ AdminSidebar / nav
│  │  └─ Collapsible → SidebarMenuSub
│  └─ content#admin-main
│     └─ Outlet
└─ AdminFooter
```

桌面 Main 是两列，Sidebar 使用固定设计 token 节奏与自身纵向滚动；Content 使用 `min-width: 0` 防止表格
撑破布局。窄屏 Sidebar 通过 shadcn Sheet/offcanvas 呈现，主内容恢复单列。Header 与 Footer 横跨整页，
满足上—中（左右）—下结构，而不是只属于右侧内容。

### 导航模型

应用层使用只读配置描述 `label`、Lucide `icon`、`to` 或 `children`。类型保证一级项二选一：叶子项有
`to`，父项有 `children`，不能同时存在。当前只配置：

```text
管理
├─ Dashboard → /admin（/admin/dashborad 也视为当前）
└─ 账号管理
   └─ 用户账号 → /admin/users?page=1
```

父项展开状态默认由当前匹配路由计算，并允许用户在会话内切换；不写 localStorage。当前页通过 Router
匹配决定，不复制 pathname 字符串判断。选择移动端叶子链接后关闭 Sheet。

## 模块与数据

- `src/app/shells/`：持有 User/Admin Shell、Header/Footer、Session actions 和导航配置；允许依赖 auth
  feature、Router 与共享 UI，符合 `app/routes → features → components/lib`。
- `src/components/ui/`：通过 shadcn/ui 加入纯 UI Sidebar/Collapsible/Sheet 及必要基础组件；不得 import
  app、routes 或 features。
- `src/routes/`：只做路由归属、guard、search params 和页面装配；现有业务页面内容尽量原样迁移。
- `src/app/pages/admin-dashboard-page.tsx`（或同层等价模块）：提供 `/admin` 与 `/admin/dashborad` 复用的
  最小 Dashboard 内容，只渲染页面级标题，不读取服务端数据。
- `src/hooks/`：仅在 shadcn Sidebar 确实需要时保存无业务含义的窄屏检测 hook。
- Session 数据继续由 `sessionQueryOptions()` 与 Query cache 持有；Shell 不新增 API，不复制用户状态。
- Sidebar/移动面板开关是当前页面局部 UI 状态，路由切换可重建，不持久化。

## 接口与状态

不改变网络接口。组件合同如下：

- `SessionActions` 接收壳层变体或展示策略，但仍统一处理 pending、error、anonymous、authenticated、
  password-change-required、logout pending/error。
- `UserAppShell` 和 `AdminAppShell` 只渲染一个 `main`，通过稳定 id 接收 skip link。
- `AdminNavigation` 从类型化配置渲染 `SidebarGroup/Menu/MenuButton/MenuSub`；叶子 Link 提供 current 状态，
  Dashboard 的 current matcher 同时覆盖 `/admin` 与 `/admin/dashborad`。
- `AdminRoute` 的 `beforeLoad` 集中调用 `requireAdmin`；子页可移除重复 guard，但权限测试必须证明行为不变。
- Header/Footer 不等待业务接口才出现，避免布局跳动；身份相关槽位保留最小稳定宽度或可读状态。

## 安全与失败

- 管理 Shell 不能代替 route guard；只有 `requireAdmin` 成功后才渲染管理导航和 Outlet。
- 导航配置不包含隐藏能力、内部服务地址、权限令牌或服务拓扑；不可用路由不以 disabled 占位伪装交付。
- 移动 Sheet 必须有标题/可访问名称、焦点约束、Escape 关闭和关闭后焦点恢复；背景不可交互。
- Session 错误不清空已知身份，不自行导航；401 的现有退出/重登录处理保持原行为。
- 若 shadcn 生成源码带 raw 色值、`.dark`、opacity 状态或 Radix import，实施时先按项目配置重新生成或
  局部改写；不能用设计系统扫描 allowlist 掩盖。

## 监控与部署

无数据迁移、服务部署或新增运行监控。交付仍是 Vite 静态产物。验证使用 Vitest/Testing Library、
Storybook a11y、Playwright 和生产构建；上线后观察主要看路由可达性、移动菜单和已有登录/管理链路，
不新增埋点供应商。

## 迁移与兼容

按“纯 UI 组件 → App Shell → 路由归属 → 页面适配 → 测试”推进，保证每步可编译。迁移时：

- 把当前 `__root.tsx` 的 SessionNavigation 移入应用层共享账号动作；根路由不再硬编码用户 Header。
- 现有页面文件按 `_site.*` 或 `admin.*` 规则改名，Router 插件重建 `routeTree.gen.ts`。
- `admin.index.tsx` 与 `admin.dashborad.tsx` 共同导入同一个 Dashboard 页面组件，不互相重定向。
- `admin.users` 移除页面底部“返回首页”，由 AdminHeader 提供统一返回；数据表、mutation 和 search params
  不变。
- Login 当前按 `100svh - header` 估算高度，需要改为在 Shell Main 内自然居中，避免新增 Footer 后溢出。
- 现有 E2E role/name 断言尽量保留；新增具体导航名称，避免用 DOM class 绑定实现。

回退只需恢复旧 root Header 与扁平路由文件，不涉及 API 或数据。`routeTree.gen.ts` 随构建重新生成。

## 备选方案

- **A（采用）**：pathless User Shell + `/admin` Layout Shell。路由树直接表达互斥边界，guard、404 与页面
  归属清楚，未来新增页面只需挂到正确父路由。
- **B**：Root 根据 `location.pathname` 条件渲染两套壳层。文件改动少，但把路由分类写成字符串分支，
  容易出现双壳层、错误 404 和权限闪烁，不采用。
- **C**：所有页面继续共用顶部导航，只在管理页面内容内加一个侧栏。无法满足独立管理 Header/Footer，
  账号与返回动作会重复，也不能形成稳定后台空间，不采用。
- **D**：完全手写 Sidebar 与 Drawer。可控但重复解决 shadcn/ui 已提供的组合和无障碍细节；当前项目已
  选择 shadcn + Base UI，因此只在官方组件无法满足全宽 Header/Footer 时做最小布局覆盖，不重写交互原语。

## 风险与重审条件

主要风险是 shadcn Sidebar 默认采用“侧栏在最外层、Header 只在右侧”的示例结构，与本项目要求的全宽
Header/Footer 不同。实施时复用其 Provider/Menu/Sub/Sheet 行为，并由 `AdminAppShell` 的 CSS Grid 决定
上中下结构；若必须大幅改写组件内部定位，应先做一个最小 story 验证，再决定是否退回更薄的项目包装。

若设计系统现有 sidebar alias 无法表达必要状态、需要新增 token/manifest，或未来出现三级导航、多个
管理员角色、可调整宽度/持久化折叠，应暂停 TASK-028，升级本 DESIGN/DECISION 和写入范围。当前不以
这些未来需求增加复杂度。

## 变更记录

- 2026-08-28：完成路由树、双 Shell、shadcn/Base UI 组合、状态与迁移方案，提交人工审核。
- 2026-08-28：按用户确认补充 `/admin`、`/admin/dashborad` 共用空 Dashboard 的路由与组件方案。
- 2026-08-28：状态变更：draft → review。原因：路由树、双 Shell、shadcn/Base UI 组合、迁移与验证方案已完成，提交用户审核
- 2026-08-28：状态变更：review → approved。原因：用户确认 Dashboard 双入口和双 Shell 技术方案并允许实施
