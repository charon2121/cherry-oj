---
id: "WORK-006"
type: "work"
title: "按思维导图结构重写开发文档系统规范"
status: "verified"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: []
depends_on: []
related: ["CHANGE-004", "TASK-006", "VERIFY-006"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-004"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-006"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-006"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-006"], "checks": ["automated-tests"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "optional", "status": "skipped", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "memory", "label": "项目记忆", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}]
required_documents: ["change", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests"]
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














# WORK-006：按思维导图结构重写开发文档系统规范

## 为什么做

现有 `development/SPECIFICATION.md` 按规则产生的先后顺序连续编号，78 个一级章节在阅读时缺少
稳定的主题分区。读者容易看到一条条正确规则，却不容易先建立“工作项如何驱动流程、文档如何承载
信息、状态和证据如何闭环、脚本与智能体如何分工”的整体模型。

本次以已经生成的思维导图为信息架构，把现行规范重排为九个相互衔接的主题章节，并改写成可以连续
阅读的文章。思维导图只提供结构，不替代正文细节；现行规范中已经落地的规则必须完整保留。

## 成功标准

- [x] `SPECIFICATION.md` 使用九个主题章节组织全文，章节顺序与思维导图主干一致。
- [x] 正文以解释性段落为主，清单、表格和代码块只用于枚举、状态、流程和示例。
- [x] 现行工作类型、风险、影响面、流程叠加、阶段状态、文档模型、目录、追踪和工具规则均被保留。
- [x] 规范内部术语与 `development/README.md`、Schema 和 `scripts/work` 的当前行为一致。
- [x] 文档系统检查、链接检查、重复章节检查和差异格式检查通过。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-006` 查看实际进度。

## 待确认项

暂无。

## 风险点

- RISK-001：重排时遗漏现行规则。通过建立旧章节到新章节的覆盖映射、关键词检查和全文复核缓解。
- RISK-002：把思维导图的概括误当成规范正文，导致约束失去精度。新正文保留字段、状态、命令和目录
  示例，并用段落解释它们之间的关系。
- RISK-003：文字重写意外改变已实现工具语义。以当前 `SPECIFICATION.md`、`development/README.md` 和
  `scripts/work` 为事实基线，发现分歧时不顺手修改工具。

## 影响面

只修改 `development/SPECIFICATION.md`、本工作项的过程文档，以及创建 WORK-006 时由工具单调推进的
`development/index.json`。不会修改业务代码、Schema、模板、脚本、既有工作项数据、公共接口、
数据库、权限、部署或用户可观察行为。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-25：完成九章文章结构重写，并核对现行规则、术语、命令和目录示例。
- 2026-08-25：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-08-25：流程阶段 复核：ready → doing。原因：开始复核内容覆盖、规则语义和范围边界
- 2026-08-25：流程阶段 复核：doing → done。原因：九个主题覆盖旧规范全部主题，现行状态、流程、目录与工具语义未改变
- 2026-08-25：根据文档、任务与验证事实刷新状态：implemented → verified。
- 2026-08-25：流程阶段 上线：ready → skipped。原因：纯规范重排，无运行时发布或部署动作
- 2026-08-25：流程阶段 观察：pending → skipped。原因：无线上行为变化，验证由文档与工具回归检查完成
