---
id: "WORK-022"
type: "work"
title: "微调双端应用布局页脚"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: ["WORK-020"]
related: ["FEATURE-004", "EXPERIENCE-010", "TASK-030", "VERIFY-022", "MEMORY-017", "VERIFY-020"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-022"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["FEATURE-004"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-010"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-030"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-030"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-022"], "checks": ["automated-tests", "accessibility"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-017"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}]
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
created_at: "2026-08-28"
updated_at: "2026-08-28"
work_type: "product"
---











# WORK-022：微调双端应用布局页脚

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

WORK-020 已建立用户端和管理端两套页面骨架。复核后确认，管理页面长期使用时不需要页脚，保留它会
占用垂直空间并制造无价值的页面收尾；用户页面仍需要页脚这一结构，但当前边框和独立底色让它像一个
额外面板，视觉层级比实际信息重要性更高。

本工作只微调两端页脚：管理端完全移除页脚；用户端保留页脚结构与现有简短内容，但让它和主要内容使用
相同页面表面，不再通过边框、独立背景或阴影制造分区。

## 成功标准

- [x] 管理页面只保留页头和“左侧导航 + 右侧内容”的中间区域，桌面与窄屏都不再显示页脚。
- [x] 用户页面仍保留页脚结构和当前简短内容，短页面时仍位于窗口底部。
- [x] 用户页脚与主要内容使用相同背景，不显示顶部分隔线、独立面板或阴影。
- [x] 两端现有导航、Dashboard、登录、改密、权限和用户管理行为不改变。
- [x] 两个主题及 320px 下均无横向溢出、内容遮挡或多余空白。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-022` 查看实际进度。

## 待确认项

需要人工确认 FEATURE-004、EXPERIENCE-010 与 TASK-030，并在后续消息中明确允许执行。本轮只整理
复核意见，不修改 Web 实现。

## 风险点

- 移除管理 Footer 后若网格仍保留第三行，会在页面底部留下无意义空白；通过短 Dashboard 的视口截图和
  布局尺寸断言发现。
- 用户 Footer 去掉表面样式时若同时误删结构，会损失 `contentinfo` landmark；通过语义查询发现。
- 共享样式调整可能误伤 Header 或 Sidebar；源码范围检查、双主题 E2E 和现有回归测试必须继续通过。

## 影响面

影响用户端与管理端所有页面的整体观感，但不改变入口、权限、文案和业务能力。实现范围仅限 WORK-020
已建立的两个 App Shell 及相应浏览器测试，不修改路由、业务页面、服务端、公开 API、设计系统真源或
主题合同。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：记录 WORK-020 人工复核意见：管理端移除 Footer，用户端 Footer 保留语义但取消与 Main
  的视觉分区；等待文档审核和执行授权。
- 2026-08-28：用户批准 FEATURE-004、EXPERIENCE-010 与 TASK-030 并允许实施；两个 Shell 的 Footer
  微调和浏览器回归已完成，验证证据见 VERIFY-022。
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-28：根据文档、任务与验证事实刷新状态：doing → implemented。
