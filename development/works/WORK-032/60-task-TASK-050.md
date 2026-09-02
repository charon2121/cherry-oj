---
id: "TASK-050"
type: "task"
title: "修复 WORK-031 遗留的 CI 测试断言"
status: "done"
work: "WORK-032"
owners: ["codex/root"]
depends_on: ["ISSUE-008", "DESIGN-025"]
related: ["WORK-031", "TASK-049", "VERIFY-032"]
implements: ["ISSUE-008#AC-001", "ISSUE-008#AC-002", "ISSUE-008#AC-003", "ISSUE-008#AC-004", "ISSUE-008#AC-005"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", ".github/workflows/ci.yml", "docs/engineering/typescript.md", "docs/engineering/conventions.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/e2e/problems.spec.ts", "apps/web/src/features/problems/components/problem-list-page.tsx", "contracts/web-api.openapi.json", "scripts/contracts_test.py", "development/works/WORK-031", "development/works/WORK-032"]
write_paths: ["apps/web/e2e/problems.spec.ts", "scripts/contracts_test.py", "development/WORKS.md", "development/index.json", "development/works/WORK-032"]
forbidden_paths: [".github", "contracts", "apps/web/src", "apps/web/design-system", "apps/server", "apps/judge-engine", "docs", "development/schema", "development/templates", "development/works/WORK-031"]
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# TASK-050：修复 WORK-031 遗留的 CI 测试断言

## 任务目标

在不修改运行时代码或公开契约的前提下，迁移 WORK-031 遗留的两处旧测试锚点，使 Web 与 contracts
两个 CI job 恢复通过，并保留原有行为与合同覆盖。

## 依据

仅依据 ISSUE-008 的 AC-001～AC-005 与 DESIGN-025。若修复必须恢复可见介绍标题、`/api/status`、
修改 OpenAPI/生产代码/CI workflow，或发现当前实现本身不符合 WORK-031，应停止并升级方案。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

实现文件只允许修改两处：`apps/web/e2e/problems.spec.ts` 与 `scripts/contracts_test.py`；WORK-032 文档
只用于记录执行和验证证据。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

尤其禁止修改 `contracts/web-api.openapi.json`、任何 `apps/web/src` 生产实现、CI workflow、设计系统、
服务端代码和 WORK-031 的已验收记录。

## 依赖

以 front matter 的 `depends_on` 为准。

ISSUE-008 经人工意图闸确认且 DESIGN-025 通过结构校验后，TASK 才能进入 ready/doing。

## 产出

- 题库 E2E 使用关键词筛选器作为页面可操作状态锚点，保留其余 URL、链接、安全与移动端断言。
- contracts 测试改用 `/api/problems`、`ProblemListSuccess` 和 `ApiMeta` 检查原有通用合同。
- VERIFY-033 记录本地两个失败 job 的完整等价验证与范围检查；若后续获得推送授权，再补远端 CI 证据。

## 完成标准

- [x] ISSUE-008 的 AC-001～AC-005 全部有可复现证据。
- [x] `problems.spec.ts` 不再引用“选择下一道题”，完整 Playwright 30 项全部通过。
- [x] `contracts_test.py` 不再引用 `/api/status` 或 `SystemStatusSuccess`，全部 9 项通过。
- [x] Web `check`、生产构建、Storybook 构建与 E2E 全部通过。
- [x] `contracts/*.json` 可解析，契约结构测试全部通过。
- [x] `git diff --check` 通过，差异只落在 TASK-050 的 write paths，生产代码、公开契约和 CI 配置无变化。

## 验证

在仓库根目录执行：

```text
python3 scripts/work_test.py
scripts/work check
python3 scripts/contracts_test.py

cd apps/web
npm run check
npm run build
npm run storybook:build
npm run test:e2e

cd ../..
git diff --check
git diff --name-only
```

另外按 CI 的 contracts 解析步骤读取每个 `contracts/*.json`，确认均为合法 JSON；核对差异中不存在
`.github/`、`contracts/`、`apps/web/src/` 或其它禁止路径。

## 风险

改动很小，但不能以“让 CI 变绿”为由删除有意义的断言。若完整 E2E 暴露新的页面故障、契约测试发现
实际 schema 缺少通用不变量，或需要扩大到生产实现，应停止 TASK-050 并更新 ISSUE/DESIGN 后重新审核。

## 执行记录

- 2026-09-02：创建任务。
- 2026-09-02：状态变更：todo → ready。原因：WORK-032 意图闸已由用户签署，TASK-050 边界与依赖满足
- 2026-09-02：状态变更：ready → doing。原因：开始迁移 Web E2E 与 contracts 两处过期断言
- 2026-09-02：题库 E2E 改以关键词筛选器判断页面可操作状态；契约测试改以公开题库成功响应和
  `ApiMeta` 检查原有信封、header 与 request ID 不变量，未修改生产实现或 OpenAPI。
- 2026-09-02：contracts 9 项、Web 单测 109 项、Playwright 30 项、生产与 Storybook 构建、工作文档、
  JSON 解析、格式和路径边界均通过，验证证据写入 VERIFY-033。
- 2026-09-02：提交 `93aeef0` 推送到 `origin/main`；GitHub Actions run `33631033143` 的 6 个 job
  全部通过，其中原失败的 Web 与 contracts job 已恢复。
- 2026-09-02：状态变更：todo → ready。原因：WORK-032 意图闸已由用户签署，TASK-050 边界与依赖满足
- 2026-09-02：状态变更：ready → doing。原因：开始迁移 Web E2E 与 contracts 两处过期断言
- 2026-09-02：状态变更：doing → done。原因：两处过期断言已迁移，Web 与 contracts 完整 CI 等价检查及范围门禁全部通过
