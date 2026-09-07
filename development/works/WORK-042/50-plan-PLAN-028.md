---
id: "PLAN-028"
type: "plan"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "checked"
work: "WORK-042"
owners: ["codex/root"]
depends_on: ["ISSUE-013", "DESIGN-036", "DECISION-023"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# PLAN-028：修复后台用户列表拒绝尚未过期的身份令牌

## 目标

完成 ISSUE-013#AC-001 至 AC-004。

## 改动区域

TokenConfig.java、TokenConfigTests.java 及本工作证据文档。

## 阶段与顺序

人工审核文档并签署意图闸、明确允许执行后，读取 TASK-081 上下文和 Java 规范；先添加时间回归用例并观察失败，再最小修复；运行回归，独立复核代码，记录验证与回退检查。

## 并行与依赖

单任务顺序执行，复核安排在实现后，不与修改并发。

## 迁移与交付

交付可审查 diff 和测试证据，不自动提交或部署。无需数据迁移，运行环境需加载重新构建的 user-service。

## 风险

保留所有其他安全校验；如原请求在修复后仍失败，获取脱敏链路日志重新定位，扩大范围前更新方案。

## 验证

按 apps/server/TOOLCHAIN.md 选择工具链；计划运行 user-service 的 TokenConfigTests、TokenServiceTests 及 user-service/gateway-service 相关回归。实际命令、环境和结果写 VERIFY-043；第一回合不声称测试通过。

## 回退

只撤回本任务两份 Java 文件的差异并重新构建 user-service；不回滚其他工作。回退会恢复原有误拒绝，仅用于故障隔离。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：只读排查和最小修复方案已整理，提交人工意图审核，尚未实施
- 2026-09-07：结构与内容校验通过，由工具置为 checked。
