---
id: "WORK-016"
type: "work"
title: "修复设计系统发布后的文档 CI"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["compatibility"]
depends_on: []
related: ["WORK-015", "ISSUE-003", "TASK-022", "VERIFY-016"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "compatibility"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-28"
updated_at: "2026-09-01"
work_type: "fix"
---

# WORK-016：修复设计系统发布后的文档 CI

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-003 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ⊘ 跳过 | 可选 | — | 确定技术方案、边界与取舍 |
| 修复任务 | ✔ 完成 | 必需 | TASK-022 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-022 `done` | 按任务实施，产出代码与测试 |
| 复核 | ○ 就绪 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ▶ 进行中 | 必需 | VERIFY-016 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。用户已审核 ISSUE-003 的根因与 TASK-022 的文件边界，并明确批准“删除无消费者角色文件、保留
旧 HTML 删除并修正链接”的方案及执行推送。

## 变更记录

- 2026-08-28：确认提交 `eb3c7a3` 的唯一失败 job 及被提前退出掩盖的失效链接，创建低风险修复工作项。
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → ready。
- 2026-08-28：根据文档、任务与验证事实刷新状态：ready → doing。
- 2026-08-28：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
