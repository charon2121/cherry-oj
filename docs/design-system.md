# Cherry OJ Web 设计系统

> **状态：已确认的长期规范。** 本文与 [`design-system/`](./design-system/) 共同定义 Cherry OJ
> 后续 Web 组件、业务组件和页面的视觉与交互合同。方案源自
> [WORK-015 / DECISION-011](../development/works/WORK-015/40-decision-DECISION-011.md)，但不是 Linear
> 官方设计系统；来源、许可与 Cherry 修改见 [`design-system/NOTICE.md`](./design-system/NOTICE.md)。

## 1. 权威关系

设计系统只有一条规范链，不能由截图、Storybook、HTML 示例或业务代码反向定义：

1. 本文规定设计原则、消费规则、组件合同和例外流程；
2. [`theme-contract.json`](./design-system/theme-contract.json) 规定每个主题必须实现的语义角色、允许组合与
   对比类别；
3. [`tokens.foundation.css`](./design-system/tokens.foundation.css) 和 manifest 登记的主题 CSS 是数值真源；
4. [`tokens.css`](./design-system/tokens.css)、[`design-tokens.json`](./design-system/design-tokens.json) 与
   HTML 参考均为生成或展示产物，不得手工建立另一套值。

包的入口、生成和校验命令见 [`design-system/README.md`](./design-system/README.md)。历史链接
[`ui-system.html`](./ui-system.html) 仅作兼容入口，不再承担视觉合同。

## 2. 设计方向

Cherry OJ 沿用 Linear fixture 的 Focused Workspace 结构：低噪声层级、紧凑但可读的信息密度、精确对齐、
克制的圆角与动效，以及用明度和细边界组织内容。它服务于“读题—编码—提交—诊断”这一长时间专注流程，
不复制 Linear 的商标、Logo、产品文案或营销页面。

所有界面遵守以下原则：

- 导航退后，当前任务获得主要对比度；普通分组优先用间距、对齐和分隔线，不把每个区块都做成 Card。
- Foundation 沿用 Inter Variable、中文系统字体回退、400/510/590 字重、4px 间距节奏、6/8/12px
  圆角与 150/200ms 动效。Berkeley Mono 只在用户环境已合法安装时优先，不随包分发。
- 品牌、危险和 OJ 状态是不同语义。Cherry 品牌色可以表示主操作和品牌链接，不能代替 destructive。
- 页面由稳定的 shell、列表、工作台和详情模板组合；真实业务差异进入内容区，不重新发明视觉语言。

## 3. 主题模型

### 3.1 已登记主题

| Theme ID | Color scheme | 定位 |
|---|---|---|
| `cherry-black` | dark | 默认主题；完整保留 Linear-derived 黑色结构，仅做已批准的 Cherry 品牌、OJ 语义和可访问性修正 |
| `pure-white` | light | Cherry 设计的完整浅色扩展；主画布和抬升面使用 `#ffffff`，以冷灰建立面板、输入和 hover 层级 |

主题选择只由 `<html data-theme="…">` 表达。缺失、空值、`cherry-black` 和未知 theme id 都回退到
`:root` 的 `cherry-black`；`pure-white` 必须显式选择。默认不跟随操作系统。
`data-color-scheme` 如为 Tailwind 兼容而存在，只能由 theme manifest 派生，不能单独持久化或成为第二真源。

当前主题文件为：

- [`themes/cherry-black.css`](./design-system/themes/cherry-black.css)
- [`themes/pure-white.css`](./design-system/themes/pure-white.css)

`cherry-black` 的画布、主要 surface 和主文字锚点分别为 `#08090a`、`#191a1b`、`#f7f8f8`；
`pure-white` 的画布/抬升面为 `#ffffff`，panel、subtle、hover 使用
`#f7f8f8`、`#f5f6f7`、`#f3f4f5`。完整值只以主题 CSS 为准。

### 3.2 扩展新主题

新增主题是“实现同一合同”，不是在组件中增加模式：

1. 新增一份完整 theme CSS，显式声明合同中的全部 required key；不得只覆盖相对默认主题的差值。
2. 在 [`themes.manifest.json`](./design-system/themes.manifest.json) 登记稳定 id、label、color scheme、文件、
   provenance 和版本。
3. 运行包内 build/check，重新生成聚合 CSS 与机器快照，并通过全部允许 surface 的对比检查。
4. 用同一组件矩阵验证桌面、320px、键盘、长中文和 reduced-motion。

新增完整主题不应修改 Tailwind adapter、组件或页面。删除/改义合同字段、改变默认主题或改变组件默认行为
属于破坏性变更，必须先建立 WORK 并重新走 DECISION。

## 4. Token 消费规则

Foundation token 与主题 token 分层：字体、字号、间距、圆角、布局和 motion 全主题共享；颜色、focus、
selection、overlay、status 与 elevation 由每个主题完整实现。Canonical token 统一使用 `--ds-*`。

组件和页面必须：

- 只消费语义 token 或 [`tailwind-v4.css`](./design-system/tailwind-v4.css) 提供的稳定 alias；
- 不写 raw hex/OKLCH，不读取 primitive palette，不出现主题 selector，也不按 theme id 分支；
- 不用承担必要对比的动态 `color-mix()`；disabled/placeholder 使用专门 token，不再叠 opacity；
- 只把 `border`/`border-soft` 当装饰分隔，控件或状态识别使用 `border-strong` 或对应 status border；
- 让同一组件在所有 manifest 主题中保持相同 anatomy、size、variant、state 和键盘行为。

Tailwind/shadcn 只做一次 theme-neutral 映射：

- `primary` 是品牌实心 surface，只与 `primary-foreground` 配对；普通品牌文字使用 `brand`。
- shadcn `accent` 是中性 hover surface，不是 Cherry 品牌色。
- `destructive` 只映射 danger solid，并与 `destructive-foreground` 配对；普通危险文字使用 danger。
- `ring` 映射主题 focus；`input`/`border-strong` 承担必要控件边界。
- 新组件优先不写 `dark:`；兼容代码确有需要时，只能消费由 manifest 派生的 color scheme，不能枚举主题。

## 5. 品牌、危险与 OJ 状态

品牌角色拆为 `brand-surface`、hover/active、`on-brand`、`brand-foreground`、`brand-soft` 和
`on-brand-soft`。实心 CTA 使用 Cherry `#de1c4e`；暗色上的品牌文字/focus 使用较亮的 Cherry 值，
pure-white 上使用可达对比的深 Cherry 值。暗色亮粉不得被直接搬到白底正文、必要图标或焦点。

`success`、`warning`、`danger`、`info`、`special` 每类状态都完整实现：

- `foreground`：状态文字与必要图标；
- `surface`：不透明的 soft 背景；
- `border`：状态需要边界识别时使用；
- `solid` / `on-solid`：实心状态面与其前景的固定配对。

Submission 生命周期与 verdict 是两套状态机，不能把 `Pending` / `Judging` 当 verdict，也不能把 CE/SE
当 HTTP 错误。所有 verdict 必须穷尽 `contracts/verdict.json`，同时显示 code、可读名称以及稳定图标或
形状；不得只靠颜色。Cherry magenta 与 danger red 接近，因此破坏性动作还必须使用危险/删除图标、
明确动词，并在高风险操作中提供确认。

## 6. 组件合同

组件分四层：

1. design token 与 Tailwind/shadcn adapter；
2. button、field、link、badge、card/panel、dialog/popover、typography、layout 等基础组件；
3. verdict、Submission lifecycle、editor workspace、problem filter/table、inline notice 等 OJ 业务组件；
4. app shell、题库列表、题目工作台、提交详情和管理表格等页面模板。

新组件必须在 [`components.manifest.json`](./design-system/components.manifest.json) 的合同范围内设计，至少
说明 anatomy、size、variant、state、semantic token、键盘行为和禁止组合。所有交互组件覆盖
default、hover、pressed、focus-visible、disabled 和 loading；disabled 不响应，loading 保持尺寸并提供
可读状态。页面还必须明确 pending、empty、error、unauthorized、not-found 和 success。

视觉参考见 [`components.html`](./design-system/components.html) 与 [`preview/`](./design-system/preview/)；
它们用于评审，不是 token 或组件行为的真源。

## 7. 可访问性与响应式

- 普通文字、metadata、placeholder 与其所有允许背景的对比度至少 `4.5:1`；必要控件边界、图标和
  focus 至少 `3:1`，验收不四舍五入。
- 正文链接保持 underline；焦点使用可见的 2px outline 与 offset，不能只靠透明光晕。
- 交互使用语义 HTML、可访问名称、正确 label 和错误关联；键盘操作与指针操作能力一致。
- 320px 下不允许关键操作或状态被裁切；长中文不能破坏按钮尺寸、表格可读性或工作台主流程。
- `prefers-reduced-motion` 下禁用非必要位移与平滑滚动；forced-colors 使用系统色，它是环境适配而不是
  新主题。

## 8. 例外与变更流程

没有“只在这个页面写一个颜色”的快速例外。若现有语义不足：

1. 先说明用户语义、允许 surface、对比类别和为什么现有 token 不能表达；
2. 在 `development/` 建立或关联 WORK，评估两个主题、全部组件消费者和未来主题兼容性；
3. 需要新增/改义合同、改变默认主题或组件默认行为时，先更新 DESIGN/DECISION 并获人工批准；
4. 同步主题、adapter、manifest、生成物、组件参考和校验，不能只修调用处。

紧急兼容代码也不得引入 raw color 或 theme-id 分支；确需临时例外时使用带 TASK 退出条件的 TODO，并在
同一工作项记录移除计划。

## 9. 当前实现边界

本设计系统已经作为文档包建立，但 `apps/web` 尚未迁移。现有 `globals.css` 的浅色 `:root`、`.dark`
选择器、运行时 resolver、偏好持久化、首屏防闪、字体和既有组件不代表本规范已经上线。

后续迁移必须另建 TASK，并至少完成：默认 `cherry-black`、显式 `pure-white`、未知值回退、manifest
派生 color scheme、首屏无闪烁、组件无 raw color/theme-id 分支、双主题 Storybook/Playwright 与可访问性
验证。迁移完成前，不得在产品说明中宣称用户已可切换新主题。

## 10. 评审清单

- 使用的是 semantic token，而不是 raw 颜色、primitive 或主题 id 吗？
- `primary`、中性 `accent`、`brand` 与 `danger` 是否各司其职且成对使用？
- 两个登记主题是否覆盖相同组件状态，并通过全部允许 surface 的对比要求？
- 状态和 verdict 是否有文字、code、图标或形状等非颜色信息？
- 是否覆盖键盘、320px、长中文、loading/error/disabled 和 reduced-motion？
- 若引入新语义，是否已走例外流程并同步合同、主题、adapter、参考与校验？
- 本次工作若涉及运行时，是否明确区分“设计系统已发布”和“Web 已完成迁移”？
