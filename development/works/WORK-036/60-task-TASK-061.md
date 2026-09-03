---
id: "TASK-061"
type: "task"
title: "实现 DataList、Toolbar 与页面模板"
status: "done"
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

- [x] shadcn registry 查询结果已记录；若官方有 `table`，骨架来自官方且未重写其 DOM/a11y。
- [x] `DataList` 桌面下列跨行严格对齐——截图中列边缘可连成直线。
- [x] 320px 下不横向裁切关键信息；低优先级列按 priority 折行，关键列始终可见。
- [x] 行可键盘聚焦、有可访问名称；整行可点击时是单个链接/按钮而非 `div` + `onClick`。
- [x] `Toolbar` 的 pill 过滤即时生效，不存在提交按钮；搜索框有可访问 label。
- [x] 三个页面模板独占首内容间距（`--ds-space-6`），页面不再自写 `pt-*`。
- [x] 组件不含业务逻辑、不发请求、不引用 `features/`。
- [x] Storybook 覆盖两主题 × default/hover/selected/focus/empty/loading；a11y 无 violation。
- [x] 组件内不出现 `var(--ds-`（除非已回到 TASK-060 补过 alias 仍无法表达并说明理由）。

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
- 2026-09-03：shadcn registry 查询结果：`https://ui.shadcn.com/r/styles/base-nova/table.json`
  返回 **200**，因此按 design-system.md §6 以官方 table 为骨架落 `table.tsx`——保留官方八个子组件、
  `data-slot` 与 DOM/a11y，只把 `bg-muted/50`、`border-b` 等透明度叠加换成语义 token，
  并把通用后台密度（h-10 / p-2）改成来源的 11px/16px。
- 2026-09-03：实施完成。新增 `table.tsx`（第 2 层官方骨架）、`data-list.tsx`、`toolbar.tsx`、
  `page-templates.tsx`，各配 stories 与 tests，测试 121→129。

  实现中修正了三处自己的设计错误：
  1. 最初的 `rowAction` 会把 `<a>` 插在 `<tr>` 与 `<td>` 之间，是非法 HTML。改为整行覆盖式链接：
     行做定位上下文，主列里的真实链接用 `::after` 铺满整行，一个链接、键盘可达、语义正确。
  2. 窄屏折行区最初挂在每个主列下，导致被隐藏的次要列在窄屏重复出现多份。改为只挂第一个主列。
  3. 行标题最初用了 `Link` 的品牌色。来源把 Cherry 限制在主按钮、focus、活动态和正文链接上，
     一屏几十行标题全是品牌色就成了它明确禁止的"大面积色块"。改为前景色 + hover 提亮，
     并把这条理由写进 `dataListRowLinkClasses` 的注释。

  另记一个工具链坑：`npm run typecheck` 用 `tsc -b`（增量），改动后可能不重新检查；
  本次有两处类型错误是 `tsc --noEmit -p tsconfig.app.json` 才暴露的。
- 2026-09-03：状态变更：todo → ready。原因：TASK-060 完成，alias 词汇表可用
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 3：构图层组件
- 2026-09-03：状态变更：doing → done。原因：DataList/Toolbar/三个页面模板就位，官方 table 为骨架，窄屏折行与整行链接语义已验证
