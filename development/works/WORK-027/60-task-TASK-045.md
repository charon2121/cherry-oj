---
id: "TASK-045"
type: "task"
title: "把表单与提示类组件改为基于 shadcn 官方实现"
status: "ready"
work: "WORK-027"
owners: ["codex/root"]
depends_on: ["DESIGN-021"]
related: []
implements: ["CHANGE-009#REQ-001", "CHANGE-009#REQ-002"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system.md"]
write_paths: ["apps/web/src/components/ui", "apps/web/src/features", "apps/web/src/routes", "apps/web/src/app"]
forbidden_paths: ["apps/web/design-system", "contracts", "apps/server"]
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# TASK-045：把表单与提示类组件改为基于 shadcn 官方实现

## 任务目标

把 `field` 与 `inline-notice` 的实现文件换成 shadcn base-nova 官方 `field` 与 `alert`
（「换文件」的含义见 [DESIGN-021 术语](./30-design-DESIGN-021.md#术语)），保持现有的 aria 关联
行为与 OJ 五态语义。这是本工作风险最高的一步：`field` 有 7 个消费者并承担可访问性关联。

## 依据

[CHANGE-009](./10-change-CHANGE-009.md) REQ-001、REQ-002、REQ-007～REQ-011 与 AC-002；
[DESIGN-021](./30-design-DESIGN-021.md) 整体方案第 3 步与「安全与失败」。

## 可查看范围

`apps/web`（含 `design-system/` 只读）、`docs/design-system.md`。

## 可修改范围

`apps/web/src/components/ui`、`apps/web/src/features`、`apps/web/src/routes`、`apps/web/src/app`。

## 禁止修改

`apps/web/design-system`、`contracts`、`apps/server`。

## 依赖

DESIGN-021 定稿。

与 TASK-044 是**执行顺序**约束而非产出依赖：本任务不消费 TASK-044 的产物，只是两者都改
`components/ui/` 与调用方，同时进行会让视觉回归无法归因。顺序写在
[PLAN-017](./50-plan-PLAN-017.md)，不编码成 `depends_on`。

## 产出

- `field` 改为官方可组合子组件（`Field`/`FieldLabel`/`FieldDescription`/`FieldError` 等），
  现有 `useId` + `cloneElement` 的 aria 关联行为由官方组合方式等价实现；
- `inline-notice` 直接使用官方 `alert` 的实现，追加 OJ 五态变体；
- 登录、改密、用户管理、题库筛选、题目工作台等 7 个 `field` 消费者与 3 个 `inline-notice` 消费者完成适配；
- 新增测试覆盖 `aria-describedby`、`aria-invalid`、`required` 三项关联。

## 完成标准

- [ ] `field` 与 `inline-notice` 直接使用官方实现作为骨架，没有保留任何手写骨架。
- [ ] `aria-describedby`、`aria-invalid`、`required` 关联由测试覆盖，而不是靠目视确认。
- [ ] 登录、改密、用户管理三条表单链路的键盘操作顺序与错误提示与改动前一致。
- [ ] OJ 五态在 `inline-notice` 上保留，且不靠颜色单独表达状态。
- [ ] `npm run check`、`npm run build` 与双主题 Playwright E2E 通过。

## 验证

除 `npm run check`、`npm run build` 外，必须执行双主题 Playwright E2E，并用键盘走完登录与改密两条链路。
可访问性关联以测试断言为准。结果汇总进 [VERIFY-028](./70-verify-VERIFY-028.md)。

## 风险

最大风险是可访问性静默回退——aria 关联丢失后屏幕阅读器用户读不到错误原因，而视觉上完全看不出来。
因此完成标准要求测试覆盖而非目视。若 `field` 无法在保持三条表单链路键盘行为一致的前提下完成改造，
按 DESIGN-021「风险与重审条件」停下重审，不要为了完成任务放宽行为。

## 执行记录

- 2026-09-01：状态变更：todo → ready。原因：定义、边界与完成标准明确；执行顺序由 PLAN 约束
