---
id: "ISSUE-013"
type: "issue"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "approved"
work: "WORK-042"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# ISSUE-013：修复后台用户列表拒绝尚未过期的身份令牌

## 为什么做

管理员已经登录，打开后台用户列表却无法加载账号，影响日常账号管理。有效登录应能持续使用，不能在登录后很短时间内被误拒绝。

## 问题现象

用户提供请求 req_434ac897d512461abd8bce147a92699d，访问后台用户列表返回 503 SERVICE_UNAVAILABLE，详情为“身份信任状态暂时不一致，请稍后重试。”。

## 复现方式

实施回合使用合法管理员令牌，将签发时间设为当前时间前 5 分钟、过期时间仍在未来，通过真实 user-service decoder 验证；另在本地登录后超过 30 秒访问 /api/admin/users。第一回合仅做静态排查，尚未重放原请求。

## 实际结果

静态证据：user-service 的 TokenConfig 仍装配 JwtIssuedAtValidator(true)，clock skew 为 30 秒；本地 Spring Security 7.1.0 字节码确认它拒绝早于 now-skew 的 iat。AdminUserController 把 user-service 401 映射为上述 503。未获取原请求运行日志，因此不能断言该 requestId 的唯一触发原因。

## 预期结果

遵循 [WORK-039 的时间语义](../WORK-039/10-issue-ISSUE-011.md)：合法令牌在 exp 前应被接受，iat 必填但不作为 30 秒最大年龄限制。

## 影响与条件

影响 user-service 自身受 JWT 保护的接口，包括后台用户列表。此前 WORK-039 只修复共享 verifier，未覆盖 user-service 的独立 decoder。

## 原因

把签发时间窗口校验误用为必填校验；现有 TokenConfigTests 的旧公钥测试使用即时签发令牌，不能发现令牌变旧后被拒绝。

## 修复方向

移除 user-service 的 JwtIssuedAtValidator，在 requiredClaims 中显式要求 iat 存在；保留 exp/nbf、签名及其他身份校验。

## 回归检查

- AC-001：user-service 真实 decoder 接受签发超过 30 秒但未过期的合法 JWT；包括仍发布的旧公钥签名。
- AC-002：缺少 iat、缺少 exp、过期、未来 nbf、错误签名或 issuer/audience 的令牌仍拒绝。
- AC-003：不改 2 小时令牌、提前 5 分钟换新、30 天固定会话、权限与错误映射；不改数据及公开契约。
- AC-004：user-service 定向测试与相关安全回归通过；验证后台列表登录超过 30 秒仍可访问，未登录与非管理员访问仍受限；无法执行的环境验证明确记录。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：只读排查和最小修复方案已整理，提交人工意图审核，尚未实施
- 2026-09-07：意图闸通过：review → approved。原因：确认修复方案，允许执行
