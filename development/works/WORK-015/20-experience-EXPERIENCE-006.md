---
id: "EXPERIENCE-006"
type: "experience"
title: "建立 Cherry OJ Web 设计系统"
status: "approved"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["CAPABILITY-005"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# EXPERIENCE-006：建立 Cherry OJ Web 设计系统

## 体验类型

这是设计者、前端开发者、评审者和后续 Agent 共用的“组件贡献体验”。最终用户不会直接操作设计系统，
但会通过一致、可恢复、可访问的 Cherry OJ 界面感受到结果。

## 入口与主流程

1. 从 `docs/README.md` 进入 `docs/design-system.md`，先确认基础原则、适用范围和当前版本。
2. 先在默认 Linear 黑色与 pure-white 两个主题中检查目标组件，再在 manifest 中查找已有模式。
3. 从语义 token 选择角色，不从色板或某个主题页面复制具体色值。
4. 按组件状态矩阵完成默认、交互、失败、窄屏、键盘和长中文设计，并确认两个主题行为一致。
5. 在视觉参考页与 Storybook（实施阶段）切换主题对照；同时检查 OJ 状态是否有文字/图标编码。
6. 新主题先实现 theme contract、登记 manifest、运行生成/完整性/对比度检查，不能手改聚合入口或
   要求组件增加 theme-id 条件分支。
7. 只有真实业务差异无法组合时才提例外，并在当前 WORK/DESIGN 留下原因和回归检查。

## 异常状态

- 找不到对应组件：先组合现有 primitive；仍不足时新增组件契约，不在页面内临时发明。
- token 缺失：升级设计系统；不得在 feature 中写任意 hex/OKLCH 绕过。
- 规范与运行时不一致：标记为“待迁移”，以已批准规范指导新设计，但不声称现有代码已经合规。
- 品牌色与 danger 难区分：危险动作固定使用危险图标、明确动词和必要确认；错误/verdict 固定显示代码与
  名称，不能只靠红色。
- 主题 id 缺失或无法识别：回退到默认 Linear 黑色，不保留半套变量或混合两个主题。
- 新主题缺少合同 token：主题不得登记为可用；先补齐并验证，不让组件用 fallback 掩盖缺口。
- 组件出现主题专用分支：停止合入并上移到语义 token；仅主题 manifest 与主题 CSS selector 可以识别
  具体 id。

## 交互与文案

界面保持 Linear 式克制：每个区域最多一个主要动作；主操作使用 Cherry accent，普通操作使用 ghost/
subtle；层级优先靠亮度、对齐和细边框，而不是大面积彩色或重阴影。按钮使用明确动词；破坏性动作
不得只写“确定”。Submission 生命周期与 verdict 分开表达；`RAN`、`SE`、`OLE` 等不能被归并成通用
成功/失败。加载、空、失败、无权限和恢复路径均有就地文案，不用 toast 承担必须阅读的信息。

## 可访问性

所有交互可由键盘完成并具有 `:focus-visible`；焦点环按主题映射但不能只靠色相可见。默认黑色与
pure-white 的正文、弱文字、链接、按钮、控件边界和状态分别满足适用的 WCAG AA/非文本对比门槛，
禁用态仍可读但不伪装可操作；颜色必须配合文字、图标、形状或位置。组件在 320px 宽度、200% 缩放、
长中文、系统高对比和 reduced-motion 下保留核心信息与操作。图标使用 Lucide 且有可访问名称或被
正确标为装饰。

## 调试与恢复

视觉问题先按 theme id → semantic token → component variant → page composition 四层定位。派生文件
必须能追溯到 foundation 与对应主题文件，完整性检查会指出缺失 token，差异检查会指出默认黑色非品牌
token 的意外变化。规范发布后若某主题出现严重可读性或语义问题，先从 manifest 撤下该非默认主题并
回退设计系统文档版本；默认黑色仍作为安全回退。已有页面代码的回退由后续迁移 TASK 自己定义。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：组件贡献流程、状态与可访问性要求已形成草案，提交人工审核
- 2026-08-27：状态变更：review → approved。原因：用户已审核并批准双主题组件贡献与可访问性体验
