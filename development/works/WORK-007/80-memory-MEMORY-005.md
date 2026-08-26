---
id: "MEMORY-005"
type: "memory"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "approved"
work: "WORK-007"
owners: ["codex/root"]
depends_on: ["VERIFY-007"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# MEMORY-005：校正全局 PRD 与当前 MVP 基线的漂移

## 背景

旧 PRD 曾同时保存稳定愿景、当前 MVP、P0/P1/P2 backlog、实施进度和技术缺口。即使每条内容在写入
当时合理，只要它们的变化速度和确认权限不同，就会在架构、代码和工作项推进后互相漂移。

## 决定与原因

`docs/product.md` 只做稳定产品合同：定位、当前 MVP、核心概念、流程、不可变产品规则、质量基线、
当前非目标和已确认演进顺序。具体功能状态与未知由 development WORK 承担；长期设想只有进入实际
开发并被确认后才成为 PRD 规则。

选择保留单一 PRD 而不是拆成愿景/路线/MVP 多份文档，是为了保持唯一产品入口并减少同步成本。

## 尝试与教训

- 只改几句过期“当前方向”不能解决信息类型混杂，下一次推进仍会漂移。
- 按代码完成度改 PRD 会把实现偶然状态误当产品决定；PRD 写目标，WORK/VERIFY 写进度。
- 把未来功能列成 P1/P2 看似方便，实际会绕过需求澄清并形成未经确认的隐性承诺。
- 当前工作项中的 blocking 问题不能为了让全局文档显得完整而被智能体代签。

## 已知问题

WORK-002 仍有三个 blocking 产品问题。确定性题目工厂、多语言、环境迁移、Agent 与教学扩展都只有
方向边界，没有功能定义；进入开发时必须新建 product WORK。

## 重新考虑条件

当产品出现多个长期并行版本、不同部署形态具有冲突产品规则，或单一 PRD 无法表达稳定的版本差异时，
重新评估拆分版本化 PRD。仅仅 backlog 变长不是拆分理由，backlog 仍应留在 development。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：已沉淀 PRD 只做稳定产品合同、状态与未知进入 development 的长期防漂移规则
