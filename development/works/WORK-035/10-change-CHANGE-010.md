---
id: "CHANGE-010"
type: "change"
title: "收敛设计系统为单一真源"
status: "approved"
work: "WORK-035"
owners: ["claude/root"]
depends_on: []
related: ["WORK-034", "WORK-015", "WORK-018"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# CHANGE-010：收敛设计系统为单一真源

## 为什么做

设计系统里同一件事被抄在好几个地方。改一个品牌颜色，要在七个文件里各改一遍；其中两个文件还是
负责"检查颜色对不对"的程序——它们把正确答案也手抄了一份。这等于让两份手抄本互相印证：抄错了
也查不出来，抄对了也只是碰巧。

后果不是某个页面变丑，而是**没人敢动，也说不清哪一份算数**。想改一个间距，要先搞明白改哪一份、
另外几份要不要跟着改、检查程序会不会因为答案对不上而报错。设计系统本来是为了让人少做决定，
现在它自己变成了需要做决定的地方。

这次工作**不改变任何页面的外观**，只做减法：把重复的删掉，让每件事只有一处定义。它是下一步
（把页面做得真正像设计稿）的前置条件——在现在这个结构上新增组件，只会造出第四份副本。

## 当前状态

设计系统目前分三层存放：

1. `docs/design-system/source/claude-design-v1/`：Claude Design 导出的 99 个文件，由
   `source-lock.json` 锁定逐文件 SHA-256，2026-09-03 提交 `ec260bd` 引入；
2. `docs/design-system/` 下另外 22 个文件：token、主题、合同、Tailwind adapter、组件 manifest、
   生成快照、preview 页和两个工具；
3. `apps/web/design-system/` 下 14 个文件：与第 2 层大量重叠，是 Web 构建实际读取的一侧。

可核对的事实：

- **6 个文件在两棵树中字节完全相同**：`theme-contract.json`、`themes.manifest.json`、`tokens.css`、
  `tokens.foundation.css`、`themes/cherry-black.css`、`themes/pure-white.css`。
- 两棵树各有一份 1336 行的 `tools/check.mjs`，互相相差 734 行；`tools/build.mjs` 相差 153 行。
- `apps/web/design-system/tools/check.mjs:236-360` 手工抄写了两个主题的**完整期望值表**。
- 品牌色 `#d2042d` 出现在 7 个非 source 文件中（两树的 `themes/*.css` 各 2 个、两树的 `check.mjs`、
  `docs/design-system/design-tokens.json`、`apps/web/design-system/README.md` 的散文）。
- 机器可读的值快照 `design-tokens.json` **只在 docs 树生成**（`docs/design-system/tools/build.mjs:142`），
  Web 真源一侧没有；因此 Web 侧唯一的值锚点就是上面那张手抄表。
- `docs/design-system/README.md` 声明的权威顺序有 6 层，且明确规定"普通 CI 不比较、复制或链接
  代码树与文档树"——即两树一致性没有任何自动保障。

来源与架构的时间顺序：

- `eb3c7a3`（2026-08-28）一次性建立了全套架构：`theme-contract.json` 501 行、`check.mjs` 866 行、
  `components.manifest.json` 330 行、`tokens.foundation.css`、`themes/*.css`、`tailwind-v4.css`、
  `preview/*.html`，共 21 个文件 4973 行。
- `ec260bd`（2026-09-03）才把 Claude Design 导出的 99 个文件第一次放进仓库，并在同一次提交里
  对上述文件**原地打补丁**：`themes/cherry-black.css` 相对 08-28 初版累计只有 `+19 / -12`（原文件 97 行），
  `tokens.foundation.css` `+40 / -5`，`theme-contract.json` `+77 / -2`。

## 当前问题

1. **同一个值有多处手写副本，且没有任何机制阻止漂移。** 两棵树的一致性完全依赖"同一个 WORK 里
   记得两边都改"，而规则又禁止 CI 去比较它们。
2. **校验器持有手抄的期望值表。** 被校验对象（`themes/*.css`）和校验标准（`check.mjs` 里的值表）
   由同一个人在同一次工作中手写产生，校验因此不构成独立证据。这份表还存在两份。
3. **值快照生成在非真源一侧。** `design-tokens.json` 由 docs 树生成，而 docs 树按规定不是真源，
   Web 侧反而没有可 diff 的值锚点。
4. **docs 树同时扮演两个角色。** 它既是"人读的追溯材料"，又是"一份可执行副本"。后一个角色它
   无法履行（没有消费者、没有一致性保障），却带来了全部维护成本。
5. **来源叙述与历史不符。** `docs/design-system.md` 称冻结来源是"直接视觉来源"，但消费它的整套
   架构比它早 6 天存在，实际关系是"既有架构 + 数值适配"。这句话会让后续工作误判改动的依据。

## 目标状态

- REQ-001：每个设计值在仓库中只有**一处手写定义**，位于 `apps/web/design-system/`
  （`tokens.foundation.css` 与 `themes/*.css`）。其余出现该值的位置必须是生成物或冻结来源。
- REQ-002：校验器不再持有任何手写期望值。值的漂移检测改由 `build.mjs` 在 Web 真源一侧生成
  `design-tokens.json` 快照并以 `--check` 锚定；值变更必须表现为一次显式的快照更新。
- REQ-003：仓库中只保留**一个** `build.mjs` 与一个 `check.mjs` 实现，位于 `apps/web/design-system/tools/`。
- REQ-004：`docs/design-system/` 只保留人读与追溯材料：`source/`、`source-lock.json`、
  `tools/source-lock.mjs`、`NOTICE.md`、`LICENSE.*`、`README.md`、`components.manifest.json`。
- REQ-005：删除自制的 `preview/*.html`；Foundation 与主题的视觉参考改为指向冻结来源自带的
  `source/claude-design-v1/guidelines/` 20 张 specimen 卡片。
- REQ-006：`components.manifest.json` 的每个条目可追溯到冻结来源中对应的 `*.prompt.md` 或
  `*.card.html`，采用**引用路径**的方式建立，不复制其内容。
- REQ-007：`docs/design-system.md` 的权威顺序由 6 层降为 3 层（冻结来源 → 生产合同与原则 →
  Web 可执行真源），并把来源叙述改写为与 git 历史一致的事实表述。
- REQ-008：`CLAUDE.md` 与 `AGENTS.md` 中指向设计系统的路由与新结构一致，不再让人从两棵树里
  猜哪一份有效。

## 不变条件

- REQ-009：两个主题的最终渲染结果不变。本次工作**不修正**已知的前景色四档塌陷等视觉问题，
  它们留给 WORK-036，以免"结构收敛"和"视觉修改"混在同一批 diff 里无法归因。
- REQ-010：`apps/web` 的 `npm ci`、`dev`、`build`、`check`、`storybook`、`test:e2e` 全程不读取
  `docs/`。删除 docs 树的可执行副本后，上述命令行为不变。
- REQ-011：冻结来源 `source/claude-design-v1/` 的 99 个文件与 `source-lock.json` 一字不改，
  `source-lock.mjs --check` 继续通过。
- REQ-012：OpenDesign Apache-2.0、字体 OFL-1.1 与 Lucide 的许可原文和归因链完整保留；删除文件
  不得削弱任何一条来源声明。
- REQ-013：不修改共享组件、页面、路由、API、生成的 API 客户端、后端与数据库。
- REQ-014：`--ds-*` 命名空间、Tailwind/shadcn adapter 的语义映射、主题 id、默认主题与偏好存储
  行为不变。

## 影响范围

`docs/design-system/`（删除可执行副本）、`apps/web/design-system/`（成为唯一真源，新增生成快照、
简化校验器）、`docs/design-system.md`、`CLAUDE.md`、`AGENTS.md`，以及 `apps/web` 的
`check:design-system` 脚本链。

不涉及：`apps/web/src/**` 的组件与页面、`apps/server`、`apps/judge-engine`、`contracts/`、
数据库与部署。

## 风险

- 删除 docs 树的可执行副本后，若某处（CI、文档检查、脚本）实际仍在读取它们，会在删除后才暴露。
  需要在删除前先做一次全仓库引用扫描，并把结果记进 VERIFY。
- 重写 `check.mjs` 可能在删除手抄值表的同时误删仍有价值的结构性校验（主题同构、对比度、
  符号链接越界、生成物一致）。这些能力必须逐条保留并在 `--self-test` 中有对应用例。
- 新生成的 `design-tokens.json` 若把 `--check` 写成"生成后再比较自己"，会退化成恒真检查，
  失去漂移检测能力。需要以"改动一个主题值必须导致 `--check` 失败"作为验收用例。
- 文档删除量较大，容易连带删掉唯一保存许可或归因的文件。

## 回归检查

- AC-001：`git grep` 全仓库，`docs/design-system/` 下被删除文件的路径引用数为 0（`source-lock.json`
  内对 `source/` 的引用除外）。
- AC-002：删除后在干净检出上执行 `cd apps/web && npm ci && npm run check && npm run build &&
  npm run storybook:build && npm run test:e2e`，结果与本次改动前一致。
- AC-003：把 `docs/` 整个目录临时移走后，上述 Web 命令仍全部通过，实证 REQ-010。
- AC-004：`#d2042d`、`#08090a` 等主题值在非 source、非生成物文件中的出现次数为 1（只在
  `apps/web/design-system/themes/*.css`）；`apps/web/design-system/README.md` 的散文不再复述具体色值。
- AC-005：手工把 `themes/cherry-black.css` 的 `--ds-brand-surface` 改成其他值后，
  `node design-system/tools/build.mjs --check` **失败**；恢复后通过。这证明快照锚定真实有效。
- AC-006：`check.mjs` 中不存在任何主题值字面量（`--self-test` 的故意篡改用例除外，且该用例
  从主题文件读取原值再替换，不硬编码原值）。
- AC-007：改动前后 `apps/web` 的 `dist/` 产物中，`tokens.css` 与主题 CSS 的最终解析结果逐值相同。
- AC-008：`node docs/design-system/tools/source-lock.mjs --check` 通过，99 文件清单与摘要未变。
- AC-009：`docs/design-system/components.manifest.json` 的每个组件条目都带有指向
  `source/claude-design-v1/` 内实际存在文件的引用路径，路径存在性由检查脚本或人工逐条确认。
- AC-010：`scripts/work check` 与仓库文档检查（`scripts/docs_test.py` 链路）通过，
  `docs/design-system.md`、`CLAUDE.md`、`AGENTS.md` 中不存在指向已删除文件的链接。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：已完成改动定义、目标状态、不变条件和回归检查，等待用户审核并签署意图闸
- 2026-09-03：意图闸通过：review → approved。原因：确认收敛范围与三阶段顺序
