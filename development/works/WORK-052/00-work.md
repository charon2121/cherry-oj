---
id: "WORK-052"
type: "work"
title: "修复沙箱启动通信被信号中断时的处理"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security"]
depends_on: []
related: ["ISSUE-016", "DESIGN-046", "DECISION-030", "PLAN-036", "TASK-116", "VERIFY-053", "MEMORY-039"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-11"
updated_at: "2026-09-11"
work_type: "fix"
---

# WORK-052：修复沙箱启动通信被信号中断时的处理

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-016 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-046 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-030 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-036 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-116 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-116 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成（手动） | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-053 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ▶ 进行中 | 必需 | MEMORY-039 `review` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-11：创建工作项并生成初始流程。
- 2026-09-11：意图闸：passed。原因：确认控制消息中断修复范围及验证方案，允许实施
- 2026-09-11：检查项 rollback 记录结论：通过。原因：本批仅新增边界诊断及阶段日志，可单独回退两份测试；不改变生产或部署。后续候选channel修复亦可独立回退，旧版本已知失败仍需保留。
- 2026-09-11：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-11：检查项 impact-analysis 记录结论：通过。原因：生产仅channel收发中断/FD生命周期；协议、helper状态机及正式预算未变。CI仅安装驱动256MiB由用户明确批准，现有服务器/业务/ZIP未改。
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：用户授权的work052_review完成生产、FD与取消链、测试期限、CI观测和256MiB例外独立复核；唯一观测P2截断回执阻断诊断已修复并复查，无未解决阻断。
- 2026-09-11：检查项 rollback 记录结论：通过。原因：通道修复及CI安装驱动例外可分别回退；旧EINTR与128MiB安装OOM记录保留，回退不构成验收通过。没有现有节点迁移或服务改动。
- 2026-09-11：检查项 automated-tests 记录结论：通过。原因：本地Go vet/race、62项基础单测通过；b699604两台新VM CI34559188597/34559407284九job全过，63内核/52必需Go/10原生及全部报告和清理复验通过。
- 2026-09-11：检查项 reliability 记录结论：通过。原因：确定性收发旧红新绿、连续EINTR与Close、真实shutdown、Poll固定期限、真实信号恢复、1000次/并发/故障通过；没有整请求重试。原始中断来源与旧安装OOM根因未确证，观测限制留VERIFY。
- 2026-09-11：检查项 security 记录结论：通过。原因：FD引用保护与一次交付、错误释放、短写/截断/非法事件仍失败；Linux隔离/权限/24限额回归通过，安装观测只记录本次归属cgroup数字和回执计数，独立复核无阻断。
- 2026-09-11：流程阶段 复核：ready → done。原因：独立复核与影响检查已记录通过，无未解决阻断；源码、观测和安装驱动例外均在授权范围
- 2026-09-11：根据文档、任务与验证事实刷新状态：doing → implemented。
