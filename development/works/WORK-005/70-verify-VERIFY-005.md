---
id: "VERIFY-005"
type: "verify"
title: "修复开发文档 CI 的 clean checkout 链接校验"
status: "approved"
work: "WORK-005"
owners: ["codex/root"]
depends_on: ["TASK-005"]
related: []
implements: []
verifies: ["ISSUE-001", "TASK-005"]
tags: []
result: "pass"
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# VERIFY-005：修复开发文档 CI 的 clean checkout 链接校验

## 验证对象

`ISSUE-001` 定义的本地/CI 链接判定差异，以及 `TASK-005` 对文档、校验器、hook 和 CI 的修复。

## 对应要求

- ISSUE-001 / AC-001：链接校验器覆盖文件、目录与未跟踪目标。
- ISSUE-001 / AC-002：当前本地工作区不再被 `tutorial/` 掩盖失效链接。
- ISSUE-001 / AC-003：工作项检查与 GitHub Actions 开发文档 job 通过。

## 检查与结果

- 本地 macOS：`python3 scripts/docs_test_test.py`，3 个测试通过。
- 本地 macOS：`python3 scripts/docs_test.py`，65 份 Markdown 文档通过。
- 本地 macOS：`python3 scripts/work_test.py`，19 个测试通过。
- 本地 macOS：`scripts/work check`，37 份开发文档通过，0 个进行中提示。
- 本地 macOS：`sh -n .githooks/pre-commit` 与 `git diff --check` 通过。
- 本地 macOS：pre-commit 使用 `PYTHONDONTWRITEBYTECODE=1` 执行文档检查，结束后工作区不产生
  `scripts/__pycache__/`。
- 暂存区 clean-checkout 模拟：用 `git checkout-index` 导出到临时仓库并重新建立 Git 索引；链接校验器
  3 个测试、65 份 Markdown 校验和 37 份开发文档校验全部通过，环境中不包含本地 `tutorial/`。
- GitHub Actions：[CI 运行 32802018365](https://github.com/charon2121/cherry-oj/actions/runs/32802018365)
  全部通过；“开发文档系统”job 在 8 秒内完成，工作项工具测试、规则校验、链接校验器测试和全局/开发
  文档链接校验均通过。

## 未通过项

无。

## 范围检查

修改限定在 TASK-005 的 `write_paths`；未修改 `apps`、`contracts`、`tutorial`，没有实现偏差。

## 遗留问题

无。

## 剩余风险

无已知剩余风险。GitHub Actions 的 clean checkout 已覆盖原失败环境。

## 结论

通过。已知失败模式在本地、暂存区 clean-checkout 模拟和 GitHub Actions 三个环境中均已消除。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：本地、暂存区 clean-checkout 模拟与 GitHub Actions 运行 32802018365 均通过
