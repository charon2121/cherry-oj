---
id: "ISSUE-005"
type: "issue"
title: "修复题目管理默认筛选返回 500"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: []
related: ["TASK-035", "TASK-036", "VERIFY-025"]
implements: []
verifies: []
tags: []
created_at: "2026-08-31"
updated_at: "2026-08-31"
---



# ISSUE-005：修复题目管理默认筛选返回 500

## 问题现象

ADMIN 进入题目管理列表时，页面请求 `GET /api/admin/problems` 后显示“服务暂时不可用”。
用户提供的错误响应为 500 `INTERNAL_ERROR`，request ID 是
`req_5e5299139b5742838ff6d55cc511e01c`。

## 复现方式

1. 以 ADMIN 身份登录 Web。
2. 打开 `/admin/problems`；路由默认 search 为 `page=1&q=&status=ALL`。
3. Web 请求 `/api/admin/problems?page=1&size=20&status=ALL`。
4. 查看 Gateway 响应和 `gateway-service.log`。

## 实际结果

Gateway 返回 500 `INTERNAL_ERROR`。同一 request ID 的日志记录
`jakarta.validation.ConstraintViolationException`，异常发生在 Gateway 方法参数校验阶段，请求尚未进入
problem-service。

## 预期结果

默认“全部状态”只是 Web 界面选项，应通过不发送 `status` 表示；Gateway 收到不符合现有
OpenAPI 约束的直接请求时，应返回稳定的 422 `VALIDATION_FAILED`，不得伪装成服务端
故障。合法的 `ACTIVE` 和 `ARCHIVED` 筛选语义保持不变。

## 影响与条件

影响 ADMIN 题目管理列表的默认入口，使题目管理和测试数据管理工作台无法开始使用。
公开题库、题目详情、题目与测试数据数据库、problem-service 和已有 OpenAPI 契约不需要改动。

## 原因

Web 路由把 `ALL` 当作界面内的“不限状态”哨兵值，但 `listAdminProblems` 把它原样编码为
Gateway 查询参数。`AdminProblemsController` 依照契约只允许 `ACTIVE|ARCHIVED`，因此抛出
`ConstraintViolationException`。`ApiProblemHandler` 只处理了 request body 的
`WebExchangeBindException`，没有处理方法参数约束异常，导致它落入 `Throwable` 的 500 兜底。

## 修复方向

1. 在 Web API 边界将 `ALL` 归一化为缺省 `status`，并用类型和测试固定这一语义。
2. Gateway 将 `ConstraintViolationException` 映射为与已有 Bean Validation 一致的 422
   `VALIDATION_FAILED`，返回脱敏的字段违例和一致 request ID。
3. 增加 Web 请求构造、Gateway 合法/非法查询参数及默认管理列表的回归测试。

## 回归检查

- AC-001：从 `/admin/problems` 以默认 `q=""`、`status="ALL"`、`page=1` 进入时，Web 请求不含
  `q` 和 `status`，Gateway 返回题目列表而不是 500。
- AC-002：非空 `q` 以及 `ACTIVE`/`ARCHIVED` 会原样进入查询参数，分页参数和现有列表响应
  不变。
- AC-003：直接请求 `status=ALL` 或其他违反 controller 约束的查询参数时，Gateway 返回
  422 `VALIDATION_FAILED` 和字段违例，不返回 `INTERNAL_ERROR`。
- AC-004：校验错误响应的 `X-Request-Id`、`instance` 和 `meta.requestId` 一致，且不包含内部异常类名。
- AC-005：Gateway 模块测试、后端全量验证、Web check/build 和管理列表回归场景通过；
  problem-service、数据库和 OpenAPI 无变更。

## 变更记录

- 2026-08-31：根据 request ID 定位 Gateway 方法参数校验异常，确认 Web `ALL` 归一化缺失与
  Gateway 错误映射缺口，提交修复范围审核。
- 2026-08-31：状态变更：draft → review。原因：已根据用户提供的 requestId 确认 Web ALL 哨兵值与 Gateway ConstraintViolationException 映射缺口，修复范围和五项验收标准已明确，提交审核
- 2026-08-31：状态变更：review → approved。原因：负责人确认 ISSUE-005/TASK-042 文档并明确允许实施
