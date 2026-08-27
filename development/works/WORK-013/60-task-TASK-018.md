---
id: "TASK-018"
type: "task"
title: "为资源服务接入身份验证与角色授权"
status: "done"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["CAPABILITY-004", "DESIGN-010", "DECISION-009", "PLAN-010", "TASK-016"]
related: []
implements: ["CAPABILITY-004#REQ-006", "CAPABILITY-004#REQ-009", "CAPABILITY-004#REQ-012"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts", "docs/architecture.md", "docs/backend.md", "apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "development/works/WORK-002", "development/works/WORK-013"]
write_paths: ["apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "development/works/WORK-013"]
forbidden_paths: ["apps/server/user-service", "apps/server/gateway-service", "apps/server/logging-support", "apps/judge-engine", "apps/web", "docs", "contracts"]
created_at: "2026-08-26"
updated_at: "2026-08-26"
---


# TASK-018：为资源服务接入身份验证与角色授权

## 任务目标

为 problem、submission 和 judging 三个资源服务接入统一 JWT 验证和 `USER | ADMIN` 授权入口，证明服务
只信任已验证的 `sub/roles`，不信任 Gateway 裸头，也不改变各自领域数据和判题职责。

## 依据

实现 CAPABILITY-004 REQ-006、REQ-009、REQ-012 的资源服务部分；以 approved 的 DESIGN-010、
DECISION-009、PLAN-010 和 TASK-016 的 JWKS/claim 契约为依据。具体 endpoint 权限只按已批准产品基线，
尚未实现的业务 API 使用测试控制器或安全组件级验证，不提前实现 WORK-002 业务。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

三个服务的 resource server 配置、共享配置约定（不新建共享业务实体）、principal/authority 映射、
401/403 处理、JWKS 缓存与故障策略、角色/claim/伪造头安全测试和本 WORK 执行记录。

## 完成标准

- [x] 三个服务均验证固定算法、issuer、audience、exp/iat、kid 和签名，并只从 `sub` 读取 userId。
- [x] USER/ADMIN 权限矩阵一致；401 与 403 分离，错误遵循内部/公开适配边界。
- [x] 裸 `X-User-Id`、伪造角色、错误 alg/aud/iss、过期 token、未知 kid 全部被拒绝。
- [x] JWKS 已缓存 key 可在短暂获取故障时继续验证，未知 key 无法获取时 fail closed 且不伪装业务 401。
- [x] 安全配置不读取 user-service 数据库，不创建跨服务共享实体，不改变领域表或 Go/Kafka 契约。
- [x] 三模块与聚合 Maven 测试通过，未修改禁止路径。

## 验证

分别运行三个 Maven module 的 `-am clean verify` 和 `cd apps/server && ./mvnw clean verify`。使用临时
RSA keys、受控 JWKS server 与固定时钟执行有效/无效 token 矩阵，再运行 `scripts/work check`、
`git diff --check`。测试必须显式证明“Gateway 前置检查通过”不是资源服务授权的替代品。

## 风险

不得借安全接入实现题目、提交或判题业务，也不得新建跨模块 auth 业务 library。若三个服务需要不同
audience、权限集合或服务身份凭据，先更新设计；不能通过放宽 issuer/audience/算法或信任裸头解决联调。

## 执行记录

- 2026-08-26：创建任务。
- 2026-08-26：开始为 problem、submission、judging 接入统一的资源服务器安全配置。
- 2026-08-26：完成 RS256/JWKS、issuer/audience/claim 校验、USER/ADMIN 授权、裸身份头拒绝以及
  JWKS 已知 key 故障缓存与未知 key 503 fail-closed；三模块 8 个安全测试通过。
- 2026-08-26：状态变更：doing → done。原因：三个资源服务的 JWT 验签、角色授权、JWKS 故障语义及安全测试已完成
- 2026-08-26：独立复核要求三个资源服务拒绝 `pwd=true` 的首次登录 token，服务端落实“改密前不能
  访问受保护资源”，聚合回归通过。
