---
id: "PLAN-017"
type: "plan"
title: "组件实现文件替换计划"
status: "checked"
work: "WORK-027"
owners: ["codex/root"]
depends_on: ["CHANGE-009", "DESIGN-021"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# PLAN-017：组件实现文件替换计划

## 目标

在不改变任何页面可见行为的前提下，把六个手写基础组件的实现文件换成 shadcn base-nova 官方版本
（下文简称「换文件」，定义见 [DESIGN-021 术语](./30-design-DESIGN-021.md#术语)），并移除
与真实组件长期漂移的手工参考页。落实 [CHANGE-009](./10-change-CHANGE-009.md) 的 REQ-001～REQ-006。

## 改动区域

| 区域 | 内容 |
|---|---|
| `apps/web/src/components/ui/` | badge、card、icon-button、async-state、field、inline-notice 及其 story 与 test |
| `apps/web/src/{features,routes,app}/` | 12 个消费者文件的最小适配 |
| `docs/design-system/` | 删除 `components.html`，同步 `components.manifest.json` |
| `docs/design-system.md` | §1、§6 的视觉参考指向 |
| `docs/frontend.md` | 组件优先级条款写实 |

`apps/web/design-system/`、`contracts/`、`apps/server`、`apps/judge-engine` 全程禁止修改。

## 阶段与顺序

1. **TASK-043** 移除手工参考页并把视觉参考改指 Storybook，同时把组件优先级原则写进 `docs/frontend.md`。
2. **TASK-044** 展示类四个组件换文件：badge、card、icon-button、async-state。
3. **TASK-045** 表单与提示类两个组件换文件：field、inline-notice。

顺序理由：TASK-043 与代码无关，先做能立刻消除一个漂移源；TASK-044 的组件没有 aria 关联，用它验证
「取官方实现 → 换 token → 追加 OJ 变体」这条流程是否顺畅；确认流程成立后再做风险最高的 TASK-045。

## 并行与依赖

TASK-043 与另外两个无依赖，可并行。TASK-044 与 TASK-045 都改 `components/ui/` 与消费者文件，
**不并行**——同时改会产生难以区分的视觉回归来源。TASK-045 依赖 TASK-044 完成，以复用其确认过的改法。

三个任务都依赖 DESIGN-021 定稿。

## 迁移与交付

无数据迁移。交付方式是合入 main，由 CI 的 web job 把关；MVP 阶段没有生产环境，不涉及发布动作。
每个 TASK 自成一个可独立回退的 commit：TASK-043 只动文档，TASK-044/045 各自只动自己那批组件与消费者。

## 风险

- **视觉回归难以归因**：两个组件改造任务并行会让回归无法定位到具体组件，因此强制串行。
- **可访问性静默回退**：`field` 的 aria 关联从 `cloneElement` 改为官方子组件，丢失后视觉上看不出来。
  由 AC-002 的测试覆盖，不靠肉眼验收。
- **范围蔓延**：官方组件带来新子组件和变体，容易顺手用进业务页面。约束是消费者改动仅限必需适配，
  且必须在 VERIFY 中逐条列出。

## 验证

每个 TASK 完成后跑 `npm run check` 与 `npm run build`；全部完成后统一执行 CHANGE-009 的 AC-001～AC-006，
包括双主题 Storybook a11y、Playwright 双主题 E2E，以及「删除整个 `docs/design-system/` 后前端仍全绿」
这一项反向依赖验证。结果写入 [VERIFY-028](./70-verify-VERIFY-028.md)。

## 回退

每个 TASK 一个 commit，`git revert` 即可单独撤销，不存在数据或部署侧的不可逆动作。若 TASK-045 中
`field` 的 aria 行为无法保持一致，单独回退该 commit 不影响已完成的 TASK-043 与 TASK-044。

## 变更记录

- 2026-09-01：状态变更：draft → review。原因：初稿写完，提交人工审核
- 2026-09-01：结构与内容校验通过，由工具置为 checked。
