---
id: "CAPABILITY-004"
type: "capability"
title: "建立用户身份与访问控制服务"
status: "approved"
work: "WORK-013"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# CAPABILITY-004：建立用户身份与访问控制服务

## 为什么需要

Cherry OJ 的业务服务已分开部署，但尚无可信的“当前用户”来源。若每个服务自行处理密码或相信 Gateway
添加的用户头，凭据会扩散，内部请求也容易伪造。需要一个唯一拥有账号、密码、安全状态和登录授权的
user-service，同时让 Gateway 负责浏览器 Session，让资源服务能够独立验证内部身份。

## 使用者

- 普通用户：登录、退出、查看当前身份和修改自己的密码。
- 管理员：创建和维护普通用户账号，处理停用、恢复与密码重置。
- Gateway：建立浏览器 Session、执行 CSRF 与来源限速，并交换短期内部身份令牌。
- problem/submission/judging 服务：验证身份和 `USER | ADMIN` 角色，不读取 user-service 数据库。
- 部署与值班人员：初始化首个管理员、轮换签名密钥、审计安全事件和恢复服务。

## 能力

- REQ-001（账号开通）：按 DECISION-009 人工选择的方式开通账号；推荐 MVP 只允许 ADMIN 创建 USER，
  不公开自助注册。首个 ADMIN 必须通过显式、一次性的受控流程初始化，生产 migration 不含演示账号。
- REQ-002（密码）：密码只在创建、登录、修改和重置时进入 user-service；只保存带算法标识的自适应
  Argon2id 摘要。密码策略、哈希成本和最大输入大小由边界校验与启动校验约束，禁止自制加密。
- REQ-003（认证）：登录成功返回最小主体和只供 Gateway 保存的登录授权；用户名不存在、密码错误、
  账号停用或临时锁定对公开调用统一为 `AUTHENTICATION_FAILED`，同时保留内部安全审计。
- REQ-004（账号安全）：支持 `ACTIVE | DISABLED`、`USER | ADMIN`、首次登录必须改密、登录失败退避、
  密码修改/重置、账号停用/恢复和乐观并发控制。普通管理 API 不得创建第二个 ADMIN。
- REQ-005（登录授权）：每次登录创建独立、可撤销的高熵授权，数据库只保存其 SHA-256 摘要。退出撤销
  当前授权；改密、重置、角色变化或停用递增 `sessionVersion` 并撤销该用户的全部授权。
- REQ-006（内部 JWT）：user-service 使用非对称密钥签发最多 2 分钟的内部访问 JWT，claim 至少包含
  `sub`、`roles`、`sv`、`iat`、`exp`、`iss`、`aud`、`jti`；通过 `kid` 和内部 JWKS 支持重叠轮换。
- REQ-007（浏览器 Session）：Gateway 使用 Redis 保存随机 Session、登录授权和内部 JWT；浏览器只收
  `HttpOnly`、生产 `Secure`、无 `Domain`、合适 `SameSite` 的 Cookie，登录前后必须更换 Session ID。
- REQ-008（CSRF 与来源保护）：所有基于 Cookie 的写请求必须校验 CSRF token；登录接口同样受保护。
  Gateway 做按来源的粗粒度限速，user-service 做按规范化账号的退避，二者错误均不得暴露判断依据。
- REQ-009（资源授权）：Gateway 转发内部 JWT，不转发密码或裸 `X-User-Id`；每个资源服务独立验证签名、
  issuer、audience、有效期和角色。未知 `kid`、密钥获取失败与业务无权限必须有不同的安全失败语义。
- REQ-010（公开体验）：公开 API 至少覆盖 CSRF 初始化、当前 Session、登录、退出、修改密码，以及 ADMIN
  的用户列表、创建、停用/恢复和重置密码；遵循 WORK-009 的 `data/meta` 与 RFC 9457 协议。
- REQ-011（审计与隐私）：记录登录成功/失败/退避、授权创建/撤销、密码和状态变更、管理员初始化与
  密钥轮换事实；审计详情、日志、指标和错误不得包含密码、摘要、Cookie、JWT、授权原文或完整登录 body。
- REQ-012（可靠性与验证）：user-service 不可用时不能把平台故障伪装成密码错误或主动清除仍可恢复的
  Session；契约、数据库、并发、时钟、密钥轮换、撤销上限和跨模块权限必须有自动化测试。

## 接入方式

浏览器只调用 Gateway `/api/auth/**` 与 `/api/admin/users/**`。Gateway 通过不对公网发布的内部 HTTP
接口调用 user-service；资源服务通过 `Authorization: Bearer <internal JWT>` 接收身份，并从受保护的
JWKS 取得公钥。数据库、Redis、内部令牌和私钥都不对浏览器开放。

## 输入与输出

公开输入仅为 endpoint DTO、CSRF header 和 Session Cookie；内部输入包括登录授权、短期 JWT 和受控服务
身份。公开输出只包含 `id`、`username`、`role`、账号状态和是否必须改密等必要字段。任何接口均不得
返回 passwordHash、登录授权摘要、sessionVersion、私钥或完整审计敏感信息。

## 限制与失败

MVP 只有 `USER | ADMIN`，不实现任意权限表达式。认证失败返回 401，已认证但角色不足返回 403，字段
校验返回 422，并发版本冲突返回 409；user-service/JWKS/Redis 不可用按 502/503/504 表达，不能返回假
401。刷新失败若属于临时基础设施故障，Gateway 保留 Session 供重试；明确撤销或过期才删除 Session。

## 质量要求

登录、哈希和令牌刷新必须有并发上限、超时与输入大小上限；密码哈希成本以目标机器实测校准，不能因
默认值让登录线程耗尽。所有时间判断使用 UTC 和可注入时钟。JWKS 缓存、轮换重叠、Redis TTL 与数据库
授权过期必须有边界测试。公开响应允许兼容新增可选字段，前端对未知错误码安全降级。

## 升级与迁移

先在临时数据库创建表并初始化测试管理员，再接入 Gateway 和资源服务，最后启用 Web。密钥轮换遵循
“发布新公钥 → 等待验证缓存覆盖 → 新钥签名 → 等待旧 token 过期 → 移除旧公钥”。既有全局文档只有在
本方案获批并由实现验证后才同步；当前草案不会直接改写它们。

## 不做什么

不做第三方 IdP、OAuth/OIDC 授权服务器、社交登录、邮箱/短信验证、自助找回密码、多租户、完整 RBAC、
API Key、设备管理页面、永久 JWT、用户资料/头像、删除与合规匿名化，也不修改 judge/sandbox。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：身份能力、边界、失败语义与十二项可验证要求已形成草案，等待人工审核
- 2026-08-26：状态变更：review → approved。原因：负责人确认管理员开通、登录授权、短 JWT 与全部安全能力边界
