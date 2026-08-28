---
id: "DESIGN-012"
type: "design"
title: "建立 Cherry OJ Web 设计系统"
status: "approved"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["CAPABILITY-005", "EXPERIENCE-006"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# DESIGN-012：建立 Cherry OJ Web 设计系统

## 背景

CAPABILITY-005 要建立长期视觉合同。指定来源
`/Users/charon/Downloads/open-design-main/design-systems/linear-app` 是 OpenDesign 的 curated bundled
fixture，不是 Linear 官方源码。其可执行 `tokens.css` 只有默认黑色主题；`DESIGN.md` 虽列出少量 light
neutral，却没有完整浅色 token、状态映射或切换合同。

用户已明确三项方向：完整保留 Linear-derived 黑色并作为默认主题；基于相同结构新增以 `#ffffff` 为
画布的 pure-white 浅色主题；主题架构必须允许以后新增主题而不修改组件。Linear 的生效紫色继续按原
要求替换为 Cherry 色，其余字体、度量、组件结构与克制的视觉原则沿用。

## 目标与限制

本设计要同时冻结：

1. `cherry-black`：Linear fixture 的默认黑色值与行为，除 Cherry 品牌映射、OJ 语义、可访问性修正、
   来源声明外不漂移。
2. `pure-white`：真正的白色画布/抬升面，使用 Linear 文档给出的冷灰建立层级，并完整实现相同语义。
3. 一份 theme contract：未来主题只需完整实现合同并登记 manifest，组件和 Tailwind utility 无需增加
   theme-id 分支。

本任务只建立 `docs/` 设计系统，不改 `apps/web`、不实现切换器/持久化/首屏防闪、不复制字体文件，
也不复制 Linear 商标、Logo、产品文案或与 OJ 无关的营销模板。现有 Web 是否迁移必须由后续 TASK
处理，不能把设计文档交付描述为界面已上线。

## 整体方案

采用“共享结构 + 完整主题映射 + 单次适配 + 派生参考”：

```text
docs/design-system.md                  规范入口：原则、主题、组件与例外
docs/design-system/
├── README.md                          阅读顺序、版本、来源与修改说明
├── LICENSE.open-design                Apache-2.0 许可副本
├── NOTICE.md                          fixture 快照、非官方声明、Cherry 修改清单
├── tokens.css                         由 manifest 生成的稳定聚合入口
├── tokens.foundation.css              字体、字号、间距、圆角、布局、动效
├── theme-contract.json                必须实现的 semantic key、类型与使用限制
├── themes.manifest.json               defaultTheme、id、label、colorScheme、文件
├── themes/
│   ├── cherry-black.css               Linear-derived 黑色 + Cherry/OJ 映射
│   └── pure-white.css                 pure-white 完整映射
├── tailwind-v4.css                    theme-neutral Tailwind/shadcn adapter
├── design-tokens.json                 从 CSS/合同派生的机器可读快照
├── manifest.json                      包入口和文件角色
├── components.manifest.json           组件、状态和 semantic token 引用
├── components.html                    双主题 Cherry OJ 组件参考
├── tools/
│   ├── build.mjs                      由 manifest 生成聚合入口与机器快照
│   └── check.mjs                      合同、派生一致性与对比组合校验
└── preview/
    ├── themes.html
    ├── colors.html
    ├── typography.html
    └── spacing.html
```

`docs/design-system.md` 规定使用规则；`theme-contract.json` 只规定 key/类型/组合/门槛，不保存颜色值；
`tokens.foundation.css` 与 manifest 登记的 theme CSS 是数值真源。`tools/build.mjs` 读取 manifest 生成
带 generated 标记的 `tokens.css` import bundle 和 JSON 快照；`tools/check.mjs` 在未重新生成、文件
漏登记或派生漂移时失败。Tailwind/shadcn adapter 和 HTML 也都是派生物，不能反向成为真源。

Canonical token 使用 `--ds-*` 命名空间。Foundation 包含 Inter Variable、510/590 字重、字号、
tracking、spacing、radius、layout 与 motion；颜色、focus、selection、overlay、status 与
elevation/shadow 全部属于主题层。Primitive palette 只供主题文件映射，组件不得消费。

复制或改写自 OpenDesign 的每个文件都必须自身携带显著修改声明，而不只依赖集中 NOTICE：CSS/HTML/
Markdown 使用文件头注释说明“Modified for Cherry OJ”、日期和修改范围；JSON 顶层保存
`provenance`/`modified` 元数据。`LICENSE.open-design` 与 `NOTICE.md` 随包提供完整许可和集中归属。

## 主题选择与扩展合同

稳定主题 id 为 `cherry-black` 与 `pure-white`，其中前者是默认且明确记录 Linear fixture provenance；
不命名为 `linear-*`，避免把 Cherry 化后的主题误述为官方 Linear 产物。

```css
:root,
:root[data-theme='cherry-black'] {
  color-scheme: dark;
  /* 完整声明 required semantic keys */
}

:root[data-theme='pure-white'] {
  color-scheme: light;
  /* 完整声明同一组 required semantic keys */
}
```

`data-theme` 只放在 `<html>`，是主题选择唯一真源。缺失、空值、`cherry-black` 或未知 id 均使用
`:root` 的默认黑色；首次访问或没有有效持久化值时也固定黑色，不自动跟随 OS。未来运行时只持久化
theme id；未知旧值必须忽略。

`themes.manifest.json` 为每个主题记录 `id`、`label`、`colorScheme`、CSS 文件、provenance 和版本，
并声明 `defaultTheme: "cherry-black"`。若兼容 shadcn/Tailwind `dark:`，运行时可从 manifest 的
`colorScheme` 派生 `data-color-scheme="dark|light"`；该属性不持久化、不能被用户单独设置，也不能
成为第二真源。本 TASK 只冻结这些行为，localStorage key、resolver 与首屏脚本留给 Web 迁移任务。

`theme-contract.json` 固定 `schemaVersion: 1`，每个 required entry 至少包含 `name`、
`type: color|shadow`、`role`、`allowedOn`、`contrastClass: text|nonText|decorative|none` 和
`required: true`。`allowedOn` 引用同一合同中的 surface key；主题可以用 `var()` 在同一文件内建立
别名，但每个 key 都必须显式声明并能解析为最终值，不能依赖另一主题的值。Required key 为：

| 分组 | Required semantic keys |
|---|---|
| Surface | `--ds-canvas`、`--ds-panel`、`--ds-surface`、`--ds-surface-subtle`、`--ds-surface-raised`、`--ds-surface-hover` |
| Foreground | `--ds-fg`、`--ds-fg-2`、`--ds-fg-muted`、`--ds-fg-meta`、`--ds-fg-disabled` |
| Border | `--ds-border-soft`、`--ds-border`、`--ds-border-strong` |
| Brand/interaction | `--ds-brand-surface`、`--ds-brand-surface-hover`、`--ds-brand-surface-active`、`--ds-on-brand`、`--ds-brand-foreground`、`--ds-brand-foreground-hover`、`--ds-brand-soft`、`--ds-on-brand-soft`、`--ds-link`、`--ds-link-hover`、`--ds-focus`、`--ds-selection-surface`、`--ds-selection-foreground` |
| Status | success/warning/danger/info/special 各自的 `foreground`、`surface`、`border`、`solid`、`on-solid` |
| System/elevation | `--ds-overlay`、`--ds-elevation-flat`、`--ds-elevation-ring`、`--ds-elevation-raised` |

Status 行精确展开规则为：对 `S ∈ {success, warning, danger, info, special}`，必须声明
`--ds-S-foreground`、`--ds-S-surface`、`--ds-S-border`、`--ds-S-solid`、`--ds-S-on-solid`，共 25 个
key。除三个 `elevation-*` 为 `shadow` 外，上表其余 required key 均为 `color`；alias 最终也必须解析为
可计算的颜色或 shadow。所有承载内容的 neutral/brand/status `surface`、`soft`、`solid` entry 都声明
`opaque: true`，校验器拒绝透明值，确保嵌套时不受父背景改变；overlay 和装饰 border 可透明。

允许组合也属于合同，不由组件猜测：

| Consumer role | Allowed surfaces | 门槛 |
|---|---|---|
| `fg*` | canvas/panel/surface/subtle/raised/hover | normal text ≥4.5:1 |
| brand foreground/link/focus | 全部 neutral surface；brand foreground 另可在 brand-soft | text ≥4.5:1；focus ≥3:1 |
| `on-brand` | brand surface/hover/active | text ≥4.5:1 |
| status foreground/border | 全部 neutral surface 与同状态 opaque soft surface | text ≥4.5:1；必要边界 ≥3:1 |
| status `on-solid` | 同状态 solid | text ≥4.5:1 |
| `border-strong` | 全部 neutral surface | non-text ≥3:1 |
| `border` / `border-soft` | 仅装饰分隔 | 不得单独承担控件或状态识别 |

`tools/check.mjs` 必须展开 alias 并验证上述每个笛卡尔组合，而不是只测 canvas。新主题的步骤固定为：新增
完整 theme CSS → 在 manifest 登记 → 运行 build/check；不需要修改聚合入口、adapter、组件或 Tailwind。

每个主题 CSS 必须完整声明合同的 required key，不能只写相对黑色主题的颜色 delta，否则遗漏会混出
黑白两套值。只有 manifest 和主题 CSS selector 可以知道 theme id。组件、页面模板、CVA variant、
component manifest 和 Tailwind utility 均不得枚举 theme id。`forced-colors` 使用系统颜色，是环境
适配而不是第三个主题。

## 模块与数据

字体、尺寸和布局沿用来源：Inter Variable（全局 `cv01`/`ss03`）、400/510/590 三档主要字重、72→12px
类型阶梯、显示字负 tracking、4/8/12/16/20/24/32/48px 间距、6/8/12px 圆角、1200px 容器和
150/200ms 动效。中文使用系统 CJK 回退；Berkeley Mono 仅在环境已有合法字体时优先，不随包分发。

`cherry-black` 的非品牌核心映射如下。来源 `#62666d` 仍保留为 raw/decorative primitive；它对
`#191a1b` 仅 `3.022:1`，不得再用于 metadata 小字，semantic `fg-meta` 映射 `#8a8f98`。所有文本角色
在最亮 neutral surface `#28282c` 上仍至少为 `4.519:1`。

| Semantic role | `cherry-black` 值 | 使用说明 |
|---|---|---|
| `canvas` | `#08090a` | 默认页面画布 |
| `panel` | `#0f1011` | sidebar、稳定面板 |
| `surface` / `raised` | `#191a1b` | card、popover、dialog |
| `surface-subtle` | `#141516` | 来源 line-tint primitive；opaque recessed/中性 subtle |
| `surface-hover` | `#28282c` | hover/pressed 层级 |
| `fg` / `fg-2` | `#f7f8f8` / `#d0d6e0` | 主/次正文 |
| `fg-muted` / `fg-meta` / disabled | `#8a8f98` | 暗底小字最低可用 semantic 层 |
| `border-soft` / `border` | `rgba(255,255,255,.05/.08)` | 装饰/结构分隔 |
| `border-strong` | `#80848d` | 必要控件边界；对最亮 neutral surface `#28282c` 为 `3.918:1` |
| `overlay` | `rgba(0,0,0,.85)` | modal backdrop |
| `elevation-raised` | `rgba(0,0,0,.4) 0 2px 4px, 0 0 0 1px rgba(255,255,255,.05)` | 来源暗色 luminance elevation |

两个主题的 `elevation-flat` 均为 `none`，`elevation-ring` 均为
`0 0 0 1px var(--ds-border)`。实心颜色不使用模糊的通用 inverse token，而使用 `on-brand` 或对应
status 的 `on-solid`。

## Pure White 核心色

“pure white”指主画布和抬升面使用真正的白色；面板、输入区与 hover 仍需冷灰层级，不能把全部 surface
都设白。下列 sRGB hex 是待批准的 v1 数值真源：

| Semantic role | `pure-white` 值 | 使用与对比 |
|---|---|---|
| `canvas` | `#ffffff` | 页面主画布 |
| `panel` | `#f7f8f8` | sidebar、稳定面板 |
| `surface` / `raised` | `#ffffff` | card、popover、dialog |
| `surface-subtle` | `#f5f6f7` | input 区、表头、recessed 区 |
| `surface-hover` | `#f3f4f5` | 中性 hover/pressed 层级 |
| `fg` | `#191a1b` | 对白 `17.429:1` |
| `fg-2` | `#34343a` | 对白 `12.365:1` |
| `fg-muted` | `#62666d` | 对白 `5.768:1` |
| `fg-meta` / placeholder / disabled | `#676b73` | 对白 `5.347:1`；对 `#f3f4f5` 为 `4.856:1`，不得再叠 opacity |
| `border-soft` | `#e6e6e6` | 仅装饰分隔 |
| `border` | `#d0d6e0` | 普通结构分隔；不得单独承担控件识别 |
| `border-strong` | `#80848d` | 控件/必要图形；对白 `3.748:1`，对 `#f3f4f5` 为 `3.404:1` |
| `overlay` | `rgba(8,9,10,.56)` | modal backdrop |
| `elevation-raised` | `0 1px 2px rgba(8,9,10,.08), 0 8px 24px rgba(8,9,10,.08), 0 0 0 1px rgba(8,9,10,.08)` | 白底抬升层级 |

## Cherry 品牌映射

品牌 surface 与品牌 foreground 必须分开，canonical 名统一使用 `brand-*`，不能再由单一 `accent`
同时承担 CTA、link 与 focus。`link`/`link-hover` 分别显式 alias 到对应 brand foreground；
selection surface/foreground 分别 alias 到 brand-soft 与主题 `fg`。

| Role | `cherry-black` | `pure-white` | 规则 |
|---|---|---|---|
| `brand-surface` | `#de1c4e` | `#de1c4e` | 配白字 `4.792:1` |
| `brand-surface-hover` | `#dd2c53` | `#d7194b` | 配白字分别约 `4.606:1` / `5.085:1` |
| `brand-surface-active` | `#c01242` | `#c01242` | 配白字 `6.165:1` |
| `on-brand` | `#ffffff` | `#ffffff` | 只用于已验证的实心品牌 surface |
| `brand-foreground` / `focus` | `#f9667a` | `#c01242` | 分别适用于暗底/白底；不得跨主题复用 |
| `brand-foreground-hover` | `#ff8494` | `#a70f38` | link/icon hover |
| `brand-soft` | `#32141d` | `#fce7ed` | 不依赖父背景的 opaque selected/selection surface |
| `on-brand-soft` | `#f9667a` | `#c01242` | 对各自 soft 为 `5.752:1` / `5.223:1` |

`#f9667a` 对白只有 `2.913:1`，在 pure-white 中不得作文字、必要图标、焦点或边界。浅色 focus 使用
`2px solid #c01242` 与 `2px outline-offset`，不使用降低到临界值的透明 mix。正文链接常驻 underline：
浅色链接与正文之间只有 `2.827:1` 的颜色差，不能只靠色相识别。

来源的生效紫色 `#5e6ad2`、`#828fff`、`#4752c4` 必须从 active token 和参考组件消失；
`#7170ff`、`#7a7fad` 只存在于来源说明/示例，也不得被误写为生效 token。

## OJ 状态映射

每个 status 必须实现 `foreground`、`surface`、`border`、`solid`、`on-solid`；当边界承担识别时，
`border` 默认等于 foreground。颜色始终配合名称、代码、图标或形状。

| Theme | Status | foreground / soft surface | solid / on-solid |
|---|---|---|---|
| black | success | `#27a644` / `#14271a` | `#187a34` / `#ffffff` |
| black | warning | `#eab308` / `#2c2410` | `#eab308` / `#08090a` |
| black | danger | `#f97066` / `#321619` | `#dc2626` / `#ffffff` |
| black | info | `#60a5fa` / `#142236` | `#245ea8` / `#ffffff` |
| black | special | `#c084fc` / `#281a35` | `#6941c6` / `#ffffff` |
| white | success | `#087c2f` / `#ecfdf3` | `#087c2f` / `#ffffff` |
| white | warning | `#8a5a00` / `#fff8db` | `#eab308` / `#08090a` |
| white | danger | `#b42318` / `#fef3f2` | `#b42318` / `#ffffff` |
| white | info | `#175cd3` / `#eff8ff` | `#175cd3` / `#ffffff` |
| white | special | `#6941c6` / `#f4f3ff` | `#6941c6` / `#ffffff` |

Pure-white foreground 对白/soft 的对比分别为 success `5.337/5.061`、warning `5.927/5.561`、
danger `6.574/6.047`、info `5.986/5.571`、special `6.620/6.024`。来源
`#27a644`、`#eab308`、`#dc2626` 仍作为 Linear primitive 保留，但不能在白底分别充当所有文字、
边界和底色角色。暗色 `#dc2626` 对 `#191a1b` 仅 `3.609:1`，因此 danger foreground 改用
`#f97066`，原值继续作实心 danger surface。暗色 soft 全部使用 opaque 色，foreground 对各自 soft
分别为 success `4.962:1`、warning `8.011:1`、danger `5.961:1`、info `6.296:1`、special
`6.164:1`；嵌套在任何允许 neutral surface 时不受父背景改变。

Cherry magenta 与 danger red 仍不能只靠色相可靠区分：破坏性动作必须有危险/删除图标、明确危险动词
和必要确认；verdict 必须同时显示 code、名称与稳定形状。

## Tailwind v4 与 shadcn 边界

`tailwind-v4.css` 先建立下列 bare alias，再用一个 `@theme inline` 注册 `--color-*` utility；各主题只改
`--ds-*`。表中箭头右侧均省略统一的 `--ds-` 前缀；这是完整 adapter 合同，不允许派生文件另行猜测：

| Alias group | Canonical mapping |
|---|---|
| Page | `--background` → canvas；`--foreground` → fg |
| Project surface | `--surface` → surface；`--surface-subtle` → surface-subtle；`--surface-raised` → surface-raised |
| Card/popover | `--card`/`--popover` → surface-raised；对应 `*-foreground` → fg |
| Secondary/muted | `--secondary`/`--muted` → surface-subtle；`--secondary-foreground` → fg；`--muted-foreground` → fg-muted |
| Neutral accent | shadcn `--accent` → surface-hover；shadcn `--accent-foreground` → fg |
| Brand | `--primary` → brand-surface；`--primary-foreground` → on-brand；project `--brand` → brand-foreground；`--brand-soft` → brand-soft |
| Control | `--border` → border；`--border-strong`/`--input` → border-strong；`--input-background` → surface-subtle；`--ring` → focus |
| Destructive | `--destructive` → danger-solid；`--destructive-foreground` → danger-on-solid |
| Status | 对 `S ∈ {success,warning,danger,info,special}`：project `--S` → `--ds-S-foreground`；`--S-soft` → `--ds-S-surface`；`--S-solid` → `--ds-S-solid`；`--S-on-solid` → `--ds-S-on-solid` |
| Sidebar | `--sidebar` → panel；`--sidebar-foreground` → fg；`--sidebar-primary*` → brand surface/on-brand；`--sidebar-accent*` → surface-hover/fg；`--sidebar-border` → border；`--sidebar-ring` → focus |
| Charts | `--chart-1..5` → brand/success/info/warning/special foreground；图例同时显示名称/形状 |
| Radius/font | shadcn radius 与 Tailwind font alias → Foundation 的 `--ds-radius-*`、`--ds-font-*` |

来源品牌 `accent` 绝不能直接映射 shadcn `--accent`/`--accent-foreground`；前者在 shadcn 是一对中性
hover token。`primary` 与 `destructive` 是 surface-only alias：只允许
`bg-primary text-primary-foreground`、`bg-destructive text-destructive-foreground` 这类配对；普通文字
分别使用 `text-brand`、`text-danger`，禁止 `text-primary`/`text-destructive`。新组件优先不写 `dark:`；
兼容代码确有需要时，`dark:` 只能读取派生
`[data-color-scheme='dark']`，不得读取或枚举 theme id。

组件内禁止 raw hex/OKLCH、主题 selector 和承担必要对比的动态 `color-mix()`。若最终主题 CSS 改用
OKLCH，必须按浏览器解析后的 sRGB 重算对比，不能沿用本设计中的 hex 证据。

## 接口与状态

最小组件集合沿用来源 manifest：button/CTA、field、card/panel、pill/badge、link、icon slot、typography
与 layout；Cherry OJ 扩展 app shell、navigation/search、table/list、dialog/popover、inline notice、
empty/error/loading、editor workspace、Submission lifecycle 与 verdict。

每个组件 contract 记录 anatomy、size、variant、state、semantic token reference、键盘行为和禁止组合。
交互状态为 default → hover → pressed，键盘另有 focus-visible；disabled 不响应，loading 保持尺寸并
提供可读状态。所有已登记主题必须覆盖相同状态。破坏性 variant 永远消费 danger，不复用品牌色。

## 安全与失败

差异 allowlist 分两类：

- `cherry-black` 只允许 Linear→Cherry 命名/品牌色、来源许可、中文字体回退、OJ 语义和可访问性
  semantic 修正；来源 raw 值继续保留并标注不能使用的角色。
- `pure-white` 是 Cherry 设计的完整主题扩展，不做“与暗色逐值相等”检查；它必须沿用共享结构、使用
  Linear light neutral 证据，并通过 theme contract、对比度和双主题组件状态检查。

主要失败是 theme key 缺失导致黑白混用、组件判断 id、多真源、白底复用亮粉/黄色、品牌与 danger
混淆、许可/商标误述和 HTML 漂移。以完整性校验、raw 值扫描、四种主题选择场景、逐主题对比与视觉
检查、逐文件修改声明和唯一入口处理。

WCAG 2.2 验收不四舍五入：普通文字与 placeholder ≥4.5:1；必要控件边界、图标和 focus ≥3:1。
`border`/`border-soft` 低于 3:1 时只能作装饰，不能承担控件或状态识别。

## 监控与部署

设计系统作为版本库文档发布，不单独部署。release 是把已批准规范合并到默认分支；observe 是在干净
克隆复查入口、链接、派生一致性和首个后续组件采用。验证至少覆盖缺失 theme、显式
`cherry-black`、`pure-white`、unknown id 四种选择，以及两个主题的桌面/320px、键盘、长中文、
reduced-motion、状态和对比度。

## 迁移与兼容

批准后先创建新包，再更新 `docs/README.md`、`docs/frontend.md` 和 `docs/ui-system.html` 的权威关系；
`CLAUDE.md` 只增加未来 UI 工作必须读取设计系统的稳定入口，不复制 token。当前
`apps/web/src/styles/globals.css` 仍是 `:root` 浅色、`.dark` 深色，尚未实现新默认主题；运行时主题
resolver、选择器、持久化、首屏防闪、字体和组件迁移必须另建 TASK。

## 备选方案

1. **推荐：`cherry-black` 默认 + `pure-white` + 完整主题合同。** 符合用户已确认方向，兼顾来源保真、
   可访问性与未来扩展。
2. **只保留黑色。** 与用户新增 pure-white 要求冲突。
3. **浅色靠反色或只覆盖少数变量。** 容易遗漏状态、混出黑白值，无法验证。
4. **组件按 theme id 分支。** 初期直接，新增主题时会成倍扩散，不采用。

## 风险与重审条件

出现正式 Cherry 品牌手册、用户测试持续混淆品牌与 danger、WCAG 检查失败、theme contract 需要删除/
改义、Linear fixture 基础更新或 Web 技术栈改变时重新审核。新增一个完整实现既有合同并通过验证的主题
是兼容扩展；合同字段删除/改义、默认主题变化或组件默认行为变化必须重新走 DECISION。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：来源对照、token 映射、包结构与兼容方案已形成草案，提交人工审核
- 2026-08-27：按用户确认改为 `cherry-black` 默认 + `pure-white` + 可扩展主题合同；补充逐主题色值、
  theme selector、Tailwind/shadcn 适配和对比度证据，继续等待人工审核
- 2026-08-27：状态变更：review → approved。原因：用户已审核并批准 cherry-black、pure-white 与可扩展主题合同
