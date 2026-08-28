---
id: "WORK-020"
type: "work"
title: "搭建用户端与管理端应用布局"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: ["WORK-015"]
related: ["FEATURE-003", "EXPERIENCE-009", "TASK-028", "VERIFY-020", "MEMORY-016", "DESIGN-016", "WORK-019", "WORK-022"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-020"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["FEATURE-003"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-009"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-016"], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-028"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-028"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-020"], "checks": ["automated-tests", "accessibility"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-016"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}]
required_documents: ["feature", "experience", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "accessibility"]
human_confirmations: []
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














# WORK-020：搭建用户端与管理端应用布局

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

当前 Web 只有一条临时顶部导航，所有页面共用同一个外壳。普通用户与管理员虽然任务完全不同，却没有
清楚的页面结构边界；管理页面也缺少能承载后续功能分组和子菜单的稳定导航。继续直接增加业务页面会让
导航、账号操作、页脚和窄屏行为在各页面重复生长，最终难以保持一致。

本工作先建立两套稳定页面骨架：普通用户使用熟悉的上中下结构；管理员使用上方页头、中间左侧导航加
右侧内容、下方页脚的结构。业务页面先放进正确骨架，之后再分别开发题库、提交和管理功能。

## 成功标准

- [ ] 普通页面稳定显示页头、主要内容和页脚，内容较少时页脚仍位于窗口底部。
- [ ] 管理页面稳定显示页头、左侧导航、右侧主要内容和页脚，不再混入普通用户导航。
- [ ] `/admin` 与 `/admin/dashborad` 展示同一个 Dashboard 页面；初期只保留可访问页面标题，不虚构统计。
- [ ] 管理导航支持可展开的二级菜单；当前页面和所属分组在视觉、文字结构和辅助技术中都可识别。
- [ ] 桌面与 320px 窄屏均可完成导航；窄屏管理导航通过明确的菜单按钮打开和关闭，不遮断主要内容。
- [ ] 现有首页、登录、改密、403 和用户管理行为保持可用，身份加载、失败、退出与权限保护不回退。
- [ ] 页面只使用 Cherry OJ 设计系统和经项目语义覆盖的 shadcn/ui 组件，不产生第二套主题或硬编码颜色。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-020` 查看实际进度。

## 待确认项

当前没有阻塞性产品未知。需要人工审核 FEATURE-003、EXPERIENCE-009、DESIGN-016 与 TASK-028，并在
后续消息中明确允许执行；本轮只完成文档，不修改 Web 实现。

## 风险点

- 路由归属不清会造成普通壳层和管理壳层重复嵌套；通过路由树测试和页面 landmark 数量检查发现。
- 直接照搬 shadcn/ui 默认颜色、尺寸或暗色分支会绕过 Cherry OJ 设计系统；源码门禁和双主题检查必须
  拒绝此类漂移。
- 桌面侧栏在窄屏简单压缩会挤占内容；320px 下改为可关闭的抽屉式导航，并检查横向溢出和焦点恢复。
- WORK-019 的主页 Figma 使用用户端侧栏，与本次明确的顶部导航方向冲突；本工作只保留其主页内容参考，
  以本次已审核文档作为 Web 壳层依据，不反向修改已交付的 Figma 历史记录。

## 影响面

影响普通用户、管理员以及后续开发所有 Web 页面的人。实现范围局限在 `apps/web` 的应用装配、路由、
共享 UI 壳层与对应测试；不修改服务端、公开 API、判题链路、身份业务规则或设计系统真源。现有页面内容
仅迁入新壳层并做必要的高度/容器适配，不在本工作中新增题库、提交或题目管理能力。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：完成双端壳层范围、响应式、设计系统、路由迁移与验证草案，提交人工审核。
- 2026-08-28：用户补充管理端 Dashboard 入口，并明确文档修改后允许直接执行 TASK-028。
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-28：根据文档、任务与验证事实刷新状态：doing → implemented。
- 2026-08-28：人工复核提出 Footer 产品调整，后续定义与实施转入 WORK-022，保留本工作作为原始实现记录。
