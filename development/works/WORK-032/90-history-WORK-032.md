---
id: "WORK-032"
type: "work"
title: "修复 WORK-031 遗留的 CI 测试断言"
status: "verified"
work: null
owners: ["codex/root"]
risk: "low"
impact: "multi-module"
concerns: ["compatibility"]
depends_on: []
related: ["ISSUE-008", "DESIGN-025", "TASK-050", "VERIFY-033"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "compatibility"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-09-02"
updated_at: "2026-09-02"
work_type: "fix"
---

# WORK-032：修复 WORK-031 遗留的 CI 测试断言

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-008 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-025 `checked` | 确定技术方案、边界与取舍 |
| 修复任务 | ✔ 完成 | 必需 | TASK-050 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-050 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-033 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ⊘ 跳过 | 可选 | — | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-02：创建工作项并生成初始流程。
- 2026-09-02：检查项 definition 记录结论：通过。原因：两个失败 job、稳定复现、预期行为、根因与五项验收标准均已明确
- 2026-09-02：检查项 scope 记录结论：通过。原因：只允许修改题库 E2E、契约测试与 WORK-032 文档，生产代码、公开契约、CI 配置和 WORK-031 均禁止修改
- 2026-09-02：意图闸：passed。原因：确认按最小范围同步两处过期测试断言
- 2026-09-02：检查项 impact-analysis 记录结论：通过。原因：差异仅为两处测试、WORK-032 文档和生成索引，生产代码、OpenAPI、CI 配置及 WORK-031 均未修改
- 2026-09-02：检查项 automated-tests 记录结论：通过。原因：work 工具、contracts 9 项、Web 109 项、Playwright 30 项、生产与 Storybook 构建全部通过
- 2026-09-02：检查项 compatibility 记录结论：通过。原因：新断言保持 URL 筛选、详情安全、成功信封、Problem Details 与 request ID 覆盖且不恢复已删除 status 资产
- 2026-09-02：流程阶段 复核：ready → done。原因：复核确认实现严格符合 ISSUE-008/DESIGN-025，未删除有效覆盖或越过 TASK-050 边界
- 2026-09-02：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-02：检查项 automated-tests 记录结论：通过。原因：本地 work/contracts/Web 全量验证通过，提交 93aeef0 的 GitHub Actions run 33631033143 六个 job 全绿
- 2026-09-02：验收闸：passed。原因：确认修复已提交推送，远端 CI 全部通过
- 2026-09-02：根据文档、任务与验证事实刷新状态：implemented → verified。
