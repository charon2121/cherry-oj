---
id: "TASK-078"
type: "task"
title: "实现异步判题任务与故障恢复"
status: "done"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["PLAN-002", "TASK-077"]
related: []
implements: ["FEATURE-001#REQ-004", "FEATURE-001#REQ-006", "FEATURE-001#REQ-007", "FEATURE-001#REQ-011", "FEATURE-001#REQ-012", "FEATURE-001#REQ-014", "FEATURE-001#AC-003", "FEATURE-001#AC-004", "FEATURE-001#AC-005", "FEATURE-001#AC-008", "FEATURE-001#AC-009"]
verifies: []
tags: []
read_paths: ["apps/server/user-service/src/test/java/com/cherryoj/userservice/config/JavaServiceConfigurationDefaultsTests.java", "CLAUDE.md", "docs/engineering", "docs/architecture.md", "docs/data-model.md", "development/works/WORK-002", "contracts", "apps/server/judging-service", "apps/server/submission-service", "apps/server/identity-security-support", "apps/judge-engine/internal/contract", "apps/judge-engine/internal/judge", "development/works/WORK-040", "AGENTS.md", "development/README.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/mvnw", "apps/server/.mvn"]
write_paths: ["apps/server/user-service/src/test/java/com/cherryoj/userservice/config/JavaServiceConfigurationDefaultsTests.java", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/messaging/SubmissionMessaging.java", "contracts/web-api.openapi.json", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/messaging/SubmissionLifecycle.java", "apps/server/TOOLCHAIN.md", "apps/server/judging-service", "development/works/WORK-002", "contracts/execution-profile.schema.json", "contracts/judge-input.schema.json", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/api/SubmissionDtos.java", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/application/SubmissionService.java", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/integration/HttpSubmissionPrerequisites.java", "apps/server/submission-service/src/test/java/com/cherryoj/submissionservice/application/SubmissionPersistenceTests.java", "apps/server/submission-service/src/test/java/com/cherryoj/submissionservice/application/SubmissionKafkaTests.java"]
forbidden_paths: ["apps/server/user-service/src/main", "apps/server/problem-service", "apps/server/gateway-service", "apps/server/identity-security-support", "apps/web", "apps/judge-engine", "apps/server/data", "compose.yaml", "contracts/judge.schema.json", "contracts/run.schema.json", "apps/server/submission-service/src/main/resources"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-078：实现异步判题任务与故障恢复

## 任务目标

消费提交引用，持久化任务与尝试；调用匹配冻结配置的真实 Judge，投影安全结果并有界处理系统故障。

意图闸已由负责人签署，按明确依赖与以下边界实施；阶段证据见执行记录和 VERIFY。

## 依据

FEATURE-001 的本轮范围；DESIGN-002 的接口与安全约束；PLAN-002 的顺序和回退。`implements` 精确列出负责要求。

## 可查看范围

以 front matter 的 read_paths 为硬边界；实施前运行 `scripts/work context TASK-078`，并按语言读取工程规范。

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

- [x] 追加任务/尝试/Outbox/Inbox 迁移，不改已执行 V1/V2；请求去重、租约/续约/attempt fencing，外部 HTTP 不持有长事务。
- [x] 服务身份拉 JudgeInput，核验源码摘要、环境指纹和节点当前数据部署；环境切换不得改写或绕过旧输入。
- [x] 补齐 Java JudgeGateway 对 CE 诊断和 caseResults 的有限解析；向 lifecycle 写入前剥离隐藏输出/差异/名称/运行期消息。
- [x] 定义执行预算、排队截止、最多尝试次数与退避的正值配置；WA/CE/TLE 不重试，失去租约不能生效，重试耗尽 SE。
- [x] 真实 Kafka 验证重投、乱序、重启、旧尝试迟到、发布器中断和死信定位；真实 Go Judge 验证主要 verdict。

## 验证

在 apps/server 执行 ./mvnw -pl judging-service -am test；运行专属测试实例验证 Kafka 和 Linux Judge，完整脚本由 TASK-080 汇总。
实际命令、工具链、环境、结果及失败重跑原因见 VERIFY-002 最终技术验证与真实整链路记录。

## 风险

接口、权限、事件内容或数据所有权不符 DESIGN 时先停下调整方案。目录边界较宽处受任务目标进一步约束，
禁止借机抽象通用框架、修改既有账号/题目/节点行为。回归失败交还原责任任务。

## 执行记录

- 2026-09-07：完成文档拆分与边界整理，status=todo；未实施，未运行本任务的业务测试。

- 2026-09-07：按 Java 工程规范补充 apps/server/TOOLCHAIN.md 写入边界，仅同步本模块新增依赖、配置和验证说明，不改变产品范围。
- 2026-09-07：状态变更：todo → ready。原因：提交与消息边界已完成，开始真实 Judge 编排
- 2026-09-07：状态变更：ready → doing。原因：实现任务租约、尝试、恢复与冻结环境路由

- 2026-09-07：集成发现只有测例数量仍不能在受理前判断完整执行预算；按已批准 DESIGN 的受理前预算检查，增补 execution-profile/JudgeInput 预算字段及提交侧对应 DTO/校验的精确写入边界。此处仅允许本项契约适配，不修改其余提交持久化、公开接口或 Go 协议。

- 2026-09-07：独立复核发现既有 Go PE 结果未贯通。扩充精确写入边界到公开 verdict 枚举与 SubmissionLifecycle，恢复对 contracts/verdict.json 既有判定的兼容，不新增结果公开字段。同时修正原始 HTTP 响应预算以覆盖 1000 点默认摘录的 JSON 转义最坏规模；安全事件仍剥离全部隐藏字段。

- 2026-09-07：补充真实 Kafka 死信验证时，避免把无效事件的未验证 message key 原样复制到死信；精确扩充 SubmissionMessaging 写入范围，两个消费者均只保存 topic/partition/offset 和固定错误码。

- 2026-09-07：Java 聚合回归发现仓库级配置清单测试位于 user-service 测试目录，尚未分类新增的有意空值。精确扩充该单个测试文件边界，登记默认关闭的服务凭据和无内置数据库口令；不修改 user-service 业务或令牌实现。

- 2026-09-07：完成本任务全部技术完成标准；真实链路、故障恢复、回退、独立复核与浏览器证据见 VERIFY-002。人工验收仍待负责人执行。
- 2026-09-07：状态变更：doing → done。原因：真实判题、持久租约、重投乱序及崩溃恢复均通过，独立复核缺陷已修复
