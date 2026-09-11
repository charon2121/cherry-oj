---
id: "VERIFY-054"
type: "verify"
title: "修复登录会话期限在数据库往返后的精度不一致"
status: "approved"
work: "WORK-053"
owners: ["codex/root"]
depends_on: ["TASK-117"]
related: []
implements: []
verifies: ["ISSUE-017#AC-001", "ISSUE-017#AC-002", "ISSUE-017#AC-003", "ISSUE-017#AC-004", "TASK-117"]
tags: []
result: "pass"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-054：期限精度修复验证记录

## 验证对象

31b4019的最小会话期限修复及对应测试、CI必需执行校验。

## 对应要求

ISSUE-017的AC-001至AC-004技术要求均满足；实际证据见下方各轮记录，最终人工验收尚未签署。

## 检查与结果

本地JDK21认证旧红新绿、81基础检查通过。真实MySQL同组测试旧轮6PASS/2FAILURE，修复轮8PASS/0ERROR/0SKIP；真实Gateway登录200、改密204、重登200，数据部署/校准/发布成功。独立源码和下载证据复核通过。

## 未通过项

WORK-053认证修复范围无未通过项。WORK-050的后续浏览器阶段仍失败，完整业务CI未完成；不能将本工作通过等同整体93项通过。

## 范围检查

生产只改AuthenticationService新会话期限；网关、Schema、JWT TTL、事务与审计时间不变。测试/CI变更均在TASK-117精确路径内，现有服务器、IDEA与用户数据未改。

## 遗留问题

TASK-112须诊断business.io的浏览器退出1，目前没有精确错误证据。WORK-049继续等待完整CI基线。

## 剩余风险

旧Redis纳秒期限会话不自动修复，需正常重新登录。准备阶段Testcontainers正常停止/Ryuk清理未另留逐容器零残留快照；业务栈完整清理已实测，两类证据不得混用。

## 结论

技术验证pass，提交WORK-053人工验收；WORK-050整体仍未完成。

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

## 修复后Linux新绿与业务边界（2026-09-11）

31b401989413476a5902d76763817e56060138fc的[CI34569661914](https://github.com/charon2121/cherry-oj/actions/runs/34569661914)：真实MySQL8.4认证8/8 PASS、零FAIL/ERROR/SKIP；与ef3312e旧红使用相同harness 3fd2ebe7d71b9270746fc0e09397fb7e597c467db0956271cfb5a8ce3c306892和同一组未修改测试。数据库循环完整经过一般纳秒、秒末、微秒与整秒，validate/exchange精度相等，到期前1微秒、到期、撤销断言全部通过。构建证据/private/tmp/cherry-work053-fixed-build-34569661914/business-build，认证摘要source/harness和八方法再次校验通过。

真实Gateway请求依次login200→password/change204→login200，之后创建题目201、上传测试数据201、部署200、校准200、发布200。新节点、部署回执、校准三项PASS；此前身份配置不一致阻断已消失。业务报告/private/tmp/cherry-work053-fixed-34569661914，report.validate(successful=False)/verify_files通过，源提交/测试harness/文件有界和清理一致。

其余9个工程/内核/原生job成功，业务job仍FAIL：浏览器单元启动13.023秒后退出1，记录business.io FAIL，后续11项NOT_RUN（合计3PASS/1FAIL/11NOT_RUN）。现有报告未导出具体浏览器错误，不能判断失败发生在登录、编辑器还是第一个运行断言；不归因为此次精度修复，也不宣称整个WORK-050或93项已通过。浏览器诊断继续归TASK-112，在本工作人工验收后交回；没有修改前端、网关或业务断言绕过。

业务退出清理confirmed=true，Docker容器/卷为空，原生tasks/mounts/cgroups/paths/accounts/groups均空，总tasks/mounts/cgroups为空。准备阶段Testcontainers清理证据限制仍如前述，不能将两种清理混同。现有用户服务/服务器/数据未改。旧Redis中可能已保存纳秒期限的会话不自动修正，需正常重新登录；此限制保留给人工验收。

对应AC：AC-001真实数据库同测试旧红新绿；AC-002原认证生命周期及新边界通过且网关不变；AC-003正常bootstrap/登录/改密/重登通过并推进部署校准发布，新的浏览器失败如实保留；AC-004独立复核、精确边界与无现有部署已核验，最终人工验收由用户签署。WORK-053的技术修复完成不代表WORK-050整体CI完成。

修复后work053_review再次独立核验两轮认证摘要、失败差值、实际请求序列、业务报告与清理文件，确认AC-001至AC-004技术满足，人工验收保留；没有把后续浏览器失败或准备阶段清理证据缺口隐去。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：技术验证pass，完整证据与范围及后续浏览器失败已记录，提交人工验收
- 2026-09-11：验收闸通过：review → approved。原因：确认会话期限精度修复及回归通过，接受已记录限制，交回真实业务 CI
