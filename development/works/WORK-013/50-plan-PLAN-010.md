---
id: "PLAN-010"
type: "plan"
title: "建立用户身份与访问控制服务"
status: "approved"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["CAPABILITY-004", "EXPERIENCE-005", "DESIGN-010", "DECISION-009"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# PLAN-010：建立用户身份与访问控制服务

## 目标

在 DECISION-009 获人工确认后，按契约先行顺序交付 CAPABILITY-004：先冻结公开/内部身份协议和数据
不变量，再实现 user-service，随后接入 Gateway、资源服务和 Web，最后执行独立安全复核与跨模块验证。
本文只描述计划，不授权编码、迁移或部署。

## 改动区域

- `contracts/web-api.openapi.json` 与契约测试：auth/admin 路径、Session/用户 DTO、错误与 CSRF header。
- `apps/server/user-service`：Spring Security、MyBatis/Flyway/MySQL、密码、登录授权、审计、JWT/JWKS、
  首个管理员命令与测试。
- `apps/server/gateway-service`：Spring Session Redis、CSRF/Cookie、BFF、限速、token 刷新与测试。
- `apps/server/problem-service`、`submission-service`、`judging-service`：resource server 验签、角色边界和
  测试；不修改领域数据模型。
- `apps/web`：Session client/Query、登录/改密、管理员用户页面、路由和 E2E。
- `docs/architecture.md`、`backend.md`、`data-model.md`、`database-design.md`、`frontend.md`：只在批准方案
  被实现和验证后同步长期事实。

## 阶段与顺序

0. 人工门禁：确认 DECISION-009 全部 checklist，解决 WORK-013 blocking items 和 WORK-002 UNKNOWN-001。
1. TASK-016：先更新 OpenAPI/契约测试，再完成 user-service 数据、认证、授权、JWT/JWKS 与 CLI。
2. TASK-017：接入 Gateway Redis Session、Cookie、CSRF、刷新、公开 BFF 与来源限速。
3. TASK-018：在资源服务接入相同 JWT 验证与 USER/ADMIN 授权，负向证明裸头不可用。
4. TASK-019：实现 Web Session、登录/改密与管理员用户体验，生成类型不手改。
5. 独立复核：检查密码/凭据泄露、账号枚举、CSRF、固定会话、SSRF/开放跳转、JWT/JWKS、并发撤销、
   数据与任务路径。
6. VERIFY-013：执行契约、Java、Web、MySQL、Redis、密钥轮换和真实跨服务 E2E；实际发布后再观察。

## 并行与依赖

TASK-016 是共同前置。其契约和内部接口冻结后，TASK-017 与 TASK-018 可以并行；TASK-019 依赖公开
Gateway 行为稳定。任何任务发现需要开放注册、改变角色模型、把 JWT 交给浏览器或信任裸用户头，必须
回到 DECISION-009，不得在任务中自行扩大范围。

## 迁移与上线

先在临时 MySQL/Redis 与一次性测试密钥上联调，使用测试管理员完成安全矩阵。非生产环境按
user-service → Gateway → 资源服务 → Web 顺序部署，资源服务认证旁路只能在显式 test profile 存在。
生产切换前必须备份 user schema、验证私钥/JWKS 权限、Cookie Secure/域名、可信 origin、服务内网和
时钟同步；首个管理员初始化后立即删除引导凭据并检查审计。

上线停止条件包括大量未知 kid、刷新 5xx、Session 循环丢失、401/403 激增、Argon2 线程耗尽、CSRF
误拒绝或任何凭据出现在日志。没有生产目标、Secret 管理和发布授权时，仓库实现不得代签上线完成。

## 风险

风险集中在安全策略误配置与跨模块不一致：OpenAPI 与实现漂移、JWT audience/issuer 不同、Redis 和 MySQL
撤销次序、并发刷新、密码哈希 DoS、客户端将 503 当成登出、以及开发认证旁路进入生产。每项必须有
自动反例和独立复核；真实 Secret 不进入仓库或测试输出。

## 验证

- 契约：JSON/OpenAPI 解析、examples、生成类型无漂移、401/403/409/422/429/503 与 no-store/cookie/CSRF。
- user-service：Testcontainers MySQL + Flyway，用户名唯一、Argon2、dummy hash、退避、授权摘要、撤销、
  乐观锁、审计 allowlist、固定时钟、JWT claim/alg/aud/iss/kid 与轮换。
- Gateway：Redis Session、登录 ID rotation、Cookie 属性、CSRF、并发刷新单飞、失败映射、return path 与
  来源限速；确认浏览器响应和日志均无 JWT/授权。
- 资源服务：有效/过期/错误 audience/未知 kid/USER/ADMIN/裸头矩阵；JWKS 临时故障与缓存行为。
- Web：format/lint/typecheck/unit/build/E2E；loading、错误、401、403、503、must-change-password 与键盘体验。
- 跨模块：登录→受保护 USER→ADMIN 拒绝→管理员开通→临时密码改密→全端撤销→停用→密钥轮换。

## 回退

应用可回到上一版本并关闭新公开入口，但保留数据库表、审计、已升级密码摘要和所有已撤销授权；不得
回滚到默认密码或重新激活旧 Session。密钥问题优先把签名切回仍在 JWKS 中的上一私钥，不能移除新公钥
后继续签新 kid。若发现凭据泄露，立即撤销相关授权、轮换密钥并按实际日志/备份系统处理清理，不能只
删本地文件。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：四阶段任务边界、顺序、验证、上线与回退计划已就绪，等待上游决策确认
- 2026-08-26：状态变更：review → approved。原因：负责人授权按四个 TASK 顺序开始实施
