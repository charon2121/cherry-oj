---
id: "DESIGN-011"
type: "design"
title: "统一登录空闲过期配置并修复提前掉线"
status: "approved"
work: "WORK-014"
owners: ["codex/root"]
depends_on: ["ISSUE-002"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# DESIGN-011：统一登录空闲过期配置并修复提前掉线

## 背景

ISSUE-002 发现 WORK-013 的三层身份对象期限没有形成单一部署入口。Redis WebSession 的
`maxInactiveInterval` 在 Gateway 注解里固定为 1800 秒；MySQL 登录授权由 user-service 的
`sessionIdleTimeout` 计算；内部 JWT 固定 120 秒。前两层任一先到期都会要求重新登录，JWT 则应在有效
登录内透明交换。

## 目标与限制

- 提供两个整数秒数和一个布尔部署变量，默认值和合法范围可在启动时验证。
- 登录空闲的含义是“最后一次经过 Gateway 的认证 API 操作”，不为静态页面增加保活心跳。
- Redis Session 与数据库登录授权不能因配置来源不同而互相截短。
- 绝对上限也可配置；无论 IDLE 是否刷新都不能突破它。保留安全事件撤销和 120 秒 JWT，不修改公开
  OpenAPI 或数据库结构。
- 修复限定在 Gateway、user-service 及 WORK-014 文档，不扩展资源业务接口。

## 整体方案

新增三个显式环境变量，由两个进程的 application 配置明确引用：

- `CHERRY_AUTH_SESSION_IDLE_TIMEOUT_SECONDS`：整数秒，默认 1800；
- `CHERRY_AUTH_SESSION_ABSOLUTE_TIMEOUT_SECONDS`：整数秒，默认 43200；
- `CHERRY_AUTH_SESSION_REFRESH_IDLE_ON_ACTIVITY`：布尔值，默认 `true`。

两个服务先以整数/布尔类型绑定并校验，再在内部转换为 Duration。user-service 用它们创建登录授权、
计算固定绝对截止时间，并按开关决定认证活动是否更新 `idle_expires_at`；Gateway 通过 Spring Session 的
`ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository>` 和每个已认证 WebSession 的状态
管理 Redis 期限，替代注解中的 1800 秒硬编码。

登录时同时记录 `idleExpiresAt` 与 `absoluteExpiresAt`。开关为 `true` 时，每次成功解析当前身份的认证
API 操作都将两层 IDLE 更新为 `min(now + idleSeconds, absoluteExpiresAt)`；为 `false` 时保留登录时的
IDLE 截止时间，后续操作和 token exchange 都不得延长。内部 JWT 的 120 秒 TTL 不读取这些值，且
token 刷新不得绕过关闭的 IDLE 刷新开关。

## 模块与数据

- Gateway：绑定并校验两个秒数与一个布尔值；认证状态携带 IDLE/绝对截止时间。开关开启时认证 API
  成功后传播一次授权 touch 并更新 Redis Session；关闭时按固定剩余时间保存 Redis TTL，不能让 Spring
  Session 的自动 last-access 行为偷偷滑动期限。明确 401 时仍清理浏览器 Session。
- user-service：绑定同名配置；登录时生成两个截止时间，新增或复用内部授权 touch 能力，按开关条件
  更新 `idle_expires_at`。token exchange 必须遵循同一开关，数据库结构不变。
- Web：不新增心跳；现有 Session 查询和受保护请求自然计为认证活动。
- 运维：必须把同一个环境变量注入 Gateway 与 user-service。测试和启动日志不得输出 Cookie、grant
  或 token。

## 接口与状态

公开 `/api/auth/*` 的请求/响应不变。配置只接受十进制整数秒数和严格布尔值，不接受 `30m`、`2h` 等
Duration 文本。IDLE 推荐范围 `[300, 7200]` 秒，绝对上限范围 `[3600, 604800]` 秒，且 IDLE 不得大于
绝对上限；非法值启动失败。配置变更通过重启生效，新登录使用新期限。

## 安全与失败

延长两个期限或开启 IDLE 刷新会增加无人看管设备被继续使用的窗口，因此保留范围校验、绝对期限和
全端撤销。配置解析失败或越界时 fail fast，不能分别回落到不同默认值。授权 touch/交换返回 401 时
Gateway 清 Session；上游 503 保留 Session 但本次请求报告服务不可用，不能伪装为空闲到期。

## 监控与部署

启动时记录采用的两个秒数与刷新开关（不记录身份或凭据）。部署后检查 Redis Session TTL 与登录授权
边界，并区分 `UNAUTHENTICATED`、上游 503 和 token 刷新错误。环境变量变更要求同时重启 Gateway 与
user-service，分批发布时先保持旧默认值，避免短暂不一致。

## 迁移与兼容

默认 1800/43200 秒和刷新开启与现有意图兼容，不修改现有数据；数据库中的活跃授权保留既有绝对期限。
开启刷新时下一次成功认证活动按新 IDLE 续期，关闭时既有授权不得因 token exchange 延长。回退为移除
三个变量恢复默认。若从长值回退到短值，旧 Session 可能在一次访问后才采用新策略，因此发布说明需要
提示重新登录或主动清理测试 Session。

## 备选方案

- 只暴露 `spring.session.timeout`：改动最少，但只能控制 Redis，无法配置绝对上限和刷新策略，拒绝。
- 让 Gateway 根据 user-service 返回的到期时间动态推导且不配置自身：单一真源更强，但匿名 Session
  仍需独立默认，且网络时延/时钟差会让 Duration 计算产生漂移；当前先使用同一部署变量并做一致性测试。
- 增加前端心跳：可以让用户一直在线，但后台标签页也会无限续期，违背“无操作后退出”，拒绝。

## 风险与重审条件

刷新开启意味着认证 API 操作需要可靠地把活动传播到 user-service，会增加数据库写入和上游依赖；实现
可采用专用 touch 而不是每次重签 JWT，但不能牺牲语义正确性。两个独立进程无法在启动时直接证明配置
完全相同，部署模板形成后应放在共享 Config 源。若未来引入多设备策略、remember-me、移动端、SSO，
或需要更长期限，应重新审查范围、Cookie 持久化和触摸写入成本。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：统一环境变量、两服务绑定、Redis customizer、安全边界与回退方案已形成，提交审核
- 2026-08-27：状态变更：review → approved。原因：负责人确认双服务统一配置、条件 touch、Redis Session 行为与安全边界，并允许实施
