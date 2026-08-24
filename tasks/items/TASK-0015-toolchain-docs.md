---
id: "TASK-0015"
title: "重写前后端工具链文档"
type: "docs"
area: "tooling/docs"
priority: "P1"
status: "in_progress"
assignee: "codex/root"
depends_on: []
related: []
requirement_ids: []
milestone: null
created_at: "2026-08-24T14:51:56+08:00"
updated_at: "2026-08-24T14:54:06+08:00"
claim_branch: "codex/task-0015"
claimed_at: "2026-08-24T14:54:06+08:00"
lease_until: "2026-08-25T14:54:06+08:00"
completed_at: null
review_required: true
---


# TASK-0015：重写前后端工具链文档

## 背景

前端已经接入 Vite、TypeScript、ESLint、Prettier、Vitest、Testing Library、MSW、Storybook 和
Playwright，后端也已经建立 Java 21、Maven Wrapper、Spring Boot 与五服务骨架，但这些工具的职责
主要散落在 `package.json`、POM、配置文件和教程中。产品负责人或刚进入项目的开发者只能看到一串
依赖名，很难判断一个页面从开发到验收会经过哪些工具，也无法区分浏览器运行依赖、构建工具和测试工具。

现有 `apps/web/README.md` 仍是 Vite 默认模板，继续介绍实际没有采用的 Oxlint；`apps/server` 没有进入
Git 的 README 或工具链说明；`CLAUDE.md` 仍把已经落地的前后端骨架标为“尚未开工”。这些漂移会让
新克隆得到错误的项目状态和操作入口。

## 目标

建立贴近代码、进入 Git、能由非专业读者理解的前后端工具链文档。读者应能从“开发一个页面或服务”
的实际流程理解每个直接工具在何时运行、解决什么问题、由哪条命令触发、配置和产物在哪里，以及删除
或失效后会发生什么。

## 范围

包含：

- 重写 `apps/web/README.md`，提供真实的安装、启动、页面开发、检查、测试、构建和常见问题入口。
- 新增 `apps/web/TOOLCHAIN.md`，解释所有直接 dependencies/devDependencies，并按运行、构建、质量、
  组件测试、真实浏览器验收和组件展厅归类。
- 新增 `apps/server/README.md`，说明五服务骨架、Maven Wrapper、构建、单服务启动、健康检查和当前边界。
- 新增 `apps/server/TOOLCHAIN.md`，解释 Java、Maven、Spring Boot Parent/Plugin、Spring Cloud BOM、
  Gateway WebFlux、MVC、Actuator、Validation 和测试依赖。
- 明确 npm dependencies/devDependencies 与 Maven runtime/test/build plugin 的区别，以及产品负责人需要
  关注哪些输出。
- 在文档中标明当前依赖归类与工具链边界的已知问题，但不在本任务调整依赖或锁文件。
- 更新根 README 和 `CLAUDE.md` 的导航与前后端骨架状态，移除“尚未开工”等失真描述。
- 检查 Markdown 本地链接、代码围栏和文档中的命令与当前配置一致。

不包含：

- 修改 `package.json`、`package-lock.json`、POM、依赖版本或依赖分类。
- 新增、删除或替换前后端开发工具。
- 实现页面、接口、数据库、Kafka、Security 或任何业务功能。
- 把工程工具链说明写入产品 REQ，或改变 REQ-0001 的状态和范围。
- 重写本地 `tutorial/` 的逐步搭建课程。

## 验收标准

- [ ] 前后端 README 不再包含脚手架默认文案，并能从新克隆开始给出可执行入口和预期结果。
- [ ] 前端 TOOLCHAIN 覆盖 `package.json` 中全部直接依赖和 devDependency，不只解释工具名称。
- [ ] 后端 TOOLCHAIN 覆盖根 POM、五个服务当前使用的直接 Starter、Maven Wrapper 和构建插件。
- [ ] 文档能按一次页面/服务开发流程解释开发服务器、类型检查、静态检查、组件测试、E2E 和打包的关系。
- [ ] 文档明确产品负责人需要关注 Storybook、可访问性、用户链路和构建结果，不要求其理解内部转换细节。
- [ ] 根 README 与 `CLAUDE.md` 的导航和当前进度与实际目录一致。
- [ ] Markdown 本地链接存在、代码围栏成对，命令和配置名称均能在当前仓库中找到对应真源。
- [ ] `npm run check`、`npm run build`、Maven `verify`、产品/任务检查和 `git diff --check` 通过。
- [ ] 未修改依赖清单、锁文件、POM、运行时代码、业务契约或产品需求。

## 产品影响

本任务不改变产品行为。它降低产品负责人审核工程结果时的理解成本，并让新开发者能够判断工具输出
与产品质量之间的关系；纯实现细节仍留在各工具配置中，不进入产品需求文档。

## 执行记录

- 2026-08-24T14:51:56+08:00：创建任务。
- 2026-08-24T14:54:06+08:00：codex/root 在分支 codex/task-0015 认领任务，租约 24 小时。

## 阻塞信息

无。

## 完成结果

尚未完成。
