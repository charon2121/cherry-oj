---
id: "WORK-003"
type: "work"
title: "按工作项聚合开发文档"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["reliability"]
depends_on: []
related: ["CHANGE-002", "DESIGN-003", "DECISION-003", "PLAN-003", "TASK-003", "VERIFY-003", "MEMORY-003"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-002"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-003"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-003"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-003"], "checks": ["rollback"], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-003"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-003"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-003"], "checks": ["automated-tests", "cross-module-regression"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "done", "status_source": "manual", "artifacts": [], "checks": ["reliability"], "source": "overlay:concern", "reason": "专项关注要求在实施后持续观察"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-003"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "reliability"]
human_confirmations: []
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










# WORK-003：按工作项聚合开发文档

## 为什么做

当前 `development/` 虽然已统一 WORK、定义、设计、任务和验证的语义模型，但仍按文档类型分散到
多个目录。阅读或交接一个工作项时需要跨目录搜集文件，也无法直接从文件树看出信息层级。按用户
确认的新方案，将一个工作项的全部过程文档聚合到独立目录，并用固定前缀表达阅读顺序。

## 成功标准

- [x] 每个 WORK 都有且只有一个 `development/works/WORK-xxx/` 目录。
- [x] 所有附属文档与所属 WORK 位于同一目录，并按固定信息层级命名。
- [x] 创建、补充文档、查询、校验、上下文和归档命令支持新结构。
- [x] 现有工作项完成无损迁移，永久 ID、元数据关系和正文内容继续保留。
- [x] README、SPECIFICATION、项目规则和自动化测试不再引导使用旧的类型目录。

## 当前流程

由 front matter 的 `workflow`、`required_documents` 与 `required_checks` 生成；使用
`scripts/work flow WORK-003` 查看实际进度。

## 待确认项

暂无。

## 风险点

- 移动文件可能造成链接失效：由 Markdown 链接检查和全文检索发现，迁移时同步修正相对路径。
- 扫描或生成逻辑遗漏旧假设：以 CLI 端到端测试覆盖创建、校验、上下文、归档和多 WORK 场景。
- 层级编号与永久 ID 混淆：文件名前缀只用于排序，front matter ID 和 `index.json` 继续单调分配。
- 回退可通过版本控制恢复本工作变更；迁移不改变业务代码、数据库或公共接口。

## 影响面

影响 `development/` 的物理目录、`scripts/work` 的路径约束、工具端到端测试及开发文档入口。不会改变
运行时业务、数据库、API、权限与部署。`docs/` 继续保存已确认的全局事实，不随单个工作项迁移。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-24：按单工作项目录完成现有开发文档迁移，并引入固定层级文件名。
- 2026-08-24：创建工作项并生成初始流程。
- 2026-08-24：根据文档、任务与验证事实刷新状态：todo → verified。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：流程阶段 上线：ready → skipped。原因：纯仓库目录结构改造，无业务服务上线
- 2026-08-24：流程阶段 观察：pending → ready。原因：既有验证证据满足可靠性观察前置条件
- 2026-08-24：流程阶段 观察：ready → doing。原因：检查新目录结构在类型化流程迁移后的稳定性
- 2026-08-24：流程阶段 观察：doing → done。原因：存量文档重建与全量校验未发现目录漂移
