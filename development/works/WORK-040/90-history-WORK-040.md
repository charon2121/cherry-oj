---
id: "WORK-040"
type: "work"
title: "重构判题节点注册与测试数据交付"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["reliability", "compatibility", "security", "observability"]
depends_on: []
related: ["ISSUE-012", "DESIGN-034", "TASK-071", "VERIFY-041", "DECISION-022", "PLAN-026", "MEMORY-030", "TASK-072", "TASK-073", "TASK-074"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "compatibility", "observability", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: true
public_api_change: true
security_sensitive: true
user_visible: true
created_at: "2026-09-06"
updated_at: "2026-09-06"
work_type: "fix"
---

# WORK-040：重构判题节点注册与测试数据交付

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-012 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-034 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-022 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-026 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-071 `done`、TASK-072 `done`、TASK-073 `done`、TASK-074 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-071 `done`、TASK-072 `done`、TASK-073 `done`、TASK-074 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-041 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-030 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

确认后实施：用真实判题节点自注册、心跳和节点侧数据安装替代 profile seed 与共享目录假设；迁移现有
本地开发环境，生产不自动切换不同指纹的判题环境。

## 变更记录

- 2026-09-06：创建工作项并生成初始流程。
- 2026-09-06：负责人否决 default-profile 局部方案，工作升级为判题节点生命周期与数据交付的系统级重构。
- 2026-09-06：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-06：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-06：意图闸：passed。原因：确认采用判题节点自注册、心跳和节点侧测试数据安装的系统级重构方案
- 2026-09-06：检查项 rollback 记录结论：通过。原因：已核对 PLAN-026：新增 V2 表保留 V1 数据；端到端通过前默认 legacy-local，回退仅切配置及挂载，不回滚 schema 或删除资产
- 2026-09-06：状态变更：todo → ready。原因：意图闸已签署，上游设计与任务边界已通过校验
- 2026-09-06：状态变更：ready → doing。原因：已完成前三阶段，正在收束真实回滚与全仓验证
- 2026-09-06：检查项 impact-analysis 记录结论：通过。原因：TASK 边界已核对；仅新增 V2 和节点协议，保留 WORK-038/039 及 V1/已发布快照/JudgeInput，细节见 VERIFY-041
- 2026-09-06：检查项 independent-review 记录结论：通过。原因：用户授权的只读 review_work040 最终复核通过，六项发现均修复关闭，无剩余确定问题
- 2026-09-06：检查项 automated-tests 记录结论：通过。原因：最终 Java clean verify 150/150 无跳过；Go race/vet；Web 136 项单测、31 项浏览器测试全部通过
- 2026-09-06：检查项 cross-module-regression 记录结论：通过。原因：真实五 Java 服务、MySQL/Redis、Go Judge/sandbox、Web 链路通过上传/部署/校准、停机恢复、迁移回滚
- 2026-09-06：检查项 compatibility 记录结论：通过。原因：已填充 V1 经 Flyway V2 逐字段不变；真实 legacy 回滚及新环境新修订通过，已发布快照不变，ZIP/JWT 回归通过
- 2026-09-06：检查项 observability 记录结论：通过。原因：保存公开 requestId、节点元数据与逐节点回执证据；日志覆盖注册/恢复/安装/固定失败码，不输出控制令牌或源码
- 2026-09-06：检查项 reliability 记录结论：通过。原因：精确租约边界、旧会话 fencing、条件回执撤销、原子安装清理、重启幂等、整体超时和真实切换 guard 均通过
- 2026-09-06：检查项 security 记录结论：通过。原因：独立控制令牌 401/合法 200、路径/ZIP/UTF-8/资源上限、严格回执字段和固定错误体通过；生产令牌必填
- 2026-09-06：检查项 rollback 记录结论：通过。原因：真实切回 legacy-local 和只读挂载后 C++ AC，原节点回执及标定不变，再恢复 node-remote 和原环境通过
- 2026-09-06：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-06：流程阶段 复核：ready → done。原因：用户授权的独立只读复核已完成，六项发现全部修复关闭；影响检查与复核检查均 pass
- 2026-09-06：验收闸：passed。原因：手工验收未发现问题，确认本次重构完成
- 2026-09-06：根据文档、任务与验证事实刷新状态：implemented → verified。
