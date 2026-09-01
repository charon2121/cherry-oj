<!--
Modified for Cherry OJ on 2026-08-27.
Scope: replaces the OpenDesign Linear fixture's single dark reference with a
Cherry-branded, dual-theme, extensible design-system package and OJ examples.
-->

# Cherry OJ 设计系统说明包

这里是 Cherry OJ Web 视觉合同的设计说明与评审参考。它保留 OpenDesign `linear-app` fixture 的紧凑
排版、冷灰层级、克制圆角和短动效，以 Cherry 品牌色替换紫色，并补齐 OJ 状态、pure-white 浅色主题与
可扩展主题合同。

权威使用规则在 [`../design-system.md`](../design-system.md)。本目录用于帮助人阅读、讨论和检查设计，
不是 Web 的安装、开发、检查、构建、Storybook 或 E2E 输入。Web 的可执行设计系统真源位于
`apps/web/design-system/`；移除本目录不会影响前端。

## 从哪里开始

1. 阅读 [`../design-system.md`](../design-system.md)，确认语义、可访问性和组件约束。
2. Web 实现只加载 `apps/web/design-system/tokens.css` 与代码侧 Tailwind adapter；组件只消费
   `--ds-*` semantic token，不从本目录 import 或复制文件。
3. 组件与交互在 Storybook 中检查（`cd apps/web && npm run storybook`），它渲染的是真实组件；
   主题、颜色、字体和间距在 [`preview/`](./preview/themes.html) 中检查。
4. 只有真正修改设计系统时，才在同一 WORK/TASK 中同时更新 `apps/web/design-system/` 和本目录；
   两侧分别验证，不建立普通 CI 的 drift、copy 或 symlink。
5. 更新本说明包后，从仓库根目录运行 `node docs/design-system/tools/build.mjs`，再运行
   `node docs/design-system/tools/check.mjs`。这两条命令只检查文档参考，不被任何 Web 命令调用。

## 文件角色

| 文件 | 文档包中的角色 | 是否手改 |
|---|---|---|
| `tokens.foundation.css` | 字体、字号、间距、圆角、布局与动效的设计参考 | 是 |
| `theme-contract.json` | 每个主题的 semantic key、类型、允许组合与对比门槛 | 是，不保存颜色值 |
| `themes.manifest.json` | 参考主题登记、默认主题、色彩模式和版本 | 是 |
| `themes/*.css` | 每个参考主题的完整 semantic 映射 | 是 |
| `tokens.css` | 由文档 manifest 生成的参考 CSS 入口 | 否，禁止手改 |
| `design-tokens.json` | 由 Foundation、合同和主题生成的机器参考快照 | 否，禁止手改 |
| `tailwind-v4.css` | Tailwind/shadcn 语义映射参考 | 是，不保存主题值 |
| `components.manifest.json` | 组件 anatomy、状态、键盘行为与 token 引用 | 是 |
| `preview/*.html` | 双主题的主题、颜色、字体与间距检查页 | 由工具维护或人工评审 |
| `manifest.json` | 文档包入口与各文件角色 | 是 |

本目录内部仍以 Foundation、合同和 manifest 登记的主题 CSS 保持说明一致；不要从 HTML、截图、机器
快照或 Tailwind alias 反推设计值。对 Web 而言，实际解析、生成和校验的值一律来自代码侧本地包。

## 主题选择

- 默认主题是 `cherry-black`；缺失、空值或未知 `data-theme` 都回退到默认黑色。
- 浅色主题是 `pure-white`；真正的白色用于画布和抬升面，冷灰用于面板、输入和 hover 层级。
- `data-theme` 只放在 `<html>`。组件、页面、CVA variant 和 Tailwind utility 不得判断主题 id。
- 本目录只说明主题合同与选择行为，不提供 Web 的 localStorage、首屏防闪或运行时。对应实现与测试均在
  `apps/web` 内完成。

`preview/` 下的静态页会从 `themes.manifest.json` 动态建立主题选择器，因此 HTML 不枚举主题 id。
请从仓库根目录启动任意静态服务器后浏览，例如 `python3 -m http.server`，再打开
`/docs/design-system/preview/themes.html`。直接用 `file://` 打开时浏览器可能拒绝读取 manifest；
页面仍按默认主题显示，但主题选择器会保持禁用。

组件本身不在本目录检查——手写 HTML 无法引用 React 组件，只能手抄 class，必然与真实组件漂移。
组件的视觉参考由 Storybook 承担。

## 新增主题

新增主题是完整实现合同，而不是覆盖几个颜色变量：

1. 先建立或关联设计系统 WORK/TASK，并把代码侧本地包与本目录同时纳入范围。
2. 在代码侧新建完整主题并登记 manifest；每个 surface/soft/solid 必须不透明，alias 必须可解析。
3. 同步本目录的主题 CSS、`themes.manifest.json`、合同与参考页面，不依赖自动复制或符号链接。
4. 分别运行 Web 本地 build/check 与本目录 build/check，确认 required key、允许 surface 的全组合对比、
   派生文件和未知主题回退。
5. 在 Storybook、E2E、组件参考与 preview 中做桌面、320px、键盘、长中文和 reduced-motion 检查。

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
[`NOTICE.md`](./NOTICE.md)。Web 代码侧同时保存并分发自身所需的许可证与 NOTICE，不能依赖本目录作为
唯一合规副本。本包不包含 Linear 商标、Logo、产品文案或字体文件。

参考 HTML 中出现的 Lucide 图标路径来自项目已安装的 `lucide-react` 1.33.0；许可包含 ISC 与
Feather-derived MIT 条款，见 [`LICENSE.lucide`](./LICENSE.lucide)。产品实现应直接使用依赖，而不是
复制 SVG 路径。
