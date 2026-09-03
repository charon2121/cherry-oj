<!--
Modified for Cherry OJ on 2026-09-03.
This Web-owned package productionizes the frozen Claude Design source without
creating a runtime dependency on repository documentation.
-->

# Cherry OJ Web 设计系统代码包

本目录是 `apps/web` 唯一的可执行设计系统真源。WORK-034 以
`docs/design-system/source/claude-design-v1/` 中冻结的 99 文件 Claude Design 产物为视觉与构图依据，
在这里转换成 React、TypeScript、Tailwind、Base UI 和可访问性检查可直接消费的生产合同。Web 的安装、
构建、Storybook 与 E2E 不读取 `docs/`，也不执行下载包中的 demo 代码。

## 一套系统、两个主题

- `cherry-black` 是默认暗色主题，精确保留来源的冷黑 surface、文字层级、Cherry 色阶与光学尺寸；
- `pure-white` 是正式浅色主题，复用完全相同的组件 anatomy、间距、排版、圆角、布局和动效，只重新校准
  必须随明暗变化的颜色与 elevation；
- 两个主题都完整实现 `theme-contract.json`，组件不得按 theme id 分支，也不得维护第二套 DOM 或 variant；
- 来源明确的三档透明 surface、ghost solid border、tertiary line 与 subtle/inset/dialog elevation 都是正式
  semantic token；组件不得内联 rgba 或 shadow 重造它们；
- `--ds-brand-surface` 沿用来源 Cherry 实心色。为了保证白字在按钮全部状态可读，hover/active 使用
  来源同色族更深的两档；亮 Cherry 只用于暗色前景与链接。具体色值只以 `themes/*.css` 为准——
  本文不复述任何设计值，复述出来的那一份迟早会和真源对不上。

## 文件角色

| 文件 | 角色 |
|---|---|
| `tokens.foundation.css` | 全主题共享的字体、光学字号/间距、圆角、布局与动效 |
| `theme-contract.json` | 每个主题必须完整实现的 semantic key、允许组合与对比合同 |
| `themes.manifest.json` | 主题登记、默认/fallback、color scheme、来源与版本 |
| `themes/*.css` | 两个主题的完整 semantic token 源 |
| `tokens.css` | 由 builder 生成的稳定 CSS 入口，禁止手改 |
| `design-tokens.json` | 由 builder 生成的值锚点快照，禁止手改；改动任何设计值都必须重新生成 |
| `tailwind-v4.css` | 主题无关的 Tailwind/shadcn adapter |
| `manifest.json` | 包版本、来源摘要和文件完整性清单 |
| `tools/build.mjs` | 确定性生成 `tokens.css` 与 `design-tokens.json` |
| `tools/check.mjs` | 校验合同结构、主题同构、对比度、废弃旧值、来源、许可和生成物；**不持有任何设计值** |
| `NOTICE.md` / `LICENSE.open-design` / `LICENSE.fonts` | 来源链、生产适配、OpenDesign Apache-2.0 与字体 OFL-1.1 许可 |

## 生产适配边界

下载版定义视觉，不直接定义生产实现。项目继续使用真实语义元素、Base UI、`useId`、Lucide React、本地
Inter/JetBrains Mono、CodeMirror 和现有业务状态；禁止把来源中的 inline style、鼠标 hover state、随机
id、内联 SVG path、远程字体和 demo 功能复制进生产。原型中不满足 WCAG 的 disabled/hover 配方按合同
收紧，并在 NOTICE 中说明。

核心组件参考位于 Storybook 的 `Foundation/Source recipes`：Button、IconButton、Pill、Input、SearchInput、
Textarea、Container、Stack、NavBar、Badge、Card、Eyebrow、Heading 与 Text。浮层、反馈、Sidebar、Select
和 TextEditor 是产品扩展，但必须复用同一 surface、border、radius、type、elevation 与 motion 合同。

## 生成与检查

从 `apps/web` 运行：

```bash
node design-system/tools/build.mjs
node design-system/tools/build.mjs --check
node design-system/tools/check.mjs
node design-system/tools/check.mjs --self-test
```

主题文件必须是本包内普通文件；绝对路径、`..`、符号链接和真实路径越界都会失败。

**本目录是设计值的唯一手写来源。** `docs/design-system/` 不再持有 token、主题、合同、adapter 或
校验器，因此没有"两侧检查"，也不需要跨树同步。改动任何设计值后运行 `build.mjs` 重新生成
`tokens.css` 与 `design-tokens.json`；忘记重新生成会被 `build.mjs --check` 拦下。
设计原则、组件合同与来源追溯仍在 `docs/design-system.md` 与 `docs/design-system/`，
改动语义时一并更新那里的说明——但不要把值抄过去。

## 来源与许可

冻结来源的根摘要是
`68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d`；完整文件清单与逐文件摘要只保存在
文档树的 `source-lock.json`，不会进入 Web 运行时。本系统仍保留 Claude Design 所依赖的 OpenDesign
`design-systems/linear-app` fixture 的来源和 Apache-2.0 许可链；它不是 Linear 官方设计系统。详情见
[`NOTICE.md`](./NOTICE.md)。
