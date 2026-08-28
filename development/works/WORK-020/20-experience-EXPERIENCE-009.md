---
id: "EXPERIENCE-009"
type: "experience"
title: "搭建用户端与管理端应用布局"
status: "approved"
work: "WORK-020"
owners: ["codex/root"]
depends_on: ["FEATURE-003"]
related: ["EXPERIENCE-008"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# EXPERIENCE-009：搭建用户端与管理端应用布局

## 体验类型

面向普通用户和管理员的全局导航与页面结构体验，同时约束后续 Web 页面如何进入一致的应用骨架。重点是
让用户随时知道自己位于普通答题空间还是管理空间，并在桌面和窄屏上都能可靠到达目标页面。

## 入口与主流程

### 用户端

1. Header 左侧是 Cherry OJ 品牌，随后是当前已存在或已确认可用的主导航；右侧显示登录或账号动作。
2. 当前导航项使用 `aria-current`、稳定文字和中性选中面共同表达；品牌 Cherry 色只用于品牌与主要动作。
3. Main 占据剩余高度，业务页继续使用现有 Container/Section 组织内容，不被 Header 或 Footer 覆盖。
4. Footer 以单行或窄屏换行形式显示 Cherry OJ 与简短产品说明；不放虚构链接或运行状态。

### 管理端

1. Header 显示品牌、`管理中心`、移动导航开关、`返回用户端` 和账号动作，让管理员明确当前空间。
2. Main 桌面为左侧 Sidebar 与右侧 Content。Sidebar 的分组标签退后，叶子链接承担页面跳转。
3. `Dashboard` 是一级叶子项；访问 `/admin` 或 `/admin/dashborad` 都显示同一个只含标题的空页面，并保持
   Dashboard 当前状态。
4. “账号管理”作为可展开一级项，子项“用户账号”指向现有页面；进入子项时父项默认展开。
5. Content 只承载页面标题、表单、表格和分页；原“返回首页”内容链接移除，由全局 Header 提供返回路径。
6. Footer 横跨应用底部；Sidebar 内容过长时自身滚动，不能把 Footer 或右侧内容推到不可达位置。

### 窄屏

- 用户端主导航收进 Header 的菜单面板；管理端 Sidebar 变成由“打开管理导航”按钮触发的抽屉。
- 打开后先聚焦导航上下文，Escape 或选择链接可关闭；关闭后焦点回到触发按钮或新页面标题。
- 主内容始终单列占满可用宽度，不能为保留桌面侧栏而把表格和表单压到不可用。

## 异常状态

- Session 检查中：壳层尺寸保持稳定，账号区显示“正在检查登录状态…”，不提前闪出管理入口。
- Session 查询失败：账号区显示“登录状态加载失败，重试”，普通内容继续可读；管理路由由 guard 阻止
  未确认身份内容渲染。
- 匿名：用户端显示登录；管理端访问被送往登录并保留安全回跳。
- USER 越权：进入 403 用户端页面，不留下管理 Header、Sidebar 或后台页面标题。
- 首次改密：继续优先进入 `/account/password`，用户端 Header 不提供可绕过的受保护入口。
- 当前子菜单目标不存在：使用路由级 404，并保留所属壳层与返回路径；不能显示空白右侧内容。
- 长内容：页面按文档流增长，Footer 后移；Sidebar 独立滚动，浮层关闭后恢复焦点。

## 交互与文案

- 用户端主导航：`首页`；后续只在真实路由交付后增加 `题库`、`我的提交`。
- 管理端空间标识：`管理中心`；返回动作：`返回用户端`。
- 管理导航：分组 `管理`，一级叶子 `Dashboard`，一级触发 `账号管理`，子项 `用户账号`。
- 移动触发：用户端 `打开主导航`，管理端 `打开管理导航`；不能只显示无名称的 hamburger 图标。
- 子菜单 Chevron 仅辅助表示展开方向，`aria-expanded` 与可见子项才是状态真源。
- Footer：`Cherry OJ` 与 `Focused Workspace` 的简短组合；不显示年份依赖文案、服务健康或版权承诺。
- 动效只用于菜单/侧栏 150–200ms 的显隐与 Chevron 旋转；reduced-motion 下立即完成。

## 可访问性

- 页面顶部提供首个可聚焦的“跳到主要内容”链接，目标是当前壳层唯一的 `main`。
- Header、Main、Sidebar navigation、Content 和 Footer 使用语义 landmark；每个导航都有不同的可访问名称。
- Tab 顺序与视觉顺序一致；展开按钮使用原生 button，链接使用 TanStack Link，不用可点击 `div`。
- 当前页面使用 `aria-current="page"`；父项展开使用 `aria-expanded`，且文字权重/结构与中性 surface 共同
  表达当前状态，颜色不是唯一线索。
- 320px、200% 缩放和长中文下不裁切菜单按钮、当前项、账号操作或页面标题；抽屉打开后焦点留在其中，
  背景不可误操作。
- 所有交互复用设计系统 2px focus 与 offset；forced-colors 使用系统焦点，必要图标不低于 3:1。

## 调试与恢复

Storybook 分别展示用户端访客/USER/ADMIN、管理端展开/折叠子菜单、桌面/320px 和双主题状态；组件测试
以 landmark、可访问名称、`aria-current`、`aria-expanded` 与焦点恢复定位问题。路由 E2E 检查每个 URL
只进入一套壳层。若迁移导致回归，可按路由文件和 AppShell 组件回退，不涉及 API、数据或部署迁移。

## 变更记录

- 2026-08-28：完成双端桌面/窄屏流程、状态、文案与可访问性草案，提交人工审核。
- 2026-08-28：补充 `/admin` 与 `/admin/dashborad` 共用的空 Dashboard 体验，并获用户批准执行。
- 2026-08-28：状态变更：draft → review。原因：桌面与窄屏主流程、状态、文案和可访问性体验已完成，提交用户审核
- 2026-08-28：状态变更：review → approved。原因：用户确认双端布局、空 Dashboard 与导航体验并允许实施
