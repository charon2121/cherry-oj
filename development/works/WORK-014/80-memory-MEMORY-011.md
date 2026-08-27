---
id: "MEMORY-011"
type: "memory"
title: "统一登录空闲过期配置并修复提前掉线"
status: "approved"
work: "WORK-014"
owners: ["codex/root"]
depends_on: ["VERIFY-014"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# MEMORY-011：统一登录空闲过期配置并修复提前掉线

## 背景

登录空闲、绝对期限和内部 JWT 是不同计时器，不能用 token TTL 表达用户登录时长。Gateway Redis
Session 与 user-service MySQL 登录授权都可能先使登录失效，因此两层必须采用同一期限和刷新语义。

## 决定与原因

长期配置入口为 `CHERRY_AUTH_SESSION_IDLE_TIMEOUT_SECONDS`、
`CHERRY_AUTH_SESSION_ABSOLUTE_TIMEOUT_SECONDS` 与
`CHERRY_AUTH_SESSION_REFRESH_IDLE_ON_ACTIVITY`。默认分别为 1800、43200、true；IDLE 合法范围
300～7200 秒，绝对期限 3600～604800 秒，且 IDLE 不得大于绝对期限，布尔值只接受小写
`true | false`。

刷新开启时，成功认证操作把 IDLE 更新为 `min(now + idleSeconds, absoluteExpiresAt)`；关闭时，登录时
生成的 IDLE 截止时间保持固定。两种模式都不突破绝对期限。Gateway 与 user-service 分别启动校验，
并在 authenticate/exchange/touch 响应中逐项核对三项配置；漂移时 fail closed 为 503，不把内部故障
伪装成用户凭据失效。

## 尝试与教训

`@EnableRedisWebSession(maxInactiveIntervalInSeconds = 1800)` 会遮蔽部署配置，不能继续作为业务期限真源。
Spring Session 默认值应由 `ReactiveSessionRepositoryCustomizer` 设置；已认证 Session 还要按数据库返回
的 IDLE/绝对截止时间设置“从现在起剩余多久”，否则 Spring 在每次请求更新 last-accessed 时可能意外
延长固定截止时间。

刷新开关关闭时，数据库仍可更新 `last_used_at` 和乐观锁版本，但不能更新 `idle_expires_at`。真实 MySQL
CASE 更新测试用于防止内存 mock 掩盖 SQL 差异。120 秒内部 JWT 在授权有效时由 Gateway 单飞交换；
token 到期不是退出登录事件。

## 已知问题

部署配置中心与线上期限观察尚未完成。两服务不能只滚动发布一侧或注入不同值；已有 Session/授权在
切换时的实际退出体验需要发布窗口观察。当前 Gateway 只对已接入的认证 BFF 操作执行 touch，未来新增
受保护资源路由时必须把成功认证活动接到相同 touch 语义，不能用前端心跳代替真实活动。

## 重新考虑条件

引入 remember-me、移动端、SSO、设备级策略或更长登录期限时重新考虑。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：三计时器边界、统一配置、Spring Session 剩余 TTL 与条件 touch 教训已沉淀
- 2026-08-27：状态变更：review → approved。原因：长期配置基线、部署一致性要求、已知问题与重新考虑条件完成复核
