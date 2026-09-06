---
id: "TASK-073"
type: "task"
title: "切换 judging-service 到真实在线节点部署"
status: "done"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["TASK-072"]
related: ["TASK-069"]
implements: ["ISSUE-012#AC-003", "ISSUE-012#AC-004", "ISSUE-012#AC-006", "ISSUE-012#AC-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "docs/engineering/typescript.md", "docs/engineering/conventions.md", "contracts", "apps/server/judging-service", "apps/server/problem-service", "apps/server/gateway-service", "apps/web", "development/works/WORK-025", "development/works/WORK-038", "development/works/WORK-040"]
write_paths: ["apps/server/judging-service", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/integration/judging", "apps/server/problem-service/src/test", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/problem", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/problem", "contracts/web-api.openapi.json", "development/works/WORK-040"]
forbidden_paths: ["apps/server/user-service", "apps/server/submission-service", "apps/web", "apps/judge-engine", "apps/server/problem-service/src/main/resources/db", "apps/server/judging-service/src/main/resources/db/migration/V1__create_judging_readiness_tables.sql"]
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# TASK-073：切换 judging-service 到真实在线节点部署

## 任务目标

让部署与 readiness 选择真实 ONLINE 节点、调用节点安装 API 并只按校验后的逐节点回执确认 READY。

## 依据

实现 ISSUE-012#AC-003、AC-004、AC-006、AC-007，遵循 PLAN-026 的兼容切换与回退边界。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 有界、流式 Judge Node client，校验 nodeId、指纹、版本、hash 和回执结构。
- 按 ACTIVE + ONLINE + data READY 选择节点的部署/readiness 逻辑和故障恢复。
- `legacy-local`/`node-remote` 单路径开关与回退测试；不双写 READY。
- problem-service/Gateway 对固定节点错误 code 的安全传播和 ADMIN 可操作 detail。

## 完成标准

- [x] 无 ONLINE 节点不会创建 READY，返回 `NO_ONLINE_JUDGE_NODE`。
- [x] 节点成功安装且回执完全匹配后写逐节点 READY；重复部署幂等。
- [x] 超时、失联、4xx、非法正文和回执不匹配分类稳定，临时/数据库状态可重试。
- [x] readiness 只在至少一个 ONLINE 节点持有匹配数据时通过。
- [x] legacy-local 回退仍可运行，V1 migration 不被修改。

## 验证

运行 judging/problem/Gateway 定向测试；用 fake node 覆盖成功、慢响应、断流、拒绝、非法 JSON、超限和
错误回执；真实 MySQL 覆盖节点离线竞态、幂等与失败重试，并验证 ADMIN 只看到 allowlist detail。

## 风险

外部 HTTP 不得进入数据库事务，输入流重试必须重新打开原始资产。若无法保证回执与节点身份绑定，不得
写 READY；不能为追求可操作错误而透传任意节点正文。

## 执行记录

- 2026-09-06：创建任务。
- 2026-09-06：状态变更：todo → ready。原因：Go 节点协议端已通过验证
- 2026-09-06：状态变更：ready → doing。原因：接入远程部署、逐节点 readiness 与可操作错误

- 2026-09-06：远程安装有界流、完整回执比对与租约/session 二次检查已接入；readiness 与校准使用持有数据的 ONLINE 节点，legacy-local 保留。真实 MySQL 回归与 fake HTTP 节点成功、拒绝、超限、错误回执及两级错误映射定向测试通过。
- 2026-09-06：状态变更：doing → done。原因：远程部署、节点 readiness/校准与受限错误映射实现完成；定向 MySQL 与 HTTP 回归通过
