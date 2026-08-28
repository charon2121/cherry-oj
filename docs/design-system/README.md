<!--
Modified for Cherry OJ on 2026-08-27.
Scope: replaces the OpenDesign Linear fixture's single dark reference with a
Cherry-branded, dual-theme, extensible design-system package and OJ examples.
-->

# Cherry OJ 设计系统包

这里是 Cherry OJ Web 视觉合同的可执行参考。它保留 OpenDesign `linear-app` fixture 的紧凑排版、
冷灰层级、克制圆角和短动效，以 Cherry 品牌色替换紫色，并补齐 OJ 状态、pure-white 浅色主题与
可扩展主题合同。

权威使用规则在 [`../design-system.md`](../design-system.md)。本目录是它的 token、适配器、机器合同与
静态参考实现，不能反向覆盖规范。

## 从哪里开始

1. 阅读 [`../design-system.md`](../design-system.md)，确认语义、可访问性和组件约束。
2. 在实现中加载 [`tokens.css`](./tokens.css)，组件只消费 `--ds-*` semantic token。
3. Tailwind v4 / shadcn 项目加载 [`tailwind-v4.css`](./tailwind-v4.css)，使用已登记的语义别名。
4. 用 [`components.html`](./components.html) 检查 OJ 组件与交互；主题、颜色、字体和间距分别在
   [`preview/`](./preview/themes.html) 中检查。
5. 修改主题后运行 `node docs/design-system/tools/build.mjs`，再运行
   `node docs/design-system/tools/check.mjs`。

## 文件角色

| 文件 | 角色 | 是否数值真源 |
|---|---|---|
| `tokens.foundation.css` | 字体、字号、间距、圆角、布局与动效 | 是 |
| `theme-contract.json` | 每个主题必须实现的 semantic key、类型、允许组合与对比门槛 | 合同真源，不保存颜色值 |
| `themes.manifest.json` | 主题登记、默认主题、色彩模式和版本 | 是 |
| `themes/*.css` | 每个主题的完整 semantic 映射 | 是 |
| `tokens.css` | 由 manifest 生成的稳定 CSS 入口 | 否，禁止手改 |
| `design-tokens.json` | 由 Foundation、合同和主题生成的机器快照 | 否，禁止手改 |
| `tailwind-v4.css` | 与主题无关的 Tailwind/shadcn 单次适配 | 否，不保存主题值 |
| `components.manifest.json` | 组件 anatomy、状态、键盘行为与 token 引用 | 组件合同 |
| `components.html`、`preview/*.html` | 双主题视觉和交互检查页 | 否 |
| `manifest.json` | 包入口与各文件角色 | 包合同 |

`tokens.foundation.css` 与 `themes.manifest.json` 登记的主题 CSS 才能提供数值。不要从 HTML、截图、
`design-tokens.json` 或 Tailwind alias 反推 token。

## 主题选择

- 默认主题是 `cherry-black`；缺失、空值或未知 `data-theme` 都回退到默认黑色。
- 浅色主题是 `pure-white`；真正的白色用于画布和抬升面，冷灰用于面板、输入和 hover 层级。
- `data-theme` 只放在 `<html>`。组件、页面、CVA variant 和 Tailwind utility 不得判断主题 id。
- 本包只冻结主题合同与选择行为，不提供 localStorage、系统主题跟随、首屏防闪或 Web 运行时迁移。

静态参考页会从 `themes.manifest.json` 动态建立主题选择器，因此 HTML 不枚举主题 id。请从仓库根目录
启动任意静态服务器后浏览，例如 `python3 -m http.server`，再打开
`/docs/design-system/components.html`。直接用 `file://` 打开时浏览器可能拒绝读取 manifest；页面仍按
默认主题显示，但主题选择器会保持禁用。

## 新增主题

新增主题是完整实现合同，而不是覆盖几个颜色变量：

1. 新建一个主题 CSS，在唯一 selector 内显式声明 `theme-contract.json` 的全部 required key。
2. 每个 surface/soft/solid 必须是不透明颜色；同文件 alias 必须能解析到最终颜色或 shadow。
3. 在 `themes.manifest.json` 登记稳定 id、label、`colorScheme`、版本、来源和文件路径。
4. 运行 build/check，确认 required key、允许 surface 的全组合对比、派生文件和未知主题回退。
5. 在组件与四个 preview 中做桌面、320px、键盘、长中文和 reduced-motion 检查。

新增主题不需要修改组件、`components.manifest.json`、Tailwind adapter 或聚合入口。若需要删改 semantic
key、改变默认主题或改变组件默认行为，必须先更新上游 DECISION，而不是伪装成兼容扩展。

## 组件约束

- 组件只使用 semantic token；禁止 raw hex/OKLCH、主题 selector 和依赖背景的透明 soft surface。
- 品牌 CTA 使用 `brand-surface` / `on-brand`；普通链接使用 `link`；中性 hover 使用 `surface-hover`。
- destructive 永远使用 danger，不复用 Cherry 品牌色。所有 verdict 同时显示 code、名称和稳定文本结构。
- 正文链接常驻下划线；状态与图表不能只靠颜色；必要控件边界、图标和 focus 至少达到 3:1。
- 原生交互优先：按钮、链接、表单、`details` 和 `dialog` 必须键盘可用；只在 `:focus-visible` 显示
  清晰焦点环。disabled 不响应，loading 保持尺寸并提供可读状态。
- 图标使用项目依赖的 Lucide React；图标是辅助信息时 `aria-hidden="true"`，图标按钮必须有可访问名称。

## 来源与许可

基础参考来自本地 OpenDesign `linear-app` bundled fixture，并按 Apache-2.0 修改；它不是 Linear 官方设计
系统或源码。完整许可见 [`LICENSE.open-design`](./LICENSE.open-design)，固定来源、校验值和修改清单见
[`NOTICE.md`](./NOTICE.md)。本包不包含 Linear 商标、Logo、产品文案或字体文件。

参考 HTML 中出现的 Lucide 图标路径来自项目已安装的 `lucide-react` 1.33.0；许可包含 ISC 与
Feather-derived MIT 条款，见 [`LICENSE.lucide`](./LICENSE.lucide)。产品实现应直接使用依赖，而不是
复制 SVG 路径。
