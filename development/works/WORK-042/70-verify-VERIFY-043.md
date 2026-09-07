---
id: "VERIFY-043"
type: "verify"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "approved"
work: "WORK-042"
owners: ["codex/root"]
depends_on: ["TASK-081"]
related: []
implements: []
verifies: ["ISSUE-013#AC-001", "ISSUE-013#AC-002", "ISSUE-013#AC-003", "ISSUE-013#AC-004", "TASK-081"]
tags: []
result: "pass"
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# VERIFY-043：修复后台用户列表拒绝尚未过期的身份令牌

## 验证对象

user-service JWT 时间校验与后台用户列表。

## 对应要求

ISSUE-013#AC-001 至 AC-004。

## 检查与结果

环境：2026-09-07，macOS arm64，OpenJDK 21.0.12.1，Maven Wrapper，真实 RSA 签名 + 生产 TokenConfig decoder；MySQL/Redis 集成测试使用 Testcontainers。

1. 修改测试、尚未改实现时，从 apps/server 执行 `./mvnw -pl user-service -am -Dtest=TokenConfigTests -Dsurefire.failIfNoSpecifiedTests=false test`：8 项中 2 项错误，当前公钥与旧公钥的历史签发令牌均报 `iat claim is invalid`，成功复现缺陷。
2. 初次修复后执行 `./mvnw -pl user-service,gateway-service -am test`：沙箱拒绝共享测试绑定本地 Socket；经工具权限审核允许后重跑。网关 58 项通过、共享身份模块 8 项通过；user-service 的负例暴露默认 converter 自动补 iat，补充 DESIGN-036 后保留原始字段缺失信息。
3. 最终执行 `./mvnw -pl user-service -am test`：BUILD SUCCESS；user-service 27 项、identity-security-support 8 项全部通过，无失败、错误或跳过。包括 TokenConfigTests 8 项以及真实 MySQL 持久化测试。
4. AC-001：历史 5 分钟的当前 ADMIN 令牌和旧发布公钥令牌均成功解码。
5. AC-002：缺少 iat/exp、过期、未来 nbf、错误 issuer/audience、同 kid 不同私钥签名全部被拒绝。
6. AC-003：审查 diff，仅修改 user-service 两份 Java 文件；签发寿命、网关提前换新、会话、角色、公开错误映射与密钥文件未修改。
7. AC-004：AdminUserControllerTests 的列表转发及 401→503 测试、AdminGatewayAccessTests 的普通用户拒绝测试、GatewaySessionRedisIntegrationTests 等网关回归通过；这些不能替代真实浏览器和正在运行服务的端到端重放。
8. `git diff --check` 通过；文档校验结果在任务交付前记录。原未跟踪 ZIP 未修改。

## 未通过项

无。用户先确认“测试完毕，没有问题”，在提示独立复核与任务记录尚未关闭后再次确认“我已经核实过了”。据此记录人工现场验证与复核通过；不声称智能体执行了服务重启或原 requestId 重放。

## 范围检查

符合 TASK-081 两份 Java 文件与当前 WORK 文档边界；WORKS.md 和 index.json 由工作管理命令维护。无数据迁移或配置改动。converter 补充用于满足已批准的 iat 必填要求。

## 遗留问题

用户已确认实际测试无问题，暂无已报告遗留问题。

## 剩余风险

人工现场复测与复核已由用户确认，验收闸待用户签署。回退只需撤回两份 Java 文件的本任务差异并重构建，不涉及数据；回退将恢复旧误拒绝行为。

## 结论

自动回归通过，用户确认现场测试及人工核实无问题，验证结论为 pass；验收闸仍由用户本人签署。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：红绿测试及实际限制已记录，result pending 不代替运行复测和独立复核

- 2026-09-07：依据用户“测试完毕，没有问题”及“我已经核实过了”补齐人工验证和复核记录。
- 2026-09-07：验收闸通过：review → approved。原因：已完成人工核实和实际测试，后台用户列表正常，确认验收通过
