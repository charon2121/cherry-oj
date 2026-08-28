---
id: "WORK-018"
type: "work"
title: "解除 Web 对设计系统文档目录的依赖"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["compatibility", "accessibility"]
depends_on: []
related: ["CHANGE-008", "DESIGN-014", "DECISION-013", "PLAN-014", "TASK-026", "VERIFY-018", "MEMORY-014", "WORK-017"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "改动说明与边界", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CHANGE-008"], "checks": ["definition", "scope"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-014"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-013"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-014"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-026"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-026"], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["VERIFY-018"], "checks": ["automated-tests", "cross-module-regression", "accessibility", "compatibility"], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "release", "label": "上线", "requirement": "optional", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "observe", "label": "观察", "requirement": "optional", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:maintenance", "reason": "maintenance 基础流程"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["MEMORY-014"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["change", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "accessibility", "compatibility"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-28"
updated_at: "2026-08-28"
work_type: "maintenance"
---

















# WORK-018：解除 Web 对设计系统文档目录的依赖

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

WORK-017 已经把统一主题和共享组件接入 Web，但为了避免文档与代码不一致，构建、检查和组件工作台
直接读取了设计系统文档目录。人工验收指出这个边界不合理：文档应帮助人理解和评审产品，不应成为
前端能否安装、检查、构建或运行的必要条件。当前只要移除该文档目录，Web 就无法完成检查和构建。

本工作只解决这一件事：让前端完整持有自己运行所需的设计系统代码与校验能力。文档继续保留为设计说明；
以后确实修改设计系统时，再在同一项变更中同步文档与代码，而不是让日常构建跨目录读取文档。

## 成功标准

- [x] 从一份不包含 `docs/design-system` 的干净仓库副本安装依赖后，Web 的检查、生产构建、组件工作台
      构建和浏览器回归全部通过。
- [x] Web 的源码、配置、脚本和命令不再读取、执行或导入任何设计系统文档文件，也不通过复制前置步骤、
      符号链接或网络请求形成隐性依赖。
- [x] 主题、首屏、共享组件、现有页面、无障碍和响应式行为与 WORK-017 的有效实现保持一致，用户不会
      因本次工程重构看到新的产品行为。
- [x] 前端本地保留完整主题源码、语义合同、生成与对比度校验、来源声明和许可证；删除文档不会削弱
      设计系统的质量门禁或合规信息。
- [x] Web 的日常检查不比较文档与代码。未来真正修改设计系统时，由对应 WORK 同时更新两侧并分别验证。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-018` 查看实际进度。

## 待确认项

CHANGE-008、DESIGN-014、DECISION-013、PLAN-014 和 TASK-026 的实施授权均已确认。当前只等待用户人工
复核 VERIFY-018：确认“删除 `docs/design-system` 后前端不受影响”的自动证据满足本工作的验收意图。

## 当前状态

CHANGE-008、DESIGN-014、DECISION-013 与 PLAN-014 已获人工批准，旧 direct-docs 能力/体验已废弃，旧设计
与决定也已正式替代。TASK-026 已按批准边界完成代码侧自包含重构和正常/无设计文档双环境自动验证，
当前等待 VERIFY-018 的人工复核；这不代表生产发布或线上观察已经完成。

## 风险点

- 只搬两份聚合 CSS 会漏掉其相对导入、主题 manifest、生成器和校验器；用本地包清单与删除目录后的
  干净构建证明依赖确实解除。
- 精简文档侧校验器时可能意外丢掉主题完整性或对比度门禁；前端本地检查必须继续证明两主题、全部语义
  token、允许组合和 Tailwind adapter 的合同。
- 旧 `dist`、`storybook-static` 或 `node_modules` 会让隔离测试假绿；验收副本必须排除这些产物并从
  `npm ci` 开始。
- 派生样式来自带许可证的上游材料；许可证与来源说明必须迁入代码侧并随静态交付保留。
- 文档与代码不再自动逐字比较，今后的设计系统变更必须在同一 WORK/TASK 中明确同步两侧。

## 影响面

影响 Web 开发者、CI、Storybook 和静态站构建；实现范围集中在 `apps/web` 的设计系统资产、生成与检查
脚本、构建配置，以及说明这些边界的项目文档。不会修改视觉数值、主题编号、组件公开接口、页面业务
逻辑、API、服务端、判题引擎或公共契约。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：由 WORK-017 人工验收失败创建；完成跨目录依赖、校验能力、许可证和隔离验收边界审计，
  仅提交文档等待人工审核，尚未修改实现。
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → ready。
- 2026-08-28：根据文档、任务与验证事实刷新状态：ready → doing。
- 2026-08-28：根据文档、任务与验证事实刷新状态：doing → implemented。
