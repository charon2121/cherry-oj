---
id: "DECISION-010"
type: "decision"
title: "统一登录空闲过期配置并修复提前掉线"
status: "approved"
work: "WORK-014"
owners: ["codex/root"]
depends_on: ["DESIGN-011"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# DECISION-010：统一登录空闲过期配置并修复提前掉线

## 要决定什么

IDLE、绝对上限和 IDLE 刷新策略分别由什么配置控制，以及怎样保证 Gateway Redis Session 与
user-service 登录授权采用一致行为。

## 背景

当前两个安全层都默认 30 分钟，但 Gateway 在注解中写死 1800 秒，user-service 独立配置；12 小时绝对
期限与 120 秒 JWT 又属于不同语义。用户需要调整的是空闲期限，不是内部 token TTL。

## 候选方案

- A：只开放 Spring `spring.session.timeout`。简单，但数据库授权、绝对上限和刷新开关不随之变化。
- B：三个显式环境变量同时注入 Gateway 和 user-service，各自在启动时类型校验并用自动化测试对齐。
- C：user-service 通过每次响应下发期限，Gateway 动态推导全部 Redis TTL。真源集中，但匿名 Session、
  网络时延和旧 Session 迁移更复杂。

## 决定

推荐 B，并按负责人反馈使用：

- `CHERRY_AUTH_SESSION_IDLE_TIMEOUT_SECONDS=1800`；
- `CHERRY_AUTH_SESSION_ABSOLUTE_TIMEOUT_SECONDS=43200`；
- `CHERRY_AUTH_SESSION_REFRESH_IDLE_ON_ACTIVITY=true`。

单位严格为秒。开关开启时认证 API 操作滑动 IDLE，关闭时 IDLE 固定；两种情况都受绝对截止时间约束。
内部 JWT 仍为 120 秒且透明刷新，不增加 Web 心跳。完整方案待人工确认后实施。

## 理由

B 能覆盖负责人要求的三项设置，保持部署方式清晰，并能通过 Gateway Redis 集成测试与 user-service
固定时钟测试证明。A 无法消除提前到期或表达关闭滑动续期；C 的集中度更好，但需要新的期限下发协议。
实现允许增加仅供 Gateway 使用的授权 touch，以保证开关开启时每次认证活动确实刷新两层 IDLE。

## 影响与风险

部署必须同时向两个服务注入三项同值配置并一起重启。值越长或开启滑动刷新，离开设备后的风险窗口
越大；因此维持范围校验和绝对上限。默认行为不变，不迁移数据库，不改变公开 API。配置错误会从
运行时提前掉线变为启动失败，这是有意的 fail-fast。开启刷新会增加授权 touch 写入量。

## 重新考虑条件

出现集中配置中心、Gateway 与 user-service 独立运维导致值频繁漂移、remember-me/移动端/SSO、多级
空闲策略，或产品明确要求超过 2 小时后，重新评估方案 C、设备级授权与 Cookie 生命周期。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：配置名、默认值、合法范围和三种候选方案已列明，等待负责人确认推荐方案 B
- 2026-08-27：状态变更：review → approved。原因：负责人确认方案 B：1800/43200 秒默认值、true 刷新默认值及严格边界语义
