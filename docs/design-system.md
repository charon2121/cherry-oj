# Cherry OJ Web 设计系统

> **状态：已确认的长期规范。** 本文与 [`design-system/`](./design-system/) 共同定义 Cherry OJ
> 后续 Web 组件、业务组件和页面的视觉与交互合同。当前 2.0 系统由
> [WORK-034 / DECISION-019](../development/works/WORK-034/40-decision-DECISION-019.md) 确认，以用户认可的
> Claude Design 下载版为直接视觉来源，同时保留正式浅色主题。它不是 Linear 官方设计系统；来源、许可、
> 冻结摘要与生产适配见 [`design-system/NOTICE.md`](./design-system/NOTICE.md)。

## 1. 权威关系

设计意图与 Web 可执行资产分层持有，依赖方向不能倒置：

1. 本文规定设计原则、消费规则、组件合同和例外流程；
2. [`design-system/source/claude-design-v1/`](./design-system/source/claude-design-v1/) 是用户认可下载版的
   原样来源证据，[`source-lock.json`](./design-system/source-lock.json) 锁定其 99 文件、239831 bytes、
   相对路径与逐文件摘要；来源定义视觉和构图，不授权直接复制原型实现；
3. `docs/design-system/` 的合同、Foundation、主题 CSS、生成快照和 HTML 帮助人检查中文生产解释是否完整，
   但不是 Web 安装、开发、检查或构建的输入；
4. `apps/web/design-system/` 是 Web 的可执行真源，持有运行所需的 Foundation、主题、manifest、合同、
   Tailwind adapter、生成器、校验器、来源与许可证；
5. 普通 Web CI 不比较、复制或链接代码树与文档树。只有真正修改设计系统时，才在同一 WORK/TASK 中
   同步两侧，并分别通过代码检查与文档参考检查；
6. Storybook 和业务页面用于验证生产实现；来源 snapshot 用于视觉对照。preview HTML 只检查 Foundation
   与主题，任何一方都不能反向改变已批准的业务、路由、权限或 API 合同。

因此删除 `docs/design-system/` 不得影响 Web 的 `npm ci`、开发服务器、检查、生产构建、Storybook 或
E2E。文档包自身的入口、生成和校验命令见 [`design-system/README.md`](./design-system/README.md)。

组件生产参考入口是 Storybook（`cd apps/web && npm run storybook`），它渲染真实组件并覆盖两个主题和
a11y；来源视觉入口是冻结 snapshot 的 specimen 与 app UI kit。实现验收必须在相同 viewport/state 下对照
来源与生产截图，不能只看 token 是否通过校验。静态 preview 不承担 React 组件参考职责。

## 2. 设计方向

Cherry OJ 采用下载版定义的 instrument panel / Focused Workspace：近黑或纯白画布、冷灰层级、紧凑但
可读的信息密度、精确对齐、细 hairline、克制圆角和几乎不打扰的状态变化。它服务于“读题—编码—提交—
诊断”这一长时间专注流程，不复制来源 demo 的营销功能、Linear 商标、Logo 或产品文案。

所有界面遵守以下原则：

- 导航退后，当前任务获得主要对比度；普通分组优先用间距、对齐、透明 surface 和分隔线，不把每个分组
  都做成不透明大 Card。
- Foundation 使用本地 Inter Variable、中文系统字体回退、本地 JetBrains Mono，并保留已合法安装的
  Berkeley Mono 为第一首选；工作字重为 400/510/590，禁止 700。
- 间距以 8px 为基础，7/11/19/22px 只用于来源明确的光学校准；圆角完整覆盖 2/4/6/8/12/22px、pill
  和 circle；桌面应用 chrome 固定为 220px sidebar 与 56px header。
- 只允许 opacity、color、background-color 使用 150/200/320ms 与标准 easing；禁止 transform、bounce、
  spring、parallax 和装饰性入场动画，reduced-motion 下时长归零。
- 不使用 gradient、photo、illustration、texture、noise、pattern 或 backdrop blur；空间由留白/留黑、明度
  台阶和 hairline 建立，不靠大面积装饰。
- 品牌、危险和 OJ 状态是不同语义。Cherry 品牌色可以表示主操作和品牌链接，不能代替 destructive。
- 页面由稳定的 shell、列表、工作台和详情模板组合；真实业务差异进入内容区，不重新发明视觉语言。

## 3. 主题模型

### 3.1 已登记主题

| Theme ID       | Color scheme | 定位                                                                              |
| -------------- | ------------ | --------------------------------------------------------------------------------- |
| `cherry-black` | dark         | 默认主题；精确承接 Claude Design 下载版暗色视觉，在生产可访问性边界内适配交互状态 |
| `pure-white`   | light        | 同一系统的正式浅色主题；结构与非颜色 token 完全同构，以冷灰/白重新建立层级        |

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

1. 建立一个设计系统 WORK/TASK，并把代码侧与本文档包同时列入写入和验证范围。
2. 在 `apps/web/design-system/` 新增完整 theme CSS，并在代码侧 manifest 登记稳定 id、label、color
   scheme、文件、provenance 和版本；不得只覆盖相对默认主题的差值。
3. 同步本文、[`themes.manifest.json`](./design-system/themes.manifest.json) 和相应参考资产，分别运行 Web
   本地 build/check 与文档包 build/check；不使用日常 drift、prebuild copy 或 symlink 自动同步。
4. 用同一组件矩阵验证桌面、320px、键盘、长中文和 reduced-motion。

新增完整主题不应修改 Tailwind adapter、组件或页面。删除/改义合同字段、改变默认主题或改变组件默认行为
属于破坏性变更，必须先建立 WORK 并重新走 DECISION。

## 4. Token 消费规则

Foundation token 与主题 token 分层：字体、字号、间距、圆角、布局和 motion 全主题共享；颜色、focus、
selection、overlay、status 与 elevation 由每个主题完整实现。Token 统一使用 `--ds-*`；本文档包说明这些
语义，Web 实际解析和校验 `apps/web/design-system/` 中的实现。

下载版核心组件明确依赖三档透明 surface（2% rest、4% hover、5% selected）、ghost 实线边界、tertiary
分隔线和 subtle/inset/dialog elevation。它们分别映射为 `surface-translucent*`、`border-solid`、
`line-tertiary` 与 `elevation-subtle/inset/dialog`，必须由两个主题完整实现，不能在组件里重新写 rgba 或 shadow。

组件和页面必须：

- 只消费代码侧设计系统提供的语义 token 或稳定 Tailwind alias；本文档中的
  [`tailwind-v4.css`](./design-system/tailwind-v4.css) 仅供设计评审和同步维护参考；
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
`on-brand-soft`。实心 CTA 使用来源 Cherry `#d2042d`；为确保白字在全部按钮状态可读，hover/active 使用同色
族更深的 `#a80324` / `#7d0219`。暗色品牌文字/focus 使用 `#ff4d67`，hover 可到 `#ff7088`；pure-white
文字、链接和 focus 使用可达对比的深 Cherry。暗色亮粉不得直接搬到白底正文或必要图标。

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

**基础组件不自己造。** 新增第 2 层基础组件前先查 shadcn registry：
`https://ui.shadcn.com/r/styles/base-nova/<name>.json` 返回 200 即官方有实现，必须以官方实现为骨架，
只把其中的颜色与尺寸 class 换成本仓库语义 token，并按需追加 OJ 语义变体；官方的 DOM 结构、属性接口
与可访问性行为不重写。返回 404 才允许手写，并在组件清单中记下这个依据。

已知官方没有对应实现的是 `link`、`typography` 和 `layout`——shadcn 的定位是交互组件，排版与布局原语
它一向不提供。第 3、4 层的 OJ 业务组件和页面模板本就没有官方对应，按本文合同自行设计。

视觉参考见 Storybook（`cd apps/web && npm run storybook`）与 [`preview/`](./design-system/preview/)；
它们用于评审，不是 token 或组件行为的真源。

WORK-034 的来源核心配方为 Button、IconButton、Pill、Input、SearchInput、Textarea、Container、Stack、
NavBar、Badge、Card、Eyebrow、Heading 与 Text。生产实现保留其 anatomy、精确密度、surface、圆角和状态层级，
同时把原型中的 clickable span、随机 id、鼠标状态、inline style 与手写 SVG 转换成原生语义、`useId`、CSS
状态、共享 class 与 Lucide。Dialog、Popover、Sheet、Sidebar、Select、Notice 和 TextEditor 属于 Cherry OJ
扩展，但必须使用同一套视觉语法；浮层使用 `elevation-dialog`，普通内容 Card 不默认抬升。

### 6.1 长内容与编辑器选择

多行输入不能只按“后台页面”或“看起来像文本”选择组件。先判断内容是否短、是否有结构/语法，以及用户
是否需要搜索、行号、稳定撤销和大段编辑：

- `Input` 只承载单行值；`Textarea` 只用于短而简单的多行纯文本，例如备注、拒绝原因或几行补充说明。
  不把它用于题面、长 Markdown、长样例、代码或预计需要持续滚动编辑的内容。
- 长 Markdown、长纯文本和后台代码使用项目统一的 `TextEditor`/`MarkdownEditor`。当前基线采用
  CodeMirror 6；项目维护 React 适配、Cherry 主题、有限扩展与可访问性合同，不 fork、复制或自行维护
  上游编辑器内核。
- Monaco 是否使用取决于任务复杂度，不简单等同于“前台可以、后台不可以”。只有用户端代码工作台需要
  IDE 级补全、诊断、命令和大文件能力时才优先评估 Monaco；后台基础 Markdown/C++ 录入默认 CodeMirror。
  某个后台功能确实需要完整 IDE 能力时必须在对应 DESIGN 中说明收益、移动端边界和 bundle 代价。
- JSON、CSV、逗号分隔文本等序列化格式不是普通运营人员的默认表单。可重复业务对象必须用结构化字段、
  列表或卡片编辑，序号和序列化由系统生成；原始格式只可作为明确的高级导入/导出能力。

编辑器必须完整实现 Field 合同：可见 label、required、description、error、唯一可访问名称、focus-visible、
disabled/read-only/loading 和错误关联。多个编辑器不能都叫“Editor content”。编辑器默认不得形成 Tab 键
陷阱，中文输入法组合期间不得触发值回写、保存或校验打断输入；两个主题、320px、200% 缩放、
forced-colors 和 reduced-motion 都必须验收。

编辑器主题只消费 `--ds-*` 语义 token 和生成主题 registry 提供的 color scheme，不写 raw color、主题 id
分支或第三套编辑器主题真源。Markdown 预览继续把内容视为不可信输入并净化，不能因为编辑器提供高亮就
放开原始 HTML。长编辑器应使用有界默认高度和“展开编辑”，避免随全文无限增高；窄屏使用编辑/预览切换，
不能把分屏硬压成两列。

## 7. 页面结构、间距与滚动

页面进入后应直接呈现当前任务，不在真实操作组件之前重复展示“栏目眉题 + 页面标题 + 页面描述”式介绍区：

- 列表页以筛选器、列表或空/错状态开始，管理页以创建表单、工作面板或数据表开始；不得为填充首屏而补写
  宣传文案、接口连通状态或没有操作价值的占位内容。
- 本规则只禁止通用页面介绍。题目名称、版本名称等业务对象标题，表单/面板/区段标题，登录身份标题，以及
  403、404、loading、empty、error 等状态标题仍须保留。
- 每个路由仍需有可访问的页面名称。若界面不需要显示页面标题，使用文档标题或 `sr-only` 的一级标题，
  不得为了视觉规则移除语义层级。

Shell 导航底部到页面第一个可见主内容的垂直距离统一为 `--ds-space-6`（24px），手机、平板与桌面一致。
页面通过共享 `Section` 布局原语消费该间距，不得用 `py-*`、断点 class 或私有 magic number 重写顶部
距离。页面底部留白可由模板按内容密度决定，但必须使用既有间距 token；确需偏离 24px 的新模板必须先在
对应 DESIGN 中说明任务原因并经过评审。

跨用户端与管理端的入口属于账号上下文切换，统一放在头像菜单中：用户端管理员菜单显示“管理中心”，管理端
头像菜单显示“返回用户端”；管理端顶部导航不得再并列放置独立返回按钮。

管理端桌面布局使用视口内滚动：顶部导航和左侧菜单保持原位，只有中间主内容区产生纵向滚动。左侧菜单自身
内容超出可用高度时可在菜单内部滚动。手机端继续使用 Sheet 承载管理导航，页面维持浏览器文档滚动，不能把
桌面侧栏的滚动模型套到移动端。

### 7.1 长任务工作台

需要数分钟以上才能完成、包含三个及以上依赖阶段的工作，不能把后端字段和接口按实现顺序铺成一个超长
表单。页面应按使用者目标建立步骤或可定位章节，并持续表达：当前对象、当前步骤、已完成内容、未保存
内容、下一步和阻塞原因。步骤导航是方向提示，不得用前端完成度冒充服务端最终就绪状态。

以下模式禁止作为长任务的默认实现：

- 把“创建新对象”和“管理已有对象”两个主任务长期并排或上下堆在同一首屏；短创建流程使用明确入口和
  Dialog/专页，列表首屏仍服务于查找与管理。
- 只有页面顶部一个保存按钮，滚动后既看不到操作也看不到保存状态；长工作台必须让保存入口和“未保存、
  保存中、已保存、失败/冲突”状态持续可达，并在离开时保护真实未保存内容。
- 直到页面底部才告诉用户发布/提交缺什么；就绪摘要应在导航或上下文栏持续可见，最终检查项能定位到
  对应步骤。
- 让用户靠点击禁用按钮猜测依赖；禁用控件旁必须有持久、可读的原因，tooltip 不能是唯一解释。
- 把 ns、bytes、hash、ordinal、内部状态码等系统事实作为默认主输入/主文案；界面使用人的单位和语言，
  精确底层值放技术详情，并在请求边界无损换算。
- 把保存、发布、可见性切换、创建修订、归档和删除放在同一操作组；普通编辑、生命周期和危险操作必须
  分层，危险操作使用项目 Dialog 明确对象、影响、可逆性和结果不明时的恢复方式。

分步工作台仍需保证未显示步骤的表单状态不会因卸载丢失；当前步骤应进入可验证的 URL 状态，刷新、前进
后退和直接访问能恢复。是否自动保存必须结合并发版本、敏感内容和服务端契约决定，不能把“现代”当作
理由：若采用明确保存，应提供快捷键、持续状态和离开保护；若采用自动保存，应提供队列、冲突和失败恢复。

## 8. 可访问性与响应式

- 普通文字、metadata、placeholder 与其所有允许背景的对比度至少 `4.5:1`；必要控件边界、图标和
  focus 至少 `3:1`，验收不四舍五入。
- 正文链接保持 underline；焦点使用可见的 2px outline 与 offset，不能只靠透明光晕。
- 交互使用语义 HTML、可访问名称、正确 label 和错误关联；键盘操作与指针操作能力一致。
- 320px 下不允许关键操作或状态被裁切；长中文不能破坏按钮尺寸、表格可读性或工作台主流程。
- `prefers-reduced-motion` 下禁用非必要位移与平滑滚动；forced-colors 使用系统色，它是环境适配而不是
  新主题。

## 9. 例外与变更流程

没有“只在这个页面写一个颜色”的快速例外。若现有语义不足：

1. 先说明用户语义、允许 surface、对比类别和为什么现有 token 不能表达；
2. 在 `development/` 建立或关联 WORK，评估两个主题、全部组件消费者和未来主题兼容性；
3. 需要新增/改义合同、改变默认主题或组件默认行为时，先更新 DESIGN/DECISION 并获人工批准；
4. 在同一 WORK/TASK 中同步 `apps/web/design-system/` 与本文档包的主题、adapter、manifest、生成物、
   组件参考和各自校验，不能只修调用处，也不能给普通 CI 增加跨树复制或漂移门禁。

紧急兼容代码也不得引入 raw color 或 theme-id 分支；确需临时例外时使用带 TASK 退出条件的 TODO，并在
同一工作项记录移除计划。

## 10. 当前实现边界

Web 的 2.0 Foundation 已登记默认 `cherry-black`、显式 `pure-white`、未知值回退、manifest 派生 color
scheme、首屏防闪和主题偏好合同。可执行资产与内部合同位于
`apps/web/design-system/`；Web 的 install/check/dev/build/Storybook/E2E 不读取本目录，移除本文档包
不会改变前端行为或质量门禁。`contracts/web-api.openapi.json` 的 OpenAPI 生成检查仍是 Web 明确保留的
monorepo 契约依赖，不属于设计文档依赖。

共享 UI 与 Storybook 已迁移到 14 个来源核心配方及同语法生产扩展。ThemeSwitcher、真实 Shell 和页面的
2.0 迁移由 WORK-034 后续 TASK 分阶段完成；最终交付前不得发布旧页面与新组件的混搭状态。切换入口只在
这两个正式主题间工作，沿用同一偏好 key 和无闪烁首屏流程，不新增“旧系统/新系统”开关。

## 11. 评审清单

- 使用的是 semantic token，而不是 raw 颜色、primitive 或主题 id 吗？
- `primary`、中性 `accent`、`brand` 与 `danger` 是否各司其职且成对使用？
- 两个登记主题是否覆盖相同组件状态，并通过全部允许 surface 的对比要求？
- 状态和 verdict 是否有文字、code、图标或形状等非颜色信息？
- 是否覆盖键盘、320px、长中文、loading/error/disabled 和 reduced-motion？
- 若引入新语义，是否已走例外流程并同步合同、主题、adapter、参考与校验？
- 真实设计系统变更是否由同一 WORK/TASK 同步代码侧与设计说明，并分别验证两侧？
- 普通 Web 命令是否仍完全不读取、复制或链接设计文档目录？
- 页面是否直接进入任务、保留语义标题，并统一使用 24px 的首内容间距？
- 管理端桌面是否只滚动主内容区，跨端入口是否只出现在头像菜单？
