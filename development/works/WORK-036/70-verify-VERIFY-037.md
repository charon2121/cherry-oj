---
id: "VERIFY-037"
type: "verify"
title: "建立页面构图层并修复前景色层级"
status: "draft"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-059"]
related: []
implements: []
verifies: ["IMPROVEMENT-003", "TASK-059", "IMPROVEMENT-003#AC-001", "IMPROVEMENT-003#AC-002", "IMPROVEMENT-003#AC-003", "IMPROVEMENT-003#AC-004", "IMPROVEMENT-003#AC-005", "IMPROVEMENT-003#AC-006", "IMPROVEMENT-003#AC-007", "IMPROVEMENT-003#AC-008", "IMPROVEMENT-003#AC-009", "IMPROVEMENT-003#AC-010", "IMPROVEMENT-003#AC-011", "IMPROVEMENT-003#AC-012"]
tags: []
result: "pending"
created_at: "2026-09-03"
updated_at: "2026-09-03"
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
| AC-001 业务层 `var(--ds-` 为 0 且 lint 能拦 | `git grep` + 负向 fixture | 待执行 |
| AC-002 `components/ui/` 用量显著下降 | 计数对比基线 807 | 待执行 |
| AC-003 四档全部 ≥4.5:1 | 逐对实测表 | 待执行 |
| AC-004 disabled 与 meta 可区分 | 取值对比 + 截图 | 待执行 |
| AC-005 DataList 列对齐、320px 不裁切、键盘可达 | 截图 + a11y | 待执行 |
| AC-006 Toolbar 即时过滤、无提交按钮 | 交互验证 | 待执行 |
| AC-007 样板页同画面对照 | 双图并排 + 差异清单 | 待执行 |
| AC-008 题库列表行为与 URL 参数不变 | 既有断言不改即通过 | 待执行 |
| AC-009 两主题/320px/200%/键盘/长中文/forced-colors/reduced-motion | 逐项 | 待执行 |
| AC-010 未迁页面外观复查 | 两主题逐路由截图 | 待执行 |
| AC-011 构图合同可判定 + 入口指向 | 人工检验 | 待执行 |
| AC-012 全量门禁 | `npm run check` 等 | 待执行 |

## 检查与结果

待执行。记录实际命令、环境、输出摘要与结论，不使用"应该通过"这类推断表述。

**必须先采集的基线**（否则"没有退化"无法证明）：

- 四档调整前，两个主题各 surface 上的前景色实测对比度；
- `git grep -c "var(--ds-"` 在 `src` 与 `src/components/ui` 的分别计数（当前 807 / 待分解）；
- 改动前两个主题下全部路由的截图；
- `check.mjs --self-test` 与源码检查 self-test 的用例数。

**AC-007 的证据形式**：迁移后的 `/problems` 与 `ui_kits/app/ProblemList.jsx` 在
1440 与 320 两个宽度下的并排截图，外加一份差异清单，逐条标注"有意适配"或"待修"。
只贴一张迁移后截图不构成对照。

## 未通过项

暂无。

## 范围检查

待补充：确认五个 TASK 各自只修改了 front matter 允许的路径，特别确认

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

待补充。至少需要回答：四档调整对未迁页面是否真的没有可读性回归（不能只看题库列表）；
lint 上线后是否存在 alias 覆盖不到、开发被迫绕行的场景；样板页被认可后，
其余页面迁移的工作量估计是否与预期一致。

## 结论

尚未验证。
