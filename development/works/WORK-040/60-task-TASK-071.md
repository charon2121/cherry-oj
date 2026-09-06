---
id: "TASK-071"
type: "task"
title: "定义节点控制协议并建立注册数据模型"
status: "done"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["ISSUE-012", "DESIGN-034", "DECISION-022", "PLAN-026"]
related: ["TASK-069"]
implements: ["ISSUE-012#AC-001", "ISSUE-012#AC-002", "ISSUE-012#AC-007", "ISSUE-012#AC-008"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "docs/engineering/go.md", "docs/engineering/conventions.md", "contracts", "docs/architecture.md", "docs/data-model.md", "docs/database-design.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/judging-service", "apps/judge-engine", "development/works/WORK-025", "development/works/WORK-038", "development/works/WORK-040"]
write_paths: ["apps/judge-engine/internal/contract", "contracts", "docs/architecture.md", "docs/data-model.md", "docs/database-design.md", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/api", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/application", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/config", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/persistence", "apps/server/judging-service/src/main/resources/db/migration", "apps/server/judging-service/src/test", "development/works/WORK-040"]
forbidden_paths: ["apps/web", "apps/server/gateway-service", "apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/judge-engine/internal/judge", "compose.yaml"]
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# TASK-071：定义节点控制协议并建立注册数据模型

## 任务目标

固定 Go/Java 共用的节点控制协议，并在 judging-service 建立环境、在线节点与逐节点部署三层事实。

## 依据

实现 ISSUE-012#AC-001、AC-002、AC-007、AC-008，遵循 DESIGN-034、DECISION-022 和 PLAN-026 第一阶段。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 版本化注册、心跳、安装请求/回执与错误 schema，以及 Java/Go 共享 fixture。
- V2 migration：`judge_node`、`test_data_node_deployment`、租约/选择/幂等所需约束和索引。
- judging-service 注册与心跳入口、首环境激活规则、nodeId/指纹不变式和可控时钟租约查询。
- 更新架构和数据模型，区分环境、节点、部署回执。

## 完成标准

- [x] 空库首次注册创建唯一 ACTIVE 环境与 ONLINE 节点；相同注册幂等。
- [x] 不同 nodeId 同指纹挂入同环境；同 nodeId 改指纹被拒绝；不同指纹不自动切换 ACTIVE。
- [x] 10 秒心跳/35 秒租约可配置，过期只影响在线选择，不删除历史。
- [x] migration 从 V1 前进通过，现有环境/部署/calibration 不被改写。
- [x] 契约 fixture 在 Java 与 Go 两端验证一致。

## 验证

运行 `scripts/work context TASK-071`，执行 contract 校验、judging-service 注册/租约定向测试、真实 MySQL
V1→V2 迁移与并发注册测试；用可控时钟覆盖租约边界，不使用 sleep。

## 风险

不实现自动环境切换，不把节点 endpoint/token 暴露到 ADMIN API。若现有表无法兼容新增逐节点事实，先
更新设计与迁移方案，不能直接重写 V1。

## 执行记录

- 2026-09-06：由 default-profile 小修重构为控制协议与注册数据模型任务。
- 2026-09-06：状态变更：todo → ready。原因：人工意图闸已签署且本轮明确授权执行，协议与注册控制面边界明确
- 2026-09-06：状态变更：ready → doing。原因：开始定义版本化节点协议与追加迁移、注册租约实现

- 2026-09-06：MySQL 8.4 实际执行 V1、V2；并发首次注册、指纹冲突、租约边界、恢复和 session 变化保留历史回执测试通过。Go 共享 fixture 无字段丢失往返通过。既有 V1 部署/校准测试通过，独立 Linux Judge 集成留待 TASK-074。
- 2026-09-06：状态变更：doing → done。原因：节点协议、追加迁移、注册和租约实现及 MySQL/Go 契约回归通过
