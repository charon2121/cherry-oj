---
id: "PLAN-004"
type: "plan"
title: "按类型与风险编排开发流程"
status: "approved"
work: "WORK-004"
owners: ["codex/root"]
depends_on: ["CHANGE-003", "DESIGN-004", "DECISION-004"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# PLAN-004：按类型与风险编排开发流程

## 目标

把通用流程开关重构为类型模板、风险 overlays、artifact 图和事实同步阶段，并完成规范与存量迁移。

## 改动区域

`scripts/work`、`scripts/work_test.py`、`development/schema/`、`development/templates/work.md`、
`development/README.md`、`development/SPECIFICATION.md`、所有现有 WORK workflow、`CLAUDE.md` 和根 README。

## 阶段与顺序

1. 用 WORK-004 固化 requirements 和“流程不等于文档”的决定。
2. 定义五种 WORK Type 模板与 fast/risk/impact/concern 增量顺序。
3. 实现 workflow 输出、artifact 绑定、事实同步和状态边界校验。
4. 改造 new/new-doc/flow/refresh/set-status，增加 rebuild-flow 与 set-stage。
5. 重建四个现有 WORK 的 workflow 并更新 Schema、规范和入口。
6. 增加类型差异、风险插入、多对多 artifacts、手工阶段和漂移拒绝测试。

## 并行与依赖

数据模型与选择器必须先稳定，生成、同步和校验依赖它们；迁移必须在新校验可用后执行。规范和测试在
接口稳定后同步，最终验证依赖全部存量 WORK 重建完成。

## 迁移与交付

使用 `rebuild-flow` 原地更新 WORK front matter，不移动或重编号任何文档。纯仓库工具与规范变更无需
业务上线，release 阶段明确 skipped；可靠性观察通过重复 CLI/文档检查完成。

## 风险

选择器顺序错误会生成过多或过少控制，状态同步错误会形成第二套状态，重建可能覆盖人工历史。通过
source/reason、只重建可推导字段、写前全量校验和端到端测试降低风险。

## 验证

运行 19 个 CLI 端到端场景、全量 `scripts/work check`、Markdown 链接检查、Python 语法检查和
`git diff --check`；另外查看 product、maintenance WORK 的 flow 输出确认真实阶段名称与 overlays。

## 回退

回退工具、Schema、规范与 WORK front matter 改动即可。文档 ID、正文、目录和业务数据未迁移，无需
数据恢复或兼容副本。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：实施顺序、迁移、验证和回退方案已完成
