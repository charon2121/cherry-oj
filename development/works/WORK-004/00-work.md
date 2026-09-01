---
id: "WORK-004"
type: "work"
title: "按类型与风险编排开发流程"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["reliability"]
depends_on: []
related: ["CHANGE-003", "DESIGN-004", "DECISION-004", "PLAN-004", "TASK-004", "VERIFY-004", "MEMORY-004"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-003"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-004"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-004"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-004"], "checks": ["rollback"], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-004"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-004"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-004"], "checks": ["automated-tests", "cross-module-regression"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "done", "status_source": "manual", "artifacts": [], "checks": ["reliability"], "source": "overlay:concern", "reason": "专项关注要求在实施后持续观察"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-004"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "reliability"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-24"
updated_at: "2026-08-24"
work_type: "maintenance"
---
























# WORK-004：按类型与风险编排开发流程

## 为什么做

现有流程选择器只使用一条通用阶段列表，再按条件把阶段标记为 required 或 not-needed。它能够选择
部分文档，却不能准确表达产品、基建、修复、重构和改进的不同主流程，也混淆了流程阶段与 Markdown
文档之间的关系。按已经确认的方案，把 WORK Type、风险增量、阶段进度和 artifacts 正式分离。

## 成功标准

- [x] 五种 WORK Type 都有独立且可校验的基础流程模板。
- [x] 风险、影响面和 concern 可以升级或插入阶段、文档、检查与人工确认。
- [x] 每个阶段记录必需性、进度、artifacts、checks、source 和 reason。
- [x] 支持阶段无文档、阶段多文档，以及同一 TASK 支撑多个阶段。
- [x] 文档/TASK/VERIFY/WORK 事实自动同步阶段，无 artifact 阶段可以受约束地显式推进。
- [x] 现有 WORK 完成迁移，SPECIFICATION、README、Schema 和测试与实现一致。

## 当前流程

由 WORK Type 基础模板和风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、进度、artifacts、检查和规则来源；使用 `scripts/work flow WORK-004` 查看实际进度。

## 待确认项

暂无。

## 风险点

- 流程模板与文档生成漂移：由 `check` 重新计算期望配置并拒绝差异。
- 阶段状态形成第二套人工状态：优先从文档、TASK、VERIFY 和 WORK 推导，只允许无 artifact 阶段显式推进。
- 类型差异被风险规则覆盖：风险规则只能增量升级或插入，不替换类型基础模板。
- 迁移破坏历史状态：保留永久 ID、文档状态和关系，仅重建 workflow 与 artifact 绑定。

## 影响面

影响 `scripts/work` 的流程选择、生成、查询、状态同步和校验，所有 WORK 的 front matter workflow，
统一 Schema、模板、SPECIFICATION、README、项目规则和工具测试。不影响业务代码、数据库、公共 API
或部署配置。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-24：完成类型化流程、风险增量、artifact 绑定和阶段状态模型实现。
- 2026-08-24：创建工作项并生成初始流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：流程阶段 上线：pending → skipped。原因：纯仓库文档工具变更，无业务服务上线
- 2026-08-24：流程阶段 观察：pending → ready。原因：实现与验证完成，可以观察流程稳定性
- 2026-08-24：流程阶段 观察：ready → doing。原因：通过重复创建、重建、校验和查询观察稳定性
- 2026-08-24：流程阶段 观察：doing → done。原因：重复回归和存量 WORK 检查未发现流程漂移
- 2026-08-24：根据文档、任务与验证事实刷新状态：todo → verified。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：流程阶段 上线：ready → skipped。原因：纯仓库文档工具变更，无业务服务上线
- 2026-08-24：流程阶段 观察：pending → ready。原因：实现与验证完成，可以观察流程稳定性
- 2026-08-24：流程阶段 观察：ready → doing。原因：通过重复创建、重建、校验和查询观察稳定性
- 2026-08-24：流程阶段 观察：doing → done。原因：重复回归和存量 WORK 检查未发现流程漂移
