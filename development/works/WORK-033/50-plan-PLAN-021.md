---
id: "PLAN-021"
type: "plan"
title: "重设计后台题目创建与编辑体验"
status: "checked"
work: "WORK-033"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-001", "EXPERIENCE-017", "DESIGN-026", "DESIGN-027", "DECISION-018"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# PLAN-021：重设计后台题目创建与编辑体验

## 目标

在不改变后端/API/数据的前提下，把后台题目管理与版本工作台改造成可恢复的六步创作流程，使用设计系统
一致的 CodeMirror 编辑器、结构化样例、明确保存状态和可导航的发布就绪信息。

## 改动区域

Web 依赖与锁文件、工具链说明、通用 UI 编辑器/步骤组件、题目管理与工作台业务组件、版本路由 search、
组件/Storybook/E2E 测试、TypeScript 规范、长期设计系统说明和 WORK-033 证据。后端、OpenAPI、生成 API
类型、数据库、公开题库和用户端页面不在改动范围。

## 阶段与顺序

1. 在长期设计系统说明中冻结编辑器选择、长表单、结构化重复项、状态/禁用原因和危险操作规则。
2. 安装 CodeMirror 官方最小依赖，建立 `TextEditor`、Markdown 预览组合、主题/扩展、Storybook 和组件
   测试；验证后移除后台 Monaco 导入、依赖和过期说明。
3. 重构题目管理页：创建入口进入 Dialog，补字段级校验、ACM/C++ 说明、错误映射和焦点恢复。
4. 建立工作台 provider、sticky 上下文栏、六步响应式导航、URL step、dirty/save 状态、快捷键和离开
   保护；先保持旧字段渲染完成状态骨架。
5. 实现基本信息、Markdown 题面、tag chip、结构化样例和 C++ 起始代码，并验证 payload 完全等价。
6. 实现上传后绑定恢复、部署/校准依赖链、s/MiB 单位转换和参考源码离开提示。
7. 实现自动发布检查、检查项步骤映射、版本/可见性/危险操作分组和项目 Dialog。
8. 更新测试与工具链文档，执行 Web 全量门禁和真实浏览器矩阵，记录 VERIFY-034、独立复核与 MEMORY-026。

## 并行与依赖

编辑器 primitive 必须先稳定，业务步骤才能消费；工作台 provider/路由壳层先于各步骤拆分。列表创建
Dialog 与编辑器可独立实施，但最终一起验收。发布检查映射依赖六步 id 已冻结。任何 API 缺口都会阻断
实施并要求修改定义/设计边界，不能并行越过。

## 迁移与交付

一次 Web 交付完成新旧组件替换，不保留用户可切换的双工作台。现有数据由前端适配读取，无数据库或服务
部署顺序。CodeMirror 依赖、锁文件、源码和工具链文档同一提交；路由缺少 step 时兼容进入默认步骤。

## 风险

高风险点为编辑器受控/IME、保存 rowVersion、步骤切换不丢字段、上传成功但绑定失败、单位往返精度、
小屏 sticky 占高、发布检查陈旧以及危险操作焦点。实现中不得把前端“已完成”当作服务器“可发布”。

## 验证

- 编辑器：双主题、中文组合输入、外部值同步、唯一 aria label、Tab 退出、搜索/撤销、预览净化、卸载。
- 创建：字段校验、409/服务错误、键盘 Dialog、取消和成功导航。
- 工作台：六步 URL、前进后退、未保存状态、保存快捷键、离开保护、冲突保留、刷新恢复。
- 样例/标签：新增、复制、排序、删除、去重、连续 ordinal 与请求 payload。
- 测试链：上传+绑定两阶段恢复、禁用原因、部署/校准、单位往返和参考源码清除。
- 发布：六项映射、自动刷新、跳转、ready 门禁、确认 Dialog、失败保持旧版本。
- 门禁：`npm run format` 后执行 `npm run check`、`npm run build`、`npm run storybook:build`、
  `npm run test:e2e`、设计文档 check、`scripts/work check` 和 `git diff --check`；浏览器覆盖 320px、桌面、
  200% 缩放、两个主题、键盘、forced-colors 与 reduced-motion。

## 回退

按单一 Web 提交整体回退旧工作台和依赖，不回滚任何数据。若只发现 CodeMirror 缺陷，允许在同一工作项
回退编辑器适配并保留已验证的分步壳层；若分步状态/保存不可靠，整体回退工作台而不保留半套导航。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：实施顺序、边界、验证、交付与回退计划已补全，提交结构校验
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
