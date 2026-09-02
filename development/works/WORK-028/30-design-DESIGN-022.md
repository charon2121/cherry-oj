---
id: "DESIGN-022"
type: "design"
title: "修复后台用户列表偶发误跳登录页"
status: "checked"
work: "WORK-028"
owners: ["codex/root"]
depends_on: ["ISSUE-006"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# DESIGN-022：修复后台用户列表偶发误跳登录页

## 背景

ISSUE-006 记录了“后台用户列表偶发跳登录、刷新又恢复”的矛盾状态。现有浏览器 Session、user-service
登录授权和 120 秒内部 JWT 是 WORK-013 明确分离的三个对象：只有前两者明确失效才代表用户需要重新
登录，内部 JWT 只是 Gateway 调用资源接口的短期凭据。

当前 `AdminUserController` 取得 Gateway Session 中的 JWT 后调用 user-service；下游 401 经
`mapUpstream(error, false)` 原样成为公开 401。`admin.users.tsx` 收到任何 401 都把 Query 中的 Session
写成匿名并导航登录页。与此同时 `/api/auth/session` 只要 Redis Session 与登录授权仍有效就会返回已
登录，因此完整刷新会“恢复”。本地默认 RSA 密钥为进程级临时密钥，user-service 重启是这一状态的
确定性触发方式之一。

## 目标与限制

- 恢复“公开 401 只表示浏览器登录明确失效”的既有安全语义。
- 资源接口拒绝内部 JWT 时，只能由 Gateway 使用登录授权恢复；JWT、授权和 Cookie 不进入 Web。
- 原业务请求最多自动重试一次；不得对 403、409、422、429 或任意 5xx 盲目重试。
- 登录授权撤销、空闲/绝对到期、改密、停用和角色不足的 fail-closed 行为不变。
- 不修改公开 OpenAPI、数据库结构、签名密钥配置或 120 秒 TTL。
- 修复限定于 Admin 用户 BFF 和 Gateway 会话刷新协调；其它资源 BFF 的相同模式列入影响复核，不在
  未验证前顺手扩大实现。

## 整体方案

在 Gateway 内引入“被资源接口拒绝后的强制令牌恢复”路径，区别于按 `expiresAt + skew` 进行的常规
预刷新：

1. `AdminUserController` 先按现有方式解析 Session、校验 ADMIN，并调用 user-service。
2. 只有首次调用返回明确的下游 HTTP 401 时，携带本次使用的 `AuthSessionState` 请求认证服务强制交换。
3. 认证服务用 Session 中仅服务器可见的 login grant 调用 `/internal/auth/token`。并发交换按 Session
   单飞；若另一请求已得到不同的新 token，复用新状态而不重复交换。
4. 交换成功后把新状态写回每一个参与请求持有的 `WebSession`，再用新 token 重试原业务调用一次。
5. 交换返回 401 表示登录授权真值已失效：清理 Session，向浏览器返回 401。交换超时/5xx 保留 Session
   并返回对应 5xx。若新 token 的一次重试仍为 401，将其归类为身份基础设施不一致并返回 503，保留
   Session 供稍后重试，不能再循环交换。

Web 不新增 token 处理，也不改变公开 401 的退出行为，因为修复后的公开边界已经先确认登录授权真值。

## 模块与数据

- `gateway-service/GatewayAuthenticationService`：提供强制交换入口，协调常规 touch 与强制 exchange 的
  单飞键，保证共享刷新结果回写到每个请求的 `WebSession`；仍是 login grant 和 JWT 的唯一浏览器侧持有者。
- `gateway-service/AdminUserController`：识别 `UserServiceClientException` 的首次 401，执行一次恢复和
  一次业务重试；其它状态沿用既有映射。
- `user-service`：实现不变；现有 token exchange 继续检查 grant、账号状态和 sessionVersion，并签发新 JWT。
- `apps/web`：实现原则上不变；增加或更新回归场景时只验证页面不因已恢复请求跳转。

不新增表、缓存键、公开字段或持久化迁移。Redis Session 中仍保存同一 `AuthSessionState` 结构。

## 接口与状态

公开 `/api/admin/users` 与 `/api/auth/session` 契约不变。内部状态转换为：

```text
资源调用成功 ───────────────────────────────► 返回原结果
资源调用首次 401 + grant 有效 ─► 强制 exchange ─► 新 token 重试一次
资源调用首次 401 + grant 失效 ─► 清 Session ─────► 公开 401
资源调用首次 401 + exchange 临时失败 ───────────► 公开 5xx，保留 Session
新 token 重试仍 401 ────────────────────────────► 公开 503，保留 Session
```

用于判定恢复的必须是客户端内部异常携带的 HTTP status，不能按错误文案、前端 URL 或通用 `Throwable`
猜测。第二次 401 不再映射为公开未认证。

## 安全与失败

强制交换使用已有 login grant、超时、配置一致性校验和撤销处理，不把长期凭据扩散到 controller 之外。
重试只允许 GET 列表及本 WORK 明确覆盖的 Admin 用户操作；写操作若纳入必须证明幂等边界，否则不自动
重放。本次首先覆盖列表读取，写接口遇到 401 仍按明确方案审查，不能默认重试。

交换 401 是登录授权真值失败，可清 Session；资源 401 只是 JWT 验证失败信号，不能直接清 Session。
所有日志只记录公开 requestId、阶段和分类，不记录 userId、用户名、Cookie、grant、JWT 或签名材料。

## 监控与部署

不增加外部监控系统。测试和现有结构化日志应能区分：首次资源 token rejected、恢复成功、grant rejected、
exchange temporary failure、fresh token rejected。部署不要求同步修改 user-service 或 Web；生产仍必须
使用显式、可轮换的 PEM，不能把本修复当成允许临时密钥上线的理由。

## 迁移与兼容

无数据迁移、无公开协议版本变化。旧 Redis Session 可在首次遇到下游 401 时就地换取新 token。回退为
撤销 Gateway 的一次恢复路径，恢复原有 401 映射；这会重新暴露误跳登录问题，但不破坏 Session 数据。

## 备选方案

- Web 收到 401 后再请求 `/api/auth/session`：能避免错误跳转，但不能让旧 JWT 完成用户列表请求，而且
  把服务端三种身份对象的协调泄漏给浏览器，拒绝作为主修复。
- 每个受保护请求都强制 exchange：行为简单，但每次都写登录授权并执行 RSA 签名，削弱短 JWT 的离线
  验证价值，拒绝。
- 只把本地临时 RSA 密钥持久化：能减少重启触发，但不能修正“下游 401 被误报成浏览器退出”的通用
  错误语义，且密钥文件生命周期需要另一项配置设计，拒绝。
- 资源 401 后强制 exchange 并单次重试：恢复发生在持有 grant 的 Gateway，且能用 exchange 结果区分
  真正撤销与短 token 失配，采用。

## 风险与重审条件

主要风险是自动重试写请求可能重复副作用，因此 TASK-046 默认只对 GET 列表启用；若实现抽象扩展到写
操作，必须先证明请求幂等或只在下游认证链尚未进入 controller 时重放。并发单飞若只缓存 Mono 而不把
结果写回每个请求自己的 WebSession，会被旧 Redis 状态覆盖，测试必须使用并发会话实例捕获。

若后续所有资源 BFF 都出现相同恢复需求，应建立统一的 Gateway authenticated-call 组件并分别验证幂等
策略；若 JWT 改为远程 introspection、Gateway 不再持有 login grant，或签名密钥改由外部身份提供方管理，
需重新评审本方案。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已明确 Gateway 强制 exchange、单次重试、错误分类、并发回写、安全边界与备选方案，提交流程检查
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
