---
id: "TASK-0011"
title: "编写 Web 前端项目初始化教程"
type: "docs"
area: "tutorial/web"
priority: "P0"
status: "in_progress"
assignee: "codex/root"
depends_on: ["TASK-0009"]
related: []
created_at: "2026-08-20T17:21:23+08:00"
updated_at: "2026-08-20T17:22:01+08:00"
claim_branch: "codex/task-0011"
claimed_at: "2026-08-20T17:22:01+08:00"
lease_until: "2026-08-21T17:22:01+08:00"
completed_at: null
review_required: true
---


# TASK-0011：编写 Web 前端项目初始化教程

## 背景

`docs/architecture.md` 已确定浏览器只能通过 Gateway 的 `/api` 访问后端，`docs/frontend.md` 也已经
敲定 React 19、严格 TypeScript、Vite、TanStack Router/Query、Tailwind CSS、shadcn/ui、测试与质量
门禁，但 `apps/web` 目前仍是空目录。用户希望亲手搭建工程骨架，因此本任务不代写前端项目，而是提供
一篇可以逐步执行、每一步都有设计依据和验收点的 M4 教程，避免脚手架默认值、一次性安装全家桶和
前端状态边界在初始化时就发生漂移。

## 目标

编写 `tutorial/07-m4-web-frontend-skeleton.md`，让用户从空的 `apps/web` 出发，手工得到一个可开发、
可构建、可测试的 React SPA 骨架，并把 Router、Query、UI token、测试工具和工程质量门禁放到正确边界。

## 范围

包含：

- Node.js LTS、npm、工作区与前端架构边界的前置检查。
- 使用 Vite React TypeScript 模板初始化 `apps/web`，保留 `package-lock.json`。
- TanStack Router 文件路由、TanStack Query Provider 与 Vite `/api` 开发代理的最小接线。
- Tailwind CSS、shadcn/ui/Radix、Lucide、`cn()` 与 Cherry OJ 语义 token 的初始化。
- TypeScript strict 加强项、ESLint Flat Config、Prettier 与统一 npm scripts。
- Vitest、React Testing Library、user-event、MSW、Storybook a11y 与 Playwright smoke 的初始验证方式。
- 最小目录结构、完整关键配置、每步检查点、最终验收和常见问题。
- 更新 `tutorial/README.md` 的 M4 阶段地图和前后篇链接。

不包含：

- 实际创建或修改 `apps/web` 下的项目文件。
- 登录、题库、编辑器、提交、判题轮询等业务页面实现。
- 提前引入 TanStack Table/Form/Virtual、Monaco、Markdown、IndexedDB 或 OpenAPI 生成器。
- 修改 Gateway、部署配置、CI、Git hooks，或创建阶段 Tag/发布制品。

## 验收标准

- [ ] 教程头部明确 M4、依赖、产物和“不代写 `apps/web`”边界。
- [ ] 所有命令以仓库根目录为基准，并写明关键交互选择、期望文件或输出。
- [ ] 依赖分批安装且与 `docs/frontend.md` 的按需引入顺序一致，不混入已明确排除的库。
- [ ] Router、Query、Vite、Tailwind、shadcn/ui 和语义 token 的最小接线完整且依赖方向正确。
- [ ] TypeScript、ESLint、Prettier 和 npm scripts 与 `CLAUDE.md` 的前端规则一致。
- [ ] Vitest/Testing Library/MSW、Storybook a11y 与 Playwright smoke 都有可执行的最小验证。
- [ ] 验收覆盖 `format:check`、`lint`、`typecheck`、`test:run`、`build`、Storybook build 和 E2E。
- [ ] 常见坑覆盖 Node 版本、在错误目录生成工程、Router 生成文件、别名、Provider、Tailwind 和代理边界。
- [ ] `tutorial/README.md` 已加入 M4，Markdown 围栏成对，本地链接有效。

## 执行记录

- 2026-08-20T17:21:23+08:00：创建任务。
- 2026-08-20T17:22:01+08:00：codex/root 在分支 codex/task-0011 认领任务，租约 24 小时。

## 阻塞信息

无。

## 完成结果

尚未完成。
