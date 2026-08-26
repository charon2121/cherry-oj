---
id: "WORK-012"
type: "work"
title: "撤回可观测性实现并保留追溯契约"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["observability"]
depends_on: []
related: ["CHANGE-007", "DESIGN-009", "DECISION-008", "PLAN-009", "TASK-015", "VERIFY-012", "MEMORY-009"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-007"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-009"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-008"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-009"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-015"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-015"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-012"], "checks": ["automated-tests", "cross-module-regression"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "done", "status_source": "manual", "artifacts": [], "checks": ["observability"], "source": "overlay:concern", "reason": "专项关注要求在实施后持续观察"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-009"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "observability"]
human_confirmations: []
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



















# WORK-012：撤回可观测性实现并保留追溯契约

## 为什么做

WORK-010 的方案 A 同时把日志、Metrics、Trace SDK、领域埋点和本地采集栈接入 Java/Go。实际复核发现
Go 业务代码、构造参数、进程生命周期和依赖树受到的侵入超过负责人可接受范围。当前实现尚未提交或
发布，应在继续叠加业务功能前撤回；只保留已经确认的 Request ID 与 Trace Context 追溯语义。

## 成功标准

- [x] Java/Go 回到可观测性接入前的运行时代码与依赖，不含 structured logging、Metrics、领域 Span、
  OTLP exporter 或 observability 共享模块。
- [x] 根 Compose 不含 Alloy/otel-lgtm profile，仓库不含 dashboard、collector 配置或 smoke 脚本。
- [x] Gateway 既有 public Request ID 行为不回退；contracts 保留 W3C header、request ID 与业务 body 的
  职责边界，以及 event traceId 32-hex 查询副本语义。
- [x] 契约、Java、Go、Compose 与开发文档检查全部通过。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-012` 查看实际进度。

## 待确认项

暂无。

## 风险点

批量回退可能误删 WORK-009 已有 Request ID、业务修复或契约追溯字段。所有 tracked 应用文件逐一对照
HEAD，contracts 采用保留清单而非整文件回退；全量测试和 diff 范围复核作为门禁。

## 影响面

影响 Java 五服务、Go judge/sandbox、根 Compose、可观测性目录与技术文档。无数据库、公开 API、判题
结果或用户行为变化；本地 Grafana/Alloy 能力被撤回，Trace Context 只有契约、不在本次提供运行时 SDK。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-26：创建工作项并生成初始流程。
- 2026-08-26：负责人要求撤回本次全部观测系统代码和设计，只保留 traceId/requestId 追溯设计。
- 2026-08-26：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-26：运行时观测实现和本地采集栈已撤回，追溯契约保留；Java、Go、contracts、Compose 与
  本地容器回归通过。
- 2026-08-26：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-08-26：流程阶段 复核：ready → done。原因：已完成允许范围、残留观测代码与追溯契约保留项复核
- 2026-08-26：根据文档、任务与验证事实刷新状态：implemented → verified。
- 2026-08-26：流程阶段 上线：ready → skipped。原因：本次仅回退未提交的本地实现，不执行生产发布
- 2026-08-26：流程阶段 观察：pending → ready。原因：本地运行观察窗口已具备，业务容器已使用回退镜像重建
- 2026-08-26：流程阶段 观察：ready → done。原因：judge/sandbox 健康，Compose 中无观测容器，回退后的运行状态符合预期
