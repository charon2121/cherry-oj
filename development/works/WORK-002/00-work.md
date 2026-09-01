---
id: "WORK-002"
type: "work"
title: "交付 C++ ACM 答题闭环"
status: "todo"
work: null
owners: ["product/owner"]
risk: "medium"
impact: "system"
concerns: ["data", "security", "accessibility"]
depends_on: []
related: ["FEATURE-001", "EXPERIENCE-001", "DESIGN-002", "DECISION-002", "PLAN-002", "TASK-002", "VERIFY-002", "MEMORY-002"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "blocked", "status_source": "derived", "artifacts": ["WORK-002"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["FEATURE-001"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["EXPERIENCE-001"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["DESIGN-002"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["DECISION-002"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["PLAN-002"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["TASK-002"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["TASK-002"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["VERIFY-002"], "checks": ["automated-tests", "cross-module-regression", "accessibility", "data", "security"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-002"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["feature", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "accessibility", "data", "security"]
human_confirmations: []
gates: {"intent": "pending", "acceptance": "pending"}
blocking_items: ["确认 WA 时允许普通用户查看的测试点信息", "确认内部 MVP 的发布环境口径"]
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-08-24"
updated_at: "2026-08-24"
work_type: "product"
---





# WORK-002：交付 C++ ACM 答题闭环

## 为什么做

judge 与 sandbox 已经能完成底层执行和 verdict 汇总，但当前尚不能证明普通用户能通过 Web 和五个
Java 服务完成一次真实答题。PRD 第一阶段要求先跑通 C++ ACM 纵向切片，再逐步扩展 CORE 和题目工厂。

## 成功标准

- [ ] 管理员能发布一个不可变的 C++ ACM A+B 题目版本。
- [ ] 普通用户能完成找题、读题、编码、自测、正式提交和结果回看。
- [ ] AC、CE、WA 与平台故障被正确区分，隐藏数据不泄漏。
- [ ] 提交能追溯实际题目版本、语言限制和判题环境。
- [ ] FEATURE-001 的所有验收场景具有实际通过证据。

## 当前流程

本工作是系统级产品功能，涉及数据、安全和无障碍，采用完整流程。当前仍在需求澄清；三个会改变用户
行为或验收口径的问题解决前，不能进入 ready 或正式拆解实现任务。

## 待确认项

- UNKNOWN-001（resolved）：MVP 不开放注册，由管理员预置普通用户账号；首个 ADMIN 由一次性离线命令
  初始化。身份与会话细节以已确认的 DECISION-009 为准。
- UNKNOWN-002（blocking）：普通用户在 WA 时允许看到多少测试点信息。
- UNKNOWN-003（blocking）：内部 MVP 的“发布”是进入主干演示环境，还是必须有独立部署环境。

## 风险点

- RISK-001：隐藏输入或标准答案经 API/页面泄漏；以契约边界、权限测试和端到端检查防护。
- RISK-002：系统故障被映射为用户 WA；以生命周期事件、错误分类和故障场景验收防护。
- RISK-003：跨五服务与 Web 的纵向切片产生状态漂移；先冻结接口和不可变 JudgeInput，再分阶段实现。

## 影响面

影响用户与管理员体验、Gateway、user/problem/submission/judging 服务、Kafka 生命周期、Web 页面、
数据库与端到端环境；Go judge/sandbox 的职责边界保持不变，但其现有契约会被业务链路调用。

## 关联文档

FEATURE-001 保存产品定义；后续体验、技术方案、决定、计划、任务和验证文档均由 `related` 关联。

## 变更记录

- 2026-08-24：创建工作项并生成初始流程。
- 2026-08-24：从旧 REQ-0001 完整迁入用户流程、规则、验收场景和未决问题；尚未人工确认。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-26：负责人确认管理员预置账号方案，解决 UNKNOWN-001；其余两个待确认项保持 blocking。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-24：根据 WORK Type、风险、影响面和关注项重建流程。
