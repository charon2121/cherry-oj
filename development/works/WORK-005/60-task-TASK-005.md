---
id: "TASK-005"
type: "task"
title: "修复开发文档 CI 的 clean checkout 链接校验"
status: "done"
work: "WORK-005"
owners: ["codex/root"]
depends_on: ["ISSUE-001"]
related: []
implements: ["ISSUE-001"]
verifies: []
tags: []
read_paths: ["docs/engine.md", "scripts/docs_test.py", ".gitignore", ".github/workflows/ci.yml", ".githooks/pre-commit"]
write_paths: ["docs/engine.md", "scripts/docs_test.py", "scripts/docs_test_test.py", ".github/workflows/ci.yml", ".githooks/pre-commit", "development/works/WORK-005"]
forbidden_paths: ["apps", "contracts", "tutorial"]
created_at: "2026-08-25"
updated_at: "2026-08-25"
---




# TASK-005：修复开发文档 CI 的 clean checkout 链接校验

## 任务目标

让本地文档校验与 GitHub Actions clean checkout 使用同一链接有效性边界，并消除当前失效链接。

## 依据

实现 `ISSUE-001` 的 AC-001 至 AC-003，不改变 `tutorial/` 的忽略策略。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

更新教程说明和链接校验器；新增校验器单测；把单测接入 pre-commit 与 CI；记录验证证据。

## 完成标准

- [x] `docs/engine.md` 不再链接未跟踪的教程文件。
- [x] 已跟踪文件与包含已跟踪内容的目录可以作为链接目标。
- [x] 未跟踪目标即使本地存在也被拒绝。
- [x] 本地所有开发文档检查通过。

## 验证

运行 `python3 scripts/docs_test_test.py`、`python3 scripts/docs_test.py`、`python3 scripts/work_test.py`、
`scripts/work check` 和 `git diff --check`，预期全部通过；提交后确认 GitHub Actions job 通过。

## 风险

低风险且可直接回退。若发现 Git 跟踪判断影响仓库外链接、锚点或已跟踪目录，暂停并升级方案。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-25：边界确定为文档、校验器、hook/CI 和本工作项，禁止修改本地教程内容。
- 2026-08-25：完成实现、单元测试、完整本地检查和暂存区 clean-checkout 模拟。
- 2026-08-25：禁止 pre-commit 写入 Python 字节码，保持 hook 只检查、不产生工作区副作用。
- 2026-08-25：状态变更：todo → ready。原因：实现边界、依赖、修改路径和客观完成标准已明确
- 2026-08-25：状态变更：ready → doing。原因：开始修复链接目标跟踪校验并接入 hook 与 CI
- 2026-08-25：状态变更：doing → done。原因：实现、单元测试、本地检查和 clean-checkout 模拟均已完成
