---
id: "TASK-044"
type: "task"
title: "把展示类组件改为基于 shadcn 官方实现"
status: "done"
work: "WORK-027"
owners: ["codex/root"]
depends_on: ["DESIGN-021"]
related: []
implements: ["CHANGE-009#REQ-001", "CHANGE-009#REQ-002", "CHANGE-009#REQ-003"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system.md", "docs/frontend.md"]
write_paths: ["apps/web/src/components/ui", "apps/web/src/features", "apps/web/src/routes"]
forbidden_paths: ["apps/web/design-system", "contracts", "apps/server"]
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# TASK-044：把展示类组件改为基于 shadcn 官方实现

## 任务目标

把展示类组件的实现文件换成 shadcn base-nova 官方版本（「换文件」的含义见
[DESIGN-021 术语](./30-design-DESIGN-021.md#术语)），只替换颜色与尺寸相关 class 为本仓库语义 token，
保留官方变体并叠加 OJ 语义变体。

范围在执行中经负责人确认缩小为 `badge` 与 `card`；`icon-button` 与 `async-state` 移入「维持现状」，
理由见 [CHANGE-009 变更记录](./10-change-CHANGE-009.md#变更记录)。

## 依据

[CHANGE-009](./10-change-CHANGE-009.md) REQ-001、REQ-002、REQ-007～REQ-011；
[DESIGN-021](./30-design-DESIGN-021.md) 整体方案第 2 步与固定改法。

## 可查看范围

`apps/web`（含 `design-system/` 只读，用于查 token 名）、`docs/design-system.md`、`docs/frontend.md`。

## 可修改范围

`apps/web/src/components/ui`、`apps/web/src/features`、`apps/web/src/routes`。

## 禁止修改

`apps/web/design-system`（token、主题、manifest、合同均不动）、`contracts`、`apps/server`。

## 依赖

DESIGN-021 定稿。与 TASK-043 无依赖；TASK-045 依赖本任务确认改法后再开始。

## 产出

- `badge` 与 `card` 以官方实现为骨架重建，官方 DOM 结构与 slot 命名保持；
- 官方变体保留，OJ 五态（`success`/`warning`/`danger`/`info`/`special`）以扩展方式追加；
- `icon-button` 与 `async-state` 维持现状，并记录「官方同名组件不覆盖我们依赖的行为」这一依据；
- story 与 test 同步更新；消费者文件的适配点逐条记录在执行记录中。

## 完成标准

- [x] `badge` 与 `card` 直接使用官方源码作为骨架，没有保留手写骨架。
- [x] 未出现 raw hex/OKLCH、primitive palette、`dark:` 或按 theme id 分支。
- [x] 两个主题下 default、hover、pressed、focus-visible、disabled、loading 六种状态齐全且表现一致。
- [x] 消费者文件的改动仅限组件 API 变化导致的必需适配（实际为零改动）。
- [x] `npm run check` 与 `npm run build` 通过。

## 验证

在 `apps/web` 下执行 `npm run check`、`npm run build`、`npm run storybook` 双主题目视比对；
四个组件的既有测试全部通过。结果汇总进 [VERIFY-028](./70-verify-VERIFY-028.md)。

## 风险

官方默认外观与现有实现必然有差异，容易在「只换文件」和「顺便改设计」之间滑动。约束是：任何视觉差异
要么消除，要么在 VERIFY 写明为何接受，不允许无声接受。

## 执行记录

- `badge`：换成官方骨架（`useRender` + `mergeProps`），7 个 OJ 变体全部保留，另按 REQ-002 补齐官方的
  default/secondary/destructive/outline/ghost/link。相对官方改了三处，均有条款支撑：颜色换语义 token
  不用 `/10` `/20` 透明度叠加（§4）；焦点用 2px outline 而非透明光晕 ring（§7）；尺寸放宽为 `min-h-6`
  且可换行——官方的 `h-5 whitespace-nowrap` 会裁切长中文（§7）。
- `card`：换成官方七件套，新增 `CardAction`（标题区两列网格，操作区固定右上）与官方 `size`；官方
  `ring-1 ring-foreground/10` 和 `bg-muted/50` 用透明度承担边界与分层，改为 `border-border` 与
  `bg-surface-subtle`。`Panel` 保留——它是仓库里真正在用的容器（14 处），官方无对应组件。
- **消费者零改动。** `badge` 的 7 个变体名全保留；`Card` 在真实代码里本就没有消费者（只有 `Panel`
  在用），换掉它不影响任何页面。
- 顺带删掉两个没有任何消费者的自造 API：`Badge.selected` 与 `Card.selected`/`selectionLabel`，
  它们只出现在自己的 story 和 test 里。测试改为断言换文件后新拿到的能力——Badge 能渲染成 `<a>`、
  CardAction 落在标题区右上角。
- **范围缩小（经负责人确认）**：`icon-button` 与 `async-state` 移入「维持现状」。官方 `button` 不强制
  无障碍名称，而 `IconButton` 的 `label` 是必填（globalRules 要求 icon-only 控件必须有可访问名称）；
  官方 `spinner` 依赖 shadcn 站点内部的 `IconPlaceholder`、`empty` 是无 `aria-live` 的纯布局，而
  `AsyncState` 覆盖四态并强制 loading 提供 `progressLabel`。**教训：判断能否换文件，判据是「官方那份
  是否覆盖我们依赖的行为」，而不是「官方有没有同名组件」。** 我写 CHANGE 时只做了后者。
- ESLint 的 `jsx-a11y/anchor-has-content` 对 Base UI `render` 模式误报（children 由组件通过 props
  注入，规则看不到），在 test 与 story 两处加了带理由的定点豁免。
- 验证：`npm run check`（30 个测试文件 / 105 个用例）与 `npm run build` 通过。

- 2026-09-01：状态变更：todo → ready。原因：意图闸已过，边界与完成标准明确
- 2026-09-01：状态变更：ready → doing。原因：开始把展示类组件换成官方实现
- 2026-09-01：状态变更：doing → done。原因：badge 与 card 换文件完成；icon-button 与 async-state 经确认维持现状
