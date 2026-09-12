---
id: "ISSUE-019"
type: "issue"
title: "修复认证数据库回归的时钟夹具漂移"
status: "approved"
work: "WORK-057"
owners: ["team/server"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# ISSUE-019：修复认证数据库回归的时钟夹具漂移

## 为什么做

一项自动测试把登录时间写死在过去，却按运行当天的时间创建账号。随着日期推进，它会制造“先登录、后创建账号”的不可能情况，阻断后续业务验证。需要让测试的时间前后一致，同时继续检查真实登录行为。

## 问题现象

CI 34694917085 的认证数据库回归报 MySQL `ck_user_account_time_order` 违反，错误码 3819；8 项测试中 1 error，业务页面场景未执行。软件下载、rootfs、内核和原生部署已通过，详见 WORK-056/VERIFY-057。

## 复现方式

在系统日期晚于 2026-09-11T12:00:00.123456789Z 的独立 Linux VM，运行现有 business_prepare.py 中必需认证测试组。UserPersistenceIntegrationTests 使用真实 users.createUser 创建账号，再以硬编码 2026-09-11 的 Clock.fixed 认证。同一提交的失败日志已存在，本轮不重跑旧失败刷证据。

## 实际结果

authenticatedDeadlineSurvivesMysqlRoundTripAtNanosecondPrecision 第 190 行在 recordLoginSuccess 更新 updated_at 时失败，尚未执行该用例的期限精度断言。数据库约束要求 updated_at >= created_at。

## 预期结果

承接 WORK-053/ISSUE-017 的真实 authenticate → MySQL → validate/exchange 期限精度验证。账号创建先于认证，四组纳秒输入及到期、撤销断言均必须实际执行。

- REQ-001：测试认证时间从账号入库后的 createdAt 推导，不再依赖固定日历日期；每轮认证时间向前推进。
- REQ-002：保留真实认证和持久化、四组纳秒输入、期限不延长、到期前与到期、撤销断言；保持 CI 必需方法名及零跳过规则。

## 影响与条件

仅一份用户服务集成测试夹具。WORK-053 曾在其当日通过，本次说明测试存在随日期变化失效的问题，不撤销已有生产期限精度修复。WORK-050 业务闭环仍需后续执行，WORK-056 固定资产任务仍保留。

## 原因

只读源码链：CoreConfig.utcClock 返回 Clock.systemUTC；UserAdministrationService 使用它写入创建时间；本测试随后调用 authenticationAt(Instant.parse("2026-09-11T12:00:00Z").plusNanos(inputNs))。AuthenticationService 使用被注入的固定时钟调用 accounts.recordLoginSuccess；mapper 将 now 写为 updated_at，因此较晚创建的账号违反 V1 中的时间顺序约束。这一机制与本次日志一致；未发现需要变更生产代码的证据。

## 修复方向

先经 accounts.findById 取得本次账号持久化的 createdAt，转 UTC 后向下取整到秒并加一秒作为基准；第 i 个纳秒样本使用基准加 i 秒再加原 inputNs。这样第一个样本严格晚于创建，之后逐轮递增，避免 999999999 纳秒经 MySQL 舍入后下一轮时间倒退。断言所构造时刻及回读更新时间不早于账号创建时间；不对输入纳秒预截断，不直接插入会话或绕过 authenticate。

只改测试文件。若实测仍涉及生产时间行为，停止并升级范围，不修改 Schema、SQL_MODE、认证服务或放宽断言。相比把硬编码日期改到未来，本方案不会在新日期再次失效；不通过禁用约束或重试掩盖问题。

## 回归检查

- AC-001：真实 MySQL 中四组输入 123456789、999999999、123456000、0 全部运行，无时间顺序错误，既有期限等值、到期和撤销断言通过。
- AC-002：CI 原必需认证组 8 项全部通过、0 skip，并保留原失败 run；至少两台新 Linux VM 运行认证组，记录 source/harness、计数与清理，不用后一次覆盖前一次失败。
- AC-003：差异仅测试夹具与工作记录；生产代码、数据库约束、既有预算、包锁及 CI 方法门禁不变。完整业务 CI 的结果独立记录，不能把认证通过当整链通过。

## 变更记录

- 2026-09-12：状态变更：draft → review。原因：只读定位混用系统时间和固定旧日期，提出仅改测试夹具的修复与验证边界
- 2026-09-12：意图闸通过：review → approved。原因：确认仅修复认证测试时钟夹具，允许实施
