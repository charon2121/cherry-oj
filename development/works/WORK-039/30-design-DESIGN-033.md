---
id: "DESIGN-033"
type: "design"
title: "修复 JWT 签发 30 秒后被资源服务误拒绝"
status: "checked"
work: "WORK-039"
owners: ["codex/root"]
depends_on: ["ISSUE-011"]
related: ["WORK-037", "DESIGN-031"]
implements: []
verifies: []
tags: []
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# DESIGN-033：修复 JWT 签发 30 秒后被资源服务误拒绝

## 背景

[ISSUE-011](./10-issue-ISSUE-011.md) 已用同一 requestId 的 Gateway、user-service 和 problem-service 日志，
以及 Redis 中 JWT 的非秘密 claims 确认：login grant 验证成功，JWT 的 `iat=00:09:18`、`exp=02:09:18`，
但 problem-service 从 00:09:48 起拒绝。对当前依赖字节码的核对显示，`JwtIssuedAtValidator` 会同时拒绝
早于 `now-clockSkew` 和晚于 `now+clockSkew` 的 `iat`。

## 目标与限制

- 恢复 DESIGN-031 已确定的 2 小时 JWT 语义，不调整 2 小时、5 分钟或 30 天配置。
- 保留 `iat` 必填以及既有签名、算法、kid、issuer、audience、`exp`/`nbf` 和业务 claims 校验。
- 修复放在所有资源服务共用的 `identity-security-support`，不在三个服务逐个增加例外。
- 不改 Gateway Session、user-service 签发逻辑、公开契约、数据库、ZIP 或部署业务。

## 整体方案

从 `IdentityVerifierConfiguration` 的 validator chain 删除 `JwtIssuedAtValidator`，并把“`iat` 必须存在”
并入 `requiredClaims()`。`JwtTimestampValidator` 继续按 clock skew 校验 `exp` 与 `nbf`；因此令牌的结束时间
仍由签发方给出的 `exp` 决定，而不是由验证时刻与 `iat` 的距离隐式决定。

在共享模块增加直接 verifier 测试：构造一个 `iat` 已过去数分钟、`exp` 尚在未来的 RS256 JWT 并断言
通过；分别构造缺少 `iat`、已过期以及未来 `nbf` 的 JWT 并断言失败。资源服务通过同一配置类获得 decoder，
现有各服务安全集成测试继续作为装配回归。

## 模块与数据

- `identity-security-support/IdentityVerifierConfiguration`：修正唯一共享 validator chain。
- `identity-security-support` 测试：直接固定长期 JWT 的时间语义，避免依赖真实等待 30 秒。
- problem/submission/judging：不改业务实现，只运行既有安全测试确认仍通过共享模块装配。
- 无数据格式、数据库或跨服务 DTO 变化。

## 接口与状态

外部和内部 HTTP 路径、响应结构与错误 code 均不变。修复后，在 `exp` 前的合法请求不再产生
`INVALID_ACCESS_TOKEN`，因此 Gateway 也不会把它误映射为公开 503。真正无效的访问令牌仍返回原有 401，
JWKS 不可用仍按原有 503 处理。

## 安全与失败

这不是取消 JWT 过期，而是移除错误的第二个、仅 30 秒的隐形寿命。2 小时 `exp` 仍是硬截止；签名与
身份 claims 全部保持验证，`iat` 仍必须存在。改动可通过恢复原 validator 快速回退，但回退会立即恢复
“签发 30 秒后全部拒绝”的故障，因此仅用于定位而非正常运行。

## 监控与部署

三个资源服务重启后加载同一共享模块即可。建议先构建，再依次重启 problem、submission、judging；
Gateway 和 user-service 无需为该代码改动重启。现有 readiness 与 `identity_authentication_failed` 日志足以
验证：历史 `iat` 的未过期 JWT 应成功，真正过期/错误令牌仍被拒绝。

## 迁移与兼容

没有数据库迁移或 Session 迁移。修复与现有 2 小时 JWT、5 分钟提前换新和 30 天 fixed-absolute 会话完全
兼容；当前已登录会话里尚未过期的 JWT 在资源服务重启后可以直接恢复使用。

## 备选方案

1. **把 clock skew 改成 2 小时**：会掩盖误用，但也把未来时间容忍扩大到 2 小时，并将 JWT 寿命和时钟
   偏差错误地绑在一起；不选。
2. **Gateway 遇到资源服务 401 就强制换新并重试**：新令牌仍会在 30 秒后被同一 validator 拒绝，只是
   反复绕过症状；不选。
3. **自定义只拒绝未来 `iat` 的 validator**：可以更严格，但本系统签发方受信且 `exp` 已约束寿命，当前
   没有额外业务收益；保持简单，只要求 `iat` 存在。

## 风险与重审条件

主要风险是误删 `iat` 必填或误伤 `exp` 校验，因此测试必须分别证明“旧但未过期”通过、“缺 `iat`/已过期”
失败。若未来接入第三方 issuer、需要限制令牌最大年龄或防止异常未来 `iat`，应以明确的独立配置和验证器
实现，不能再次借用 clock skew 充当寿命。

## 变更记录

- 2026-09-06：状态变更：draft → review。原因：共享 verifier 最小修复、回归测试、部署与回退方案已明确，提交意图审核
- 2026-09-06：结构与内容校验通过，由工具置为 checked。
