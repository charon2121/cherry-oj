---
id: "TASK-061"
type: "task"
title: "实现 DataList、Toolbar 与页面模板"
status: "todo"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-060"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-004", "IMPROVEMENT-003#REQ-005"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system/source/claude-design-v1/ui_kits"]
write_paths: ["apps/web/src/components/ui"]
forbidden_paths: ["apps/web/design-system", "contracts"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-061：实现 DataList、Toolbar 与页面模板

## 任务目标

把 `docs/design-system/components.manifest.json` 里"有合同无实现"的第 3、4 层建出来，
让后续页面能"选模板 + 填数据"，而不是从零件现拼。

## 依据

[DESIGN-030](./30-design-DESIGN-030.md)「DataList」「Toolbar」「页面模板」；
构图依据是冻结来源的 `ui_kits/app/ProblemList.jsx`、`ProblemView.jsx`、`Sidebar.jsx`。
[PLAN-024](./50-plan-PLAN-024.md) 阶段 3。

## 可查看范围

以 front matter 的 `read_paths` 为准。来源 `ui_kits/` 只读，且**只作构图参考**：
其中的 inline style、`onMouse*` 状态、可点击 `div` 与随机 id 都不得进入生产
（`scripts/check-design-system.mjs` 已有对应禁写规则会拦截）。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。设计系统包不在本任务范围内。

## 依赖

依赖 TASK-060 的 alias 词汇表。新组件必须用 alias 写；若发现某处只能用 `var(--ds-*)`，
说明 alias 有缺口，回到 TASK-060 的范围补，不要在新组件里开例外。

## 产出

1. **先查 shadcn registry**：`https://ui.shadcn.com/r/styles/base-nova/table.json` 返回 200 则
   按 `design-system.md` §6 以官方实现为骨架，只替换颜色与尺寸 class；返回 404 才手写。
   查询结果与依据记入本文执行记录。
2. `components/ui/data-list.tsx`：列定义（含每列 `priority`）、跨行共享 `grid-template-columns`、
   行密度 11px/16px、hairline 分隔、状态点、行链接语义、focus-visible、窄屏按 priority 折行。
3. `components/ui/toolbar.tsx`：两行 anatomy、即时 pill 过滤（无提交按钮）、搜索可访问 label、
   计数与主操作位置。
4. `components/ui/page-templates.tsx`：`ListPageTemplate`、`WorkbenchPageTemplate`、
   `DetailPageTemplate`，承担首内容间距、页面语义标题与状态位置。
5. 每个组件的 stories 与 tests。

## 完成标准

- [ ] shadcn registry 查询结果已记录；若官方有 `table`，骨架来自官方且未重写其 DOM/a11y。
- [ ] `DataList` 桌面下列跨行严格对齐——截图中列边缘可连成直线。
- [ ] 320px 下不横向裁切关键信息；低优先级列按 priority 折行，关键列始终可见。
- [ ] 行可键盘聚焦、有可访问名称；整行可点击时是单个链接/按钮而非 `div` + `onClick`。
- [ ] `Toolbar` 的 pill 过滤即时生效，不存在提交按钮；搜索框有可访问 label。
- [ ] 三个页面模板独占首内容间距（`--ds-space-6`），页面不再自写 `pt-*`。
- [ ] 组件不含业务逻辑、不发请求、不引用 `features/`。
- [ ] Storybook 覆盖两主题 × default/hover/selected/focus/empty/loading；a11y 无 violation。
- [ ] 组件内不出现 `var(--ds-`（除非已回到 TASK-060 补过 alias 仍无法表达并说明理由）。

## 验证

```bash
cd apps/web
npm run check && npm run storybook:build
npx playwright test   # 若为组件补了 e2e
```

Storybook 中逐个对照 `ui_kits/app/` 的对应界面，记录差异。

## 风险

固定列宽与长中文、320px 天然冲突。设计上用 priority 折行而非横向滚动或裁切；
若实测发现折行破坏可读性，**不要自行改成横向滚动**——按 DESIGN-030 的重审条件回到设计层，
退回"关键列 + 展开详情"方案并更新 DESIGN。

## 执行记录

- 2026-09-03：创建任务。
