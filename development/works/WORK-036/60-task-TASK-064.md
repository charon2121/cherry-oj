---
id: "TASK-064"
type: "task"
title: "按 Linear 重新识别构图并改造列表页外壳"
status: "doing"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-062"]
related: []
implements: ["IMPROVEMENT-003"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system/source/claude-design-v1/ui_kits"]
write_paths: ["apps/web/src/components/ui", "apps/web/src/features/problems", "apps/web/e2e"]
forbidden_paths: ["apps/web/design-system", "contracts"]
created_at: "2026-09-04"
updated_at: "2026-09-04"
---

# TASK-064：按 Linear 重新识别构图并改造列表页外壳

## 任务目标

把列表页从"装在卡片里的后台数据表"改成"铺满的应用工作区"。用户对样板页给出的三处判断
——居中卡片、筛选像表单、有表头太像表格——逐条消除。

## 依据

[DESIGN-030](./30-design-DESIGN-030.md)「构图特征的重新识别（2026-09-04）」；
触发条件是 [DECISION-020](./40-decision-DECISION-020.md) C-2 的重审条款：样板被判不像时
回设计层重新识别，不继续迁移其余页面。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。含 `e2e`：本次**有意改变筛选控件的形态**，
与 TASK-062"只改表达不改行为"的前提不同，相关断言需要随之更新，并在执行记录说明改了哪几条、为什么。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。设计值不动。
**不动两个 Shell**：用户端是否改成左侧固定侧栏属于独立工作项，不在本任务内。

## 依赖

依赖 TASK-062 的样板页与用户判断。

## 产出

1. `DataList` 增加 `showHeader`（默认 `false`）与 `bleed`（默认 `false`）：
   - 无列头时直接就是行，列的含义由内容自明（mono 是标识、右对齐是度量）；
   - `bleed` 去掉圆角外框，边界交给 hairline 与留白。
2. `Toolbar` 改为列表的头部而不是浮在它上面的盒子：与列表之间不留边框间隙。
3. `ListPageTemplate` 增加 `width="full"`：不套 1200px 居中容器，列表左右贴主内容区边缘。
4. 筛选控件改为紧凑形态：去掉竖排标签，标签改由 `aria-label` 承担，触发器像 toolbar 按钮，
   显示"全部难度"这类当前值。保持 `role="combobox"` 与可访问名称。
5. 题库列表页应用以上四项。

## 完成标准

- [ ] 列表左右边缘贴主内容区边缘，页面上没有居中留白的空白带。
- [ ] 列表外层没有圆角边框；工具条与首行之间没有第二条边界。
- [ ] 默认无列头；`showHeader` 打开时列头仍可用（管理端数据表保留该能力）。
- [ ] 四个筛选控件不带竖排标签，横向占用显著小于改造前，仍是 `role="combobox"` 且有可访问名称。
- [ ] 筛选仍即时生效，没有提交按钮。
- [ ] 题库列表的业务行为、URL search 参数与 API 调用不变。
- [ ] 改动的 E2E 断言逐条说明原因；未涉及设计变化的断言不得改动。
- [ ] 两主题、320px、键盘、长中文验收通过。

## 验证

```bash
cd apps/web
npm run check && npm run build && npm run test:e2e && npm run storybook:build
```

产出改造前后的同画面截图交用户判断。判据是用户对三处问题的复核，不是测试是否通过。

## 风险

这是第二版。若仍被判不像，**不要继续在列表内部微调**：按 DESIGN-030 的重审条件，
下一个怀疑对象是应用外壳（顶部导航 vs 左侧固定侧栏），而那属于独立工作项，
需要新建 WORK 并重新走意图闸，不在 WORK-036 内扩大。

## 执行记录

- 2026-09-04：创建任务。用户判定第一版样板页不符合预期，指出居中卡片、筛选像表单、
  有表头三处，参照物为 Linear 本身。
- 2026-09-04：状态变更：todo → ready。原因：DESIGN-030 已记录重新识别，边界明确
- 2026-09-04：状态变更：ready → doing。原因：开始第二版构图改造
