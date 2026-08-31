---
id: "DESIGN-020"
type: "design"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "approved"
work: "WORK-026"
owners: ["codex/root"]
depends_on: ["CAPABILITY-007", "EXPERIENCE-014"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-31"
updated_at: "2026-08-31"
---


# DESIGN-020：为 Java 服务提供可直接启动的本地默认配置

## 背景

CAPABILITY-007 要求五个 Java 服务在本地无需预设 `CHERRY_*` 环境变量启动。盘点所有
`application*.yaml` 后，启动阻塞项只有 user-service 的 `CHERRY_AUTH_KEY_ID`、
`CHERRY_AUTH_PRIVATE_KEY_LOCATION`、`CHERRY_AUTH_PUBLIC_KEY_LOCATION`，以及 judging-service 的
`CHERRY_JUDGING_DB_PASSWORD`。user/problem 数据库已经有本地默认；Gateway Redis 空密码是有效配置；
judging provision 空字段只在功能关闭时存在；`${LOG_FILE}` 由 Spring 日志系统运行时提供。

## 目标与限制

- 覆盖 CAPABILITY-007 REQ-001～REQ-006，保持原环境变量与公开接口不变。
- 默认配置只面向单机本地开发，不承诺自动准备外部基础设施。
- 不提交固定私钥或新真实密码，不改变数据库 schema、服务端口、JWT claim/算法和 JWKS 契约。
- production profile 必须 fail-closed；不能因为改善本地体验而放松多实例稳定密钥要求。
- 不能覆盖用户当前 WORK-025 的其它未提交实现，只在 TASK-041 路径内做最小增量。

## 整体方案

建立“本地可用默认、生产显式 Secret、可选空值保留”的三类配置规则：

1. judging-service 数据库用户名/密码补为与 user/problem 当前本地最小权限账号约定一致的默认值，环境
   变量仍优先，不再默认使用 MySQL `root`。
2. user-service 的三项 RSA 配置补本地语义默认：固定的开发 `kid` 和成对的 `generated:local` 位置标记。
   TokenConfig 识别这一对标记，启动时用 JCA 在内存生成一把 RSA 密钥，复用既有 JwtEncoder、
   JwtDecoder 与 JWKS 输出，不写文件。
3. production profile 或明确非本地运行模式遇到 `generated:local` 时直接拒绝启动；显式文件位置继续走
   当前 PEM 读取与校验，密钥轮换行为不变。
4. 为配置分类增加自动化检查：`${CHERRY_NAME}` 或 `${CHERRY_NAME:}` 只有登记为语义可空时才允许；
   `${LOG_FILE}` 等 Spring 内部占位符不按环境变量处理。

若实现阶段确认 Spring profile 无法可靠区分部署环境，则不得静默猜测生产；应暂停并把运行模式入口
升级到本设计重新审核。

## 模块与数据

- user-service：application 配置、TokenConfig 本地随机密钥分支及配置/上下文测试。密钥对象仍由该服务
  独占，其他服务只从 JWKS 获取公钥。
- judging-service：application 数据库密码默认与上下文/覆盖测试；不修改 migration 和领域代码。
- gateway/problem/submission：核对并测试配置分类，除必要注释或测试外不机械修改已有有效默认。
- apps/server 文档：更新启动前提、本地临时密钥和生产覆盖说明。
- 数据：无 schema 或业务数据变化；临时私钥只在内存，服务停止即消失。

## 接口与状态

环境变量与 Spring 属性名不变。`generated:local` 是 user-service 内部资源位置哨兵，不进入 HTTP 接口。
默认启动时 JWKS 仍返回一个 RS256 公钥，`kid` 为本地开发标识；显式 PEM 配置继续返回部署指定 `kid`
和轮换公钥。production 缺失显式值在应用上下文创建阶段失败。

## 安全与失败

随机 2048 位以上 RSA 密钥使用 JCA `SecureRandom` 生成，不记录、序列化或落盘。它避免已知固定开发
私钥被误用于生产后可伪造 token，但不适合重启续签与多副本；因此 production profile 必须拒绝。
数据库默认口令只视为公开的本地开发约定，不能称为 Secret；部署必须覆盖。显式文件不存在、PEM
错误、只覆盖公钥/私钥一侧、非法 `kid` 都保持启动失败，日志只含属性名。

## 监控与部署

本地日志记录所选模式与临时密钥生命周期警告，不记录凭据。生产部署检查 active profile、稳定 `kid`、
私钥文件只读权限、JWKS 和数据库最小权限。发布前以显式生产测试证明缺少 Secret 会失败；上线采用
原变量值，不需要数据迁移。

## 迁移与兼容

现有本地 export 与生产变量完全兼容；显式三个 RSA 值时不进入生成分支。默认生成会使本地 user-service
重启前签发的 JWT 失效，但 Gateway 可在会话有效时重新交换，仍需在联调测试确认。回退删除默认/生成
分支后恢复必须 export 的旧行为，不触及数据。

## 备选方案

- 提交一对固定开发 PEM：实现最简单，但已知私钥极易被误带到部署，违反 WORK-013“私钥不进仓库”，
  拒绝。
- 默认指向本机未跟踪 PEM：不泄露私钥，但第一次启动仍需人工生成文件，未满足零 export/直接启动。
- 只在 `application-local.yaml` 提供默认并要求激活 profile：安全边界清楚，但仍要求启动参数；可作为
  production 判定无法可靠实现时的回退候选。
- 把 Redis 空密码和所有 provision 空字段也填满：会改变当前有效语义并可能连不上本地 Redis，拒绝。

## 风险与重审条件

主要风险是部署遗漏 production profile、随机密钥导致多副本 JWKS 不一致，以及数据库默认口令被误认
为安全凭据。若项目增加统一部署清单/配置中心、多实例 user-service、外部身份提供者或本地容器化整套
后端，应重新考虑由启动器生成本机 Secret、由 Compose 注入或统一 Secret manager 管理，而不是继续
扩展 application 默认。

## 变更记录

- 2026-08-31：状态进入 review；完成配置分类、进程内临时 RSA、生产拒绝降级和回退方案，提交人工审核。
- 2026-08-31：状态变更：review → approved。原因：负责人确认本地随机 RSA、production 稳定 PEM 与配置语义分类方案，并允许实施
