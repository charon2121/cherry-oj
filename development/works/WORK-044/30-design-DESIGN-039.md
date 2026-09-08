---
id: "DESIGN-039"
type: "design"
title: "自定义运行开关收敛至Gateway"
status: "checked"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["DESIGN-038"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# DESIGN-039：自定义运行开关收敛至Gateway

## 背景

用户本地重启后仍无法运行，需要在三个服务同时设置同一个开关。只读检查确认三处 enabled 判断均表达相同的功能开放意图，形成重复配置和漏配故障。本设计是 DESIGN-038 的配置职责补充；用户能力仍遵循 FEATURE-012，不改变自测执行语义。

## 目标与限制

只在 Gateway 设置一次 CHERRY_CUSTOM_RUN_ENABLED 即可控制公开运行入口。保留原变量名和默认false，避免把“减少配置点”悄悄变成“默认开放”。不新增配置中心、跨服务查询开关接口、管理页面或动态热更新；不改 sandbox，也不重新启动其内存统计修复。

## 整体方案

Gateway 作为浏览器唯一公开入口，独占功能开放决策。submission-service 负责题目快照/版本校验与调用编排；judging-service 负责执行配置/节点检查与执行资源约束。下游不重复判断同一个产品开关。

## 模块与数据

- Gateway 保留现有属性、关闭提示与控制器分支。关闭时在触发下游请求和Redis准入之前拒绝。
- submission-service、judging-service 删除 application.yaml 的 custom-run.enabled、构造器 enabled 参数/字段及禁用分支，更新受影响测试。
- 保留两级鉴权、Gateway CSRF/身份前置条件/Redis准入、judging本地并发槽位、请求/响应容量及deadline。删除开关不等于删除下游安全边界。
- 无数据库、Kafka、公开API或Go契约变更；README补充一个Gateway配置点及本地启动方式。

## 接口与状态

Gateway开关false返回既有503/CUSTOM_RUN_DISABLED；true时进入既有执行链。下游不可用、凭据错误、环境未就绪继续返回相应错误，不伪装成开关关闭。下游不再受该环境变量影响，旧变量可从其启动配置删除。

## 安全与失败

公开入口关闭只阻止后续浏览器请求，不取消在途程序，也不定义为所有受信内部调用的全局紧急停止。submission的内部JWT验证、judging的submission-service凭据验证继续生效；不增加可绕过Gateway的公开入口。若未来需要全局调度暂停，另行设计控制面，不能用三个静态bool实现分布式一致性。

## 监控与部署

本地仅在gateway-service运行配置中设置 CHERRY_CUSTOM_RUN_ENABLED=true。多Gateway副本由同一部署配置提供该值，仍为启动时配置，不承诺热更新。部署新版下游后，只需配置并重启Gateway来切换开放状态；本轮文档回合不操作当前进程。

## 迁移与兼容

保留Gateway变量名和默认值。升级下游会移除旧布尔条件；先部署下游兼容版本，再启用Gateway，避免混跑旧下游仍因false拒绝。初次替换二进制仍需重启更新过的服务，此后开关变更只涉及Gateway。回退时保留已去重的下游，关闭Gateway入口即可；若回退旧下游二进制，要恢复其旧开关配置，不能声称混版本无差异。

## 备选方案

统一 .env 注入三个服务只能减少输入次数，仍保留三个判断点和配置漂移。配置中心/共享开关查询能统一动态状态，但增加依赖、可用性和缓存一致性问题，不适合当前单一公开入口的MVP。直接默认全部开启会改变开放策略，不作为这次重构内容。

## 风险与重审条件

最大风险是把删除布尔条件误做成删除鉴权或资源限制，需实际安全链回归。真实环境仍须检查已部署的下游版本。出现多个公开入口、运维动态停机或跨副本实时一致性需求时重新评估。已记录的sandbox内存统计限制按用户决定延期。

## 变更记录

- 2026-09-08：状态变更：draft → review。原因：Gateway单点开关补充设计完成，提交用户审核
- 2026-09-08：状态变更：review → checked。原因：用户已确认方案；记录类设计结构校验通过，不代签决定文档
