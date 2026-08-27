---
id: "DESIGN-010"
type: "design"
title: "建立用户身份与访问控制服务"
status: "approved"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["CAPABILITY-004", "EXPERIENCE-005"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# DESIGN-010：建立用户身份与访问控制服务

## 背景

CAPABILITY-004 要把“浏览器登录”“账号与密码真源”“内部身份传递”和“资源服务授权”连成一个安全
边界。现有全局基线已确定 Gateway BFF、Redis Session、user-service 自建认证、内部非对称 JWT 和
`USER | ADMIN`，但 user-service 仍为空骨架，且现有文档对“Gateway Session 与 user-service 登录会话
谁是真源”存在表述张力。本设计将两者拆成不同对象：Gateway 拥有浏览器会话，user-service 拥有登录
授权与撤销事实。

## 目标与限制

目标是交付可供 WORK-002 使用的登录与最小访问控制，并使密码修改、账号停用和密钥轮换具有可证明的
失效边界。限制是浏览器只经过 Gateway，服务各写自己的数据库，公开协议遵循 WORK-009，MVP 只有两个
角色，不依赖外部 IdP，也不为本工作扩展用户资料或 judge 能力。

## 整体方案

身份链分为三层：

```text
浏览器 ── Session Cookie + CSRF ──► Gateway / Redis
                                      │ 登录授权（仅服务端持有）
                                      ▼
                                user-service / MySQL
                                      │ 2 分钟内部 JWT
                                      ▼
                  problem / submission / judging / user-service 管理接口
```

登录时 Gateway 先验证 CSRF 和来源限速，再把凭据送到 user-service。user-service 校验账号、密码和退避，
创建高熵登录授权，只存其摘要并返回授权原文一次；Gateway 更换 Session ID，将授权和短期 JWT 放入 Redis，
浏览器只收到 Cookie。JWT 临近过期时，Gateway 用登录授权换取新 token；user-service 每次检查账号状态、
授权状态、绝对/空闲期限和 sessionVersion。资源服务只离线验证短 JWT，因此安全事件的最坏传播窗口等于
token TTL，而不是浏览器 Session 时长。

## 模块与数据

- `user-service`：账号、密码摘要、临时锁定、必须改密、角色、状态、sessionVersion、登录授权、审计、
  JWT 签发与 JWKS。私钥来自只读 Secret 文件，不进数据库或应用配置仓库。
- `gateway-service`：公开 auth/admin BFF、Spring Session Redis、Cookie、CSRF、return path、来源限速、
  token 刷新协调和下游错误映射；不校验密码、不签 JWT。
- 各资源服务：Spring Security resource server，独立校验 JWT 与角色；userId 只取已验证 `sub`。
- `apps/web`：Session Query、登录/改密/管理员用户页面和路由保护；不把 JWT 或密码放入 localStorage、
  sessionStorage、URL、Query cache 日志或错误遥测。

在现有 `user_account` 基础上拟增加 `password_change_required`、失败计数/时间与 `locked_until`。新增
`user_login_session`，保存 id、userId、登录授权 SHA-256、创建/最近使用/空闲与绝对过期、撤销事实、
sessionVersion 快照和乐观锁；新增/沿用 `user_audit_event` 保存允许字段清单内的安全事件。用户名按
Unicode NFKC 后执行项目固定规则并以唯一索引兜底，展示值与规范化登录值不能形成两个唯一性真源。

## 接口与状态

公开 OpenAPI 提案：

- `GET /api/auth/csrf`：建立匿名 Session 并返回 CSRF token，`Cache-Control: no-store`。
- `GET /api/auth/session`：返回 `authenticated` 与最小当前用户；不返回内部 token。
- `POST /api/auth/login`、`POST /api/auth/logout`、`POST /api/auth/password/change`。
- `GET/POST /api/admin/users`、`PATCH /api/admin/users/{id}/status`、
  `POST /api/admin/users/{id}/password-reset`。

user-service 内部接口包括 authenticate、exchange/refresh、revoke-current/revoke-all、当前主体与管理员
账号命令，以及只在内部网络发布的 JWKS。内部 DTO 不套公开 `data/meta`，但错误具有稳定 code。公开
登录失败统一 401 `AUTHENTICATION_FAILED`；角色不足 403；重复用户名、状态/版本冲突 409；字段规则
422；限速 429；Redis/user-service/JWKS 故障使用 502/503/504。基础设施故障不得改写成 401。

建议会话策略为 30 分钟空闲、12 小时绝对期限、允许多端登录；退出撤销当前授权，改密/重置/角色变化/
停用撤销全部授权。具体数值和并发策略属于 DECISION-009 人工门禁，配置入口对越界值启动失败。

## 安全与失败

- 密码最长输入和请求体先限流/限长，再进行 Argon2id；使用 dummy hash 平衡未知用户名与错误密码路径。
- 公开登录错误不区分不存在、停用、锁定或错误密码；内部审计使用受限枚举和不可逆主体摘要。
- 登录前后旋转 Session ID；Cookie 生产 `Secure + HttpOnly + SameSite=Lax + Path=/api` 且不设 Domain；
  所有 Cookie 写请求含 CSRF，CORS 仅允许配置的同源/开发 origin 且不与通配 credential 组合。
- 登录授权至少 256 bit 随机，只经内部 TLS 传输，MySQL 只存 SHA-256；Redis 与数据库泄漏不能单独恢复
  浏览器密码。授权比较常量时间，撤销和 sessionVersion 更新在本地事务内完成。
- JWT 推荐 RS256/RSA-3072、唯一 `kid`、固定 issuer/audience、120 秒 TTL、最多 30 秒时钟偏差；禁止
  接受 `alg=none`、来访算法或未知 audience。公钥重叠时间覆盖缓存和最大 token 生命周期。
- user-service 临时故障时，尚未过期的内部 JWT 可继续完成当前请求；刷新故障返回 503 并保留 Gateway
  Session。明确授权过期/撤销才返回 401 并清理 Session。
- 首个管理员由一次性命令从标准输入读取密码；已有 ADMIN 时默认失败，禁止 migration seed、默认密码、
  命令行参数或普通启动自动创建。

## 监控与部署

健康检查区分进程存活与 MySQL/密钥就绪；Redis 属于 Gateway 健康边界。记录登录、刷新、撤销、403、
429、未知 kid、JWT 验证失败、哈希耗时和授权清理数量等有界计数/日志字段，但当前仓库没有遥测后端，
不能把“定义观测点”写成已经可查询的线上监控。用户名、userId、token、来源 IP 和错误原文不作为指标
标签。部署至少提供当前私钥、当前/上一公钥、issuer/audience、TTL、密码哈希参数与可信内部网络。

## 迁移与兼容

当前无业务用户表，可从首个 Flyway migration 创建完整结构，不迁移历史密码。生产 migration 不带账号；
测试与演示 seed 只在独立 profile。公开 OpenAPI 先增加 auth schemas/paths，再生成 Web 类型；Gateway 与
user-service 可先在暗路径联调，资源服务验证全部接入后再开放登录入口。

兼容上线先让资源服务同时接受当前旧的“无认证测试路径”和新 JWT 仅限受控开发环境，生产发布时采用
维护窗口一次切换；真实业务数据上线后不得长期保留认证旁路。回退只能关闭新公开入口并恢复上一应用，
不能删除用户/审计表或恢复已撤销授权。

## 备选方案

- Gateway-only Session：只在 Redis 保存用户身份、每次向下游转发裸头。实现最少，但资源服务无法验证
  身份，违背既定边界，排除。
- 浏览器直接持有 access/refresh JWT：省去 Gateway Session，却扩大 XSS、存储、撤销和前端协议风险，
  与当前 BFF 基线冲突，排除。
- Gateway Session + user-service 登录授权 + 短 JWT（推荐）：多一个授权表和刷新接口，但浏览器凭据、
  服务撤销与资源离线验证各有清晰真源。
- 外部 OIDC/IdP：成熟但引入第三方部署与账号产品决策；未来多组织/SSO 时重新考虑，不进入 MVP。

## 风险与重审条件

主要代价是 Redis Session 与 MySQL 登录授权两个状态需要一致地撤销，且离线 JWT 天生存在最长 2 分钟
撤销窗口。测试必须覆盖“DB 已撤销但 Redis 仍在”“Redis 已删但授权仍在”“并发刷新与退出”“轮换中
未知 kid”和时钟边界。若需要即时全局撤销、公开 API 客户端、第三方 SSO、细粒度权限、多租户、设备
管理或超过两个角色，应重新审查登录授权、token audience 和 RBAC 模型，而不是继续堆 claim。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：Session、登录授权、短 JWT、数据、接口、安全、迁移与备选方案已形成草案，等待人工审核
- 2026-08-26：状态变更：review → approved。原因：负责人确认 Gateway Session、user-service 登录授权、短 JWT、数据与安全设计
