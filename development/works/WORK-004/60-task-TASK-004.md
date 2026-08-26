---
id: "TASK-004"
type: "task"
title: "按类型与风险编排开发流程"
status: "verified"
work: "WORK-004"
owners: ["codex/root"]
depends_on: ["CHANGE-003", "DESIGN-004", "DECISION-004", "PLAN-004"]
related: []
implements: ["CHANGE-003#REQ-001", "CHANGE-003#REQ-002", "CHANGE-003#REQ-003", "CHANGE-003#REQ-004", "CHANGE-003#REQ-005", "CHANGE-003#REQ-006", "CHANGE-003#REQ-007", "CHANGE-003#REQ-008", "CHANGE-003#REQ-009", "CHANGE-003#REQ-010", "CHANGE-003#REQ-011", "CHANGE-003#REQ-012", "CHANGE-003#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "README.md", "docs/**", "development/**", "scripts/work", "scripts/work_test.py", "scripts/docs_test.py"]
write_paths: ["CLAUDE.md", "README.md", "development/**", "scripts/work", "scripts/work_test.py"]
forbidden_paths: ["apps/**", "contracts/**", "deploy/**", "pom.xml", "package-lock.json"]
created_at: "2026-08-24"
updated_at: "2026-08-24"
---





# TASK-004：按类型与风险编排开发流程

## 任务目标

实现 CHANGE-003 的类型化流程、风险增量、artifact 关系、阶段同步和校验，并完成存量迁移与规范更新。

## 依据

依据 front matter 中 CHANGE-003 的全部 REQ、DESIGN-004 的数据模型、DECISION-004 的控制面/产物面
分离决定，以及 PLAN-004 的迁移顺序。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 五种声明式 WORK Type 流程模板和增量选择器。
- 带 requirement/status/artifacts/checks/source/reason 的 workflow。
- artifact 绑定、事实同步、状态边界与完整性校验。
- `rebuild-flow`、`set-stage` 及改造后的现有 CLI 命令。
- 新 Schema、模板、SPECIFICATION、README、项目规则和迁移后的 WORK。
- 覆盖分类、风险、进度和回归行为的端到端测试。

## 完成标准

- [x] product、infra、fix、maintenance、improvement 使用独立模板。
- [x] high/system、delivery 和 concern 规则可解释地插入流程与检查。
- [x] 同一 TASK 同时绑定 tasks/development，无文档阶段不生成空 Markdown。
- [x] 阶段事实同步、显式操作推进和 WORK 状态边界生效。
- [x] 四个存量 WORK 完成 workflow 重建且 ID、目录和正文保留。
- [x] SPECIFICATION、README、Schema、模板和 CLAUDE 与实现一致。
- [x] 全量验证完成并写入 VERIFY-004。

## 验证

执行 `python3 scripts/work_test.py`、`scripts/work check`、`python3 scripts/docs_test.py`、Python 语法检查
和 `git diff --check`。预期类型流程、风险 overlays、多对多 artifacts、set-stage 和旧行为全部通过。

## 风险

若重建会改变永久 ID/正文、合法流程不能由模板表达、阶段状态无法从事实稳定重算，停止迁移并升级。
本次只改可推导 workflow 字段，未触发这些条件。

## 执行记录

- 2026-08-24：实现类型模板、风险 overlays、artifact 绑定与阶段同步。
- 2026-08-24：迁移四个 WORK，更新 Schema、SPECIFICATION、README 和 19 个回归场景。
- 2026-08-24：创建任务。
- 2026-08-24：状态变更：todo → ready。原因：上游定义、设计、决策和计划均已确认
- 2026-08-24：状态变更：ready → doing。原因：开始实现类型化流程和风险增量模型
- 2026-08-24：状态变更：doing → done。原因：工具、迁移、规范、Schema 和测试改造完成
- 2026-08-24：状态变更：done → verified。原因：VERIFY-004 已确认通过并覆盖全部完成标准
