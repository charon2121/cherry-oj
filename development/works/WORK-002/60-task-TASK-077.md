---
id: "TASK-077"
type: "task"
title: "持久化正式提交并接收判题结果"
status: "done"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["PLAN-002", "TASK-076"]
related: []
implements: ["FEATURE-001#REQ-001", "FEATURE-001#REQ-002", "FEATURE-001#REQ-004", "FEATURE-001#REQ-005", "FEATURE-001#REQ-006", "FEATURE-001#REQ-007", "FEATURE-001#REQ-009", "FEATURE-001#REQ-011", "FEATURE-001#REQ-012", "FEATURE-001#REQ-013", "FEATURE-001#REQ-014", "FEATURE-001#AC-003", "FEATURE-001#AC-005", "FEATURE-001#AC-006", "FEATURE-001#AC-007", "FEATURE-001#AC-008", "FEATURE-001#AC-009"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "docs/architecture.md", "docs/data-model.md", "development/works/WORK-002", "contracts", "apps/server/submission-service", "apps/server/problem-service", "apps/server/judging-service", "apps/server/identity-security-support", "apps/server/pom.xml", "AGENTS.md", "development/README.md", "apps/server/TOOLCHAIN.md", "apps/server/mvnw", "apps/server/.mvn"]
write_paths: ["apps/server/TOOLCHAIN.md", "apps/server/submission-service", "development/works/WORK-002"]
forbidden_paths: ["contracts", "apps/server/user-service", "apps/server/problem-service", "apps/server/judging-service", "apps/server/gateway-service", "apps/server/identity-security-support", "apps/web", "apps/judge-engine", "apps/server/data", "compose.yaml"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-077：持久化正式提交并接收判题结果

## 任务目标

新增受用户身份保护的提交创建、本人查询和按幂等键恢复；原子保存输入与待发消息，并安全接收最终结果。

意图闸已由负责人签署，按明确依赖与以下边界实施；阶段证据见执行记录和 VERIFY。

## 依据

FEATURE-001 的本轮范围；DESIGN-002 的接口与安全约束；PLAN-002 的顺序和回退。`implements` 精确列出负责要求。

## 可查看范围

以 front matter 的 read_paths 为硬边界；实施前运行 `scripts/work context TASK-077`，并按语言读取工程规范。

## 可修改范围

以 front matter 的 write_paths 为硬边界，只允许为本任务目标修改；新路径表示计划新增，不代表文件已存在。
工作文档仅更新本任务执行记录和相关验证证据。契约或产品行为变化必须先升级上游，不能用记录替代批准。

## 禁止修改

以 front matter 的 forbidden_paths 为硬边界。禁止读取/改动真实密钥及运行数据，禁止部署、清库、删卷、自动签闸。
没有列入可写范围的文件也不允许写；确需越界时先更新 DESIGN/PLAN/任务边界并写明原因。

## 依赖

以 depends_on 为准。PLAN 中的验收/部署前置条件同样适用；不跳过失败的依赖测试。

## 产出

本任务目标对应的契约或代码、必要测试、实际执行记录；不重做已交付能力。

## 完成标准

- [x] 仅在本服务增加必要 MySQL/Flyway/Kafka 依赖与配置，新增五类表及唯一键；同事务写入提交、JudgeInput、请求键、Outbox。
- [x] 已存在同键同摘要先返回旧提交；不同摘要 409；首次创建核对当前 snapshot 与 expected 版本。并发请求以唯一约束收敛。
- [x] 公开查询只允许本人，ADMIN 无越权例外；内部 JudgeInput 只允许 judging 服务身份，响应不缓存。
- [x] Outbox 可重启恢复；Inbox 与状态更新原子，完成可先到、重复无副作用、终态不可回退。
- [x] 公开 allowlist 二次校验，日志不记录源码/输出/凭据；测试真实数据库并发、事务中断与消息重复。

## 验证

在 apps/server 执行 ./mvnw -pl submission-service -am test；使用隔离 MySQL/Kafka 验证事务和消息，不操作现有数据库。
命令为未来实施计划，尚未执行；记录工具链、环境、退出码、失败及重跑原因。只在适用范围内运行检查。

## 风险

接口、权限、事件内容或数据所有权不符 DESIGN 时先停下调整方案。目录边界较宽处受任务目标进一步约束，
禁止借机抽象通用框架、修改既有账号/题目/节点行为。回归失败交还原责任任务。

## 执行记录

- 2026-09-07：完成文档拆分与边界整理，status=todo；未实施，未运行本任务的业务测试。
- 2026-09-07：状态变更：todo → ready。原因：TASK-076 完成，正式提交的两个内部快照已可用
- 2026-09-07：状态变更：ready → doing。原因：开始提交事务、幂等与消息持久化实现

- 2026-09-07：按 Java 工程规范补充 apps/server/TOOLCHAIN.md 写入边界，仅同步本模块新增依赖、配置和验证说明，不改变产品范围。

- 2026-09-07：提交事务、原始源码与 JudgeInput 冻结、请求幂等、本人查询、Outbox/Inbox 完成。Maven 模块测试通过；隔离 MySQL 覆盖八路并发、回滚、旧请求重放与乱序，真实 Kafka 覆盖发送/回传与重复结果。补齐显式 Flyway 初始化；测试容器关闭前关闭 Spring 应用，重跑正常退出。
- 2026-09-07：状态变更：doing → done。原因：提交持久化和幂等恢复完成，MySQL/Kafka 验证及安全结果消费通过
