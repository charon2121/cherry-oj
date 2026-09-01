---
id: "WORK-002"
type: "work"
title: "交付 C++ ACM 答题闭环"
status: "todo"
work: null
owners: ["product/owner"]
risk: "medium"
impact: "system"
concerns: ["data", "security", "accessibility"]
depends_on: []
related: ["FEATURE-001", "EXPERIENCE-001", "DESIGN-002", "DECISION-002", "PLAN-002", "TASK-002", "VERIFY-002", "MEMORY-002"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "accessibility", "data", "security"]
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: ["确认 WA 时允许普通用户查看的测试点信息", "确认内部 MVP 的发布环境口径"]
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-08-24"
updated_at: "2026-09-01"
work_type: "product"
---

# WORK-002：交付 C++ ACM 答题闭环

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 需求澄清 | ✖ 受阻 | 必需 | WORK-002 `todo` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | · 未开始 | 必需 | FEATURE-001 `draft` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | · 未开始 | 必需 | EXPERIENCE-001 `draft` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | · 未开始 | 必需 | DESIGN-002 `draft` | 确定技术方案、边界与取舍 |
| 技术决策 | · 未开始 | 必需 | DECISION-002 `draft` |  |
| 开发计划 | · 未开始 | 必需 | PLAN-002 `draft` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | · 未开始 | 必需 | TASK-002 `todo` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | · 未开始 | 必需 | TASK-002 `todo` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | · 未开始 | 必需 | VERIFY-002 `draft` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | · 未开始 | 必需 | MEMORY-002 `draft` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- UNKNOWN-001（resolved）：MVP 不开放注册，由管理员预置普通用户账号；首个 ADMIN 由一次性离线命令
  初始化。身份与会话细节以已确认的 DECISION-009 为准。
- UNKNOWN-002（blocking）：普通用户在 WA 时允许看到多少测试点信息。
- UNKNOWN-003（blocking）：内部 MVP 的“发布”是进入主干演示环境，还是必须有独立部署环境。

## 变更记录

- 2026-08-24：创建工作项并生成初始流程。
- 2026-08-24：从旧 REQ-0001 完整迁入用户流程、规则、验收场景和未决问题；尚未人工确认。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-26：负责人确认管理员预置账号方案，解决 UNKNOWN-001；其余两个待确认项保持 blocking。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
