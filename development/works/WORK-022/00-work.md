---
id: "WORK-022"
type: "work"
title: "微调双端应用布局页脚"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: ["WORK-020"]
related: ["FEATURE-004", "EXPERIENCE-010", "TASK-030", "VERIFY-022", "MEMORY-017", "VERIFY-020"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "accessibility"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-08-28"
updated_at: "2026-09-01"
work_type: "product"
---

# WORK-022：微调双端应用布局页脚

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 需求澄清 | ✔ 完成 | 必需 | WORK-022 `implemented` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | ✔ 完成 | 必需 | FEATURE-004 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | ✔ 完成 | 必需 | EXPERIENCE-010 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 开发计划 | ⊘ 跳过 | 可选 | — | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-030 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-030 `done` | 按任务实施，产出代码与测试 |
| 复核 | ○ 就绪 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ▶ 进行中 | 必需 | VERIFY-022 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | · 未开始 | 必需 | MEMORY-017 `draft` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

需要人工确认 FEATURE-004、EXPERIENCE-010 与 TASK-030，并在后续消息中明确允许执行。本轮只整理
复核意见，不修改 Web 实现。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：记录 WORK-020 人工复核意见：管理端移除 Footer，用户端 Footer 保留语义但取消与 Main
  的视觉分区；等待文档审核和执行授权。
- 2026-08-28：用户批准 FEATURE-004、EXPERIENCE-010 与 TASK-030 并允许实施；两个 Shell 的 Footer
  微调和浏览器回归已完成，验证证据见 VERIFY-022。
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-28：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
