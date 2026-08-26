---
id: "TASK-001"
type: "task"
title: "重建统一开发文档系统"
status: "verified"
work: "WORK-001"
owners: ["codex/root"]
depends_on: ["CHANGE-001", "DESIGN-001", "DECISION-001", "PLAN-001"]
related: []
implements: ["CHANGE-001"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "README.md", "docs/**", "development/SPECIFICATION.md", "product/**", "tasks/**", "scripts/**", ".github/workflows/ci.yml", ".gitignore"]
write_paths: ["AGENTS.md", "CLAUDE.md", "README.md", "docs/**", "development/**", "scripts/work", "scripts/work_test.py", "scripts/docs_test.py", ".github/workflows/ci.yml", ".gitignore", "product/**", "tasks/**", "PRD.md"]
forbidden_paths: ["apps/**", "contracts/**", "docker-compose.yml", "testdata/**"]
created_at: "2026-08-24"
updated_at: "2026-08-24"
---





# TASK-001：重建统一开发文档系统

## 任务目标

实现并迁移统一开发文档系统，使仓库只剩清晰的全局文档和开发过程文档入口。

## 依据

CHANGE-001、DESIGN-001、DECISION-001 与 PLAN-001。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

统一 `development/` 文档树、模板与 Schema；`scripts/work`、端到端测试和文档链接检查；迁移后的 FEATURE/MEMORY；
更新后的协作规则和 CI；删除旧双中心。

## 完成标准

- [x] 新工作可以按分类和风险自动生成正确流程与文档。
- [x] 校验覆盖编号、元数据、引用、依赖环、状态、范围和验证证据。
- [x] 项目总览、查询、追踪、状态刷新和任务上下文命令可用。
- [x] 旧系统有效内容完成迁移，专用目录、脚本和 CI 被删除。
- [x] 全局文档与开发过程文档边界在所有入口一致。
- [x] 验证命令和结果写入 VERIFY-001。

## 验证

运行 `python3 scripts/work_test.py`、`scripts/work check`、`scripts/work flow WORK-001`、
`scripts/work context TASK-001`、旧引用全文搜索和 `git diff --check`。现有工程按 CI 入口回归。

## 风险

若迁移需要改变产品行为或现有架构定义，停止并升级到对应 FEATURE/DESIGN；本任务只改变文档协作系统。

## 执行记录

- 2026-08-24：创建任务。
- 2026-08-24：完成实现、迁移与规则切换，等待最终验证。
- 2026-08-24：状态变更：todo → ready。原因：上游定义与方案已确认，范围明确
- 2026-08-24：状态变更：ready → doing。原因：开始统一系统实现与迁移
- 2026-08-24：状态变更：doing → done。原因：实现、迁移和回归检查完成
- 2026-08-24：状态变更：done → verified。原因：VERIFY-001 已记录通过证据
