---
id: "VERIFY-036"
type: "verify"
title: "收敛设计系统为单一真源"
status: "approved"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["TASK-056", "TASK-057", "TASK-058"]
related: []
implements: []
verifies: ["CHANGE-010", "TASK-056", "TASK-057", "TASK-058", "CHANGE-010#AC-001", "CHANGE-010#AC-002", "CHANGE-010#AC-003", "CHANGE-010#AC-004", "CHANGE-010#AC-005", "CHANGE-010#AC-006", "CHANGE-010#AC-007", "CHANGE-010#AC-008", "CHANGE-010#AC-009", "CHANGE-010#AC-010"]
tags: []
result: "pass"
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
| AC-001 被删路径引用数为 0 | `git grep` 扫描 | ✅ 通过 |
| AC-002 Web 全套命令与改动前一致 | check / build / storybook / e2e | ✅ 通过 |
| AC-003 移走 `docs/` 后 Web 命令仍通过 | 隔离验证 | ✅ 通过 |
| AC-004 主题值只在 `themes/*.css` 与生成物 | `git grep -l` | ✅ 通过（见下方修正说明）|
| AC-005 篡改主题值会让 `build.mjs --check` 失败 | 真实篡改后恢复 | ✅ 通过 |
| AC-006 `check.mjs` 无主题值字面量 | grep + 人工通读 | ✅ 通过 |
| AC-007 改动前后 token 解析值逐值相同 | `dist/` 对比 | ✅ 通过 |
| AC-008 冻结来源未变 | `source-lock.mjs --check` | ✅ 通过 |
| AC-009 `sourceRefs` 路径真实存在 | 脚本逐条核对 | ✅ 通过 |
| AC-010 文档检查通过、无死链 | `scripts/work check` + `docs_test.py` | ✅ 通过 |

## 检查与结果

环境：macOS Darwin 24.3.0（arm64），Node v26.3.0，npm 11.x，Vitest 4.1.11，Playwright chromium。
全部命令在 `main` 分支工作树本地执行，基线取自实施前的 `ec260bd`。

### 门禁基线与实测对比

改动前后 `npm run check:design-system` 的输出逐项相同：

| 指标 | 基线（ec260bd） | 现在 |
|---|---|---|
| theme contract required keys | 65 across 2 themes | 65 across 2 themes |
| contrast allowed combinations | 308 | 308 |
| 最小对比度 | 3.4035078594052393:1（pure-white `--ds-border-strong` on `--ds-surface-hover`）| 同 |
| `check.mjs --self-test` 负向 fixture | 18 | 18 |
| 源码检查 self-test | 19 源码 + 1 符号链接 + 1 生成漂移 + 3 生成路径 + 7 合法 | 同 |

`check.mjs` 1336 → 1104 行（-272 / +102）。删除三张手写期望值表，未删除任何结构性校验：
contract 完整性与主题同构、对比度阈值、路径安全、生成物一致性、adapter 映射、provenance 均保留。

### AC-005 篡改验证（真实执行，已恢复）

把 `apps/web/design-system/themes/cherry-black.css` 的 `--ds-brand-surface` 改成另一个值：

```
node design-system/tools/build.mjs --check
  → generated output is stale: design-tokens.json; run node tools/build.mjs   exit=1
node design-system/tools/check.mjs
  → ERROR: design-tokens.json is stale; run node tools/build.mjs              exit=1
恢复后 → 两者均通过
```

证明值锚点是真实比较磁盘上已提交的快照，而不是生成两次自比。

### AC-007 渲染无变化

改动前 `npm run build` 导出 `dist/assets/*.css` 中全部 `--ds-*` 声明共 **220 条**，改动后重新导出
并 `diff`，**无差异**。主题 CSS、`tokens.foundation.css`、`theme-contract.json` 三类文件 diff 为空。

### AC-003 隔离验证

把 `docs/` 整个移出仓库后在 `apps/web` 执行：

```
npm run check          → 32 test files / 116 tests passed
npm run build          → built in 495ms
npm run storybook:build → Vite built in 1.91s
npm run test:e2e       → 30 passed
```

随后恢复 `docs/`，`source-lock.mjs --check` 通过。这实证了 `docs/design-system.md` 长期声称但
从未验证过的一条：删除文档树不影响 Web 行为或质量门禁。

### AC-001 / AC-004 副本消除

被删的 14 个路径在全仓库的引用数为 0（历史 WORK 文档中以代码块形式出现的命令记录不是链接，
按"不篡改历史"保留）。主题值副本：

```
改动前：#d2042d 出现在 7 个非来源文件（两树 themes/*.css ×4、两树 check.mjs ×2、
        docs/design-tokens.json、apps/web/design-system/README.md 散文）
改动后：apps/web/design-system/themes/cherry-black.css
        apps/web/design-system/themes/pure-white.css
        apps/web/design-system/design-tokens.json（生成物）
```

**AC-004 措辞修正**：原文写"出现次数为 1"，但两个主题各自独立声明自己的品牌实心色，
两处都是真源而非副本。实际判据应为"只出现在 `themes/*.css` 与生成物中"，按此判定通过。

### AC-009 sourceRefs

`components.manifest.json` 18 个条目全部补齐，43 条引用路径经脚本逐条核对全部指向
`source/claude-design-v1/` 中真实存在的文件。3 个条目（`inline-notice`、`async-state`、
`submission-lifecycle`）为空数组，因为来源中确无对应物，已在 manifest 的 `reference.sourceRefs`
中说明"空数组不是遗漏"。

### AC-002 / AC-010 全量门禁（恢复 `docs/` 后）

```
npm run check              → 32 test files / 116 tests passed
npm run build              → built in 534ms
npm run storybook:build    → Vite built in 1.79s
npm run test:e2e           → 30 passed（连续 3 次均为 30）
python3 scripts/docs_test.py → 347 份 Markdown 文档入口和本地链接有效
scripts/work check         → 280 份开发文档通过校验
node docs/design-system/tools/source-lock.mjs --check → source snapshot lock is current
```

## 未通过项

暂无。

### 一次未能复现的 E2E 计数异常

在阶段 3 的一轮验证中，`npm run test:e2e` 汇总行显示 `29 passed (9.9s)`，而其余各轮均为
`30 passed`。该轮没有 failed / flaky / skipped 计数，本地 `retries: 0`。随后连续 3 次重跑
均为 30 passed，无法复现。当次运行紧跟在 `npm run build` 之后，而 Playwright 配置了
`reuseExistingServer: !process.env.CI`，怀疑与 preview server 复用及 `dist/` 刚被重写有关，
但没有取得证据，因此不作为结论。

记录在此而不是略去：这是一次没有解释的观测，若日后 CI 出现同类计数波动，这里是第一条线索。

## 范围检查

三个 TASK 的实际改动均在各自 `write_paths` 内，且三条关键不变条件都已核验：

- TASK-056 未改动 `themes/*.css`、`tokens.foundation.css`、`theme-contract.json`（diff 为空）；
- TASK-057 未改动 `apps/web` 与 `source/`；
- TASK-058 未改动 WORK-034 目录（diff 为空）与 `apps/web/src`、主题、foundation、合同。

**本次工作有三处边界扩大，均在动手前记录了理由，没有先改文件后补说明：**

1. TASK-056 增加 `apps/web/design-system/manifest.json`：新增的 `design-tokens.json` 必须在包文件
   登记表中声明，否则 `verifyPackage` 因清单不匹配而失败。属于新增生成物的固有组成部分。
2. TASK-057 增加 `docs/design-system.md`：`scripts/docs_test.py:68` 校验 markdown 链接目标存在，
   而该文有 5 处链接指向待删文件。不同提交处理会让阶段 2 的提交文档检查变红。阶段边界据此
   修正并同步进 PLAN-023。
3. TASK-058 增加 `apps/web/design-system/README.md`，并把 `apps/web` 的整体禁止收窄为
   `apps/web/src` 与三类值文件：该 README 仍要求"同步两棵树并分别通过两侧检查"，而两侧检查
   在阶段 2 后已不存在。

另有一处引用范围放宽：`sourceRefs` 从只允许 `*.prompt.md` / `*.card.html` 扩展到
`ui_kits/app/*.jsx` 与 `readme.md`。理由是第 3、4 层组件的视觉依据只存在于 app UI kit，
排除它们会让最需要追溯的条目全部落空。

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

- **追溯材料是否足够。** 文档树现在只剩冻结来源、来源锁、组件合同与许可。判断依据是：被删的
  22 个文件中没有一个包含来源里没有的信息——它们是代码树的副本或自制 preview（后者是来源 20 张
  guidelines 卡片的子集）。若日后发现某类评审确实需要一份只读的值视图，正确做法是从真源生成，
  而不是恢复手写副本。
- **值锚点在并发修改下的行为。** `design-tokens.json` 是确定性生成的，两人同时改不同主题会在该
  文件产生 git 冲突。这是期望行为（冲突可见优于静默覆盖），但冲突解决必须靠重新生成而不是手工
  合并 JSON。已写入 `apps/web/design-system/README.md`。
- **校验器不再检测"新增未审批的 Foundation token"这一条独立报错。** 该能力现由快照过期承担：
  新增 key 会让 `build.mjs --check` 失败。防护等级相同，但错误信息从"contains an unapproved token"
  变成"design-tokens.json is stale"，定位需要多看一眼 diff。
- **构图层缺口未动。** 五个只有合同没有实现的组件、前景色四档塌陷、约 807 处业务页面直接消费
  `var(--ds-*)`，全部按 REQ-009 留给后续工作。本次之后它们的位置更清楚了，但一个也没修。

## 结论

通过。10 条验收标准全部有实际命令与输出支撑，其中 AC-004 的判据措辞已按事实修正并说明理由。
两条核心目标达成：设计值在仓库中只剩一处手写定义（`apps/web/design-system/` 的
`tokens.foundation.css` 与 `themes/*.css`），校验器不再持有任何设计值；渲染结果与门禁强度均无退化。

`check.mjs` 从 1336 行降到 1104 行，`docs/design-system/` 从 22 个非来源文件降到 8 个，
主题值副本从 7 个文件降到 2 个真源加 1 个生成物。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：阶段 1-3 全部完成，10 条 AC 均已记录实际命令与结果
- 2026-09-03：状态变更：review → draft。原因：退回以便在进入 review 时一并记录验证结论
- 2026-09-03：状态变更：draft → review。原因：10 条 AC 全部有实际命令与输出支撑，结论为 pass；approved 由用户在验收闸签署
- 2026-09-03：验收闸通过：review → approved。原因：WORK-035 通过验证
