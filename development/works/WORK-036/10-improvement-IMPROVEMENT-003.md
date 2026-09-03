---
id: "IMPROVEMENT-003"
type: "improvement"
title: "建立页面构图层并修复前景色层级"
status: "review"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["WORK-035"]
related: ["WORK-034", "WORK-031", "WORK-025"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# IMPROVEMENT-003：建立页面构图层并修复前景色层级

## 当前问题

设计系统把颜色、字号、间距这些"材料"定义得很完整，但没有规定**怎么用这些材料拼出一个页面**。
结果是每个页面各拼各的：同一个列表，这一页的列对不齐，那一页用卡片堆叠；同样是筛选，这一页是
即时生效的一排标签，那一页是一个要点"提交"的表单。单看每个页面都"没有违规"，合起来看不像
同一个产品。

用户的原话是"只实现了材料合规，没有实现页面风格合规"。这不是谁不认真，是系统里缺了一层：
现在只有按钮、卡片这种零件，没有"一个列表页长什么样"的成品模板。缺了它，每个页面只能从零件
现拼，而现拼一定会拼出不同的东西。

同时有一个具体的可用性缺陷要修：界面上"次要文字"和"不可用文字"现在是同一个灰。用户看到一段灰字，
分不清是"这是补充信息"还是"这个功能现在不能用"。

这次要做的是：把材料补齐到能直接写页面的程度、把文字深浅重新分开、造出三种页面模板，
然后**用题库列表页做一个样板**给用户看效果。其余页面等样板被认可后再迁。

## 当前数据

- `docs/design-system/components.manifest.json` 声明的 18 个组件合同中，`data-table-list`、
  `app-shell-navigation`、`editor-workspace`、`submission-lifecycle`、`verdict` 五个在
  `apps/web/src` 中没有任何实现文件。`docs/design-system.md` §6 声明的第 3 层（OJ 业务组件）
  和第 4 层（页面模板）在代码里是空的。
- `apps/web/src` 中有 **807 处**直接消费 `var(--ds-*)`，其中 244 处是间距。消费最多的是业务文件：
  `admin-problem-workbench.tsx` 55 处、`admin.users.tsx` 25 处。
- `apps/web/design-system/tailwind-v4.css` 的 `@theme inline` 只 alias 了颜色与 radius，没有
  spacing、字号、字重、tracking、leading、motion、ease、三档透明 surface、fg 各档、
  `line-tertiary`、elevation 与布局尺寸。业务代码因此只能写 arbitrary value。
- `theme-contract.json` 把 `--ds-fg-disabled` 的 `contrastClass` 定为 `text`（阈值 4.5:1）。
  这一条直接导致 `cherry-black` 的 `fg-muted`、`fg-meta`、`fg-disabled` 全部取 `#8a8f98`，
  `pure-white` 取 `#62666d` / `#676b73` / `#676b73`：来源的四档文字层级塌成两档半，
  且 disabled 与普通 metadata 视觉不可区分。
- 冻结来源的 `ui_kits/app/`（`ProblemList.jsx`、`ProblemView.jsx`、`Sidebar.jsx`、
  `CommandPalette.jsx`）是仓库中唯一存在的页面级构图依据，至今没有被迁移到生产。
  WORK-035 已为这四个文件建立 `sourceRefs` 引用，但没有实现。

### 问题归纳

1. **页面必须从零件现拼。** 缺第 3、4 层，每个页面自行决定列表怎么排、筛选怎么放、密度多少。
   这是"材料合规、页面风格不合规"的机制性原因，不是执行不力。
2. **业务代码在写 token 管道。** 页面里出现 `px-[var(--ds-space-4)]` 说明缺少承载它的组件或
   alias。这也让 shadcn 官方组件难以接入：官方带的 `bg-muted p-4` 每次都要手改成 arbitrary value。
3. **文字层级塌陷，且 disabled 不可辨。** 四档变两档半让界面比来源"吵"；disabled 与 metadata
   同色是可用性缺陷，不只是风格问题。
4. **来源里最有辨识度的页面语法没有被搬运。** 对照 `ui_kits/app/ProblemList.jsx` 与现有题库列表：
   来源是固定列宽、严格对齐、8px 状态点、hairline 分隔、即时 pill 过滤；现有实现是 `flex-wrap`
   不对齐、三个同色 Badge、Panel 包住的 grid 表单加一个"筛选"提交按钮。

## 目标指标

- REQ-001：`tailwind-v4.css` 的 `@theme inline` 覆盖业务代码实际需要的全部 token 类别——间距、
  字号、字重、tracking、leading、motion 时长与 ease、三档透明 surface、四档前景与 ghost/disabled、
  `line-tertiary`、elevation、sidebar/header 尺寸。目标是常规页面用 `px-4 text-sm text-meta
  bg-surface-translucent-hover border-line` 即可表达，不必写 arbitrary value。
- REQ-002：`--ds-fg-disabled` 的合同类别从 `text` 改为 WCAG 对失效控件的豁免类别，使 disabled
  可以取一个明显更弱的值；`fg`、`fg-2`、`fg-muted`、`fg-meta` 四档在两个主题、全部允许 surface
  上都保持 ≥4.5:1，且四档彼此视觉可分。
- REQ-003：新增 lint 规则，禁止 `apps/web/src` 中除 `components/ui/` 外的文件出现 `var(--ds-`。
  违反时的正确处理是补 alias 或补组件，不是加例外。
- REQ-004：实现第 3 层的 `DataList`（固定列、hairline 分隔、行密度、状态点、hover、键盘可达）
  与 `Toolbar`（标题、计数、SearchInput、主操作、pill 过滤、toolbar 按钮），anatomy 与状态
  以 `ui_kits/app/ProblemList.jsx` 为构图依据。
- REQ-005：实现第 4 层的 `ListPageTemplate`、`WorkbenchPageTemplate`、`DetailPageTemplate`，
  统一承担首内容间距、页面语义标题、pending/empty/error/unauthorized/not-found 状态位置。
- REQ-006：`docs/design-system.md` 增加"构图合同"一节，用可在截图上判定的规则描述页面语法：
  列表页组成、列对齐、行密度、卡片与分隔线的选择、四档文字用途、状态点形状、mono 使用场景。
- REQ-007：新增 `docs/design-system/PROMPT.md`，作为后续页面/组件工作的统一提示词，
  并在 `CLAUDE.md`、`AGENTS.md` 的 Web UI 路由中指向它。
- REQ-008：把题库列表页（`/problems`）迁移到新模板与新组件，作为样板；迁移后与
  `ui_kits/app/ProblemList.jsx` 在相同 viewport 下做同画面对照，并提交给用户人工判断。
- REQ-009：`components.manifest.json` 中 `data-table-list` 与 `app-shell-navigation` 的合同
  与新实现一致；不再出现"有合同无实现"。

### 不变条件

- REQ-010：题库列表的业务行为不变——筛选字段、游标分页、错误与空状态语义、权限、API 请求与
  响应形状、路由地址和 URL search 参数全部保持现状。本次只改表达方式。
- REQ-011：`--ds-*` 命名空间、主题 id、默认主题、偏好存储与首屏防闪行为不变；
  新增 alias 不改变任何既有 token 的值（`fg-muted`、`fg-disabled` 的调整除外，见 REQ-002）。
- REQ-012：设计值仍然只有一处手写定义，改动后重新生成 `design-tokens.json`；
  不把值抄进文档、校验器或组件。
- REQ-013：不修改后端、`contracts/`、生成的 API 客户端、数据库或判题链路。
- REQ-014：除题库列表外，其余页面在本次工作中不迁移，但必须因 alias 与四档调整而重新验证外观
  没有意外变化。

## 影响范围

`apps/web/design-system/`（adapter、主题前景色、合同）、`apps/web/src/components/ui/`（新增
DataList、Toolbar、页面模板，既有组件改为消费 alias）、`apps/web/src/features/problems/`
（题库列表迁移）、`apps/web/scripts/check-design-system.mjs`（新 lint 规则）、
`docs/design-system.md`、新增 `docs/design-system/PROMPT.md`、`docs/design-system/components.manifest.json`、
`CLAUDE.md`、`AGENTS.md`。

四档前景色调整会改变**所有页面**的次要文字外观，因此影响面是系统级，即使只迁一个页面。

不涉及：`apps/server`、`apps/judge-engine`、`contracts/`、数据库、部署。

## 主要风险

- 四档前景色调整触及每个页面。若只验证题库列表，其他页面可能出现对比度或层级回归。
- `fg-disabled` 放宽对比度是有意降低可读性，方向与常规可访问性直觉相反。必须确认它只用于
  真正失效的控件，且失效原因另有可读说明（`design-system.md` §7.1 已要求"禁用控件旁必须有
  持久、可读的原因"）。
- 补 alias 会让同一个值有两种写法（alias 与 arbitrary value）。若不同时加 lint 并迁移既有用法，
  会长期并存两套风格，比现在更乱。
- `DataList` 的固定列宽与响应式、长中文、320px 存在天然冲突；处理不当会在窄屏裁掉关键信息。
- 只迁一个样板页意味着仓库会短暂存在新旧两种列表写法。必须明确这是过渡状态并有后续工作承接。

## 验证方式

- AC-001：`apps/web/src` 中除 `components/ui/` 外，`var(--ds-` 出现次数为 0，且新 lint 规则
  能拦住新增违例（负向 fixture 证明）。
- AC-002：`components/ui/` 内部的 `var(--ds-` 用量相对当前 807 处显著下降，剩余用量逐条能说明
  为什么 alias 无法表达。
- AC-003：四档前景在两个主题、全部允许 surface 上的实测对比度均 ≥4.5:1，数值记入 VERIFY；
  四档之间的相邻对比度差异足以在截图上分辨。
- AC-004：`fg-disabled` 与 `fg-meta` 在两个主题中取值不同，且在同一 surface 上视觉可区分；
  合同类别变更已在 DECISION-020 记录理由。
- AC-005：`DataList` 在桌面下列跨行严格对齐（截图上列边缘成直线）；320px 下不裁切关键信息，
  降级方式明确；键盘可达、行可聚焦、有可访问名称。
- AC-006：`Toolbar` 的 pill 过滤即时生效，不需要提交按钮；搜索框有可访问标签；
  计数与主操作位置符合构图合同。
- AC-007：题库列表迁移后与 `ui_kits/app/ProblemList.jsx` 在相同 viewport 下的同画面对照截图
  已产出，差异逐条说明是有意适配还是待修。
- AC-008：题库列表的筛选字段、游标分页、空/错/加载状态、URL search 参数与迁移前完全一致；
  既有 E2E 与组件测试不改断言即通过。
- AC-009：两个主题、320px、200% 缩放、键盘、长中文、forced-colors、reduced-motion 全部验收。
- AC-010：其余未迁移页面在两个主题下的外观复查完成，四档调整未引入可读性回归。
- AC-011：`docs/design-system.md` 的构图合同与 `PROMPT.md` 已建立，规则可在截图上判定，
  不是又一批 token 检查；`CLAUDE.md`、`AGENTS.md` 指向它。
- AC-012：`npm run check`、build、Storybook、E2E、设计系统自检、文档检查与 `scripts/work check`
  全部通过；`design-tokens.json` 已重新生成且 `--check` 通过。

## 持续观察

本项目无生产环境。持续观察发生在样板页的人工判断上：用户把迁移后的题库列表与来源 UI kit
并排看，如果仍然觉得"不像"，说明构图合同抓错了特征，应当回到 DESIGN 重新识别，
而不是继续迁移其余页面。这是本次工作唯一无法用自动化替代的判断。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：已完成问题定义、基线数据、14 条 REQ 与 12 条 AC，等待用户审核
