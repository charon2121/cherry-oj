---
id: "TASK-0007"
title: "统一微服务架构与数据模型"
type: "docs"
area: "architecture"
priority: "P0"
status: "done"
assignee: "codex/root"
depends_on: []
related: []
created_at: "2026-08-19T17:48:29+08:00"
updated_at: "2026-08-20T10:31:22+08:00"
claim_branch: "codex/task-0007"
claimed_at: "2026-08-19T17:49:57+08:00"
lease_until: "2026-08-20T17:49:57+08:00"
completed_at: "2026-08-20T10:31:22+08:00"
review_required: true
---

# TASK-0007：统一微服务架构与数据模型

## 背景

PRD 已确认题目版本、ACM/CORE、环境标定和提交快照，但现有设计文档仍混用两套架构：
`backend.md` 采用五个 Java 微服务与 Kafka 异步判题，`architecture.md`、`data-model.md` 和
`CLAUDE.md` 的部分内容仍描述单体 server 同步调用 judge。若不先统一，后续表归属、接口、Kafka
事件和 CORE 模板合并位置会由不同实现各自猜测，导致契约和数据所有权漂移。

## 目标

形成一套与 PRD 一致、可以直接指导 contracts v2 和 Java 服务初始化的微服务架构与数据模型。

## 范围

包含：

- 以 `backend.md` 的五个 Java 服务、Kafka 异步判题和 Go judge/sandbox 边界为基线。
- 明确实体、表和文件资产的服务所有权，禁止跨服务共享数据库或 JOIN。
- 统一 Submission、JudgeInput、JudgeTask、Outbox/Inbox 与判题生命周期。
- 明确 CORE 模板存储、合并、冻结和送判边界。
- 明确 TestDataVersion、TestDataDeployment、JudgeEnvironment、LanguageCalibration 的归属与交互。
- 同步 `architecture.md`、`data-model.md` 和 `CLAUDE.md` 中的新克隆核心拓扑说明。
- 记录后续 contracts v2 与实现任务需要遵守的依赖顺序。

不包含：

- 修改 `contracts/*.json`、Go 实现或创建 Java/TypeScript 工程。
- 实现 Kafka、数据库迁移、HTTP API 或前端页面。
- 决定生产编排、对象存储、搜索和可观测性供应商。

## 验收标准

- [x] 三份架构文档不再把单体同步 server 描述为现行实现基线。
- [x] 每个 MVP 核心实体都有唯一写入服务，跨服务读取路径明确。
- [x] Submission 创建、Kafka 判题、结果回写和重试幂等流程完整且无跨库事务。
- [x] CORE 用户源码、judgeTemplate 和最终 JudgeInput 的生成与历史追溯边界明确。
- [x] 数据版本部署、环境选择和语言绝对限制能形成一次可复现 JudgeInput。
- [x] 普通答题角色枚举统一为 `USER`，管理员为 `ADMIN`。
- [x] 文档明确下一步先改 contracts，再改 Go/Java，最后接 web。
- [x] `scripts/task check` 通过，文档代码块结构检查通过。

## 执行记录

- 2026-08-19T17:48:29+08:00：创建任务。
- 2026-08-19：以五个 Java 微服务和 Kafka 异步判题为基线，重写 architecture/data-model，
  同步 backend、PRD 与 CLAUDE 核心约定。
- 2026-08-19：验证 `scripts/task check` 通过；CLAUDE、PRD、architecture、backend、data-model 的
  Markdown 代码围栏均成对；全文冲突扫描无现行单体同步链路或旧角色枚举残留。
- 2026-08-19T17:49:57+08:00：codex/root 在分支 codex/task-0007 认领任务，租约 24 小时。
- 2026-08-19T18:05:48+08:00：实现与验证完成，进入 review。
- 2026-08-20T10:31:22+08:00：验收完成，任务关闭。

## 阻塞信息

无。

## 完成结果

设计已统一并通过评审。下一项实现工作应从 contracts v2 开始，不在本任务修改运行代码。

统一五服务数据所有权、Kafka 异步判题、CORE JudgeInput 冻结和环境标定链路；task check、diff check 与文档结构检查通过

用户确认继续下一步，架构与数据模型评审通过；远端提交 891f0b8，既有验证全部通过
