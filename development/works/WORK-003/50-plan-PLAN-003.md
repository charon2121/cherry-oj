---
id: "PLAN-003"
type: "plan"
title: "按工作项聚合开发文档"
status: "approved"
work: "WORK-003"
owners: ["codex/root"]
depends_on: ["CHANGE-002", "DESIGN-003", "DECISION-003"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# PLAN-003：按工作项聚合开发文档

## 目标

在不改变永久 ID 和工作流语义的前提下，将开发文档迁移到单工作项目录，并使工具、规范和测试共同
约束这一结构。

## 改动区域

`development/works/` 及原类型目录、`scripts/work`、`scripts/work_test.py`、`development/README.md`、
`development/SPECIFICATION.md`、`CLAUDE.md` 和受影响的全局文档链接。

## 阶段与顺序

1. 建立 WORK-003，确认层级映射与文件命名规则。
2. 改造 CLI 的发现、生成、校验、补充文档和归档逻辑。
3. 按元数据归属迁移现有 WORK-001、WORK-002 与 WORK-003 文档。
4. 更新规范、开发入口、历史设计修订说明和回归测试。
5. 运行全量工具、文档与仓库检查，记录验证证据。

## 并行与依赖

CLI 路径模型先于迁移完成，以便迁移后立即校验。规范与测试可以在路径模型稳定后同步更新；最终验证
依赖全部迁移和文档修订完成。

## 迁移与交付

按 WORK ID 建目录并原样移动文档，根据类型添加层级前缀，最后删除空类型目录。无需业务上线；变更
随仓库版本发布，CI 即为启用边界。

## 风险

主要风险是漏移文档、同 ID 重复、相对链接失效和工具命令仍依赖旧路径。每阶段运行 `scripts/work
check`，最终增加错误层级、跨目录放置和归档原地保留的自动化案例。

## 验证

- 使用 `scripts/work check` 验证全量元数据、位置、文件名和关系。
- 使用 `python3 scripts/work_test.py` 验证 CLI 的十二个端到端场景。
- 使用 `python3 scripts/docs_test.py` 验证 Markdown 链接与全局文档边界。
- 使用全文检索确认没有开发系统旧类型目录的现行引用。

## 回退

回退本工作涉及的 CLI、规范与测试改动，并依据版本控制把文件移回原路径。没有数据库或运行时数据
迁移，不需要额外恢复步骤。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：实施顺序、验证与回退方案已完成
