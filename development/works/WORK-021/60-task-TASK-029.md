---
id: "TASK-029"
type: "task"
title: "修复 IDEA 错误按叶子工程构建 user-service"
status: "todo"
work: "WORK-021"
owners: ["codex/root"]
depends_on: ["ISSUE-004", "DESIGN-017"]
related: []
implements: ["ISSUE-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "apps/server/pom.xml", "apps/server/.gitignore", "apps/server/.run", "apps/server/README.md", "apps/server/TOOLCHAIN.md", "apps/server/logging-support", "apps/server/user-service", "development/works/WORK-021"]
write_paths: ["apps/server/.run/UserServiceApplication.run.xml", "apps/server/README.md", "apps/server/TOOLCHAIN.md", "development/works/WORK-021"]
forbidden_paths: ["apps/server/pom.xml", "apps/server/logging-support", "apps/server/user-service", "apps/server/gateway-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine", "contracts", "docs"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# TASK-029：修复 IDEA 错误按叶子工程构建 user-service

## 任务目标

提供仓库共享的 IDEA 用户服务启动配置，并修正 Java 后端构建说明，使 IDEA 启动使用根工程模块图、
Maven 局部构建使用根 reactor 的 `-pl user-service -am`，消除叶子 POM 无法解析 `logging-support` 的
误用路径。

## 依据

落实 ISSUE-004 的 AC-001～AC-005，只依据经人工确认的 DESIGN-017。若实现需要改变 Maven 模块、
`logging-support` 依赖、服务源码或运行环境，必须停止并升级设计，不能自行扩大任务。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `apps/server/.run/UserServiceApplication.run.xml`：绑定 `user-service` 模块和正确 Spring Boot 主类，
  启动前执行 IDEA `Make`。
- `apps/server/README.md`：补充从根 POM 导入 IDEA、使用共享启动项，以及根 reactor 局部构建命令。
- `apps/server/TOOLCHAIN.md`：澄清根聚合、`-pl/-am` 与叶子 Wrapper 的能力边界。
- `VERIFY-021`：记录静态检查、Maven 局部/全量构建和 IDEA 人工启动结果。

## 完成标准

- [ ] 共享 IDEA 配置的模块名、主类和 `Make` 步骤与现有工程一致。
- [ ] 文档不再把叶子 Wrapper 描述成可独立解析仓库内兄弟模块的入口。
- [ ] 根目录 `-pl user-service -am` 构建在无预装 `logging-support` 前提下成功选择正确 reactor 顺序。
- [ ] 全量 Maven 验证无回归，POM、服务源码、共享日志源码和公共契约没有修改。
- [ ] IDEA 重新导入根 POM 后，用户服务启动不再出现 `logging-support` artifact missing。

## 验证

在 `apps/server` 执行并记录：

```text
./mvnw -pl user-service -am clean package -DskipTests
./mvnw clean verify
```

检查第一条的 reactor 顺序包含 `server`、`logging-support`、`user-service` 且构建成功；第二条验证全部
后端模块。解析新增 XML，核对模块/主类存在；检查 `git diff --check` 和改动路径。最后在 IDEA 重新
加载 `apps/server/pom.xml`，选择共享 `UserServiceApplication` 启动，确认不再执行用户日志中的叶子
Maven `package`，也不再报告缺失 `logging-support`。运行基础设施导致的后续失败需单独记录。

## 风险

主要风险是共享配置在当前 IDEA 工程模型中找不到模块，或说明仍让开发者从叶子目录执行 Maven。实际
导入/启动检查必须覆盖这两点。若只能通过 `mvn install`、改 POM、删除共享依赖或修改业务源码解决，
停止任务并重审 DESIGN-017。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：根据用户提供的 Maven 日志与仓库模块结构补全读写边界、产出和验收，等待人工批准。
