---
id: "WORK-014"
type: "work"
title: "统一登录空闲过期配置并修复提前掉线"
status: "todo"
work: null
owners: ["codex/root"]
risk: "high"
impact: "multi-module"
concerns: ["reliability", "security", "compatibility"]
depends_on: []
related: ["WORK-013", "MEMORY-010", "ISSUE-002", "DESIGN-011", "DECISION-010", "PLAN-011", "TASK-020", "VERIFY-014", "MEMORY-011"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "问题说明、复现与预期", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["ISSUE-002"], "checks": ["definition", "scope"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "design", "label": "原因与修复方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-011"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-010"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-011"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "修复任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-020"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-020"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-014"], "checks": ["automated-tests", "compatibility", "security"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": ["rollback"], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": ["reliability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-011"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["issue", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "compatibility", "reliability", "security"]
human_confirmations: ["安全边界与权限影响已经由负责人确认"]
gates: {"intent": "passed", "acceptance": "passed"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: true
user_visible: true
created_at: "2026-08-27"
updated_at: "2026-08-27"
work_type: "fix"
---




















# WORK-014：统一登录空闲过期配置并修复提前掉线

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

用户在一段时间没有操作网站后会退出登录，但目前看不出实际采用的是哪个时间，也无法通过一个清晰的
部署配置调整。代码中的登录空闲时间分散在 Gateway 和 user-service：一处直接写死为 30 分钟，另一处
也有独立的 30 分钟值。两处不一致时会取更早的一个，容易表现为“明明改了配置却仍提前掉线”。

本工作把“最后一次认证操作后多久需要重新输入密码”变成单一、可说明、可测试的配置，同时保留安全
事件立即失效和最长登录时间上限。

## 成功标准

- [x] 部署者可分别设置 IDLE 秒数、绝对上限秒数和是否在认证操作后刷新 IDLE，Gateway Session 与
  user-service 登录授权采用相同语义。
- [x] IDLE 默认 1800 秒、绝对上限默认 43200 秒；配置为其它合法值后，用户不会在期限到达前因为
  120 秒内部令牌而退出。
- [x] 超过配置的空闲时间后，下一次认证操作明确进入未登录状态并要求重新输入密码。
- [x] 刷新开关为 `true` 时认证操作刷新 IDLE，为 `false` 时 IDLE 从登录开始固定；只停留页面、滚动
  或编辑未提交表单均不会续期。
- [x] 无论是否刷新 IDLE，都不能突破可配置的绝对上限；退出、改密、停用和重置仍立即失效。
- [x] 非法、缺失或两层不一致的配置不能静默退回另一个值，自动化测试覆盖时间边界。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-014` 查看实际进度。

## 待确认项

2026-08-27 负责人已明确要求时间单位统一为秒，并增加 IDLE 刷新布尔开关。推荐默认值与合法范围为：

- `CHERRY_AUTH_SESSION_IDLE_TIMEOUT_SECONDS=1800`，范围 300～7200 秒；
- `CHERRY_AUTH_SESSION_ABSOLUTE_TIMEOUT_SECONDS=43200`，范围 3600～604800 秒；
- `CHERRY_AUTH_SESSION_REFRESH_IDLE_ON_ACTIVITY=true`，只接受 `true | false`。

2026-08-27 负责人已确认 DESIGN-011、DECISION-010、PLAN-011，并明确允许实施；仓库内实现与验证完成。
生产发布、两个进程的变量注入和线上到期观察仍需目标环境授权。

## 风险点

- 配置过长会扩大离开设备后被他人继续使用的窗口，因此保留上限、绝对期限与安全事件全端撤销。
- 只修改 Gateway 会让数据库登录授权更早过期，只修改 user-service 会让 Redis Session 更早消失；
  验证必须同时检查两层实际期限。
- 把 120 秒内部令牌误当成登录期限会造成频繁掉线；回归测试必须跨过令牌到期点仍保持登录。
- 回退时恢复原 30 分钟默认即可，不涉及数据库结构和已存用户数据。

## 影响面

影响所有已登录用户、本地和部署环境的会话配置、Gateway Redis Session，以及 user-service 的登录授权
续期。不会改变账号、密码、角色、公开登录接口、数据库表结构、资源服务验权或 Web 页面样式。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-27：创建工作项并生成初始流程。
- 2026-08-27：确认当前默认空闲期限为 30 分钟、绝对期限为 12 小时、内部 JWT 为 120 秒；发现
  Gateway 注解硬编码与 user-service 配置分散，形成统一配置修复草案，等待审核。
- 2026-08-27：根据审核反馈把 IDLE 与绝对期限配置单位改为秒，并增加“认证 API 操作是否刷新 IDLE”
  布尔配置；默认值建议保持现有 1800/43200 秒和刷新开启。
- 2026-08-27：完成 Gateway 与 user-service 双层实现、配置一致性 fail-closed、真实 Redis/MySQL 回归及
  Java 聚合验证；仓库内成功标准全部满足，生产发布与观察保留为阻塞阶段。
- 2026-08-27：负责人明确“确认方案，开始实施”，记录完成安全边界与权限影响的人工确认；该确认不
  包含生产发布或线上观察授权。
- 2026-08-27：流程阶段 复核：ready → doing。原因：开始对会话截止时间、配置一致性、内部接口权限与 TASK 路径边界执行实现后复核
- 2026-08-27：流程阶段 复核：doing → done。原因：复核确认 IDLE 等号失效、绝对期限封顶、JWT 透明刷新、503 保留 Session、安全白名单与范围边界均有测试证据
- 2026-08-27：流程阶段 上线：pending → blocked。原因：未提供生产环境、Secret、配置中心或发布授权，本次仅完成仓库实现
- 2026-08-27：流程阶段 观察：pending → blocked。原因：尚未生产发布，无法取得真实用户登录期限与可靠性观察证据
