---
id: "WORK-019"
type: "work"
title: "设计 Cherry OJ 任务入口主页"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["accessibility"]
depends_on: ["WORK-015"]
related: ["FEATURE-002", "EXPERIENCE-008", "TASK-027", "VERIFY-019", "MEMORY-015", "DESIGN-015"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-019"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["FEATURE-002"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-008"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "optional", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-015"], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "plan", "label": "开发计划", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:fast", "reason": "低风险、局部、可回退工作采用快速流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-027"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-027"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-019"], "checks": ["automated-tests", "accessibility"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-015"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}]
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









# WORK-019：设计 Cherry OJ 任务入口主页

## 为什么做

Cherry OJ 已经有一套确认过的界面规则，但当前主页仍是用来证明前后端连通的工程占位页。它没有清楚
回答使用者进入平台后应该先做什么，也没有把匿名访问、普通用户、首次登录需改密和管理员几种状态
组织成一套稳定体验。若直接从空白画布开始，很容易把主页画成与当前产品无关的营销长页、数据看板，
或者加入尚未承诺的排行榜、统计与多语言能力。

本工作先在 Figma 中形成一个可评审的“任务入口主页”目标稿：让普通学习者一眼看到浏览题库、完成
C++ ACM 答题流程和查看提交的入口；管理员入口保持次要；系统连通状态退到辅助位置。它只交付设计，
不修改当前 Web，也不把目标稿误称为已经上线的页面。

## 成功标准

- [x] 交付一个可编辑、可复用且可打开的 Figma 文件，并返回文件链接与主页主 Frame 链接。
- [x] 主页被设计成应用内任务入口，而不是营销长页、统计看板或组件展示页。
- [x] 桌面与 320px 窄屏都能清楚呈现匿名、普通用户和首次改密等关键状态。
- [x] 默认 `cherry-black` 与 `pure-white` 使用同一套语义结构，颜色、间距和组件不散落为无来源值。
- [x] 主要动作、正文、焦点和状态表达满足设计系统的对比、键盘与非颜色识别要求。
- [x] 不修改 `apps/web`、服务端、契约或既有工作项，也不暗示尚未实现的路由和数据已经可用。

## 当前流程

这是低风险、局部且可回退的产品设计工作，采用快速流程，并额外保留一份独立 Figma 方案。TASK-027
已经完成，目标 Draft、局部变量/组件、五类交付 Frame 和验证证据均已生成。Starter plan 的单集合单
mode 限制按用户批准采用“双独立 semantic collection + 组件 Theme 变体”降级；当前进入交付人工复核。

## 待确认项

- 请人工复核已交付的主页主 Frame、访客/白色/移动端 Frame 与状态矩阵是否满足产品意图；机器检查和
  执行侧视觉检查已通过，但不替代交付验收。
- 本次只交付 Figma 设计；若之后需要落地到 Web，必须另建或补充独立实施 TASK。

## 风险点

- 主页可能加入尚未存在的统计、排行榜、注册或多语言能力；FEATURE-002 用明确的非目标和验收扫描限制。
- Cherry 品牌红与危险红接近；设计中不以品牌色表示错误，必要状态同时使用文字和稳定图标/结构。
- 文档设计系统没有对应的已发布 Figma Library；执行时只在目标文件建立主页所需的语义变量与局部组件，
  不把本任务扩大成完整设计系统迁移。
- Figma 是外部状态；若布局失败，按 Frame/node id 做小范围修复，必要时删除本任务新建文件并重建，
  不触碰用户已有设计文件。

## 影响面

直接影响产品负责人、设计评审者和未来实施主页的 Web 开发者。仓库内只新增 WORK-019 文档与后续执行
记录；外部只新增一个 Figma Draft。`docs/design-system*`、当前 Web 页面、公共 API、服务端和判题链路
均保持不变。WORK-002 提供 C++ ACM 产品边界，WORK-015 提供视觉基线，但本工作不改变它们的状态。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：用户批准文档并允许执行 TASK-027，工作进入 doing。
- 2026-08-28：用户明确批准 Starter plan 降级：Cherry Black / Pure White 使用两个独立单 mode semantic
  collection，局部组件用 Theme 变体保持同一 anatomy。
- 2026-08-28：TASK-027 完成；Figma Draft、4 个变量集合、207 个变量、6 个组件集/34 个变体、5 个交付
  Frame 与 60 个实例通过机器检查和逐 Frame 视觉检查，工作进入 implemented 并等待人工复核。
