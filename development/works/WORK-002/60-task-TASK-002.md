---
id: "TASK-002"
type: "task"
title: "冻结提交契约与内部服务身份边界"
status: "done"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["FEATURE-001", "EXPERIENCE-001", "DESIGN-002", "DECISION-002", "PLAN-002"]
related: []
implements: ["FEATURE-001#REQ-001", "FEATURE-001#REQ-002", "FEATURE-001#REQ-004", "FEATURE-001#REQ-007", "FEATURE-001#REQ-009", "FEATURE-001#REQ-011", "FEATURE-001#REQ-012", "FEATURE-001#AC-004", "FEATURE-001#AC-005", "FEATURE-001#AC-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "docs/architecture.md", "docs/data-model.md", "development/works/WORK-002", "contracts", "apps/server/identity-security-support", "apps/server/user-service", "apps/server/problem-service", "apps/server/judging-service", "apps/server/submission-service", "apps/server/gateway-service", "apps/server/pom.xml", "scripts/contracts_test.py", "AGENTS.md", "development/README.md", "apps/server/TOOLCHAIN.md", "apps/server/mvnw", "apps/server/.mvn"]
write_paths: ["contracts/web-api.openapi.json", "contracts/submission.json", "contracts/judge-input.schema.json", "contracts/judge-events.schema.json", "contracts/problem-judge-snapshot.schema.json", "contracts/execution-profile.schema.json", "contracts/submission-internal.openapi.json", "apps/server/identity-security-support", "scripts/contracts_test.py", "development/works/WORK-002"]
forbidden_paths: ["apps/server/user-service", "apps/server/gateway-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine", "apps/server/data", "compose.yaml"]
created_at: "2026-08-24"
updated_at: "2026-09-07"
---

# TASK-002：冻结提交契约与内部服务身份边界

## 任务目标

把创建/查询/恢复请求、内部快照和安全事件定义成可验证契约；提供与用户 JWT 隔离的服务凭据校验基础，后续模块只接入指定端点。

意图闸已由负责人签署，按明确依赖与以下边界实施；阶段证据见执行记录和 VERIFY。

## 依据

FEATURE-001 的本轮范围；DESIGN-002 的接口与安全约束；PLAN-002 的顺序和回退。`implements` 精确列出负责要求。

## 可查看范围

以 front matter 的 read_paths 为硬边界；实施前运行 `scripts/work context TASK-002`，并按语言读取工程规范。

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

- [x] 按 DESIGN 冻结路径、请求/响应、状态码、256 KiB 字节上限与字段 allowlist，区分公共 DTO 与内部 Submission/JudgeInput。
- [x] 在快照契约中明确执行预算需要的测试点数量等非敏感元信息及兼容方式；不允许后续靠无限等待或猜测超时。
- [x] 定义事件专用安全结果，负例包含 output/diff/运行期 message/源码/token；原 Go JudgeResult 不改。
- [x] 在内部 API 契约明确服务凭据的路由授权；新增有限的凭据校验与配置支持，恒定时间比较、缺配置拒绝；不扩大现有用户 verifier 的接受范围。
- [x] 正向、错误调用链、伪造 caller、用户 JWT/节点 token 混入、撤销旧凭据和缺失配置测试通过；未接业务端点时明确这是基础能力。

## 验证

python3 scripts/contracts_test.py；在 apps/server 按 TOOLCHAIN 执行 ./mvnw -pl identity-security-support -am test。
命令为未来实施计划，尚未执行；记录工具链、环境、退出码、失败及重跑原因。只在适用范围内运行检查。

## 风险

接口、权限、事件内容或数据所有权不符 DESIGN 时先停下调整方案。目录边界较宽处受任务目标进一步约束，
禁止借机抽象通用框架、修改既有账号/题目/节点行为。回归失败交还原责任任务。

## 执行记录

- 2026-09-07：完成文档拆分与边界整理，status=todo；未实施，未运行本任务的业务测试。
- 2026-09-07：状态变更：todo → ready。原因：意图闸已签且已明确授权实施，上游和读写边界已核对
- 2026-09-07：状态变更：ready → doing。原因：开始契约与内部调用权限基础实现

- 2026-09-07：新增公开提交/恢复契约、内部快照路由、安全 lifecycle 结果与三调用链服务凭据基础。contracts_test.py 11 项通过；identity-security-support Maven 8 项通过（首次沙箱禁止测试监听，获准后重跑）。
- 2026-09-07：状态变更：doing → done。原因：契约 11 项与身份模块 8 项测试通过，五项完成标准已落实
