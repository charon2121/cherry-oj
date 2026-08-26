---
id: "VERIFY-006"
type: "verify"
title: "按思维导图结构重写开发文档系统规范"
status: "approved"
work: "WORK-006"
owners: ["codex/root"]
depends_on: ["TASK-006"]
related: []
implements: []
verifies: ["CHANGE-004#AC-001", "CHANGE-004#AC-002", "CHANGE-004#AC-003", "CHANGE-004#AC-004", "TASK-006"]
tags: []
result: "pass"
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# VERIFY-006：按思维导图结构重写开发文档系统规范

## 验证对象

重写后的 `development/SPECIFICATION.md`、CHANGE-004 的四项验收标准，以及 TASK-006 的完成标准和
读写范围。

## 对应要求

- CHANGE-004#AC-001：九个主题构成新的一级文章结构。
- CHANGE-004#AC-002：现行类型、状态、关系、目录和流程字段仍可定位。
- CHANGE-004#AC-003：开发文档、Markdown 链接和差异格式检查通过。
- CHANGE-004#AC-004：修改范围限于规范、本工作项和永久编号索引。
- TASK-006：正文是解释性文章，而不是思维导图节点的简单扩写。

## 检查与结果

验证环境为 Darwin 24.3.0 arm64，Python 3.12.4。

1. 运行 `scripts/work check`：41 份开发文档通过校验，进行中提示为 0。
2. 运行 `python3 scripts/docs_test.py`：69 份 Markdown 文档入口和本地链接有效。
3. 运行 `python3 scripts/work_test.py`：19 个工作流、风险叠加、目录、状态与上下文回归场景全部通过。
4. 检查一级标题数量、旧编号标题和代码围栏：除文档标题外恰好存在 9 个主题章节，没有残留 10–78
   的并列一级标题，代码围栏成对闭合。
5. 检查工作类型、concern、风险、影响面、阶段状态、文档状态、关系、流程选择器字段和任务路径字段：
   所有必需术语都能在新正文中定位。
6. 运行 `git diff --check`：通过，没有空白或补丁格式错误。

## 未通过项

暂无。

## 范围检查

`git status --short` 只包含 `development/SPECIFICATION.md`、创建 WORK-006 后单调推进的
`development/index.json`，以及 `development/works/WORK-006/`。
没有修改 Schema、模板、脚本、既有工作项、业务代码、契约、全局文档或 CI。

新正文改变章节编号、顺序和表达方式，不改变现行工具语义，未发现实现偏差。

## 遗留问题

暂无。

## 剩余风险

文字重排仍然可能影响熟悉旧编号的读者，但旧规范没有被其他文档按章节号引用；新结构以稳定主题名
替代 78 个并列编号。工具字段、命令和目录示例均保留，剩余风险可接受。

## 结论

通过。九章结构、文章表达、现行规则覆盖、自动检查和修改范围均满足验收标准。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：结构、内容覆盖、文档系统、链接、回归与范围检查全部通过
