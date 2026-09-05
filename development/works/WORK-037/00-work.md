---
id: "WORK-037"
type: "work"
title: "重建内部身份信任链并消除管理请求 502"
status: "verified"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["compatibility", "observability", "release", "reliability", "security"]
depends_on: []
related: ["ISSUE-009", "DESIGN-031", "DECISION-021", "PLAN-025", "TASK-065", "VERIFY-038", "MEMORY-029", "TASK-066", "TASK-067", "TASK-068"]
implements: []
verifies: []
tags: []
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "compatibility", "observability", "release", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: true
public_api_change: false
security_sensitive: true
user_visible: true
created_at: "2026-09-04"
updated_at: "2026-09-05"
work_type: "fix"
---

# WORK-037：重建内部身份信任链并消除管理请求 502

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
| 问题说明、复现与预期 | ✔ 完成 | 必需 | ISSUE-009 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 原因与修复方案 | ✔ 完成 | 必需 | DESIGN-031 `checked` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-021 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-025 `checked` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 修复任务 | ✔ 完成 | 必需 | TASK-065 `done`、TASK-066 `done`、TASK-067 `done`、TASK-068 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-065 `done`、TASK-066 `done`、TASK-067 `done`、TASK-068 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 回归验证 | ✔ 完成 | 必需 | VERIFY-038 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-029 `checked` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

- 是否通过 DECISION-021 的系统级方案并允许进入实施；当前意图闸仍为 `pending`。
- 实施时需核实正式环境当前 K1/Secret/滚动发布条件；这不影响架构选择，但决定首次迁移步骤。
- 时间策略已确认：JWT 2 小时、提前 5 分钟续签、取消 idle、absolute 为首次登录起固定 30 天；保留每请求
  grant validate，使退出、改密、密码重置和账号禁用立即生效。

## 变更记录

- 2026-09-04：创建工作项并生成初始流程。
- 2026-09-04：负责人否决 multipart 局部恢复；影响级别提升为 system，重写为身份信任链架构重构。
- 2026-09-04：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-04：按负责人意图把 JWT 调整为 2 小时、提前 5 分钟刷新，并增加主动撤销型持久 Session 数据迁移。
- 2026-09-04：负责人确认取消 idle、absolute 固定 30 天，并允许与身份架构重构一并实施。
- 2026-09-04：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-04：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-09-05：意图闸：passed。原因：确认实施系统级身份架构重构：JWT 2 小时、提前 5 分钟续签、取消 idle、absolute 固定 30 天，并保留每请求 grant validate
- 2026-09-05：检查项 rollback 记录结论：通过。原因：密钥轮换保留 previous key 且不自动删除，数据库迁移为兼容性变更，原会话 absolute deadline 保留，可按 TOOLCHAIN 回退
- 2026-09-05：检查项 release 记录结论：通过。原因：TOOLCHAIN 已明确 init、prepare、三服务 probe、activate、等待与 retire 的发布顺序和阻断条件
- 2026-09-05：检查项 impact-analysis 记录结论：通过。原因：已覆盖 Gateway、user-service、三个资源服务、共享验证模块、Redis、MySQL、ZIP 上传与运维轮换路径
- 2026-09-05：检查项 independent-review 记录结论：通过。原因：已完成两轮独立复核；正确性问题已处理，超出负责人确认可信内网模型的加固项已明确记录为不实施
- 2026-09-05：检查项 automated-tests 记录结论：通过。原因：./mvnw clean verify 通过：137 个测试成功、0 失败，仅 1 个 Linux 专用测试按平台跳过
- 2026-09-05：检查项 cross-module-regression 记录结论：通过。原因：8 个 reactor 模块全量通过，包含真实 Redis、MySQL 8.4/Flyway、JWKS 与 Gateway multipart ZIP 集成场景
- 2026-09-05：检查项 compatibility 记录结论：通过。原因：公开 API 未变，保留 legacy public key 兼容入口和既有会话 absolute deadline，idle 字段以可空迁移兼容
- 2026-09-05：检查项 observability 记录结论：通过。原因：保留 requestId 与错误分类，readiness 暴露已发布 kid，不记录或暴露私钥及 bearer
- 2026-09-05：检查项 reliability 记录结论：通过。原因：持久 key ring、轮换重叠窗口、统一 verifier、单飞续签与 ZIP 单次流式传输均有测试覆盖
- 2026-09-05：检查项 security 记录结论：通过。原因：在负责人确认的可信内网威胁模型内，保留 RS256 校验、声明约束、CSRF、HttpOnly、grant validate 与即时撤销
- 2026-09-05：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-09-05：验收闸：passed。原因：确认身份架构重构、时间策略和可信内网简化方案符合要求
- 2026-09-05：根据文档、任务与验证事实刷新状态：implemented → verified。
