---
id: "WORK-046"
type: "work"
title: "统一服务配置与环境启动管理"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: []
depends_on: []
related: ["CHANGE-012", "DESIGN-040", "PLAN-031", "TASK-091", "VERIFY-047"]
implements: []
verifies: []
tags: []
required_documents: ["change", "design", "plan", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-08"
updated_at: "2026-09-09"
work_type: "maintenance"
---

# WORK-046：统一服务配置与环境启动管理

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
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-012 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-040 `checked` | 确定技术方案、边界与取舍 |
| 开发计划 | ✔ 完成 | 必需 | PLAN-031 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-091 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-091 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成（手动） | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✖ 受阻 | 必需 | VERIFY-047 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-08：创建工作项并生成初始流程。
- 2026-09-08：意图闸：passed。原因：确认五个后端服务按 application-local.yaml 方案重构，允许实施
- 2026-09-08：检查项 automated-tests 记录结论：通过。原因：后端188项通过1项真实Linux测试跳过，迁移/配置/产物检查通过；完整运行验收另列待确认
- 2026-09-08：检查项 impact-analysis 记录结论：通过。原因：复核后端配置与测试/打包影响，未改业务契约、数据库或Web/Go，私有数据隔离已验证
- 2026-09-08：流程阶段 复核：ready → done。原因：复核配置优先级、迁移等值、凭据配对、测试隔离和产物排除
- 2026-09-08：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-08：状态变更：implemented → doing。原因：用户真实启动发现 IDE 内存仍保留旧参数，补齐迁移验证
- 2026-09-08：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-09：验收闸：passed。原因：确认后端配置统一完成，私有配置不进入 Git
- 2026-09-09：流程阶段 复核：ready → done。原因：配置隔离检查和最终联调证据已完成，用户已验收
