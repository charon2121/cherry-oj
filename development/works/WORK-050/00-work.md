---
id: "WORK-050"
type: "work"
title: "将沙箱已验收回归固化为重构 CI"
status: "todo"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security"]
depends_on: ["WORK-048"]
related: ["CAPABILITY-008", "EXPERIENCE-021", "DESIGN-044", "DECISION-028", "PLAN-034", "TASK-109", "VERIFY-051", "MEMORY-037", "TASK-110", "TASK-111", "TASK-112", "TASK-113", "WORK-049", "TASK-115"]
implements: []
verifies: []
tags: []
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-10"
updated_at: "2026-09-11"
work_type: "infra"
---

# WORK-050：将沙箱已验收回归固化为重构 CI

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-050 `todo` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 能力定义 | ✔ 完成 | 必需 | CAPABILITY-008 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 开发体验 / 运维要求 | ✔ 完成 | 必需 | EXPERIENCE-021 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-044 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-028 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-034 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ○ 就绪 | 必需 | TASK-109 `done`、TASK-110 `done`、TASK-111 `done`、TASK-112 `doing`、TASK-113 `todo`、TASK-115 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ▶ 进行中 | 必需 | TASK-109 `done`、TASK-110 `done`、TASK-111 `done`、TASK-112 `doing`、TASK-113 `todo`、TASK-115 `done` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ▶ 进行中 | 必需 | VERIFY-051 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ▶ 进行中 | 必需 | MEMORY-037 `review` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-10：创建工作项并生成初始流程。
- 2026-09-10：意图闸：passed。原因：确认将已验收测试固化为分层CI，使用独立测试环境，作为重构回归基线
- 2026-09-10：检查项 rollback 记录结论：通过。原因：PLAN-034明确仅回退新增CI与驱动，保持原六项CI、已验收实现和用户环境；一次性VM资源按本轮所有权清理
- 2026-09-10：检查项 automated-tests 记录结论：未通过。原因：451b4d6正式CI34476081108为7/8成功，C++与Java功能测试触发5秒wall；内核63项及回收通过，但不能认定整体CI稳定
- 2026-09-10：检查项 automated-tests 记录结论：未通过。原因：TASK-115期限回归与当前8项CI已通过；WORK-050仍缺原生部署/真实业务/总汇总执行，不能将已接入的通过等同全部93项通过
- 2026-09-11：检查项 automated-tests 记录结论：未通过。原因：TASK-111/116原生及生产修复已通过，TASK-112接入及本地74项基础检查通过但真实业务VM尚未执行，TASK-113总汇总未实施；不能声称全部93项通过
