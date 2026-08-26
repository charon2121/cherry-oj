---
id: "TASK-007"
type: "task"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "done"
work: "WORK-007"
owners: ["codex/root"]
depends_on: ["CHANGE-005", "DESIGN-005", "DECISION-005", "PLAN-005"]
related: []
implements: ["CHANGE-005"]
verifies: []
tags: []
read_paths: ["docs/product.md", "docs/prd-background.md", "docs/architecture.md", "docs/data-model.md", "docs/backend.md", "docs/frontend.md", "contracts", "development/works/WORK-002"]
write_paths: ["docs/product.md", "development/works/WORK-007"]
forbidden_paths: ["apps", "contracts", "docs/architecture.md", "docs/data-model.md", "docs/backend.md", "docs/frontend.md", "development/works/WORK-002"]
created_at: "2026-08-25"
updated_at: "2026-08-25"
---




# TASK-007：校正全局 PRD 与当前 MVP 基线的漂移

## 任务目标

重写全局 PRD，消除实现状态、未来 backlog 和当前 MVP 的混杂，同时保持全部已确认产品规则。

## 依据

实现 CHANGE-005 的 REQ-001 至 REQ-008，遵循 DESIGN-005 与 DECISION-005 的单一稳定 PRD 方案。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

更新 `docs/product.md`；完成 WORK-007 的变更、决定、计划、验证和记忆证据。

## 完成标准

- [x] PRD 明确首个 C++ ACM 切片、完整 ACM/CORE MVP 与长期题目运营方向。
- [x] PRD 不再维护当前实施进度、P1/P2 backlog 或已落地架构的未来缺口。
- [x] 九项产品不变量、安全边界与当前非目标均有清晰位置。
- [x] WORK-002 三个 blocking 问题仍未被代签，并有明确入口。
- [x] 全部文档与工作项检查通过。

## 验证

按 PLAN-005 运行文档系统全量检查、关键词检查、差异检查和跨文档人工核对，预期无错误、无漂移词、
无超范围修改。

## 风险

若发现必须改变已确认 ACM/CORE、角色、版本、提交追溯、限制或 Agent MVP 边界，停止维护任务并新建
product WORK 请求人工确认。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-25：完成漂移审计并冻结只读/写入/禁止路径边界。
- 2026-08-25：状态变更：todo → ready。原因：上游定义与决定已批准，读写边界和完成标准完整
- 2026-08-25：状态变更：ready → doing。原因：开始按稳定产品合同结构重写 docs/product.md
- 2026-08-25：完成 PRD 全文重写、兼容性复核和本地全量文档验证。
- 2026-08-25：状态变更：doing → done。原因：PRD 全文重写、范围复核、跨文档核对和全部本地检查已完成
