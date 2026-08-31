---
id: "TASK-042"
type: "task"
title: "修复题目管理列表参数归一化与校验错误映射"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["ISSUE-005", "TASK-035", "TASK-036"]
related: []
implements: ["ISSUE-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts/web-api.openapi.json", "apps/server/gateway-service", "apps/web/src", "apps/web/e2e"]
write_paths: ["apps/server/gateway-service", "apps/web/src", "apps/web/e2e", "development/works/WORK-025"]
forbidden_paths: ["apps/server/problem-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "contracts"]
created_at: "2026-08-31"
updated_at: "2026-08-31"
---




# TASK-042：修复题目管理列表参数归一化与校验错误映射

## 任务目标

修复 ADMIN 题目列表默认请求：Web 只把后端可接受的筛选值编码到 URL，Gateway 将方法参数
校验失败返回稳定 422 Problem Detail，避免用户输入错误被报告为服务端 500。

## 依据

实现 `ISSUE-005` AC-001～AC-005，继续遵守 `FEATURE-007`、`DESIGN-019` 和现有
`contracts/web-api.openapi.json`。`ALL` 是 Web 界面状态，不扩展后端的 ProblemStatus 枚举。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- Web 管理列表查询参数的边界类型与归一化：空 `q` 和 `ALL` 不进入 API URL。
- Gateway `ConstraintViolationException` 的统一 422 Problem Detail 映射，字段路径和约束代码可诊断但
  不泄漏内部实现。
- Web API 单测、Gateway handler/controller 回归测试，以及默认 ADMIN 列表的浏览器场景。
- `VERIFY-027` 的实际命令、结果和范围证据。

## 完成标准

- [x] `listAdminProblems('', 'ALL', 1)` 生成的 URL 只含 `page=1&size=20`。
- [x] `ACTIVE`/`ARCHIVED` 和非空 `q` 保持原有查询语义与 Query key 隔离。
- [x] Gateway 对方法参数的 `Pattern`/`Size`/`Min`/`Max` 违例返回 422
  `VALIDATION_FAILED`，字段 path/code/message 稳定。
- [x] 校验错误保持 `application/problem+json` 和 header/body request ID 一致，不进入 500 兜底。
- [x] 不修改 OpenAPI、problem-service、数据库、题目数据或测试数据资产。

## 验证

至少执行 Gateway 模块测试和后端 `clean verify`，Web 的 `npm run check`、`npm run build` 及题目管理
默认列表浏览器回归。测试同时覆盖默认 `ALL`、两个合法状态、直接非法状态、边界分页
参数和 request ID 一致性。

## 风险

主要风险是把前端哨兵值扩展成公开契约，或用过宽异常捕获改变真实服务端错误的 500 语义。
处理器必须只匹配 Bean Validation 的方法参数违例；若发现后端需要接受 `ALL`、需要改变错误 schema
或问题查询语义，停止并升级契约/设计，不在本 TASK 扩范围。

## 执行记录

- 2026-08-31：创建任务。
- 2026-08-31：负责人批准 ISSUE-005/TASK-042 并允许实施；将 ISSUE 置为 approved，TASK 从
  todo 经 ready 进入 doing。
- 2026-08-31：Web 在 `listAdminProblems` 边界把 `ALL` 归一化为缺省 `status`，并使用生成
  `ProblemStatus | 'ALL'` 类型限制筛选输入；单测固定默认和合法筛选 URL。
- 2026-08-31：Gateway 新增 `ConstraintViolationException` 的 422 Problem Detail 映射，从约束注解
  生成稳定 code，从方法路径只暴露字段名；回归覆盖 Pattern/Size/Min/Max 和 request ID。
- 2026-08-31：Web check 30 个文件/105 项测试、build 与 problems Playwright 3 项通过；Gateway
  46 项通过；后端 7 模块 clean verify 共 120 项，119 通过、1 项既有真实 Judge 条件测试跳过。
- 2026-08-31：状态变更：todo → ready。原因：ISSUE-005 已获批准，既有 Gateway/Web 依赖任务已完成
- 2026-08-31：状态变更：ready → doing。原因：开始修复题目管理列表参数归一化与 Gateway 校验错误映射
- 2026-08-31：状态变更：doing → done。原因：Web ALL 参数归一化、Gateway ConstraintViolationException 422 映射及两端回归均完成，Web check/build/E2E 与后端全量 clean verify 通过
