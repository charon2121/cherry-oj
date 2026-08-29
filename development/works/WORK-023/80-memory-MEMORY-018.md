---
id: "MEMORY-018"
type: "memory"
title: "设计双端导航栏与导航功能组件"
status: "draft"
work: "WORK-023"
owners: ["codex/root"]
depends_on: ["VERIFY-023"]
related: ["MEMORY-016", "MEMORY-017", "WORK-020", "WORK-022"]
implements: []
verifies: []
tags: []
created_at: "2026-08-29"
updated_at: "2026-08-29"
---

# MEMORY-018：设计双端导航栏与导航功能组件

## 背景

Cherry OJ 的双端壳层已稳定，但导航会随题库、提交和运营页面增长。WORK-023 记录导航职责和扩展规则；
只有在实现验证通过后，本记忆才适合批准为长期事实。

## 决定与原因

实现决定：用户端 Header 承载品牌、已交付主导航和账号菜单；管理端 Header 承载空间切换与账号，
Sidebar 承载 Dashboard 和最多二级的业务导航。桌面与移动端共享菜单定义，导航可见性不是权限边界。

账号动作集中是为了控制 Header 宽度并统一状态；未来入口“已交付才显示”是为了避免死链接和产品承诺
漂移；面包屑留在 Main 是为了保持全局 Header 稳定。

## 尝试与教训

WORK-020 的最小导航证明了 Sidebar/Collapsible/Sheet 与双端路由结构，但横向 SessionActions 会随角色和
状态增长；未来不能继续逐个追加链接。平面主导航使用语义链接比多级 Navigation Menu 更合适；shadcn
优先不等于无条件使用更重的组件。账号菜单采用 shadcn base-nova 的 Base UI 路线并覆盖语义 token；
Base UI disabled 菜单项仍可被键盘读取但不能触发，这一行为应在测试中按无激活结果断言。

## 已知问题

当前只有首页和少量管理路由，题库、提交、搜索、通知和主题入口均未交付。本 WORK 不解决这些业务缺口，
也不修正既有 `/admin/dashborad` 拼写。

## 重新考虑条件

出现三级导航、多角色差异菜单、租户切换、动态 feature flag、命令面板、未读通知、用户主题选择、持久化
侧栏偏好，或移动端 Header 无法容纳品牌/导航/账号三个关键入口时重新设计。

## 变更记录

- 2026-08-29：记录待审核的双端导航职责、渐进入口和组件选择原则。
- 2026-08-29：记录已实现的共享导航模型、统一账号菜单及 Base UI Dropdown Menu 行为。
