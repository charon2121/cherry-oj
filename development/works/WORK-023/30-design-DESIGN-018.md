---
id: "DESIGN-018"
type: "design"
title: "设计双端导航栏与导航功能组件"
status: "approved"
work: "WORK-023"
owners: ["codex/root"]
depends_on: ["FEATURE-005", "EXPERIENCE-011"]
related: ["WORK-020", "WORK-022"]
implements: []
verifies: []
tags: []
created_at: "2026-08-29"
updated_at: "2026-08-29"
---



# DESIGN-018：设计双端导航栏与导航功能组件

## 背景

WORK-020 建立 `SiteAppShell`、`AdminAppShell`、`AdminNavigation` 与 `SessionActions`，WORK-022 移除管理
Footer 并收敛用户 Footer。FEATURE-005 与 EXPERIENCE-011 进一步要求导航只显示已交付入口、统一账号
动作、复用桌面/移动定义，并为后续页面提供稳定接入点。

## 目标与限制

- 在不改变路由、Session/API、权限和页面骨架的前提下，重组现有导航与账号组件。
- 桌面和移动端共享导航模型，业务可见性与当前态有单一来源。
- 优先采用项目 base-nova 配置的 shadcn/ui 组件并覆盖为现有设计系统语义；不得引入 Radix。
- 只消费现有 token，不修改 `apps/web/design-system`、主题合同或生成资产。
- 本任务只接入当前真实路由：首页、登录、改密、管理 Dashboard 和用户账号。

## 整体方案

```text
TanStack Router location ─┐
                          ├─ navigation model / active matcher
TanStack Query Session ───┘          │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
           SitePrimaryNav     AdminNavigation     AccountMenu
                    │                │                │
              desktop / Sheet  Sidebar / Sheet    both Headers
```

Shell 只负责布局与装配；导航模型描述可达项；渲染组件负责语义和交互；Session/Router 仍是身份和位置的
唯一真源。桌面与移动容器不同，但消费同一模型，不复制标签、路径、顺序或角色条件。

## 模块与数据

- `app-brand.tsx`：从现有 `BrandLink` 提取稳定品牌入口，供两端 Header 使用；管理端通过旁注表达空间。
- `site-navigation.ts`：定义当前用户端叶子项及稳定顺序。当前只有首页；后续路由在各自 WORK 中显式加入。
- `site-primary-navigation.tsx`：以语义 `nav` + TanStack `Link` 渲染横向或 Sheet 版本，接收
  `onNavigate`，不读取 Session、不发请求。
- `admin-navigation.ts`：以只读配置描述 Dashboard 与分组/子项，图标来自 Lucide；不保存展开状态。
- `admin-navigation.tsx`：把配置映射到现有 shadcn Sidebar + Collapsible，结合 pathname 计算当前项与父组。
- `account-menu.tsx`：替代横向增长的 `SessionActions` 登录后部分；继续拥有 Session query、logout mutation
  与缓存更新。pending/error/visitor 使用直接状态，authenticated 使用 Dropdown Menu。
- `site-app-shell.tsx` / `admin-app-shell.tsx`：只装配品牌、导航、账号和现有 Main/Footer，不包含业务判断。
- `components/ui/dropdown-menu.tsx`：从项目 shadcn registry 的 base-nova 版本生成，再逐项映射 Cherry OJ
  token、尺寸、focus、danger 与 disabled 合同，并补 Story/Test。

导航模型只包含稳定 id、label、typed destination、匹配策略、可选 Lucide icon 和 children。不从后端下载
菜单，不存数据库，也不创建远程 feature flag。

## 接口与状态

- Router：TanStack Router 提供 pathname/search；首页精确匹配，Dashboard 显式匹配 `/admin` 与
  `/admin/dashborad`，用户账号匹配 `/admin/users` 且忽略合法 search 参数。
- Session：继续消费 `sessionQueryOptions()`；角色只决定 ADMIN 入口可见性，授权仍由路由 guard 执行。
- 退出：继续由 `useMutation` 管理 pending/error；成功或 401 时写入匿名 Session 并进入登录页。
- 菜单：Dropdown/Sheet 由 Base UI/shadcn 管理开关与焦点；Shell 只为受控 Sheet 保存局部 open。
- 管理分组：`activeDescendant || userOpened`。当前叶子所属父组保持展开，非当前组允许本地开合，不持久化。
- `onNavigate`：移动导航选择叶子后通知 Shell 关闭 Sheet；桌面不传。
- 面包屑只保留后续接口方向：由具体页面给出层级数组并使用 shadcn Breadcrumb，本任务不创建无消费者组件。

## 安全与失败

导航可见性不是权限边界；USER 即使通过地址或 DOM 修改尝试进入管理页面，仍由现有 ADMIN guard 拒绝。
Session 错误不降级成访客。导航标签和路径来自源码常量，不渲染服务端 HTML；用户名只作为 React 文本。
退出失败保留 Session，错误通过现有 `authErrorMessage` 映射，不展示响应体、header 或敏感信息。

## 监控与部署

不增加遥测、环境变量、API 或部署步骤。验证使用组件测试、Storybook 与现有 Playwright；生产仍由现有
静态 Web 构建交付。导航点击分析不在本工作范围，未来如需要必须先定义隐私与指标合同。

## 迁移与兼容

原地小步迁移：先加入 Dropdown Menu 覆盖组件与测试，再提取导航模型/品牌，最后替换两个 Shell 装配并
更新 E2E。现有路由、查询参数、Header 高度、Sidebar 宽度、Footer 和 Main landmark 不变。

## 备选方案

1. **继续横向平铺账号动作。** 代码最少，但角色、改密、错误和未来入口会持续挤压 Header，拒绝。
2. **用 shadcn Navigation Menu 承载平面链接。** 更适合带内容面板的多级导航；当前 `nav + Link` 更轻。
3. **从后端动态下发菜单。** 会引入新契约、缓存、失败状态与权限误解，当前单角色菜单不需要，拒绝。
4. **把面包屑放进 Header。** 会让全局 Header 随层级变化并挤压账号区；改为 Main 内按页面需要使用。

## 风险与重审条件

- shadcn 输出若依赖 Radix 或需要新增未登记 token，停止实施并升级 DESIGN，不手工绕过门禁。
- 类型化路由与通用配置难以同时保持推断时，优先明确的小型 typed arrays，不以 `any`/断言换抽象。
- 出现三级导航、多角色差异菜单、租户切换、动态 feature flag、命令面板、通知、主题选择或导航偏好
  持久化时重新评估模型和状态边界。
- 题库、提交或题目管理页面交付时，在对应 WORK 中更新导航；本设计不授权提前创建这些路由。

## 变更记录

- 2026-08-29：完成导航模型、组件拆分、状态来源、shadcn 覆盖、迁移与备选方案草案，提交审核。
- 2026-08-29：状态变更：draft → review。原因：组件拆分、状态来源、shadcn 覆盖与迁移边界已完整，提交用户审核
- 2026-08-29：状态变更：review → approved。原因：用户明确批准 WORK-023 并授权执行 TASK-031
