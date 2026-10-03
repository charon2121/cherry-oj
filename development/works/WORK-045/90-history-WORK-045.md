---
id: "WORK-045"
type: "work"
title: "统一本地judging服务启动入口"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: []
depends_on: []
related: ["CHANGE-011", "TASK-090", "VERIFY-046"]
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
created_at: "2026-09-08"
updated_at: "2026-09-09"
work_type: "maintenance"
---

# WORK-045：统一本地judging服务启动入口

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
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-011 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 开发计划 | ⊘ 跳过 | 可选 | — | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-090 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-090 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成（手动） | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✖ 受阻 | 必需 | VERIFY-046 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-08：创建工作项并生成初始流程。
- 2026-09-08：意图闸：passed。原因：同意统一启动入口与本地配置迁移，允许实施
- 2026-09-08：流程阶段 复核：ready → done。原因：复核路径边界、唯一入口、备份与凭据隔离，未改业务代码
- 2026-09-08：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-08：状态变更：implemented → doing。原因：用户授权追加submission启动修复
- 2026-09-08：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-08：状态变更：implemented → doing。原因：首次自定义运行暴露配套服务凭据缺失，继续修复配置链路
- 2026-09-08：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-08：状态变更：implemented → doing。原因：judging实际进程仍未加载参数，修复本地配置加载依赖
- 2026-09-08：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-09：验收闸：passed。原因：确认 IDEA 服务启动入口统一完成
- 2026-09-09：检查项 automated-tests 记录结论：通过。原因：已有配置加载与产物检查通过，最终联调证据见 VERIFY-048
- 2026-09-09：流程阶段 复核：ready → done。原因：最终配置由 WORK-046 接替，联调证据和用户验收齐全
