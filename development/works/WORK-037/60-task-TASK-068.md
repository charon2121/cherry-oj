---
id: "TASK-068"
type: "task"
title: "取消会话 idle 并保留 30 天固定期限"
status: "done"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["ISSUE-009", "DESIGN-031", "DECISION-021", "PLAN-025", "TASK-065", "TASK-066"]
related: []
implements: ["ISSUE-009#AC-009", "ISSUE-009#AC-012"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "apps/server/TOOLCHAIN.md", "apps/server/user-service", "apps/server/gateway-service", "development/works/WORK-037"]
write_paths: ["apps/server/user-service/src/main/java/com/cherryoj/userservice/application/AuthenticationService.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application/AuthenticationResult.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application/TokenExchangeResult.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application/SessionTouchResult.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/domain/LoginGrant.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/persistence/LoginSessionMapper.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config/AuthProperties.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/api/AuthController.java", "apps/server/user-service/src/main/resources/mapper/LoginSessionMapper.xml", "apps/server/user-service/src/main/resources/db/migration", "apps/server/user-service/src/main/resources/application.yaml", "apps/server/user-service/src/test", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/auth", "apps/server/gateway-service/src/main/resources/application.yaml", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/auth", "development/works/WORK-037"]
forbidden_paths: ["contracts", "apps/web", "apps/judge-engine", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application/UserAdministrationService.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application/AuditService.java", "apps/server/user-service/src/main/resources/db/migration/V1__create_user_identity_tables.sql"]
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# TASK-068：取消会话 idle 并保留 30 天固定期限

## 任务目标

删除 login grant 与 Gateway Redis WebSession 的 idle 自动过期，从首次登录起使用不可滑动的 30 天
absolute deadline；每请求只读 validate 保持退出、改密、密码重置和账号禁用立即生效。

## 依据

实现 `ISSUE-009#AC-009` 与 `#AC-012`。负责人已确认 JWT 2 小时、提前 5 分钟续签、取消 idle、absolute
固定 30 天，并保留每请求 grant/账号状态验证。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 显式 `fixed-absolute` lifetime policy 与 30 天配置；删除 idle 秒数、deadline 和 refresh-on-activity。
- 新 Flyway 迁移将 `idle_expires_at` 改为 nullable 并停止参与查询，保留非空 `absolute_expires_at`。
- Gateway Session 只保存 absolute deadline，Redis TTL 只取其剩余时间，不因活动滑动。
- `/internal/auth/touch` 迁移为只读 `/internal/auth/validate`，不锁行、不写 idle，继续检查撤销、账号状态、
  `session_version` 和 absolute deadline。
- 旧内部响应/Redis Session 的兼容读取，以及不延长存量 Session absolute 的迁移策略。

## 完成标准

- [x] 连续空闲超过旧 30 分钟后仍可访问；首次登录满 30 天后必须重新登录。
- [x] validate 与 token exchange 都不能延长 absolute deadline，Redis TTL 不随普通业务活动向后滑动。
- [x] 新登录的 MySQL/Redis absolute 来自同一登录时刻加 30 天，idle 为 NULL 且不参与有效性判断。
- [x] 存量有效 Session 保留原 absolute，已经过期或撤销的 Session 不复活，前进/回退有测试。
- [x] logout、改密、密码重置、账号禁用和显式撤销在下一次 validate 时阻止请求并清理 Gateway Session。
- [x] 浏览器 Cookie 仍保持当前 session-cookie 行为，不擅自增加持久 Cookie `Max-Age`。

## 验证

实施前运行 `scripts/work context TASK-068`。使用可控时钟覆盖旧 30 分钟边界、30 天前后、主动撤销矩阵和
内部响应兼容；用真实 Redis 验证 TTL 只递减不滑动，用真实 MySQL 验证 Flyway 与存量会话迁移。

## 风险

30 天 Session 使 Cookie 泄漏窗口变长，因此生产 `Secure`/HttpOnly/SameSite、每请求 validate 与主动撤销
是完成条件。若 validate 的同步 user-service 依赖需要移除，必须另立撤销传播设计，不能在本任务降级。

## 执行记录

- 2026-09-04：创建任务。
- 2026-09-04：由永久 Session 修订为取消 idle、absolute 固定 30 天，并确定保留每请求 validate。
- 2026-09-05：状态变更：todo → ready。原因：TASK-065/066 已完成，准备迁移 fixed-absolute 会话
- 2026-09-05：状态变更：ready → doing。原因：开始取消 idle、迁移 30 天固定 absolute 与只读 validate
- 2026-09-05：状态变更：doing → done。原因：fixed-absolute 30 天会话、无 idle、只读 validate、MySQL/Redis 期限约束及 Cookie 回归均已实现并通过定向测试
