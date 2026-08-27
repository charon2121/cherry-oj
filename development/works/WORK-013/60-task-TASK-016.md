---
id: "TASK-016"
type: "task"
title: "建立用户身份与访问控制服务"
status: "done"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["CAPABILITY-004", "DESIGN-010", "DECISION-009", "PLAN-010"]
related: []
implements: ["CAPABILITY-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts", "docs/architecture.md", "docs/backend.md", "docs/data-model.md", "docs/database-design.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/user-service", "apps/server/gateway-service", "development/works/WORK-002", "development/works/WORK-009", "development/works/WORK-013"]
write_paths: ["contracts", "scripts/contracts_test.py", "apps/server/pom.xml", "apps/server/user-service", "development/works/WORK-013"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "apps/web", "docs"]
created_at: "2026-08-26"
updated_at: "2026-08-26"
---




# TASK-016：建立用户身份与访问控制服务

## 任务目标

在批准的决策下，冻结身份公开契约并实现 user-service 的账号、密码、登录授权、JWT/JWKS、管理员操作、
安全审计和首个管理员初始化，提供后续 Gateway/资源服务可独立接入的稳定边界。

## 依据

实现 CAPABILITY-004 REQ-001～REQ-006、REQ-010～REQ-012 中属于 user-service/契约的部分，只依据
approved 的 DESIGN-010、DECISION-009 与 PLAN-010。未解决 blocking items 或未获执行授权时不得开始。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

OpenAPI auth/admin schemas 与 examples、契约测试、user-service Flyway/MyBatis/Spring Security 实现、
Argon2id、登录授权与审计、JWT/JWKS、管理员 CLI、单元/集成/安全测试和本 WORK 的执行记录。

## 完成标准

- [x] 公开契约先于实现更新，错误、no-store、CSRF 与敏感字段负面 schema 完整。
- [x] migration 无默认用户/密码，Testcontainers 验证表约束、索引、事务与 Flyway clean checkout。
- [x] 创建/登录/改密/重置/停用/恢复、失败退避、must-change-password 和并发冲突均通过。
- [x] 数据库只存密码与登录授权摘要；日志、审计、错误和所有响应不含敏感正文。
- [x] JWT 的 claim、算法、issuer/audience、TTL、kid、JWKS 重叠轮换和固定时钟边界通过。
- [x] 首个管理员命令幂等失败安全，从标准输入取密；普通 API 不能创建 ADMIN。
- [x] 聚合 Maven、契约、文档与范围检查通过，未修改禁止路径。

## 验证

至少运行 `python3 scripts/contracts_test.py`、`cd apps/server && ./mvnw -pl user-service -am clean verify`、
聚合 `./mvnw clean verify`、`scripts/work check` 与 `git diff --check`。使用固定时钟、临时 RSA keys 和
Testcontainers MySQL 验证 REQUIREMENT 与安全反例，不使用共享开发数据库或真实 Secret。

## 风险

Argon2 参数、登录授权结构、JWT claim/TTL、账号状态、公开错误或管理员创建策略需要改变时必须更新
DESIGN/DECISION；不得为方便测试把 JWT 下发浏览器、记录密码、添加默认管理员或修改其它业务服务。

## 执行记录

- 2026-08-26：创建任务。
- 2026-08-26：状态变更：todo → ready。原因：上游方案已批准、人工门禁解除且任务读写边界完整
- 2026-08-26：状态变更：ready → doing。原因：开始实现公开契约与 user-service 身份核心
- 2026-08-26：完成 OpenAPI auth/admin 契约、Flyway/MyBatis 三表模型、Argon2id、登录授权、短期
  RS256 JWT/JWKS、认证与账号管理服务、审计和一次性管理员初始化命令。
- 2026-08-26：`python3 scripts/contracts_test.py` 9/9 通过；使用 MySQL 8.4 Testcontainers 的
  `./mvnw -pl user-service -am clean verify` 10/10 通过；文档与 diff 检查通过。
- 2026-08-26：状态变更：doing → done。原因：公开身份契约、user-service 数据/认证/JWT/审计/管理员初始化已实现，契约与 MySQL 8.4 clean verify 全部通过
- 2026-08-26：独立复核修正失败登录更新随异常回滚、MySQL 同行赋值导致第 4 次提前锁定、临时密码
  偶然包含用户名和轮换后 user-service 不接受上一公钥四个问题；新增真实 MySQL 与上一公钥回归，
  user-service 最终 13/13 通过。
