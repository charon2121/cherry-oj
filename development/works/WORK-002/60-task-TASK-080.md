---
id: "TASK-080"
type: "task"
title: "验证真实答题链路与交付回退"
status: "done"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["PLAN-002", "TASK-077"]
related: []
implements: ["FEATURE-001#REQ-001", "FEATURE-001#REQ-002", "FEATURE-001#REQ-003", "FEATURE-001#REQ-004", "FEATURE-001#REQ-005", "FEATURE-001#REQ-006", "FEATURE-001#REQ-007", "FEATURE-001#REQ-008", "FEATURE-001#REQ-009", "FEATURE-001#REQ-010", "FEATURE-001#REQ-011", "FEATURE-001#REQ-012", "FEATURE-001#REQ-013", "FEATURE-001#REQ-014", "FEATURE-001#AC-001", "FEATURE-001#AC-002", "FEATURE-001#AC-003", "FEATURE-001#AC-004", "FEATURE-001#AC-005", "FEATURE-001#AC-006", "FEATURE-001#AC-007", "FEATURE-001#AC-008", "FEATURE-001#AC-009", "FEATURE-001#AC-010"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "docs/architecture.md", "docs/data-model.md", "development/works/WORK-002", "contracts", "apps/server", "apps/judge-engine", "apps/web", "compose.yaml", "scripts", "docs/product.md", "AGENTS.md", "development/README.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/mvnw", "apps/server/.mvn"]
write_paths: ["scripts/work-002-e2e.py", "compose.work-002-test.yaml", "apps/web/e2e", "development/works/WORK-002"]
forbidden_paths: ["contracts", "apps/server", "apps/judge-engine", "apps/web/src", "apps/web/design-system", "compose.yaml", "compose.legacy.yaml", "docs"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-080：验证真实答题链路与交付回退

## 任务目标

提供隔离、可重放的整链路验证和人工验收步骤，记录独立复核与回退演练，不以假 Judge 代替交付验收。

意图闸已由负责人签署，按明确依赖与以下边界实施；阶段证据见执行记录和 VERIFY。

## 依据

FEATURE-001 的本轮范围；DESIGN-002 的接口与安全约束；PLAN-002 的顺序和回退。`implements` 精确列出负责要求。

## 可查看范围

以 front matter 的 read_paths 为硬边界；实施前运行 `scripts/work context TASK-080`，并按语言读取工程规范。

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

- [x] 专用脚本和 Compose 隔离数据库、Kafka、Java、Linux Judge/sandbox、端口和数据；只清理自己创建的资源。
- [x] 按 VERIFY 覆盖 AC/WA/CE/TLE/SE、重复创建、隐藏输出探针、认证隔离、节点故障、Kafka 恢复、冻结与乱序。
- [x] 记录实际提交编号、环境/镜像指纹、校准与数据版本、命令、结果、耗时和未通过项，不记录秘密或隐藏测试正文。
- [x] 完成独立复核与可复现的回退演练；发现实现缺陷退回原任务修复，不借验收任务扩大业务写入范围。
- [x] 准备用户无需读代码的手测步骤；验收环境按已确认选项执行，现有环境部署另行授权；VERIFY 技术结果可记录 pass，人工 approved 只能由验收闸签署。

## 验证

python3 scripts/work-002-e2e.py --help（新增脚本须提供预检与清理说明）；按脚本实际参数执行隔离验证，并记录 Web 手测与独立复核。
实际命令、工具链、环境、结果及失败重跑原因见 VERIFY-002 最终技术验证与真实整链路记录。

## 风险

接口、权限、事件内容或数据所有权不符 DESIGN 时先停下调整方案。目录边界较宽处受任务目标进一步约束，
禁止借机抽象通用框架、修改既有账号/题目/节点行为。回归失败交还原责任任务。

## 执行记录

- 2026-09-07：完成文档拆分与边界整理，status=todo；未实施，未运行本任务的业务测试。

- 2026-09-07：为验证 TASK-078 的真实 Judge 完成标准，隔离脚本准备允许在 TASK-077 完成后启动，与 TASK-078/079 的模块回归交替推进；工作最终收束仍必须等待全部实现任务完成，不将未验证条目标为通过。
- 2026-09-07：状态变更：todo → ready。原因：提交接口已完成，开始搭建隔离栈供判题与页面集成验证
- 2026-09-07：状态变更：ready → doing。原因：准备专属脚本、隔离基础设施与真实 Linux 判题链路

- 2026-09-07：完成本任务全部技术完成标准；真实链路、故障恢复、回退、独立复核与浏览器证据见 VERIFY-002。人工验收仍待负责人执行。
- 2026-09-07：状态变更：doing → done。原因：隔离真实整链路、故障与回退演练、独立复核和人工验收说明已完成，证据见VERIFY-002
