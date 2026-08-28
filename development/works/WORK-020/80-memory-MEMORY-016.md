---
id: "MEMORY-016"
type: "memory"
title: "搭建用户端与管理端应用布局"
status: "draft"
work: "WORK-020"
owners: ["codex/root"]
depends_on: ["VERIFY-020"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# MEMORY-016：搭建用户端与管理端应用布局

## 背景

Web 在业务页面扩展前需要先区分普通用户空间与管理空间。WORK-020 记录本次壳层选择；该记忆只有在
TASK-028 完成并通过 VERIFY-020 后才整理为已验证事实。

## 决定与原因

已实施决定：用户端使用 topbar 的上中下结构；管理端使用独立 Header、Sidebar+Content、Footer；路由树
而非 pathname 条件分支负责壳层归属。`/admin` 和 `/admin/dashborad` 复用一个最小 Dashboard 组件，
管理权限集中在 `admin` 父路由。原因是边界清楚、可扩展且能集中权限与无障碍行为。

## 尝试与教训

shadcn CLI 的完整 Sidebar 会覆盖既有 Button，并带入与 Cherry 设计系统冲突的主题与持久化假设。因此
保留其 Sidebar/Collapsible/Sheet 组合方式和 Base UI 原语，建立最小适配组件，只消费既有 semantic token。
管理 Header/Footer 放在两列中区外层，避免侧栏占满视口后破坏“上—中（左右）—下”结构。

## 已知问题

WORK-019 的用户端侧栏 Figma 与本次顶部导航方向存在历史差异；后续 Web 以经确认并已实现的 WORK-020
为准，但旧 Figma 不在本任务中修改。`dashborad` 是经用户明确指定的兼容地址，不应在无迁移方案时静默
更名。

## 重新考虑条件

出现三级以上管理导航、多管理员角色、可调整或持久化侧栏、设计系统 sidebar 合同变化、用户端改回
工作台侧栏，或需要新增主题语义时，重新评估路由与组件方案。
