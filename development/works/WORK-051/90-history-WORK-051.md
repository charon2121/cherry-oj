---
id: "WORK-051"
type: "work"
title: "修复沙箱连续请求完成与容量归还的竞态"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security"]
depends_on: []
related: ["ISSUE-015", "DESIGN-045", "DECISION-029", "PLAN-035", "TASK-114", "VERIFY-052", "MEMORY-038", "WORK-050"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-10"
updated_at: "2026-09-11"
work_type: "fix"
---

# WORK-051：修复沙箱连续请求完成与容量归还的竞态

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-015 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-045 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-029 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-035 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-114 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-114 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-052 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-038 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-10：创建工作项并生成初始流程。
- 2026-09-10：意图闸：passed。原因：同意连续请求时序修复方案及实施边界
- 2026-09-10：检查项 rollback 记录结论：通过。原因：PLAN-035限定回退helper与清单改动，无现有节点和数据迁移，保留失败证据
- 2026-09-10：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-10：检查项 automated-tests 记录结论：通过。原因：946e528在CI 34470867753的8job与63内核项全绿，45必需Go测试无跳过，制品下载复验通过
- 2026-09-10：检查项 impact-analysis 记录结论：通过。原因：生产只改helper连接收尾，私有消费者配套EOF，未改公开契约/权限/限额/现有节点；未来新身份部署校准
- 2026-09-10：检查项 reliability 记录结论：通过。原因：受控时序、EOF超时/reset、1000次、并发、故障与容量回归通过，最终任务/挂载/cgroup为空
- 2026-09-10：检查项 automated-tests 记录结论：未通过。原因：946e528曾8job全绿，最新b03e6bd的Linux63项仍通过但旧Java语言功能测试5秒失败，另列待审TASK-115
- 2026-09-10：检查项 automated-tests 记录结论：通过。原因：TASK-115已完成独立语言功能期限诊断和调整；e44f9b4及a0e1b35现有8项CI全绿，helper与946e528一致，63项内核和完整清理证据核验通过
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：2026-09-11用户授权Zeno独立只读审查7a66b35..946e528及当前一致性，无本次引入的阻断发现；两项测试覆盖限制已入VERIFY-052
- 2026-09-11：检查项 security 记录结论：通过。原因：独立复核确认Completion加正常EOF、先清理及fatal停服再归还槽位关闭socket，容量和期限未放宽；输入Close契约与测试证据限制已记录
- 2026-09-11：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-11：流程阶段 复核：ready → done。原因：Zeno独立源码复核完成，实施者核对范围和两项非阻断证据限制，结论入VERIFY-052
- 2026-09-11：验收闸：passed。原因：确认连续请求修复及独立复核结论，接受已记录的测试覆盖限制
- 2026-09-11：根据文档、任务与验证事实刷新状态：implemented → verified。
