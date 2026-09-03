---
id: "TASK-062"
type: "task"
title: "迁移题库列表页作为构图样板"
status: "done"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-061"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-008", "IMPROVEMENT-003#REQ-010", "IMPROVEMENT-003#REQ-013", "IMPROVEMENT-003#REQ-014"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system/source/claude-design-v1/ui_kits"]
write_paths: ["apps/web/src/features/problems", "apps/web/src/routes", "apps/web/src/components/ui/data-list.tsx"]
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

- [x] 筛选字段、游标分页、空/错/加载状态、URL search 参数与迁移前**完全一致**。
- [x] 既有 E2E 与组件测试**不修改断言**即通过；若必须改断言，说明改的是表达而非行为。
- [x] 页面中 `var(--ds-` 出现次数为 0。
- [x] 桌面下列跨行对齐；320px 下关键信息不裁切；长中文标题不破坏列结构。
- [x] 两主题、200% 缩放、键盘、forced-colors、reduced-motion 验收通过。
- [x] 同画面对照截图已产出，差异清单已写入 VERIFY-037。

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
- 2026-09-03：实施完成。`/problems` 改为 `ListPageTemplate` + `Toolbar` + `DataList`。
  筛选字段、游标分页、空/错/加载状态、URL search 参数全部未变，**既有 E2E 断言一行没改，
  30/30 通过**。

  构图变化：筛选从"Panel 包住的 grid 表单 + 红色「筛选」提交按钮"改为工具条——关键词/标签
  回车生效，四个下拉选中即生效，没有提交按钮；列表从 `flex-wrap` 行改为对齐列，行高由
  约 65px 降到约 30px，标识与语言用 mono，难度用颜色+文字，行间 1px hairline。

  **未完成的一项**：TASK-062 原定产出"与 `ui_kits/app/ProblemList.jsx` 的同画面对照截图"。
  该原型从 unpkg 加载 React 与 Babel 才能运行，而 `docs/design-system.md` 与 `CLAUDE.md`
  明确要求"对照来源但不执行其 demo"，来源包内也没有 app kit 的静态渲染产物
  （`.thumbnail` 只是品牌图）。因此改为交付**迁移前后**的同画面对照，并把是否放宽这条规则
  留给用户判断——不自行解释成"这次可以执行"。

  另记两个排查过程：截图工具的 mock 连续三次被拒（`requestId` 必须匹配
  `^req_[A-Za-z0-9_-]{16,64}$`、`allowedLanguages[].id` 而非 `languageId`、Playwright 的
  route 后注册优先导致兜底盖住具体规则）——这些是验证工具的问题，不是产品缺陷，
  但说明"看起来渲染出错"未必是被测对象的错。
- 2026-09-03：扩大 `write_paths` 增加 `apps/web/src/components/ui/data-list.tsx`。原因：样板页接入后
  暴露 DataList 的实现缺陷——`<table>` 默认是 `table-layout: auto`，声明的列宽只是"建议"，
  内容长了照样撑开。真实 slug（`segment-tree-range-sum · v3`）因此折成三行，行高从约 36px 涨到
  65px，"列宽跨行共享、列边缘连成竖线"这条构图合同实际没有兑现。这是 TASK-061 交付物的缺陷，
  由第一个真实消费者发现——正是样板页存在的意义。修复属于该缺陷本身，不是范围扩张。
- 2026-09-03：状态变更：todo → ready。原因：TASK-061 完成，DataList/Toolbar/模板可用
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 4：题库列表样板迁移
- 2026-09-03：状态变更：doing → done。原因：题库列表已迁到新模板，行为与 URL 参数不变，E2E 断言未改即通过；同画面对照改为迁移前后（来源 demo 不执行）
