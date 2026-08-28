---
id: "VERIFY-020"
type: "verify"
title: "搭建用户端与管理端应用布局"
status: "review"
work: "WORK-020"
owners: ["codex/root"]
depends_on: ["TASK-028"]
related: ["WORK-022", "VERIFY-022"]
implements: []
verifies: ["FEATURE-003", "TASK-028"]
tags: []
result: "pending"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---


# VERIFY-020：搭建用户端与管理端应用布局

## 验证对象

TASK-028 交付的用户端/管理端 Shell、Dashboard 双入口、路由归属、管理子菜单、移动导航，以及现有身份
与用户管理回归。

## 对应要求

覆盖 FEATURE-003 的 REQ-001～REQ-017 与 AC-001～AC-008，重点验证：

- 壳层互斥、语义 landmark 与短页 Footer；
- `/admin` 与 `/admin/dashborad` 共用 Dashboard 组件、当前导航且没有业务请求；
- 管理端两列、二级菜单、当前项和全宽 Header/Footer；
- 320px Sheet、键盘、焦点恢复、双主题与无横向溢出；
- Session/权限/登录/改密/用户管理不回退；
- shadcn 组件已受 Cherry 设计系统约束且没有修改设计系统真源。

## 检查与结果

验证环境：macOS，Node `v26.3.0`，npm `12.0.2`，Playwright Chromium（项目锁定版本）。2026-08-28
实际执行：

- `npm run check`：退出码 0；设计系统生成物/源码门禁及自测试、API 生成一致性、Prettier、ESLint、
  TypeScript 与 Vitest 全部通过。Vitest 为 24 个测试文件、92 个测试通过。
- `npm run build`：退出码 0；TypeScript 与 Vite 生产构建通过，Router 插件生成的路由树包含 `_site`、
  `admin`、`/admin`、`/admin/dashborad` 与 `/admin/users` 的预期归属。
- `npm run storybook:build`：退出码 0；Sidebar 展开/键盘场景和 Sheet 默认/320px 场景进入静态产物。
- `npm run test:e2e`：退出码 0；Chromium 20/20 通过，覆盖双 Dashboard 地址、现有身份/权限链路、
  当前项、移动管理导航、Escape/焦点恢复、双主题 320px、forced-colors 与 reduced-motion。
- 本地浏览器检查：1440×900 用户端 Header/Main/Footer 和短页 Footer 布局正常；320×720 下移动导航
  Sheet 无横向溢出（实测差值 0px），标题、关闭按钮与当前项均可见。管理端桌面/移动行为由带认证 mock
  的 Playwright 场景检查。

## 未通过项

暂无自动化未通过项。人工复核确认原实现可运行，但提出两项产品调整：管理端移除 Footer，用户端 Footer
取消与 Main 的视觉分区。调整已转入 WORK-022，因此本 VERIFY 保持 `review` / `pending`，不签署旧形态。

## 范围检查

`git status` 与 `git diff --check` 已核对：实现改动只在 TASK-028 允许的 `apps/web/src/app`、
`apps/web/src/components/ui`、`apps/web/src/routes`、生成路由树和 `apps/web/e2e`；WORK-020 及其项目级索引
为流程记录。`apps/web/design-system`、`docs`、服务端、契约和其它 WORK 均未修改，diff 无空白错误。

## 遗留问题

Dashboard 按用户指定保留 `/admin/dashborad` 拼写；若未来提供规范拼写 `/admin/dashboard`，需另行定义
兼容与重定向策略。

## 剩余风险

原定义下的自动化与本地视觉检查未发现阻断风险；但最终产品形态以 WORK-022 经确认后的定义为准。

## 结论

原实现与自动化验证通过，但人工复核要求调整 Footer；由 WORK-022 完成后重新验证，在此之前不把本
VERIFY 置为 `approved/pass`。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：自动化与本地视觉检查全部通过，提交独立人工验收
- 2026-08-28：记录人工复核意见并关联 WORK-022；原实现保留为可追溯基线，不作为最终产品形态签署。
