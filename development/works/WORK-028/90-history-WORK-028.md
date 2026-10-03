---
id: "WORK-028"
type: "work"
title: "修复后台用户列表偶发误跳登录页"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["security"]
depends_on: []
related: ["ISSUE-006", "DESIGN-022", "DECISION-016", "PLAN-018", "TASK-046", "VERIFY-029", "MEMORY-022"]
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

# WORK-028：修复后台用户列表偶发误跳登录页

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-006 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-022 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-016 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-018 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-046 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-046 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-029 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-022 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-02：创建工作项并生成初始流程。
- 2026-09-02：意图闸：passed。原因：确认 Gateway 在下游 JWT 401 后单次恢复，只有登录授权失效才退出，并允许实施
- 2026-09-02：检查项 rollback 记录结论：通过。原因：PLAN-018 明确无数据迁移，回退仅撤销 Gateway forced exchange 与单次重试，旧 Redis Session 结构兼容
- 2026-09-02：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-02：检查项 automated-tests 记录结论：通过。原因：Gateway 55/55、服务端 130 项零失败（1 项环境跳过）、Web 109/109、typecheck/build 与 2 项 Admin E2E 通过
- 2026-09-02：检查项 impact-analysis 记录结论：通过。原因：复核 Gateway 其它 BFF：Admin problems 的资源 401 映射 502；Admin 用户写请求未自动重放；实际 diff 未越过 TASK-046 边界
- 2026-09-02：检查项 independent-review 记录结论：通过。原因：用户选择 Security Review；独立安全复核 branch changes 未发现 finding
- 2026-09-02：检查项 security 记录结论：通过。原因：Security Review 无 finding；grant/JWT/Cookie 未进入响应或新增日志，真实 grant 401 仍逐 WebSession fail-closed
- 2026-09-02：流程阶段 复核：ready → done。原因：独立 Security Review 无 finding，影响面、并发回写、错误分类、凭据边界与任务范围复核完成
- 2026-09-02：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-02：验收闸：passed。原因：后台用户列表 JWT 恢复修复验收通过
- 2026-09-02：根据文档、任务与验证事实刷新状态：implemented → verified。
