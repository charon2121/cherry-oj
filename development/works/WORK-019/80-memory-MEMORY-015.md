---
id: "MEMORY-015"
type: "memory"
title: "设计 Cherry OJ 任务入口主页"
status: "draft"
work: "WORK-019"
owners: ["codex/root"]
depends_on: ["VERIFY-019"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# MEMORY-015：设计 Cherry OJ 任务入口主页

## 背景

当前主页是 Web/Gateway 连通占位，而 WORK-015 已建立 Focused Workspace 设计系统。WORK-019 把两者之间
缺失的产品入口目标整理为 Figma 稿，但不承担 Web 实现。

## 决定与原因

用户已批准并完成的核心方向是：主页优先帮助普通学习者进入题库和提交链路；匿名、首次改密和管理员
是同一结构的状态变体；主页不是营销 Landing、数据 Dashboard 或设计系统展示页。默认主题为 Cherry
Black，Pure White 用同一 semantic anatomy 校验。交付文件为 `Cherry OJ · 任务入口主页`，主 Frame 是
`20:3482`。

## 尝试与教训

只读核对发现 WORK-002、WORK-015、WORK-018 都不能授权本设计：前者仍有产品阻塞项，后两者分别只管
设计系统与行为不变迁移。现有仓库没有 Cherry OJ Code Connect 文件，也没有可直接假定存在的发布
Figma Library，因此本任务建立了主页所需局部子集，而没有把相似社区组件当成项目真源。

Starter plan 同一 variable collection 只能有一个 mode，用户批准用两个独立 single-mode semantic
collection 与组件 Theme variants 保持双主题 anatomy。远程 MCP 配额耗尽后，经用户另行授权，使用临时
本地 Development Plugin 在同一 Draft 完成 Foundations、Structure、Components、Screens、Verify；
Verify 通过后立即移除插件登记。Figma 对 variable-bound shadow 的 spread 读回会归零，因此三个 1px
ring effect 使用语义 token 解析后的颜色快照保留几何，并用机器与视觉双重检查补足。

## 已知问题

- “CherryOJ” Figma plan 只有 View 席位；默认只能写入 “Xian Xian's team” Drafts，除非用户提供可编辑
  目标。
- 当前 Web 尚无题库/提交路由；设计中的入口是目标态，未来实施需要独立工作项。
- Figma 文档与 `apps/web/design-system/` 没有自动同步，未来实现仍需以代码侧资产为真源。

## 重新考虑条件

正式品牌规范到来、主页改为营销入口、产品增加首页统计、应用壳决定继续使用顶部导航、C++ ACM 边界
改变、身份流程变化、Figma Library 建立或设计系统合同变更时，应重新审核 FEATURE-002 与 DESIGN-015。
