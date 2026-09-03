---
id: "TASK-055"
type: "task"
title: "迁移题目与管理业务页面并完成视觉收口"
status: "done"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["TASK-054"]
related: ["WORK-015", "WORK-031", "WORK-033"]
implements: ["IMPROVEMENT-002#REQ-008", "IMPROVEMENT-002#REQ-009", "IMPROVEMENT-002#REQ-010", "IMPROVEMENT-002#REQ-011", "IMPROVEMENT-002#REQ-012", "IMPROVEMENT-002#REQ-013", "IMPROVEMENT-002#REQ-015", "IMPROVEMENT-002#REQ-016", "IMPROVEMENT-002#AC-006", "IMPROVEMENT-002#AC-007", "IMPROVEMENT-002#AC-008", "IMPROVEMENT-002#AC-009", "IMPROVEMENT-002#AC-010", "IMPROVEMENT-002#AC-012", "IMPROVEMENT-002#AC-013", "IMPROVEMENT-002#AC-014", "IMPROVEMENT-002#AC-016"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/design-system", "apps/web/src/styles", "apps/web/src/components/ui", "apps/web/src/app/shells", "apps/web/src/features/problems", "apps/web/src/routes/_site.problems.tsx", "apps/web/src/routes/_site.problems.index.tsx", "apps/web/src/routes/_site.problems.$slug.tsx", "apps/web/src/routes/admin.problems.tsx", "apps/web/src/routes/admin.problems.index.tsx", "apps/web/src/routes/admin.problems.$problemId.versions.$versionId.tsx", "apps/web/e2e/problems.spec.ts", "apps/web/e2e/smoke.spec.ts", "apps/web/e2e/design-system.spec.ts", "/Users/charon/Downloads/Cherry OJ Design System/ui_kits", "development/works/WORK-033", "development/works/WORK-034"]
write_paths: ["apps/web/src/features/problems", "apps/web/src/routes/_site.problems.tsx", "apps/web/src/routes/_site.problems.index.tsx", "apps/web/src/routes/_site.problems.$slug.tsx", "apps/web/src/routes/admin.problems.tsx", "apps/web/src/routes/admin.problems.index.tsx", "apps/web/src/routes/admin.problems.$problemId.versions.$versionId.tsx", "apps/web/e2e/problems.spec.ts", "apps/web/e2e/smoke.spec.ts", "apps/web/e2e/design-system.spec.ts", "development/works/WORK-034"]
forbidden_paths: ["apps/web/design-system", "docs/design-system.md", "docs/design-system", "apps/web/src/styles", "apps/web/src/components/ui", "apps/web/src/app/shells", "apps/web/src/lib/theme", "apps/web/src/features/auth", "apps/web/src/api", "apps/web/src/generated", "contracts", "apps/server", "apps/judge-engine", "database migrations", "development/works（WORK-034 除外）"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-055：迁移题目与管理业务页面并完成视觉收口

## 任务目标

迁移全部题库与管理题目页面，在不改变 WORK-033 业务合同的前提下完成工作台视觉收口、零样例稳定性和
全路由一致性验证，使最终产品不再混用新旧系统。

## 依据

依赖 TASK-054 的新 Shell 与单主题运行时。密集列表、详情工作区和编辑表单构图参考下载版 Judge app UI kit；
六步 UX、CodeMirror、并发保存/校准/发布合同继续以 WORK-033 为准。

## 可查看范围

以 front matter 为准。实施前运行 `scripts/work context TASK-055`，为每个题目路由建立视觉状态矩阵，并对照
WORK-033 验收场景标记不得变化的业务行为。

## 可修改范围

仅迁移题库列表/详情、管理题目列表、新建/编辑工作台及其问题域组件和 E2E。允许调整布局与视觉组合，
不允许改变 API 调用、字段语义、校验规则、保存状态机、路由地址或权限。

## 禁止修改

Foundation、设计文档、共享组件、Shell、主题运行时、认证、API、生成代码、后端、contracts、数据库和其它
WORK 禁止修改。发现组件缺口时回退到对应上游 TASK 修正，不在业务目录复制设计系统。

## 依赖

TASK-054 完成并通过全站入口、主题删除和基础路由验证后执行；WORK-033 必须已有可回退基线。

## 产出

- 用户题库列表/详情和管理题目列表的新视觉实现。
- 新建/编辑题目六步工作台的新设计实现，保留 CodeMirror 与全部业务状态。
- 零样例切换的稳定滚动/锚点策略，不使用固定假内容或高度动画掩盖跳动。
- 全路由双主题桌面/320px、键盘、reduced-motion、业务回归和视觉对照证据。

## 完成标准

- [x] 所有题目路由只消费新语义 token/共享组件；暗色符合下载版构图，浅色保持同一状态、密度与层级。
- [x] 六步 UX、保存并发保护、校准、发布、错误恢复和长文本编辑完全回归通过。
- [x] 从长内容步骤切到零样例时没有意外页面上跳，滚动位置策略有浏览器几何断言。
- [x] 两主题下空、加载、错误、无权限、危险操作、长中文和 320px 都有视觉与行为证据。
- [x] 最终旧 token/variant/private patch 扫描为零，全量 check/build/E2E 与范围检查通过。

## 验证

运行 WORK-033 回归集、题目 E2E、滚动几何断言、双主题桌面/320px 截图、键盘/焦点、forced-colors、
reduced-motion、源码负向扫描、Web check/build 和跨模块回归；暗色逐路由与下载版 UI kit 做视觉对照，
浅色检查同构性、层级和对比度。

## 风险

题目工作台状态多且与未提交的 WORK-033 重叠，是最容易出现功能回归和视觉私补丁的阶段。任何 API diff、
保存状态变化、需要修改共享组件或无法一次回退的情况都必须阻断并升级上游文档。

## 执行记录

- 2026-09-03：创建任务；随后纳入下载版设计系统的系统级迁移链。
- 2026-09-03：状态变更：todo → ready。原因：TASK-054 已完成双主题 Shell、基础页面与浏览器矩阵迁移
- 2026-09-03：状态变更：ready → doing。原因：开始迁移题库、管理题目与六步工作台并执行全路由视觉收口
- 2026-09-03：实施前发现原任务边界仍引用重构前已不存在的题目 route 名称；将 read/write paths 修正为
  当前 `_site.problems*` 与 `admin.problems*` 文件。没有扩大产品范围，API、生成代码、后端与其它 WORK 继续禁止修改。
- 2026-09-03：完成题库列表/详情、管理题目列表/创建 Dialog、Markdown/代码编辑器、样例与六步工作台的
  新系统迁移。表单、列表、sticky 对象栏、步骤导航、状态提示和危险操作只消费共享组件与语义 token；
  CodeMirror、六步状态机、保存并发保护、校准/发布合同和路由/API 均保持不变。
- 2026-09-03：零样例不再用固定高度掩盖布局变化；步骤切换后把新步骤内容锚定在工作台 sticky 栏下方。
  Chromium 从长题面滚到底部切到空样例时断言内容相对 `#admin-main` 顶部为 76–84px，实际通过。
- 2026-09-03：以 1280×760 同视口并排检查下载版 Judge app 与 Cherry Black 的题库、详情、管理列表、
  空样例工作台，再检查 Pure White 同构页面；另检查两主题 320×720 题库。侧栏、顶栏、hairline、密度、
  字重、弱层级和滚动边界一致，无横向溢出或主题专属 DOM。
- 2026-09-03：`npm run check` 通过 32 文件/116 测试，生产 build、Storybook build 和 Chromium E2E
  30 项通过；工作台产物 757.24 kB / gzip 256.14 kB，较 WORK-033 基线 754.24/255.80 kB 约增加
  0.4%，仍保留既有非阻断 500 kB warning，未新增动画库。
- 2026-09-03：跨模块回归在放开本地端口、Docker 与 JVM attach 限制后通过：Server Maven reactor
  133 项、Judge Engine 全包测试通过。`git diff --check`、后端/API/contract 负向范围扫描与 tracked diff
  反向应用 dry-run 均通过。
- 2026-09-03：状态变更：doing → done。原因：题目页面、六步工作台、零样例滚动锚点、双主题视觉与全量回归均完成
