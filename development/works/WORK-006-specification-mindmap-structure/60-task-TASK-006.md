---
id: "TASK-006"
type: "task"
title: "按思维导图结构重写开发文档系统规范"
status: "verified"
work: "WORK-006"
owners: ["codex/root"]
depends_on: ["CHANGE-004"]
related: []
implements: ["CHANGE-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "development/README.md", "development/SPECIFICATION.md", "development/schema/**", "development/templates/**", "scripts/work", "scripts/work_test.py", "scripts/docs_test.py", "development/works/WORK-006-specification-mindmap-structure/**"]
write_paths: ["development/SPECIFICATION.md", "development/index.json", "development/works/WORK-006-specification-mindmap-structure/**"]
forbidden_paths: ["apps/**", "contracts/**", "docs/**", "tutorial/**", "development/schema/**", "development/templates/**", "scripts/**", ".github/**"]
created_at: "2026-08-25"
updated_at: "2026-08-25"
---





# TASK-006：按思维导图结构重写开发文档系统规范

## 任务目标

以思维导图九条主干为结构，重写 `development/SPECIFICATION.md`，把逐条罗列的规范改造成层次清楚、
可以连续阅读的文章，同时完整保留当前已实现规则。

## 依据

以 CHANGE-004 的 REQ-001 至 REQ-009 为依据；内容事实以重写前的现行 `SPECIFICATION.md`、
`development/README.md` 和当前工具行为为准。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

重写后的 `development/SPECIFICATION.md`，以及记录实际验证结果的 VERIFY-006。

## 完成标准

- [x] 九个主题构成清晰的一级章节，子章节覆盖原规范全部规则。
- [x] 文字以解释性文章段落为主，没有退化为思维导图节点的简单扩写。
- [x] 现行流程、状态、文档、目录、追踪、权限和工具契约没有语义变化。
- [x] 文档系统、链接、章节覆盖和 Markdown 格式检查通过。

## 验证

运行 `scripts/work check`、`python3 scripts/docs_test.py`、标题与关键词覆盖检查、
`git diff --check` 和范围检查。通读新旧结构映射，确认每个原一级主题都有明确去向。

## 风险

本任务只允许重排和改写规范。发现 Schema、工具或既有规则需要变化时停止并建立新的设计工作，不能
在本任务中顺手修改。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-25：状态变更：todo → ready。原因：上游定义已确认，读写边界和验证方式明确
- 2026-08-25：状态变更：ready → doing。原因：开始按九个主题重写规范
- 2026-08-25：完成正文重写和内容覆盖复核，进入自动验证。
- 2026-08-25：状态变更：doing → done。原因：九章正文重写、术语覆盖和范围复核完成
- 2026-08-25：状态变更：done → verified。原因：VERIFY-006 已记录并通过全部验收检查
