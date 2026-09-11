---
id: "VERIFY-054"
type: "verify"
title: "修复登录会话期限在数据库往返后的精度不一致"
status: "draft"
work: "WORK-053"
owners: ["codex/root"]
depends_on: ["TASK-117"]
related: []
implements: []
verifies: ["ISSUE-017#AC-001", "ISSUE-017#AC-002", "ISSUE-017#AC-003", "ISSUE-017#AC-004", "TASK-117"]
tags: []
result: "pending"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-054：期限精度修复验证记录

## 验证对象

ISSUE-017的AC-001至AC-004。当前仅源码归因和WORK-050实测失败，无生产修复结果。

## 检查与结果

CI34563521786登录成功后改密503；五服务和新节点正常，全部清理通过。独立只读复核确认纳秒签发、DATETIME(6)回读与网关equals之间存在缺陷机制。现有单测用整秒时钟、数据库测试预先截断微秒、网关集成模拟身份返回，未覆盖实际组合。

## 未通过项

真实MySQL确定性旧红新绿、原认证回归、零skip检查、完整Linux业务链和修复后独立复核均未执行。诊断轮34564019849的错误类别与清理证据已归档WORK-050/VERIFY-051。

## 范围检查

本工作未改Java、数据库、用户配置或部署。旧纳秒会话不自动修复；当前CI具体503仍需结合错误分类确认。

## 结论

pending，未达到验收条件。

## 对应要求

AC-001确定性旧红新绿，AC-002认证回归，AC-003真实业务，AC-004独立复核与人工验收；均待实施。

## 遗留问题

诊断CI34564019849再次在改密503，固定分类IDENTITY_CONFIGURATION_MISMATCH，全部清理通过；仍未采集原始期限差值。

## 剩余风险

精度候选尚无确定性数据库实验和修复验证，不能声明根因和修复已完成。

## TASK-117复现批次本地证据（2026-09-11）

已核验用户意图闸passed并明确允许实施，TASK-117经工具ready→doing。当前只修改两份指定测试与CI准备/结果校验，不修改AuthenticationService生产代码。

本地Darwin/arm64，Homebrew OpenJDK21，MavenWrapper离线缓存。从apps/server执行`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -o -B -ntp -pl user-service -am test -Dtest=AuthenticationServiceTests -Dsurefire.failIfNoSpecifiedTests=false`：首次默认沙箱因Mockito无法附加JVM而4ERROR，不作为缺陷证据。允许测试JVM附加后的同命令运行见/private/tmp/cherry-work053-unit-old-2.log：4项执行、1FAIL/0ERROR/0SKIP，新增期限测试在input fraction ns=1时返回纳秒尾数1，预期可精确持久化的微秒值；原3项通过。这证明本地签发精度缺陷，尚不等于MySQL往返复现。

新增数据库测试不预先截断Clock、不直接insert会话：正常创建测试账号、真实authenticate入库后validate及事务内exchange，断言完整期限相等，再验证到期和撤销。TokenService只替代JWT签名，与数据库期限持久化无关。输入覆盖123456789ns、999999999ns、整微秒、整秒。`./mvnw -o -B -ntp -pl user-service -am test-compile`使用同JDK21通过，编译12份测试；见/private/tmp/cherry-work053-compile.log。未在本机启动Docker或数据库，真实MySQL旧红新绿仍未取得。

CI改为先清除选中模块的旧构建再执行两类必需测试，固定8方法，拒绝缺失/重复/错误类/不一致计数/失败/skip/符号或硬链接/超限/XML实体；失败仅保存固定名称和状态，不泄漏报告正文。Maven本身失败即使报告全绿仍必须失败。独立复核与本工作提交推送尚未授权或执行；下一步先发布测试批次取得Linux旧红，再做已批准最小生产修复。

最终本地`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work053-basic-final`通过81项（15安装+6rootfs+60CI），包含新增5项认证报告反例；Python AST、shell语法和报告验证通过。开发流程刷新首次指出rollback检查未记录，按PLAN-037既有回退事实补记后再刷新，不改工具规则或人工闸。当前源码类未改，测试批次等待本工作独立复核/发布授权。

## 真实MySQL旧红及最小修复（2026-09-11）

用户已明确授权独立复核、复现及后续修复分批commit/push/CI。只读work053_review复核测试、真实事务、失败/skip校验及资源输出，无发布阻断。ef3312e0c9bcfeba42c20e98c4c57df8faa81729的[CI34569308782](https://github.com/charon2121/cherry-oj/actions/runs/34569308782)其余9job通过；业务准备认证测试8执行、6PASS/2FAILURE/0ERROR/0SKIP，未进入业务栈。MySQL8.4/JDK21.0.12.1实际启动并完成迁移；新数据库用例期望签发2026-10-11T12:00:00.123456789，validate回读2026-10-11T12:00:00.123457，相差+211ns。由断言FAILURE而非环境ERROR证明实际持久化精度不一致。

有界日志与摘要下载至/private/tmp/cherry-work053-old-34569308782/business-build，source SHA、harness 3fd2ebe7d71b9270746fc0e09397fb7e597c467db0956271cfb5a8ce3c306892及6PASS/2FAILURE复核通过。后续用例未被替换或预截断；旧轮数据库循环在首个纳秒值失败，秒末、到期及撤销分支仍需新绿。准备阶段Testcontainers依赖正常stop/Ryuk，当前没有逐容器零残留快照，不能用未启动业务栈的cleanup代称MySQL清理已实测。

取得旧红后，仅AuthenticationService生成absoluteExpiresAt处追加truncatedTo(MICROS)，保留同一值入库/返回；不更改Clock、JWT TTL、审计now、事务、网关或Schema。JDK21相同本地AuthenticationServiceTests命令4PASS/0FAIL/0ERROR/0SKIP，/private/tmp/cherry-work053-unit-fixed.log，形成同测试本地旧红新绿。work053_review再次独立复核生产差异与测试，无发布阻断；Linux8/8和真实改密仍待修复批次。
