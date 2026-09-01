---
id: "WORK-024"
type: "work"
title: "重新设计登录页视觉与体验"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: []
related: ["FEATURE-006", "EXPERIENCE-012", "TASK-032", "VERIFY-024", "MEMORY-019"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-024"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["FEATURE-006"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-012"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-032"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-032"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-024"], "checks": ["automated-tests", "accessibility"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-019"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}]
required_documents: ["feature", "experience", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "accessibility"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-08-29"
updated_at: "2026-08-30"
work_type: "product"
---

























# WORK-024：重新设计登录页视觉与体验

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

当前登录页已经能够完成账号密码登录，也能显示提交中和失败状态，但主要内容只是一个位于大面积空白
中央的表单。它能用，却没有延续主页和导航已经建立的“专注练习空间”感受，用户进入产品的第一步显得
单调、临时，也没有充分利用桌面空间建立品牌记忆。

本工作重新组织登录页的信息层级与视觉构图，让用户一眼知道自己正在进入 Cherry OJ，并能立即完成
登录。变化只发生在页面呈现与状态布局，不改变账号、密码、安全、跳转或权限规则。

## 成功标准

- [ ] 登录页不再表现为孤立在空白画布中的小表单，品牌信息、登录任务与页面空间形成清楚的整体构图。
- [ ] 用户名、密码、密码显隐、登录和失败恢复仍是唯一主流程，用户无需寻找或判断下一步。
- [ ] 登录中、校验错误、接口错误和限流提示不会因视觉改造被弱化、遮挡或造成布局跳动。
- [ ] 页面与现有 Header、Footer、Cherry 品牌和双主题保持一致，不出现未交付入口或设计系统外样式。
- [ ] 桌面、320px、200% 缩放、键盘和辅助技术下均可完成登录，关键文案和控件不被裁切。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-024` 查看实际进度。

## 待确认项

视觉方向已选择为本轮第 2 个方案；仍需人工审核 FEATURE-006、EXPERIENCE-012 与 TASK-032，并在后续
消息中明确允许实施。

## 风险点

- 装饰内容抢过表单注意力；通过首屏视觉复核和任务完成路径检查，保证“登录”始终是唯一主要动作。
- 桌面构图在窄屏直接压缩后变得拥挤；为 320px 单独定义内容取舍和顺序，不缩小关键控件硬塞。
- 为了丰富页面误加注册、找回密码或第三方登录；用入口清单和 E2E 检查，只显示真实交付能力。
- 错误提示与新布局脱节；覆盖空提交、限流、服务失败和重复点击，失败后保持输入并允许恢复。

## 影响面

影响所有进入 `/login` 的访客、登录过期用户和被保护页面重定向来的用户。范围局限于 Web 登录页的
呈现、必要的页面级展示组件、Storybook/组件测试和登录 E2E；不修改登录接口、Session、权限、路由
规则、全局 Shell、设计系统真源、主题合同或后端。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-29：创建工作项并生成初始流程。
- 2026-08-29：完成现状截图与三种视觉方向探索，等待用户选择后定稿。
- 2026-08-29：用户选择第 2 个视觉方向，确定为“左侧表单、右侧低对比工作区字景”的实施目标。
- 2026-08-29：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-29：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-29：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-29：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-08-29：状态变更：implemented → doing。原因：用户人工验收退回 TASK-032，重新进入开发修正
- 2026-08-29：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-08-29：状态变更：implemented → doing。原因：用户复核仍发现登录页滚动条，继续在 TASK-032 内修正短桌面窗口溢出
- 2026-08-30：根据文档、任务与验证事实刷新状态：doing → implemented。
