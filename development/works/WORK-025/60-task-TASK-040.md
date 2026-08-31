---
id: "TASK-040"
type: "task"
title: "实现题目发布编排与不可变切换"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "TASK-037", "TASK-038", "TASK-039"]
related: []
implements: ["FEATURE-007#REQ-021", "FEATURE-007#REQ-022", "FEATURE-007#REQ-023", "FEATURE-007#REQ-024", "FEATURE-007#REQ-025", "FEATURE-007#REQ-026", "FEATURE-007#REQ-027"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/backend.md", "docs/data-model.md", "docs/database-design.md", "contracts/web-api.openapi.json", "apps/server/problem-service", "apps/server/judging-service", "development/works/WORK-025"]
write_paths: ["apps/server/problem-service", "development/works/WORK-025"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-040：实现题目发布编排与不可变切换

## 任务目标

在 problem-service 编排绑定数据的部署、参考程序验证、publish-check 与最终本地原子发布，确保远程失败
只影响草稿，不产生半发布或覆盖历史。

## 依据

落实 FEATURE-007 REQ-021～027，消费 TASK-038 资产流和 TASK-039 judging API；遵守“外部调用不进本地
事务”和 `ProblemVersion` 发布不可变边界。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 有超时、流式、委托 ADMIN JWT 的 judging client 与内部 DTO，不共享业务实体。
- deploy、calibrate/validate、publish-check、publish application services 和稳定错误映射。
- DRAFT/VALIDATING/READY_FOR_REVIEW 状态条件更新、过期 VALIDATING 恢复、rowVersion/资源状态重试。
- 发布前本库检查与 readiness，最终 `SELECT ... FOR UPDATE` 写 PUBLISHED/current pointer/audit。
- 故障、并发、超时结果不明、重试与敏感参考源码/测试内容负向测试。

## 完成标准

- [x] 只有本题 READY 数据可部署/绑定；asset 流不进内存，ADMIN JWT/正文不进日志。
- [x] 验证期间状态可见；成功 READY_FOR_REVIEW，失败恢复 DRAFT；超时恢复不碰活跃操作。
- [x] publish-check 明确列出本库、deployment、calibration/readiness 缺项，不改变状态。
- [x] 发布远程检查在事务外；最终事务重查并原子写版本/指针/审计，任一步失败完全回滚。
- [x] 并发 publish 或超时重试只得到一个当前版本；旧公开版本、READY 数据和 calibration 不变。
- [x] PUBLISHED 后 update/delete 拒绝，新修订流程仍可用；测试与全后端 verify 通过。

## 验证

用 fake judging/asset 和 MySQL 8.4 覆盖部署/验证/readiness 每个故障点、VALIDATING crash recovery、并发
发布、状态/rowVersion/资源重试、事务回滚与 canary；再用 TASK-039 实服务做 A+B 集成。

## 风险

主要风险是把远程调用放进锁事务、检查后不重查本库、结果不明时重复发布或泄漏参考源码。若需分布式
事务、自动后台发布、服务身份、CORE 模板或修改 judging 所有权，停止并重审。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全部署/验证编排、状态恢复、本地发布事务和并发验收，等待批准。
- 2026-08-30：状态变更：todo → ready。原因：TASK-039 已完成，开始实现 problem-service 部署、校准与原子发布编排
- 2026-08-30：状态变更：ready → doing。原因：开始实现 problem-service 的 judging client、VALIDATING 状态机、publish-check 与原子发布
- 2026-08-30：实现有界超时、流式 multipart 与 ADMIN JWT/trace 委托的 judging client；安全映射下游
  400/409/413/5xx、超时、断连和非法/超限响应，不记录 token、参考源码或测试正文。
- 2026-08-30：实现部署绑定/hash 重查、DRAFT→VALIDATING→READY_FOR_REVIEW/DRAFT 条件更新、过期验证
  启动恢复和固定六项 publish-check；所有 judging 调用均在 problem 数据库事务外。
- 2026-08-30：实现发布前 readiness、本地最终锁重查与同事务 PUBLISHED/current pointer/audit 切换；已发布
  重试返回同一事实，并发竞态只有一个写者，历史版本与 READY 资产保持不可变。
- 2026-08-30：新增 6 项 MySQL 8.4 发布编排集成场景和 2 项真实 HTTP client 测试；problem-service
  34 项全通过。`./mvnw clean verify` 的 7 模块均成功，共 103 项测试、102 通过、1 项条件跳过。
- 2026-08-30：状态变更：doing → done。原因：judging 委托、验证状态机、六项发布检查与原子发布已实现，problem-service 34 项及全后端 103 项验证通过
