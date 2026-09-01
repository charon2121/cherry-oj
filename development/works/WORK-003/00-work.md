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

# WORK-003：按工作项聚合开发文档

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。 -->

```mermaid
flowchart TD
    definition["✔ 改动说明与边界"]
    design["✔ 技术方案"]
    decision["✔ 技术决策"]
    plan["✔ 开发计划"]
    tasks["✔ 开发任务"]
    development["✔ 开发"]
    review["✔ 复核"]
    verification["✔ 回归验证"]
    release["⊘ 上线"]
    observe["✔ 观察"]
    memory["✔ 项目记忆"]
    definition --> design --> decision --> plan --> tasks --> development --> review --> verification --> release --> observe --> memory
    classDef done stroke-width:2px
    classDef doing stroke-width:3px
    classDef skipped stroke-dasharray:4 3
    classDef blocked stroke-width:3px,stroke-dasharray:2 2
    class definition,design,decision,plan,tasks,development,review,verification,observe,memory done
    class release skipped
```

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-002 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-003 `approved` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-003 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-003 `approved` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-003 `verified` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-003 `verified` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-003 `approved` | 用可复现的证据确认要求逐条满足 |
| 上线 | ⊘ 跳过（手动） | 可选 | — | 把成果交付出去 |
| 观察 | ✔ 完成（手动） | 必需 | — | 交付后观察实际结果，确认没有引入新问题 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-003 `approved` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

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
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
