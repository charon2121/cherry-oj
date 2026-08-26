---
id: "WORK-001"
type: "work"
title: "重建统一开发文档系统"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["reliability"]
depends_on: []
related: ["CHANGE-001", "DESIGN-001", "DECISION-001", "PLAN-001", "TASK-001", "VERIFY-001", "MEMORY-001"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-001"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-001"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-001"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-001"], "checks": ["rollback"], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-001"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-001"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-001"], "checks": ["automated-tests", "cross-module-regression"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "done", "status_source": "manual", "artifacts": [], "checks": ["reliability"], "source": "overlay:concern", "reason": "专项关注要求在实施后持续观察"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-001"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
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










# WORK-001：重建统一开发文档系统

## 为什么做

仓库原来把产品需求放在 `product/`、研发执行放在 `tasks/`，两边有独立状态、模板、Schema、命令和
需要人工维护的双向关系。这个模型只能覆盖 REQ 与 TASK，无法自然表达基建、问题修复、重构、风险、
体验、设计、验证和项目记忆，也把本应属于一次工作的上下文拆成两个管理中心。

本次以 [`development/SPECIFICATION.md`](../../SPECIFICATION.md) 的工作项规范为基础重建文档系统，并明确
项目只保留全局文档与开发过程文档两层。

## 成功标准

- [x] `docs/` 明确只保存已经确认、跨工作项长期有效的全局文档。
- [x] `development/` 以 WORK 为入口，统一管理定义、设计、计划、任务、验证和记忆。
- [x] 工具可以选择流程、自动提高风险、生成文档、校验关系、推导状态、追踪和生成任务上下文。
- [x] 旧 `product/`、`tasks/` 及其专用脚本和 CI 入口被移除，有效产品定义和历史原因没有丢失。
- [x] 工具端到端测试、全局/开发文档链接校验与现有工程回归检查通过。

## 当前流程

本工作是系统级、高风险维护，采用完整流程。front matter 记录必需阶段、文档与专项检查，
`scripts/work flow WORK-001` 展示各阶段的实际文档状态。

## 待确认项

暂无。

## 风险点

- RISK-001：迁移时丢失仍有效的产品定义。通过逐段迁移旧 REQ-0001 并保留旧 ID 来源降低风险；旧全文
  仍可从 Git 历史恢复。
- RISK-002：新流程自身不可执行。使用无第三方依赖的 CLI、端到端测试和 CI 校验，在删除旧工具前验证
  创建、风险升级、断链检查、状态和上下文功能。
- RISK-003：把草稿混入全局文档。通过 `docs/README.md` 的准入规则，把工作中的未知和方案保留在
  `development/`。

## 影响面

影响仓库协作规则、文档目录、CI 和智能体开发入口；不修改运行时代码、跨语言契约、数据库、用户行为
或部署产物。旧工具删除后不能继续使用 `scripts/task` 与 `scripts/product`，统一改用 `scripts/work`。

## 关联文档

关联 ID 由 `related` 维护。全局规则见 `docs/README.md`，系统使用说明见 `development/README.md`。

## 变更记录

- 2026-08-24：创建工作项并生成初始流程。
- 2026-08-24：完成统一工具、模板、迁移、规则与 CI 重构。
- 2026-08-24：根据文档、任务与验证事实刷新状态：todo → verified。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：流程阶段 上线：ready → skipped。原因：纯仓库文档系统改造，无业务服务上线
- 2026-08-24：流程阶段 观察：pending → ready。原因：既有验证证据满足可靠性观察前置条件
- 2026-08-24：流程阶段 观察：ready → doing。原因：检查迁移后创建、校验和 CI 入口运行情况
- 2026-08-24：流程阶段 观察：doing → done。原因：后续两次文档系统重构均通过存量回归
