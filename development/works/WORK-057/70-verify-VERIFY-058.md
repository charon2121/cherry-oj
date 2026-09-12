---
id: "VERIFY-058"
type: "verify"
title: "修复认证数据库回归的时钟夹具漂移"
status: "approved"
work: "WORK-057"
owners: ["team/server"]
depends_on: ["TASK-124"]
related: []
implements: []
verifies: ["ISSUE-019#AC-001", "ISSUE-019#AC-002", "ISSUE-019#AC-003", "TASK-124"]
tags: []
result: "pass"
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

本工作修复范围无未通过项。两轮完整业务CI仍在历史business.io失败，保留原失败和新结果。

## 范围检查

本轮仅修改指定 UserPersistenceIntegrationTests.java 与 WORK-057 记录。生产代码、工作流、包锁、数据库约束及预算未改。

## 遗留问题

WORK-050 的 business.io 与历史内核采样问题、WORK-056 的固定资产留存不由本工作关闭。

## 剩余风险

源码定位明确，但修复仍需真实 MySQL 实测；完整 CI 可能在后续场景继续发现失败。

## 结论

本工作修复、两台Linux VM的真实认证回归及范围检查已完成，结果pass，等待人工验收；不代表WORK-050完整业务CI通过。

## 实施与本地验证（2026-09-12）

命令（apps/server）：`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home MAVEN_OPTS=-Xmx768m ./mvnw -o -B -ntp -pl user-service -am test -Dtest=AuthenticationServiceTests -DargLine=-Xmx512m -Dsurefire.failIfNoSpecifiedTests=false`。
结果 BUILD SUCCESS，4 tests、0 failures、0 errors、0 skipped，3.958秒；指定数据库集成测试同时完成编译但未执行。日志 `/private/tmp/cherry-work057-authentication.log`。

差异自查：仅移除硬编码日历时刻，改从实际持久化createdAt推导认证基准；四组输入及原期限等值、不延长、到期前1微秒、到期和撤销断言保持。增补账号时间顺序断言，未直接插入会话、模拟数据库或绕过真实authenticate。未委派子智能体复核，不将自查记为独立复核。

本批尚未提交推送，AC-001/002需要现有GitHub完整CI在两台新Linux VM执行；原失败34694917085继续保留。AC-003代码边界检查已通过，但完整业务结果未产生。

- 2026-09-12：用户明确授权本批提交推送 origin/main 及两台新 Linux VM 的完整 CI 验证；现开始发布验证。

## 首轮 Linux 验证

已提交推送 `7c74d99770ca66edc617e55e037816b8d76fe849`。CI34695556153 attempt1 中真实认证组8/8 PASS、0 skip，四组纳秒输入的数据库用例实际通过。authentication-tests.json 与业务报告的 sourceSha 一致，harness 为 `3cd7768c4447b1300044cc51f081c81585a1b9cfc693a5f0e8f719bb292a6fc3`；本地调用 verify_authentication_tests、validate(successful=False)、verify_files 复验通过。

后续业务报告为注册/部署/校准3PASS、business.io 1FAIL、11NOT_RUN，最终cleanup PASS。内核与原生部署job通过；不能将本工作认证通过说成全部CI通过。证据保存于 `/private/tmp/cherry-work057-attempt1-sandbox-business-build`、`/private/tmp/cherry-work057-attempt1-sandbox-business` 及 `/private/tmp/cherry-work057-attempt1-status.json`。首轮已保存后，依本批授权启动同SHA完整attempt2，未只重跑消费者。

## 第二轮与最终结论

同一提交 `7c74d99770ca66edc617e55e037816b8d76fe849`，完整 CI34695556153 attempt2 认证8/8 PASS、0 failures/errors/skips。认证日志结束时间分别为第一轮13:09:43Z、第二轮13:15:34Z；业务报告窗口分别13:10:35–13:12:19Z、13:16:39–13:18:47Z，确认不同轮次，不用旧产物冒充新运行。
第二轮业务构建/报告资产ID为10298886178、10298636679（创建于13:18:49Z、13:18:48Z），列表保存在 `/private/tmp/cherry-work057-artifacts.json`。本地两轮证据分目录保存，第二轮追加到 `/private/tmp/cherry-work057-attempt2-sandbox-business-build`、`/private/tmp/cherry-work057-attempt2-sandbox-business`；两次状态为同 run 的不同 run_attempt，分别保存在 attempt1/2-status.json。

两轮authentication结果通过verify_authentication_tests，业务报告通过validate(successful=False)及verify_files，身份一致。均新环境/部署/校准3PASS、business.io 1FAIL、11NOT_RUN，诊断均support.ts:45:24，最终cleanup PASS。这里最终清理报告通过不等于未执行的business.cleanup业务用例通过。真实数据库生命周期沿用Testcontainers，业务栈最终所有权清理已确认；不额外声称已新增独立容器残留探针。

AC-001/002：四组纳秒输入的真实数据库认证方法两轮全部执行，原期限/到期/撤销断言通过，原时间顺序错误消失。AC-003：仅指定测试文件及工作记录改变；生产逻辑、数据库约束、预算和包锁未改。差异自查完成，无独立子智能体复核授权或声明。本工作技术完成，结果pass，人工验收仍由用户签署。

[第一轮](https://github.com/charon2121/cherry-oj/actions/runs/34695556153/attempts/1)、[第二轮](https://github.com/charon2121/cherry-oj/actions/runs/34695556153/attempts/2) 的完整CI均failure；历史business.io不是本批时钟修复范围。WORK-050/WORK-056剩余工作继续保留。运行后证据记录先留本地，不在红色CI基础上追加纯文档提交。

## 变更记录

- 2026-09-12：状态变更：draft → review。原因：已记录两台Linux认证通过及完整业务失败边界，提交人工验收
- 2026-09-12：验收闸通过：review → approved。原因：确认认证测试时钟修复，两轮真实数据库回归通过
