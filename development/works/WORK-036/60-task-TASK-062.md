---
id: "TASK-062"
type: "task"
title: "迁移题库列表页作为构图样板"
status: "todo"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-061"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-008", "IMPROVEMENT-003#REQ-010", "IMPROVEMENT-003#REQ-013", "IMPROVEMENT-003#REQ-014"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system/source/claude-design-v1/ui_kits"]
write_paths: ["apps/web/src/features/problems", "apps/web/src/routes"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/generated", "contracts"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-062：迁移题库列表页作为构图样板

## 任务目标

用新模板和新组件重写 `/problems`，让用户第一次看到"构图合规"的页面长什么样，
并给出与冻结来源的同画面对照，供人工判断这条路走没走对。

## 依据

[DECISION-020](./40-decision-DECISION-020.md) C-2；[DESIGN-030](./30-design-DESIGN-030.md)；
构图依据 `docs/design-system/source/claude-design-v1/ui_kits/app/ProblemList.jsx`；
[PLAN-024](./50-plan-PLAN-024.md) 阶段 4。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`apps/web/src/generated` 在禁止范围内——
API 客户端由 OpenAPI 生成，本任务不改契约。

## 依赖

依赖 TASK-061 的 `DataList`、`Toolbar` 与 `ListPageTemplate`。

## 产出

1. `features/problems/components/problem-list-page.tsx` 改写为
   `ListPageTemplate` + `Toolbar` + `DataList` 组合：
   - 筛选从"Panel 包住的 grid 表单 + 筛选提交按钮"改为 Toolbar 的即时 pill + 搜索；
   - 列表从 `flex-wrap` 行改为对齐列，难度用颜色区分并保留文字，
     增加状态点，slug 与通过率等度量值用 mono；
2. 必要时同步 `routes/_site.problems.index.tsx` 的 search 解析（**不改参数名与语义**）；
3. 与 `ui_kits/app/ProblemList.jsx` 在相同 viewport（桌面 1440、320px）下的同画面对照截图，
   差异逐条说明是有意适配还是待修。

## 完成标准

- [ ] 筛选字段、游标分页、空/错/加载状态、URL search 参数与迁移前**完全一致**。
- [ ] 既有 E2E 与组件测试**不修改断言**即通过；若必须改断言，说明改的是表达而非行为。
- [ ] 页面中 `var(--ds-` 出现次数为 0。
- [ ] 桌面下列跨行对齐；320px 下关键信息不裁切；长中文标题不破坏列结构。
- [ ] 两主题、200% 缩放、键盘、forced-colors、reduced-motion 验收通过。
- [ ] 同画面对照截图已产出，差异清单已写入 VERIFY-037。

## 验证

```bash
cd apps/web
npm run check && npm run build && npm run test:e2e
```

人工对照：把迁移后的页面与 `ui_kits/app/ProblemList.jsx` 的渲染结果在同 viewport 并排截图。
判据不是"token 通过校验"，而是**列是否对齐、密度是否接近、层级是否安静**。

## 风险

这一步的产物要交给用户判断"像不像"，而这个判断无法用自动化替代。
**交付后必须停下来等用户反馈，不要顺势迁移其余页面**——这是 DECISION-020 C-2 的全部意义，
也是这个仓库前四轮设计系统返工的根本教训。

若用户判断"仍然不像"，不要在页面上继续微调：按 DESIGN-030 的重审条件回到设计层，
重新识别构图特征。

## 执行记录

- 2026-09-03：创建任务。
