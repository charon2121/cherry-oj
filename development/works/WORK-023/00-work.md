---
id: "WORK-023"
type: "work"
title: "设计双端导航栏与导航功能组件"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: ["WORK-020", "WORK-022"]
related: ["FEATURE-005", "EXPERIENCE-011", "TASK-031", "VERIFY-023", "MEMORY-018", "DESIGN-018"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-023"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["FEATURE-005"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-011"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-018"], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-031"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-031"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-023"], "checks": ["automated-tests", "accessibility"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-018"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}]
required_documents: ["feature", "experience", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "accessibility"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: true
created_at: "2026-08-29"
updated_at: "2026-08-29"
work_type: "product"
---













# WORK-023：设计双端导航栏与导航功能组件

## 为什么做

WORK-020 与 WORK-022 已经搭好用户端和管理端页面骨架，但导航目前仍是为了验证布局而设置的最小版本：
用户端只有首页，登录用户的账号动作散在页头；管理端菜单虽然已有 Dashboard 和账号管理，却还没有一套
明确的“什么功能应该放进全局导航、如何随页面增加而扩展”的规则。

本工作在现有骨架上设计一套稳定的双端导航系统。它既要让访客、用户和管理员快速找到当前位置与可用
入口，也要给后续题库、提交记录和管理功能留下清晰的组件边界；尚未交付的页面不能为了填满导航而出现。

## 成功标准

- [x] 用户端页头稳定承载品牌、当前可用主导航和账号区域，访客、USER、ADMIN 各自只看到可执行的动作。
- [x] 登录后的账号、改密、管理入口和退出操作有统一承载位置，窄屏不因动作增多而挤坏页头。
- [x] 管理端页头与侧栏职责清楚：页头负责空间切换和账号，侧栏负责 Dashboard 与分组业务导航。
- [x] 管理侧栏继续支持二级子菜单，并能从一份导航定义得到桌面和移动端一致的标签、顺序与当前状态。
- [x] 未交付的题库、提交、通知、搜索、主题等能力不显示空入口；新增页面时有明确接入规则。
- [x] 两端导航在 320px、键盘、200% 缩放、双主题及辅助技术下仍可识别当前位置并完成切换。

## 当前流程

由 WORK Type 基础模板与风险、影响面和 concern 增量规则生成；使用 `scripts/work flow WORK-023` 查看。

## 待确认项

需要人工确认 FEATURE-005、EXPERIENCE-011、DESIGN-018 与 TASK-031，并在后续消息中明确允许实施。本轮
只完成导航信息架构、组件职责和开发边界，不修改 Web 实现。

## 风险点

- 把未来能力提前放进导航会产生死链接或误导；通过路由清单与导航可达性测试发现，只渲染已交付入口。
- 账号动作收进菜单后可能隐藏首次改密或退出错误；通过三种 Session、改密要求和 Mutation 失败场景检查。
- 桌面和移动端各维护一份菜单会发生顺序、权限或文案漂移；使用共享导航定义并分别做组件/E2E 断言。
- 当前项匹配过宽可能让多个入口同时激活；对 Dashboard 双入口、子路由和查询参数验证精确匹配规则。

## 影响面

影响所有 Web 访客、登录用户与管理员的全局导航体验，也影响后续页面接入方式。实现范围局限在
`apps/web` 现有双端 Shell、导航/账号组件、必要的 shadcn/ui 覆盖组件及测试；不新增业务路由、API、
权限、主题、设计 token 或后端能力，也不改变 WORK-022 已确认的 Footer 结构。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-29：创建工作项并生成初始流程。
- 2026-08-29：完成双端导航信息架构、账号区、响应式、扩展位与组件边界草案，提交人工审核。
- 2026-08-29：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-29：用户批准 WORK-023 并授权 TASK-031；完成双端导航、账号菜单、共享模型及回归验证。
- 2026-08-29：根据文档、任务与验证事实刷新状态：doing → implemented。
