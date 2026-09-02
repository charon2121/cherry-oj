---
id: "ISSUE-008"
type: "issue"
title: "修复 WORK-031 遗留的 CI 测试断言"
status: "approved"
work: "WORK-032"
owners: ["codex/root"]
depends_on: []
related: ["WORK-031", "TASK-049", "VERIFY-032"]
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# ISSUE-008：修复 WORK-031 遗留的 CI 测试断言

<!--
本节面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。报错原文、依赖坐标、字段、类、框架、协议、表名、路径和
命令从下一节开始再出现。
-->

## 为什么做

上一批页面调整已经通过人工验收，但提交到主分支后，两项自动检查仍按调整前的页面和接口判断结果，
所以主分支显示为失败。实际页面和接口已经按照确认过的新规则工作，本次需要让检查标准与新规则一致，
避免真正的后续问题被这两条已知误报遮住。

## 问题现象

提交 `56150e1` 推送后，GitHub Actions run `33629041648` 中 `web (React SPA)` 与
`contracts (结构与边界)` 两个 job 失败；Go、开发文档、`go.mod` 整洁度和容器联调均通过。

## 复现方式

在包含提交 `56150e1` 的干净工作区执行：

```text
cd apps/web
npm run test:e2e

cd ../..
python3 scripts/contracts_test.py
```

## 实际结果

Web E2E 共 30 项，29 项通过，`apps/web/e2e/problems.spec.ts:67` 在重试两次后仍找不到已经按
FEATURE-009 移除的可见标题“选择下一道题”。契约测试共 9 项，7 项通过，另外两项分别读取已删除的
`SystemStatusSuccess` 与 `/api/status`，触发 `KeyError`。

## 预期结果

测试应验证 WORK-031 已确认的新行为：题库页面直接从筛选任务开始，同时保留可访问的页面语义；公开
Web 契约继续使用成功信封、`X-Request-Id` 与 Problem Details，但不恢复 `/api/status` 或
`SystemStatusSuccess`。两项 CI 等价命令均应通过。

## 影响与条件

问题只影响自动测试与主分支健康状态，不影响运行时页面、Gateway 或数据。只要 CI 在包含 WORK-031
改动但仍使用旧断言的提交上运行，就会稳定复现；不是概率性业务故障。

## 原因

TASK-049 已允许并完成 `apps/web/e2e` 调整，但验证时只运行了 27 项定向 Playwright 用例，没有覆盖
`problems.spec.ts` 中这条旧标题断言；契约结构测试 `scripts/contracts_test.py` 不在 TASK-049 的写入
边界，也没有进入该工作记录的本地验证命令。公开契约删除正确完成，而两处消费者测试没有同步迁移。

## 修复方向

1. 将题库 E2E 的页面就绪断言改为 WORK-031 保留的首个真实任务组件，不再要求已禁止的可见介绍标题。
2. 将契约测试中的成功信封与 request ID 样例断言迁移到现存的公开题库接口和通用 `ApiMeta`，继续
   保留 Problem Details 检查。
3. 不修改生产代码、OpenAPI、CI workflow 或 WORK-031 的已验收记录；运行两个失败 job 的完整本地
   等价检查并核对差异范围。

## 回归检查

- AC-001：`npm run test:e2e` 全部通过，题库用例不再引用“选择下一道题”，并验证关键词筛选器可见、
  URL 中的“两数”正确恢复。
- AC-002：`python3 scripts/contracts_test.py` 全部通过；成功信封仍要求 `data`、`meta` 与
  `X-Request-Id`，成功/失败样例中的 request ID 关联仍被检查。
- AC-003：仓库内不恢复 `/api/status`、`SystemStatusSuccess` 或首页连通状态组件，OpenAPI 和生产代码
  均无改动。
- AC-004：Web `check`、`build`、`storybook:build`、完整 E2E，以及 contracts JSON 解析与结构测试
  全部通过。
- AC-005：实际差异只包含两处测试实现、WORK-032 文档及工作索引生成文件，不修改 CI workflow 或
  WORK-031 的已确认结论。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已记录 CI 复现、根因、最小修复方向与五项验收标准，提交人工审核
- 2026-09-02：意图闸通过：review → approved。原因：确认按最小范围同步两处过期测试断言
