---
id: "WORK-017"
type: "work"
title: "建立 Web 设计系统代码基建"
status: "cancelled"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["accessibility", "compatibility"]
depends_on: []
related: ["CAPABILITY-006", "EXPERIENCE-007", "DESIGN-013", "DECISION-012", "PLAN-013", "TASK-023", "VERIFY-017", "MEMORY-013", "WORK-015", "TASK-024", "TASK-025", "WORK-018"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-017"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-006"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-007"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-013"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-012"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-013"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-023", "TASK-024", "TASK-025"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-023", "TASK-024", "TASK-025"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "manual", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "blocked", "status_source": "derived", "artifacts": ["VERIFY-017"], "checks": ["automated-tests", "cross-module-regression", "accessibility", "compatibility"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-013"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "accessibility", "compatibility"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-08-28"
updated_at: "2026-08-28"
work_type: "infra"
---































# WORK-017：建立 Web 设计系统代码基建

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

Cherry OJ 已经确认了默认黑色、纯白浅色和一套统一的组件规则，但浏览器端仍在运行更早的样式：
默认显示浅色、深色靠另一套开关、字体和按钮状态也与新规范不同。现在继续写页面，会出现“文档说一套、
实际组件做一套”的情况；设计者看到的参考、开发者复用的组件和用户最终看到的页面无法对齐。

本工作把已经确认的设计系统真正接入 Web：先建立不会闪错主题的加载与记忆能力，再把常用控件做成
可复用组件，最后迁移当前已有页面并加上持续检查。完成后，后续页面从同一套颜色、间距、状态和无障碍
规则出发，不再每个页面各写一遍。

## 成功标准

- [x] 首次打开网站默认直接显示 Cherry 黑色主题；已有有效浅色偏好时刷新不会先闪黑再变白。
- [x] 缺失、空白、过期或无法读取的主题偏好都安全回到默认黑色，不影响页面使用。
- [x] Web 与组件工作台都直接使用同一份已确认设计 token，不保存一套可手工漂移的颜色副本。
- [x] 字体、焦点、按钮、表单、链接、徽标、面板、浮层、提示和异步状态有可复用的代码基线，并在
      黑白两套主题下覆盖键盘、禁用、加载、错误、长中文和窄屏。
- [x] 当前登录、账户、管理、系统状态和导航页面迁移后，业务流程、权限、请求与文案含义保持不变。
- [x] 自动检查会拦截旧的深色分支、任意颜色、主题编号分支、过期生成物和缺失的双主题回归。
- [x] 在仓库规定的 Node 24 / npm 11 环境中，Web 检查、构建、Storybook 和浏览器测试全部通过。
- [x] 本工作不把“代码已支持主题”误写成“用户已经获得主题切换功能”；生产页面不新增切换入口。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-017` 查看实际进度。

## 待确认项

本工作已在人工验收中终止，不再等待实施确认。以下内容是当时获批并据以实施的历史边界，不代表最终
架构已经验收：

- 用户已批准 DECISION-012：构建时直接使用 `docs/design-system` 的 CSS 真源，只从主题 manifest
  生成类型注册表和首屏脚本，不把 token 复制进 Web。
- 继续使用项目现有 Base UI 作为无样式交互基础，`docs/frontend.md` 已同步；不同时维护两套 primitive。
- 本工作只交付基础层和当前真实消费者，不实现题目编辑器、判题生命周期、verdict 等 OJ 业务组件，
  也不新增用户可见主题切换器。

后续人工验收明确否决第一条的 direct-docs 依赖；VERIFY-017 记为 fail，WORK-018 负责把可复用实现
重构为删除设计系统文档后仍可独立检查、构建和运行的前端代码。

## 风险点

- 主题脚本、React 状态和 CSS 各自保存一份主题名单会形成三套真源；以 manifest 生成物和漂移检查约束。
- 全局默认从旧浅色切到黑色会同时影响全部现有页面；按 token/runtime、共享组件、现有页面三段迁移，
  每段独立构建与回归，出现问题可按段回退。
- 品牌红与危险红接近，组件若只换颜色仍可能误导；危险操作和状态必须同时使用明确文字、图标或结构。
- 当前组件清单的 verdict 变体漏了契约中的 `PE`，而 AsyncState 与页面 not-found 的归属也未冻结。
  本工作不据此实现 OJ 业务组件；在首次实现 Verdict 前必须另行修正文档合同与校验器。
- 日常本地环境仍是 Node 26；最终证据已在隔离的 Node 24.20.0 / npm 11.19.0 干净安装中复验。

## 影响面

设计者、Web 开发者、评审者和所有现有 Web 页面都会受到影响。用户会看到默认视觉从旧浅色基线切换为
已确认的 Cherry 黑色基线，已有页面的控件和状态表达会统一，但登录、权限、数据请求、路由和业务文案
不改变。改动限定在 Web、必要的前端长期文档和本 WORK；服务端、判题引擎、公共契约及设计 token 数值
真源不在实施范围内。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：完成设计系统、Web 基线和开发流程只读审计，拆分主题运行时、共享组件、现有页面迁移
  三个任务；仅提交文档等待人工审核，尚未修改 `apps/web` 实现。
- 2026-08-28：用户批准 WORK-017，并授权按 DECISION-012 执行 TASK-023～TASK-025。
- 2026-08-28：三个 TASK 实施完成；目标 Node/npm 干净安装、自动检查、两类构建、浏览器回归与独立
  复核全部通过，VERIFY-017 提交人工验证。未执行生产发布或线上观察。
- 2026-08-28：流程阶段 复核：ready → done。原因：两轮独立代码复核的四项 P2 均已修复并通过 unit、构建与真实 Chromium 复验
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → implemented。
- 2026-08-28：状态变更：implemented → cancelled。原因：核心构建边界未通过人工验收；保留可复用实现，由后续维护工作完成自包含重构
