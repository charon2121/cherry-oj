---
id: "WORK-005"
type: "work"
title: "修复开发文档 CI 的 clean checkout 链接校验"
status: "verified"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: []
depends_on: []
related: ["ISSUE-001", "TASK-005", "VERIFY-005"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-25"
updated_at: "2026-09-01"
work_type: "fix"
---

# WORK-005：修复开发文档 CI 的 clean checkout 链接校验

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-001 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 修复任务 | ✔ 完成 | 必需 | TASK-005 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-005 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-005 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：确认 Git 跟踪状态是本地与 clean checkout 一致性的判断边界。
- 2026-08-25：本地工作区与暂存区 clean-checkout 模拟均通过，等待远端 CI 证据。
- 2026-08-25：提交 `6688360` 的 GitHub Actions 运行 `32802018365` 全部通过。
- 2026-08-25：流程阶段 复核：ready → done。原因：已复核实现 diff、任务边界、错误消息及 clean-checkout 行为
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-08-25：根据文档、任务与验证事实刷新状态：implemented → verified。
- 2026-08-25：流程阶段 上线：ready → done。原因：修复提交 6688360 已推送到 origin/main
- 2026-08-25：状态变更：verified → released。原因：修复提交 6688360 已推送到 origin/main，GitHub Actions 运行 32802018365 已通过
- 2026-08-25：流程阶段 观察：doing → done。原因：GitHub Actions 运行 32802018365 全部通过，开发文档系统 job 四项检查均为绿色
- 2026-08-25：状态变更：released → confirmed。原因：远端 clean checkout 验证通过且整套 CI 全绿，观察未发现回归
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：上线与线上观察阶段已从流程中移除，MVP 阶段没有生产环境；状态 confirmed → verified。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
