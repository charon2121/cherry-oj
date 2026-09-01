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
required_documents: ["change", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-25"
updated_at: "2026-09-01"
work_type: "maintenance"
---

# WORK-006：按思维导图结构重写开发文档系统规范

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-004 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 开发计划 | ⊘ 跳过 | 可选 | — | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-006 `verified` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-006 `verified` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-006 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

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
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
