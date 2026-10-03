---
id: "WORK-048"
type: "work"
title: "Linux 沙箱隔离与资源计量硬化"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["security", "reliability", "performance"]
depends_on: []
related: ["IMPROVEMENT-004", "DESIGN-042", "DECISION-026", "PLAN-032", "TASK-093", "VERIFY-049", "MEMORY-035", "TASK-094", "TASK-095", "TASK-096", "TASK-097", "TASK-098", "TASK-099", "TASK-100", "TASK-101", "TASK-102", "TASK-103", "TASK-108"]
implements: []
verifies: []
tags: []
required_documents: ["improvement", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "performance", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-09"
updated_at: "2026-09-10"
work_type: "improvement"
---

# WORK-048：Linux 沙箱隔离与资源计量硬化

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-048 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 改进说明与目标指标 | ✔ 完成 | 必需 | IMPROVEMENT-004 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-042 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-026 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-032 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 实施任务 | ✔ 完成 | 必需 | TASK-093 `done`、TASK-094 `done`、TASK-095 `done`、TASK-096 `done`、TASK-097 `done`、TASK-098 `done`、TASK-099 `done`、TASK-100 `done`、TASK-101 `done`、TASK-102 `done`、TASK-103 `done`、TASK-108 `cancelled` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 实施 | ✔ 完成 | 必需 | TASK-093 `done`、TASK-094 `done`、TASK-095 `done`、TASK-096 `done`、TASK-097 `done`、TASK-098 `done`、TASK-099 `done`、TASK-100 `done`、TASK-101 `done`、TASK-102 `done`、TASK-103 `done`、TASK-108 `cancelled` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-049 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-035 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-09：创建工作项并生成初始流程。
- 2026-09-09：意图闸：passed。原因：确认目标和验收标准，允许按 TASK-093 开展只读探测与实施边界冻结
- 2026-09-09：检查项 rollback 记录结论：通过。原因：PLAN-032 已明确 TASK-093 无远端写入；后续仅回退项目独占资源及已硬化版本，无已硬化版本则停止不可信服务，实际恢复验证留 TASK-099
- 2026-09-10：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-10：检查项 automated-tests 记录结论：通过。原因：VERIFY-049记录TASK-098最终race/vet与Linux构建、1000次/故障套件，TASK-099十五项部署测试及TASK-100真实业务；机器重启单列未覆盖
- 2026-09-10：检查项 performance 记录结论：通过。原因：VERIFY-049记录CPU累计预算、5ms观测粒度、约1.052s运行区间及4.207/4.343ms超限误差，1000次回收和双并发基线；不宣称普适误差上界
- 2026-09-10：检查项 impact-analysis 记录结论：通过。原因：TASK-100已核对ACTIVE全局影响、新修订部署校准、发布前后回退限制；旧节点保留，原ZIP和server/web生产代码未变
- 2026-09-10：检查项 independent-review 记录结论：通过。原因：VERIFY-049 TASK-098记录已获用户授权的Boyle独立隔离/失败回收复核，TASK-103两项P2修复后原复核者确认充分且最终Linux套件通过；不扩大为未做的部署专项独立审计
- 2026-09-10：检查项 security 记录结论：通过。原因：VERIFY-049首站namespace/权限/seccomp/文件边界/节点资源与正式七cap收敛实测通过；仅既定可信宿主loopback与共享内核边界，不声称其它Linux或机器重启已验证
- 2026-09-10：状态变更：implemented → doing。原因：用户要求补齐AC-004整机重启恢复，重新打开实施范围，先补充任务与具体恢复方案，不影响已完成TASK事实
- 2026-09-10：状态变更：doing → todo。原因：新增重启恢复任务需要重新界定实施范围；控制面要求未完成任务拆分前回到todo，已完成任务和意图闸事实保留
- 2026-09-10：检查项 reliability 记录结论：通过。原因：按用户明确留置机器重启后的AC-004范围：TASK-098最终1000次与并发、容量/聚合OOM及崩溃恢复通过；TASK-099正式服务故障回收、缺文件拒绝、卸载恢复通过；整机重启与隧道自动重连未验证，留置TASK-108
- 2026-09-10：检查项 rollback 记录结论：通过。原因：TASK-099已实测正式服务停止/恢复、缺配置恢复及卸载后按回执恢复，保留数据与账号；TASK-100记录ACTIVE切换及新修订发布后的回退限制，不切host、不复制校准；TASK-108未实施无远端变更
- 2026-09-10：流程阶段 复核：ready → done。原因：核对最终TASK-098/103独立复核修复闭环、TASK-099原生部署与恢复和TASK-100新身份校准及真实业务证据，无已知未解决阻断；用户明确将机器重启移出本轮范围，保留共享内核和平台支持限制，未代签人工验收
- 2026-09-10：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-10：验收闸：passed。原因：确认当前首站沙箱硬化与业务闭环验收通过；重启恢复留置后续，接受已记录的平台及运维限制
- 2026-09-10：根据文档、任务与验证事实刷新状态：implemented → verified。
