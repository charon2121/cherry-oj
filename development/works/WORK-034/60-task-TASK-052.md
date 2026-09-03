---
id: "TASK-052"
type: "task"
title: "冻结下载来源并重建双主题 Foundation"
status: "done"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["WORK-033", "IMPROVEMENT-002", "DESIGN-028", "DECISION-019", "PLAN-022"]
related: ["WORK-015", "WORK-033"]
implements: ["IMPROVEMENT-002#REQ-001", "IMPROVEMENT-002#REQ-002", "IMPROVEMENT-002#REQ-003", "IMPROVEMENT-002#REQ-004", "IMPROVEMENT-002#REQ-005", "IMPROVEMENT-002#REQ-006", "IMPROVEMENT-002#REQ-007", "IMPROVEMENT-002#REQ-011", "IMPROVEMENT-002#REQ-012", "IMPROVEMENT-002#REQ-014", "IMPROVEMENT-002#REQ-016", "IMPROVEMENT-002#AC-001", "IMPROVEMENT-002#AC-002", "IMPROVEMENT-002#AC-003", "IMPROVEMENT-002#AC-005", "IMPROVEMENT-002#AC-007", "IMPROVEMENT-002#AC-011", "IMPROVEMENT-002#AC-012", "IMPROVEMENT-002#AC-013", "IMPROVEMENT-002#AC-015"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/design-system.md", "docs/design-system", "docs/engineering/typescript.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/design-system", "apps/web/public/legal", "apps/web/src/styles", "apps/web/src/generated/design-system", "apps/web/src/lib/theme", "apps/web/scripts", "apps/web/index.html", "/Users/charon/Downloads/Cherry OJ Design System", "development/works/WORK-015", "development/works/WORK-033", "development/works/WORK-034"]
write_paths: ["CLAUDE.md", "AGENTS.md", "docs/design-system.md", "docs/design-system", "docs/engineering/typescript.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/design-system", "apps/web/public/legal/NOTICE.md", "apps/web/public/legal/LICENSE.fonts", "apps/web/src/styles", "apps/web/src/generated/design-system", "apps/web/scripts/check-design-system.mjs", "apps/web/scripts/generate-design-system.mjs", "development/works/WORK-034"]
forbidden_paths: ["apps/web/src/components/ui", "apps/web/src/app", "apps/web/src/features", "apps/web/src/routes", "apps/web/e2e", "contracts", "apps/server", "apps/judge-engine", "database migrations", "development/works（WORK-034 除外）"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-052：冻结下载来源并重建双主题 Foundation

## 任务目标

把下载版 99 文件冻结为可追溯来源，重建 docs/Web 设计系统双树、共用 token、暗色/浅色主题和 tooling，
并准备后续组件迁移，但不在本任务改共享组件或业务页面。

## 依据

实现 front matter 锚定的 IMPROVEMENT-002 要求，视觉/生产边界以 DESIGN-028 与 DECISION-019 为准，执行
顺序以 PLAN-022 为准。必须先完成并冻结 WORK-033 基线。

## 可查看范围

以 front matter 为准。下载目录只读；实施前运行 `scripts/work context TASK-052`，核对 99 文件、hash、许可、
当前双树生成器、主题消费者和 package 字体来源。

## 可修改范围

允许完整替换 docs/Web 设计系统包、全局 token 入口、生成/check 脚本、字体依赖和相关长期/工具链说明；
CLAUDE/AGENTS 只更新路由与方法，不复制设计规则正文。本任务不得切换页面组件。

## 禁止修改

共享组件、Shell、页面、业务 API、后端、contracts、数据库和其它 WORK 禁止修改。发现 Foundation 无法在
不改业务语义的情况下落地时，先更新 DECISION/PLAN，不越界。

## 依赖

WORK-034 意图闸和 WORK-033 验收/基线均完成后才能进入 ready。

## 产出

- 下载版原始快照、source lock、NOTICE/LICENSE 与中文新设计合同。
- 下载版暗色主题、同构 `pure-white` 浅色主题、共用 token、adapter、manifest、builder/checker 和生成物。
- 本地字体策略、双主题完整性/对比度检查、旧值负向检查、文档/代码双树边界。
- CLAUDE/AGENTS/TypeScript/TOOLCHAIN 的新入口说明。

## 完成标准

- [x] 99 文件、大小、hash 与下载源一致，来源快照不进入 Web 运行时。
- [x] docs 与 Web 设计包只描述新系统，关键 token 精确匹配下载版。
- [x] `cherry-black`/`pure-white` 主题键集合一致且都通过对比度检查；旧品牌值被 checker 拒绝。
- [x] 主题选择/偏好/首屏合同保留，页面可通过新 adapter 继续编译。
- [x] 无远程 font/CDN/原型脚本进入生产；许可与生产适配清楚。
- [x] 设计系统自检、生成检查、Web typecheck/build 与文档检查通过。

## 验证

执行来源 hash 比对、token/对比度/生成确定性/负向 fixture、依赖树、typecheck/build、设计双树独立检查、
`scripts/work check` 和范围检查；记录 CSS/font/build 基线。

## 风险

重建双主题文件和 adapter 影响面大；必须先用精确文件清单确认目标并由 Git 回退。浅色语义不完整、字体包或
许可不明确、任一主题关键对比不达标、来源 hash 变化时阻断，不继续到 TASK-053。

## 执行记录

- 2026-09-03：原局部工作台优化任务被用户提供的新设计系统整体替换目标取代，本任务重构为 Foundation 阶段。
- 2026-09-03：状态变更：todo → ready。原因：WORK-033 验收闸和 WORK-034 意图闸均已通过，任务依赖、范围与验收标准完整
- 2026-09-03：状态变更：ready → doing。原因：开始冻结下载来源并重建双主题 Foundation
- 2026-09-03：将 Web 发布目录中的 NOTICE 纳入写入范围；代码包检查要求发布副本与新来源说明逐字一致，
  这是许可交付的一部分，不涉及页面、业务或运行时能力扩张。
- 2026-09-03：新增发布侧 `LICENSE.fonts` 范围；Inter 与 JetBrains Mono 字体二进制随 Web 构建分发，必须
  同步提供两者的 OFL-1.1 版权声明和许可，不把许可证只留在本机 `node_modules`。
- 2026-09-03：完成下载目录与冻结目录逐文件 `diff -qr`，确认 99 文件完全一致；source lock 记录
  239831 bytes，根摘要 `68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d`，且文档 checker
  重新计算逐文件大小/hash、拒绝 symlink 与路径逃逸。
- 2026-09-03：完成 Web/docs 2.0 Foundation、双主题、来源元数据、生成物和本地字体接入。两主题均实现
  56 个语义键，296 组允许对比组合通过，最低 3.4035078594052393:1；18 个包级与 14 个消费侧负向
  fixture 均正确失败，旧 Cherry 色值已加入拒绝规则。
- 2026-09-03：`npm run check` 通过（31 文件、112 测试），`npm run build` 通过；构建新增 JetBrains Mono
  6 个按 unicode-range 拆分的 WOFF2，共约 84.19 kB 未压缩，CSS gzip 15.58 kB。现有 CodeMirror 页面
  chunk 仍触发大于 500 kB 的非阻断 warning，留在最终性能复核中，不由 Foundation 擅改业务拆包。
- 2026-09-03：`node docs/design-system/tools/source-lock.mjs --check`、docs/Web build/check、Web checker
  self-test、`git diff --check` 与 `scripts/work check` 均通过；WORK-033 既有 `implemented` 推导提示不属于
  本任务失败。
- 2026-09-03：状态变更：doing → done。原因：下载来源、双主题 Foundation、生产字体/许可与严格校验均已完成，npm check/build 和文档检查通过
