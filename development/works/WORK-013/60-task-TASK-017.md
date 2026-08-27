---
id: "TASK-017"
type: "task"
title: "接入 Gateway Session 与认证 BFF"
status: "done"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["CAPABILITY-004", "DESIGN-010", "DECISION-009", "PLAN-010", "TASK-016"]
related: []
implements: ["CAPABILITY-004#REQ-006", "CAPABILITY-004#REQ-007", "CAPABILITY-004#REQ-008", "CAPABILITY-004#REQ-010", "CAPABILITY-004#REQ-012"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts", "docs/architecture.md", "docs/backend.md", "apps/server/gateway-service", "apps/server/user-service", "development/works/WORK-009", "development/works/WORK-013"]
write_paths: ["apps/server/gateway-service", "development/works/WORK-013"]
forbidden_paths: ["apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "apps/web", "docs", "contracts"]
created_at: "2026-08-26"
updated_at: "2026-08-26"
---




# TASK-017：接入 Gateway Session 与认证 BFF

## 任务目标

在 user-service 契约与实现可用后，为 Gateway 接入 Redis Session、Cookie/CSRF、登录授权交换、内部 JWT
刷新与公开 auth/admin BFF，使浏览器始终只接触 Session Cookie 和公开 DTO。

## 依据

实现 CAPABILITY-004 REQ-006～REQ-008、REQ-010、REQ-012 的 Gateway 部分；以 approved 的
DESIGN-010、DECISION-009、PLAN-010 和已完成 TASK-016 为前置，不改变公开契约或 user-service 语义。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

Spring Session Redis 配置、Cookie/CSRF、安全过滤链、user-service client、token 刷新协调、公开
auth/admin adapter、来源限速、错误映射、单元/Redis 集成测试与本 WORK 执行记录。

## 完成标准

- [x] 登录前后 Session ID 旋转，Cookie 属性和 Redis 空闲/绝对过期符合批准策略。
- [x] 登录、Session、退出、改密和 admin BFF 与 OpenAPI examples/status/code 对齐。
- [x] 所有 Cookie 写操作包括登录均要求 CSRF；可信 origin、CORS 与 return path 反例通过。
- [x] Gateway 能在授权有效时刷新短 JWT，并对同一 Session 并发刷新做单飞/条件更新。
- [x] 当前端退出、全端撤销、明确过期和 user-service/Redis 临时故障具有不同恢复行为。
- [x] 浏览器响应、日志、Problem 与 trace 不含密码、JWT、登录授权、Cookie 或完整 body。
- [x] Gateway 与聚合 Maven 验证通过，未修改 contracts、user-service、Web 或其它业务服务。

## 验证

运行 `cd apps/server && ./mvnw -pl gateway-service -am clean verify` 与聚合 `./mvnw clean verify`，使用
Testcontainers/嵌入式测试替身验证真实 Redis Session 生命周期，并运行 `scripts/work check`、
`git diff --check` 和敏感 canary 扫描。对 401/403/429/502/503/504、并发刷新、CSRF、Cookie 和开放跳转
逐项断言。

## 风险

不得让 Gateway 校验密码、签 JWT、信任浏览器身份头或修改 user-service。若公开 contract、token TTL、
Session 语义或 Cookie/CSRF 策略需要改变，先升级 DESIGN/DECISION；临时故障不得通过清 Session 掩盖。

## 执行记录

- 2026-08-26：创建任务。
- 2026-08-26：状态变更：todo → ready。原因：TASK-016 已完成，Gateway 任务上游和边界已满足
- 2026-08-26：状态变更：ready → doing。原因：开始接入 Redis Session、CSRF 与认证 BFF
- 2026-08-26：完成 Redis Session、Cookie/CSRF、auth/admin BFF、来源限速、绝对期限和并发 token
  单飞刷新；真实 Redis 集成测试通过 Session ID 旋转、敏感字段负向断言和退出清理。
- 2026-08-26：状态变更：doing → done。原因：Gateway Session、CSRF、认证 BFF、刷新单飞与 Redis 集成测试已完成
- 2026-08-26：独立复核补充 Session 查询刷新撤销事实、上游撤销失败仍清理当前浏览器 Session，以及
  429 `Retry-After`；Gateway 最终 26/26 通过。
