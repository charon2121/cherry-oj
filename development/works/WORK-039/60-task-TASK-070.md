---
id: "TASK-070"
type: "task"
title: "修复 JWT 签发 30 秒后被资源服务误拒绝"
status: "done"
work: "WORK-039"
owners: ["codex/root"]
depends_on: ["ISSUE-011", "DESIGN-033"]
related: ["TASK-068"]
implements: ["ISSUE-011#AC-001", "ISSUE-011#AC-002", "ISSUE-011#AC-003", "ISSUE-011#AC-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "docs/engineering/conventions.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/identity-security-support", "apps/server/problem-service/src/test/java/com/cherryoj/problemservice/security", "apps/server/submission-service/src/test/java/com/cherryoj/submissionservice/security", "apps/server/judging-service/src/test/java/com/cherryoj/judgingservice/security", "development/works/WORK-037", "development/works/WORK-039"]
write_paths: ["apps/server/identity-security-support/src/main/java/com/cherryoj/identitysecurity/IdentityVerifierConfiguration.java", "apps/server/identity-security-support/src/test", "development/works/WORK-039"]
forbidden_paths: ["apps/web", "apps/judge-engine", "contracts", "apps/server/gateway-service", "apps/server/user-service", "apps/server/problem-service/src/main", "apps/server/submission-service/src/main", "apps/server/judging-service/src/main", "database migrations"]
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# TASK-070：修复 JWT 签发 30 秒后被资源服务误拒绝

## 任务目标

修正共享 JWT verifier 的 `iat` 语义，让未过 `exp` 的 2 小时 JWT 不再于签发 30 秒后被拒绝，并用直接
回归测试固定有效与无效时间边界。

## 依据

实现 ISSUE-011#AC-001～AC-004，遵循 DESIGN-033 的共享修复与最小边界。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 共享 validator chain 移除错误的 `iat` 时间窗口，required claims 显式要求 `iat`。
- 覆盖历史 `iat` 未过期、缺少 `iat`、已过期和未来 `nbf` 的自动化测试。
- VERIFY-040 中记录定向与聚合回归证据。

## 完成标准

- [x] `iat` 早于当前时间超过 30 秒但 `exp` 在未来的合法 JWT 可被共享 decoder 接受。
- [x] 缺 `iat`、已过期、未来 `nbf` 及现有签名/claims 非法用例仍被拒绝。
- [x] 不修改 2 小时、5 分钟、30 天配置或任何 Session/数据库/公开契约。
- [x] 共享模块定向测试和 `apps/server` 聚合回归通过。

## 验证

先运行 `scripts/work context TASK-070`，再运行 identity-security-support 定向测试和
`./mvnw clean verify`。测试必须通过构造历史 `iat` 来稳定复现，不使用真实 sleep。结果写入 VERIFY-040。

## 风险

不得通过扩大 clock skew 或 Gateway 401 重试绕过问题。若修复需要修改签发方、Gateway、三个资源服务
业务代码、契约或数据，先更新 DESIGN/TASK 边界并重新请负责人确认。

## 执行记录

- 2026-09-06：创建任务。
- 2026-09-06：状态变更：todo → ready。原因：意图闸已签署，根因、最小修复与代码边界明确
- 2026-09-06：状态变更：ready → doing。原因：开始修复共享 JWT iat 校验并补时间边界回归测试
- 2026-09-06：移除误用的 `JwtIssuedAtValidator`，把原始 `iat` 必填并入共享 required-claims 校验；
  同时阻止 claim converter 为缺失的 `iat` 合成默认值。
- 2026-09-06：历史 `iat`、缺失 `iat`、过期 `exp`、未来 `nbf` 定向测试，三个资源服务安全回归及
  后端 `clean verify` 全部通过。
- 2026-09-06：状态变更：doing → done。原因：共享 JWT verifier 已按 exp/nbf 校验寿命并保留原始 iat 必填，时间边界、三资源服务安全回归和 8 模块 clean verify 均通过
