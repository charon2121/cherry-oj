---
id: "WORK-053"
type: "work"
title: "修复登录会话期限在数据库往返后的精度不一致"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security"]
depends_on: []
related: ["ISSUE-017", "DESIGN-047", "DECISION-031", "PLAN-037", "TASK-117", "VERIFY-054", "MEMORY-040"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: true
created_at: "2026-09-11"
updated_at: "2026-09-11"
work_type: "fix"
---

# WORK-053：修复登录会话期限在数据库往返后的精度不一致

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-017 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-047 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-031 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-037 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-117 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-117 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-054 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-040 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。

## 变更记录

- 2026-09-11：创建工作项并生成初始流程。
- 2026-09-11：意图闸：passed。原因：确认期限精度修复方案，允许实施
- 2026-09-11：检查项 rollback 记录结论：通过。原因：复现批次仅两份测试及CI准备校验，可独立回退；后续签发精度修复也仅一个类。无需数据库迁移或现有服务变更，旧精度问题和CI失败记录保留
- 2026-09-11：检查项 automated-tests 记录结论：未通过。原因：本地JDK21编译通过；旧实现认证单测3PASS/1FAIL，新增精度用例确定性失败；81项基础检查通过。真实MySQL旧红与修复后回归及Linux业务尚未执行，不宣称修复完成
- 2026-09-11：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-09-11：检查项 independent-review 记录结论：通过。原因：经本工作明确授权，work053_review只读复核复现测试及单处生产截断：真实事务、固定期限、到期撤销、零skip和日志资源边界无阻断；指出准备阶段容器清理仅依赖Testcontainers，已保留证据限制
- 2026-09-11：检查项 impact-analysis 记录结论：通过。原因：仅AuthenticationService签发absoluteExpiresAt统一微秒；JWT/审计时间/事务/网关/Schema/配置/现有服务均未改。新增两类测试及CI报告门禁，不改变沙箱或业务预算；旧Redis纳秒会话仍需重新登录
- 2026-09-11：检查项 automated-tests 记录结论：通过。原因：同harness真实MySQL ef3312e 6PASS/2精度FAILURE，31b4019 8PASS/0ERROR/0SKIP；真实登录200改密204重登200，部署校准发布成功。本地81基础检查及4认证通过；后续浏览器失败归TASK-112，完整WORK-050仍未通过
- 2026-09-11：检查项 reliability 记录结论：通过。原因：确定性数据库期限差值旧红新绿；validate/exchange、到期前1微秒、到期及撤销均通过，未重试业务。新环境实际改密恢复，原有认证不变量保持
- 2026-09-11：检查项 security 记录结论：通过。原因：签发期限最多缩短999ns，网关严格相等/JWT TTL/撤销规则未改；独立源码与证据复核通过。日志摘要白名单、CI零skip与现有用户环境边界保持；旧会话与Testcontainers清理观测限制已记录
- 2026-09-11：流程阶段 复核：ready → done。原因：独立复核与影响检查通过，源码及下载证据无未解决的本修复阻断
- 2026-09-11：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-09-11：验收闸：passed。原因：确认会话期限精度修复及回归通过，接受已记录限制，交回真实业务 CI
- 2026-09-11：根据文档、任务与验证事实刷新状态：implemented → verified。
