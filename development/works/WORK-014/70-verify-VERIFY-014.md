---
id: "VERIFY-014"
type: "verify"
title: "统一登录空闲过期配置并修复提前掉线"
status: "approved"
work: "WORK-014"
owners: ["codex/root"]
depends_on: ["TASK-020"]
related: []
implements: []
verifies: ["ISSUE-002", "TASK-020"]
tags: []
result: "pass"
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# VERIFY-014：统一登录空闲过期配置并修复提前掉线

## 验证对象

ISSUE-002 AC-001～AC-007、Gateway Redis Session、user-service 登录授权、三个部署配置、内部 JWT 刷新
与 WORK-013 的撤销不变量。

## 对应要求

验证 ISSUE-002 和 TASK-020 的全部完成标准，重点证明“配置对象读到值”与“Redis/数据库实际期限采用该
值”一致，true/false 行为相反且均受绝对上限约束，并区分空闲到期、JWT 到期和上游故障。

## 检查与结果

验证日期为 2026-08-27。本地使用 OpenJDK 21.0.12.1、Docker Engine 29.7.2、Testcontainers Redis
7.4-alpine 与 MySQL 8.4：

- `cd apps/server && TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -pl gateway-service,user-service -am test`：
  Gateway 与 user-service 模块测试通过。最终 Gateway 34 项、user-service 18 项，两个模块合计 52 项。
  Redis 集成验证 Session ID 旋转、退出清理、Cookie 策略与 repository 缺省 IDLE；
  MySQL 集成验证条件更新在刷新开启时改变 `idle_expires_at`、关闭时保持原截止时间。
- `cd apps/server && TESTCONTAINERS_RYUK_DISABLED=true ./mvnw clean verify`：七个 Reactor 模块全部
  SUCCESS，共 60 项 Java 测试通过；其中包含新增的 IDLE 等号与绝对期限封顶边界。
- 固定时钟单元测试证明 `now == idleExpiresAt` 时立即清理 Session；刷新开启时新 IDLE 为
  `min(now + idleSeconds, absoluteExpiresAt)`，关闭时不调用 touch 且 Gateway 剩余 TTL 仍指向原固定
  截止点。120 秒 token 进入刷新窗口时只交换内部 JWT，不清除用户登录。
- 两个属性对象均验证自定义 7200/86400 秒、严格 `true | false`、IDLE 300～7200、绝对期限
  3600～604800 以及 IDLE 不大于绝对期限。Gateway 对 user-service 返回的三项配置逐项比对，不一致
  返回 503 且保留现有 Session，不以另一层值静默继续。
- 业务源码扫描未发现旧 `session-idle-timeout`/`session-absolute-timeout` 键、注解 1800 硬编码或旧环境
  变量名；`git diff --check` 无输出。

## 未通过项

仓库内无未通过项。生产变量注入、重启切换和真实用户等待观察未执行。

## 范围检查

逐项对照 TASK-020：修改限定在 Gateway auth/config/test、user-service 内部 auth/config/persistence/test
与 WORK-014 文档。未修改 contracts、Web、数据库 migration、其它资源服务、judge-engine、compose 或
产品文档。新增 `/internal/auth/touch` 仅为 Gateway 与 user-service 内部边界，不改变公开浏览器 API。

## 遗留问题

生产环境必须把三项变量以相同值同时注入 Gateway 与 user-service；本 WORK 不代替发布配置变更。

## 剩余风险

生产环境是否同时向两个进程注入同值，以及真实用户观察到的到期时长，仍需发布阶段证据。

## 结论

ISSUE-002 AC-001～AC-007 与 TASK-020 的仓库内实现、兼容、安全和真实 Redis/MySQL 回归通过。登录空闲、
绝对期限与 120 秒内部 JWT 保持三个独立计时器；生产发布和线上观察不在本结论内。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：真实 Redis/MySQL、模块与七模块聚合验证结果已记录，提交验证结论复核
- 2026-08-27：状态变更：review → approved。原因：ISSUE-002 AC-001 至 AC-007 与 TASK-020 仓库内验收全部通过，生产发布明确排除
