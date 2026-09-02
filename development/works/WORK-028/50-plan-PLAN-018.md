---
id: "PLAN-018"
type: "plan"
title: "修复后台用户列表偶发误跳登录页"
status: "checked"
work: "WORK-028"
owners: ["codex/root"]
depends_on: ["ISSUE-006", "DESIGN-022", "DECISION-016"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# PLAN-018：修复后台用户列表偶发误跳登录页

## 目标

交付 ISSUE-006 AC-001～AC-006：下游短 JWT 首次被拒绝时由 Gateway 恢复一次，真实 grant 失效才退出，
临时身份链故障保留 Session；用并发、密钥切换和 Web 导航回归证明不再出现“跳登录但刷新仍在线”。

## 改动区域

- Gateway 认证状态与单飞协调：`GatewayAuthenticationService` 及单元/Redis Session 测试。
- Admin 用户读取 BFF：`AdminUserController`、`UserServiceClient` 的错误分类及 controller 测试。
- Web 只做行为回归；除非实施证据证明 Gateway 修复仍无法维持既有 401 合同，否则不改 auth/session 代码。
- WORK-028 的 TASK、VERIFY、MEMORY 和流程证据。

## 阶段与顺序

1. 先补失败测试：旧 token 的列表调用返回 401，但 token exchange 成功并签发新 token；现状应暴露公开
   401，以此锁定问题。
2. 在认证服务实现强制 exchange、操作类型隔离的单飞和逐 WebSession 回写，覆盖并发与失败分类。
3. 在 Admin 用户 GET 调用边界接入“首次 401 → exchange → 单次重试”，第二次 401 转 503。
4. 运行 Gateway 模块测试和 Web 相关单测/E2E；检查正常 401、403、退出、改密与 Session 恢复。
5. 独立复核其它 Admin BFF 是否存在同模式，只记录影响，不在未扩边界时修改；完成 VERIFY 与 MEMORY。

## 并行与依赖

步骤 1～3 强依赖，不能并行。Gateway 单元测试与 Web 导航回归可在实现稳定后并行执行。真实 user-service
密钥切换场景依赖本地 MySQL/Redis 与两服务进程，只作为最终集成验证；单元测试必须先用受控假 token
稳定覆盖，不让验证依赖人工碰概率。

## 迁移与交付

不改数据库和公开契约，按普通 Gateway 代码交付。Gateway 可以先于 user-service/Web 合入；现有客户端
只会看到更准确的 401/5xx 语义。生产发布仍沿用显式 PEM 与既有密钥轮换顺序，本 WORK 不变更 Secret。

## 风险

自动重试如果误用于写接口可能制造重复操作；单飞若让 touch 与 forced exchange 共用无法区分的槽位，
可能拿回旧 token；Reactive Redis Session 的并发请求各有对象实例，只有发起者写回会被其它旧实例覆盖；
错误映射若把第二次 401 继续透传，会保留原 bug。

## 验证

- Java：`cd apps/server && ./mvnw -pl gateway-service -am test`，覆盖 AC-001～AC-005 的 controller、认证
  服务与 Redis Session 行为。
- Web：`cd apps/web && npm run test:run -- <相关 auth/admin 测试>`，再运行 `npm run typecheck` 与关键
  `/admin/users` E2E；若无源码改动也要记录现有跳转合同证据。
- 集成：受控旧签名 token（优先自动化；可补本地服务重启）证明 AC-006，记录公开 status/code/requestId，
  不记录凭据。
- 仓库：`scripts/work check`、`git diff --check`，并按 TASK 边界核对改动。

## 回退

回退 Gateway 的 forced exchange 与 Admin 用户单次重试改动即可；无数据回滚。回退后旧版本仍能读取
现有 Redis Session，但会重新暴露 ISSUE-006。若发布后出现资源请求放大、重复业务调用或 Session 回写
异常，应立即回退，不通过放宽 401 或关闭资源服务验签止血。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已拆分失败测试、认证协调、controller 接入、回归与回退顺序，提交流程检查
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
