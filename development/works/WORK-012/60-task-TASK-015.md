---
id: "TASK-015"
type: "task"
title: "撤回可观测性实现并保留追溯契约"
status: "done"
work: "WORK-012"
owners: ["codex/root"]
depends_on: ["CHANGE-007", "DESIGN-009", "DECISION-008", "PLAN-009"]
related: []
implements: ["CHANGE-007#REQ-001", "CHANGE-007#REQ-002", "CHANGE-007#REQ-003", "CHANGE-007#REQ-004", "CHANGE-007#REQ-005", "CHANGE-007#REQ-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", ".claude/rules/go.md", "apps/server", "apps/judge-engine", "contracts", "compose.yaml", "observability", "scripts", "docs", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012"]
write_paths: ["apps/server", "apps/judge-engine", "contracts", "compose.yaml", "observability", "scripts/observability_smoke.py", "scripts/contracts_test.py", "docs/architecture.md", "docs/backend.md", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012"]
forbidden_paths: ["apps/web", "docs/product.md", "docs/data-model.md", "docs/database-design.md", "development/works/WORK-002"]
created_at: "2026-08-26"
updated_at: "2026-08-26"
---




# TASK-015：撤回可观测性实现并保留追溯契约

## 任务目标

撤回全部运行时观测代码/依赖和本地采集栈，保留 Request ID/W3C Trace Context 契约并验证业务基线。

## 依据

CHANGE-007、DESIGN-009、负责人确认的 DECISION-008 和 PLAN-009。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

恢复后的 Java/Go/Compose，保留的追溯 contracts/tests，收敛后的长期 docs，带撤回标记的历史设计和
实际验证记录。

## 完成标准

- [x] Java/Go tracked 运行时代码与 POM/go.mod 恢复观测接入前 HEAD。
- [x] 新增观测源文件、collector/dashboard/smoke 全部删除，观测容器停止且业务容器/volume 保留。
- [x] contracts 追溯设计和 Gateway Request ID 保留，docs 不再声称观测实现已存在。
- [x] 全量验证与残留扫描通过。

## 验证

按 PLAN-009 执行 contracts、Java、Go、Compose、work/diff 检查，并用 `rg` 扫描 OTel/Alloy/Grafana/
Metrics/structured logging 残留和追溯契约保留项。

## 风险

发现待回退文件含不属于 WORK-010/011 的用户改动时停止；发现追溯契约无法独立保留时升级 DECISION，
不擅自保留部分运行时观测 SDK。

## 执行记录

- 2026-08-26：创建任务。
- 2026-08-26：负责人明确授权整体回退，并确认只保留 traceId/requestId 追溯设计。
- 2026-08-26：状态变更：todo → ready。原因：上游变更、设计、决策和计划均已批准，读写边界完整
- 2026-08-26：状态变更：ready → doing。原因：开始停止本地观测容器并精确回退运行时观测实现
- 2026-08-26：只停止并移除 Alloy、otel-lgtm 容器；保留业务容器和
  `cherry-oj-engine_alloy-data`、`cherry-oj-engine_observability-lgtm-data` 数据卷。
- 2026-08-26：Java/Go/Compose tracked 运行时文件恢复到接入前基线，删除新增 observability 模块、
  顶层采集配置、dashboard 与 smoke 脚本；contracts 和长期文档只保留 Request ID/W3C Trace Context
  职责边界。
- 2026-08-26：contracts 8 项测试、Java `clean verify`、Go format/vet/build/tidy/race、Compose 配置、
  work 文档与 diff 检查通过；回退镜像重建后 judge/sandbox 均 healthy。
- 2026-08-26：状态变更：doing → done。原因：运行时观测实现已撤回，追溯契约保留且全量回归通过
