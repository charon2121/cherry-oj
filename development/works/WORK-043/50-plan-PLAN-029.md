---
id: "PLAN-029"
type: "plan"
title: "题目内提交记录与代码回看"
status: "checked"
work: "WORK-043"
owners: ["codex/root"]
depends_on: ["FEATURE-011", "EXPERIENCE-019", "DESIGN-037", "DECISION-024"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# PLAN-029：题目内提交记录与代码回看

## 目标

完成 FEATURE-011 的历史查询、源码回看、受保护载入和 AC-001 至 AC-007。

## 改动区域

公开契约、submission-service、Gateway 提交接口、Web 题目路由 search/左侧 Tabs/提交记录与草稿载入及测试。TASK 明确各自边界。

## 阶段与顺序

1. 人工审核定义、体验和技术决策并签署意图闸，后续明确允许执行。
2. TASK-082：先更新契约，再实现后端本题列表/源码，完成跨账号、分页与历史记录回归。
3. TASK-083：生成 API 类型，实现左侧 Tabs 内本题列表、panel 内详情及确认载入，保留当前提交恢复流程。
4. TASK-084：运行集成、安全、E2E 与 Web 质量检查，截图并逐条回答设计系统八问，完成独立复核，记录 VERIFY 和 MEMORY。
5. 提交人工验收，不由智能体执行 gate。

## 并行与依赖

首版按任务顺序执行，避免契约与生成代码冲突。不主动派生子智能体；独立复核由独立人员完成，若用户后续授权独立智能体复核再委派。人工复核的确认来源必须记录，不冒充 Agent 自评。

## 迁移与交付

无数据库迁移，后端增量接口先于前端发布。源码始终在 submission-service 读取，前端仅确认载入后走既有草稿存储。交付代码与验证证据；不自动提交、推送、部署或重启既有环境。

## 风险

源码隐私、历史版本误导与草稿覆盖优先于视觉完整；遇到索引迁移或新增共享组件需求先更新方案及边界。

## 验证

后端从 apps/server 执行 ./mvnw -pl submission-service,gateway-service -am test，数据隔离用真实 MySQL 测试验证查询，保持原提交/消息回归；契约使用仓库既有检查脚本。Web 使用 npm run generate:api、npm run check、npm run build 和相应 Playwright E2E。记录实际版本、命令、结果和跳过原因，不以占位文档当测试证据。

## 回退

撤回左侧记录 Tab和新增读取接口，不回滚历史提交或草稿数据；兼容旧前端 POST/get/恢复键 API。只回退本工作差异，保留其他工作的改动。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：已完成现有能力核对、本人源码边界、历史版本与草稿保护方案及任务拆分，提交意图审核

- 2026-09-07：按用户澄清收敛为题目工作台左侧 Tabs；撤回全局页面、独立详情页、题目解析及导航修改范围，未来个人简介页仅记录为候选方向。
- 2026-09-07：结构与内容校验通过，由工具置为 checked。
