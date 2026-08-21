---
id: "TASK-0013"
title: "完善 Web 前端工程门禁与 CI"
type: "chore"
area: "web/tooling"
priority: "P0"
status: "in_progress"
assignee: "codex/root"
depends_on: []
related: ["TASK-0011", "TASK-0012"]
created_at: "2026-08-21T17:32:47+08:00"
updated_at: "2026-08-21T17:33:15+08:00"
claim_branch: "codex/task-0012"
claimed_at: "2026-08-21T17:33:15+08:00"
lease_until: "2026-08-22T17:33:15+08:00"
completed_at: null
review_required: true
---


# TASK-0013：完善 Web 前端工程门禁与 CI

## 背景

用户已手工完成 `apps/web` 骨架，但现有 Git hooks 只覆盖 Go 与 contracts，CI 也没有执行前端门禁。
同时，首次完整审计发现 ESLint 依赖未完整声明、Playwright smoke 放在 Vitest 扫描范围内、格式检查
未通过。若直接合并，`npm run check` 和 CI 都无法可靠保护前端主干。

## 目标

修复前端骨架的工程质量问题，将 Prettier、ESLint、类型检查、组件测试、生产构建、Storybook 与
Playwright 接入本地 hook 和 GitHub Actions，并保证新克隆可以通过锁文件复现验证。

## 范围

包含：

- 审计 `apps/web` 的依赖、目录与统一 npm scripts，修复阻塞门禁的最小问题。
- pre-commit 仅检查暂存前端文本文件的 Prettier 与 ESLint 结果，不改写工作区或暂存区。
- pre-push 按 Go/Web 改动独立判断，前端改动运行 `npm run check` 与 `npm run build`。
- CI 新增独立 Web job，使用 Node 24 与 `npm ci` 执行完整前端验证。
- 删除 Storybook 初始化器的通用示例，只保留与真实组件同目录的 story。

不包含：

- 登录、题库、编辑器、提交与判题轮询等业务功能。
- Gateway、Java 服务、judge 或部署拓扑修改。
- 引入当前阶段未确定的前端库或云端视觉回归服务。

## 验收标准

- [ ] `npm run check`、`npm run build`、`npm run storybook:build` 与 `npm run test:e2e` 全部通过。
- [ ] ESLint 配置引用的工具均为直接开发依赖，移除未使用的 Oxc 配置与依赖。
- [ ] Playwright 用例仅位于 `apps/web/e2e/`，不会被 Vitest 收集。
- [ ] pre-commit 对暂存前端文件只检查不改写，缺失本地依赖时给出明确提示。
- [ ] pre-push 分别处理 Go 与 Web 改动，不因一侧无改动而提前跳过另一侧。
- [ ] CI 使用 Node 24、`npm ci` 并运行 check、生产构建、Storybook 静态构建与 Chromium E2E。
- [ ] hook shell 语法、任务中心校验和工作区 diff 检查通过。

## 执行记录

- 2026-08-21T17:32:47+08:00：创建任务。
- 2026-08-21T17:33:15+08:00：codex/root 在分支 codex/task-0012 认领任务，租约 24 小时。

## 阻塞信息

无。

## 完成结果

尚未完成。
