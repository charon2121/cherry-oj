---
id: "TASK-081"
type: "task"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "done"
work: "WORK-042"
owners: ["codex/root"]
depends_on: ["ISSUE-013", "DESIGN-036", "DECISION-023", "PLAN-028"]
related: []
implements: ["ISSUE-013#AC-001", "ISSUE-013#AC-002", "ISSUE-013#AC-003", "ISSUE-013#AC-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "apps/server", "development/works/WORK-039", "development/works/WORK-042"]
write_paths: ["apps/server/user-service/src/main/java/com/cherryoj/userservice/config/TokenConfig.java", "apps/server/user-service/src/test/java/com/cherryoj/userservice/config/TokenConfigTests.java", "development/works/WORK-042"]
forbidden_paths: ["contracts", "apps/web", "apps/judge-engine", "apps/server/gateway-service", "apps/server/identity-security-support", "apps/server/user-service/src/main/resources"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-081：修复后台用户列表拒绝尚未过期的身份令牌

## 任务目标

修复 user-service 对合法历史签发令牌的拒绝并增加正反例回归。

## 依据

ISSUE-013#AC-001 至 AC-004、DESIGN-036、DECISION-023、PLAN-028。

## 可查看范围

以 read_paths 为准。

## 可修改范围

以 write_paths 为准。

## 禁止修改

以 forbidden_paths 为准；禁止顺手改变密钥、网关续签与时间配置。

## 依赖

人工意图闸及后续明确实施授权；上游文档见 depends_on。

## 产出

最小实现差异、真实签名 decoder 测试、VERIFY-043 实际证据和 MEMORY-032。

## 完成标准

- [x] 历史 iat 未过期令牌（含旧发布公钥）解码成功。
- [x] 缺少 iat/exp、过期、未来 nbf、错误签名/issuer/audience 仍失败。
- [x] 定向与相关回归完成，权限和时间配置不变。
- [x] 原场景验证及独立复核、回退检查有证据；环境阻碍如实记录。

## 验证

依据 PLAN-028 执行；先证明回归用例在旧实现失败，再证明修复后通过。

## 风险

需要越界先修订设计和任务边界；不打印身份凭证。

## 执行记录

- 2026-09-07：仅完成静态排查及文档，尚未获得实施授权。
- 2026-09-07：状态变更：todo → ready。原因：用户已签意图闸并明确允许实施，方案与边界完整
- 2026-09-07：状态变更：ready → doing。原因：开始真实签名回归测试与最小修复

- 2026-09-07：用户签署意图闸并允许实施；完成真实签名回归红绿验证与 converter 缺字段修复。实现及自动测试已完成，独立复核与运行复测留待后续阶段。

- 2026-09-07：用户确认实际测试与人工核实无问题，完成最后一项检查，准备签署验收闸。
- 2026-09-07：状态变更：doing → done。原因：自动回归通过，用户确认实际测试及人工核实无问题
