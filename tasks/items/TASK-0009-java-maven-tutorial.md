---
id: "TASK-0009"
title: "编写 Java Maven 五服务初始化教程"
type: "docs"
area: "tutorial/server"
priority: "P0"
status: "review"
assignee: "codex/root"
depends_on: ["TASK-0008"]
related: []
created_at: "2026-08-20T11:09:55+08:00"
updated_at: "2026-08-20T14:42:35+08:00"
claim_branch: "codex/task-0009"
claimed_at: "2026-08-20T11:10:43+08:00"
lease_until: "2026-08-21T11:10:43+08:00"
completed_at: null
review_required: true
---

# TASK-0009：编写 Java Maven 五服务初始化教程

## 背景

contracts v2 已验收，下一步才轮到 Java 业务后端。用户希望亲自初始化工程，因此本任务不代写
`apps/server`，而是提供一篇能逐步执行、每一步都有检查点的 M3 教程。若只给一个最终 POM，容易在
JDK 版本、聚合/继承关系、Gateway 响应式栈和普通服务 MVC 栈上走偏，也无法判断某一步何时已经完成。

## 目标

编写 `tutorial/06-m3-java-maven-skeleton.md`，让用户从当前空的 `apps/server` 出发，手工得到可由
Maven Wrapper 一次构建、也可单独启动的五服务 Spring Boot 聚合工程，同时不提前引入数据库、Kafka
或跨服务业务代码。

## 范围

包含：

- JDK 21 LTS、Maven、IntelliJ IDEA 和项目工作区的前置检查与版本统一。
- 父 POM、Spring Boot/Spring Cloud BOM、Maven Wrapper 与五个 module 的创建顺序。
- gateway-service 的 WebFlux/Gateway 依赖和四个普通服务的 Spring MVC 依赖。
- 每个服务的最小启动类、端口、Actuator 健康检查和上下文测试。
- 聚合构建、单模块构建、逐服务启动和失败定位命令。
- 解释为何本阶段不创建 shared 业务模块、不接数据库/Kafka、不生成 contracts DTO。
- 更新 `tutorial/README.md` 阶段地图和下一篇入口。

不包含：

- 实际创建或修改 `apps/server` 下的 Maven/Java 文件。
- 数据库 migration、MyBatis、Flyway、Kafka、Redis、Security 或业务 API 实现。
- 从 JSON Schema 生成 Java DTO，或实现五个服务之间的 HTTP/Kafka 调用。
- 创建阶段分支、Tag、PR 或发布制品。

## 验收标准

- [x] 教程头部明确 M3 阶段、任务分支规则、依赖 M2/contracts v2 和本章产物。
- [x] 所有命令都以仓库根目录为基准，并明确命令执行后的期望输出或文件树。
- [x] 父 POM 与五个子 POM 给出可复制的完整文件，版本只有一个管理位置。
- [x] Gateway 明确只使用 WebFlux；user/problem/submission/judging 明确只使用 Spring MVC。
- [x] 教程提供最小 Java 类、配置和测试，并保留后续业务实现的 TODO 而不提前填答案。
- [x] 验收覆盖 JDK 21、IDEA/Maven JDK 统一、Maven Wrapper、聚合构建、单模块测试和五个健康检查。
- [x] 常见坑覆盖本机 Java 22、模块路径、parent.relativePath、端口占用和 Web MVC/WebFlux 混装。
- [x] `tutorial/README.md` 已加入 M3，并且 Markdown 代码围栏成对。

## 执行记录

- 2026-08-20T11:09:55+08:00：创建任务。
- 2026-08-20T11:10:43+08:00：codex/root 在分支 codex/task-0009 认领任务，租约 24 小时。
- 2026-08-20：新增 `tutorial/06-m3-java-maven-skeleton.md`，并将 M3 加入教程阶段地图。
- 2026-08-20：核对 Spring 官方兼容矩阵，确认 Boot 4.1.0 + Cloud 2025.1.2 支持组合；采用
  Boot 4.1 推荐的 `spring-boot-starter-webmvc` 与 Gateway WebFlux 专用 starter。
- 2026-08-20：验证 102 个 Markdown 围栏成对、6 份完整 XML 和 5 份 YAML 均可解析，且本地链接存在；
  `git diff --check` 无格式错误。教程是执行说明，未修改 `apps/server`，实际 Maven 构建与健康检查由执行者
  按教程完成。
- 2026-08-20T11:20:14+08:00：实现与验证完成，进入 review。
- 2026-08-20T14:40:56+08:00：根据审核反馈返回 in_progress；当前 IntelliJ IDEA 不支持 Java 25，
  Java 基线改为 IDE 可用且受 Spring Boot 4.1 支持的 Java 21 LTS。
- 2026-08-20：同步更新 `CLAUDE.md`、`docs/backend.md` 与 M3 教程；复查 102 个 Markdown 围栏、
  6 份完整 XML、5 份 YAML 和本地链接，`scripts/task check` 与 `git diff --check` 均通过。
- 2026-08-20T14:42:35+08:00：实现与验证完成，进入 review。

## 阻塞信息

无。

## 完成结果

已完成 Java Maven 五服务初始化教程，并根据审核反馈将 Java 基线从 25 调整为 21 LTS。教程覆盖
终端、Maven 与 IDEA 的 JDK 统一、父子 POM、Maven Wrapper、Gateway WebFlux、四个 MVC 服务、最小
启动类/配置/测试、依赖树边界、构建与健康检查，并明确推迟数据库、Kafka、Redis、Security、业务 API
和 contracts DTO 实现。已核对 Boot 4.1.0/Cloud 2025.1.2/Java 21 兼容性；102 个 Markdown 围栏、
6 份完整 XML、5 份 YAML、本地链接与任务状态检查均通过。`apps/server` 是用户正在创建的内容，
本任务没有修改；实际 Maven 构建与健康检查由用户按教程执行。
