---
id: "TASK-053"
type: "task"
title: "迁移共享 UI 组件与 Storybook"
status: "done"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["TASK-052"]
related: ["WORK-015", "WORK-033"]
implements: ["IMPROVEMENT-002#REQ-007", "IMPROVEMENT-002#REQ-008", "IMPROVEMENT-002#REQ-011", "IMPROVEMENT-002#REQ-012", "IMPROVEMENT-002#REQ-016", "IMPROVEMENT-002#AC-004", "IMPROVEMENT-002#AC-007", "IMPROVEMENT-002#AC-009", "IMPROVEMENT-002#AC-011", "IMPROVEMENT-002#AC-012", "IMPROVEMENT-002#AC-013"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system", "docs/engineering/typescript.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/design-system", "apps/web/scripts/check-design-system.mjs", "apps/web/.storybook", "apps/web/src/styles", "apps/web/src/components/ui", "apps/web/src/app", "apps/web/src/features", "apps/web/e2e/design-system.spec.ts", "/Users/charon/Downloads/Cherry OJ Design System/components", "/Users/charon/Downloads/Cherry OJ Design System/guidelines", "/Users/charon/Downloads/Cherry OJ Design System/ui_kits", "development/works/WORK-033", "development/works/WORK-034"]
write_paths: ["apps/web/design-system", "apps/web/scripts/check-design-system.mjs", "docs/design-system.md", "docs/design-system", "apps/web/.storybook", "apps/web/src/components/ui", "apps/web/e2e/design-system.spec.ts", "apps/web/TOOLCHAIN.md", "docs/engineering/typescript.md", "development/works/WORK-034"]
forbidden_paths: ["apps/web/src/app", "apps/web/src/features", "apps/web/src/routes", "apps/web/src/lib/theme", "apps/web/src/generated", "contracts", "apps/server", "apps/judge-engine", "database migrations", "development/works（WORK-034 除外）"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-053：迁移共享 UI 组件与 Storybook

## 任务目标

把下载版 14 个核心组件配方和 Cherry OJ 生产扩展组件迁移到新 Foundation，形成页面可消费、可访问、
有完整 Storybook 状态的新共享组件层。

## 依据

依赖 TASK-052 的新 token/adapter。组件视觉以下载版 component prompt/specimen 为准，生产结构以
DESIGN-028/DECISION-019 和当前 Base UI/TypeScript 合同为准。

## 可查看范围

允许查看组件消费者以评估兼容性，但不修改调用页面。实施前运行 `scripts/work context TASK-053`，按组件组
建立旧 API→新配方迁移表。

## 可修改范围

以共享 UI、Storybook 配置/故事、组件测试和设计系统 E2E 为主。组件逐值映射若发现 TASK-052 遗漏、但已
由批准来源明确规定的视觉语义，可同步补齐 docs/Web 合同、主题与 checker；不得借此发明来源外 token。
允许调整组件 API，但该 TASK 结束时现有调用必须仍可编译；临时 adapter 必须有 TASK-054/055 的明确删除点。

## 禁止修改

Shell、业务页面/API、主题运行时、后端和其它 WORK 禁止修改；组件不得用 raw 值掩盖 Foundation 缺口。

## 依赖

TASK-052 完成并通过 Foundation 检查后执行。

## 产出

- Controls/Typography/Layout/Forms/Surfaces/Navigation 的核心配方。
- Overlay/Feedback/Sidebar/Editor 等生产扩展配方。
- 双主题 Storybook 状态矩阵、组件单测和新设计系统浏览器用例。
- 原型到生产适配记录：语义节点、useId、focus、disabled、link、icon、reduced-motion。

## 完成标准

- [x] 14 个核心配方的暗色 Storybook 与下载版 specimen 一致，浅色沿用相同结构、密度和状态层级。
- [x] 所有现有扩展组件只使用新 token/视觉语法，无旧 variant 或页面私有补丁。
- [x] 两主题下键盘、焦点、loading/disabled/error、320px、长中文与 forced-colors 不回归。
- [x] 无 inline raw style、远程 CDN、随机 id、鼠标专用交互或内联图标路径进入生产。
- [x] 组件 tests、Storybook build、E2E、typecheck/build 和范围检查通过。

## 验证

逐组运行双主题 Vitest/Storybook/a11y；暗色用相同 viewport 对照下载版 card/UI kit，浅色验证语义同构、层级
与对比度；执行源码负向 fixture、键盘、reduced-motion/forced-colors、320px 和全量 Web typecheck/build。

## 风险

组件 API 变化会影响大量调用。采用小组提交和类型检查，不保留无限期兼容层；若视觉 fidelity 必须依赖
破坏语义的 DOM，优先保留可访问性并把差异提交用户复核。

## 执行记录

- 2026-09-03：创建任务；随后纳入下载版设计系统的系统级迁移链。
- 2026-09-03：状态变更：todo → ready。原因：TASK-052 已完成并通过 Foundation、双主题、许可、npm check/build 与文档校验
- 2026-09-03：状态变更：ready → doing。原因：开始迁移共享 UI 组件、Storybook 与组件视觉合同
- 2026-09-03：逐值读取 14 个来源组件后发现 TASK-052 合同缺少来源明确规定的透明 surface、ghost 实线
  边界、tertiary line 与完整 elevation。工作流不允许在依赖任务活跃后回退其状态，因此将这组“只补来源
  明示语义”的 docs/Web 合同文件纳入本任务；整体架构、主题、业务与用户批准范围不变。
- 2026-09-03：把既有 Web 设计系统源码检查器纳入本任务边界，用负向 fixture 固化批准方案中明确禁止的
  inline style、手写 SVG、随机 DOM id、鼠标专用视觉状态与 transform 动效；只扩充质量门禁，不改变业务。
- 2026-09-03：transform 门禁暂时只为 TASK-054 尚未迁移的三个 Shell 文件登记精确 allowlist；组件层已无
  transform/scale/translate 动效。TASK-054 修改对应 Shell 时必须同时删除三项 allowlist，不能长期保留。
- 2026-09-03：完成 14 个来源核心配方、同语法 Overlay/Feedback/Sidebar/Editor 扩展、双主题
  `Foundation/Source recipes` Storybook、65 键主题合同与 19 个源码负向 fixture。`npm run check` 通过
  32 个文件/115 项测试，Storybook 与生产构建通过；设计系统 Chromium E2E 17 项通过，期间发现并修复
  forced-colors 下 Input 焦点被 `outline-none` 吞掉的问题。
- 2026-09-03：状态变更：doing → done。原因：14 个来源核心配方、双主题 Storybook、生产扩展、源码负向门禁及 17 项设计系统 E2E 均已通过
