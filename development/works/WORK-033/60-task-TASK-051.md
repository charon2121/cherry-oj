---
id: "TASK-051"
type: "task"
title: "重设计后台题目创建与编辑体验"
status: "done"
work: "WORK-033"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-001", "EXPERIENCE-017", "DESIGN-026", "DESIGN-027", "DECISION-018", "PLAN-021"]
related: ["WORK-015", "WORK-025", "WORK-031"]
implements: ["IMPROVEMENT-001#REQ-001", "IMPROVEMENT-001#REQ-002", "IMPROVEMENT-001#REQ-003", "IMPROVEMENT-001#REQ-004", "IMPROVEMENT-001#REQ-005", "IMPROVEMENT-001#REQ-006", "IMPROVEMENT-001#REQ-007", "IMPROVEMENT-001#REQ-008", "IMPROVEMENT-001#REQ-009", "IMPROVEMENT-001#REQ-010", "IMPROVEMENT-001#REQ-011", "IMPROVEMENT-001#REQ-012", "IMPROVEMENT-001#AC-001", "IMPROVEMENT-001#AC-002", "IMPROVEMENT-001#AC-003", "IMPROVEMENT-001#AC-004", "IMPROVEMENT-001#AC-005", "IMPROVEMENT-001#AC-006", "IMPROVEMENT-001#AC-007", "IMPROVEMENT-001#AC-008", "IMPROVEMENT-001#AC-009", "IMPROVEMENT-001#AC-010", "IMPROVEMENT-001#AC-011", "IMPROVEMENT-001#AC-012"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/design-system.md", "docs/design-system", "docs/engineering/typescript.md", "docs/engineering/conventions.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/design-system", "apps/web/src/components/ui", "apps/web/src/lib/theme", "apps/web/src/features/problems", "apps/web/src/routes", "apps/web/e2e", "contracts/web-api.openapi.json", "development/works/WORK-015", "development/works/WORK-025", "development/works/WORK-031", "development/works/WORK-033"]
write_paths: ["docs/design-system.md", "docs/engineering/typescript.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/src/components/ui", "apps/web/src/features/problems/components", "apps/web/src/routes/admin.problems.index.tsx", "apps/web/src/routes/admin.problems.$problemId.versions.$versionId.tsx", "apps/web/e2e", "development/works/WORK-033"]
forbidden_paths: ["apps/web/design-system", "docs/design-system", "apps/web/src/generated", "apps/web/src/lib/theme", "apps/web/src/features/problems/api", "contracts", "apps/server", "apps/judge-engine", "database migrations", "development/works（WORK-033 除外）"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-051：重设计后台题目创建与编辑体验

## 任务目标

交付新的后台题目创建与六步编辑工作台，用统一 CodeMirror 编辑器、结构化样例、保存保护和发布就绪导航
替换现有长表单，同时保持 API 与已有数据完全兼容。

## 依据

实现 front matter 锚定的 IMPROVEMENT-001 全部 REQ/AC；体验以 EXPERIENCE-017 为准，UI 以 DESIGN-027
为准，适配、状态和依赖以 DESIGN-026/DECISION-018/PLAN-021 为准。

## 可查看范围

以 front matter 的 `read_paths` 为准。实施前运行 `scripts/work context TASK-051`，重点核对 WORK-025 的
安全/不可变边界、WORK-031 的页面布局合同、现有 API Zod schema 和 ThemeProvider colorScheme。

## 可修改范围

以 front matter 的 `write_paths` 为准。允许更新 Web 依赖、工具链/TypeScript/设计系统说明、通用 UI
组件、题目业务组件、两个相关路由和测试；不允许借重构修改 API client 或设计 token。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。后端、OpenAPI、生成物、主题实现、Problem API client、公开
题库和其它 WORK 均禁止修改；若现有接口无法满足已批准体验，停止实施并升级设计边界。

## 依赖

以 front matter 的 `depends_on` 为准。WORK-033 意图闸由用户签署且 TASK 进入 ready 后才能实施。

## 产出

- CodeMirror 6 本地 UI 适配、Markdown 组合、Storybook 与测试，移除后台 Monaco。
- 创建 Dialog、六步工作台、URL/保存保护、结构化样例/标签、测试校准链和发布/危险操作 UI。
- 依赖锁文件、工具链/TypeScript/长期设计系统文档更新。
- 组件、路由、E2E、浏览器无障碍和构建体积证据；VERIFY-034 与 MEMORY-026。

## 完成标准

- [x] IMPROVEMENT-001 的 12 项 REQ 与 12 项 AC 均有实现和 VERIFY-034 精确锚点。
- [x] 管理列表、创建 Dialog、六步 URL/导航、保存状态/快捷键/离开保护与冲突恢复完整。
- [x] 所有后台长 Markdown/代码和样例字段使用本地 CodeMirror/结构化 UI，无 Monaco 和样例 JSON。
- [x] 上传绑定、部署、校准、单位转换、发布检查映射和危险操作分组按设计运行。
- [x] 两主题、键盘/IME、320px、200%、forced-colors/reduced-motion 和可访问名称验收通过。
- [x] API/后端/数据/主题合同/公开题库未变化，依赖、工具链和长期设计规范一致。
- [x] Web 全量门禁、Storybook、E2E、文档/工作流检查和独立复核通过。

## 验证

按 PLAN-021 执行编辑器/创建/步骤/保存/样例/测试数据/发布的组件与浏览器矩阵，以及 Web check、build、
Storybook、Playwright、设计文档和工作流门禁；记录管理工作台构建产物体积前后对比。

## 风险

高风险为 CodeMirror IME/受控状态、rowVersion、步骤卸载、上传绑定半成功和发布检查陈旧。任何必须修改
API、后端、主题合同或敏感源码持久化的情况都立即停止并更新上游方案，不扩大 TASK 边界。

## 执行记录

- 2026-09-03：创建任务。
- 2026-09-03：状态变更：todo → ready。原因：WORK-033 意图闸已由用户签署，任务边界和依赖满足实施条件
- 2026-09-03：状态变更：ready → doing。原因：用户明确要求开始实施已批准的后台题目创建与编辑体验
- 2026-09-03：完成 CodeMirror 适配、创建 Dialog、六步工作台、结构化样例/标签、测试校准链、发布与
  危险操作，并同步依赖、工具链和长期设计规范。
- 2026-09-03：复核关闭保存请求期间继续编辑会被旧响应覆盖、dirty 误报、参考源码状态和上传取消状态
  四项实现风险；Web check、build、Storybook 与 Chromium 回归通过。
- 2026-09-03：状态变更：doing → done。原因：CodeMirror、创建 Dialog、六步工作台、保存保护、测试校准与发布流程已实施并通过 Web/浏览器验证
