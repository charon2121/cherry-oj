---
id: "TASK-034"
type: "task"
title: "落地 problem-service 题库读取能力"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "EXPERIENCE-013", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033"]
related: []
implements: ["FEATURE-007#REQ-001", "FEATURE-007#REQ-002", "FEATURE-007#REQ-003", "FEATURE-007#REQ-004", "FEATURE-007#REQ-005", "FEATURE-007#REQ-006", "FEATURE-007#REQ-008", "FEATURE-007#REQ-010", "FEATURE-007#REQ-011"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/database-design.md", "docs/data-model.md", "contracts/web-api.openapi.json", "apps/server/pom.xml", "apps/server/TOOLCHAIN.md", "apps/server/problem-service"]
write_paths: ["apps/server/problem-service", "apps/server/TOOLCHAIN.md", "development/works/WORK-025"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-034：落地 problem-service 题库读取能力

## 任务目标

在 problem-service 内落地已确认的题目数据库结构与只读领域能力，使匿名公开列表/详情能够从真实
MySQL 当前发布版本查询，同时证明私有、草稿和敏感判题字段不可达。

## 依据

落实 front matter 所列 `FEATURE-007` 要求，严格依据 `docs/database-design.md` 的 problem-service DDL、
`docs/data-model.md` 的 `ProblemSummary/ProblemDetail`、`DESIGN-019` 和人工批准后的 `DECISION-014`。
若物理表或公开范围需要改变，必须先升级上游文档。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- problem-service 所需 MySQL driver、Flyway、MyBatis 及测试依赖，并同步更新 Server 工具链说明。
- `V1__create_problem_tables.sql`：六张表、索引、CHECK、服务内外键和最后添加的循环外键。
- 列表/详情公开读模型的 Controller、DTO、Service、Repository/Mapper、游标编码校验、统一内部错误。
- SecurityFilterChain 仅对 `/internal/public/problems/**` GET 精确匿名放行，其余规则不放宽。
- 独立开发 profile 的幂等 A+B seed；生产 profile 与 migration 不包含业务题目。
- 单元、MockMvc、安全与 Testcontainers MySQL 8.4 集成测试。

## 完成标准

- [x] 空库 Flyway migrate 成功，表/列/约束/索引与数据库设计一致，已发布 V1 不被测试或 seed 改写。
- [x] 列表组合筛选、正反排序和 cursor 在同值时间戳、多页及并发新增公开题时无页内重复；读取
  `size + 1`，不执行总数查询或 N+1。
- [x] 详情按 slug 返回当前 PUBLISHED 版本，samples/languages 顺序稳定；不可见记录统一 404。
- [x] 公开 DTO、JSON、异常和日志不存在 judgeTemplate、测试数据/存储/审计字段及 canary 内容。
- [x] 匿名只允许两个公开 GET 范围；admin、snapshot、其它路径仍需有效 JWT/角色，裸身份头无效。
- [x] 开发 seed 重复执行幂等，生产配置不启用；代表性查询 EXPLAIN 使用预期索引并记录结果。
- [x] problem-service 测试和后端全量构建通过，现有资源服务 JWT 测试无回归。

## 验证

从 `apps/server` 使用根 reactor 执行 problem-service 及依赖测试，再执行 `./mvnw clean verify`。集成测试
必须启动临时 MySQL 8.4，验证 clean migration、约束负例、开发 seed 两次执行、筛选/游标边界、统一
404、敏感 canary 和 Security matcher。对代表性数据运行并保存 EXPLAIN；运行 `git diff --check` 和
路径检查，将实际结果写入 `VERIFY-025`。

## 风险

migration 上线后不可重写，公开 DTO 泄漏和 permitAll 路径过宽是停止发布级风险。若兼容依赖要求修改
根 Maven 版本、需要跨服务数据库、需要服务身份、需新增/改变数据库字段或查询性能必须依赖新索引，
停止并更新 DESIGN/DECISION，而不是在实现中偏离既定 DDL。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全数据库、公开读模型、安全、seed、测试与升级边界，等待人工批准。
- 2026-08-30：状态变更：todo → ready。原因：契约前置已完成，开始 problem-service V1 与公开读模型
- 2026-08-30：完成六表 V1、MyBatis 公开读模型、筛选/三种排序/绑定筛选条件的键集游标、精确 public
  GET 安全规则和 dev A+B 幂等 seed；MySQL 8.4、JWT/JWKS 与全 reactor clean verify 通过。
- 2026-08-30：状态变更：ready → doing。原因：problem-service V1、公开查询、安全与 MySQL 测试已完成，整理验证证据
- 2026-08-30：状态变更：doing → done。原因：problem-service 六表 V1、公开读模型、游标、安全与 dev seed 完成，MySQL 8.4 和全 reactor 验证通过
