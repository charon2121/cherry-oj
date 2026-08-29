---
id: "TASK-031"
type: "task"
title: "设计双端导航栏与导航功能组件"
status: "done"
work: "WORK-023"
owners: ["codex/root"]
depends_on: ["FEATURE-005", "EXPERIENCE-011", "DESIGN-018", "TASK-028", "TASK-030"]
related: ["WORK-020", "WORK-022", "VERIFY-020", "VERIFY-022"]
implements: ["FEATURE-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/design-system.md", "docs/design-system/README.md", "apps/web/TOOLCHAIN.md", "apps/web/components.json", "apps/web/design-system", "apps/web/src/app/shells", "apps/web/src/components/ui", "apps/web/src/features/auth", "apps/web/src/routes", "apps/web/e2e", "development/works/WORK-020", "development/works/WORK-022", "development/works/WORK-023"]
write_paths: ["apps/web/package.json", "apps/web/package-lock.json", "apps/web/src/app/shells", "apps/web/src/components/ui/dropdown-menu.tsx", "apps/web/src/components/ui/dropdown-menu.test.tsx", "apps/web/src/components/ui/dropdown-menu.stories.tsx", "apps/web/e2e", "development/works/WORK-023"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/generated", "apps/web/public/generated", "apps/web/src/features", "apps/web/src/routes", "apps/server", "apps/judge-engine", "contracts", "docs", "development/works（WORK-023 除外）"]
created_at: "2026-08-29"
updated_at: "2026-08-29"
---




# TASK-031：设计双端导航栏与导航功能组件

## 任务目标

在现有双端 Shell 上实现 FEATURE-005 的导航组件体系：提取共享品牌与类型化导航定义，将登录后账号动作
收敛为符合设计系统的 shadcn Dropdown Menu，使用户端桌面/移动主导航与管理端 Sidebar/Sheet 共享各自
模型，并完成组件、Storybook 和 E2E 回归。

## 依据

实现 FEATURE-005 的 REQ-001～REQ-017、AC-001～AC-007；交互以 EXPERIENCE-011 为准，模块和状态边界以
DESIGN-018 为准。TASK-028/TASK-030 提供已交付 Shell、导航和 Footer 基线。

## 可查看范围

以 front matter 的 `read_paths` 为准。实施前运行 `scripts/work context TASK-031`，完整阅读设计系统与 Web
工具链，并核对工作树中 WORK-021 等不属于本任务的本地修改。

## 可修改范围

以 front matter 的 `write_paths` 为准。Shell 目录可新增品牌、用户导航、管理导航模型与账号菜单文件；
UI 目录只允许新增/覆盖 Dropdown Menu 及其测试和 Story；E2E 只调整导航、身份状态与回归场景。仅当
shadcn base-nova 生成需要时修改 Web 依赖锁文件。

## 禁止修改

不得创建题库/提交等新路由，不得修改 Session/API/权限业务代码、设计系统真源、主题、生成资产、后端
或其它 WORK 文档；不得以 allowlist 或 raw CSS 绕过门禁。

## 依赖

FEATURE-005、EXPERIENCE-011 与 DESIGN-018 必须先获得用户批准；TASK-028/TASK-030 的现有行为必须作为
兼容基线。未收到后续明确执行授权前，TASK-031 保持 `todo`。

## 产出

- 双端 Header 的稳定装配：共享品牌、用户主导航、管理空间动作与统一账号区域。
- 类型化用户/管理导航模型；管理 Sidebar 与移动 Sheet 复用同一菜单定义和当前态算法。
- shadcn/ui Base UI Dropdown Menu 的 Cherry OJ 覆盖、组件测试与 Storybook 状态。
- 访客、USER、ADMIN、首次改密、Session/退出失败、管理子菜单和 320px 的测试证据。
- VERIFY-023 的实际检查、范围和人工视觉验收记录。

## 完成标准

- [x] 用户端品牌、当前可用主导航与账号区域职责明确，只显示真实可达入口。
- [x] 登录用户账号菜单支持改密、ADMIN 管理入口、退出 pending/error；访客和 Session 错误不回退。
- [x] 管理 Header/Sidebar 无重复入口；Dashboard 双地址与用户账号当前态准确，二级分组键盘可用。
- [x] 桌面/移动菜单由共享模型生成，320px、长用户名、双主题和 200% 缩放无溢出或关键动作丢失。
- [x] Dropdown Menu 来自 shadcn base-nova 路线并完全映射现有 token；无 Radix、raw 值或设计系统修改。
- [x] 路由、API、权限、Main/Footer 和业务页面行为不变，修改严格位于 `write_paths`。
- [x] `npm run check`、`npm run build`、`npm run storybook:build`、`npm run test:e2e` 与文档校验通过。

## 验证

在 `apps/web` 执行 `npm run format`、`npm run check`、`npm run build`、`npm run storybook:build` 和
`npm run test:e2e`。至少验证：

1. Dropdown Menu 键盘、焦点、disabled、danger、错误 alert 和关闭恢复；
2. 访客/USER/ADMIN/首次改密/Session error/退出 error 的 Header 可见项；
3. `/admin`、`/admin/dashborad`、`/admin/users?page=1` 的当前项和父组展开；
4. 1440px、320px、两个主题、200% 缩放、长用户名、forced-colors、reduced-motion；
5. 登录、改密、403、404、Dashboard、用户管理、用户 Footer 与管理无 Footer 的既有回归。

仓库根目录执行 `scripts/work check` 与文档链接校验，结果按事实写入 VERIFY-023。

## 风险

主要风险是账号动作被菜单隐藏、当前态匹配错误、共享模型失去类型推断，以及 shadcn 输出越过 Base UI/
token 边界。若需要新业务路由、设计 token、第三层菜单、全局状态或 Session/API 变化，立即停止并升级
FEATURE/DESIGN/TASK，不在本任务内扩大范围。

## 执行记录

- 2026-08-29：创建任务。
- 2026-08-29：补齐导航模型、账号菜单、shadcn 覆盖、响应式、测试与禁止范围；等待文档审核和授权。
- 2026-08-29：状态变更：todo → ready。原因：上游功能、体验与技术方案已获用户批准
- 2026-08-29：状态变更：ready → doing。原因：用户明确授权执行 TASK-031
- 2026-08-29：检查 shadcn base-nova Dropdown Menu 注册表实现，并以现有 Base UI 与语义 token 完成 Cherry OJ 覆盖。
- 2026-08-29：完成共享品牌、用户主导航、管理导航模型、统一账号菜单及桌面/移动端 Shell 装配。
- 2026-08-29：补齐组件、导航模型、Storybook 与 E2E 场景；自动化构建和本地浏览器视觉检查通过。
- 2026-08-29：状态变更：doing → done。原因：导航组件体系、测试与本地视觉检查已完成
