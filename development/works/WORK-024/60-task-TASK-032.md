---
id: "TASK-032"
type: "task"
title: "重新设计登录页视觉与体验"
status: "done"
work: "WORK-024"
owners: ["codex/root"]
depends_on: ["FEATURE-006", "EXPERIENCE-012"]
related: ["WORK-013", "WORK-019", "WORK-020", "WORK-023"]
implements: ["FEATURE-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/design-system.md", "docs/design-system/README.md", "apps/web/TOOLCHAIN.md", "apps/web/design-system", "apps/web/src/routes/_site.login.tsx", "apps/web/src/app/shells", "apps/web/src/app/pages", "apps/web/src/components/ui", "apps/web/src/features/auth", "apps/web/e2e", "development/works/WORK-013", "development/works/WORK-019", "development/works/WORK-020", "development/works/WORK-023", "development/works/WORK-024"]
write_paths: ["apps/web/src/routes/_site.login.tsx", "apps/web/src/app/pages/login-page.tsx", "apps/web/src/app/pages/login-page.test.tsx", "apps/web/src/app/pages/login-page.stories.tsx", "apps/web/public/login-workspace-art.png", "apps/web/e2e", "apps/web/design-qa.md", "development/works/WORK-024", "development/WORKS.md", "development/index.json"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/components/ui", "apps/web/src/features/auth", "apps/web/src/app/shells", "apps/web/src/generated", "apps/web/public/generated", "apps/web/package.json", "apps/web/package-lock.json", "apps/server", "contracts", "docs", "development/works（WORK-024 除外）"]
created_at: "2026-08-29"
updated_at: "2026-08-30"
---








# TASK-032：重新设计登录页视觉与体验

## 任务目标

按用户选定的第 2 个视觉方向重新组织 `/login` 页面，提取可独立展示的登录页视图，同时保持现有认证、错误、
跳转和 Shell 行为不变，并补齐状态测试、Storybook 与 E2E 证据。

## 依据

实现 FEATURE-006 的 REQ-001～REQ-013、AC-001～AC-007；交互与响应式以 EXPERIENCE-012 及用户后续
选定的视觉方向为准。WORK-013 提供认证行为基线，WORK-019/020/023 提供主页、Shell 与导航视觉基线。

## 可查看范围

以 front matter 的 `read_paths` 为准；实施前运行 `scripts/work context TASK-032`，并查看选定视觉图、
当前登录页、设计系统组件合同和既有登录 E2E。

## 可修改范围

以 front matter 的 `write_paths` 为准。路由保留请求与导航编排；页面级视图负责构图和可展示状态；仅
调整本任务 E2E 和 WORK-024 证据，不修改共享组件或设计 token。

## 禁止修改

不得修改认证 API、错误映射、Session/CSRF、权限、Shell、共享 UI 合同、依赖、设计系统真源、主题、
后端、契约或其它 WORK；不得用 raw CSS、主题分支、透明状态色或 allowlist 绕过门禁。

## 依赖

用户已选择本轮第 2 个视觉方向。FEATURE-006 与 EXPERIENCE-012 仍必须获得用户批准；未收到后续明确
执行授权前保持 `todo`，不得开始实现。

## 产出

- 与第 2 个视觉方向一致、双主题可用的左侧表单/右侧低对比字景构图。
- 默认、pending、错误、长文案和窄屏可独立检查的页面视图与 Storybook 状态。
- 登录行为和无下划线导航等既有 Shell 回归，以及 320px/键盘/200% 的 E2E 证据。
- VERIFY-024 的实际检查、视觉比较、范围与遗留风险记录。

## 完成标准

- [x] 登录页呈现所选方向的结构、信息层级和视觉重点，不退回孤立居中小卡片。
- [x] 仅保留现有登录能力，认证请求、错误映射、returnTo、首次改密与角色跳转无行为变化。
- [x] 默认、pending、空提交、错误凭据、429、服务失败和成功状态均有稳定布局和恢复路径。
- [x] 320px、200% 缩放、长中文、双主题、键盘、forced-colors 与 reduced-motion 满足设计系统合同。
- [x] 只消费 semantic token、现有共享组件和 Lucide；无 raw 值、主题分支、依赖或设计系统修改。
- [x] 修改严格位于 `write_paths`，WORK-021 等不相关本地修改保持原样。
- [x] `npm run check`、`npm run build`、`npm run storybook:build`、`npm run test:e2e` 与文档校验通过。

## 验证

在 `apps/web` 执行格式化后运行 `npm run check`、`npm run build`、`npm run storybook:build` 和
`npm run test:e2e`。使用应用内浏览器比较选定视觉目标和实现截图，至少覆盖 1440×1024、320×720、
两个主题、200% 缩放、错误/限流、pending、长文案、键盘焦点、forced-colors 和 reduced-motion。

仓库根目录执行 `scripts/work check`、文档链接检查与 `git diff --check`；结果按事实写入 VERIFY-024。

## 风险

主要风险是为了视觉丰富改变登录行为、把生成图中的假内容带入产品，或为单页绕过设计系统。若实现
需要新 token、共享组件改义、依赖、新认证能力或 Shell 变化，立即停止并升级 FEATURE/TASK，不在本
任务内扩大范围。

## 执行记录

- 2026-08-29：创建任务。
- 2026-08-29：补齐视觉重构、行为不变、状态覆盖、读写边界和验证计划；等待方向选择与文档审批。
- 2026-08-29：用户选择第 2 个视觉方向；任务目标收敛为同画布左侧表单与右侧工作区字景。
- 2026-08-29：状态变更：todo → ready。原因：上游功能与体验已获用户批准
- 2026-08-29：状态变更：ready → doing。原因：用户明确授权执行 TASK-032
- 2026-08-29：将 `apps/web/design-qa.md` 纳入可写范围，用于记录 image-to-code 要求的设计 QA 报告；
  不改变产品实现范围。
- 2026-08-29：将由 `scripts/work` 随状态流转自动维护的 `development/WORKS.md` 与
  `development/index.json` 纳入可写范围；其中 WORK-021 条目和计数属于原有本地修改，不在本任务修改。
- 2026-08-29：完成页面级 `LoginPageView`、路由编排接线、默认/pending/错误/长错误/移动 Storybook、
  组件测试及桌面/320px/200%/双主题/forced-colors/reduced-motion E2E；认证与 Shell 未修改。
- 2026-08-29：按选定视觉目标完成同尺寸全页及表单局部两轮设计 QA，最终报告为 `passed`；完整证据
  见 `apps/web/design-qa.md` 与 VERIFY-024。
- 2026-08-29：状态变更：doing → done。原因：登录页重构、状态展示、Storybook、自动化回归与浏览器设计 QA 均已完成
- 2026-08-29：用户人工验收指出右侧视觉未忠实还原第 2 个方向，且登录页出现纵向滚动条；WORK 与任务
  退回 `doing`，VERIFY 退回 `draft/pending`。
- 2026-08-29：将 `apps/web/public/login-workspace-art.png` 纳入可写范围，用真实透明视觉资产承载参考稿的
  大型工作区字景，替换此前不合格的 HTML/CSS 近似绘制；不扩大认证、Shell 或共享组件范围。
- 2026-08-29：完成用户退回修正：右侧改为测量后裁切的透明工作区字景资产；登录内容改为
  `box-border` 的视口高度网格，移除 `min-height + padding` 溢出，并增加桌面无纵向滚动断言。
- 2026-08-29：重新完成同画面对照、应用内浏览器几何检查和全量验证；1440×1024 下页面宽高与视口
  完全一致，`npm run check`、build、Storybook build 与 E2E 23/23 均通过。
- 2026-08-29：用户再次复核指出登录页仍有滚动条；1280×720 复测确认文档为 1312×843，任务再次退回
  `doing`。修正页面对 Shell 网格的最小高度贡献，并补充短桌面默认/错误状态无溢出验证。
- 2026-08-30：使用 `h-0 min-h-full` 让登录页填满 Shell 分配的主区域但不反向撑高网格；右侧字景受
  剩余高度/宽度约束，短窗口收紧顶部节奏。1280×720 默认和错误状态双轴溢出均为 0。
- 2026-08-29：状态变更：done → doing。原因：用户人工验收指出视觉未忠实还原图 2 且页面出现纵向滚动条，退回实现修正
- 2026-08-29：状态变更：doing → done。原因：用户退回的图 2 视觉还原与页面滚动问题已修正，浏览器几何、设计 QA 与全量回归通过
- 2026-08-29：状态变更：done → doing。原因：1280×720 实测确认右侧字景超出可用高度和宽度，退回响应式修正
- 2026-08-30：状态变更：doing → done。原因：短桌面窗口默认及错误状态无滚动条，设计 QA、check、build、Storybook 与 E2E 全部通过
