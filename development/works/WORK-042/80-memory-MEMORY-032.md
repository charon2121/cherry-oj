---
id: "MEMORY-032"
type: "memory"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "checked"
work: "WORK-042"
owners: ["codex/root"]
depends_on: ["VERIFY-043"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# MEMORY-032：修复后台用户列表拒绝尚未过期的身份令牌

## 背景

WORK-039 修复共享 verifier，但 user-service 另有自己的 decoder。

## 决定与原因

用户确认采用最小修复，独立 decoder 的 iat 现为必填；exp/nbf 继续决定有效期。

## 尝试与教训

检查 JWT 时间问题必须覆盖签发者自身的资源接口；即时签发的成功用例无法发现短窗口误用。

## 已知问题

代码已修复，用户已确认实际测试和人工核实无问题。智能体未自行重放原 requestId；现场结论来源为用户确认。

## 重新考虑条件

实施或运行证据反驳当前定位时更新结论；新增 decoder 时检查所有时间正反例覆盖。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：记录独立 decoder 遗漏和默认 converter 补 iat 的回归教训
- 2026-09-07：结构与内容校验通过，由工具置为 checked。
