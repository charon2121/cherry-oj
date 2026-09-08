---
id: "TASK-087"
type: "task"
title: "题目工作台输入与运行结果体验"
status: "done"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["TASK-086", "PLAN-030"]
related: []
implements: ["FEATURE-012#REQ-001", "FEATURE-012#REQ-002", "FEATURE-012#REQ-003", "FEATURE-012#REQ-004", "FEATURE-012#REQ-005", "FEATURE-012#REQ-006", "FEATURE-012#REQ-007", "FEATURE-012#REQ-008", "FEATURE-012#REQ-009", "FEATURE-012#REQ-010", "FEATURE-012#REQ-011", "FEATURE-012#REQ-012", "FEATURE-012#AC-001", "FEATURE-012#AC-002", "FEATURE-012#AC-003", "FEATURE-012#AC-004", "FEATURE-012#AC-005", "FEATURE-012#AC-006", "FEATURE-012#AC-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs", "contracts", "development/works/WORK-044", "apps/web", "development/works/WORK-041", "development/works/WORK-043"]
write_paths: ["apps/web/src/features/problems", "apps/web/src/features/submissions", "apps/web/src/features/custom-runs", "apps/web/src/generated/api", "apps/web/e2e", "development/works/WORK-044"]
forbidden_paths: ["apps/web/design-system", "apps/web/src/components/ui", "apps/web/src/app/shells", "apps/server", "apps/judge-engine", "contracts"]
created_at: "2026-09-07"
updated_at: "2026-09-08"
---

# TASK-087：题目工作台输入与运行结果体验

## 任务目标

生成 API 类型并新增 custom-runs 业务模块；右侧底部三 Tabs、样例输入确认、空输入和只读输出、请求快照/迟到保护。复用组件，避免重挂载主代码编辑器。提交结果迁入 panel 但不更改幂等恢复语义。

## 依据

FEATURE-012、EXPERIENCE-020、DESIGN-038、DECISION-025、PLAN-030；implements 关联全部验收锚点，按上述目标分工。

## 可查看范围

以 read_paths 为准；编码前读取对应 Java/Go/TypeScript 规范与工具链。Web 另读设计系统 PROMPT。

## 可修改范围

以 write_paths 为上限，只改本任务所需文件；production 修改仅前置实现任务执行。配置只增加自测相关配置，不调整正在运行的环境。

## 禁止修改

以 forbidden_paths 为准；无数据库迁移、身份信任链修改或新 UI 原语。跨边界先修订设计和任务。

## 依赖

以 depends_on 为准；意图闸和后续实施授权前保持 todo，执行前必须 ready。

## 产出

上述实现/测试与对应 VERIFY-045 实际证据。

## 完成标准

- [x] 对应功能及边界已实现，契约与类型一致。
- [x] 正常/失败/权限/容量场景测试通过，正式提交行为不回归。
- [x] 命令、环境、结果和未执行项记录 VERIFY-045。
- [x] TASK-088 统一交付真实端到端、截图八问、独立复核和回退检查（本项在前置任务表示交接，在 TASK-088 表示完成）。

## 验证

npm run generate:api/check/build，工作台/历史与新增 custom-run E2E；双请求并行不抢焦点、修改期间旧结果提示、换账号清理、空输入、429/504等异常、输入替换取消不丢文本。

## 风险

发现需修改范围外文件、扩大限额、改变用户流程时先更新上游。测试中不打印源码、输入、输出、凭据。

## 执行记录

- 2026-09-07：完成设计回合任务拆分，尚未实施。
- 2026-09-08：状态变更：todo → ready。原因：契约与服务自测接口完成
- 2026-09-08：状态变更：ready → doing。原因：实现右侧输入/运行结果/提交结果 Tabs

- 2026-09-08：Node24 check（168 单元测试）与 build 通过；27 项 workspace/history/custom-run E2E 通过。截图发现底部输出挤压代码区，已修复高度上限并加入编辑器可见高度断言；换账号迟到结果、空输入、输入替换取消、429 等待时间通过。交由 TASK-088 补充真实环境与独立复核。
- 2026-09-08：状态变更：doing → done。原因：工作台实现完成，27项浏览器回归与Web检查通过
- 2026-09-08：状态变更：done → doing。原因：联动上游服务复核，保留前端通过证据
- 2026-09-08：状态变更：doing → blocked。原因：暂候TASK-086响应边界补强
- 2026-09-08：状态变更：blocked → doing。原因：上游补强完成，恢复前端检查结论
- 2026-09-08：状态变更：doing → done。原因：27项浏览器回归通过，前端无新增业务改动
- 2026-09-08：状态变更：done → doing。原因：上游路由修复需联动任务状态，Web通过证据保留
- 2026-09-08：状态变更：doing → blocked。原因：等待TASK-086路由异常处理修复
- 2026-09-08：状态变更：blocked → doing。原因：上游修复完成，恢复前端验证结论
- 2026-09-08：状态变更：doing → done。原因：前端27项回归通过
