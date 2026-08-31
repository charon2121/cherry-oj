---
id: "DESIGN-017"
type: "design"
title: "修复 IDEA 错误按叶子工程构建 user-service"
status: "review"
work: "WORK-021"
owners: ["codex/root"]
depends_on: ["ISSUE-004"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---


# DESIGN-017：修复 IDEA 错误按叶子工程构建 user-service

## 背景

依据 ISSUE-004。后端是一个 Maven 多模块工程，`logging-support` 与五个可执行服务由
`apps/server/pom.xml` 聚合。用户日志证明 IDEA 当前只加载了 `user-service` 叶子工程，导致仓库内兄弟
依赖被误当成必须从 Maven 仓库下载的外部制品。

## 目标与限制

目标是在不改变服务职责、日志能力或发布结构的前提下，让 IDEA 有一个可共享、可重复的用户服务启动
入口，并让 Maven 的局部构建始终从根 reactor 选择依赖模块。

限制：不把 `logging-support` 发布到远程仓库，不要求开发者先执行本地 `install`，不复制共享源码，不
修改业务代码/公共契约/数据库配置，也不承诺在 MySQL、密钥等运行依赖缺失时完整启动成功。本工作只
消除构建模型导致的 `logging-support` 解析失败。

## 整体方案

保留现有 Maven 聚合与依赖声明，把“IDEA 启动”和“Maven 生命周期构建”分成两个明确入口：

```text
IDEA 导入 apps/server/pom.xml
  └─ 共享 UserServiceApplication 配置
       └─ IDEA Make(user-service + logging-support) → 启动主类

终端 / IDEA Maven 生命周期
  └─ apps/server/mvnw -pl user-service -am <phase>
       └─ Maven reactor(logging-support → user-service)
```

新增 `apps/server/.run/UserServiceApplication.run.xml`，沿用已有 Judging 服务共享配置的格式，绑定模块
`user-service`、主类 `com.cherryoj.userservice.UserServiceApplication` 和启动前 `Make`。文档要求把
`apps/server/pom.xml` 作为 Maven 根工程导入；若需要 `package/test/verify`，从根目录执行 `-pl` 并带
`-am`，不在叶子 POM 上启动 Maven。

## 模块与数据

`logging-support` 继续是普通 jar 模块，五个服务继续作为消费者；根 POM 继续负责聚合顺序。新增文件
只属于 IDEA 开发入口，README/TOOLCHAIN 解释通用构建规则。没有持久化数据、运行配置、网络、接口或
依赖版本变化。

## 接口与状态

不新增运行时接口或状态。IDEA 配置中的模块名必须与根 Maven 导入得到的模块一致，主类必须与源码
一致。Maven 局部构建的契约是：`-pl user-service` 选择目标，`-am` 把其 reactor 依赖一并加入。

## 安全与失败

不触及鉴权、密钥、数据库或网络边界。若 IDEA 没有导入根 POM，共享配置会暴露模块不可用，而不是
静默回退到本地 Maven 缓存。若 MySQL、Redis、密钥或端口缺失，启动可以在依赖解析之后以原有错误
失败；不得把这些独立问题伪装成此次构建修复未生效。

## 监控与部署

无生产部署变化。验证记录区分三层：XML/模块静态一致性、根 reactor 局部构建、IDEA 实际启动。自动
构建不能代替 IDEA 人工确认，但必须证明 `logging-support` 不依赖预装的本地快照。

## 迁移与兼容

现有命令仍兼容；根目录全量构建不变。文档会把叶子 Wrapper 从“可用于模块独立场景”降为不推荐且不
保证解析仓库内兄弟依赖，避免误导。共享运行配置是新增文件，可删除回退，不影响 CLI 或部署。

## 备选方案

- 先执行 `mvn install` 把 `logging-support` 放入个人本地仓库：能暂时解锁叶子构建，但会隐藏真实依赖
  图，源码变化后可能继续使用过期快照，新克隆也必须重复隐式引导步骤，因此不采用。
- 删除共享依赖或把日志代码复制回每个服务：会破坏已建立的统一日志边界并制造重复实现，不采用。
- 让每个叶子 POM聚合 `logging-support`：可执行服务必须保持 `jar` packaging，且兄弟模块聚合属于根
  POM 职责；复制 reactor 定义会造成结构漂移，不采用。
- 只写一段本机 IDEA 操作说明：无法随仓库共享，也不能为后续开发者提供一键入口；采用共享 `.run`
  配置加说明。

## 风险与重审条件

共享配置依赖 IDEA 的 Maven 根工程导入和稳定模块名；升级 IDEA 或改变模块命名后需同步更新。若未来
要求每个服务仓库外真正独立构建，应另建基建工作，为 `logging-support` 定义版本发布、仓库地址与
兼容策略，而不是继续扩充本次开发入口修复。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：已提出保留 Maven 聚合结构、使用共享 IDEA 启动项与根 reactor 命令的最小修复方案，提交技术审核
