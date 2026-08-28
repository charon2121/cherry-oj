---
id: "TASK-024"
type: "task"
title: "建立共享组件与 Storybook 双主题基线"
status: "done"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["TASK-023", "DESIGN-013", "DECISION-012", "PLAN-013"]
related: []
implements: ["CAPABILITY-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system/README.md", "docs/design-system/components.manifest.json", "docs/design-system/theme-contract.json", "docs/design-system/components.html", "apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/src/components", "apps/web/src/lib/theme", "apps/web/src/lib/utils.ts", "apps/web/src/generated/design-system", "apps/web/src/styles", "apps/web/.storybook", "development/works/WORK-017"]
write_paths: ["apps/web/src/components/ui", "apps/web/src/test/design-system", "apps/web/.storybook", "development/works/WORK-017"]
forbidden_paths: ["docs/design-system.md", "docs/design-system", "docs/frontend.md", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/index.html", "apps/web/vite.config.ts", "apps/web/src/styles", "apps/web/src/lib", "apps/web/src/generated", "apps/web/src/app", "apps/web/src/routes", "apps/web/src/features", "apps/web/e2e", "apps/server", "apps/judge-engine", "contracts", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# TASK-024：建立共享组件与 Storybook 双主题基线

## 任务目标

在 TASK-023 的主题运行时上建立可供后续页面复用的通用组件层和 manifest 驱动的 Storybook 双主题
审核基线；不改业务 routes/features，也不把 OJ 业务组件混入基础任务。

## 依据

实现 CAPABILITY-006 的 REQ-006～REQ-008 与 REQ-011 的组件部分，依据获批准的 DESIGN-013、
DECISION-012、PLAN-013 和 `components.manifest.json`。组件若需要新 token 或修改合同，先升级设计，
不能在 class 中补 raw color。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- Button：primary/secondary/ghost/danger、sm/md 和完整交互/loading 合同。
- Field primitive：Input、Textarea、Select、Label/description/error 关联与 invalid/disabled 状态。
- Link style、IconButton、Badge、Card/Panel、Typography/Layout。
- Base UI 驱动的 Dialog/Popover；Escape、焦点约束与返回 trigger 由行为测试钉住。
- InlineNotice 与 AsyncState（empty/loading/error/unauthorized）；not-found 继续由 Router 独立承担。
- 每个组件就近 Vitest/Testing Library 测试和 Story；Story 覆盖 manifest variant/state、长中文和窄屏。
- Storybook 全局主题 toolbar/decorator，主题列表与 color scheme 消费 TASK-023 生成 registry。
- 可选的小型 `src/test/design-system` render helper，统一主题测试，不能建立另一份 theme resolver。
- 为让本 TASK 完成时现有 routes/features 仍可编译，可在 Button 内暂留不进入 Story/新调用的
  `outline → secondary` variant 和 `lg → md` size 兼容别名，并写明由 TASK-025 删除；不得保留
  其他无消费者的旧 variant/size。

## 完成标准

- [x] 所列基础组件只消费 semantic alias/canonical token，无 raw color、primitive、theme id 或 `dark:`。
- [x] primary/brand/accent/danger/status 各司其职；必要 foreground/surface 成对，disabled 不叠 opacity。
- [x] Button loading 保持尺寸、不可重复触发且有可读状态；danger 视觉与品牌 CTA 可区分。
- [x] 若存在 `outline`/`lg` 临时别名，它们只有现有调用可用、分别映射 secondary/md、有
      TASK-025 退出条件且无 Story。
- [x] Field 有真实 label 和 description/error 关联；placeholder 不代替 label，控件边界满足合同。
- [x] Link 保持语义链接/下划线；IconButton 有 accessible name；Badge/Notice 不只靠颜色。
- [x] Dialog/Popover 的 Enter/Space/Escape、focus trap/restore 与 ARIA 行为通过用户级测试。
- [x] Storybook 在同一 Story 上全局切换两个 manifest 主题，不在 Story 中手写主题 id。
- [x] 320px、长中文、focus-visible、disabled/loading/error 和 reduced-motion 有审核入口。
- [x] 未新增依赖，未修改 routes/features、主题实现或设计系统真源。

## 验证

- `npm run test:run -- src/components/ui`（或 Vitest 等价过滤）覆盖行为与 ARIA。
- `npm run format:check && npm run lint && npm run typecheck`。
- `npm run storybook:build`，并在两个主题下检查全部 Story、320px 与长中文。
- Storybook a11y addon 对每个 Story 以 error 级别运行；人工补键盘顺序、焦点返回与非颜色表达。
- 静态搜索确认组件/Story 无 raw color、`.dark`、`dark:`、`--ds-raw-*` 与 literal theme id。
- `git diff --name-only` 与 TASK 路径边界对照。

## 风险

最大风险是把组件参考页机械翻译成僵硬 API，或让 Base UI 默认行为与合同冲突。优先保持语义和键盘行为，
视觉只消费 token；需要改组件 anatomy/default、引入新依赖或实现 OJ 业务状态时，停止并升级
DESIGN/PLAN，不扩大本 TASK。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：冻结首期通用组件与 Storybook 范围，等待人工批准上游文档和明确执行授权。
- 2026-08-28：状态变更：todo → ready。原因：TASK-023 已完成且上游设计、决策和计划均已批准，组件任务边界与完成标准齐备
- 2026-08-28：状态变更：ready → doing。原因：开始建立共享组件与 Storybook 双主题基线
- 2026-08-28：完成 Button、Field、Link/IconButton、Badge、Card/Panel、Typography/Layout、
  Dialog/Popover、InlineNotice 与 AsyncState；Storybook toolbar 从生成 registry 派生并复用主题 runtime。
- 2026-08-28：Node 26 临时实施环境下，格式、ESLint、TypeScript、22 个测试文件/89 个用例、Vite
  build 与 Storybook 10.5.10 build 全部通过；UI 源码/Story 禁用模式扫描无命中。Story 级 320px
  viewport 已使用 Storybook 10 的 `globals.viewport`，不再使用已移除的 `defaultViewport`。
- 2026-08-28：独立审查提出并关闭两项 P2：AsyncState 的 busy/live region 分离与显式 urgent error
  策略；storage event 按 localStorage area 过滤。复核后无剩余 blocker/finding，真实浏览器与辅助技术风险
  归 TASK-025/VERIFY-017。
- 2026-08-28：TASK-025 真实 Chromium 回归与末轮独立复核继续关闭三项组件边界：Button loading 使用
  显式 accessible name、disabled 在 pressed 组合态中保持视觉优先、Link 新窗口提示由最终 target
  派生；unit、Story 和浏览器 computed-style 回归均通过。
- 2026-08-28：状态变更：doing → done。原因：共享组件、双主题 Storybook 与行为/a11y测试已完成，全量检查、构建和独立复核通过
