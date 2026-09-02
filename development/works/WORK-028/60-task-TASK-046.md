---
id: "TASK-046"
type: "task"
title: "修复后台用户列表偶发误跳登录页"
status: "done"
work: "WORK-028"
owners: ["codex/root"]
depends_on: ["ISSUE-006", "DESIGN-022", "DECISION-016", "PLAN-018"]
related: []
implements: ["ISSUE-006#AC-001", "ISSUE-006#AC-002", "ISSUE-006#AC-003", "ISSUE-006#AC-004", "ISSUE-006#AC-005", "ISSUE-006#AC-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/engineering/java.md", "docs/engineering/typescript.md", "apps/server/TOOLCHAIN.md", "apps/server/gateway-service", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config", "apps/server/user-service/src/main/java/com/cherryoj/userservice/security", "apps/server/user-service/src/test", "apps/web/TOOLCHAIN.md", "apps/web/src/features/auth", "apps/web/src/features/admin-users", "apps/web/src/routes/admin.tsx", "apps/web/src/routes/admin.users.tsx", "apps/web/e2e", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-026", "development/works/WORK-028"]
write_paths: ["apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/auth", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/auth", "apps/web/src/features/auth", "apps/web/src/features/admin-users", "apps/web/src/routes/admin.users.tsx", "apps/web/e2e", "development/works/WORK-028"]
forbidden_paths: ["contracts", "apps/server/user-service/src/main", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "apps/web/design-system", "docs/design-system.md", "docs/design-system", "database migrations", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016", "development/works/WORK-017", "development/works/WORK-018", "development/works/WORK-019", "development/works/WORK-020", "development/works/WORK-021", "development/works/WORK-022", "development/works/WORK-023", "development/works/WORK-024", "development/works/WORK-025", "development/works/WORK-026", "development/works/WORK-027"]
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# TASK-046：修复后台用户列表偶发误跳登录页

## 任务目标

让 Admin 用户列表在内部短 JWT 被首次拒绝时由 Gateway 安全恢复一次：grant 有效则刷新并返回列表，
grant 无效才退出，临时故障保留 Session；补齐并发与 Web 导航回归，不扩大公开协议。

## 依据

实现 ISSUE-006#AC-001～AC-006，遵循 DESIGN-022 的强制 exchange/单次重试状态机和 DECISION-016 的
Gateway 恢复边界；PLAN-018 规定先失败测试、后认证协调、再 controller 接入。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- Gateway forced token exchange 与逐 WebSession 回写实现。
- Admin 用户 GET 首次下游 401 的单次恢复与错误映射。
- 认证服务/controller/Redis 并发测试，以及必要的 Web 导航回归。
- VERIFY-029 实际命令、结果和范围证据；MEMORY-022 经验证后的长期结论。

## 完成标准

- [x] AC-001：正常用户列表不增加 token exchange 或重复业务请求。
- [x] AC-002：旧 token 首次 401、grant 有效时只 exchange 一次、只重试一次并成功，公开不出现 401。
- [x] AC-003：exchange 401 清 Session，Web 跳登录且刷新不能恢复旧 Session。
- [x] AC-004：exchange 5xx/timeout 与 fresh token 二次 401 转为 5xx、保留 Session且不循环。
- [x] AC-005：并发恢复单飞且每个 WebSession 保存同一新状态；敏感值不进日志/响应。
- [x] AC-006：受控旧签名 token 或本地密钥切换回归通过，正常 401/403/CSRF/退出/改密不回归。
- [x] 未修改 contracts、user-service 实现、数据库、其它服务或设计系统；如确需越界，先更新本 TASK。

## 验证

按 PLAN-018 执行 Gateway 聚合测试、Web 相关测试/类型检查、受控密钥切换集成、`scripts/work check` 和
`git diff --check`。每项在 VERIFY-029 记录环境、精确命令、测试数、失败项与剩余风险。

## 风险

禁止未经幂等证明自动重放 Admin 写操作。若 forced exchange 必须改 user-service、公开契约、Redis 数据
结构或其它资源 BFF，立即停止并先升级 DESIGN/PLAN 与 TASK 边界。多实例 Gateway 的跨进程单飞不在
本任务承诺内，但必须确认不会因每实例各一次交换而破坏授权真值。

## 执行记录

- 2026-09-02：创建任务。
- 2026-09-02：状态变更：todo → ready。原因：意图闸已通过，设计、决策、计划与代码边界完整，任务具备实施条件
- 2026-09-02：状态变更：ready → doing。原因：开始先补下游 JWT 401 恢复失败测试，再实现 Gateway 单次强制交换与重试
- 2026-09-02：Gateway 增加资源 token 被拒后的强制 exchange，touch/exchange 按 Session 单飞并把结果回写每个
  参与请求的 WebSession；Admin 用户 GET 只在首次下游 401 时恢复并单次重试，写操作未自动重放。
- 2026-09-02：补充 controller、认证并发和真实 Redis HTTP 集成用例；Gateway 55 项、服务端 130 项、Web
  109 项及 2 个 Admin 用户页 E2E 通过，Security Review 无 finding。
- 2026-09-02：状态变更：doing → done。原因：Gateway 强制 exchange、Admin 用户 GET 单次重试、并发回写与全部自动化证据已完成
