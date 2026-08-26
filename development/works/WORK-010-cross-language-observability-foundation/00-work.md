---
id: "WORK-010"
type: "work"
title: "建立跨语言可观测性基础设施"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["observability"]
depends_on: []
related: ["CAPABILITY-003", "EXPERIENCE-004", "DESIGN-008", "DECISION-007", "PLAN-008", "TASK-010", "VERIFY-010", "MEMORY-008", "TASK-011", "TASK-012", "TASK-013"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-010"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-003"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-004"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-008"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-007"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-008"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-010", "TASK-011", "TASK-012", "TASK-013"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-010", "TASK-011", "TASK-012", "TASK-013"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-010"], "checks": ["automated-tests", "cross-module-regression"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": ["observability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-008"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "observability"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-25"
updated_at: "2026-08-26"
work_type: "infra"
---






































# WORK-010：建立跨语言可观测性基础设施

> 历史记录：本工作交付的运行时实现已由 WORK-012 整体撤回；除 traceId/requestId 追溯契约外，本文
> 不再代表当前仓库能力或有效实施基线。

## 为什么做

五个 Java 服务目前只有 Actuator health/info 和 Gateway 局部的公开 request ID；Go judge/sandbox
主要使用 `log` 文本输出，只有 testcase 局部支持 `slog`。当前没有统一的结构化日志字段、内部 Trace
上下文、运行/HTTP/判题指标，也没有能把三类信号放到同一条排障链里的采集与查询入口。

这会让一次“提交卡住或判题失败”的排查只能按时间猜测多个进程的输出，无法回答请求经过了哪些边界、
慢在哪一段、是单次异常还是系统性退化。本工作建立跨 Java/Go 的最小可观测性底座，先覆盖已有进程
和 HTTP 边界，并为尚未实现的 Kafka 判题链规定传播语义；不借基建工作提前实现业务链路。

## 成功标准

- [x] 五个 Java 服务和 Go judge/sandbox 都输出可机器解析的结构化日志，并具有一致的服务、环境、
  request/trace/span 与安全业务关联字段。
- [x] Gateway 现有公开 `X-Request-Id` 语义保持不变；同步内部 HTTP 能关联该 ID，内部 Trace 使用 W3C
  Trace Context 贯穿当前 Java/Go HTTP 边界，且不把 request ID、trace ID、幂等键和业务 ID 混用。
- [x] 所有进程提供运行时和 HTTP RED 指标，judge/sandbox 提供有限基数的判题、执行和资源指标；
  requestId、traceId、submissionId 等无界值绝不成为 metric label。
- [x] 遥测导出和采集后端不可用时业务继续服务，队列、超时和关闭 flush 有界，并能观察遥测丢弃。
- [x] 可选本地采集栈能从一条请求进入日志、Trace 和 Metrics 查询；自动化测试覆盖传播、脱敏、
  基数边界和跨模块回归。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-010` 查看实际进度。

## 待确认项

暂无。DECISION-007 的方案 A 与五项边界已于 2026-08-26 获负责人确认。

## 风险点

- 日志泄露源码、Cookie、JWT、测试数据或标准答案：使用允许字段清单、长度上限和负向测试，默认不记录
  request/response body、命令环境与异常对象的任意字段。
- 指标维度失控：只允许路由模板、状态码类、verdict、phase、mode 和受配置约束的 languageId；对
  原始 path、错误消息和所有业务 ID 做自动化反例检查。
- Trace/日志导出阻塞请求或放大故障：应用异步、有界、fail-open 导出，采集端与存储端不进入健康
  判定，关闭只做带超时的 best-effort flush。
- 外部来访 Trace/Baggage 污染内部链路：Gateway 作为信任边界丢弃来访 `traceparent`、`tracestate`
  和 `baggage`，内部网络才允许提取受支持的 W3C Trace Context，初期不传播 baggage。
- 采样导致单次 Trace 查不到：错误与关键生命周期始终保留结构化日志；采样率可配置，不能把“有 Trace”
  当业务正确性的条件。

## 影响面

影响五个 Java 模块、Go judge/sandbox、根 Compose、本地运维配置、跨语言事件契约测试和后端技术文档。
不改变 browser-facing OpenAPI、业务响应、verdict、数据库所有权、认证授权或用户界面；不新增源码、
测例或凭据的采集。`judge-events.traceId` 会明确为 W3C Trace ID 的可查询副本，真正的父子传播仍通过
transport header 完成。生产高可用、告警值班、长期留存和完整 Kafka 业务实现不在本工作范围。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-26：负责人确认 DECISION-007 方案 A 与全部五项边界，授权按 PLAN-008 开始实施。
- 2026-08-26：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-26：流程阶段 复核：ready → doing。原因：开始独立复核跨模块影响、安全脱敏、基数、Context 传播、资源边界与任务路径
- 2026-08-26：流程阶段 复核：doing → done。原因：影响、安全、基数、传播、资源、路径与回退复核完成；未发现未解决缺陷或越界改动
- 2026-08-26：VERIFY-010 通过契约、Java、Go、Compose、Alloy/Grafana、默认容器与 host fallback
  跨运行时验证；五项成功标准均有可执行证据。
- 2026-08-26：流程阶段 上线：pending → blocked。原因：当前工作只授权仓库实现与本地 reference stack，没有生产部署目标、凭据、容量/SLO 或发布授权
- 2026-08-26：流程阶段 线上观察：pending → blocked。原因：生产发布尚未发生，无法进行线上负载、成本、留存和告警观察；本地停采/恢复已由 VERIFY-010 覆盖
- 2026-08-26：根据文档、任务与验证事实刷新状态：doing → verified。
