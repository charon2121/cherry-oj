---
id: "TASK-086"
type: "task"
title: "自定义运行服务编排与公开接口"
status: "done"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["TASK-085", "PLAN-030"]
related: []
implements: ["FEATURE-012#REQ-001", "FEATURE-012#REQ-002", "FEATURE-012#REQ-003", "FEATURE-012#REQ-004", "FEATURE-012#REQ-005", "FEATURE-012#REQ-006", "FEATURE-012#REQ-007", "FEATURE-012#REQ-008", "FEATURE-012#REQ-009", "FEATURE-012#REQ-010", "FEATURE-012#REQ-011", "FEATURE-012#REQ-012", "FEATURE-012#AC-001", "FEATURE-012#AC-002", "FEATURE-012#AC-003", "FEATURE-012#AC-004", "FEATURE-012#AC-005", "FEATURE-012#AC-006", "FEATURE-012#AC-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs", "contracts", "development/works/WORK-044", "apps/server", "apps/judge-engine/internal/contract", "apps/judge-engine/config.example.yaml"]
write_paths: ["apps/server/gateway-service/src", "apps/server/submission-service/src", "apps/server/judging-service/src", "development/works/WORK-044"]
forbidden_paths: ["apps/server/user-service", "apps/server/identity-security-support", "apps/server/problem-service", "apps/server/logging-support", "apps/server/submission-service/src/main/resources/db/migration", "apps/server/judging-service/src/main/resources/db/migration", "apps/judge-engine", "apps/web"]
created_at: "2026-09-07"
updated_at: "2026-09-08"
---

# TASK-086：自定义运行服务编排与公开接口

## 任务目标

实现独立 trial client/DTO、公开与内部接口、profile purpose 单 case预算、Gateway 原子限流、独立 semaphore和执行器、timeout/取消/错误安全投影。源码/输入不入库或日志，不触碰正式提交状态机。

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

三个 Java 服务及依赖测试；公开路由注册/方法、预期账号/CSRF/服务鉴权、Redis 并发拒绝、deadline、异常后容量回收与禁止自动重试；真实 HTTP 模拟合法最大转义响应。

## 风险

发现需修改范围外文件、扩大限额、改变用户流程时先更新上游。测试中不打印源码、输入、输出、凭据。

## 执行记录

- 2026-09-07：完成设计回合任务拆分，尚未实施。
- 2026-09-08：状态变更：todo → ready。原因：契约与 Go 前置任务完成
- 2026-09-08：状态变更：ready → doing。原因：实施三服务自测接口与准入保护
- 2026-09-08：状态变更：doing → done。原因：三服务自测边界及新增测试通过，后续统一端到端验证
- 2026-09-08：状态变更：done → doing。原因：响应缺字段拒绝与资源生命周期复核补强
- 2026-09-08：状态变更：doing → done。原因：完整Java回归通过，新增6项自测边界测试通过
- 2026-09-08：状态变更：done → doing。原因：修复真实trial路由异常映射
- 2026-09-08：状态变更：doing → blocked。原因：真实Linux空输入暴露Go stdin序列化缺陷，退回TASK-085
- 2026-09-08：状态变更：blocked → doing。原因：Go修复完成，恢复服务验证结论
- 2026-09-08：状态变更：doing → done。原因：trial真实鉴权路由与异常处理测试通过
