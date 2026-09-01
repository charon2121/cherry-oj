---
id: "WORK-008"
type: "work"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: ["reliability"]
depends_on: []
related: ["WORK-009", "CAPABILITY-001", "EXPERIENCE-002", "DESIGN-006", "PLAN-006", "TASK-008", "VERIFY-008", "MEMORY-006"]
implements: []
verifies: []
tags: []
required_documents: ["capability", "experience", "design", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "reliability"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: true
security_sensitive: false
user_visible: true
created_at: "2026-08-25"
updated_at: "2026-09-01"
work_type: "infra"
---

# WORK-008：建立 Web 到 Gateway 的 REST 基础连通模块

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 需求澄清 | ✔ 完成 | 必需 | WORK-008 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 能力定义 | ✔ 完成 | 必需 | CAPABILITY-001 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 开发体验 / 运维要求 | ✔ 完成 | 必需 | EXPERIENCE-002 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-006 `approved` | 确定技术方案、边界与取舍 |
| 开发计划 | ✔ 完成 | 必需 | PLAN-006 `approved` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-008 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-008 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-008 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-006 `approved` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → ready。
- 2026-08-25：完成 Gateway、Web 与 Vite proxy 的真实 REST 联调。
- 2026-08-25：根据文档、任务与验证事实刷新状态：ready → doing。
- 2026-08-25：流程阶段 复核：ready → done。原因：已完成接口边界、依赖方向、信息暴露和禁止路径影响复核
- 2026-08-25：根据文档、任务与验证事实刷新状态：doing → verified。
- 2026-08-25：人工复核认为当前设计不通用；建立 WORK-009 重新设计统一协议，并阻止本试验发布。
- 2026-08-25：流程阶段 上线：ready → blocked。原因：人工复核认为现有局部实现不具备通用性，需等待 WORK-009 设计获批后迁移或替换
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
