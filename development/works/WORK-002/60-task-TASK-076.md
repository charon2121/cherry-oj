---
id: "TASK-076"
type: "task"
title: "提供正式提交所需的题目与执行配置快照"
status: "done"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["PLAN-002", "TASK-002"]
related: []
implements: ["FEATURE-001#REQ-002", "FEATURE-001#REQ-004", "FEATURE-001#REQ-005", "FEATURE-001#REQ-006", "FEATURE-001#AC-001", "FEATURE-001#AC-008"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "docs/architecture.md", "docs/data-model.md", "development/works/WORK-002", "contracts", "apps/server/problem-service", "apps/server/judging-service", "apps/server/identity-security-support", "development/works/WORK-025", "development/works/WORK-040", "AGENTS.md", "development/README.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/mvnw", "apps/server/.mvn"]
write_paths: ["apps/server/problem-service/src", "apps/server/judging-service/src", "development/works/WORK-002"]
forbidden_paths: ["contracts", "apps/server/user-service", "apps/server/gateway-service", "apps/server/submission-service", "apps/server/identity-security-support", "apps/web", "apps/judge-engine", "apps/server/data", "compose.yaml"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-076：提供正式提交所需的题目与执行配置快照

## 任务目标

提供 submission-service 可调用的两个只读内部 API，精确解析当前已发布版本和可执行环境，不依赖 ADMIN 用户凭证。

意图闸已由负责人签署，按明确依赖与以下边界实施；阶段证据见执行记录和 VERIFY。

## 依据

FEATURE-001 的本轮范围；DESIGN-002 的接口与安全约束；PLAN-002 的顺序和回退。`implements` 精确列出负责要求。

## 可查看范围

以 front matter 的 read_paths 为硬边界；实施前运行 `scripts/work context TASK-076`，并按语言读取工程规范。

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

- [x] problem-service 对 ACTIVE/PUBLIC、PUBLISHED、ACM/cpp、READY 数据作一致读取，返回固定版本/数据摘要和契约所需预算元信息。
- [x] judging-service 复用 readiness 规则，返回与 snapshot 一致的 VALID 校准、绝对限制与冻结环境；检查在线节点 session 和该节点的数据回执。
- [x] 接入 TASK-002 服务身份，原管理员管理接口与节点注册鉴权保持；普通 USER/ADMIN 无权访问新内部端点。
- [x] 无校准、数据摘要不符、节点掉线/旧 session、语言不符全部明确失败，不改状态、不补默认数据。
- [x] 回归发布、校准、节点注册；不得在本任务提前实现 Worker、迁移表或重构管理业务。

## 验证

在 apps/server 按 TOOLCHAIN 执行 ./mvnw -pl problem-service,judging-service -am test；记录真实数据库/节点边界测试结果。
命令为未来实施计划，尚未执行；记录工具链、环境、退出码、失败及重跑原因。只在适用范围内运行检查。

## 风险

接口、权限、事件内容或数据所有权不符 DESIGN 时先停下调整方案。目录边界较宽处受任务目标进一步约束，
禁止借机抽象通用框架、修改既有账号/题目/节点行为。回归失败交还原责任任务。

## 执行记录

- 2026-09-07：完成文档拆分与边界整理，status=todo；未实施，未运行本任务的业务测试。
- 2026-09-07：状态变更：todo → ready。原因：TASK-002 完成，接口和服务凭据边界已冻结
- 2026-09-07：状态变更：ready → doing。原因：实现两个只读内部快照端点

- 2026-09-07：两个内部快照端点完成；problem/judging 全模块 Maven 回归通过（真实隔离 MySQL），新增快照与路由鉴权 3 项定向测试通过。缺服务凭据关闭新端点，不影响原业务；现有发布、节点和校准测试通过。
- 2026-09-07：状态变更：doing → done。原因：只读快照、调用链鉴权与现有发布/节点回归通过
