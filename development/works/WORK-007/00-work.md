---
id: "WORK-007"
type: "work"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["compatibility"]
depends_on: []
related: ["CHANGE-005", "DESIGN-005", "DECISION-005", "PLAN-005", "TASK-007", "VERIFY-007", "MEMORY-005"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-005"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-005"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-005"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-005"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-007"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-007"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-007"], "checks": ["automated-tests", "cross-module-regression", "compatibility"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-005"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "compatibility"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-25"
updated_at: "2026-08-25"
work_type: "maintenance"
---















# WORK-007：校正全局 PRD 与当前 MVP 基线的漂移

## 为什么做

`docs/product.md` 同时承担长期产品愿景、当前 MVP、未来题目工厂/Agent 设想、实施路线和已完成架构
缺口说明。随着 contracts v2、不可变 JudgeInput、五服务边界以及统一 development 系统落地，其中
“当前方向”“未来需要补”的叙述已经失真，未来能力也被混入当前 P0，导致下游无法判断哪些规则
现在必须遵守、哪些只是未来方向。

## 成功标准

- [x] PRD 只保存已确认且跨工作长期有效的产品事实，不维护交付状态和未决选择。
- [x] 当前 MVP、首个 C++ ACM 纵向切片、完整 MVP 与长期方向的边界清晰且不互相冒充。
- [x] 核心概念、服务边界和非目标与 architecture、data-model、contracts v2 及 WORK-002 一致。
- [x] 登录方式、WA 明细和发布环境继续由 WORK-002 阻塞，不在 PRD 中代签。
- [x] 全局/开发文档校验、链接校验和漂移关键词检查通过。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-007` 查看实际进度。

## 待确认项

暂无。

## 风险点

- RISK-001：重写时把长期产品差异化误删成非目标。保留运营侧确定性流水线和受控 Agent 的长期方向，
  但不再把它们列入当前 MVP。
- RISK-002：把当前实现状态反向写成产品规则。PRD 只写目标边界，完成度仍以 WORK/VERIFY 为准。
- RISK-003：擅自解决 WORK-002 的产品未知。对三个 blocking 问题只建立链接和责任边界，不给答案。
- RISK-004：章节重写破坏引用。仓库没有 `product.md#...` 锚点引用，并通过链接检查验证。

## 影响面

直接修改全局产品真源 `docs/product.md` 和本 WORK 记录。影响所有未来功能定义对 MVP、长期方向和
产品优先级的理解，但不改变代码、契约、接口、数据库、权限、部署或既有 WORK-002 的待确认项。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：完成 PRD、架构、数据模型、contracts v2 与 WORK-002 的漂移审计。
- 2026-08-25：按稳定产品合同重写 PRD，并完成跨文档、范围、链接和漂移关键词检查。
- 2026-08-25：流程阶段 复核：ready → doing。原因：开始核对 PRD 不变量、WORK-002 未决边界、技术真源兼容性和修改范围
- 2026-08-25：流程阶段 复核：doing → done。原因：确认重写保留产品定位和九项不变量，未代签 WORK-002，未修改代码、契约或技术真源
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → verified。
- 2026-08-25：流程阶段 上线：ready → skipped。原因：纯全局 PRD 文档校正，无运行时发布、数据迁移或部署动作
- 2026-08-25：流程阶段 观察：pending → skipped。原因：无线上行为变化，观察由跨文档核对、链接检查和开发文档回归完成
