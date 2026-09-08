---
id: "WORK-039"
type: "work"
title: "修复 JWT 签发 30 秒后被资源服务误拒绝"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: ["reliability"]
depends_on: []
related: ["ISSUE-011", "DESIGN-033", "TASK-070", "VERIFY-040"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "reliability"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-09-06"
updated_at: "2026-09-09"
work_type: "fix"
---

# WORK-039：修复 JWT 签发 30 秒后被资源服务误拒绝

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-011 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-033 `checked` | 确定技术方案、边界与取舍 |
| 修复任务 | ✔ 完成 | 必需 | TASK-070 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-070 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-040 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-06：创建工作项并生成初始流程。
- 2026-09-06：意图闸：passed。原因：确认修复 JWT iat 30 秒误拒绝，不调整既定时间配置
- 2026-09-06：流程阶段 复核：ready → done。原因：复核确认修复集中在共享 verifier，未引入按服务例外；原始 iat 必填、exp/nbf 及既有身份 claims 校验均有回归证据
- 2026-09-06：检查项 impact-analysis 记录结论：通过。原因：改动只影响共享 JWT 解码器，problem/submission/judging 均通过既有安全装配测试；无 API、数据或时间配置变化
- 2026-09-06：检查项 automated-tests 记录结论：通过。原因：共享时间边界测试、三资源服务安全回归以及 8 模块 clean verify 共 143 项测试通过
- 2026-09-06：检查项 reliability 记录结论：通过。原因：消除 30 秒隐形寿命且保留 exp/nbf 硬截止、必需 claims 与 JWKS 失败分类；重启资源服务即可生效
- 2026-09-06：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-09：验收闸：passed。原因：确认 JWT 签发 30 秒后被误拒绝的问题修复完成
- 2026-09-09：根据文档、任务与验证事实刷新状态：implemented → verified。
