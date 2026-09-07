---
id: "WORK-002"
type: "work"
title: "交付 C++ ACM 答题闭环"
status: "implemented"
work: null
owners: ["product/owner"]
risk: "high"
impact: "system"
concerns: ["data", "security", "accessibility"]
depends_on: ["WORK-025", "WORK-037", "WORK-040", "WORK-041"]
related: ["FEATURE-001", "EXPERIENCE-001", "DESIGN-002", "DECISION-002", "PLAN-002", "TASK-002", "TASK-076", "TASK-077", "TASK-078", "TASK-079", "TASK-080", "VERIFY-002", "MEMORY-002"]
implements: []
verifies: []
tags: []
required_documents: ["feature", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "accessibility", "data", "security"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: true
public_api_change: true
security_sensitive: true
user_visible: true
created_at: "2026-08-24"
updated_at: "2026-09-07"
work_type: "product"
---

# WORK-002：交付 C++ ACM 答题闭环

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 需求澄清 | ✔ 完成 | 必需 | WORK-002 `implemented` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 功能定义 | ✔ 完成 | 必需 | FEATURE-001 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 体验设计 | ✔ 完成 | 必需 | EXPERIENCE-001 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-002 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-002 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-002 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-002 `done`、TASK-076 `done`、TASK-077 `done`、TASK-078 `done`、TASK-079 `done`、TASK-080 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-002 `done`、TASK-076 `done`、TASK-077 `done`、TASK-078 `done`、TASK-079 `done`、TASK-080 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成（手动） | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ▶ 进行中 | 必需 | VERIFY-002 `review` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ▶ 进行中 | 必需 | MEMORY-002 `review` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- UNKNOWN-001（resolved）：MVP 不开放注册，由管理员预置普通用户账号；首个 ADMIN 由一次性离线命令
  初始化。身份与会话细节以已确认的 DECISION-009 为准。
- UNKNOWN-002（resolved）：2026-09-07 负责人确认 WA 仅展示汇总信息，具体公开范围见 FEATURE-001；不展示隐藏输入输出与逐点详情。
- UNKNOWN-003（resolved）：2026-09-07 负责人确认隔离栈完整验收，现有环境另行确认部署后人工冒烟；不在现有环境做故障注入。

## 变更记录

- 2026-08-24：创建工作项并生成初始流程。
- 2026-08-24：从旧 REQ-0001 完整迁入用户流程、规则、验收场景和未决问题；尚未人工确认。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-26：负责人确认管理员预置账号方案，解决 UNKNOWN-001；其余两个待确认项保持 blocking。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
- 2026-09-07：按当前实现重写全部方案；将运行和完整历史移出本轮，补齐六任务边界、验收映射与回退计划。风险提升为 high，明确数据、公开 API 与安全变更；尚未签闸或实施。
- 2026-09-07：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-07：根据负责人明确答复解除 UNKNOWN-002/003，清空 blocking_items；仅记录澄清，不代签意图闸。
- 2026-09-07：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-07：意图闸：passed。原因：确认正式提交闭环方案、六个任务边界及验收标准，允许开始实施
- 2026-09-07：检查项 rollback 记录结论：通过。原因：开工前已检查 PLAN-002 回退方案：关闭新受理、保留查询和在途事实、不删表清队列；实际演练仍由 TASK-080 验证
- 2026-09-07：流程阶段 复核：ready → done。原因：经授权的只读独立审查完成，多轮问题修复且末轮无阻塞发现；影响与边界记录于PLAN和VERIFY
- 2026-09-07：检查项 definition 记录结论：通过。原因：按已签署的正式提交MVP定义实现，运行和完整历史保留后续范围
- 2026-09-07：检查项 scope 记录结论：通过。原因：六个任务边界及先行扩展均记录PLAN；未修改现有运行数据或部署现有环境
- 2026-09-07：检查项 rollback 记录结论：通过。原因：隔离栈实测关闭新受理保留查询与原键重放，恢复后真实AC；未删表或清队列
- 2026-09-07：检查项 impact-analysis 记录结论：通过。原因：核对契约、五服务调用、Kafka、Web与配置默认值，新增迁移和凭据默认关闭，证据见VERIFY
- 2026-09-07：检查项 independent-review 记录结论：通过。原因：授权只读审查多轮完成，发现问题已修复并复查，末轮无阻塞
- 2026-09-07：检查项 automated-tests 记录结论：通过。原因：Java169通过1条件跳过且真实栈覆盖，Web165通过及17E2E，契约11通过
- 2026-09-07：检查项 cross-module-regression 记录结论：通过。原因：真实MySQLRedisKafka及五Java服务至LinuxJudge和sandbox验证ACWACETLESE及恢复，浏览器ACCE通过
- 2026-09-07：检查项 accessibility 记录结论：通过。原因：工作台键盘、320px、200%等效视口、双主题、强制颜色与状态播报测试通过并检查截图
- 2026-09-07：检查项 data 记录结论：通过。原因：并发幂等、事务回滚、冻结版本校准环境、乱序与旧租约迟到均由真实MySQL/Kafka验证
- 2026-09-07：检查项 security 记录结论：通过。原因：服务身份隔离、本人可见、管理员无特权、账号切换前置条件、隐藏输出与诊断及死信脱敏验证通过
- 2026-09-07：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-07：流程阶段 复核：doing → done。原因：所有复核检查已记录通过；独立审查末轮无阻塞，影响分析和范围证据完备
