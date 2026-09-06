---
id: "TASK-069"
type: "task"
title: "兼容常见测试数据 ZIP 并返回可操作校验错误"
status: "done"
work: "WORK-038"
owners: ["codex/root"]
depends_on: ["ISSUE-010", "DESIGN-032"]
related: []
implements: ["ISSUE-010#AC-001", "ISSUE-010#AC-002", "ISSUE-010#AC-003", "ISSUE-010#AC-004", "ISSUE-010#AC-005", "ISSUE-010#AC-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "docs/engineering/conventions.md", "contracts/web-api.openapi.json", "apps/server/TOOLCHAIN.md", "apps/server/problem-service", "apps/server/judging-service", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/problem", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/problem", "development/works/WORK-025", "development/works/WORK-037", "development/works/WORK-038"]
write_paths: ["contracts/web-api.openapi.json", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/storage", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/application/TestDataService.java", "apps/server/problem-service/src/test", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/storage", "apps/server/judging-service/src/test", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/problem", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/problem", "development/works/WORK-038"]
forbidden_paths: ["apps/web", "apps/judge-engine", "apps/server/user-service", "apps/server/submission-service", "apps/server/problem-service/src/main/resources/db", "apps/server/judging-service/src/main/resources/db", "database migrations"]
created_at: "2026-09-05"
updated_at: "2026-09-05"
---

# TASK-069：兼容常见测试数据 ZIP 并返回可操作校验错误

## 任务目标

让 Finder 生成的单外层目录测试数据 ZIP 可以从上传、manifest、原包下载一直部署到判题目录，同时让
不合法 ZIP 的 422 说明可直接指导管理员修正。

## 依据

实现 ISSUE-010#AC-001～AC-006，遵循 DESIGN-032 的逻辑根归一化、元数据忽略、原包不变与双服务
一致校验方案。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- problem-service ZIP entry 分类/逻辑名归一化、manifest 和错误 detail 映射。
- judging-service 同规则二次校验与按逻辑名安全解压。
- Gateway 对 allowlist 业务错误的受限 detail 保留。
- OpenAPI 说明更新及平面/包装/macOS/恶意 ZIP 的跨模块回归测试。

## 完成标准

- [x] 实际 `testin.zip` 等价 fixture 上传 READY，manifest 只有 `test1.in/test1.out`，原包下载逐字节一致。
- [x] judging-service 以该原包和逻辑 manifest 部署成功，目录只含两份逻辑 case 文件。
- [x] 平面、单外层目录、macOS 元数据三类合法输入在两服务结论一致。
- [x] 多根/混合层级/多层目录/路径穿越/软链接/重复逻辑名/孤儿/非 UTF-8/各类超限仍失败并清理。
- [x] Gateway 返回固定、安全、可操作的 422 detail 和 requestId，未知或非 allowlist 上游错误仍不透传。
- [x] OpenAPI、problem/judging/gateway 定向测试和后端聚合回归通过。

## 验证

先运行 `scripts/work context TASK-069`。执行三个相关模块的定向测试，再运行 `./mvnw clean verify`；使用
真实 Finder 形态 fixture 验证上传生成的原始 hash/逻辑 manifest 能被 judging-service 二次校验并落成
平面目录。结果写入 VERIFY-039。

## 风险

不能用“忽略所有隐藏文件”或“递归找测例”替代明确规则。若实现需要改变 manifest DTO、数据库、原包
字节、Web 组件或 judge-engine，先更新 DESIGN/TASK 边界并重新请负责人确认。

## 执行记录

- 2026-09-05：创建任务。
- 2026-09-05：状态变更：todo → ready。原因：意图闸已由负责人签署，设计和读写边界明确，进入实现准备
- 2026-09-05：状态变更：ready → doing。原因：开始实现 ZIP 逻辑根归一化、双服务一致部署和可操作错误详情
- 2026-09-05：problem-service 与 judging-service 已按相同规则接受平面/单包装目录 ZIP、跳过明确的
  Finder 元数据，并在逻辑名层面生成/校验 manifest；Gateway 只为 allowlist code 保留受限 detail。
- 2026-09-05：状态变更：doing → done。原因：Finder ZIP 兼容、双服务逻辑 manifest/部署、Gateway 可操作错误和 OpenAPI 已实现，142 项全量测试通过
