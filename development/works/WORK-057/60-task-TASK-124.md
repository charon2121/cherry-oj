---
id: "TASK-124"
type: "task"
title: "修复认证数据库回归的时钟夹具漂移"
status: "done"
work: "WORK-057"
owners: ["team/server"]
depends_on: ["ISSUE-019"]
related: []
implements: ["ISSUE-019#REQ-001", "ISSUE-019#REQ-002"]
verifies: []
tags: []
read_paths: ["apps/server/user-service", "apps/server/pom.xml", "apps/server/TOOLCHAIN.md", "docs/engineering", "deploy/sandbox-linux/ci", "development/works/WORK-053", "development/works/WORK-056", "development/works/WORK-057"]
write_paths: ["apps/server/user-service/src/test/java/com/cherryoj/userservice/persistence/UserPersistenceIntegrationTests.java", "development/works/WORK-057"]
forbidden_paths: ["apps/server/user-service/src/main", "apps/judge-engine", "apps/web", "contracts", ".github/workflows", "deploy"]
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# TASK-124：修复认证数据库回归的时钟夹具漂移

## 任务目标

修复一份认证集成测试的时间基准，使其随日期变化仍能运行真实精度回归。

## 依据

ISSUE-019#REQ-001/REQ-002；来源为 WORK-056 的 CI34694917085 失败，承接 WORK-053 已验收的精度断言。

## 可查看范围

以 read_paths 为准；允许读取生产认证、账号创建和约束来核对原因，不属于 TASK-121 范围扩张。

## 可修改范围

只修改 UserPersistenceIntegrationTests.java 和本工作文档。具体从持久化 createdAt 推导逐轮时刻的算法见 ISSUE-019。

## 禁止修改

以 forbidden_paths 为准。禁止生产类、Schema、约束、认证会话策略、依赖、工作流或限额改动，不运行用户服务器或用户 Docker 容器。

## 依赖

本轮仅文档及只读定位；意图闸通过并获得后续实施授权后才编码。无需重新签署 WORK-053，也不把旧验收自动视为新夹具已通过。

## 产出

单文件修复、真实 MySQL 回归证据及本批 CI 结果。沿用现有 8 项认证门禁，不增通用时钟框架。

## 完成标准

- [x] 首次认证严格晚于持久化创建时间，四组样本逐轮递增。
- [x] 保留四组原精度及到期、撤销断言，真实数据库 8 项零跳过通过。
- [x] 两台新 Linux VM 的原始结果分别留存，差异检查与相关本地编译/认证单测通过。

## 验证

本地先按 apps/server/TOOLCHAIN.md 编译相关测试并运行 AuthenticationServiceTests；不把本地无 Docker 的 skip 计通过。获提交推送授权后用现有完整 CI 运行认证组，再进行同 SHA 全新运行验证日期无关路径及缓存消费；每轮保留全部成功/失败。数据库容器沿用现有 Testcontainers 限额与清理，禁止全局 prune；重跑须完整 workflow，避免同 attempt 产物缺失。

## 风险

真实数据库仍可能暴露其他问题，需区分原因。部署资产和历史 business.io 不在本任务修复范围。回退仅撤回测试文件改动，保留原日期失败证据；回退不意味着 CI 会转绿。提交推送及子智能体复核分别遵循本批授权，不继承 WORK-056 的发布授权。

## 执行记录

- 2026-09-12：已建立只读调查边界并确认源码中的混用时钟；尚未改测试、运行数据库或提交推送。
- 2026-09-12：状态变更：todo → ready。原因：意图闸已签署，用户明确允许实施测试夹具修复
- 2026-09-12：状态变更：ready → doing。原因：开始按持久化创建时间推导逐轮认证时钟

- 2026-09-12：核验意图闸 passed，依用户明确授权实施。仅修改指定集成测试文件：回读账号 createdAt，基准秒加一后保留原纳秒输入并逐轮加秒，新增输入/回读时间顺序断言，原会话断言不变。
- 2026-09-12：Homebrew JDK21、Maven Wrapper 离线运行 AuthenticationServiceTests：4项通过、0失败/错误/跳过；同时编译集成测试。日志 /private/tmp/cherry-work057-authentication.log。未启动数据库或用户服务，真实MySQL8项尚待两台Linux VM验证，任务保持doing。

- 2026-09-12：7c74d99 已授权提交推送，CI34695556153完整attempt1/2在两台新VM均认证8/8零错误零跳过；后续business.io均失败且最终清理通过，详见VERIFY-058。本任务修复目标完成，整链失败留归TASK-112。
- 2026-09-12：状态变更：doing → done。原因：同SHA两台LinuxVM真实MySQL认证8项均通过；原会话断言保持，独立业务io失败另记
