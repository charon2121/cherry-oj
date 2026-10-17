<!--
写 Web 页面或组件前先读本文。
本文只写「做什么」和「不这么做会怎样」。规则的来历、实测依据与例外流程见
../design-system.md 与 measurements.md，需要时再读。
-->

# Cherry OJ Web 页面与组件提示词

视觉目标是 **instrument panel**：近黑或纯白画布、冷灰层级、高信息密度、严格对齐、
hairline 分隔、克制圆角、几乎无动效。参照物是工程工具，不是后台管理模板，也不是营销页。

会话内只需一句话时用：**「按 `docs/design-system/PROMPT.md` 写；完成后逐条回答自检九问。」**

## 一句话原则

**用位置说话，不用装饰说话。装饰只在表达「层」的时候出现。**

框、线、ring 都是在用画边界说「这些是一组」「这个是激活的」。对齐用摆位置说同样的话，
而且不占视觉预算。下面九条都是它的推论：

| | 规则 | 判据 | 不遵守会怎样 |
|---|---|---|---|
| A | 分组靠间距不靠容器 | 一屏「框」数接近 0；组内 8px、组间 24px | 框是间距档位没拉开的补丁 |
| B | 状态只改颜色不加东西 | hover/focus 前后元素尺寸不变 | 加东西会推挤相邻元素，对齐当场崩 |
| C | 强调只有提亮和变灰 | 不加粗、不放大、不上色 | 手段一多就没有层级 |
| D | 尺寸档位要少 | 不发明新高度；图标只有 14 / 16 | 档位一多就不知道该跟谁对齐 |
| E | 对齐可见内容的边缘 | 工具条标题、分组带、行首图标左边缘成一条竖线 | 有隐藏前置元素时按容器对齐会歪 |
| F | 空白是内容 | 列表下方的空白不填。但**行内的空白要先确认不是排布错了**：短字段推到两端造成的空不算「留白」 | 填满会变仪表盘；拿这条给排错的行背书则会一直错下去 |
| G | 颜色只管层级和状态 | 分组、强调都不用颜色 | 颜色一多就掉价 |
| H | 动效只做颜色 | 无位移、无缩放 | 位移破坏对齐感 |
| I | 只有浮层允许厚重 | 浮层三件套；其余一律无阴影 | 阴影用在平面元素上会显脏 |

## 先找参照，再动手

视觉语言**只有一个来源：linear.app 真实页面的实测**（数值在 [`measurements.md`](./measurements.md)）。
做任何新页面或改外观之前，按这个顺序找参照：

1. **仓库里已有同类页面**——查下面的「页面对照表」，照现有实现的结构和组件写。这是最快也最一致的路。
2. **没有同类时，去 linear.app 找功能最接近的页面实测**：列表→Issues，对象编辑→Issue 详情，
   新建→新建 issue 对话框，选值→属性浮层，设置→Settings，跳转→命令面板（⌘K）。用浏览器对已登录页面读
   `getComputedStyle` / `getBoundingClientRect`，量到的新数值补进 `measurements.md`（只记事实）。
   用户已授权使用其 Chrome 中已登录的 Linear 账号（可建测试数据、切主题），用完把主题恢复为 Dark。
3. **其他产品（如 leetcode.cn）只能参考交互，不参考外观。** 可以学的是流程、反馈时机、信息顺序
   （例如"运行后先给结论、自动选中第一个失败用例"）；不能搬的是布局、配色、画布、卡片、顶栏、导航。
   顺序是：先说清 Cherry 现有页面上用户卡在哪一步，再看别人怎么解决，最后用本系统的组件与 token 实现。
   「某某网站就是这样」不是改动理由；用户说「重新设计 X」时，不要自行替 X 选别的产品当参照。
   （2026-10-17 曾把做题页的画布、浮卡片、顶栏乃至用户端主导航照 leetcode.cn 复刻，已全部撤回。）

## 三层来源，顺序不可颠倒

1. **结构与可访问性 = shadcn 官方实现**（style `base-nova`）。新增第 2 层基础组件前先查
   `https://ui.shadcn.com/r/styles/base-nova/<name>.json`：200 就以官方 DOM、props 和可访问性
   行为为骨架，404 才手写并在组件清单记下依据。不重写官方结构。
2. **样式 = 本仓库语义 token**，通过 Tailwind alias 消费。只替换官方的颜色与尺寸 class。
3. **构图 = 下面的页面语法。** token 全对而构图错，判定为不合格。

## 先选模板，不要从零件现拼

| 要写什么 | 用什么 |
|---|---|
| 列表页（行信息密集） | `ListPageTemplate width="bleed"` + `Toolbar` + `DataList align="spread"` |
| 列表页（行只有几个短字段） | `ListPageTemplate width="column"` + `Toolbar` + `DataList align="packed"` |
| 详情页 | `DetailPageTemplate` |
| 长任务工作台 | `WorkbenchPageTemplate`（含持续可达的保存状态栏） |
| 对齐的数据列表 | `DataList` |
| 列表工具条 | `Toolbar` + `ToolbarFilterGroup` |
| 真正的数据表（可排序列头、批量操作） | `./table` 的 `Table` 原语 |

模板独占首内容间距与页面语义标题；页面不再自写 `pt-*`，也不再自己写 `<h1 className="sr-only">`。

## 页面对照表（新页面先找最像的一行照着写）

| Cherry 页面 | 现有实现 | linear.app 对应 | 要点 |
|---|---|---|---|
| 管理端外壳 | `app/shells/admin-app-shell.tsx` | 侧栏 + 内嵌面板 | 侧栏 244px 与画布同底；主内容是内嵌面板，44px 顶栏在面板内。页面级操作用 `HeaderActions`、当前对象名用 `usePageCrumb`（`components/ui/header-slot.tsx`）放进这条顶栏，不在内容里再叠一条 |
| 用户端外壳 | `app/shells/site-app-shell.tsx` | 无（用户端没有侧栏） | 44px 顶栏：品牌与栏目在左，主题与账号在右；做题页填满视口、隐藏页脚 |
| 列表 | `features/problems/components/problem-list-page.tsx`、`admin-problem-list-page.tsx` | Issues 列表 | `ListPageTemplate` + `Toolbar` + `DataList`，见下方「页面语法」 |
| 成员/账号管理 | `features/admin-users/components/admin-users-page.tsx` | Settings › Members | 页标题、一行工具条、成组的行 |
| 对象编辑（一页到底） | `features/problems/components/admin-problem-workbench.tsx` | Issue 详情 | 左正文、右属性栏（`property-button.tsx` 的属性胶囊原地改）；长文本用 `RichMarkdownEditor`（所见即所得，存 Markdown）；不属于"写"的动作是属性栏打开的对话框，可用 `?panel=` 直达；保存规则见 `../design-system.md` §7.1 |
| 新建对象 | `features/problems/components/admin-problem-create-dialog.tsx` | 新建 issue 对话框 | 只问必要字段（标题、标识、难度），其余在编辑页补 |
| 做题页（编码工作台） | `features/problems/components/problem-detail-page.tsx`、`features/custom-runs/custom-run-workbench.tsx` | 无直接对应，由 Linear 零件组合 | 面包屑一行（题库 › 题名，右侧 slug）；宽屏 `SplitPane` 分栏（题目｜代码/测试，栏间 0.5px 线，可拖、可键盘调）；每栏顶部 `PaneHeader`（44px、0.5px 下边）；栏内切换用胶囊 `Tabs`；窄屏用 `Tabs` 在题目/代码间切换 |

## 已有组件（先用，不要重写）

`components/ui/` 下的通用件：`Button`、`IconButton`、`Pill`（视图切换与可选项胶囊，可带状态圆点）、
`Tabs`（胶囊式视图切换）、`SplitPane`（可拖动分栏）、`DataList`、`Toolbar`、`SearchInput`、`Field`/`Input`/
`SelectField`/`Textarea`、`Dialog`、`Sheet`、`Popover`、`DropdownMenu`、`InlineNotice`、`AsyncState`（加载/空/错）、
`Badge`、`Breadcrumb`、`TextEditor`（CodeMirror：长文本、样例、只读输出）、`CodeEditor`（Monaco：只用于做题
编码区）、`RichMarkdownEditor`、`CodeBlockEditor`、页面模板 `ListPageTemplate`/`DetailPageTemplate`/
`WorkbenchPageTemplate`。业务里复用的：`CopyIconButton`（`features/problems/components/source-copy-button.tsx`）、
`DifficultyIcon`、`SafeMarkdown`（不可信 Markdown 渲染）、品牌标志 `public/brand-mark.svg`（用 `<img>` 引用，
不要重画）、`Pane`/`PaneHeader`（`problem-source-editor.tsx`）。
确实缺件时，先按「三层来源」查 shadcn base-nova，再在 `components/ui/` 新增并写 story。

## 交互与文案约定

- **同一事物只有一个叫法**，用户端、管理端、自测、提交都一样（例如两端都叫"输入说明/输出说明"；判定名称
  统一用 `features/submissions/submission-panel.tsx` 导出的 `labels`，并按 `../design-system.md` §5 写成
  "代码 · 名称"，例如"WA · 答案错误"）。写法可以不同，叫法不能不同。
- **界面上不写功能说明。** 页面自己说明怎么用就是没设计好；给读屏的说明放 `sr-only`。只有操作被挡住时，
  才在旁边用可读文字说明原因和下一步。
- **结果先给一句结论，再给细节。** 结论用颜色区分：通过 `text-success`、失败 `text-danger`、进行中
  `text-fg-2`；有多项时自动选中第一个失败项，细节按"输入 → 实际 → 预期"并排。
- **操作放在它产生结果的区域旁**，宽窄屏同一位置。用户触发的结果自动切到对应页签，但不抢焦点。
- **快捷键**：⌘/Ctrl+S 保存，⌘/Ctrl+Enter 运行，⌘/Ctrl+Shift+Enter 提交（会留下记录的动作多按一个键）。
  用 `src/lib/use-mod-enter.ts`，按钮带 `aria-keyshortcuts` 与 `title`；对话框打开时不响应。
- **覆盖或清空用户手写的内容前要确认**；在系统给的内容之间切换（例如样例 1 → 样例 2）不确认。
- 保存状态与所属区域的标题同一行，不单独占一行；出问题时才变成醒目的文字和按钮。

## 页面语法（判定用，不是建议）

**列表页 = 工具条 + 数据列表 + 分页。** 工具条第一行放标题、准确计数、搜索；第二行放过滤器。
**过滤即时生效，不放「筛选」提交按钮**——除非查询确实昂贵，并在对应 DESIGN 里说明什么算昂贵。
文本输入回车即应用（提交按钮 `sr-only`），下拉选中即应用。

**先判断行里的内容能不能撑满宽度，再选排布方式。** 这一步选错，后面怎么调都不对。

```
spread（标题本身很长，例如一句话标题）
[图标] [标识] [标题 ────────────────────────]        [尾部元信息]
                                              └ 推到右边缘 ┘

packed（标题只有几个字，中文标题基本都属于这类）
[图标] [标题（定宽 352px）] [难度] [标签] [语言]
        └─ 定宽让后续字段跨行落在同一个 x ─┘
```

**短标题必须用 `packed` + `width="column"`。** 把几个短字段推到两端，中间会空掉整行的一大半；
而只做"紧跟"不定宽，后续字段又会随标题长短参差——**那比空白更伤扫视**。定宽是唯一站得住的解。
标题宽度必须是确定值，不能是 `w-full`：packed 下父容器是 shrink-to-fit，百分比会解析回内容宽度。

`DataList` 的 `renderRow` 返回 `{ leading, id, title, trailing, meta }` 五个插槽。
**不要用列定义或 `<table>` 排应用列表**：表格把剩余宽度均分给各列，字段会摊满整行、
中间全是空隙，去掉表头也去不掉表格感——表格感来自列均分。

**内容装不下时先减字段，不要挤压标题。**

行高 44px、圆角 8px，行间不画分隔线，分段靠 36px 分组带的明度，不用卡片间距。
窄屏把右簇折进 `meta` 显示在标题下方——降级是折行，不是裁切也不是横向滚动。

**`Card` 与 `Panel` 默认无框无底色。** 确需边界时显式 `bordered`，确需抬升时显式 `elevated`，
两者都应当罕见。

**文字四档各司其职**，一屏只用两档即为层级塌陷：

| 档 | class | 用途 |
|---|---|---|
| 1 | `text-foreground` | **列表行标题**、页面主标题——一眼扫到的那个 |
| 2 | `text-fg-2` | 正文、说明 |
| 3 | `text-fg-muted` | 次要说明、占位符 |
| 4 | `text-fg-meta` | 标识、时间戳、计数等查证用信息 |

第五档 `text-fg-disabled` **不参与这个排序**，它表达「不可用」，是全系统唯一豁免 4.5:1 的前景色。
**禁止**把它当作更浅的第五档正文。禁用控件旁必须另有可读的原因说明。

**有序量用形状，不用颜色。** 难度、优先级这类用图标的数量或形状编码（三根条编码简单/中等/困难），
文本通过 `sr-only`、`title` 与窄屏折行区保留。彩色文字每行一次，六行就是六处饱和色。

**状态用形状加文字**：8px 实心圆=完成、8px 空心圆=进行中、2px 圆角方块=未开始。
不要把一行信息全做成同色 Badge。

**度量值用 mono 并给准确数字**：`42 ms`、`14 / 14`、`23%`。不写「很快」「大量」。

**焦点是 1px、贴边、换成 focus 色。** 有边框的控件让边框变色，没边框的用 1px 零偏移 outline，
另配 `forced-colors:` 回退——那个模式下颜色由系统接管，边框变色表达不出焦点。

**浮层材质**：不透明 `bg-surface-raised` + 0.5px 边框（`border-hairline`）+ 12px 圆角 + 三层小阴影（`shadow-dialog`），不用模糊。

**品牌红只用于主按钮、focus 和当前项。** 列表行标题用前景色不用品牌色。品牌色不代替 `destructive`。

**动效**只有 opacity / color / background-color，150/200/320ms 标准 easing，`reduced-motion` 下归零。

**文案**：中文简洁陈述句，动词开头（「提交」「重新判题」），无感叹号和 emoji。
报错说清原因和下一步——「第 14 个测试点答案错误」，不是「出错了」。

## 禁止

- **业务页面里出现 `var(--ds-*)`。** lint 规则 `ds-var-outside-ui` 会拦。缺 alias 就去
  `apps/web/design-system/tailwind-v4.css` 补，或者说明此处应该有一个组件——不要加例外。
  `calc()` 里的布局运算用 `var(--space-6)`、`var(--layout-header)`、`var(--layout-sidebar)`。
- **新增 alias 后不在 `src/lib/utils.ts` 的 `extendTailwindMerge` 登记。** `cn()` 认不出自定义键
  会把它和同前缀的类当成冲突组互相覆盖，样式**静默消失**，构建和测试都不报错。
- **引用不存在的 `--ds-*`。** CSS 对未定义变量是静默的，样式直接消失。
- **给 focus 加 ring、加 outline offset、加光晕。**
- **给非浮层元素加阴影。**
- **用框表达分组。** 先用间距；间距不够就把档位差拉大。
- inline style、手写 `<svg>`、`onMouse*` 维护视觉状态、`Math.random()` 生成 id、`dark:`、
  枚举 theme id、raw hex / oklch、用 `color-mix()` 制造必要对比、用 opacity 表达 disabled。
- 页面顶部「眉题 + 标题 + 描述」介绍区。页面直接进入任务。
- 把长 Markdown 或代码放进 `Textarea`；用项目的 `TextEditor`。
- 从 linear.app 以外的产品搬外观（布局、配色、画布、卡片、顶栏、导航）。见「先找参照，再动手」第 3 条。
- `dark:` 变体、`translate-*` 位移、用 opacity 表达禁用或占位——`npm run check:design-system` 会拦。
- 把 `source/claude-design-v1/` 当设计依据。它只是许可与来源证据，不要照它的 readme
  或原型代码做设计判断。

## 交付前自检（逐条作答，不接受「已遵守」）

0. **参照的是哪个现有页面或 linear.app 的哪个页面？** 有没有任何外观来自别的产品？

1. **这一屏画了几个框？** 每一个都要说出为什么间距解决不了。
2. **行内的字段跨行对齐吗？** 截图后同一列应当落在同一条竖线上。
   这一屏的标题平均多长？短标题却用了 `spread`，或用了 `packed` 但标题没定宽，都会立刻露出来。
3. **这一屏用了几档文字色？** 只有两档就是层级塌陷。`fg-disabled` 有没有被当成第五档正文？
4. 这是列表 / 详情 / 工作台中的哪一种？用的是哪个页面模板？一个都没用的话，为什么？
5. 我写了几处 `var(--ds-*)`？每一处为什么不能用 alias 或组件表达？新增的 alias 登记了吗？
6. 有序量用的是形状还是颜色？这一屏出现了几次饱和色？
7. 两个主题、320px、200% 缩放、键盘、长中文、forced-colors、reduced-motion 分别验过没有？
   窄屏的降级方式是折行还是裁切？
8. 与 [`measurements.md`](./measurements.md) 的实测数值对过没有（行高、行内边距、字号、
   图标尺寸、簇内 gap、控件三态描边）？对不上的地方是有意偏离还是没注意到？

## 验收方式

本地验收：`cd apps/web && npm run lint && npx vitest run && npm run check:design-system && npm run build`，
再跑相关 E2E；浏览器里两个主题各截一张图，与 linear.app 对应页面同尺寸并排对照。

**不要用「token 通过校验」证明构图正确**——校验只能证明没用错颜色，证明不了排得对。
改动外观时截图对照；怀疑有偏差时逐元素 dump 计算样式再 diff，它能指到具体是哪个元素的
哪个属性变了，截图只能告诉你「有问题」。
