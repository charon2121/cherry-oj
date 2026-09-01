---
id: "TASK-044"
type: "task"
title: "把展示类组件改为基于 shadcn 官方实现"
status: "ready"
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

把 `badge`、`card`、`icon-button`、`async-state` 四个展示类组件的实现文件换成 shadcn base-nova 官方
版本（「换文件」的含义见 [DESIGN-021 术语](./30-design-DESIGN-021.md#术语)），
只替换颜色与尺寸相关 class 为本仓库语义 token，保留官方变体并叠加 OJ 语义变体。

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

- 四个组件以官方实现为骨架重建，官方 DOM 结构与 slot 命名保持；
- 官方变体保留，OJ 五态（`success`/`warning`/`danger`/`info`/`special`）以扩展方式追加；
- `async-state` 按官方拆为 `spinner` + `empty`，调用处相应拆分；
- `icon-button` 并入官方 button 的 icon size；保留薄封装还是直接改调用处，按实际 diff 大小决定并在
  执行记录说明理由；
- story 与 test 同步更新；消费者文件的适配点逐条记录在执行记录中。

## 完成标准

- [ ] 四个组件直接使用官方源码作为骨架，没有保留任何手写骨架。
- [ ] 未出现 raw hex/OKLCH、primitive palette、`dark:` 或按 theme id 分支。
- [ ] 两个主题下 default、hover、pressed、focus-visible、disabled、loading 六种状态齐全且表现一致。
- [ ] 消费者文件的改动仅限组件 API 变化导致的必需适配，且已逐条列出。
- [ ] `npm run check` 与 `npm run build` 通过；Storybook a11y addon 无新增违规。

## 验证

在 `apps/web` 下执行 `npm run check`、`npm run build`、`npm run storybook` 双主题目视比对；
四个组件的既有测试全部通过。结果汇总进 [VERIFY-028](./70-verify-VERIFY-028.md)。

## 风险

官方默认外观与现有实现必然有差异，容易在「只换文件」和「顺便改设计」之间滑动。约束是：任何视觉差异
要么消除，要么在 VERIFY 写明为何接受，不允许无声接受。

## 执行记录

- 2026-09-01：状态变更：todo → ready。原因：意图闸已过，边界与完成标准明确
