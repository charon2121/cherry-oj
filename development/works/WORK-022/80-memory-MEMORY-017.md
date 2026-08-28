---
id: "MEMORY-017"
type: "memory"
title: "微调双端应用布局页脚"
status: "draft"
work: "WORK-022"
owners: ["codex/root"]
depends_on: ["VERIFY-022"]
related: ["MEMORY-016", "WORK-020"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# MEMORY-017：微调双端应用布局页脚

## 背景

WORK-020 最初按用户端 Header/Main/Footer、管理端 Header/Main(Sidebar+Content)/Footer 实现。人工复核
后，产品方向收敛为管理端无 Footer，用户端 Footer 仅保留结构且不与 Main 形成视觉分区。

## 决定与原因

已实施决定：管理工具优先保留垂直工作空间，不需要统一页脚；用户页面保留语义页脚和短页收尾，但页面
层级靠内容、间距和排版表达，不为低优先级信息建立独立表面。

## 尝试与教训

WORK-020 证明独立管理 Footer 在技术上可行，但人工复核认为它没有产品价值。后续不能仅因为两端结构
“对称”就保留元素；页面骨架应服务各端任务，而不是追求标签数量一致。

## 已知问题

`/admin/dashborad` 的兼容拼写和 WORK-020 其它已知边界不属于本工作。当前实现已完成自动化验证，但在
VERIFY-022 获得独立人工确认前，不能把该形态记为最终发布事实。

## 重新考虑条件

管理端未来出现必须全局常驻的合规链接、环境状态或操作区，或用户 Footer 承担独立导航/法律信息时，
重新评估是否需要 Footer 表面或其它稳定区域；不能未经产品定义直接恢复旧样式。
