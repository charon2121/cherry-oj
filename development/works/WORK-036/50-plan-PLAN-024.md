---
id: "PLAN-024"
type: "plan"
title: "建立页面构图层并修复前景色层级"
status: "checked"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["IMPROVEMENT-003", "DESIGN-030", "DECISION-020"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# PLAN-024：建立页面构图层并修复前景色层级

## 目标

按 [DESIGN-030](./30-design-DESIGN-030.md) 补齐 alias 词汇表、修复四档前景、建立 DataList /
Toolbar / 三个页面模板，并用题库列表做一个可供人工判断的样板。

## 改动区域

- `apps/web/design-system/`：`tailwind-v4.css`、`theme-contract.json`、`themes/*.css`、
  重新生成 `design-tokens.json`；
- `apps/web/src/components/ui/`：新增 `data-list`、`toolbar`、`page-templates` 及其
  stories/tests；既有组件改为消费 alias；
- `apps/web/src/features/problems/`：题库列表迁移；
- `apps/web/scripts/check-design-system.mjs`：新 lint 规则与 fixture；
- `docs/design-system.md`、新增 `docs/design-system/PROMPT.md`、
  `docs/design-system/components.manifest.json`、`CLAUDE.md`、`AGENTS.md`。

不进入本次范围：其余页面的迁移、`apps/server`、`apps/judge-engine`、`contracts/`。

## 阶段与顺序

**阶段 1 — 四档前景与合同（TASK-059）**
1. `theme-contract.json` 把 `--ds-fg-disabled` 的 `contrastClass` 改为 `decorative`；
2. 两个主题按 DECISION-020 的表调整 `fg-muted`、`fg-meta`、`fg-disabled`；
3. 重新生成 `design-tokens.json`，跑 `build.mjs --check` 与 `check.mjs`；
4. 记录四档在全部允许 surface 上的实测对比度。

出口条件：四档均 ≥4.5:1；`fg-disabled` 与 `fg-meta` 取值不同；设计系统门禁通过。
先做这一步是因为它独立于组件，且样板页对照必须基于正确的层级。

**阶段 2 — alias 词汇表与 lint（TASK-060）**
1. 按 DESIGN-030 的表补 `tailwind-v4.css` 的 `@theme inline`；
2. 把 `src/components/ui/` 与业务层现有的 `var(--ds-*)` 改写为 alias；
3. 新增 lint 规则 `forbidden-ds-var-outside-ui` 与负向 fixture、`--self-test` 用例；
4. 上线前全量扫描确认业务层剩余违例为 0。

出口条件：业务层 `var(--ds-` 计数为 0；lint 能拦住新增违例；外观无变化（截图对照）。

**阶段 3 — 构图层组件（TASK-061）**
1. 查 shadcn registry 是否有 `table`，有则以官方为骨架（记入执行记录）；
2. 实现 `DataList`（列定义、priority 折行、状态点、行语义、键盘）；
3. 实现 `Toolbar`（两行 anatomy、即时 pill 过滤、搜索标签）；
4. 实现三个页面模板；
5. 每个组件补 stories 与 tests，覆盖两主题、状态矩阵、320px、键盘。

出口条件：Storybook 中三层组件齐备并通过 a11y；组件内部不出现业务逻辑。

**阶段 4 — 样板页与构图合同（TASK-062、TASK-063）**
1. TASK-062：题库列表迁移到新模板与组件，业务行为与 URL 参数不变；
   产出与 `ui_kits/app/ProblemList.jsx` 的同画面对照截图；
2. TASK-063：`design-system.md` 增加构图合同一节、新增 `PROMPT.md`、
   更新 `components.manifest.json` 的 `data-table-list` 合同、`CLAUDE.md` 与 `AGENTS.md` 路由。

出口条件：对照截图已产出并逐条说明差异；文档规则可在截图上判定。

## 并行与依赖

阶段 1 与阶段 2 之间无代码依赖，但按顺序串行以保证每个提交的外观变化可归因：阶段 1 只改颜色，
阶段 2 只改写法且外观不变。阶段 3 依赖阶段 2 的 alias。阶段 4 依赖阶段 3。
TASK-063 可与 TASK-062 并行起草，但必须在样板页定稿后再定稿文档。

## 迁移与交付

无数据迁移。每阶段一个提交，各自可独立编译并通过 `npm run check`。交付方式为合入 `main`。

**样板页交付后必须停下来等用户人工判断**，不要顺势开始迁移其余页面——这是本次工作
最重要的交付纪律，也是 DECISION-020 C-2 的全部意义。

## 风险

- 阶段 1 改变全部页面的次要文字外观，而验证集中在题库列表。缓解：AC-010 要求两主题下对未迁
  页面做外观复查，范围与结论记入 VERIFY。
- 阶段 2 的改写量大（807 处），容易在批量替换中引入外观偏差。缓解：改写前后各截一组图对照，
  且该阶段的验收标准是"外观零变化"。
- 阶段 3 的 DataList 响应式降级可能破坏长中文可读性。缓解：320px 与长中文是该阶段出口条件，
  不达标就按 DESIGN-030 的重审条件退回。
- 阶段 4 的判断只能由人做。缓解：不把"测试全绿"当成通过，交付即停。

## 验证

- 阶段 1：`build.mjs --check`、`check.mjs`、`check.mjs --self-test`；四档对比度实测表。
- 阶段 2：`git grep -c 'var(--ds-'` 业务层为 0；lint 负向 fixture 失败、移除后恢复；
  改写前后 `dist/` token 解析值与页面截图对照。
- 阶段 3：Storybook a11y、组件测试、两主题 × 状态矩阵 × 320px × 键盘。
- 阶段 4：题库列表既有 E2E 与组件测试**不改断言**通过；同画面对照截图；
  两主题、200%、forced-colors、reduced-motion、长中文。
- 全局：`npm run check`、build、storybook:build、test:e2e、`docs_test.py`、`scripts/work check`。

结果逐条记入 [VERIFY-037](./70-verify-VERIFY-037.md)。

## 回退

每阶段一个提交，`git revert` 可单独回退。阶段 1 的回退会同时恢复旧的三档合一取值；
阶段 4 被否时优先回退阶段 4 并保留 1-3（alias 与组件本身有独立价值），
除非判断结论指向构图合同本身错误，那时按 DESIGN-030 的重审条件重做阶段 3。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：五阶段计划已写完
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
