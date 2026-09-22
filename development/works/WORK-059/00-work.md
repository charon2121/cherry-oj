---
id: "WORK-059"
type: "work"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "cancelled"
work: null
owners: ["team/server"]
risk: "medium"
impact: "multi-module"
concerns: ["observability"]
depends_on: []
related: ["ISSUE-020", "DESIGN-052", "TASK-132", "VERIFY-060"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "observability"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-15"
updated_at: "2026-09-22"
work_type: "fix"
---

# WORK-059：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-020 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-052 `checked` | 确定技术方案、边界与取舍 |
| 修复任务 | · 未开始 | 必需 | TASK-132 `cancelled` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | · 未开始 | 必需 | TASK-132 `cancelled` | 按任务实施，产出代码与测试 |
| 复核 | · 未开始 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-060 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

用户要求本问题先封存，本轮跟进结束。工作系统没有 WORK/TASK 的 archived 状态，按工具规则将
WORK-059 与 TASK-132 记为 cancelled；这表示停止本轮工作，不表示原故障已修复或验收通过。

诊断与异常边界修复以及全部证据继续保留，详见 [VERIFY-060](./70-verify-VERIFY-060.md)。
三轮完整 CI 各 12 个 job、93 项必需回归通过，均未复现原 PATCH 500；根因仍未知，AC-003 未满足，
VERIFY-060 保持 partial，验收闸保持 pending。停止主动复现和后续排查，不新增监控。

将来有可关联的同类失败证据或用户明确要求重新调查时，另开后续工作引用本工作与原始证据；
cancelled 是现有状态机的终态，保留本轮历史，不将封存改写为完成。

## 变更记录

- 2026-09-15：创建工作项并生成初始流程。
- 2026-09-22：用户要求解决问题；补齐原有方案与任务占位内容，完成只读调查和 28 项已有 Python 测试。
  纠正旧定义中的过强归因，明确有界诊断、错误分类与根因修复的验收边界，等待人工意图闸。
- 2026-09-15：检查项 definition 记录结论：通过。原因：现象、证据可得性限制、已排除的身份链路假设、已定位的唯一 500 产出点（gateway ApiProblemHandler.handleUnexpected）、以及分三步且前两步不依赖复现的修复方向均已写明；无稳定复现方式这一点也已如实写出，不以推测代替
- 2026-09-15：检查项 scope 记录结论：通过。原因：范围划清为三步：第一步只动 deploy/sandbox-linux/ci 的证据导出，不碰 Java；第二步与第三步改 apps/server，需本工作自己的授权。第三步明确要求拿到带证据的失败后再动手，不接受推测性改动，也不接受「重跑不再复现」作为修复证据
- 2026-09-22：检查项 definition 记录结论：通过。原因：已区分历史现象、当前代码推断与确定诊断缺口；AC-001 至 AC-004 明确，原 500 未定位不能验收，见 ISSUE-020。
- 2026-09-22：检查项 scope 记录结论：通过。原因：DESIGN-052 与 TASK-132 已明确 CI 诊断、网关安全日志、题目服务异常边界及测试路径；根因需要越界时先更新方案；不改契约、数据库、超时或依赖，不自动重试写请求。
- 2026-09-22：意图闸：passed。原因：同意修复方案
- 2026-09-22：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-22：检查项 impact-analysis 记录结论：通过。原因：只修改任务内诊断、异常边界和测试；无契约、数据库、超时、依赖改动，最终两服务160项回归通过。
- 2026-09-22：检查项 observability 记录结论：通过。原因：requestId、来源校验、固定分类、controller前后异常及安全日志已有反例与组件验证，见VERIFY-060；原事故根因未知不作为已修复。
- 2026-09-22：检查项 automated-tests 记录结论：通过。原因：Python 124项通过；Java全量verify及最终修改模块160项通过，既有真实Linux测试限制已记录；CI 35707446283的12个job与93项必需回归全绿。原故障根因仍未知，AC-003未满足。
- 2026-09-22：状态变更：doing → cancelled。原因：用户要求暂时封存，停止本轮跟进；保留诊断修复与证据，原500根因未确认，未验收。
