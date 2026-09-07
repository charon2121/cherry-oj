---
id: "TASK-075"
type: "task"
title: "建立题目阅读与 Monaco 编码工作台"
status: "done"
work: "WORK-041"
owners: ["codex/root"]
depends_on: ["FEATURE-010", "DESIGN-035", "PLAN-027"]
related: ["EXPERIENCE-018"]
implements: ["FEATURE-010#REQ-001", "FEATURE-010#REQ-002", "FEATURE-010#REQ-003", "FEATURE-010#REQ-004", "FEATURE-010#REQ-005", "FEATURE-010#REQ-006", "FEATURE-010#REQ-007", "FEATURE-010#REQ-008", "FEATURE-010#REQ-009", "FEATURE-010#REQ-010", "FEATURE-010#AC-001", "FEATURE-010#AC-002", "FEATURE-010#AC-003", "FEATURE-010#AC-004", "FEATURE-010#AC-005", "FEATURE-010#AC-006", "FEATURE-010#AC-007", "FEATURE-010#AC-008"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/design-system.md", "docs/design-system", "docs/engineering/typescript.md", "docs/frontend.md", "apps/web", "contracts/web-api.openapi.json", "development/works/WORK-041"]
write_paths: ["apps/web/src/features/problems", "apps/web/src/components/ui", "apps/web/src/app/shells/site-app-shell.tsx", "apps/web/src/routes/_site.problems.$slug.tsx", "apps/web/src/lib/utils.ts", "apps/web/src/styles", "apps/web/design-system", "apps/web/scripts", "apps/web/e2e", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/vite.config.ts", "apps/web/TOOLCHAIN.md", "docs/frontend.md", "docs/design-system.md", "docs/design-system/components.manifest.json", "docs/engineering/typescript.md", "development/works/WORK-041"]
forbidden_paths: ["apps/server", "apps/judge-engine", "contracts", "compose.yaml", "compose.legacy.yaml", "apps/web/src/generated", "apps/web/src/features/auth", "apps/web/src/routes/_site.problems.index.tsx", "apps/web/src/features/problems/components/admin-problem-workbench.tsx", "docs/design-system/source"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-075：建立题目阅读与 Monaco 编码工作台

## 任务目标

在原 /problems/$slug 路由交付可阅读、可编辑、可恢复草稿且身份展示正确的前端工作台。

## 依据

实现 FEATURE-010 的全部要求与验收场景；遵循 EXPERIENCE-018、DESIGN-035 与 PLAN-027。

## 可查看范围

以 front matter 的 read_paths 为准。

## 可修改范围

以 front matter 的 write_paths 为准；共享模板与 shell 只增加本路由所需的 opt-in 行为，不重做其他页面。

## 禁止修改

以 front matter 的 forbidden_paths 为准；本轮不能新增或模拟后端运行/提交链路。

## 依赖

意图闸已签署，用户已明确允许实施。上游以 front matter 为准。

## 产出

- 左题面右 Monaco、窄屏切换与手机回退，保持原路由。
- 系统会话分支和按账号/版本隔离的本机草稿，存储故障与冲突恢复。
- 暂未开放的运行/提交及对应请求缺席断言。
- 必要的本地 UI/worker/主题适配、质量与浏览器证据、事实说明同步。

## 完成标准

- [x] FEATURE-010 AC-001～AC-008 全部有实际证据。
- [x] USER/ADMIN 无固定登录误提示；访客、改密、加载和故障不混为一类。
- [x] 起始代码、空草稿、切题、换账号、版本变化、双标签页与存储拒绝不静默覆盖代码。
- [x] Monaco 本地异步加载、worker 正常、模型与事件释放；后台仍使用 CodeMirror。
- [x] 深浅主题、键盘、320px、200% 缩放和手机回退通过，并逐条回答设计系统八问。
- [x] Web 检查、构建及受影响回归通过；无 Java/Go/API/数据库/Compose 改动。

## 验证

运行 Web 定向 Vitest、npm run check、npm run build、npm run storybook:build 与受影响的 Playwright；
真实会话浏览器验证编辑恢复与身份变化。保存构建大小和页面截图，VERIFY-042 记录命令与实际结果。

## 风险

如果需要修改后端、公开契约、运行模型或旧题库布局，先修订定义与范围，不把范围外能力藏在此任务。
新增基础组件时先检查 shadcn base-nova 官方骨架；本轮默认复用现有组件，不引入拖拽分栏基础组件。

## 执行记录

- 2026-09-07：完成文档规划；未开始实现、安装依赖或业务测试，等待意图闸。
- 2026-09-07：状态变更：todo → ready。原因：WORK-041 意图闸已签署，用户已明确允许实施，前端边界与验收标准完整
- 2026-09-07：状态变更：ready → doing。原因：开始 Monaco 适配、本机草稿和答题工作台集成

- 2026-09-07：实现左右工作台、Monaco/触控回退、真实会话、本机草稿与冲突恢复；独立复核问题已修复。Web 165 项单测、44 项浏览器用例、构建和 Storybook 通过，见 VERIFY-042。
- 2026-09-07：状态变更：doing → done。原因：实现完成，独立复核问题已修复，165 项单测与44项浏览器回归通过
