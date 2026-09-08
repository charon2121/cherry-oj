---
id: "TASK-085"
type: "task"
title: "自定义运行契约与 Go trial 输出补齐"
status: "done"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["FEATURE-012", "DESIGN-038", "DECISION-025", "PLAN-030"]
related: []
implements: ["FEATURE-012#REQ-001", "FEATURE-012#REQ-002", "FEATURE-012#REQ-003", "FEATURE-012#REQ-004", "FEATURE-012#REQ-005", "FEATURE-012#REQ-006", "FEATURE-012#REQ-007", "FEATURE-012#REQ-008", "FEATURE-012#REQ-009", "FEATURE-012#REQ-010", "FEATURE-012#REQ-011", "FEATURE-012#REQ-012", "FEATURE-012#AC-001", "FEATURE-012#AC-002", "FEATURE-012#AC-003", "FEATURE-012#AC-004", "FEATURE-012#AC-005", "FEATURE-012#AC-006", "FEATURE-012#AC-007"]
verifies: []
tags: []
read_paths: ["scripts/contracts_test.py", "CLAUDE.md", "docs", "contracts", "development/works/WORK-044", "apps/judge-engine", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/judge"]
write_paths: ["scripts/contracts_test.py", "contracts", "apps/judge-engine/internal/contract", "apps/judge-engine/internal/judge/flow", "apps/judge-engine/internal/judge/api", "development/works/WORK-044"]
forbidden_paths: ["apps/judge-engine/internal/sandbox", "apps/judge-engine/cmd", "apps/server", "apps/web"]
created_at: "2026-09-07"
updated_at: "2026-09-08"
---

# TASK-085：自定义运行契约与 Go trial 输出补齐

## 任务目标

先定义公开与内部 DTO/profile purpose，随后补齐 trial stderr 的 Go 类型与执行结果投影。submit 行为不变；不修改 sandbox 执行/隔离。

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

契约校验与 Go contract/flow/api 回归，重点 RAN stdout/stderr、CE、RE、截断、正式 submit 不泄漏 stderr；定义字节计数与文本替换的真实边界。

## 风险

发现需修改范围外文件、扩大限额、改变用户流程时先更新上游。测试中不打印源码、输入、输出、凭据。

## 执行记录

- 2026-09-07：完成设计回合任务拆分，尚未实施。
- 2026-09-08：状态变更：todo → ready。原因：意图闸已签署，用户允许实施，契约与 Go 任务依赖就绪
- 2026-09-08：状态变更：ready → doing。原因：开始自测契约与 trial stderr 实现

- 2026-09-08：契约校验器使用精确文件清单，新增内部契约须同步 scripts/contracts_test.py 清单；先补充这一单文件边界，不修改校验强度。
- 2026-09-08：状态变更：doing → done。原因：契约校验与 Go contract/flow/api race 回归通过，自测 stderr 补齐
- 2026-09-08：状态变更：done → doing。原因：空输入需要使用无stdin对象表示EOF，避免序列化为空对象
- 2026-09-08：状态变更：doing → done。原因：空输入EOF修复通过JSON序列化及Go race回归
