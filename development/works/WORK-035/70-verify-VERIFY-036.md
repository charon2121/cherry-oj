---
id: "VERIFY-036"
type: "verify"
title: "收敛设计系统为单一真源"
status: "draft"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["TASK-056", "TASK-057", "TASK-058"]
related: []
implements: []
verifies: ["CHANGE-010", "TASK-056", "TASK-057", "TASK-058", "CHANGE-010#AC-001", "CHANGE-010#AC-002", "CHANGE-010#AC-003", "CHANGE-010#AC-004", "CHANGE-010#AC-005", "CHANGE-010#AC-006", "CHANGE-010#AC-007", "CHANGE-010#AC-008", "CHANGE-010#AC-009", "CHANGE-010#AC-010"]
tags: []
result: "pending"
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# VERIFY-036：收敛设计系统为单一真源

## 验证对象

WORK-035 三个阶段的实施结果：Web 真源的值锚点与去值化校验器（TASK-056）、文档树可执行副本的
删除（TASK-057）、叙述与来源引用的对齐（TASK-058）。

核心要证明两件事：**值只剩一处手写来源**，以及**渲染结果与门禁强度都没有退化**。

## 对应要求

覆盖 [CHANGE-010](./10-change-CHANGE-010.md) 的 AC-001 至 AC-010。实施完成后按下列命令
把逐条锚定写入 front matter：

```bash
scripts/work link VERIFY-036 --relation verifies --to CHANGE-010#AC-001
# ... AC-002 至 AC-010 同样逐条锚定
```

| 验收标准 | 验证手段 | 结果 |
|---|---|---|
| AC-001 被删路径引用数为 0 | `git grep` 扫描 | 待执行 |
| AC-002 Web 全套命令与改动前一致 | `npm ci && check && build && storybook:build && test:e2e` | 待执行 |
| AC-003 移走 `docs/` 后 Web 命令仍通过 | 隔离验证 | 待执行 |
| AC-004 主题值在非来源非生成物文件中只出现 1 次 | `git grep -c` 计数 | 待执行 |
| AC-005 篡改主题值会让 `build.mjs --check` 失败 | 真实篡改后恢复 | 待执行 |
| AC-006 `check.mjs` 无主题值字面量 | 人工通读 + grep | 待执行 |
| AC-007 改动前后 token 解析值逐值相同 | `dist/` 对比 | 待执行 |
| AC-008 冻结来源未变 | `source-lock.mjs --check` | 待执行 |
| AC-009 `sourceRefs` 路径真实存在 | 脚本逐条核对 | 待执行 |
| AC-010 文档检查通过、无死链 | `scripts/work check` + 文档检查 | 待执行 |

## 检查与结果

待执行。实施完成后在此记录实际命令、环境（Node / npm 版本、操作系统）、完整输出摘要和结论，
不使用"应该通过"这类推断表述。

AC-005 与 AC-007 必须记录**改动前的基线数据**，否则"没有退化"无法证明：

- AC-007 基线：改动前执行 `npm run build`，导出 `dist/` 中 `--ds-*` 的最终解析值清单存档；
- 门禁基线：改动前记录 `check.mjs --self-test` 的用例数与 `check.mjs` 校验项清单。

## 未通过项

暂无。

## 范围检查

待补充：确认三个 TASK 各自只修改了 front matter 允许的路径，特别确认

- TASK-056 未改动 `themes/*.css`、`tokens.foundation.css`、`theme-contract.json`；
- TASK-057 未改动 `apps/web` 与 `source/`；
- TASK-058 未改动 WORK-034 目录与 `apps/web`。

## 遗留问题

已知但**明确不在本次范围**、留给 WORK-036 的问题：

- `--ds-fg-muted` / `--ds-fg-meta` / `--ds-fg-disabled` 在 `cherry-black` 中同为 `#8a8f98`，
  在 `pure-white` 中为 `#62666d` / `#676b73` / `#676b73`，来源的四档文字层级实际塌成两档半，
  且 disabled 与普通 metadata 视觉不可区分；
- `components.manifest.json` 声明的 `data-table-list`、`app-shell-navigation`、`editor-workspace`、
  `submission-lifecycle`、`verdict` 五个合同在 `apps/web/src` 中没有实现；
- 业务页面中存在约 807 处 `var(--ds-*)` 直接消费，Tailwind adapter 的 alias 覆盖面不足；
- 冻结来源自带的 `_adherence.oxlintrc.json` 逐组件 prop/variant 白名单尚未接入项目 lint。
  推迟理由：WORK-036 会重建组件层，prop 表届时会变，现在接入等于要写两遍。

## 剩余风险

待补充。至少需要回答：删除文档副本后，未来修改设计系统的人是否还能找到足够的追溯材料；
新的值锚点在多人/多智能体并发修改时是否足够。

## 结论

尚未验证。
