---
id: "VERIFY-058"
type: "verify"
title: "修复认证数据库回归的时钟夹具漂移"
status: "draft"
work: "WORK-057"
owners: ["team/server"]
depends_on: ["TASK-124"]
related: []
implements: []
verifies: ["ISSUE-019#AC-001", "ISSUE-019#AC-002", "ISSUE-019#AC-003", "TASK-124"]
tags: []
result: "pending"
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# VERIFY-058：修复认证数据库回归的时钟夹具漂移

## 验证对象

TASK-124 的认证测试时钟夹具修复，不变更 WORK-053 生产精度逻辑。

## 对应要求

AC-001 四组纳秒输入及会话断言；AC-002 两次真实 Linux 数据库零跳过；AC-003 路径、限额与完整业务结果的区分。

## 检查与结果

2026-09-12 只读定位：UserPersistenceIntegrationTests 先使用注入的系统时钟创建账号，再硬编码旧日期认证；CoreConfig、UserAdministrationService、AuthenticationService、mapper 与 V1 约束构成完整源码链。已有 CI34694917085 日志为 8 项、1 error，ck_user_account_time_order 违反。证据 `/private/tmp/cherry-work056-34694917085-business-build/business-build/authentication-tests.log`。

实施后本地编译及认证单测已通过，真实数据库测试尚未执行；见末节。

## 未通过项

现有失败保留；实施后数据库验证待执行。

## 范围检查

本轮仅修改指定 UserPersistenceIntegrationTests.java 与 WORK-057 记录。生产代码、工作流、包锁、数据库约束及预算未改。

## 遗留问题

WORK-050 的 business.io 与历史内核采样问题、WORK-056 的固定资产留存不由本工作关闭。

## 剩余风险

源码定位明确，但修复仍需真实 MySQL 实测；完整 CI 可能在后续场景继续发现失败。

## 结论

意图闸已签署；本地实现与部分验证完成，真实MySQL两台VM验证未完成，result pending，不能签署验收闸。

## 实施与本地验证（2026-09-12）

命令（apps/server）：`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home MAVEN_OPTS=-Xmx768m ./mvnw -o -B -ntp -pl user-service -am test -Dtest=AuthenticationServiceTests -DargLine=-Xmx512m -Dsurefire.failIfNoSpecifiedTests=false`。
结果 BUILD SUCCESS，4 tests、0 failures、0 errors、0 skipped，3.958秒；指定数据库集成测试同时完成编译但未执行。日志 `/private/tmp/cherry-work057-authentication.log`。

差异自查：仅移除硬编码日历时刻，改从实际持久化createdAt推导认证基准；四组输入及原期限等值、不延长、到期前1微秒、到期和撤销断言保持。增补账号时间顺序断言，未直接插入会话、模拟数据库或绕过真实authenticate。未委派子智能体复核，不将自查记为独立复核。

本批尚未提交推送，AC-001/002需要现有GitHub完整CI在两台新Linux VM执行；原失败34694917085继续保留。AC-003代码边界检查已通过，但完整业务结果未产生。

- 2026-09-12：用户明确授权本批提交推送 origin/main 及两台新 Linux VM 的完整 CI 验证；现开始发布验证。
