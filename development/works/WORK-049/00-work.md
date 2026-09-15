---
id: "WORK-049"
type: "work"
title: "按命令执行顺序重构 Go 判题引擎源码"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability"]
depends_on: []
related: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-104", "VERIFY-050", "MEMORY-036", "TASK-105", "TASK-106", "TASK-107", "WORK-050"]
implements: []
verifies: []
tags: []
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-10"
updated_at: "2026-09-14"
work_type: "maintenance"
---

# WORK-049：按命令执行顺序重构 Go 判题引擎源码

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
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-013 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-043 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-027 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-033 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-104 `done`、TASK-105 `done`、TASK-106 `done`、TASK-107 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-104 `done`、TASK-105 `done`、TASK-106 `done`、TASK-107 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成（手动） | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-050 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ▶ 进行中 | 必需 | MEMORY-036 `review` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- 意图闸已于 2026-09-13 由用户签署；WORK-050 已验收。原有签署前置条件已满足。
- 用户已允许按新方案实施，沙箱和判题侧已完成本地重构与检查；R8 独立阅读和完整 Linux 93/93 项回归已通过，人工验收闸待用户签署。
- 候选编码规则仍未提升为全局规范；其长期适用性留到源码阅读验收后确认。

## 变更记录

- 2026-09-10：创建工作项并生成初始流程。
- 2026-09-13：意图闸：passed。原因：确认基于已冻结的 CI 基线开展 judge-engine 重构，允许实施

- 2026-09-13：按用户要求建立具体重构计划；当前轮次只完成计划文档，保留既有人工签署。
- 2026-09-13：检查项 rollback 记录结论：通过。原因：PLAN-033已明确以8fe6e41及实施前工作区快照分批撤销，仅撤销本工作diff，保留人签署和其他WORK/数据；未发生远端部署，无部署回退动作

- 2026-09-14：按用户要求修订后续重构计划，明确各部分负责什么、资源由谁管理以及完成条件；本轮仅更新文档，原有签署和源码保留。

- 2026-09-14：依用户“开始重新重构”的授权完成新方案的本地实施，原有签署保持；完整平台回归与独立阅读尚待完成。
- 2026-09-14：流程阶段 复核：ready → doing。原因：按独立审查报告核对七问、四条路径与 R0 对照证据
- 2026-09-14：流程阶段 复核：doing → done。原因：独立阅读无阻断卡点或确认的新缺陷，结果与最终候选模块一致；人工验收另行签署
- 2026-09-14：检查项 definition 记录结论：通过。原因：CHANGE-013 意图与不变条件保持原签署，VERIFY-050 六项 AC 已分别给出证据，未扩大范围
- 2026-09-14：检查项 scope 记录结论：通过。原因：候选仅包含 apps/judge-engine 的重构；CI/harness/contracts/依赖未改，WORK-050 和 server 既有增量保留
- 2026-09-14：检查项 automated-tests 记录结论：通过。原因：候选 a611be3 在 CI 34830811953/1 完整 93/93 及全部必需 job 通过；52 个固定 Go 测试和清理证据复验通过
- 2026-09-14：检查项 impact-analysis 记录结论：通过。原因：R0 对照覆盖协议、数值、权限、挂载顺序、预算、错误归因与移交；四条源码路径及真实 Linux 回归通过
- 2026-09-14：检查项 independent-review 记录结论：通过。原因：未参与实现的 r8_reading 独立七问与正常/超时/取消/部分启动失败阅读均通过，具体符号和限制见 VERIFY-050
- 2026-09-14：检查项 rollback 记录结论：通过。原因：保留 R0 与 R8 实际快照及模块精确补丁；独立验证分支未合并，不在 main 清理工作区，只按本工作增量回退
- 2026-09-14：检查项 reliability 记录结论：通过。原因：真实 Linux namespace/mount/CPU/OOM、1000 次、并发、故障与服务恢复通过，整组/挂载/账户等清理无残留
- 2026-09-14：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-14：流程阶段 复核：doing → done。原因：impact-analysis 与 independent-review 已登记通过，七問四路径和最终候选核对完成，无待处理审查项
