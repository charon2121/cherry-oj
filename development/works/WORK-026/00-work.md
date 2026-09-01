---
id: "WORK-026"
type: "work"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "todo"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["reliability", "security", "compatibility"]
depends_on: []
related: ["WORK-013", "WORK-025", "CAPABILITY-007", "EXPERIENCE-014", "DESIGN-020", "DECISION-015", "PLAN-016", "TASK-041", "VERIFY-026", "MEMORY-021"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-026"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-007"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-014"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-020"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-015"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-016"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-041"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-041"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-026"], "checks": ["automated-tests", "cross-module-regression", "compatibility", "security"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["rollback"], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["reliability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["MEMORY-021"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "compatibility", "reliability", "security"]
human_confirmations: ["安全边界与权限影响已经由负责人确认"]
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: false
created_at: "2026-08-31"
updated_at: "2026-08-31"
work_type: "infra"
---
















# WORK-026：为 Java 服务提供可直接启动的本地默认配置

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

五个 Java 服务的大部分本地配置已经带默认值，但仍有少数启动必需项只能靠开发者先在每个终端手工
`export`。遗漏任意一项时，服务通常要到 Spring 创建数据库连接或签名组件时才失败，日志离真正原因
较远；重新开终端后还要重复配置，也让“从根工程构建成功”和“服务实际可启动”成为两套体验。

本工作为本地开发建立完整、可覆盖的默认配置。开发者准备好项目约定的 MySQL、Redis 等基础设施后，
可以直接启动任一 Java 服务；部署环境仍使用自己的 Secret（密码或私钥等敏感配置），不会把本地默认
误当成生产凭据。

## 成功标准

- [x] 在未设置任何 `CHERRY_*` 环境变量时，五个 Java 服务都不会因为缺少配置值而拒绝启动。
- [x] 环境变量仍可覆盖每一项本地默认值，已有部署配置名称和优先级不变。
- [x] 仓库不新增固定 RSA 私钥、真实账号密码或其它可用于生产的 Secret。
- [x] 生产配置继续要求显式提供数据库凭据和稳定签名密钥；缺失时明确失败，不能静默采用本地临时值。
- [x] 可选的空值与框架运行时属性不会被误判为缺失配置；自动化检查能防止后续服务重新引入无默认的
  本地启动必需变量。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-026` 查看实际进度。

## 待确认项

2026-08-31 负责人已确认 DESIGN-020 / DECISION-015，并明确允许实施。本地随机 RSA、生产稳定 PEM、
数据库最小权限账号默认和有效空值保留均已实现；生产发布仍需目标环境授权与 Secret 注入。

## 风险点

- 临时签名密钥在 user-service 重启后变化，旧 JWT 会失效；这只适用于单实例本地联调，生产必须提供
  稳定密钥文件。
- 把本地数据库口令带到部署环境会形成弱凭据风险；生产 profile 和验证必须证明显式覆盖仍为必需。
- Redis 密码空值是本地无鉴权 Redis 的有效配置，强行填值反而会使 Gateway 无法连接。
- 默认值只能消除配置导出步骤，不能自动创建数据库、账号或启动 MySQL、Redis、Go Judge。

## 影响面

影响所有本地启动 Java 服务的开发者，以及 user-service 身份签名、user/problem/judging 数据库连接和
相关启动测试。公开 API、数据库表、Web 页面、Go Judge、生产 Secret 内容和服务端口均不改变。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-31：创建工作项并生成初始流程。
- 2026-08-31：完成五个服务配置盘点，区分启动必需项、有效空值与 Spring 运行时属性，形成零 export
  本地启动且生产继续 fail-closed 的方案草案，等待负责人审核。
- 2026-08-31：负责人明确“WORK-026 文档通过，允许实施”，完成配置、随机密钥、生产守卫、跨服务
  扫描、启动说明和聚合验证；本机 judging 数据库服务账号尚未创建/授权，不属于配置缺省。
- 2026-08-31：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-31：流程阶段 复核：ready → doing。原因：开始复核配置语义、生产边界、WORK-025 差异和敏感信息
- 2026-08-31：流程阶段 复核：doing → done。原因：复核确认默认仅用于本地、production fail-closed、无固定私钥且未覆盖 WORK-025 实现
- 2026-08-31：运行时联调发现三个资源服务的本地 JWKS 默认地址与 user-service 实际发布地址不一致；
  已在原批准范围内修正、增加跨服务回归，并重新完成七模块聚合验证。
