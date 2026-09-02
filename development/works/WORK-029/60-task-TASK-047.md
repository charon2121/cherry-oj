---
id: "TASK-047"
type: "task"
title: "新增页面主题切换入口"
status: "done"
work: "WORK-029"
owners: ["codex/root"]
depends_on: ["FEATURE-008", "EXPERIENCE-015"]
related: []
implements: ["FEATURE-008#REQ-001", "FEATURE-008#REQ-002", "FEATURE-008#REQ-003", "FEATURE-008#REQ-004", "FEATURE-008#REQ-005", "FEATURE-008#REQ-006", "FEATURE-008#REQ-007", "FEATURE-008#REQ-008", "FEATURE-008#REQ-009", "FEATURE-008#AC-001", "FEATURE-008#AC-002", "FEATURE-008#AC-003", "FEATURE-008#AC-004", "FEATURE-008#AC-005", "FEATURE-008#AC-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/design-system.md", "docs/engineering/typescript.md", "apps/web/TOOLCHAIN.md", "apps/web/design-system/themes.manifest.json", "apps/web/src/generated/design-system/themes.ts", "apps/web/src/lib/theme", "apps/web/src/app/shells", "apps/web/src/components/ui/icon-button.tsx", "apps/web/e2e", "development/works/WORK-015", "development/works/WORK-018", "development/works/WORK-023", "development/works/WORK-029"]
write_paths: ["apps/web/src/app/shells/theme-switcher.tsx", "apps/web/src/app/shells/theme-switcher.test.tsx", "apps/web/src/app/shells/theme-switcher.stories.tsx", "apps/web/src/app/shells/site-app-shell.tsx", "apps/web/src/app/shells/admin-app-shell.tsx", "apps/web/e2e", "development/works/WORK-029"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/generated", "apps/web/public/generated", "apps/web/src/lib/theme", "apps/web/src/components/ui", "apps/web/src/features", "apps/web/src/routes", "apps/server", "apps/judge-engine", "contracts", "docs", "development/works（WORK-029 除外）"]
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# TASK-047：新增页面主题切换入口

## 任务目标

在现有双端 Shell 中新增一个 manifest 驱动的主题切换按钮，复用主题 Provider 完成即时切换、本地持久化
与异常播报，并用组件、Storybook 和浏览器测试证明它在用户端、管理端和窄屏场景可用。

## 依据

实现 FEATURE-008 的 REQ-001～REQ-009 与 AC-001～AC-006；入口、文案、目标图标、焦点和异常体验以
EXPERIENCE-015 为准。现有主题合同、ThemeProvider/运行时以及双端导航布局作为不可改动的兼容基线。

## 可查看范围

以 front matter 的 `read_paths` 为准。实施前运行 `scripts/work context TASK-047`，重点确认生成主题清单、
ThemeProvider API、双端页头空间和 WORK-023 导航验收边界。

## 可修改范围

以 front matter 的 `write_paths` 为准。Shell 目录可新增主题切换组件、单元测试与 Story，并只在两个 Shell
中完成装配；E2E 可增加主题入口、持久化和响应式场景；本 WORK 文档用于记录状态和实际证据。

## 禁止修改

不得修改主题 manifest、主题 CSS、合同、生成文件、首屏脚本、主题运行时、共享 UI 基础组件、业务路由/
功能/API、后端、跨语言契约、长期设计文档或其他 WORK。不得加入 raw 颜色、literal theme id、独立
storage key、系统主题分支或源码门禁 allowlist。

## 依赖

FEATURE-008 与 EXPERIENCE-015 必须先通过人工意图闸；WORK-018 已交付的主题运行时和 WORK-023 已交付
的双端页头作为实现基础。收到用户后续明确执行授权前，TASK-047 保持 `todo`。

## 产出

- `ThemeSwitcher`：从生成 registry 计算下一个主题，使用现有 `useTheme` 切换并播报结果。
- 用户端与管理端页头装配，不改变原导航、账号菜单和身份错误状态。
- 组件测试：主题循环、动态名称/图标、持久化成功与失败、单主题防御行为。
- Storybook：两个当前主题、存储失败和 320px 页头状态。
- E2E：双端可达、键盘切换、刷新持久化、跨标签页同步与窄屏无溢出。
- VERIFY-030 中逐条记录检查、范围和遗留风险。

## 完成标准

- [x] 页头按钮在用户端和管理端、访客和登录态均可达，不改变当前 URL 或页面交互状态。
- [x] 按钮按生成 registry 顺序计算目标主题，不硬编码 ID；当前双主题可稳定往返，未来新增登记主题可循环。
- [x] 切换即时生效并沿用现有持久化、首屏恢复和跨标签页同步；写入失败仍生效且有礼貌级状态播报。
- [x] 图标、动态可访问名称、焦点、键盘、320px、200% 缩放、forced-colors 和 reduced-motion 满足
  EXPERIENCE-015，导航与账号入口无关键溢出。
- [x] 未修改设计系统、生成文件、主题运行时、共享 UI、路由、API、后端、契约、docs 或其他 WORK。
- [x] 定向测试、`npm run check`、`npm run build`、`npm run storybook:build`、`npm run test:e2e` 与
  `scripts/work check` 通过，实际结果写入 VERIFY-030。

## 验证

在 `apps/web` 先运行主题切换组件与主题运行时的定向 Vitest，再执行 `npm run format`、`npm run check`、
`npm run build`、`npm run storybook:build` 和 `npm run test:e2e`。至少人工或浏览器验证：

1. 访客/USER/ADMIN 的用户端页头，以及 ADMIN 管理端页头；
2. 黑色→白色→黑色、键盘激活、刷新恢复、用户端/管理端共用偏好和另一标签页同步；
3. 本地存储写入失败、未知旧值和清空偏好的安全行为；
4. 1440px、320px、200% 缩放、两个主题、forced-colors 和 reduced-motion；
5. 导航、账号菜单、登录、改密、管理入口和页面局部状态的现有回归。

仓库根目录执行 `scripts/work check`，并用 `git diff --check` 与范围清单确认没有越界修改。

## 风险

主要风险是按钮挤压窄屏页头、图标表达当前状态而名称表达目标导致歧义、重复主题状态源，或为了测试去
修改生成资产/运行时。若发现必须增加设置页、服务端偏好、系统主题、设计 token、共享 UI 接口或新
storage 语义，立即停止并升级 FEATURE/TASK，不在本任务内扩大范围。

## 执行记录

- 2026-09-02：创建任务。
- 2026-09-02：补齐 manifest 驱动的页头入口、浏览器偏好、可访问性、测试范围与禁止边界；等待文档审核和执行授权。
- 2026-09-02：状态变更：todo → ready。原因：意图闸已由用户签署，依赖、写入范围与完成标准均明确
- 2026-09-02：状态变更：ready → doing。原因：用户已明确授权开始实施主题切换功能
- 2026-09-02：新增 ThemeSwitcher、组件测试和 Storybook 状态，并装配到用户端与管理端全局页头。
- 2026-09-02：补充刷新持久化、跨标签页、存储失败、forced-colors、双主题和 320px Playwright 场景；
  最窄管理页头隐藏装饰性返回箭头后无横向溢出，文字动作保持完整。
- 2026-09-02：使用项目要求的 Node 24.19.0 完成 Web 全量检查、生产构建、Storybook 和 29 项 E2E 回归。
- 2026-09-02：状态变更：todo → ready。原因：意图闸已由用户签署，依赖、写入范围与完成标准均明确
- 2026-09-02：状态变更：ready → doing。原因：用户已明确授权开始实施主题切换功能
- 2026-09-02：状态变更：doing → done。原因：主题切换控件、双端页头装配、组件与浏览器测试均已完成并通过
