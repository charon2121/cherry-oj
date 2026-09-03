---
id: "TASK-059"
type: "task"
title: "修复前景色四档并放开 disabled 的对比度约束"
status: "done"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["IMPROVEMENT-003", "DESIGN-030", "DECISION-020", "PLAN-024"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-002", "IMPROVEMENT-003#REQ-011", "IMPROVEMENT-003#REQ-012"]
verifies: []
tags: []
read_paths: ["apps/web/design-system", "docs/design-system/source/claude-design-v1/tokens", "docs/design-system/source/claude-design-v1/guidelines"]
write_paths: ["apps/web/design-system/theme-contract.json", "apps/web/design-system/themes/cherry-black.css", "apps/web/design-system/themes/pure-white.css", "apps/web/design-system/design-tokens.json", "apps/web/design-system/tools/check.mjs"]
forbidden_paths: ["apps/web/src", "apps/web/design-system/tokens.foundation.css", "apps/web/design-system/tailwind-v4.css", "docs/design-system/source", "contracts"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-059：修复前景色四档并放开 disabled 的对比度约束

## 任务目标

把塌成两档半的前景色恢复成四档可分且全部达标的层级，并把 `--ds-fg-disabled` 从正文约束中移出，
让"不可用"重新成为一个看得出来的状态。

## 依据

[DECISION-020](./40-decision-DECISION-020.md) A-3 及其取值表；
[DESIGN-030](./30-design-DESIGN-030.md)「前景色」；[PLAN-024](./50-plan-PLAN-024.md) 阶段 1。

## 可查看范围

以 front matter 的 `read_paths` 为准。来源的 `tokens/colors.css` 与
`guidelines/colors-text.card.html` 只读，用于核对四档的原始意图。

## 可修改范围

以 front matter 的 `write_paths` 为准。`design-tokens.json` 只能由 `build.mjs` 生成，不手改。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。本任务**不碰任何组件或页面**：这一步只改值与合同，
外观变化必须完全来自 token，便于归因。

## 依赖

无代码依赖，是 WORK-036 的第一个实施任务。

## 产出

1. `theme-contract.json`：`--ds-fg-disabled` 的 `contrastClass` 由 `text` 改为 `decorative`。
   **这是本次唯一的合同语义变更**，其余 entry 不动；`--ds-fg-ghost` 保持 `text`。
2. `themes/cherry-black.css`：`--ds-fg-muted` → `#a8adb6`，`--ds-fg-meta` 保持 `#8a8f98`，
   `--ds-fg-disabled` → `#62666d`。
3. `themes/pure-white.css`：`--ds-fg-muted` → `#55595f`，`--ds-fg-meta` → `#6b7079`，
   `--ds-fg-disabled` → `#9aa0a8`。
4. `tools/check.mjs` 的 `expectedContractShape` 中 `--ds-fg-disabled` 的 `contrastClass`
   同步改为 `decorative`。该表是 WORK-035 刻意保留的"合同不许被悄悄放宽"防线，
   它按设计会拒绝本次变更；由 DECISION-020 授权后同步更新，**只改这一个键**。
5. 重新生成 `design-tokens.json`。
5. 四档在全部允许 surface 上的实测对比度表，写入 VERIFY-037。

## 完成标准

- [x] `fg`、`fg-2`、`fg-muted`、`fg-meta` 在两个主题、全部 `allowedOn` surface 上实测 ≥4.5:1。
- [x] 四档相邻两档在同一 surface 上的对比度比值足以在截图上分辨（不是同色）。
- [x] `fg-disabled` 与 `fg-meta` 在两个主题中取值不同。
- [x] `theme-contract.json` 只改了 `--ds-fg-disabled` 的 `contrastClass` 与 `role`，其余 diff 为空。
- [x] `check.mjs` 只改了 `expectedContractShape` 中同一个键，未删除任何校验能力。
- [x] 对比度组合数由 308 降为 296，差值 12 = disabled 的 6 个 allowedOn × 2 主题，
      证明豁免范围精确，没有连带放宽其他 entry。
- [x] `node design-system/tools/build.mjs --check`、`check.mjs`、`check.mjs --self-test` 通过。
- [x] `apps/web/src` 与 `tokens.foundation.css`、`tailwind-v4.css` 的 diff 为空。

## 验证

```bash
cd apps/web
node design-system/tools/build.mjs && node design-system/tools/build.mjs --check
node design-system/tools/check.mjs && node design-system/tools/check.mjs --self-test
npm run check
```

对比度实测：用 `check.mjs` 输出的 contrast 报告，或按 DECISION-020 的方法逐对计算，
把四档 × 五个 surface × 两个主题的数值表记入 VERIFY-037。

外观复查：两个主题下逐个路由截图，确认次要文字层级变化符合预期、未出现可读性回归（AC-010）。

## 风险

放开 disabled 的对比度是**有意降低可读性**，与常规可访问性直觉相反。它成立的前提是
`design-system.md` §7.1 已要求"禁用控件旁必须有持久、可读的原因，tooltip 不能是唯一解释"。
实施时若发现现有页面存在"只靠禁用态传达信息、旁边没有原因说明"的地方，**不要在本任务修它**，
记入 `00-work.md` 待确认项并由后续 TASK 承接——但必须记，否则这次改动会让那些地方更难理解。

## 执行记录

- 2026-09-03：创建任务。
- 2026-09-03：实施完成。合同 `--ds-fg-disabled` 由 `text` 改为 `decorative` 并补写 role 说明理由；
  `cherry-black` 的 `fg-muted` `#8a8f98`→`#a8adb6`、`fg-disabled` `#8a8f98`→`#62666d`；
  `pure-white` 的 `fg-muted` `#62666d`→`#55595f`、`fg-meta` `#676b73`→`#6b7079`、
  `fg-disabled` `#676b73`→`#9aa0a8`。对比度组合数 308→296，差值 12 与预期（disabled 6 surface × 2 主题）
  完全一致。四档在两主题全部 6 个 surface 上的最低值分别为 13.80 / 10.05 / 6.52 / 4.52（暗）与
  15.83 / 11.23 / 6.40 / 4.52（浅），均 ≥4.5:1。
  **额外补了一条校验**：豁免之后没有任何机制阻止 disabled 漂回 meta（即本次修复的缺陷本身），
  因此在 `verifyTheme` 增加"disabled 必须在画布上弱于 meta"的阶梯断言，并配负向 fixture，
  self-test 用例 18→19。断言做了 token 存在性前置判断，避免在其他 fixture 的缺失场景下抢先抛错。
- 2026-09-03：扩大 `write_paths` 增加 `apps/web/design-system/tools/check.mjs`。原因：
  `expectedContractShape` 手工钉住了每个 entry 的 `contrastClass`，它按设计会拒绝合同变更
  （这正是 WORK-035 保留该表的目的）。DECISION-020 已授权本次变更，因此同步更新该表中的同一个键；
  这不是范围扩张，是合同变更的固有组成部分。DESIGN-030「前景色」原文只写了"合同改动一处"，
  未计入校验器侧的镜像键，属设计遗漏。
- 2026-09-03：状态变更：todo → ready。原因：意图闸已签署，DECISION-020 已 approved，取值表确定
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 1：四档前景与 disabled 合同类别
- 2026-09-03：状态变更：doing → done。原因：四档恢复且全部达标，disabled 移出正文约束并新增阶梯断言防止回退
