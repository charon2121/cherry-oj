---
id: "VERIFY-004"
type: "verify"
title: "按类型与风险编排开发流程"
status: "approved"
work: "WORK-004"
owners: ["codex/root"]
depends_on: ["TASK-004"]
related: []
implements: []
verifies: ["CHANGE-003", "TASK-004"]
tags: []
result: "pass"
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# VERIFY-004：按类型与风险编排开发流程

## 验证对象

五种 WORK Type 基础流程、风险/影响/concern 增量规则、阶段与文档 artifact 关系、阶段进度同步、存量
workflow 迁移、Schema 与规范一致性。

## 对应要求

覆盖 CHANGE-003 的 REQ-001 至 REQ-013、AC-001 至 AC-009，以及 TASK-004 的全部完成标准。

## 检查与结果

环境：macOS，本地仓库根目录，Python 3.12。

- `python3 scripts/work_test.py`：通过，19 个端到端场景全部成功。分别覆盖 product、infra、fix、
  maintenance、improvement，快速流程，高风险/system overlays，delivery/observe 规则，多对多 artifacts，
  set-stage、配置漂移、阶段同步及既有路径/状态/引用行为。
- `scripts/work check`：通过，33 份开发文档的目录、元数据、类型流程、风险增量、artifacts、阶段进度、
  依赖、状态边界和验证证据无错误、无进行中提示。
- `python3 scripts/docs_test.py`：通过，61 份 Markdown 文档入口和本地链接有效。
- `python3 -m py_compile scripts/work scripts/work_test.py scripts/docs_test.py`：通过。
- `git diff --check`：通过，没有空白错误。
- `scripts/work flow WORK-002/WORK-003/WORK-004`：产品显示功能定义与体验设计；重构显示 CHANGE 起始
  流程；高风险、系统级与 reliability overlays 的来源、checks 和进度符合规则。

## 未通过项

暂无。

## 范围检查

变更限于 TASK-004 的 write_paths：工作流工具与测试、development 文档系统、项目开发规则和根 README。
没有修改 apps、contracts、deploy、依赖配置或业务数据。实现与 CHANGE-003、DESIGN-004、DECISION-004
一致。

## 遗留问题

暂无。

## 剩余风险

当前不支持 WORK 级任意流程 overrides，也没有独立阶段事件日志；这是为了保持同类流程和风险门禁可
校验的有意限制。新增 WORK Type 或合法定制需求出现时，需要同步扩展模板、规范和测试。

## 结论

通过。类型化流程、增量规则、artifact 模型、阶段状态、存量迁移和规范更新满足验收要求，可以将
TASK-004 与 WORK-004 标记为 verified。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：19 个工具测试、33 份开发文档校验和 61 份链接检查全部通过
