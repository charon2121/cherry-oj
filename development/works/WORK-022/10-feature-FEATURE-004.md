---
id: "FEATURE-004"
type: "feature"
title: "微调双端应用布局页脚"
status: "approved"
work: "WORK-022"
owners: ["codex/root"]
depends_on: ["FEATURE-003"]
related: ["WORK-020", "VERIFY-020"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# FEATURE-004：微调双端应用布局页脚

## 为什么做

WORK-020 的双端壳层已通过自动化检查，但人工复核认为页脚层级需要收敛：管理端不需要页脚；用户端
页脚只承担结构和简短收尾，不应在视觉上成为独立区域。本功能以最小变化修正这两个边界。

## 使用者

- 普通用户与访客：浏览首页、登录、账号及未来业务页面时，页面尾部保持低噪声。
- 管理员：在 Dashboard 和管理页面中获得更多垂直工作空间，不被无功能页脚打断。
- Web 开发者：继续通过用户端或管理端 App Shell 自动获得统一结构，无需页面自行处理页脚。

## 入口

所有用户端页面和 `/admin/*` 管理页面自动应用，不增加新入口或设置项。

## 正常流程

1. 用户进入普通页面，看到 Header、Main 和语义 Footer。
2. Main 与 Footer 处于同一页面表面，中间没有边框、独立底色或阴影分区。
3. 管理员进入 `/admin`、`/admin/dashborad` 或 `/admin/users`，只看到 Header 与中间的 Sidebar + Content。
4. 页面内容短时两端仍合理占满视口；内容长时按原有滚动方式访问，不遮挡导航或主要内容。

## 业务规则

- REQ-001：管理端 App Shell 不渲染 Footer，也不保留只为 Footer 服务的第三行、占位高度或底部边界。
- REQ-002：用户端继续使用唯一的 `header → main → footer` 结构，Footer 继续作为 `contentinfo` landmark。
- REQ-003：用户端 Footer 的外层表面必须与 Main 一致，不使用顶部分隔线、独立背景、抬升阴影或其它
  将其表现为独立面板的样式。
- REQ-004：用户端 Footer 保留 WORK-020 已确认的简短内容，不在本调整中新增、删除或改写业务链接。
- REQ-005：短页面中用户 Footer 仍位于视口底部；管理端短页面由 Main 区域自然填满 Footer 移除后的空间。
- REQ-006：不得改变 Dashboard 双入口、管理侧栏、移动 Sheet、身份状态、权限保护和业务请求行为。

## 边界与失败

- 320px 下 Footer 内容可以按原有方式换行，但不能造成横向溢出。
- 管理端不存在 Footer 后，Sidebar 与 Content 仍需共享完整中间区域高度；不得留下空白网格行。
- 用户端 Footer 的语义结构不能因“取消样式区分”被一并删除。
- 若需要修改设计系统 token 或组件合同才能完成，应停止 TASK-030 并升级范围，而不是增加页面级例外。

## 要求

- REQ-007：两个登记主题中 Main 与用户 Footer 都必须自然使用同一页面背景，不按 theme id 写条件分支。
- REQ-008：保持 skip link、唯一 Main、导航名称、键盘操作、reduced-motion 与 forced-colors 行为不变。
- REQ-009：不新增网络请求、依赖、持久化状态、原始颜色值或设计系统 allowlist。

## 验收标准

- AC-001：访问任意管理页面，页面存在一个 Header 和一个 Main，不存在 Footer/`contentinfo`，底部没有
  为 Footer 保留的空白行。
- AC-002：访问用户端短页面，存在 Header、Main、Footer；Footer 位于视口底部且与 Main 计算后的背景
  相同，外层没有顶边框和阴影。
- AC-003：在 `cherry-black`、`pure-white` 及 320px 中重复检查 AC-001/AC-002，无横向溢出或内容遮挡。
- AC-004：WORK-020 的 Dashboard 双入口、管理子菜单、移动抽屉焦点恢复及登录/权限/用户管理 E2E
  继续通过，且没有新增业务 API 请求。
- AC-005：设计系统源码门禁、组件测试、生产构建、Storybook 构建和 Playwright 全部通过。

## 不做什么

- 不移除用户端 Footer 或修改其中现有文案。
- 不为管理端增加新的底部操作栏、状态栏或悬浮区域。
- 不调整 Header、Sidebar、内容宽度、路由、权限、主题 token 或业务页面内容。

## 待确认项

无阻塞产品未知；上述两条页脚调整来自用户复核。文档仍需在实施前获得明确确认和执行授权。

## 变更记录

- 2026-08-28：根据 WORK-020 复核意见定义双端 Footer 微调，提交人工审核。
- 2026-08-28：状态变更：draft → review。原因：页脚微调的产品规则与验收标准已完整，提交用户审核
- 2026-08-28：状态变更：review → approved。原因：用户明确确认页脚微调产品定义并允许执行
