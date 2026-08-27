---
id: "WORK-013"
type: "work"
title: "建立用户身份与访问控制服务"
status: "todo"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["security", "privacy", "data", "reliability", "compatibility", "release"]
depends_on: ["WORK-009"]
related: ["WORK-002", "CAPABILITY-004", "EXPERIENCE-005", "DESIGN-010", "DECISION-009", "PLAN-010", "TASK-016", "VERIFY-013", "MEMORY-010", "TASK-017", "TASK-018", "TASK-019"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-013"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-004"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-005"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-010"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-009"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-010"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-016", "TASK-017", "TASK-018", "TASK-019"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-016", "TASK-017", "TASK-018", "TASK-019"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-013"], "checks": ["automated-tests", "cross-module-regression", "compatibility", "data", "privacy", "security"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": ["rollback", "release"], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": ["reliability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-010"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "compatibility", "data", "privacy", "release", "reliability", "security"]
human_confirmations: ["安全边界与权限影响已经由负责人确认"]
blocking_items: []
reversible: true
data_change: true
public_api_change: true
security_sensitive: true
user_visible: true
created_at: "2026-08-26"
updated_at: "2026-08-26"
work_type: "infra"
---

































# WORK-013：建立用户身份与访问控制服务

## 为什么做

当前 user-service 只有能启动和返回健康状态的工程骨架，用户还不能安全登录，管理员也不能开通或停用
账号。没有统一身份后，题目、提交和判题管理服务既不知道请求是谁发起的，也无法可靠区分普通用户与
管理员，WORK-002 的完整答题闭环因此无法继续。

本工作建立最小但完整的身份底座：用户看到的是登录、退出、修改密码和清晰的无权限状态；管理员能够
维护账号；内部服务得到可验证、短时有效的身份凭据。密码和内部凭据始终留在受控边界内。

## 成功标准

- [x] 普通用户能够用已开通账号登录、刷新页面保持登录、退出和修改密码，失败时不会泄露账号是否存在。
- [x] 管理员能够创建、停用、恢复和重置普通用户账号；停用或改密后，已有登录在明确上限内全部失效。
- [x] 普通用户只能访问允许的题目和自己的数据，管理员入口与内部接口不能被普通用户调用。
- [x] 浏览器只持有受保护的 Session Cookie，不接触内部 JWT、密码摘要或登录授权。
- [x] 登录限速、密码哈希、CSRF、防固定会话、密钥轮换、审计与敏感信息负向测试具有实际通过证据。
- [x] user-service、Gateway、Web 和各资源服务通过真实组件级登录与授权回归。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-013` 查看实际进度。

## 待确认项

暂无。2026-08-26 已确认：MVP 由管理员开通账号；首个 ADMIN 使用一次性离线命令初始化；会话空闲
30 分钟、最长 12 小时并允许多端登录；退出结束当前端，改密、重置、角色变化或封禁结束全部登录。

## 风险点

- 凭据泄露：浏览器不接触内部 JWT，数据库只存密码与登录授权的摘要；日志、错误、审计和测试均检查
  密码、Cookie、JWT 不出现。
- 撤销延迟：资源服务离线校验内部 JWT，安全事件不会在同一纳秒传播；用 2 分钟访问令牌上限、立即撤销
  登录授权与清理 Gateway Session 把窗口限制在明确范围内。
- 暴力登录与账号枚举：公开失败统一文案，Gateway 按来源限速，user-service 按账号执行短时退避和审计。
- 密钥轮换导致全站拒绝：新旧公钥重叠发布，先分发验证能力、再切换签名、最后移除旧钥，并提供回退。
- 管理员误封或误重置：所有安全操作审计，使用乐观锁避免覆盖并发变更，恢复操作不得恢复旧登录授权。

## 影响面

影响普通用户与管理员、公开 Web API、Gateway Session/CSRF、user-service 数据库和密钥、Web 登录状态，
以及 problem/submission/judging 资源服务的认证授权入口。它依赖 WORK-009 已确认的 REST 交换协议，并为
WORK-002 提供登录前置能力；不改变 Go judge/sandbox、判题契约、题目与提交的数据所有权。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-26：创建工作项并生成初始流程。
- 2026-08-26：完成能力、体验、技术方案、候选决策、计划、任务和验证草案；推荐方案等待人工审核，
  未授权实施。
- 2026-08-26：负责人确认 DECISION-009 全部推荐项与安全边界，授权按 PLAN-010 开始实施。
- 2026-08-26：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-26：四个 TASK 完成；独立复核修正失败登录事务、MySQL 锁定阈值、Session 撤销、首次改密
  token、轮换上一公钥、Retry-After 与 Web 恢复/无障碍缺口，聚合回归全部通过。
- 2026-08-26：流程阶段 复核：ready → doing。原因：开始对认证事务、MySQL 阈值、Session 撤销、JWT/JWKS、首次改密门禁和 Web 恢复路径执行独立复核
- 2026-08-26：流程阶段 复核：doing → done。原因：独立复核发现的问题均已修正并新增回归测试，影响面与安全边界已复查
- 2026-08-26：流程阶段 上线：pending → blocked。原因：本次授权范围仅为仓库实施，未提供生产环境、Secret、域名或发布授权
- 2026-08-26：流程阶段 线上观察：pending → blocked。原因：尚未生产发布，无法产生线上可靠性与撤销时延观察证据
