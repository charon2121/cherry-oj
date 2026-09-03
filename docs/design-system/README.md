<!--
Modified for Cherry OJ on 2026-09-03.
This package freezes the accepted Claude Design source and documents its
production dual-theme interpretation for Cherry OJ.
-->

# Cherry OJ 设计系统说明包

本目录保存 WORK-034 新设计系统的人类可读依据：下载版 Claude Design 完整快照、来源锁、双主题参考
token、组件合同和检查页。它用于审阅和追溯，不是 Web 的安装或运行依赖；真正被前端构建消费的代码真源
位于 `apps/web/design-system/`。

## 权威顺序

1. [`../design-system.md`](../design-system.md) 定义生产设计原则、组件/页面规则和例外流程；
2. [`source/claude-design-v1/`](./source/claude-design-v1/) 是用户认可下载版的原样视觉证据，
   [`source-lock.json`](./source-lock.json) 锁定其 99 文件、239831 bytes 和逐文件 SHA-256；
3. 本目录的 Foundation、主题、合同、manifest 和 preview 是对来源的中文生产解释；
4. `apps/web/design-system/` 是 Web 可执行真源；真实 React 组件和 Storybook 是最终消费结果；
5. 来源里的 JSX/HTML/bundle 是原型证据，不可覆盖 TypeScript、语义 HTML、Base UI、可访问性和业务合同。

## 暗色与浅色

来源只直接定义暗色。生产系统保留两个正式主题：

- `cherry-black` 精确对齐来源的 surface、文字层级、Cherry 色族、光学间距、排版、圆角和密度；
- `pure-white` 不是机械反相，也不是旧系统残留。它复用同一语义结构与全部非颜色 token，只为浅色画布
  重新标定 surface、foreground、border、status 和 elevation；
- 两主题共享组件 DOM、variant、spacing、typography、radius、layout 和 motion；任何页面主题分支都属于
  合同违规。

## 文件角色

| 文件 | 角色 | 是否手改 |
|---|---|---|
| `source/claude-design-v1/**` | 冻结来源证据 | 否 |
| `source-lock.json` | 来源路径、大小与摘要锁 | 由工具生成 |
| `tokens.foundation.css` | 共享字体、字号、间距、圆角、布局与动效 | 是 |
| `theme-contract.json` | 主题 semantic key、允许组合与对比门槛 | 是，不保存主题值 |
| `themes.manifest.json` | 双主题登记、默认值、来源与版本 | 是 |
| `themes/*.css` | 两主题完整 semantic 映射 | 是 |
| `tokens.css` / `design-tokens.json` | 稳定 CSS 与机器参考快照 | 由工具生成 |
| `tailwind-v4.css` | Tailwind/shadcn 语义映射参考 | 是，不保存主题值 |
| `components.manifest.json` | 组件 anatomy、状态、键盘与 token 合同 | 是 |
| `preview/*.html` | 主题、颜色、字体和间距的参考检查页 | 是 |
| `manifest.json` | 说明包入口、来源和完整文件登记 | 是 |

## 生产化原则

- 视觉值与构图应匹配来源；实现继续使用 React 19、TypeScript、Base UI、Lucide、Tailwind 和现有业务层；
- 不复制远程字体、CDN、inline style、随机 id、鼠标模拟状态、内联 SVG path 或 demo 导航；
- Inter Variable 与 JetBrains Mono 由 Web npm 依赖本地打包；Berkeley Mono 只作为用户设备上的可选首选；
- 原型里白字对亮 Cherry hover、opacity disabled 等不满足生产对比的配方，以同色族可访问映射替代；
- 三档透明 surface、ghost solid border、tertiary line 与 subtle/inset/dialog elevation 已进入双主题合同，
  组件不得重新写来源 rgba 或 shadow；
- 14 个核心配方由真实 TypeScript 组件和 `Foundation/Source recipes` Storybook specimen 承担，浅色主题
  使用相同 anatomy、密度和状态层级；
- 长 Markdown/代码继续使用 CodeMirror，不把下载包的 `Textarea` 样例误当成长内容编辑器；
- 状态、危险动作和 verdict 必须有文字/图标/形状，不可只靠颜色。

## 维护与检查

真正修改设计系统时，在同一 WORK/TASK 内同步代码树和文档树；普通 CI 不做跨树 copy、drift 或 symlink。
从仓库根目录运行：

```bash
node docs/design-system/tools/source-lock.mjs --check
node docs/design-system/tools/build.mjs
node docs/design-system/tools/build.mjs --check
node docs/design-system/tools/check.mjs
node docs/design-system/tools/check.mjs --self-test
```

组件视觉只在 Storybook 和产品页面验收；`preview/` 只帮助核对 Foundation/主题，不手抄 React 组件。

## 来源与许可

Claude Design 下载版根摘要为
`68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d`。它声明自己的基础来自 OpenDesign
`linear-app` fixture；该 fixture 以 Apache-2.0 提供，并非 Linear 官方源码。完整来源链、固定摘要和生产
适配见 [`NOTICE.md`](./NOTICE.md)，许可原文见 [`LICENSE.open-design`](./LICENSE.open-design)。Lucide
参考图标许可见 [`LICENSE.lucide`](./LICENSE.lucide)。
