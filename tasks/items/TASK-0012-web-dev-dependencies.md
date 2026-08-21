---
id: "TASK-0012"
title: "修复 Web 前端依赖缺失导致启动失败"
type: "bug"
area: "web/tooling"
priority: "P0"
status: "done"
assignee: "codex/root"
depends_on: []
related: ["TASK-0011"]
created_at: "2026-08-20T17:58:32+08:00"
updated_at: "2026-08-21T17:58:12+08:00"
claim_branch: "codex/task-0012"
claimed_at: "2026-08-20T17:58:54+08:00"
lease_until: "2026-08-21T17:58:54+08:00"
completed_at: "2026-08-21T17:58:12+08:00"
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

- [x] `apps/web/package-lock.json` 已生成并与 `package.json` 一致。
- [x] `npm run dev` 启动后打印可访问的本地地址，不再出现 unresolved import。
- [x] `npm run lint` 成功。
- [x] `npm run build` 成功。
- [x] 修复没有覆盖或重写用户现有的 `src/` 内容。

## 执行记录

- 2026-08-20T17:58:32+08:00：创建任务。
- 2026-08-20T17:58:54+08:00：codex/root 在分支 codex/task-0012 认领任务，租约 24 小时。
- 2026-08-20：复现 `vite` 与 `@vitejs/plugin-react` unresolved import；确认根因是依赖与锁文件缺失。
- 2026-08-20：在 `apps/web` 执行 `npm install`，安装 28 个包并生成 `package-lock.json`。
- 2026-08-20：`npm run dev -- --host 127.0.0.1` 以 Vite 8.2.2 启动，访问根路径返回 HTTP 200；
  `npm run lint` 与 `npm run build` 均成功。
- 2026-08-20T18:00:51+08:00：实现与验证完成，进入 review。
- 2026-08-21T17:58:12+08:00：验收完成，任务关闭。

## 阻塞信息

无。

## 完成结果

根因是 apps/web 未安装 package.json 声明的依赖。已执行 npm install 生成 package-lock.json；Vite 8.2.2 开发服务器在 127.0.0.1:5173 正常启动且返回 HTTP 200，npm run lint 与 npm run build 通过，未改写用户 src 内容。

前端依赖、开发服务器与完整工程门禁均已验证通过。
