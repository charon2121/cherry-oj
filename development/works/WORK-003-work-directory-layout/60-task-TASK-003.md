---
id: "TASK-003"
type: "task"
title: "按工作项聚合开发文档"
status: "verified"
work: "WORK-003"
owners: ["codex/root"]
depends_on: ["CHANGE-002", "DESIGN-003", "DECISION-003", "PLAN-003"]
related: []
implements: ["CHANGE-002#REQ-001", "CHANGE-002#REQ-002", "CHANGE-002#REQ-003", "CHANGE-002#REQ-004", "CHANGE-002#REQ-005", "CHANGE-002#REQ-006", "CHANGE-002#REQ-007", "CHANGE-002#REQ-008", "CHANGE-002#REQ-009"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "README.md", "docs/**", "development/**", "scripts/work", "scripts/work_test.py", "scripts/docs_test.py"]
write_paths: ["CLAUDE.md", "docs/frontend.md", "development/**", "scripts/work", "scripts/work_test.py"]
forbidden_paths: ["apps/**", "contracts/**", "deploy/**", "pom.xml", "package-lock.json"]
created_at: "2026-08-24"
updated_at: "2026-08-24"
---





# TASK-003：按工作项聚合开发文档

## 任务目标

实现 CHANGE-002 的全部目录聚合和层级排序要求，完成现有文档迁移，并让规范与自动化校验阻止旧
结构重新出现。

## 依据

依据 front matter 中列出的 CHANGE-002 全部 REQ、DESIGN-003 的路径模型、DECISION-003 的目录决定
和 PLAN-003 的迁移顺序执行。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 支持单工作项目录的 `scripts/work`。
- 聚合后的 `development/works/WORK-xxx-slug/` 文档树。
- 更新后的 README、SPECIFICATION、项目规则和关联全局文档。
- 覆盖生成、层级、共置、归档和既有行为的 CLI 回归测试。

## 完成标准

- [x] 三个现有 WORK 的全部文档按固定层级聚合，旧类型目录删除。
- [x] CLI 创建、发现、补充、校验、查询、上下文、刷新和归档使用新结构。
- [x] 错层级与跨 WORK 文件被校验拒绝，归档文件原地保留。
- [x] SPECIFICATION 和入口文档准确描述新方案。
- [x] 全量验证通过并写入 VERIFY-003。

## 验证

运行 `scripts/work check`、`python3 scripts/work_test.py`、`python3 scripts/docs_test.py` 以及旧路径全文
检索；预期无结构错误、测试全部通过且无现行规范引用旧开发类型目录。

## 风险

若发现无法从元数据唯一确定所属 WORK、一个永久 ID 出现多份文件、相对链接无法可靠修复，立即停止
迁移并升级处理。本次实际文档均有明确 `work` 字段，未触发升级条件。

## 执行记录

- 2026-08-24：完成 CLI 路径模型改造和三个 WORK 的目录迁移。
- 2026-08-24：完成规范、入口文档与回归测试修订，进入全量验证。
- 2026-08-24：创建任务。
- 2026-08-24：状态变更：todo → ready。原因：上游定义、设计、决策和计划均已确认
- 2026-08-24：状态变更：ready → doing。原因：开始执行目录迁移和工具改造
- 2026-08-24：状态变更：doing → done。原因：目录迁移、工具改造、规范与测试修订完成
- 2026-08-24：状态变更：done → verified。原因：VERIFY-003 已确认通过并覆盖全部完成标准
