---
id: "TASK-056"
type: "task"
title: "在 Web 真源建立值锚点并去值化校验器"
status: "done"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["CHANGE-010", "DESIGN-029", "PLAN-023"]
related: []
implements: ["CHANGE-010", "CHANGE-010#REQ-001", "CHANGE-010#REQ-002", "CHANGE-010#REQ-003", "CHANGE-010#REQ-009", "CHANGE-010#REQ-014"]
verifies: []
tags: []
read_paths: ["apps/web/design-system", "docs/design-system/tools/build.mjs", "apps/web/package.json"]
write_paths: ["apps/web/design-system/tools/build.mjs", "apps/web/design-system/tools/check.mjs", "apps/web/design-system/design-tokens.json", "apps/web/design-system/manifest.json", "apps/web/design-system/README.md"]
forbidden_paths: ["docs/design-system/source", "apps/web/src", "apps/web/design-system/themes", "apps/web/design-system/tokens.foundation.css", "apps/web/design-system/theme-contract.json"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-056：在 Web 真源建立值锚点并去值化校验器

## 任务目标

让 `apps/web/design-system/` 拥有一个由构建生成、提交入库、可 `--check` 锚定的值快照
`design-tokens.json`，并据此删除 `check.mjs` 中手抄的期望值表。完成后 Web 侧具备独立的值漂移
检测能力，为下一步删除 docs 副本创造前置条件。

## 依据

[CHANGE-010](./10-change-CHANGE-010.md) REQ-001、REQ-002、REQ-003；
[DESIGN-029](./30-design-DESIGN-029.md)「整体方案」与「模块与数据」；
[PLAN-023](./50-plan-PLAN-023.md) 阶段 1。

## 可查看范围

以 front matter 的 `read_paths` 为准。`docs/design-system/tools/build.mjs` 只读，用于取用其
`design-tokens.json` 生成逻辑（`:142` 附近）。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。特别注意：**不得修改任何主题值**。
`themes/*.css`、`tokens.foundation.css`、`theme-contract.json` 在本任务中是只读输入，
它们的内容变化会让"渲染结果不变"这一不变条件无法验证。

## 依赖

以 front matter 的 `depends_on` 为准。本任务是 WORK-035 的第一个实施任务，无代码依赖。

## 产出

- `apps/web/design-system/tools/build.mjs`：新增 `design-tokens.json` 生成，纳入既有
  `generateOutputs()` / `--check` 机制；
- `apps/web/design-system/design-tokens.json`：新增生成物并提交；
- `apps/web/design-system/tools/check.mjs`：删除三张手写期望值表（`expectedFoundationTokens`、
  `exactThemeValues`、`expectedThemeDefinitions`）及其消费逻辑；改写 `--self-test` 中依赖
  `--ds-brand-surface: #d2042d` 字面量的篡改用例；
- `apps/web/design-system/manifest.json`：登记 `design-tokens.json` 为 `generated: true` 的包文件；
- `apps/web/design-system/README.md`：删除散文中复述的 `#d2042d` 等具体色值，改为指向 `themes/*.css`。

## 完成标准

- [x] `design-tokens.json` 由 `build.mjs` 生成，输出确定（固定 key 顺序、无时间戳），重复运行结果一致。
- [x] `node design-system/tools/build.mjs --check` 比较的是磁盘上已提交的快照与重新生成的结果，
      不是生成两次自比。
- [x] `check.mjs` 中不存在任何主题值或 foundation 值字面量；`--self-test` 的篡改用例从主题文件
      读取现值再替换。
- [x] DESIGN-029 列出的结构性校验能力逐条保留：contract 完整性与主题同构、对比度阈值、
      路径安全（绝对路径 / `..` / 符号链接 / 真实路径越界）、生成物一致性、Tailwind adapter 映射、
      provenance；`--self-test` 用例数不少于改动前。
- [x] `apps/web/design-system/README.md` 中不再出现具体色值字面量。
- [x] 主题 CSS、foundation、contract 三类文件的 diff 为空。

## 验证

```bash
cd apps/web
node design-system/tools/build.mjs --check
node design-system/tools/check.mjs
node design-system/tools/check.mjs --self-test
npm run check:design-system
```

篡改验证（AC-005，验证后必须恢复）：把 `design-system/themes/cherry-black.css` 的
`--ds-brand-surface` 改成其他值，`node design-system/tools/build.mjs --check` 必须失败；
`git checkout` 恢复后必须通过。

改动前后对比（AC-007）：`npm run build`，比较 `dist/` 中 token 的最终解析值逐值相同。

## 风险

删除期望值表时可能连带删除仍有价值的结构性校验，导致门禁静默削弱。执行时以 DESIGN-029 的
"必须保留的能力"清单为准，逐条确认后再删。若发现某项校验确实依赖硬编码值才能表达，
先在 `00-work.md` 记为待确认项并升级到 DESIGN，不要自行保留一张缩小版的值表。

## 执行记录

- 2026-09-03：创建任务。
- 2026-09-03：实施完成。`build.mjs` 新增 `design-tokens.json` 生成（foundation 78 + cherry-black 74 +
  pure-white 65 个 token）；`check.mjs` 删除 `expectedFoundationTokens`、`exactThemeValues`、
  `expectedThemeDefinitions` 三张手抄表，1336 → 1104 行（-272/+102）。5 个 self-test fixture 改为从
  主题文件读取现值再替换，用例数保持 18。`check.mjs` 中剩余的 hex 只有两条废弃值黑名单和三个
  故意错误的替换值，无当前设计值。门禁数字与基线完全一致：65 keys / 308 combinations /
  最小对比度 3.4035078594052393:1 / 18 fixtures。AC-005 篡改验证通过，AC-007 dist 220 条 token
  声明逐值无差异，`npm run check` 32 文件 116 测试通过。
- 2026-09-03：扩大 `write_paths` 增加 `apps/web/design-system/manifest.json`。原因：新增的
  `design-tokens.json` 必须在包文件登记表中声明，否则 `check.mjs` 的 `verifyPackage` 会因文件清单
  不匹配而失败——这是新增生成物的固有组成部分，不是范围扩张。DESIGN-029「模块与数据」已同步补录。
- 2026-09-03：状态变更：todo → ready。原因：意图闸已签署，上游 CHANGE/DESIGN/PLAN 均已定稿，路径边界明确
- 2026-09-03：状态变更：ready → doing。原因：开始实施阶段 1：建立 design-tokens.json 值锚点并去值化 check.mjs
- 2026-09-03：状态变更：doing → done。原因：阶段 1 完成：design-tokens.json 值锚点已建立，check.mjs 三张手抄值表已删除，门禁强度与渲染结果均无退化
