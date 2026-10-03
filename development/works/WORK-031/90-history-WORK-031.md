---
id: "WORK-031"
type: "work"
title: "统一页面任务优先布局并移除状态占位"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: ["accessibility", "compatibility"]
depends_on: []
related: ["FEATURE-009", "EXPERIENCE-016", "DESIGN-024", "PLAN-020", "TASK-049", "VERIFY-032", "MEMORY-025"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "design", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "accessibility", "compatibility"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: true
security_sensitive: false
user_visible: true
created_at: "2026-09-02"
updated_at: "2026-09-02"
work_type: "product"
---

# WORK-031：统一页面任务优先布局并移除状态占位

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
| 需求澄清 | ✔ 完成 | 必需 | WORK-031 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | ✔ 完成 | 必需 | FEATURE-009 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | ✔ 完成 | 必需 | EXPERIENCE-016 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-024 `checked` | 确定技术方案、边界与取舍 |
| 开发计划 | ✔ 完成 | 必需 | PLAN-020 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-049 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-049 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-032 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-025 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-02：创建工作项并生成初始流程。
- 2026-09-02：检查项 definition 记录结论：通过。原因：用户、入口、五项变化、标题例外、边界和八项验收场景均已明确
- 2026-09-02：检查项 scope 记录结论：通过。原因：范围限定于 status 垂直切片、Web 页面与壳层、设计规范；其它服务、主题、业务 API、路由和数据禁止修改
- 2026-09-02：意图闸：passed。原因：用户明确确认方案通过并允许开始实施 WORK-031
- 2026-09-02：检查项 automated-tests 记录结论：通过。原因：Web check 109 项、Playwright 27 项、Gateway 55 项、server clean verify 133 项及构建全部通过
- 2026-09-02：检查项 impact-analysis 记录结论：通过。原因：影响限定为 status 公开契约删除、现有 Web 页面/壳层布局和 Gateway status 目录；Actuator、其它 API、数据与主题未变
- 2026-09-02：检查项 accessibility 记录结论：通过。原因：保留业务/登录/状态标题并为无可见标题页面保留 sr-only H1；键盘菜单、skip link、320px、forced-colors 与 200% 等效尺寸回归通过
- 2026-09-02：检查项 compatibility 记录结论：通过。原因：OpenAPI 生成一致，双主题/移动与桌面 E2E、Storybook、7 模块 server verify 通过；仓库内无 status 消费者
- 2026-09-02：流程阶段 复核：ready → done。原因：已逐项复核需求分类、status 全链路删除、24px 共享间距、账号菜单、滚动所有权与 TASK 边界，无越界或可操作缺陷
- 2026-09-02：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-02：验收闸：passed。原因：用户明确验收通过并授权签署 WORK-031 验收闸
- 2026-09-02：根据文档、任务与验证事实刷新状态：implemented → verified。
