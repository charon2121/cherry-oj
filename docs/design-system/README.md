# Cherry OJ 设计系统说明目录

本目录存放面向开发者与智能体的设计系统协作文档，**不持有任何具体色值或构建输入**。Web 前端的可执行设计系统唯一真源位于 `apps/web/design-system/`。

## 文件职责

| 文件 | 角色 |
| --- | --- |
| [`../design-system.md`](../design-system.md) | Web 视觉架构、浅色优先双主题契约、排版与空间尺度、页面构图规范与可访问性门禁（唯一设计权威） |
| [`PROMPT.md`](./PROMPT.md) | 动手修改 Web UI 前的模板选型速查、编码红线与交付前八项自检清单 |
| [`components.manifest.json`](./components.manifest.json) | 核心 UI 原语与 OJ 业务组件的结构解剖、状态矩阵、键盘行为与语义 token 引用清单 |

## 可执行真源与门禁（`apps/web/design-system/`）

所有 Foundation 尺度、双主题（默认 `pure-white` 与暗色 `cherry-black`）CSS 定义、`theme-contract.json`、Tailwind v4 映射与校验脚本均集中在 `apps/web/design-system/`：

```bash
cd apps/web && npm run check:design-system
```

该命令会自动校验生成物零漂移、65 个语义键双主题完整性、296 组 WCAG AA 对比度阈值、源码零裸色值/零主题分支扫描以及负向自测试。组件交互样本通过 Storybook（`cd apps/web && npm run storybook`）在真实组件上预览与验收。
