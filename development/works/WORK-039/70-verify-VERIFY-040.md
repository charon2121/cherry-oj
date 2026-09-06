---
id: "VERIFY-040"
type: "verify"
title: "修复 JWT 签发 30 秒后被资源服务误拒绝"
status: "review"
work: "WORK-039"
owners: ["codex/root"]
depends_on: ["TASK-070"]
related: ["VERIFY-038"]
implements: []
verifies: ["ISSUE-011#AC-001", "ISSUE-011#AC-002", "ISSUE-011#AC-003", "ISSUE-011#AC-004", "TASK-070"]
tags: []
result: "pass"
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# VERIFY-040：修复 JWT 签发 30 秒后被资源服务误拒绝

## 验证对象

TASK-070 对共享 JWT verifier 时间语义的修复，以及三个资源服务继承该 verifier 后的回归行为。

## 对应要求

- ISSUE-011#AC-001：历史 `iat`、未来 `exp` 的合法 JWT 被接受。
- ISSUE-011#AC-002：缺 `iat`、过期、未来 `nbf` 与既有非法身份仍被拒绝。
- ISSUE-011#AC-003：时间配置与 Session/login grant 代码没有变化。
- ISSUE-011#AC-004：定向测试、资源服务安全测试和后端聚合回归通过。

## 检查与结果

- 失败先行：新增共享 decoder 测试后、修改生产代码前运行
  `./mvnw -pl identity-security-support -Dtest=IdentityVerifierConfigurationTests test`，历史 `iat`、未来
  `exp` 的合法 JWT 以 `iat claim is invalid` 失败，稳定复现 30 秒隐形寿命。
- 共享模块定向测试：同一命令在修复后通过，1 个用例、0 失败。单个测试同时验证签发 10 分钟但未过期
  的 JWT 被接受，缺 `iat`、已过期 `exp` 和未来 `nbf` 均被拒绝。
- 资源服务装配回归：
  `./mvnw -pl problem-service,submission-service,judging-service -am -Dtest=ResourceSecurityIntegrationTests,ResourceSecurityConfigTests,AdminJudgingSecurityIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，共 11 个相关测试、0 失败，三个资源服务继续使用共享 verifier。
- `./mvnw clean verify`：8 个 reactor 模块完成；48 个 test suite 共 143 个测试，0 failure、0 error，
  1 个仅 Linux 支持的用例按 macOS 平台跳过。
- `git diff --check`：通过。

## 未通过项

无。实施过程中发现 Spring 默认 claim converter 会在缺失 `iat` 时按 `exp - 1s` 合成该字段；已在共享
converter 中仅对原始令牌确实提供的 `iat` 保留转换结果，并由缺失 `iat` 反例锁定。

## 范围检查

生产代码只修改 TASK-070 允许的 `IdentityVerifierConfiguration`，测试只新增于共享模块，工作文档只修改
WORK-039。没有修改 Gateway、user-service、三个资源服务业务代码、时间配置、Session、数据库迁移、Web、
judge-engine 或公开契约。工作区中 WORK-038 的既有未提交改动与本任务分离，不计入 TASK-070 实施范围。

## 遗留问题

当前由 IntelliJ 启动的 problem-service、submission-service、judging-service JVM 仍装载旧类；页面复测前
需要重启这三个资源服务。Gateway 和 user-service 无需因本次改动重启，现有未过期 JWT 可直接继续使用。

## 剩余风险

`iat` 继续作为必填审计字段，但不再限制令牌最大年龄；实际寿命仍由签发方 `exp` 的 2 小时配置硬截止。
未来如需额外的最大令牌年龄，应建立语义明确、独立可配置的 validator，而不是复用 clock skew。

## 结论

通过。共享层已消除签发 30 秒后误拒绝，保留 `exp`、`nbf`、签名、算法、kid、issuer、audience 和业务
claims 校验；既定 2 小时 JWT、提前 5 分钟换新、30 天 fixed-absolute Session 均未变化。

## 变更记录

- 2026-09-06：状态变更：draft → review。原因：共享定向测试、三资源服务安全回归与 143 项后端聚合测试通过，代码边界和时间配置保持不变
