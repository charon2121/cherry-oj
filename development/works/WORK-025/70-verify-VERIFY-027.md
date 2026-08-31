---
id: "VERIFY-027"
type: "verify"
title: "验证题目管理列表参数修复"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["TASK-042"]
related: []
implements: []
verifies: ["ISSUE-005", "TASK-042"]
tags: []
result: "pass"
created_at: "2026-08-31"
updated_at: "2026-08-31"
---


# VERIFY-027：验证题目管理列表参数修复

## 验证对象

验证 Web 对 ADMIN 题目列表筛选的归一化、Gateway 方法参数校验错误映射，以及默认
进入题目管理时不再返回 500 的端到端回归。

## 对应要求

覆盖 `ISSUE-005` AC-001～AC-005 与 `TASK-042` 全部完成标准。

## 检查与结果

验证环境为 Java 21.0.12.1、Spring Boot 4.1.0、Node v26.3.0、npm 12.0.2、Chromium、Docker Desktop
与 Testcontainers MySQL 8.4/Redis 7.4。

- `npm test -- --run src/features/problems/api/problems-api.test.ts`：4 项通过。新增断言证明
  `listAdminProblems('', 'ALL', 1)` 只生成 `page=1&size=20`，非空 q 和 ARCHIVED 保持在 URL。
- `./mvnw -pl gateway-service -am -Dtest=ApiProblemHandlerTests -Dsurefire.failIfNoSpecifiedTests=false test`：
  17 项通过；Pattern/Size/Min/Max 分别产生 PATTERN/SIZE/MIN/MAX 字段违例，均为 422
  `VALIDATION_FAILED`，header/body request ID 一致且无异常类名泄漏。
- `./mvnw -pl gateway-service -am test`：Gateway 46 项通过，其中 Redis Session Testcontainers 3 项通过。
- `./mvnw clean verify`：7 个模块全部 BUILD SUCCESS；Gateway 46、user-service 23、problem-service 34、
  submission-service 2、judging-service 15，共 120 项，119 通过、1 项既有真实 Linux Judge 条件测试跳过。
- `npm run check`：设计系统、API 生成漂移、Prettier、ESLint、TypeScript 均通过；30 个 Vitest
  文件的 105 项测试通过。
- `npm run build`：通过；仅保留已知 ADMIN Monaco chunk 大小警告。
- `npm run test:e2e -- e2e/problems.spec.ts`：Chromium 3 项通过；ADMIN 从带 `status=ALL&q=` 的页面
  URL 进入时，实际 API 请求不含 q/status，工作台正常显示。
- `scripts/work check`：208 份文档通过；`git diff --check` 通过。

## 未通过项

暂无。

## 范围检查

TASK-042 实施只修改 Gateway 的 Problem handler/测试、Web problems API/测试/E2E 和 WORK-025
文档。工作区原已存在 WORK-025/026 其它未提交改动，本 TASK 保留它们且未改 contracts、
problem-service、其它服务、数据库或题目/测试数据。

## 遗留问题

暂无。

## 剩余风险

未在用户当前长时运行的 Gateway 进程中使用原 request ID 重放；实施证据来自真实 Chromium、
Web 预览服务、Spring Gateway 模块测试及全后端 Testcontainers。部署前需重启 Gateway 和 Web 进程
以加载新构建。

## 结论

通过。ISSUE-005 AC-001～AC-005 和 TASK-042 完成标准均有自动化证据；默认题目管理列表
不再把 `ALL` 发往 Gateway，直接非法查询也不再被映射为 500。

## 变更记录

- 2026-08-31：状态变更：draft → approved。原因：ISSUE-005 AC-001～AC-005 与 TASK-042 完成标准均有自动化和 Chromium 回归证据，验证通过
