---
id: "DESIGN-030"
type: "design"
title: "建立页面构图层并修复前景色层级"
status: "review"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["IMPROVEMENT-003", "DECISION-020"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# DESIGN-030：建立页面构图层并修复前景色层级

## 背景

上游见 [IMPROVEMENT-003](./10-improvement-IMPROVEMENT-003.md) 与
[DECISION-020](./40-decision-DECISION-020.md)。WORK-035 已把设计值收敛到单一真源，本次在这个
基础上补第 3、4 层与 alias 词汇表——顺序不能反，否则新组件会造出新的副本。

## 目标与限制

目标是让"写一个列表页"变成"选一个模板 + 填业务数据"，而不是从 Button 和 `var(--ds-space-3)` 现拼。

硬限制：

- 设计值仍只有一处手写定义，改动后重新生成 `design-tokens.json`（WORK-035 REQ-001）；
- 题库列表的业务行为、URL search 参数与 API 形状不变；
- 两个主题共享同一组件 DOM 与 variant，不得按 theme id 分支。

## 整体方案

四件事，按依赖顺序：

```text
1. alias 词汇表        tailwind-v4.css @theme inline 补全
        │              （业务代码从此有话可说）
2. 四档前景 + 合同     theme-contract.json contrastClass + themes/*.css 取值
        │              （DECISION-020 A-3）
3. 构图层组件          DataList / Toolbar / 三个页面模板
        │              （依据 ui_kits/app/）
4. 样板页 + 合同文档   题库列表迁移 + design-system.md 构图合同 + PROMPT.md
                       （交给用户判断"像不像"）
```

第 1 步必须先于第 3 步：新组件要用 alias 写，否则刚建的组件立刻成为新的 `var(--ds-*)` 集中地。
第 2 步独立于其余三步，但必须在样板页对照之前完成，否则对照的是错的层级。

## 模块与数据

### Alias 词汇表（`tailwind-v4.css`）

按业务代码实测用量补全，不做全量映射（DECISION-020 B-2）：

| 类别 | 新增 alias | 覆盖的现有用量 |
|---|---|---|
| 间距 | `--spacing-*` 映射 `--ds-space-*`（含 `1x/2x/4x/5x` 光学档）| 244 处 |
| 字号 | `--text-*` 映射 `--ds-text-*` | ~70 处 |
| 字重 | `--font-weight-*` 映射 `--ds-weight-*` | ~66 处 |
| 字距行高 | `--tracking-*`、`--leading-*` | ~30 处 |
| 动效 | `--transition-duration-*`、`--ease-*` | ~40 处 |
| 前景 | `--color-fg-2`、`-muted`、`-meta`、`-ghost`、`-disabled` | ~50 处 |
| 透明面 | `--color-surface-translucent{,-hover,-selected}` | 50 处 |
| 边界 | `--color-border-soft`、`--color-line`（映射 `line-tertiary`）| ~45 处 |
| 抬升 | `--shadow-subtle/inset/dialog/raised/ring` | ~15 处 |
| 布局 | `--spacing-header`、`--spacing-sidebar` | ~15 处 |

判断标准是"业务代码真的需要它来表达任务"。**不为存在而存在**：primitive、`--ds-raw-*` 和
只在组件内部出现一次的值不进词汇表。

### 前景色（`theme-contract.json` + `themes/*.css`）

合同改动一处：`--ds-fg-disabled` 的 `contrastClass` 由 `text` 改为 `decorative`
（阈值 `null`，即不做对比度断言）。这是本次唯一的合同语义变更，属破坏性改动，由 DECISION-020 承担。

取值按 DECISION-020 的表执行。`fg`、`fg-2` 不变；`fg-muted` 两个主题都变；`fg-meta` 暗色不变、
浅色变；`fg-disabled` 两个主题都变为明显更弱的值。

`--ds-fg-ghost` 保持 `text` 类别不变——它是 ghost 按钮的标签，是可用控件。

### DataList（第 3 层，`components/ui/data-list.tsx`）

构图依据 `source/claude-design-v1/ui_kits/app/ProblemList.jsx`。

- anatomy：`DataList` > `DataListRow` > `DataListCell`，可选 `DataListStatusDot`；
- 列宽由列定义声明并跨行共享，保证列边缘对齐。桌面用 CSS grid 的共享
  `grid-template-columns`，行不使用 `flex-wrap`；
- 行密度：垂直 `--ds-space-2x`（11px）、水平 `--ds-space-4`（16px），行间 1px
  `--ds-line-tertiary` hairline，`last:border-b-0`；
- 状态：rest 透明、hover `surface-translucent-hover`、selected `surface-translucent-selected`、
  focus-visible 2px inset outline；只动 background-color；
- 语义：整行可点击时渲染为单个链接或按钮，不用 `onClick` 包裹 `div`；行有可访问名称；
  列头用真实 `<table>` 语义或 `role="row"/"columnheader"`，二选一在 DESIGN 定稿时确定实现细节；
- 窄屏降级：**不横向裁切**。低优先级列在 `sm` 以下折到行内第二行，列定义声明每列的
  `priority`；关键列（标识、标题、状态）始终可见。

### Toolbar（第 3 层，`components/ui/toolbar.tsx`）

构图依据同上。

- anatomy：第一行 `title` + `count` + `spacer` + `SearchInput` + `primaryAction`；
  第二行 `PillFilterGroup` + `spacer` + `toolbarActions`；
- 高度对齐 `--ds-header-height`，下边界 `--ds-border-soft`；
- pill 过滤即时生效，**不提供提交按钮**；受控 value 由调用方持有并写入 URL；
- 搜索框有可访问 label；快捷键提示是可选装饰，不是唯一入口。

### 页面模板（第 4 层，`components/ui/page-templates.tsx`）

`ListPageTemplate`、`WorkbenchPageTemplate`、`DetailPageTemplate` 统一承担：

- 首内容间距 `--ds-space-6`（`design-system.md` §7 既有规则），由模板独占，页面不再写 `pt-*`；
- 页面语义标题（可 `sr-only`），保证每个路由有可访问名称；
- pending / empty / error / unauthorized / not-found 的**位置**固定，内容由页面提供。

模板只管骨架与状态位置，不持有业务逻辑，也不代替 `Container`/`Section` 的既有职责。

## 接口与状态

题库列表迁移后，对外契约完全不变：`ProblemSearch` 的字段、`listProblems` 调用、游标分页语义、
`/problems` 路由与 URL search 参数、错误分支（`INVALID_CURSOR` 与 contract 错误）保持现状。
改变的只有渲染方式：筛选表单 → `Toolbar` 的 pill + 搜索；`Panel` 包裹的 `flex-wrap` 行 →
`DataList` 的对齐列。

新增 lint 规则 `forbidden-ds-var-outside-ui`：扫描 `apps/web/src`，排除 `src/components/ui/`，
命中 `var(--ds-` 即失败。与既有规则一样提供负向 fixture 与 `--self-test` 用例。

## 安全与失败

不触碰认证、权限与数据。主要失败模式是**外观回归**：四档调整影响全部页面，而本次只迁一个页面。
防护方式是 AC-010 要求对未迁页面做两主题外观复查，并在 VERIFY 记录复查范围与结论。

第二个失败模式是 lint 与 alias 不同步导致开发被卡。防护方式是两者同批交付，且 lint 上线前先跑
一次全量扫描确认剩余违例为 0。

## 监控与部署

无生产环境，无部署动作。

## 迁移与兼容

无数据迁移。分四个可独立编译、独立回滚的提交，顺序见 [PLAN-024](./50-plan-PLAN-024.md)。

样板期内 `/problems` 使用新模板，其余页面维持现状。这是**明确的过渡状态**，
在 `design-system.md` §10 记录并指明由后续工作承接；不得长期保留。

## 备选方案

**备选一：先迁页面，再补 alias 与组件。** 不采用。页面会先用 `var(--ds-*)` 写一遍，
补完 alias 再改一遍，等于同一批代码写两次。

**备选二：用 `@tailwindcss/typography` 或第三方 table 组件替代自建 DataList。** 不采用。
本次要复现的是来源的具体密度、列对齐与状态层级，第三方组件的默认样式需要大量覆盖才能贴近，
覆盖量会超过自建，且再次落入"材料合规、风格不合规"。

**备选三：把 DataList 建成 shadcn `table` 之上。** 部分采用为待定项：
`https://ui.shadcn.com/r/styles/base-nova/table.json` 若返回 200，按 `design-system.md` §6
"基础组件不自己造"必须以官方为骨架。实施时先查 registry，结果记入 TASK 执行记录。
但 `DataList` 属第 3 层业务组件（含状态点、优先级降级、行链接语义），官方 `table` 只能作为
第 2 层骨架，两者不冲突。

**备选四：四档改为三档，取消 meta。** 不采用。来源的安静层级正是"不像"的一部分；
把 metadata 与正文合并会让列表更吵，与目标相反。

## 风险与重审条件

- **列对齐与响应式的冲突。** 固定列宽在长中文与 320px 下必然溢出。设计上用 priority 折行而非
  横向滚动或裁切；若实测发现折行破坏可读性，退回到"关键列 + 展开详情"，并回到本文修订。
- **alias 词汇表过大。** 若实施中发现某类 alias 无人使用，应当删掉而不是留着，
  否则下一个人会以为它是推荐写法。
- **样板页被判"仍然不像"。** 这是本次最重要的重审条件：说明构图合同抓错了特征。
  正确反应是回到本文重新识别（可能需要更细的密度、对齐或色彩分布规则），
  而不是继续迁移其余页面把问题放大。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：技术方案与四个备选已写完
