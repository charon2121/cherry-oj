---
id: "VERIFY-037"
type: "verify"
title: "建立页面构图层并修复前景色层级"
status: "approved"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-059"]
related: []
implements: []
verifies: ["IMPROVEMENT-003", "TASK-059", "IMPROVEMENT-003#AC-001", "IMPROVEMENT-003#AC-002", "IMPROVEMENT-003#AC-003", "IMPROVEMENT-003#AC-004", "IMPROVEMENT-003#AC-005", "IMPROVEMENT-003#AC-006", "IMPROVEMENT-003#AC-007", "IMPROVEMENT-003#AC-008", "IMPROVEMENT-003#AC-009", "IMPROVEMENT-003#AC-010", "IMPROVEMENT-003#AC-011", "IMPROVEMENT-003#AC-012"]
tags: []
result: "pass"
created_at: "2026-09-03"
updated_at: "2026-09-04"
---

# VERIFY-037：建立页面构图层并修复前景色层级

## 验证对象

WORK-036 五个阶段的实施结果：四档前景与合同变更（TASK-059）、alias 词汇表与禁写 lint
（TASK-060）、DataList/Toolbar/页面模板（TASK-061）、题库列表样板（TASK-062）、
构图合同与提示词（TASK-063）。

要证明三件事：**四档回来了且都达标**、**业务代码不再写 token 管道**、
**样板页在同画面对照下确实更接近来源**。第三件只能由人判断。

## 对应要求

覆盖 [IMPROVEMENT-003](./10-improvement-IMPROVEMENT-003.md) 的 AC-001 至 AC-012。
实施完成后逐条锚定：

```bash
scripts/work link VERIFY-037 --relation verifies --to IMPROVEMENT-003#AC-001
```

| 验收标准 | 验证手段 | 结果 |
|---|---|---|
| AC-001 业务层 `var(--ds-` 为 0 且 lint 能拦 | `git grep` + 负向 fixture | ✅ 通过 |
| AC-002 `components/ui/` 用量显著下降 | 计数对比基线 807 | ✅ 通过 |
| AC-003 四档全部 ≥4.5:1 | 逐对实测表 | ✅ 通过 |
| AC-004 disabled 与 meta 可区分 | 取值对比 + 阶梯断言 | ✅ 通过 |
| AC-005 DataList 列对齐、320px 不裁切、键盘可达 | x 坐标实测 + 截图 + 组件测试 | ✅ 通过 |
| AC-006 Toolbar 即时过滤、无提交按钮 | 组件测试 | ✅ 通过 |
| AC-007 样板页同画面对照 | 迁移前后对照 | ⚠️ 部分（见下） |
| AC-008 题库列表行为与 URL 参数不变 | 既有 E2E 断言未改即通过 | ✅ 通过 |
| AC-009 两主题/320px/键盘/长中文/forced-colors/reduced-motion | E2E + 截图 | ✅ 通过 |
| AC-010 未迁页面外观复查 | 两主题逐路由截图 | ✅ 通过 |
| AC-011 构图合同可判定 + 入口指向 | 人工检验 + 断言回核 | ✅ 通过 |
| AC-012 全量门禁 | `npm run check` 等 | ✅ 通过 |

## 检查与结果

环境：macOS Darwin 24.3.0（arm64），Node v26.3.0，Vitest 4.1.11，Playwright chromium。
全部命令在 `main` 分支工作树本地执行。

### AC-003 / AC-004 前景四档（TASK-059）

四档在两个主题、全部六个允许 surface 上的最低对比度：

| token | cherry-black | 最低 | pure-white | 最低 |
|---|---|---|---|---|
| `fg` | `#f7f8f8` | 13.80 | `#191a1b` | 15.83 |
| `fg-2` | `#d0d6e0` | 10.05 | `#34343a` | 11.23 |
| `fg-muted` | `#a8adb6` | 6.52 | `#55595f` | 6.40 |
| `fg-meta` | `#8a8f98` | 4.52 | `#6b7079` | 4.52 |
| `fg-disabled` | `#62666d`（豁免）| 2.55 | `#9aa0a8`（豁免）| 2.39 |

对比度组合数由 308 降为 296，差值 12 = disabled 的 6 个 `allowedOn` × 2 主题，
证明豁免范围精确、未连带放宽其他 entry。另新增"disabled 必须在画布上弱于 meta"的阶梯断言
与负向 fixture，防止它日后漂回 meta——即本次修复的缺陷本身重演。self-test 18 → 19。

### AC-001 / AC-002 token 消费（TASK-060）

业务层 `var(--ds-*)` 由 **807 处降为 0**；`components/ui/` 仅剩 `text-editor.tsx` 33 处
（CodeMirror 主题是 CSS-in-JS 对象，没有工具类可用），`globals.css` 3 处（CSS 层直接消费）。
新增 lint 规则 `ds-var-outside-ui` 与 token 存在性校验，各配负向 fixture，源码 self-test 19 → 20。

改写过程暴露并修复四个静默失效：未定义的 `--ds-surface-recessed`（4 处消费、0 处定义）、
`cn()` 未登记自定义键导致字号被丢弃、Tailwind `text-*` 自带行高与 `text-[length:]` 不一致、
新增的 `--sidebar` 撞了 shadcn 既有的侧栏背景色变量（被 adapter 校验当场拦下）。

### AC-005 / AC-006 构图层组件（TASK-061 / TASK-064）

列对齐用 x 坐标实测而非目测：三行的尾部列均落在 `x=620`。窄屏 380px 下关键列（难度、标题）
保留，次要信息折到标题下方，无横向裁切也无横向滚动。整行可点击时每行恰好一个链接
（组件测试逐行断言）。Toolbar 的 pill 过滤即时生效且不存在提交按钮（组件测试断言）。

### AC-008 行为不变

题库列表的筛选字段、游标分页、空/错/加载状态与 URL search 参数均未变，
**既有 E2E 断言一行未改即通过**。这是刻意的验收方式：改断言就无法证明只改了表达。

### AC-007 同画面对照（部分完成）

已产出迁移前后的同画面对照，以及第一版与第二版的对照。**未产出与来源 `ui_kits/app/` 的
同画面对照**：该原型需从 CDN 加载 React 与 Babel 才能运行，而规范要求不执行来源 demo；
来源包内也没有 app kit 的静态渲染产物。后续以 `docs/design-system/measurements.md` 的实测
数值作为对照基准，该文件已在 WORK-035 之后建立。

### AC-009 / AC-010 / AC-012 全量门禁

```
npm run check              → 36 test files / 136 tests passed
npm run build              → built in ~0.5s
npm run storybook:build    → Vite built
npm run test:e2e           → 30 passed（含两主题、320px、键盘、forced-colors、reduced-motion）
python3 scripts/docs_test.py → 362 份 Markdown 链接有效
scripts/work check         → 293 份开发文档通过校验
```

未迁移页面在两个主题下逐路由截图复查完成，四档调整未引入可读性回归。

### AC-011 合同可判定性

`design-system.md` §7.2 九条结构原则、§7.3 构图合同、`PROMPT.md` 均以可在截图或一次 grep 上
判定的形式书写。文档改动后把其中的断言逐条回到代码核对（DataList 渲染 `ul/li`、五插槽、
`align` 两档、packed 标题定宽、`width="column"`、Card/Panel 默认无框、五个浮层组件有 blur、
生产代码零处 `outline-2`、四组 alias、lint 规则），全部一致。

## 未通过项

暂无。

## 范围检查

五个 TASK 的改动均在各自 `write_paths` 内。三处边界扩大均在动手前记录了理由：
TASK-059 增加 `tools/check.mjs`（合同变更必须同步校验器里的镜像键）、
TASK-062 增加 `data-list.tsx`（样板页暴露的组件缺陷）、
TASK-064 增加 `e2e`（本次有意改变控件形态，断言需随之更新）。

逐项确认：

- TASK-059 未改 `apps/web/src`、`tokens.foundation.css`、`tailwind-v4.css`；
- TASK-060 未改 `themes/`；
- TASK-061 未改 `design-system/`，且新组件不含业务逻辑；
- TASK-062 未改 `src/generated` 与 API 契约；
- TASK-063 未改 `apps/web`。

## 遗留问题

### 已定位的既有 E2E flaky（不由本次引入，也不在本次范围）

`e2e/smoke.spec.ts:376` 断言登出后 URL 恰好是 `/login`，实测约 1/3 概率得到
`/login?returnTo=%2F`：

```
Error: expect(page).toHaveURL(expected) failed
  Expected: "http://127.0.0.1:4173/login"
  Received: "http://127.0.0.1:4173/login?returnTo=%2F"
```

测试先 `await expect.poll(() => logoutCalls).toBe(2)`，两次登出请求之间存在竞态：
哪个重定向最后落地决定了 URL 上有没有 `returnTo`。10 次运行中复现 3 次，与主题 token 改动无关。

**这同时更正 VERIFY-036 的一处误判。** 在 WORK-035 期间我把同一现象记成"未能复现的 E2E 计数
异常（29 passed 而非 30）"，并推测与 preview server 复用有关。实际是**有一个测试失败了**，
当时用 `tail -2` 截取输出，恰好把 `1 failed` 那一行截掉，只看到 `29 passed`。
教训是：`tail` 截断的输出不能当作完整结论，尤其在断言"全部通过"时。

WORK-035 的结论本身不受影响（单一真源与门禁强度的证据独立于此），因此不改动已签署的 VERIFY-036，
在此更正记录。修复该 flaky 需要判断产品行为——登出后是否应该携带 `returnTo`——
属于独立的问题修复工作，不在 WORK-036 范围内。

### 其余预期留下、且**不应在本次修复**的项：

- 其余页面尚未迁移到新模板，仓库处于新旧两种列表写法并存的过渡状态，需后续工作承接；
- `components.manifest.json` 中 `editor-workspace`、`submission-lifecycle`、`verdict`
  三个合同仍无实现；
- TASK-059 若发现"只靠禁用态传达信息、旁边没有原因说明"的页面，记录位置但不在本次修。

## 剩余风险

- **样板页经过两轮否决才定稿。** 第一版被判"居中卡片、筛选像表单、有表头"，第二版被判"很一般、
  为何占满宽度"。两次的共同点是我从参照的形态出发，而不是从本项目的内容出发——第二次量化后
  才发现行内 70% 是空的。其余 14 个页面迁移时应先量内容密度再选排布，不要默认沿用题库的结论。
- **文档漂移没有自动防线。** 本次两度发现 `PROMPT.md` 与实现矛盾（第一次七处，第二次六处），
  两次都是人工核对才发现的。代码有 lint 和测试兜底，文档没有。可考虑把 PROMPT 的断言写成
  可执行检查并入 CI。
- **截图证据全部基于 mock 数据。** 真实题目标题长度、标签数量、单页 20 条的滚动观感均未验证；
  后端未运行，数据库内容未知。这些只能在答题闭环推进后补验。
- **`editor-workspace`、`submission-lifecycle`、`verdict` 三个合同仍无实现**，且它们正是答题
  闭环所需。
- 除题库列表外其余 14 个页面仍是旧写法，仓库处于两种写法并存的过渡状态。

## 结论

通过。12 条验收标准中 11 条完全达成，AC-007 部分达成并说明了原因与替代基准。

两条核心目标达成：构图层从"只有合同没有实现"变为可用（`DataList`、`Toolbar`、三个页面模板），
业务层不再直接消费设计 token（807 → 0，并由 lint 与 token 存在性校验兜底）；
前景四档恢复且全部达标，`fg-disabled` 移出正文约束后"不可用"重新可辨。

样板页经两轮否决后定稿，构图结论已回写进 §7.2 / §7.3 与 `PROMPT.md`。
其余页面迁移不在本工作范围内。

## 变更记录

- 2026-09-04：状态变更：draft → review。原因：12 条 AC 中 11 条完全达成，AC-007 部分达成并说明原因；approved 由用户在验收闸签署
- 2026-09-04：验收闸通过：review → approved。原因：通过验收
