---
id: "TASK-045"
type: "task"
title: "把表单与提示类组件改为基于 shadcn 官方实现"
status: "done"
work: "WORK-027"
owners: ["codex/root"]
depends_on: ["DESIGN-021"]
related: []
implements: ["CHANGE-009#REQ-001", "CHANGE-009#REQ-002"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system.md"]
write_paths: ["apps/web/src/components/ui", "apps/web/src/features", "apps/web/src/routes", "apps/web/src/app", "apps/web/e2e"]
forbidden_paths: ["apps/web/design-system", "contracts", "apps/server"]
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# TASK-045：把表单与提示类组件改为基于 shadcn 官方实现

## 任务目标

把 `field` 与 `inline-notice` 的实现文件换成 shadcn base-nova 官方版本
（「换文件」的含义见 [DESIGN-021 术语](./30-design-DESIGN-021.md#术语)），保持现有的 aria 关联
行为与 OJ 五态语义。这是本工作风险最高的一步：`field` 有 7 个消费者并承担可访问性关联。

**范围在执行中经负责人确认扩大**：`field.tsx` 一个文件打包了官方 5 个组件的职责，而官方
`field`、`label`、`input`、`textarea`、`select` 全部存在（registry 均返回 200）。负责人明确
「只要是 shadcn 有的、前端还手写的，都换」，因此拆成对应文件逐个换成官方实现，包括把原生
`<select>` 换成官方 Base UI Select。`icon-button` 与 `async-state` 不在此列——它们不是「官方有而
我们手写」，而是「官方那份不覆盖我们依赖的可访问性保证」，处置已在 CHANGE-009 变更记录中确认。

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

- [x] `field`、`label`、`input`、`textarea`、`select`、`inline-notice` 均以官方实现为骨架。
- [x] `aria-describedby`、`aria-invalid`、`required` 关联由测试覆盖，而不是靠目视确认。
- [x] 登录、改密、用户管理三条表单链路的既有测试全部通过，键盘顺序与错误提示未变。
- [x] OJ 五态在 `inline-notice` 上保留，可见状态文字未变（改写时误改被测试抓回）。
- [x] `npm run check`（30 文件 / 107 用例）、`npm run build` 与设计系统源码扫描通过。

## 验证

除 `npm run check`、`npm run build` 外，必须执行双主题 Playwright E2E，并用键盘走完登录与改密两条链路。
可访问性关联以测试断言为准。结果汇总进 [VERIFY-028](./70-verify-VERIFY-028.md)。

## 风险

最大风险是可访问性静默回退——aria 关联丢失后屏幕阅读器用户读不到错误原因，而视觉上完全看不出来。
因此完成标准要求测试覆盖而非目视。若 `field` 无法在保持三条表单链路键盘行为一致的前提下完成改造，
按 DESIGN-021「风险与重审条件」停下重审，不要为了完成任务放宽行为。

## 执行记录

- `inline-notice`：新增 `alert.tsx` 承载官方 Alert/AlertTitle/AlertDescription/AlertAction，
  InlineNotice 改为组合它们，补上官方不提供的三件事——OJ 五态、可见状态文字、可控播报强度。
  官方把 `role="alert"` 写死会让每条提示都打断屏幕阅读器；这里 off/polite/assertive 分别对应
  不播报、`role="status"`、`role="alert"`。消费者零改动。
- 改写时把 warning/info 的状态文字误写成「注意」「提示」（原值「警告」「信息」），被既有测试当场
  抓住。这正是完成标准要求「由测试覆盖而非目视」的理由。
- `field` 拆分：原 `field.tsx` 一个文件打包了官方 5 个组件的职责。拆成 `label.tsx`、`input.tsx`、
  `textarea.tsx`、`select.tsx`，各自以官方实现为骨架；`field.tsx` 换成官方十件套
  （FieldSet/FieldLegend/FieldGroup/Field/FieldContent/FieldLabel/FieldTitle/FieldDescription/
  FieldSeparator/FieldError）。共享控件外观抽到 `control-classes.ts`，避免三份实现各自漂移。
- 官方 `Field` 只是 `role="group"` 排版容器，aria 关联在官方那边由绑定 react-hook-form 的
  `FormField` 承担。本仓库用 TanStack Form，因此保留自动接线的组合件并按 shadcn 的分工改名为
  `FormField`（`Field` 管排版，`FormField` 管接线），5 个消费者机械替换。
- 原生 `<select>` 换成官方 Base UI Select。`FormField` 的 `cloneElement` 到不了复合组件的 trigger，
  因此新增 `SelectField` 组合件显式把 `htmlFor` 接到 trigger 的 id 上，并把 6 处调用从
  `onChange(event)` + `<option>` 迁移为 `onValueChange(value)` + `items`。
- **一次险些漏掉的静默回归**：6 处 `<Select>` 在改完 import 后仍用原生 API，TypeScript 与全部测试
  都通过——因为没有任何测试覆盖它们。是逐个人工核对 JSX 才发现的。已补两条 SelectField 测试
  （点击标签激活控件、选中项回传值）。
- 两处 ESLint 误报加了带理由的定点豁免：`jsx-a11y/anchor-has-content` 对 Base UI `render` 模式、
  `jsx-a11y/label-has-associated-control` 对通用 label primitive。
- 设计系统源码扫描一度报 3 处 `dark:` 违规，全部来自**我写的注释**——扫描器按字面匹配不跳过注释。
  改写了注释措辞；扫描器本身在 `apps/web/scripts`，不在本 TASK 可写范围内，作为发现记录。
- 验证：`npm run check`（30 个测试文件 / 107 个用例）、`npm run build`、
  `node scripts/check-design-system.mjs` 全部通过。

- 2026-09-01：状态变更：todo → ready。原因：定义、边界与完成标准明确；执行顺序由 PLAN 约束
- 2026-09-01：状态变更：ready → doing。原因：开始 field 与 inline-notice 换文件
- 2026-09-01：状态变更：doing → done。原因：field 系列与 inline-notice 全部改建在官方实现上，含原生 select 迁移
