---
id: "ISSUE-001"
type: "issue"
title: "修复开发文档 CI 的 clean checkout 链接校验"
status: "approved"
work: "WORK-005"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# ISSUE-001：修复开发文档 CI 的 clean checkout 链接校验

## 为什么做

文档里的链接有一项自动检查，用来防止读者点到失效的链接。这项检查在开发者自己电脑上和在服务器上
看到的文件范围不一样：本地能看到一些不会进入项目仓库的私人材料，服务器上看不到。于是本地显示
通过、推送之后却失败，开发者要等推送完才知道链接是坏的。这破坏了「本地通过就应该推送也通过」
这个基本约定——检查一旦不可信，人就会开始绕过它。

## 问题现象

提交 `d451f5f` 的“开发文档系统”CI 在链接校验步骤失败，但相同命令在提交前的本地工作区通过。

## 复现方式

1. 保持 `.gitignore` 忽略 `tutorial/`，并在本地保留 `tutorial/README.md`。
2. 在受校验的 Markdown 中链接该文件。
3. 本地运行 `python3 scripts/docs_test.py`。
4. 在只包含 Git 跟踪文件的 clean checkout 中运行相同命令。

## 实际结果

本地因 `tutorial/README.md` 实际存在而通过；GitHub Actions clean checkout 不包含该文件，报告
`docs/engine.md` 第 6、722 行链接不存在。

## 预期结果

本地校验应当在提交前拒绝无法出现在 clean checkout 中的链接；全局文档也不应把本地材料描述成
所有克隆都可访问的链接。

## 影响与条件

只要被忽略或未跟踪的文件恰好存在于提交者本地，任何 `docs/`、`development/` 或文档入口中的链接
都可能复现。本次失败阻断 `main` 的 CI，但不影响产品运行时。

## 原因

`scripts/docs_test.py` 只用 `Path.exists()` 判断目标，没有确认目标是否由 Git 跟踪；本地环境因此掩盖
了 clean checkout 中必然缺失的文件。

## 修复方向

把两处教程链接改为明确的本地目录说明，并基于 `git ls-files` 校验文件或目录目标能否进入 clean
checkout；为文件、目录和未跟踪目标补充单元测试，并在 hook/CI 中执行。

## 回归检查

- AC-001：`python3 scripts/docs_test_test.py` 覆盖已跟踪文件、已跟踪目录和未跟踪文件。
- AC-002：`python3 scripts/docs_test.py` 在当前含本地 `tutorial/` 的工作区通过。
- AC-003：`scripts/work check` 及 GitHub Actions“开发文档系统”job 通过。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：CI 日志与 clean checkout 差异已复现，原因、范围和验收条件明确
