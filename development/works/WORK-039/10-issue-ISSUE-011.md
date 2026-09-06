---
id: "ISSUE-011"
type: "issue"
title: "修复 JWT 签发 30 秒后被资源服务误拒绝"
status: "approved"
work: "WORK-039"
owners: ["codex/root"]
depends_on: []
related: ["WORK-037", "ISSUE-009", "DESIGN-031"]
implements: []
verifies: []
tags: []
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# ISSUE-011：修复 JWT 签发 30 秒后被资源服务误拒绝

<!--
本节面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。报错原文、依赖坐标、字段、类、框架、协议、表名、路径和
命令从下一节开始再出现。
-->

## 为什么做

管理员刚登录后可以正常操作，但大约半分钟后，后续管理请求就会被系统误认为身份无效。页面仍显示
已经登录，账号也没有失效，却只能得到“身份信任状态暂时不一致”的错误。这让上传完成后的部署无法
继续，也会让所有管理功能在短时间后随机失效。

## 问题现象

2026-09-06 00:09:18 登录成功，00:09:37 上传并绑定测试数据成功；从 00:09:48 起，两次部署请求分别
返回 503 `SERVICE_UNAVAILABLE`，详情为“身份信任状态暂时不一致，请稍后重试。”。

## 复现方式

1. 启动 user-service、Gateway 和任一使用共享 JWT verifier 的资源服务。
2. 以管理员登录并持续调用受保护的管理接口。
3. 在 JWT 签发后的前 30 秒观察请求成功。
4. 签发超过 30 秒、但远未达到 2 小时 `exp` 时再次请求。

## 实际结果

Gateway 每次请求都向 user-service 验证 login grant 且得到 200，但 problem-service 从签发后约 30 秒开始
返回 401 `INVALID_ACCESS_TOKEN`。Gateway 将该资源服务 401 转成公开 503。judging-service 没有收到业务
部署请求，数据库也没有生成部署记录。

## 预期结果

遵循 [ISSUE-009](../WORK-037/10-issue-ISSUE-009.md) 已确认的时间模型：JWT 在 2 小时有效期内都应被资源
服务接受，Gateway 只在剩余 5 分钟时换新；`iat` 只用于记录签发时刻，不应把有效期缩短为 30 秒。

## 影响与条件

影响所有使用 `identity-security-support` 的资源服务：problem-service、submission-service 和
judging-service。只要令牌签发超过配置的 30 秒 clock skew 就会发生，与 ZIP 内容、账号状态、login
grant 和 30 天 Session 截止时间无关。公开 API 结构、数据库和既定 2 小时/5 分钟/30 天数值不变。

## 原因

共享 verifier 把 Spring Security 7.1 的 `JwtIssuedAtValidator(true)` 当成“要求 `iat` 存在”。实际实现
会要求 `iat` 落在当前时间前后一个 clock-skew 窗口内；本项目配置为 30 秒，因此合法的 2 小时 JWT 在
签发约 30 秒后就被误拒绝。Gateway 根据 `exp` 正确判断尚不需要续签，所以重新验证 login grant 也无法
修复这张被资源服务错误判定的令牌。

## 修复方向

移除不适用于长寿命访问令牌的 `JwtIssuedAtValidator`。在既有 required-claims 校验中明确要求 `iat`
存在；有效期限仍由 `JwtTimestampValidator` 的 `exp`/`nbf` 校验负责，签名、算法、kid、issuer、audience、
角色、会话版本和密码状态校验全部保持不变。用无需真实等待的历史 `iat` + 未来 `exp` 测试覆盖
“签发超过 30 秒但未过期仍有效”，并继续覆盖缺少 `iat` 和已过期令牌被拒绝。

## 回归检查

- AC-001：由受信私钥签发、`iat` 已早于当前时间超过 30 秒、`exp` 仍在未来的合法 JWT，三个资源服务
  使用的共享 verifier 均接受它。
- AC-002：缺少 `iat`、已经超过 `exp`、未来 `nbf` 或签名/算法/kid/issuer/audience/角色等不合法 JWT
  继续被拒绝；现有 401/503 分类不因本修复改变。
- AC-003：JWT 仍为 2 小时，Gateway 仍提前 5 分钟换新，Session/login grant 仍为固定 30 天且无 idle
  timeout；本次不修改任何时间配置或持久数据。
- AC-004：共享模块定向测试和后端聚合回归通过；公开请求/响应契约、数据库和测试数据部署协议不变。

## 变更记录

- 2026-09-06：状态变更：draft → review。原因：已用请求链路日志、JWT 时间 claims 和当前依赖字节码确认 30 秒误拒绝根因，修复边界与验收标准完整
- 2026-09-06：意图闸通过：review → approved。原因：确认修复 JWT iat 30 秒误拒绝，不调整既定时间配置
