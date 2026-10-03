---
id: "WORK-030"
type: "work"
title: "修复后台题目列表间歇性 502"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["security"]
depends_on: []
related: ["ISSUE-007", "DESIGN-023", "DECISION-017", "PLAN-019", "TASK-048", "VERIFY-031", "MEMORY-024"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: true
created_at: "2026-09-02"
updated_at: "2026-09-02"
work_type: "fix"
---

# WORK-030：修复后台题目列表间歇性 502

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-007 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-023 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-017 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-019 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-048 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-048 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-031 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-024 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-02：创建工作项并生成初始流程。
- 2026-09-02：检查项 definition 记录结论：通过。原因：现场 requestId、触发条件、已确认直接原因与八项可验证 AC 已完整记录
- 2026-09-02：检查项 scope 记录结论：通过。原因：实现限定于 Gateway Admin 普通 JSON GET 与 problem-service security 测试/条件式修复，写请求、下载、contracts、Web、数据库均禁止
- 2026-09-02：检查项 rollback 记录结论：通过。原因：Gateway 恢复与可选 JWKS 改动可分别回退，均无数据或公开契约迁移
- 2026-09-02：意图闸：passed。原因：用户明确确认方案通过并允许开始实施 WORK-030
- 2026-09-02：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-02：检查项 impact-analysis 记录结论：通过。原因：改动仅影响 Gateway Admin 普通 JSON GET 恢复与 problem-service 安全观测/测试；公开契约、Web、数据、写请求和下载未变
- 2026-09-02：检查项 independent-review 记录结论：通过。原因：独立安全复核未发现可操作 finding，确认 401 单次 GET 重试和非重放边界正确
- 2026-09-02：检查项 automated-tests 记录结论：通过。原因：Gateway 57 项及服务端 clean verify 135 项通过，0 failure、0 error，1 项既有 Linux 集成测试跳过
- 2026-09-02：检查项 security 记录结论：通过。原因：K1→K2 16 路并发只刷新一次且全部成功，未知/错误签名/过期 token fail-closed，日志脱敏测试通过
- 2026-09-02：流程阶段 复核：ready → done。原因：影响分析、独立安全复核、范围与重放边界检查均通过
- 2026-09-02：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-02：验收闸：passed。原因：用户明确授权签署 WORK-030 验收闸，接受当前实现、验证结果与已记录剩余风险
- 2026-09-02：根据文档、任务与验证事实刷新状态：implemented → verified。
