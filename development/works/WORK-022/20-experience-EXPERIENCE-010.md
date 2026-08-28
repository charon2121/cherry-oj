---
id: "EXPERIENCE-010"
type: "experience"
title: "微调双端应用布局页脚"
status: "approved"
work: "WORK-022"
owners: ["codex/root"]
depends_on: ["FEATURE-004"]
related: ["EXPERIENCE-009"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# EXPERIENCE-010：微调双端应用布局页脚

## 体验类型

用户体验与响应式页面壳层微调。

## 入口与主流程

### 用户端

1. Header、Main、Footer 的结构与内容顺序保持不变。
2. Main 继续占据剩余高度，短页面时 Footer 自然落在视口底部。
3. Footer 不再使用与 Main 不同的背景、顶部分隔线或阴影；用户感知到的是同一连续页面，而不是底部面板。

### 管理端

1. Header 保持全宽；中间区域继续使用 Sidebar + Content。
2. Footer 完全移除，中间区域占据 Header 以下的剩余高度。
3. 桌面侧栏与移动 Sheet 的入口、当前项、子菜单和关闭行为不变。

## 异常状态

本调整不新增加载、空、错误或提交状态。Session 加载失败、未登录、无权限、Dashboard 空内容和用户管理
错误继续使用 WORK-020 的既有状态；Footer 变化不得依赖任何请求结果。

## 交互与文案

- 用户端 Footer 保留 `Cherry OJ` 与 `Focused Workspace`，但只通过正常排版和留白收尾。
- 管理端不使用替代 Footer 文案，也不把原 Footer 内容移动到 Sidebar 或 Main。
- 不新增关闭提示、toast 或设置入口。

## 可访问性

- 用户端 Footer 继续使用语义 `<footer>`，保留 `contentinfo` landmark。
- 管理端移除 Footer 后不伪造空 `contentinfo`；页面仍有唯一 Header、Main 和具名导航。
- 320px 下用户 Footer 内容可以换行，管理 Main 不被移动 Sheet 遮挡；关闭 Sheet 后焦点仍回到触发按钮。
- forced-colors 中不依赖边框区分 Footer，因为其信息层级由文档结构和正常间距表达。

## 调试与恢复

通过浏览器 landmark、计算样式、网格行数与 320px 横向溢出检查定位问题。若出现回归，可独立恢复两个
App Shell 的 Footer 标记和网格行定义，不涉及 API、数据、路由或设计系统回退。

## 验收矩阵

- 用户端：短页/长页、桌面/320px、`cherry-black`/`pure-white`，检查 Footer 位置与连续表面。
- 管理端：Dashboard/用户账号、桌面/320px，检查无 Footer、无底部占位和导航行为不变。
- 回归：键盘、Escape、焦点恢复、forced-colors、reduced-motion 与现有身份链路。

## 变更记录

- 2026-08-28：完成双端 Footer 微调体验定义，提交人工审核。
- 2026-08-28：状态变更：draft → review。原因：双端页脚结构、视觉与无障碍体验已完整，提交用户审核
- 2026-08-28：状态变更：review → approved。原因：用户明确确认双端页脚体验并允许执行
