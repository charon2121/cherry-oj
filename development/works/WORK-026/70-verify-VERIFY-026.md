---
id: "VERIFY-026"
type: "verify"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "review"
work: "WORK-026"
owners: ["codex/root"]
depends_on: ["TASK-041"]
related: []
implements: []
verifies: ["CAPABILITY-007", "TASK-041"]
tags: []
result: "pass"
created_at: "2026-08-31"
updated_at: "2026-08-31"
---


# VERIFY-026：为 Java 服务提供可直接启动的本地默认配置

## 验证对象

TASK-041 对五服务本地默认、环境覆盖、user-service 临时 RSA、production Secret 边界和启动文档的实现。

## 对应要求

CAPABILITY-007 REQ-001～REQ-006，以及 TASK-041 全部完成标准。

## 检查与结果

- 环境：macOS、JDK 21.0.12.1、Maven Wrapper 3.9.16、Docker Desktop、Testcontainers MySQL/Redis。
- `./mvnw -pl user-service -am -Dtest=TokenConfigTests,JavaServiceConfigurationDefaultsTests
  -Dsurefire.failIfNoSpecifiedTests=false test`：7 项通过，覆盖随机密钥、production 拒绝、单边覆盖、
  五服务 YAML 分类和三个资源服务的 JWKS 发布地址一致性。
- `./mvnw -pl user-service -am test`：24 项通过，包含真实 MySQL 持久化回归。
- `./mvnw clean verify`：七模块 BUILD SUCCESS；Gateway 46、user 24、problem 34、submission 2、judging
  15，共 121 项，0 失败；真实 Linux Judge 外部联调 1 项因未提供环境变量按设计跳过。
- 移除 user 数据库和三项密钥变量后运行 `spring-boot:run --server.port=0`：连接本机 MySQL、生成临时
  RSA 并成功启动；随后正常优雅停止。
- 移除生产 Secret 并启用 `prod` profile：按预期启动失败，明确报告 production 必须提供 `key-id`、
  `private-key-location` 和 `public-key-location`，未采用本地临时密钥。
- judging 无变量 smoke 已使用默认 `cherry_oj_judging` 账号和非空密码，到达 MySQL 后因本机尚未创建/
  授权该账号而返回 Access denied，证明配置缺省已消除且外部基础设施错误可区分。
- 运行时请求 `req_08453d3dffd84e219fcd682246943697` 的五服务健康检查均为 UP；旧默认 JWKS 地址
  `/internal/.well-known/jwks.json` 返回 401，公开地址 `/.well-known/jwks.json` 返回 200。修正三个资源
  服务的默认地址后，新增配置回归测试并重新执行七模块聚合验证通过。
- `scripts/work check`、`git diff --check`、占位符扫描和私钥头扫描在收尾状态通过。

## 未通过项

本机缺少按 `docs/database-design.md` 准备的 `cherry_oj_judging` 数据库账号/权限，因此无法把本地 judging
smoke 推进到应用完全启动；Testcontainers 中 judging 完整 migration/上下文测试通过。

## 范围检查

改动仅落在 TASK-041 write_paths：三个数据库服务 application 配置、user TokenConfig/测试、Server
README/TOOLCHAIN 和 WORK-026。未修改 contracts、Web、Go Judge、migration、Compose 或 WORK-025
文档；保留了 WORK-025 的全部未提交实现。

## 遗留问题

本地需要一次性创建 schema 和最小权限账号；这不属于 application 默认值能力，不在本任务内自动执行。

## 剩余风险

临时密钥的重启失效、多实例限制和生产 profile 依赖在验证通过后仍属于需明确记录的剩余风险。

## 结论

仓库实现和自动化验证通过；本地 judging 完整启动只剩外部数据库账号准备，配置目标已满足。结果提交
复核。

## 变更记录

- 2026-08-31：状态变更：draft → review。原因：116 项聚合测试零失败，配置扫描、正负启动 smoke、diff 与安全检查通过，提交结果复核
- 2026-08-31：运行时联调暴露资源服务 JWKS 默认地址多出 `/internal`；修正三个服务、增加端点一致性
  回归后重新执行 121 项聚合测试，0 失败、1 项按环境条件跳过。
