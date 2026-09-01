---
id: "PLAN-013"
type: "plan"
title: "建立 Web 设计系统代码基建"
status: "approved"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["CAPABILITY-006", "EXPERIENCE-007", "DESIGN-013", "DECISION-012"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# PLAN-013：建立 Web 设计系统代码基建

## 目标

在 CAPABILITY-006、DESIGN-013 与 DECISION-012 获人工批准并明确允许实施后，分三步把 docs 设计系统
接入 Web 运行时、建立共享组件/Storybook 基线，并迁移当前页面与持续门禁。交付后代码与文档合同一致，
但不新增生产主题切换入口或 OJ 业务组件。

## 改动区域

- `docs/frontend.md`：把与实际依赖冲突的 Radix 基线收敛为 Base UI；不修改设计 token 数值合同。
- `apps/web/index.html`、`vite.config.ts`、`package*.json`、`src/styles`、
  `src/lib/theme`、`src/generated/design-system`、`public/generated`、`scripts`：
  canonical CSS、Inter、生成与主题运行时。
- `apps/web/src/components/ui`、`.storybook`：通用组件合同、测试、Stories 和双主题工作台。
- `apps/web/src/routes` 与现有 feature 组件：消费者迁移，不改 API/权限/路由业务。
- `apps/web/e2e`、Web `check`：主题、首屏、响应式、无障碍与漂移门禁。
- 当前 WORK：任务状态、实际验证和遗留风险。

## 阶段与顺序

0. **人工审核**：确认 DECISION-012 的直接 import + manifest 生成、Base UI、storage key、组件范围和
   “无生产切换器/无 OJ 业务组件”边界，并明确允许执行。
1. **TASK-023**：接入 canonical token/adapter；生成 registry 和首屏脚本；建立主题模块、Inter、
   单元测试与生成漂移检查；同步前端技术文档。保持业务 routes/features 不变。
2. **TASK-024**：在运行时基线上建立 Button、Field、Link/Icon、Badge、Card/Panel、
   Dialog/Popover、Typography/Layout、InlineNotice、AsyncState；为每项补组件测试和双主题 Storybook。
   为保持现有 routes/features 可编译，Button 可暂留带退出条件的 `outline → secondary` 和
   `lg → md` 兼容别名。
3. **TASK-025**：迁移当前 app shell、routes 与 feature 组件；清除旧样式/模板资产；加入 Web 源码扫描、
   主题 E2E 和现有业务跨模块回归；迁移调用并删除 Button 两个临时别名，把门禁接入
   `npm run check`。
4. **VERIFY-017**：在 Node 24 / npm 11 下执行全部自动检查和浏览器/人工矩阵；独立复核影响面后再决定
   implemented/verified，不能因构建通过自动签署产品或发布确认。

## 并行与依赖

TASK-024 依赖 TASK-023 的 token、生成 registry 与主题 API；TASK-025 依赖共享组件，不能并行改同一
消费者。TASK-024 内不同组件可在合同冻结后并行实现，但统一由 Storybook decorator 和基础测试工具
收口。文档设计系统 check、生成器单测和现有 Web 只读基线可以提前运行。

## 迁移与交付

三个 TASK 均应保持可编译：先接线、再组件、再消费者。生产构建把 docs CSS 与首屏脚本打包/复制到
`dist`，运行时不依赖 docs 服务。上线时默认视觉切到 Cherry 黑色；已有有效 pure-white 偏好可由
运行时恢复，但仓库当前没有产品入口创建该偏好。上线后观察首屏主题、静态资源、控制台错误、登录/
账户/管理页面和双主题响应式。没有生产环境则 release/observe 保持未完成。

## 风险

- 直接跨目录 import 在 Vite 与 Storybook 配置不同；两条构建都必须先做最小验证。
- 首屏脚本若与 React resolver 规则漂移会闪烁或切回；两者只消费同一生成 registry，并用 E2E 钉住。
- 一次重写太多组件会混入业务重设计；TASK-024 固定通用层，TASK-025 保持业务语义不变。
- 强扫描过早启用会阻断分段迁移；先实现/迁移，最后在 TASK-025 开启全量门禁。
- 本机 Node 26 不符合工具链；最终判断以 CI 同构 Node 24/npm 11 为准。
- Verdict manifest 漏 `PE`；本计划明确排除 Verdict，不能用不完整列表先写代码。

## 验证

- 设计源：`node docs/design-system/tools/build.mjs --check` 与
  `node docs/design-system/tools/check.mjs`；预期 2 个主题、56 required key、296 个对比组合通过。
- 生成：`npm run generate:design-system:check`；修改 manifest 后未生成、手改生成物或文件缺失必须失败。
- Web 静态门禁：无 raw hex/OKLCH、旧 `.dark`、literal theme-id 分支、`--ds-raw-*`、
  必要对比 `color-mix()` 和 disabled/placeholder opacity。
- 主题单测：missing/empty/black/white/unknown、存储读取/写入异常、storage event、DOM 两属性原子更新。
- 组件：每个基础组件的语义、键盘、disabled/loading/error、label/description/error 关联和禁止组合。
- Storybook：双主题 × variant/state，320px、长中文、键盘焦点、reduced-motion；静态构建通过。
- 浏览器：首次默认黑、保存 white 刷新无黑闪、未知偏好直接黑、forced-colors/reduced-motion、
  两主题无横向裁切；现有登录、账户、管理、403/404 和系统状态 smoke。
- 工程：Node 24/npm 11 下 `npm run check`、`npm run build`、
  `npm run storybook:build`、`npm run test:e2e` 全部通过，并记录实际数量与环境。

## 回退

无数据库或服务端迁移。按 TASK-025 → TASK-024 → TASK-023 逆序回退：消费者可先恢复旧组件调用；
共享组件可恢复上一实现；最后恢复旧全局 CSS/入口与依赖。偏好值无法识别时 resolver 自动回到黑色，
无需数据清理。若仅首屏脚本故障，HTML 默认黑色仍可保证页面可用。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：三任务实施顺序、验证矩阵与回退方案已形成草案，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准三任务顺序、验证矩阵与回退计划
