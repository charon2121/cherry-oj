---
id: "CHANGE-011"
type: "change"
title: "统一本地judging服务启动入口"
status: "approved"
work: "WORK-045"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# CHANGE-011：统一本地judging服务启动入口

## 为什么做

开发者使用熟悉的启动入口重启后，服务仍缺少必要设置；另一个名称相近的入口却能加载这些设置。统一入口，让正常重启即可使用已有本地配置，避免重复启动和反复排错。

## 当前状态

共享 `apps/server/.run/JudgingServiceApplication.run.xml` 仅指定主类与模块；忽略的 `.idea/runConfigurations/Local_JudgingServiceApplication.xml` 包含正式判题开关、Kafka、双向服务凭据及 submission 地址。当前进程没有加载接收凭据，内部请求返回 401，经 submission 转为 503，后续请求在 Gateway 租约期内返回 429。对应 WORK-044 的验收排查记录。

## 当前问题

同一服务存在两套参数不同的启动入口；当前 workspace 中另有同名临时配置，需要一并核对，避免遮蔽共享入口。

## 目标状态

- REQ-001：保留 `JudgingServiceApplication` 唯一入口，固定以 apps/server 为工作目录，使用 Spring 的 `spring.config.additional-location` 加载 `.local/judging-service.properties`；补充配置不替代原 application.yaml。
- REQ-002：迁移已有 Local 配置中的五项参数到被 Git 忽略的本地文件，保持值与类型，保留已有未知配置，不覆盖冲突值。共享运行配置仅包含文件位置。提供不含真实凭据的示例与 README 操作说明。
- REQ-003：验证迁移后删除 Local judging 启动项及重复的同名临时项，保留唯一共享配置；不改其他服务的启动入口。

## 不变条件

- REQ-004：不改鉴权实现、服务凭据、业务代码、端口和功能开关职责，不关闭限流；自定义运行仍仅由 Gateway 控制。

## 影响范围

仅 judging 的 IDE 启动配置、私有本地文件、忽略规则及说明文档；不迁移数据库，不提交或推送。实现时先读取 TASK 边界。当前以 apps/server 为 IDEA 项目根，共享配置显式使用 `$PROJECT_DIR$` 作为工作目录。缺失本地文件时启动明确失败并说明复制示例，避免悄悄回退为空凭据。

## 风险

properties 特殊字符必须正确转义；迁移前备份相关本地配置到被忽略的目录，备份不得入库或输出凭据。IDE 已载入配置需刷新。验证启动需先确认 8084 空闲；已有用户进程不擅自终止，无法完成的启动验证如实记录。

## 回归检查

- AC-001：IDE 仅有一个 judging 入口，主类、模块、工作目录及配置加载路径正确。
- AC-002：五项本地参数迁移前后等值，双向凭据只在内存中比较；Git 忽略文件及备份，跟踪文件无真实凭据。
- AC-003：使用唯一入口启动后内部调用不再因缺失接收凭据被拒绝；正式判题开关及连接参数保持不变。若未执行真实启动，不宣称通过。
- AC-004：可用本地备份恢复原运行配置；无业务代码变更。

## 变更记录

- 2026-09-08：状态变更：draft → review。原因：统一入口及私有配置迁移方案已准备，提交人工审核
- 2026-09-08：意图闸通过：review → approved。原因：同意统一启动入口与本地配置迁移，允许实施

## 2026-09-08 用户授权的 submission 启动修复

用户明确要求使用其提供的本机 root 凭据修改 cherry_oj_submission 密码并写入 submission 配置。范围补充为该数据库账号的密码更新、被忽略的 submission 本地配置及同名共享启动入口；不改账号权限、不改其他数据库用户、不改业务代码。密码不得写入跟踪文件、日志或验证报告。先读取账号 Host，按本地 TCP 连接匹配账号操作，并验证该账号登录和访问自己的数据库。

## 自定义运行恢复的配套配置补充

17:11 首次运行在 submission 返回 503，未到 judging；只迁移数据库参数不足以恢复既有服务链路。按用户继续修复运行的要求，补齐 submission 调用 problem/judging 及接收 judging 的配套凭据，problem 接收端新增私有配置及唯一共享启动入口；保留既有 judging 凭据，缺失的 problem 配套凭据生成后仅保存于双方私有文件。不得降低鉴权、限流或修改业务实现。实际服务重启仍由用户操作；可以使用无持久化的内部调用验证既有服务。

## 重启仍未加载 judging 配置的修正

17:18 同一 trace 显示 problem/submission 配置已生效，judging 仍因启动参数缺失拒绝内部请求。磁盘共享配置正确，但不能仅依赖 IDE 是否使用该配置。增加 judging application.yaml 的 optional:file:./.local/judging-service.properties 导入，常用 apps/server 工作目录下直接运行主类也读取既有私有配置；已有共享入口的 mandatory additional-location 保留，因此该入口缺文件仍明确失败。无本地文件的其他运行环境保持既有配置行为。仅修改该 YAML，不改 Java 鉴权或业务实现。
