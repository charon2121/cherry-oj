<!--
Modified for Cherry OJ on 2026-09-03.
This package freezes the accepted Claude Design source and documents its
production dual-theme interpretation for Cherry OJ.
-->

# Cherry OJ 设计系统说明包

本目录保存设计系统的人类可读依据：下载版 Claude Design 完整快照、来源锁和组件合同。它只用于审阅和
追溯，**不持有任何设计值**——token、主题、合同、adapter 和校验器的唯一真源是
`apps/web/design-system/`。

WORK-035 之前，本目录还存放着与代码树字节相同的 token、主题、合同、adapter 和一份 1336 行的校验器。
那份副本没有消费者，也没有任何机制保证它与真源一致，只是让每次改动都要在两处各做一遍。现在它们
已被删除：需要查某个值，去代码树；需要查这个值凭什么是这样，看 `source/`。

## 权威顺序

1. [`source/claude-design-v1/`](./source/claude-design-v1/) 是用户认可下载版的原样视觉证据，
   [`source-lock.json`](./source-lock.json) 锁定其 99 文件、239831 bytes 和逐文件 SHA-256；
2. [`../design-system.md`](../design-system.md) 定义生产设计原则、组件/页面规则和例外流程；
3. `apps/web/design-system/` 是 Web 可执行真源，持有全部 token、主题、合同、adapter 与校验器；
   真实 React 组件和 Storybook 是最终消费结果。

来源里的 JSX/HTML/bundle 是原型证据，不可覆盖 TypeScript、语义 HTML、Base UI、可访问性和业务合同。

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
| `components.manifest.json` | 组件 anatomy、状态、键盘与 token 合同 | 是 |
| `manifest.json` | 说明包入口、来源和完整文件登记 | 是 |
| `NOTICE.md` / `LICENSE.*` | 来源链、生产适配与许可原文 | 是 |

token、主题、合同、Tailwind adapter、生成快照与校验器**不在本目录**，见
`apps/web/design-system/`。Foundation 与主题的视觉参考用来源自带的
[`source/claude-design-v1/guidelines/`](./source/claude-design-v1/guidelines/)（20 张 specimen 卡片），
本目录不再自制 preview 页。

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

本目录只有一条检查命令，从仓库根目录运行：

```bash
node docs/design-system/tools/source-lock.mjs --check
```

它确认冻结来源的 99 个文件未被改动。设计值的生成与校验全部在代码树，见
`apps/web/design-system/README.md`。

组件视觉只在 Storybook 和产品页面验收；Foundation 与主题的核对用 `source/` 的 guidelines 卡片。

## 来源与许可

Claude Design 下载版根摘要为
`68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d`。它声明自己的基础来自 OpenDesign
`linear-app` fixture；该 fixture 以 Apache-2.0 提供，并非 Linear 官方源码。完整来源链、固定摘要和生产
适配见 [`NOTICE.md`](./NOTICE.md)，许可原文见 [`LICENSE.open-design`](./LICENSE.open-design)。Lucide
参考图标许可见 [`LICENSE.lucide`](./LICENSE.lucide)。
