---
id: "WORK-009"
type: "work"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["compatibility", "reliability", "security"]
depends_on: []
related: ["WORK-008", "CAPABILITY-002", "EXPERIENCE-003", "DESIGN-007", "DECISION-006", "PLAN-007", "TASK-009", "VERIFY-009", "MEMORY-007"]
implements: []
verifies: []
tags: []
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "compatibility", "reliability", "security"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: true
security_sensitive: false
user_visible: true
created_at: "2026-08-25"
updated_at: "2026-09-01"
work_type: "infra"
---

# WORK-009：建立统一的 Web REST 交换协议与请求基建

## 流程

<!-- 本节由 `scripts/work` 生成，请勿手工编辑；改动请运行 refresh。交互式视图见 `scripts/work board`。 -->

| 阶段 | 状态 | 必需性 | 依据文档 | 说明 |
|---|---|---|---|---|
| 需求澄清 | ✔ 完成 | 必需 | WORK-009 `verified` | 把还没想清楚的问题问出来并得到答复，否则不开工 |
| 能力定义 | ✔ 完成 | 必需 | CAPABILITY-002 `approved` | 说清楚这件事要达成什么、边界在哪、怎样算完成 |
| 开发体验 / 运维要求 | ✔ 完成 | 必需 | EXPERIENCE-003 `approved` | 设计使用者实际看到和操作的流程，包含异常与失败状态 |
| 技术方案 | ✔ 完成 | 必需 | DESIGN-007 `approved` | 确定技术方案、边界与取舍 |
| 技术决策 | ✔ 完成 | 必需 | DECISION-006 `approved` |  |
| 开发计划 | ✔ 完成 | 必需 | PLAN-007 `approved` | 拆成阶段与顺序，说明并行、依赖、迁移与回退 |
| 开发任务 | ✔ 完成 | 必需 | TASK-009 `done` | 拆成可独立完成并验证的任务，划定可读、可写与禁止范围 |
| 开发 | ✔ 完成 | 必需 | TASK-009 `done` | 按任务实施，产出代码与测试 |
| 复核 | ✔ 完成 | 必需 | — | 独立复核实现是否符合定义与方案，边界有没有被越过 |
| 验证 | ✔ 完成 | 必需 | VERIFY-009 `approved` | 用可复现的证据确认要求逐条满足 |
| 项目记忆 | ✔ 完成 | 必需 | MEMORY-007 `approved` | 留下未来仍有参考价值的判断、教训与重审条件 |

## 待确认项

暂无。2026-08-25 人工确认采用推荐方案 A，并确认 Gateway 所有权、`/api` 兼容演进与安全边界。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：根据人工反馈，将 WORK-008 的局部连通实现升级为独立系统级协议设计；设置“审阅后才
  编码”的 blocking 门禁。
- 2026-08-25：人工审阅通过推荐方案 A，确认错误模型、Gateway 所有权、版本策略与安全边界；解除
  编码门禁。
- 2026-08-25：范围澄清后将 `security_sensitive` 从 true 调整为 false：本工作验证错误脱敏、CSRF 与
  request ID 边界，但不修改认证、授权、凭据或权限模型；`security` concern 与验证门禁继续保留。
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-25：OpenAPI、Gateway、Web client、status 迁移、生成漂移门禁和全局文档完成；本地契约、
  聚合后端、Web、浏览器与真实 Vite proxy 验证通过，等待仓库复核后结束 development/verification。
- 2026-08-25：根据文档、任务与验证事实刷新状态：doing → verified。
- 2026-08-25：流程阶段 上线：ready → blocked。原因：当前没有可用生产环境；本次仅提交并推送已验证代码，不代签生产发布
- 2026-08-25：流程阶段 线上观察：pending → blocked。原因：未执行生产发布且当前没有可用生产环境，无法进行线上可靠性观察
- 2026-09-01：正文收敛为控制面入口，「为什么做、成功标准、当前流程、风险点、影响面、关联文档」不再在此重复；产品面内容以定义层文档为准。
- 2026-09-01：移除流程阶段：release、observe。MVP 阶段没有生产环境，这两个阶段永远无法完成。
