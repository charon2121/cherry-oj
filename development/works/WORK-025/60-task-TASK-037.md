---
id: "TASK-037"
type: "task"
title: "实现题目草稿与版本管理"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "EXPERIENCE-013", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "TASK-034"]
related: []
implements: ["FEATURE-007#REQ-013", "FEATURE-007#REQ-014", "FEATURE-007#REQ-015", "FEATURE-007#REQ-016", "FEATURE-007#REQ-017", "FEATURE-007#REQ-025", "FEATURE-007#REQ-026"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/data-model.md", "docs/database-design.md", "contracts/web-api.openapi.json", "apps/server/problem-service", "development/works/WORK-025"]
write_paths: ["apps/server/problem-service", "development/works/WORK-025"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-037：实现题目草稿与版本管理

## 任务目标

在 problem-service 实现 ADMIN 可用的 C++ ACM Problem/Version/Sample/Language 草稿、修订、预览、删除和
归档领域，为测试数据和发布任务提供稳定状态机与乐观锁边界。

## 依据

落实 front matter 的 FEATURE 要求，严格遵守既有表结构、DESIGN-019 和批准后的 DECISION-014；不在本
任务实现测试资产、judging 调用或最终发布。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- ADMIN internal Controller/DTO、Problem/Version application/domain service、MyBatis mapper 与审计。
- 创建 Problem+v1 DRAFT、管理列表/详情、整体保存样例和 cpp starterCode、预览、删除未引用草稿、归档。
- 从已发布版本复制新 DRAFT，递增 versionNo；显式决定是否复用 testDataVersionId。
- rowVersion/状态条件更新、字段/Markdown/数组限制、slug 唯一冲突与稳定内部错误。
- 单元、MockMvc、MySQL 并发/事务/权限/审计测试。

## 完成标准

- [x] ADMIN 创建原子产生 Problem 与 v1 DRAFT；失败无半记录，slug 并发只有一个成功。
- [x] 只有未发布状态可改，samples ordinal 连续、cpp/ACM 模板规则正确，旧 rowVersion 409 且不覆盖。
- [x] 预览只经 ADMIN DTO 返回并 no-store；USER/匿名/首次改密主体均拒绝。
- [x] 删除只允许未发布且未引用草稿并按依赖顺序审计；归档不删历史且公开查询立即不可见。
- [x] 删除最后一个未发布草稿后保留 Problem 容器并允许重新创建 DRAFT；删除审计不建立指向已删版本的 FK。
- [x] 从 PUBLISHED 创建修订复制允许字段、versionNo 唯一递增，源版本保持不可变。
- [x] 测试与全后端 verify 通过，不修改测试数据内容、judging 或公开读语义。

## 验证

用 MockMvc/MySQL 8.4 覆盖 create/save/preview/delete/archive/revise、字段边界、rowVersion 两窗口、slug/
versionNo 并发、事务故障和审计 allowlist；执行 problem-service 模块与后端全量 verify、diff 路径检查。

## 风险

风险是让草稿规则污染 PUBLISHED 不可变性或删除历史。若需要改变表、支持 CORE/多语言、保存参考源码、
级联删除或跨服务事务，停止并升级设计。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全草稿/版本领域、事务、乐观锁、审计与不可变验收，等待批准。
- 2026-08-30：状态变更：todo → ready。原因：problem-service 读取基础已完成，开始题目草稿、版本、样例、语言与审计领域
- 2026-08-30：状态变更：ready → doing。原因：题目草稿、版本、样例、语言、审计及管理接口已实现，进入全后端回归验证
- 2026-08-30：状态变更：doing → done。原因：草稿与版本管理完成；MySQL 并发/事务、权限、审计及 72 项全后端回归均通过
