---
id: "DECISION-023"
type: "decision"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "approved"
work: "WORK-042"
owners: ["codex/root"]
depends_on: ["DESIGN-036"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# DECISION-023：修复后台用户列表拒绝尚未过期的身份令牌

## 要决定什么

是否将 user-service 的时间校验与 WORK-039 已确认语义保持一致。

## 背景

user-service 是签发者，也是后台用户管理接口的资源服务，使用独立 decoder。

## 候选方案

A：最小修复独立 decoder；B：全面统一 verifier；C：网关不断换新。A 范围最小且能直接修复错误，B 扩大架构范围，C 无法消除根因。

## 决定

采用 A。用户已于 2026-09-07 签署意图闸并在后续消息明确允许执行。

## 理由

复用已确认的时间语义，保持本地公钥集合与轮换路径。

## 影响与风险

iat 由窗口校验改为必填；签名与有效期校验不变。必须验证恶意或缺字段令牌仍失败。

## 重新考虑条件

接入第三方签发者或新增最大令牌年龄要求时重新设计。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：只读排查和最小修复方案已整理，提交人工意图审核，尚未实施
- 2026-09-07：意图闸通过：review → approved。原因：确认修复方案，允许执行
