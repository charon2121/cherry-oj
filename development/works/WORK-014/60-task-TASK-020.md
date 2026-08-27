---
id: "TASK-020"
type: "task"
title: "统一登录空闲过期配置并修复提前掉线"
status: "done"
work: "WORK-014"
owners: ["codex/root"]
depends_on: ["ISSUE-002", "DESIGN-011", "DECISION-010", "PLAN-011"]
related: []
implements: ["ISSUE-002#AC-001", "ISSUE-002#AC-002", "ISSUE-002#AC-003", "ISSUE-002#AC-004", "ISSUE-002#AC-005", "ISSUE-002#AC-006", "ISSUE-002#AC-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "apps/server/gateway-service", "apps/server/user-service", "development/works/WORK-013", "development/works/WORK-014"]
write_paths: ["apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/auth", "apps/server/gateway-service/src/main/resources/application.yaml", "apps/server/gateway-service/src/test", "apps/server/user-service/src/main/java/com/cherryoj/userservice/api", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config", "apps/server/user-service/src/main/java/com/cherryoj/userservice/persistence", "apps/server/user-service/src/main/resources/application.yaml", "apps/server/user-service/src/main/resources/mapper", "apps/server/user-service/src/test", "development/works/WORK-014"]
forbidden_paths: ["contracts", "apps/web", "apps/server/user-service/src/main/resources/db", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "compose.yaml", "docs/product.md"]
created_at: "2026-08-27"
updated_at: "2026-08-27"
---




# TASK-020：统一登录空闲过期配置并修复提前掉线

## 任务目标

实现 IDLE 秒数、绝对上限秒数与认证活动刷新布尔配置，修正 Gateway Redis Session 硬编码，补齐
Gateway/user-service 时间边界、true/false 行为和 token 刷新回归，并记录实际验证结果。

## 依据

ISSUE-002 AC-001～AC-007、DESIGN-011、待确认的 DECISION-010 与 PLAN-011。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

两个服务的显式秒数/布尔 application 配置、Gateway 属性与 reactive Redis repository customizer、
user-service 条件 touch、相关单元/真实 Redis/MySQL 测试，以及 VERIFY-014/MEMORY-011。无 migration。

## 完成标准

- [x] 三项配置缺省为 1800、43200、true，自定义值在两层实际生效。
- [x] Gateway 不再含决定登录期限的 1800/43200 硬编码，非法整数秒或布尔配置启动失败。
- [x] 空闲/绝对边界、认证活动在 true 时续期、false 时不续期、JWT 120 秒透明刷新均有自动化证据。
- [x] 退出、改密、重置、停用、401/503 与敏感字段回归保持通过。
- [x] Gateway、user-service、Java 聚合、WORK 文档与 diff 检查通过，未修改禁止路径。

## 验证

运行 `./mvnw -pl gateway-service -am clean verify`、`./mvnw -pl user-service -am clean verify` 和聚合
`./mvnw clean verify`；测试使用固定时钟、Testcontainers Redis/MySQL 和整数秒测试配置，不真实等待，
不依赖真实用户数据。最后运行 `scripts/work check`、`git diff --check` 和配置/硬编码全文扫描。

## 风险

若 Spring Session customizer 无法覆盖每个已认证 Session 的实际 TTL、条件 touch 需要修改公开接口，
或发现短时掉线并非期限分散而是 Cookie/Redis 故障，先升级 ISSUE/DESIGN 并重新审核，不扩大 TASK。

## 执行记录

- 2026-08-27：创建任务。
- 2026-08-27：负责人要求 IDLE/绝对上限使用秒配置，并增加认证操作是否刷新 IDLE 的布尔配置；TASK
  范围已更新，等待完整方案批准和执行授权。
- 2026-08-27：状态变更：todo → ready。原因：上游 ISSUE/DESIGN/DECISION/PLAN 已人工批准，读写与禁止路径完整
- 2026-08-27：状态变更：ready → doing。原因：开始实现统一秒数配置、刷新布尔开关和双服务时间边界测试
- 2026-08-27：执行上下文复核发现 write_paths 漏列已批准 DESIGN-011 所需的 user-service 内部
  API/application/persistence/mapper；仅校正路径边界，不增加公开接口、数据库或功能范围。
- 2026-08-27：两个服务新增统一秒数/布尔配置与严格范围校验；Gateway Redis Session 删除注解硬编码，
  user-service 增加登录授权 touch，true 时滑动、false 时固定，并通过返回配置校验阻止双层漂移。
- 2026-08-27：Gateway 34 项、user-service 18 项模块测试通过，包含真实 Redis/MySQL；Java 七模块聚合
  验证通过。补充 IDLE 等于截止点、绝对期限封顶、非法上下界和配置不一致的负向测试。
- 2026-08-27：状态变更：doing → done。原因：三项配置、双层期限同步、true/false 条件续期、配置漂移保护及 Redis/MySQL/聚合回归均已完成
