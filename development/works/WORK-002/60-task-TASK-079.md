---
id: "TASK-079"
type: "task"
title: "接通题目页正式提交和结果查询"
status: "done"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["PLAN-002", "TASK-077", "TASK-077"]
related: []
implements: ["FEATURE-001#REQ-001", "FEATURE-001#REQ-002", "FEATURE-001#REQ-003", "FEATURE-001#REQ-005", "FEATURE-001#REQ-006", "FEATURE-001#REQ-007", "FEATURE-001#REQ-008", "FEATURE-001#REQ-009", "FEATURE-001#REQ-010", "FEATURE-001#REQ-012", "FEATURE-001#REQ-013", "FEATURE-001#AC-002", "FEATURE-001#AC-003", "FEATURE-001#AC-004", "FEATURE-001#AC-006", "FEATURE-001#AC-007", "FEATURE-001#AC-010"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "docs/architecture.md", "docs/data-model.md", "development/works/WORK-002", "contracts", "docs/design-system/PROMPT.md", "docs/design-system.md", "apps/web", "development/works/WORK-041", "apps/server/gateway-service", "apps/server/submission-service", "AGENTS.md", "development/README.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/mvnw", "apps/server/.mvn"]
write_paths: ["contracts/web-api.openapi.json", "apps/web/scripts/check-design-system.mjs", "apps/server/gateway-service/src", "apps/web/src/features/problems", "apps/web/src/features/submissions", "apps/web/src/routes/_site.problems.$slug.tsx", "apps/web/src/lib/api", "apps/web/src/generated/api", "apps/web/e2e", "development/works/WORK-002"]
forbidden_paths: ["apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/server/identity-security-support", "apps/judge-engine", "apps/server/data", "apps/web/design-system", "apps/web/src/features/auth", "compose.yaml"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-079：接通题目页正式提交和结果查询

## 任务目标

通过 Gateway 把正式提交与单次结果接入现有工作区；支持刷新恢复与权限失效，不重新设计编辑器。

意图闸已由负责人签署，按明确依赖与以下边界实施；阶段证据见执行记录和 VERIFY。

## 依据

FEATURE-001 的本轮范围；DESIGN-002 的接口与安全约束；PLAN-002 的顺序和回退。`implements` 精确列出负责要求。

## 可查看范围

以 front matter 的 read_paths 为硬边界；实施前运行 `scripts/work context TASK-079`，并按语言读取工程规范。

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

- [x] 新增提交 BFF 转发与错误映射，沿用 Session/CSRF/强制改密及用户身份机制，不把内部接口暴露到浏览器。
- [x] 从契约生成 API 类型，创建捕获不可变请求/幂等键；响应不确定可确认原请求，刷新 GET，不生成新请求。
- [x] 已有提交编号可复制链接并恢复；账号变化停轮询并清结果；未登录、失败与不同题目/他人编号有明确状态。
- [x] 结果区按 EXPERIENCE 展示，轮询退避/终态停止/焦点恢复；提交后编辑不改变已有输入。
- [x] 运行保持暂未开放，不实现完整历史/统计；保留 Monaco/触屏 fallback/草稿冲突恢复，完成设计系统七问与键盘/窄屏验证。

## 验证

Gateway Maven tests；apps/web 内 npm run check、npm run build、与提交及既有题目工作区相关的 Playwright 测试。
实际命令、工具链、环境、结果及失败重跑原因见 VERIFY-002 最终技术验证与真实整链路记录。

## 风险

接口、权限、事件内容或数据所有权不符 DESIGN 时先停下调整方案。目录边界较宽处受任务目标进一步约束，
禁止借机抽象通用框架、修改既有账号/题目/节点行为。回归失败交还原责任任务。

## 执行记录

- 2026-09-07：完成文档拆分与边界整理，status=todo；未实施，未运行本任务的业务测试。

- 2026-09-07：提交读写接口与真实 Kafka 编排已可用，前端接入依赖调整为 TASK-077，以便在 TASK-078 补齐真实 Go 验证期间推进；最终整链路验收仍依赖 TASK-078 全部完成，不降低其完成标准。
- 2026-09-07：状态变更：todo → ready。原因：提交接口和消息编排已可用，按调整后的依赖开始前端接入
- 2026-09-07：状态变更：ready → doing。原因：接入 Gateway 本人提交接口与现有工作台结果恢复

- 2026-09-07：源码门禁将协议幂等 UUID 误判为随机 DOM id。允许精确修改 Web 门禁文件，仅对 submission-recovery.ts 的 random-dom-id 规则增加精确例外；该文件只生成协议身份，不生成 DOM，其他路径继续拒绝。恢复索引改为账号+题目稳定入口，原请求体仍冻结旧版本，避免版本发布使未决请求失联。

- 2026-09-07：独立复核发现跨标签页切换账号后，CSRF 自动更新可能把旧账号编辑器内容提交为新账号。先扩充精确契约边界：创建请求增加 X-Expected-User-Id 一致性前置条件，Gateway 与真实会话账号比较，不用该头授权或代替 JWT。前端原请求重试固定原账号，失配明确拒绝。该修复落实既定账号与源码隔离要求。

- 2026-09-07：Gateway 全模块测试、Web check/build、17 项工作台浏览器测试通过；补充结果区双主题/320px forced-colors 截图已查看，自检八问记录在 VERIFY。独立复核确认跨版本与账号切换修复闭合。
- 2026-09-07：状态变更：doing → done。原因：Gateway与工作台接入完成，检查、浏览器测试与设计自检通过，独立复核两项恢复问题修复闭合
- 2026-09-07：状态变更：done → doing。原因：真实浏览器发现正常POST期间提前查询导致短暂错误提示，调整查询时机后补回归

- 2026-09-07：完成本任务全部技术完成标准；真实链路、故障恢复、回退、独立复核与浏览器证据见 VERIFY-002。人工验收仍待负责人执行。
- 2026-09-07：状态变更：doing → done。原因：工作台提交、账号隔离和恢复实现完成，165项测试及17项E2E通过，真实浏览器AC和CE通过
