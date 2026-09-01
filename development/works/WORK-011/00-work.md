---
id: "WORK-011"
type: "work"
title: "收敛 Go 领域日志调用"
status: "verified"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["observability"]
depends_on: []
related: ["CHANGE-006", "TASK-014", "VERIFY-011"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-006"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-014"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-014"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-011"], "checks": ["automated-tests"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["observability"], "source": "overlay:concern", "reason": "专项关注要求在实施后持续观察"}, {"stage": "memory", "label": "项目记忆", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}]
required_documents: ["change", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "observability"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-26"
updated_at: "2026-08-26"
work_type: "maintenance"
---












# WORK-011：收敛 Go 领域日志调用

> 历史记录：本重构随 WORK-012 的观测实现整体回退，不再存在于当前代码。

## 为什么做

WORK-010 已建立 Go structured stdout，但 `judge` 与 `sandbox` 的业务 Handler 仍直接拼装日志消息、
`event.action`、领域字段和关联 logger。日志 schema 因而散落在业务流程中，一次完成日志占据多行，后续
字段调整也会迫使业务代码跟随可观测性实现变化。本维护项只收敛日志表达，不改变已经验收的
Trace、Metrics、Request ID 或业务行为。

## 成功标准

- [x] judge/sandbox 每个领域完成事件在业务 Handler 中只保留一条日志器方法调用。
- [x] 业务 Handler 不再直接调用 `ContextLogger(...).Info(...)` 或拼装日志字段键。
- [x] 原有 JSON event.action、领域字段、requestId/traceId/spanId、级别和脱敏边界保持不变。
- [x] Go 格式、vet、build、race tests 与可观测性 smoke 的相关日志断言通过。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-011` 查看实际进度。

## 待确认项

暂无。

## 风险点

集中字段组装时可能漏字段、改名或误记录源码/输入输出。通过注入 buffer 的日志测试逐字段断言并保留
敏感 canary 负向检查；改动不涉及持久化和外部 API，可直接回退本次适配器重构。

## 影响面

只影响 `apps/judge-engine` 内部日志调用与测试，不修改 contracts、Java、Compose、日志采集配置、公开
HTTP 响应、verdict、RunStatus、Metrics 名称/attributes 或 Trace 拓扑。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-26：创建工作项并生成初始流程。
- 2026-08-26：按负责人反馈明确只收敛 Go 领域日志调用，Metrics/Trace 语义与外部行为保持不变。
- 2026-08-26：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-26：完成具名 EventLogger 适配器、业务单行调用和真实容器采集回归，未改变日志 schema 或
  其他遥测/业务行为。
- 2026-08-26：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-08-26：流程阶段 复核：ready → done。原因：复核确认业务包不再直接拼日志字段，消费方接口可空安全，EventLogger 只读取 allowlist，Metrics/Trace 与外部行为未变
- 2026-08-26：根据文档、任务与验证事实刷新状态：implemented → verified。
- 2026-08-26：流程阶段 上线：ready → skipped。原因：纯内部日志重构不需要独立发布动作，变更随当前未提交工作统一交付
