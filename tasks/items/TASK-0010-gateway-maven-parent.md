---
id: "TASK-0010"
title: "修复 Gateway Maven 父继承与依赖解析"
type: "bug"
area: "server/gateway"
priority: "P0"
status: "done"
assignee: "codex/root"
depends_on: ["TASK-0008"]
related: ["TASK-0009"]
created_at: "2026-08-20T14:47:28+08:00"
updated_at: "2026-08-20T15:59:42+08:00"
claim_branch: "codex/task-0010"
claimed_at: "2026-08-20T14:48:20+08:00"
lease_until: "2026-08-21T14:48:20+08:00"
completed_at: "2026-08-20T14:50:20+08:00"
review_required: false
---

# TASK-0010：修复 Gateway Maven 父继承与依赖解析

## 背景

用户开始按 M3 教程初始化 Java 工程后，IDEA 报告
`org.springframework.cloud:spring-cloud-starter-gateway-server-webflux:${project.version} not found`。
根 POM 的坐标是 `com.cherryoj:server:0.0.1-SNAPSHOT`，Gateway 子 POM 却引用了不存在的
`com.cherryoj:cherry-oj-server:com.cherryoj:cherry-oj-server:0.1.0-SNAPSHOT`。父工程无法解析时，
Gateway 也就无法继承根 POM 导入的 Spring Cloud BOM，IDEA 因而不能得到 starter 的正式版本。

## 目标

让 gateway-service 正确继承根 Maven 父工程，并由 Spring Cloud 2025.1.2 BOM 解析
`spring-cloud-starter-gateway-server-webflux` 的版本，不在子 POM 手写 Spring Cloud 组件版本。

## 范围

包含：

- 将 Gateway 的 parent groupId、artifactId、version 与根 POM 完全对齐。
- 保留 `relativePath` 指向根 POM，并验证 Maven 模型与 Gateway 依赖可解析。

不包含：

- 修改根 POM 坐标或 Spring Boot/Spring Cloud 版本。
- 添加其他四个 Java 服务、Gateway 路由或业务代码。
- 修改 IDEA 私有配置和用户正在进行的其他 `apps/server` 文件。

## 验收标准

- [x] Gateway parent 坐标等于根 POM 的 `com.cherryoj:server:0.0.1-SNAPSHOT`。
- [x] Gateway POM 不显式声明 Spring Cloud Gateway 版本，版本继续由父 POM 的 BOM 管理。
- [x] Maven `validate` 和 Gateway `dependency:tree` 成功，依赖树中不存在 `${project.version}` 占位符。

## 执行记录

- 2026-08-20T14:47:28+08:00：创建任务。
- 2026-08-20：将 Gateway parent 从错误的 `com.cherryoj:cherry-oj-server` 及非法复合 version
  修正为根 POM 的 `com.cherryoj:server:0.0.1-SNAPSHOT`。
- 2026-08-20：`./apps/server/mvnw -f apps/server/pom.xml validate` 成功；Gateway 依赖树将
  `spring-cloud-starter-gateway-server-webflux` 解析为 `5.0.2`，未出现 `${project.version}`。
- 2026-08-20T14:48:20+08:00：codex/root 在分支 codex/task-0010 认领任务，租约 24 小时。
- 2026-08-20T14:50:20+08:00：验收完成，任务关闭。
- 2026-08-20：按用户指示将完整 Java Maven 五服务骨架纳入 TASK-0010 分支；修正父 POM 的重复
  `user-service` module 为 `submission-service`，并在 Java 21 下通过聚合 `clean verify`。

## 阻塞信息

无。

## 完成结果

Gateway 已能正确继承根 POM 及其 Spring Cloud 2025.1.2 BOM，starter 版本解析恢复正常。
根据用户后续的分支整合指示，`apps/server` 完整五服务 Maven 骨架与根 `.gitignore` 调整一并提交，
没有提交 `.idea`、`target` 或生成的 `HELP.md`。Maven `validate` 成功，`dependency:tree` 将 WebFlux
Gateway starter 解析为 5.0.2；Java 21 下的聚合 `clean verify` 全部通过。
