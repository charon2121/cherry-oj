---
id: "VERIFY-033"
type: "verify"
title: "修复 WORK-031 遗留的 CI 测试断言"
status: "approved"
work: "WORK-032"
owners: ["codex/root"]
depends_on: ["TASK-050"]
related: ["WORK-031", "VERIFY-032"]
implements: []
verifies: ["ISSUE-008#AC-001", "ISSUE-008#AC-002", "ISSUE-008#AC-003", "ISSUE-008#AC-004", "ISSUE-008#AC-005", "TASK-050"]
tags: []
result: "pass"
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# VERIFY-033：修复 WORK-031 遗留的 CI 测试断言

## 验证对象

验证 TASK-050 是否只迁移两处过期测试锚点，并让 WORK-031 对题库页面构成和 status 删除的已确认行为
继续成立；不重新设计或验收 WORK-031。

## 对应要求

逐条覆盖 ISSUE-008 的 AC-001～AC-005，并验证 TASK-050 的全部完成标准。

## 检查与结果

2026-09-02 在 macOS 本地工作区运行：

- `python3 scripts/work_test.py -q` 与 `scripts/work check`：退出码 0，251 份开发文档通过校验；
- 逐个 `json.load` 读取 `contracts/*.json`：全部可解析；
- `python3 scripts/contracts_test.py`：9 项全部通过；
- `cd apps/web && npm run check`：设计系统、OpenAPI 生成、format、lint、typecheck 与 109 项 Vitest
  全部通过；
- `npm run build` 与 `npm run storybook:build`：生产包和 Storybook 静态构建完成；
- `npm run test:e2e -- e2e/problems.spec.ts`：题库 3 项通过；`npm run test:e2e`：Chromium 30 项全部
  通过，旧 CI 中失败的 URL 筛选恢复用例本次通过；
- `git diff --check`：退出码 0；禁止路径差异为空。

构建只出现既有的大 chunk、Node experimental/deprecation 和 `NO_COLOR` 提示，没有失败或新增产物进入
版本差异。

提交 `93aeef0` 推送到 `origin/main` 后，[GitHub Actions run 33631033143](https://github.com/charon2121/cherry-oj/actions/runs/33631033143)
结论为 success：原失败的 `web (React SPA)` 与 `contracts (结构与边界)` 均完整通过；Go、开发文档、
`go.mod` 整洁度和容器联调另外 4 个 job 也全部通过。

## 未通过项

首次在受限沙箱内运行 Playwright 时，Vite preview 绑定 `127.0.0.1:4173` 返回 `EPERM`；使用同一命令
在获准的非沙箱执行环境复跑后，题库 3 项和完整 30 项均通过，判定为执行环境限制而非测试失败。

## 范围检查

`git diff --name-only` 与未跟踪文件确认改动仅涉及 `apps/web/e2e/problems.spec.ts`、
`scripts/contracts_test.py`、WORK-032 目录及工具生成的 `development/WORKS.md`、`development/index.json`。
对 `.github`、`contracts`、`apps/web/src`、`apps/server`、`apps/judge-engine`、`docs` 和 WORK-031 的定向
差异为空；没有实现偏差。

## 遗留问题

暂无。

## 剩余风险

本地与 GitHub Linux runner 均已通过，没有已知技术风险。未来若题库筛选入口或公开题库接口有意删除，
应在对应产品工作中同步迁移测试锚点，不能再次遗留旧资产断言。

## 结论

TASK-050 在批准边界内完成，ISSUE-008 的 AC-001～AC-005 均通过本地 CI 等价验证，结论为 pass，提交
人工验收；自动检查不能代替验收闸签署。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：contracts 9 项、Web 单测 109 项、Playwright 30 项、构建与路径检查全部通过，提交人工验收
- 2026-09-02：验收闸通过：review → approved。原因：确认修复已提交推送，远端 CI 全部通过
