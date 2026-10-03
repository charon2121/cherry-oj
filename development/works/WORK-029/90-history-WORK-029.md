---
id: "WORK-029"
type: "work"
title: "新增页面主题切换入口"
status: "verified"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: []
related: ["FEATURE-008", "EXPERIENCE-015", "TASK-047", "VERIFY-030", "MEMORY-023"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "accessibility"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-09-02"
updated_at: "2026-09-02"
work_type: "product"
---

# WORK-029：新增页面主题切换入口

<!--
本文件是工作项的控制面入口，只回答一个问题：做到哪一步了。

「为什么做、怎样算完成、有什么风险、影响哪里」属于定义层文档（FEATURE / CAPABILITY / ISSUE /
CHANGE / IMPROVEMENT），不要在这里重复。同一个问题在两处各自表述一定会漂移，而本文件既不在
信息优先级链上，也不携带 REQ / AC 锚点，冲突时无法判定以谁为准。

「流程」一节由 `scripts/work` 生成，请勿手工编辑；阶段状态的真源是各文档、TASK 与 VERIFY
自己的状态，这里只是视图。
-->

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 需求澄清 | ✔ 完成 | 必需 | WORK-029 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | ✔ 完成 | 必需 | FEATURE-008 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | ✔ 完成 | 必需 | EXPERIENCE-015 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 开发计划 | ⊘ 跳过 | 可选 | — | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-047 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-047 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-030 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-023 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-02：创建工作项并生成初始流程。
- 2026-09-02：因另一工作树已分配 WORK-028，将本工作迁移为 WORK-029，并同步避让附属文档编号。
- 2026-09-02：意图闸：passed。原因：确认主题切换入口、交互和本地偏好边界
- 2026-09-02：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-02：流程阶段 复核：ready → done。原因：已逐项复核 TASK-047 允许范围、主题状态单一真源、无障碍反馈和响应式调整，未发现越界或实现偏差
- 2026-09-02：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-02：检查项 automated-tests 记录结论：通过。原因：Node 24 下 Vitest 31 文件 112 项、生产构建、Storybook 和 Playwright 29 项全部通过
- 2026-09-02：检查项 accessibility 记录结论：通过。原因：键盘、动态可访问名称、礼貌级播报、forced-colors、reduced-motion、双主题及 320px 双端页头均通过
- 2026-09-02：验收闸：passed。原因：确认双端主题切换、持久化和响应式体验符合预期
- 2026-09-02：根据文档、任务与验证事实刷新状态：implemented → verified。
