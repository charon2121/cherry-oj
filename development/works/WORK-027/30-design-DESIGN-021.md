---
id: "DESIGN-021"
type: "design"
title: "组件基座迁移方案"
status: "review"
work: "WORK-027"
owners: ["codex/root"]
depends_on: ["CHANGE-009"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# DESIGN-021：组件基座迁移方案

## 背景

见 [CHANGE-009](./10-change-CHANGE-009.md)。核心事实：16 个基础组件里 6 个从零手写，而 shadcn base-nova
官方 registry 有对应实现；另有一份 1381 行手工维护的组件参考页与真实组件长期漂移。

判断「有没有官方对应」不靠印象，靠 registry 探测：`https://ui.shadcn.com/r/styles/base-nova/<name>.json`
返回 200 即存在。`typography` 与 `link` 返回 404，因此保留手写是有依据的，不是偷懒。

## 目标与限制

目标是把六个组件的**基座**换成官方实现，而不是重写它们的外观。限制有三条：

- 只替换颜色、尺寸相关 class 为语义 token 或稳定 alias，不改官方的 DOM 结构与 slot 命名；
- OJ 语义是**叠加**而非替换：官方变体全部保留，`success`/`warning`/`danger`/`info`/`special` 追加；
- 消费者的调用形态尽量不动；确需适配时，适配点必须可枚举、可在 VERIFY 中逐条列出。

## 整体方案

三步，每步独立可回退：

1. **移除手工参考页**（不依赖后两步）。删除 `components.html`，`components.manifest.json` 的
   `reference` 与 `sourceFiles` 改为指向 Storybook，`docs/design-system.md` §1、§6 同步。
2. **展示类组件改基座**：`badge`、`card`、`icon-button`、`async-state`。这四个没有复杂交互与 aria 关联，
   回归面集中在视觉。
3. **表单与提示类改基座**：`field`、`inline-notice`。`field` 有 7 个消费者且承担 aria 关联，风险最高，
   单独一步做完再验。

每个组件的改法固定：`npx shadcn@latest add <name>` 取官方实现到临时位置 → 逐行比对现有实现 → 以官方为
骨架重建，把官方的 `bg-primary`、`text-muted-foreground` 等替换为本仓库语义 token → 追加 OJ 变体 →
更新 story 与测试 → 跑消费者测试。

## 模块与数据

只涉及 `apps/web/src/components/ui/` 与其 story、test，以及 12 个消费者文件。无数据结构、无持久化、
无接口变化。`apps/web/design-system/` 的 token、主题、manifest 与合同**不动**——本次改的是 token 的
消费方，不是 token 本身。

## 接口与状态

组件对外 API 以「不变」为默认。允许变化的只有两处，且都必须在 VERIFY 列出：

- `async-state` 拆成官方的 `spinner` + `empty` 两个组件，调用处需要相应拆分；
- `field` 从单一封装改为官方的可组合子组件（`Field`/`FieldLabel`/`FieldDescription`/`FieldError` 等），
  消费者写法随之变化。

`icon-button` 并入 `button` 的 icon size 后，保留一个薄封装或直接改调用处，二选一在实施时按实际
diff 大小决定，并在 TASK 执行记录里说明选了哪个及原因。

## 安全与失败

无安全边界变化。失败模式集中在可访问性回归：`field` 若丢失 `aria-describedby` / `aria-invalid` 关联，
屏幕阅读器用户将读不到错误原因，而视觉上完全看不出来。因此 AC-002 要求这三项由测试覆盖，不靠肉眼。

## 监控与部署

无部署动作。质量门禁沿用现有：`npm run check`、`npm run build`、Storybook a11y addon、Playwright 双主题
E2E。AC-005 额外要求验证「删除整个 `docs/design-system/` 后前端仍然全绿」，确认文档树没有反向依赖。

## 迁移与兼容

无数据迁移。兼容性风险是消费者写法变化，处理方式是同一 TASK 内改完组件与其全部消费者，不留中间态。
两个主题必须同时验证——只验默认主题会漏掉 `pure-white` 的对比度问题。

## 备选方案

1. **保持手写，只优化外观**：改动最小，但违背项目既定原则，且下一个组件仍会被手写，问题会反复出现。
   否决。
2. **全部 16 个组件一次性对齐官方**：包括已基于 base-ui 的 7 个。范围过大，且那 7 个已有正确基座，
   收益远小于风险。改为只记录差异（REQ-003），留待后续按需处理。
3. **保留 `components.html` 并改用真实组件渲染后的 HTML 快照**：需要新增导出流程与漂移校验，
   而 Storybook 已经提供同样的能力。否决。

## 风险与重审条件

最大风险是「只换基座」在实施中滑向「顺便改设计」。官方组件的默认外观与现有实现必然有差异，取舍点会
反复出现。约束是：任何视觉差异要么消除，要么在 VERIFY 中写明为何接受。

出现以下情况应停下重审而不是继续：官方实现与本仓库 token 体系存在结构性冲突（例如官方依赖某个本仓库
没有的语义层）；或 `field` 改造导致三条表单链路中任意一条的键盘行为无法保持一致。

## 变更记录

- 2026-09-01：状态变更：draft → review。原因：初稿写完，提交人工审核
