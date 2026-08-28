<!--
Modified for Cherry OJ on 2026-08-28.
This Web-owned package was separated from the executable documentation package
so frontend commands do not depend on a documentation directory.
-->

# Cherry OJ Web 设计系统代码包

本目录是 `apps/web` 的设计系统可执行真源。Web 的 CSS、主题生成器、源码门禁、Storybook 和 CI 只读取
本目录，不读取或执行仓库 `docs/` 下的文件。

## 文件角色

| 文件 | 角色 |
|---|---|
| `tokens.foundation.css` | 字体、字号、间距、圆角、布局与动效源 |
| `theme-contract.json` | 主题必须实现的 semantic key、允许组合与对比合同 |
| `themes.manifest.json` | 主题登记、默认/fallback、color scheme 与版本 |
| `themes/*.css` | 每个主题的完整 semantic token 源 |
| `tokens.css` | 由本地 builder 生成的稳定 CSS 入口，禁止手改 |
| `tailwind-v4.css` | 与主题无关的 Tailwind/shadcn adapter |
| `manifest.json` | 本代码包的文件完整性清单 |
| `tools/build.mjs` | 确定性生成 `tokens.css` |
| `tools/check.mjs` | 校验逐值合同、对比度、来源、许可、路径与生成物 |
| `NOTICE.md` / `LICENSE.open-design` | 来源、Cherry 修改说明与 Apache-2.0 许可 |

## 生成与检查

从 `apps/web` 运行：

```bash
node design-system/tools/build.mjs
node design-system/tools/build.mjs --check
node design-system/tools/check.mjs
node design-system/tools/check.mjs --self-test
```

本地 checker 保留已批准运行资产的精确值与合同断言。主题文件必须是本包内普通文件；绝对路径、`..`、
符号链接和真实路径越界都会失败。

## 变更边界

日常 Web 命令不比较本目录与设计文档，也不从文档目录自动复制。只有真正修改设计系统时，才在同一
WORK/TASK 中同步更新代码与设计说明，并分别运行两侧自己的检查。不要通过 prebuild、symlink、fetch
或 fallback 恢复对文档目录的依赖。

## 来源与许可

本包由 OpenDesign `design-systems/linear-app` bundled fixture 修改而来，不是 Linear 官方设计系统。
固定来源摘要与 Cherry OJ 修改见 [`NOTICE.md`](./NOTICE.md)，Apache-2.0 原文见
[`LICENSE.open-design`](./LICENSE.open-design)。
