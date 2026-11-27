# Cherry OJ Web 设计系统包

本目录是 `apps/web` 的可执行设计系统真源（v3.0），定义浅色优先（`pure-white`）与深色对等（`cherry-black`）的双主题语义 Token、排版刻度、圆角阴影与 Tailwind v4 桥接，不依赖仓库 `docs/` 目录。

## 构建与校验

在 `apps/web` 下执行：

```bash
node design-system/tools/build.mjs
node design-system/tools/check.mjs
node design-system/tools/check.mjs --self-test
```

改动 `themes.manifest.json` 后，还需同步更新前端运行时注册表与首屏防闪脚本：

```bash
npm run generate:design-system
npm run check:design-system
```
