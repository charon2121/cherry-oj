---
id: "WORK-015"
type: "work"
title: "建立 Cherry OJ Web 设计系统"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["accessibility", "compatibility"]
depends_on: []
related: ["CAPABILITY-005", "EXPERIENCE-006", "DESIGN-012", "DECISION-011", "PLAN-012", "TASK-021", "VERIFY-015", "MEMORY-012"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-015"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-005"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-006"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-012"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-011"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-012"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-021"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-021"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-015"], "checks": ["automated-tests", "cross-module-regression", "accessibility", "compatibility"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-012"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "accessibility", "compatibility"]
human_confirmations: []
gates: {"intent": "passed", "acceptance": "pending"}
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-27"
updated_at: "2026-08-27"
work_type: "infra"
---

















# WORK-015：建立 Cherry OJ Web 设计系统

## 为什么做

Cherry OJ 已有一份早期界面说明，但它只“借鉴 Linear”，实际文档、网页样式和示例之间已经出现
不同的颜色与规则。继续在这个基础上画页面或写组件，不同参与者会各自理解“像 Linear”意味着什么，
最终形成多套按钮、间距、主题和状态表达。

本工作把用户指定的 OpenDesign Linear 设计系统整理成 Cherry OJ 的长期界面基线：完整保留其黑色
主题并作为默认主题，沿用字体节奏、间距、圆角、边框、动效和组件结构，只把 Linear 的紫色交互族
替换为 Cherry 樱桃红；同时基于同一结构增加以纯白为画布的浅色主题。以后新增或修改共享组件都从
同一套语义规则出发，不再从空白画布或个人偏好开始。

## 成功标准

- [x] `docs/` 中存在一个自包含、可离线阅读的 Cherry OJ 设计系统，不依赖个人下载目录。
- [x] 除品牌/交互色、Cherry 命名、来源声明和 OJ 必需语义外，Linear 基础规则没有无说明漂移。
- [x] 人与工具都能从同一处取得颜色、字体、间距、圆角、动效和组件状态规则。
- [x] Cherry 主色与危险/判题失败语义即使不看颜色也能区分，正文、控件和焦点满足可访问性门槛。
- [x] 旧的“蓝紫主交互”基线被明确替代，不留下两份都自称有效的 UI 真源。
- [x] 缺省情况下使用 Linear 黑色主题；pure-white 浅色主题覆盖同一套语义和组件状态。
- [x] 新主题只需实现稳定合同、登记 metadata 并运行生成/校验工具，不需要修改聚合入口、组件或
      Tailwind class。
- [x] 本工作只交付设计系统文档和视觉参考；不会暗示现有 Web 实现已经完成迁移。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-015` 查看实际进度。

## 待确认项

用户已在 2026-08-27 明确批准 DESIGN-012 的双主题色值、Cherry/danger 分离、selector、manifest、
扩展合同和 docs-only 范围，并明确允许执行 TASK-021。实现现已完成并进入 VERIFY-015 人工复核；
待确认项不是重新决定方案，而是检查实际组件参考后决定是否签署 `approved/pass`。

## 风险点

- Cherry 红与错误红接近，单靠颜色会把主操作误读为危险操作；所有危险、错误和 verdict 必须同时有
  稳定文字、图标或形状，验收时单独检查。
- 机械复制 Linear 的名称、Logo、营销模板或声称“官方 Linear 设计系统”会造成来源与商标误导；只
  采用 OpenDesign fixture 的视觉规则，并保留 Apache-2.0 归属和修改声明。
- 现有文档、视觉合同和运行时 token 已经漂移；本工作明确文档真源和迁移边界，不把尚未执行的代码
  迁移写成既成事实。
- pure-white 不能靠简单反色生成；正文、弱文字、边框、状态和 Cherry 交互色必须分别通过对比检查。
- 多主题若允许组件判断具体 theme id，会迅速产生分支；组件只能消费语义 token，主题新增不能修改
  组件 contract。

## 影响面

设计者、Web 开发者、代码评审者和后续 Agent 都会受这套基线约束。长期文档入口、前端视觉规则和旧
HTML 视觉合同会在批准后更新；本任务不修改 `apps/web` 运行时代码、服务端、判题引擎、公共契约或
产品业务行为。现有组件迁移需要独立任务与再次审核。

本 WORK 流程中的“上线”指把已批准规范合并到默认分支并成为仓库入口，“线上观察”指合并后在干净
克隆中复查入口、链接、派生产物和首个后续组件的采用情况；不代表部署独立服务或声称 Web 已迁移。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-27：创建工作项并生成初始流程。
- 2026-08-27：完成 Linear fixture、Cherry 现有品牌色和 Web 样式的只读对照，形成待人工审核的
  能力、体验、设计、决策、计划、任务和验证草案；尚未修改全局设计文档或运行时代码。
- 2026-08-27：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-27：用户确认 Linear 黑色为默认、增加 pure-white 浅色主题，并要求主题架构支持未来扩展；
  回到方案层补充双主题 token 与主题合同。
- 2026-08-27：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-27：用户批准双主题方案并允许执行；完成 docs 设计系统、全局入口与验证证据，运行时代码
  保持不变，准备将 TASK-021 标记完成并把 VERIFY-015 提交人工复核。
- 2026-08-27：根据文档、任务与验证事实刷新状态：doing → implemented。
