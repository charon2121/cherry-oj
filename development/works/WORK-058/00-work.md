---
id: "WORK-058"
type: "work"
title: "按信任与部署边界重切判题引擎模块结构"
status: "implemented"
work: null
owners: ["team/judge-engine"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security"]
depends_on: []
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041", "TASK-125", "VERIFY-059", "MEMORY-044", "TASK-126", "TASK-127", "TASK-128", "TASK-129", "TASK-130", "TASK-131"]
implements: []
verifies: []
tags: []
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-14"
updated_at: "2026-09-15"
work_type: "maintenance"
---

# WORK-058：按信任与部署边界重切判题引擎模块结构

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
| 改动说明与边界 | ✔ 完成 | 必需 | CHANGE-014 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-051 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-035 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-041 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-125 `done`、TASK-126 `done`、TASK-127 `done`、TASK-128 `done`、TASK-129 `done`、TASK-130 `done`、TASK-131 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-125 `done`、TASK-126 `done`、TASK-127 `done`、TASK-128 `done`、TASK-129 `done`、TASK-130 `done`、TASK-131 `done` | 按任务实施，产出代码与测试 |
| 复核 | ▶ 进行中 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-059 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-044 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- 本轮只交付文档，尚未开始实施。意图闸需由用户签署，智能体不代签。
- 用户已就四件事给出结论，已记录进 DECISION-035：接受全量轮换环境指纹；零隔离后端改名 devhost
  并默认拒绝启动；错误消息统一为英文；结构说明文档按新结构重写。这些结论在意图闸上一次性确认。
- 实施会改变全部判题节点的环境标识。新标识只会以「已登记」状态出现，需要有人显式把它切换为
  「生效」并重新完成一次测试数据部署，判题才会重新路由到新环境。该切换动作不在本工作范围内，
  由人在实施完成后执行。
- 错误消息改为英文后，现有针对中文消息的日志检索与告警规则会失配，需要在同一批次内更新。

## 变更记录

- 2026-09-14：创建工作项并生成初始流程。
- 2026-09-14：完成 CHANGE-014、DESIGN-051、DECISION-035、PLAN-041 与七份 TASK，VERIFY-059 与
  MEMORY-044 先记录方案与已成立的判断；上游文档置为 review，等待人工审核与意图闸。
- 2026-09-14：意图闸：passed。开始 S1 实施。
- 2026-09-14：**范围升级（用户已同意）。** S1 推送后 CI 报必跑用例缺失：判题引擎的自动检查
  清单按「包路径 + 测试名」记录哪些测试必须真的跑过，而本工作要做的正是调整代码的组织位置，
  测试一换位置，清单就对不上。初版计划没有考虑这层关联。经用户确认，把该清单纳入各阶段的可
  修改范围，并要求在同一次提交里一起更新，使清单任何时候都不过期；同时明确只允许更新位置，
  不得增删检查项或放宽要求。已更新 PLAN-041 与 TASK-125～131 的范围说明。
- 2026-09-14：意图闸：passed。原因：签署重构方案
- 2026-09-14：检查项 rollback 记录结论：通过。原因：PLAN-041 已明确以 a611be3 为基线、逐阶段 git revert，只撤销本工作 diff，不触碰 WORK-049/050 与 apps/server 既有增量；S3 撤销后若已人工切过 ACTIVE 需按节点协议切回，该动作已标为人工；本工作不执行远端部署，无部署回退动作
- 2026-09-15：**范围再次扩充（用户已同意）。** S2 搬动目录后发现，指向判题引擎内部位置的引用
  不止自动检查清单一处：持续集成的工作流配置里有一处测试数据目录，指向旧位置后会让「judge +
  sandbox 回退」那条检查失败；另有一份手工操作说明里的路径会过时。经用户确认，把这两处纳入
  各阶段可修改范围，同样只允许改路径，不改流程结构与操作语义。已更新 PLAN-041 与
  TASK-125～131。
- 2026-09-15：TASK-126 的两条完成标准按事实改写：顶层共享目录中 `config` 的下沉归 S3；
  命令行入口以「不含服务逻辑」为准，不再用行数作门槛。原写法与计划分期不一致，属文档缺陷。
- 2026-09-15：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-15：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-15：检查项 automated-tests 记录结论：通过。原因：CI run 34938772322（66a0a35，attempt 1）一次通过 12 个 job；必需回归汇总 PASS，93 项全过：basic 5/5、kernel 63/63、native 10/10、business 15/15。本机另跑 gofmt/go vet（darwin 与 linux-amd64 两个目标）/go test -race 全绿
- 2026-09-15：检查项 reliability 记录结论：通过。原因：kernel 63 项覆盖隔离、计量与故障回收，native 10 项覆盖原生安装、权限与服务恢复，business 15 项走真实页面到 Kafka 的完整闭环，全部通过。S5 的取消顺序缺陷已有专门回归用例 TestCancelInputMustNotMakeNormalRunLookCancelled；S6 修复的 fault_batch ENODEV 守卫使故障批次不再误报
- 2026-09-15：检查项 impact-analysis 记录结论：通过。原因：跨语言影响已逐条核对：Java 侧用户可见文案一律按错误码分支（NO_ONLINE_JUDGE_NODE 等），不匹配引擎错误正文，business 15 项全绿予以印证；结构化日志的 event 字段名一个未变，仅 error 字段取值变化，完整新旧对照见 TASK-131 附录；唯一把英文正文带到人眼前的是 language_calibration.error_message 在管理台的显示。contracts/、apps/server、apps/web、go.mod、go.sum 未改动
- 2026-09-15：检查项 security 记录结论：通过。原因：信任边界改为编译期强制：sandbox 引用 helperd/internal、judge 引用 sandbox/internal、helperd 引用 judge/internal、cmd 引用任一服务 internal，四条均在 S2 构造验证为编译失败。零隔离的 devhost 默认拒绝启动，需显式 allowUnsafeBackend；linux 后端在开 HTTP 端口前做启动冒烟，S2 修复了该闸此前因错误处理 bug 而形同虚设的问题。helperd 的 root 自检（CGO_ENABLED=0、root 托管、无 setuid、manifest 摘要钉住、三身份分离）与 SO_PEERCRED + socket 权限双重认证未削弱。本次未改变任何特权、身份或网络暴露面
- 2026-09-15：验收闸：passed。原因：CI 34938772322 一次通过，93 项必需回归全绿，AC-001 至 AC-012 均有证据。验收通过。
