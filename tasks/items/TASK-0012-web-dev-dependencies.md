---
id: "TASK-0012"
title: "修复 Web 前端依赖缺失导致启动失败"
type: "bug"
area: "web/tooling"
priority: "P0"
status: "in_progress"
assignee: "codex/root"
depends_on: []
related: ["TASK-0011"]
created_at: "2026-08-20T17:58:32+08:00"
updated_at: "2026-08-20T17:58:54+08:00"
claim_branch: "codex/task-0012"
claimed_at: "2026-08-20T17:58:54+08:00"
lease_until: "2026-08-21T17:58:54+08:00"
completed_at: null
review_required: true
---


# TASK-0012：修复 Web 前端依赖缺失导致启动失败

## 背景

用户已经用 Vite 创建 `apps/web`，但目录中没有 `node_modules` 和 `package-lock.json`。执行
`npm run dev` 时，Vite 配置无法解析 `vite` 与 `@vitejs/plugin-react`，开发服务器无法启动。

## 目标

安装并锁定 `apps/web` 当前声明的依赖，确认开发服务器可以启动，并用 lint 与生产构建验证脚手架基线。

## 范围

包含：

- 复现并记录 `npm run dev` 的实际错误。
- 通过 `npm install` 生成 `package-lock.json` 与本地依赖目录。
- 验证 `npm run dev`、`npm run lint` 和 `npm run build`。
- 若安装后暴露新的脚手架兼容错误，进行最小范围修复。

不包含：

- 按 M4 教程一次性接入 Router、Query、Tailwind、shadcn/ui 或测试栈。
- 重写用户刚生成的 Vite 示例页面。
- 修改 Java Gateway 或其它后端服务。

## 验收标准

- [ ] `apps/web/package-lock.json` 已生成并与 `package.json` 一致。
- [ ] `npm run dev` 启动后打印可访问的本地地址，不再出现 unresolved import。
- [ ] `npm run lint` 成功。
- [ ] `npm run build` 成功。
- [ ] 修复没有覆盖或重写用户现有的 `src/` 内容。

## 执行记录

- 2026-08-20T17:58:32+08:00：创建任务。
- 2026-08-20T17:58:54+08:00：codex/root 在分支 codex/task-0012 认领任务，租约 24 小时。

## 阻塞信息

无。

## 完成结果

尚未完成。
