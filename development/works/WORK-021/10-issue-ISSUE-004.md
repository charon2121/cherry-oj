---
id: "ISSUE-004"
type: "issue"
title: "修复 IDEA 错误按叶子工程构建 user-service"
status: "review"
work: "WORK-021"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---


# ISSUE-004：修复 IDEA 错误按叶子工程构建 user-service

## 问题现象

在 IDEA 中对 `apps/server/user-service/pom.xml` 执行 Maven `package` 时，构建在扫描完
`user-service` 后立即失败，提示无法解析：

```text
com.cherryoj:logging-support:jar:0.0.1-SNAPSHOT
```

因此开发者无法继续编译或启动 `UserServiceApplication`。

## 复现方式

1. 在没有把 `logging-support` 预先 `install` 到本地 Maven 仓库的环境中打开项目。
2. 让 IDEA 以 `apps/server/user-service` 作为 `maven.multiModuleProjectDirectory`。
3. 对该叶子 POM 执行 `package`。

用户提供的日志已经包含上述入口与稳定错误；等价命令是从 `apps/server/user-service` 单独构建该 POM，
且不让根聚合工程选择 `logging-support`。

## 实际结果

Maven 当前 reactor 只有 `user-service`。它读取到对 `logging-support:0.0.1-SNAPSHOT` 的普通依赖后，
只能查询本地或远程仓库；兄弟目录虽然存在，但不在本次 reactor 中，因此依赖解析失败。失败发生在
编译、测试和 Spring Boot 启动之前。

## 预期结果

IDEA 应从 `apps/server/pom.xml` 导入完整后端工程，并通过仓库共享的用户服务启动项使用 IDE 模块依赖
图完成编译和启动。需要执行 Maven 生命周期时，应从 `apps/server` 根工程使用
`./mvnw -pl user-service -am package`，其中 `-am` 会同时选择用户服务依赖的仓库内模块。

## 影响与条件

只要某个服务依赖未发布到 Maven 仓库的仓库内共享模块，并且 IDE/命令把服务叶子 POM 当作独立工程，
同类错误就会发生。当前五个 Java 服务都依赖 `logging-support`；本次先修复用户报告的 IDEA 启动入口，
同时在通用说明中澄清所有服务都必须从根聚合工程构建。

## 原因

`logging-support` 已由 `apps/server/pom.xml` 声明为 reactor 模块，`user-service/pom.xml` 也正确声明了
依赖。错误命令中的 `maven.multiModuleProjectDirectory` 却是 `apps/server/user-service`，所以 Maven
没有加载根 POM 的 `<modules>`，也没有构建兄弟模块。子模块自带 Wrapper 以及旧说明中的“模块独立
场景”表述进一步造成了叶子 POM 可以独立解析仓库内依赖的误解。

## 修复方向

1. 新增可提交的 IDEA `UserServiceApplication` Spring Boot 运行配置，绑定 Maven 根工程导入出的
   `user-service` 模块和正确主类，启动前使用 IDEA `Make` 编译模块依赖。
2. 在 README 中增加 IDEA 导入/启动步骤，并把单模块 Maven 构建统一写为从 `apps/server` 根目录执行
   `-pl <service> -am ...`。
3. 在 TOOLCHAIN 中明确叶子 Wrapper/POM 不构成独立发布边界，不能依靠本地 `install` 缓存掩盖缺失的
   reactor 模块。
4. 不修改 `logging-support`、服务源码或依赖关系；若共享运行配置无法在根工程导入后工作，停止并重审
   方案，而不是删除共享依赖。

## 回归检查

- AC-001：仓库包含可解析的共享 `UserServiceApplication` IDEA 配置，模块名和主类与源码一致。
- AC-002：从 `apps/server` 执行 `./mvnw -pl user-service -am clean package -DskipTests` 时，reactor 包含
  `logging-support` 和 `user-service`，且依赖解析、编译与打包成功。
- AC-003：IDEA 重新导入 `apps/server/pom.xml` 后，共享启动项不调用叶子 POM 的 Maven `package`，能够
  进入 Spring Boot 启动流程；数据库等运行环境缺失可作为后续独立错误，但不得再出现缺失
  `logging-support`。
- AC-004：`cd apps/server && ./mvnw clean verify` 保持通过，五个服务的日志依赖和交付包不变。
- AC-005：README/TOOLCHAIN 不再建议使用叶子 Wrapper 独立构建含仓库内兄弟依赖的服务，并给出根工程
  的 `-pl ... -am` 命令。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：已根据用户提供的 Maven 日志确认叶子 POM 未加载 sibling reactor 的根因，提交问题定义审核
