---
id: "TASK-117"
type: "task"
title: "修复登录会话期限在数据库往返后的精度不一致"
status: "doing"
work: "WORK-053"
owners: ["codex/root"]
depends_on: ["ISSUE-017", "DESIGN-047", "DECISION-031", "PLAN-037"]
related: []
implements: ["ISSUE-017#REQ-001", "ISSUE-017#REQ-002", "ISSUE-017#REQ-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "docs/engineering", "apps/server/TOOLCHAIN.md", "apps/server", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "development/works/WORK-050", "development/works/WORK-053"]
write_paths: ["development/works/WORK-053", "development/works/WORK-050", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application/AuthenticationService.java", "apps/server/user-service/src/test/java/com/cherryoj/userservice/application/AuthenticationServiceTests.java", "apps/server/user-service/src/test/java/com/cherryoj/userservice/persistence/UserPersistenceIntegrationTests.java", "deploy/sandbox-linux/ci/business_prepare.py", "deploy/sandbox-linux/ci/business_results.py", "deploy/sandbox-linux/ci/business_test.py", "deploy/sandbox-linux/ci/README.md"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/user-service/src/main/resources", "apps/server/pom.xml", "apps/server/user-service/pom.xml", "apps/judge-engine", "contracts", "apps/web", "compose.yaml", "deploy/backend", "AGETNTS.local.md", "apps/server/data"]
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# TASK-117：统一签发期限并补数据库往返回归

## 任务目标

实现ISSUE-017的REQ-001至REQ-003及AC-001至AC-004，按DESIGN-047和PLAN-037先复现再修复。

## 依赖

意图闸passed，用户在看到材料后明确允许开始，TASK-117已通过工具置doing。当前先完成测试和CI编排，取得真实MySQL旧红后再修复。

## 可修改范围

以front matter精确路径为准。CI脚本仅允许本工作必需的数据库测试执行、报告零skip核验和相应反例，不修改业务用例断言。WORK-050仅用于交回和任务依赖记录。

## 禁止修改

网关及其他生产类、迁移/依赖/协议/会话配置、Go沙箱和限额、用户私有文件及服务器，详见forbidden_paths。

## 完成标准

- [ ] 实际authenticate→MySQL→validate/exchange在纳秒固定时钟下旧红新绿，签发值不晚于配置期限。
- [ ] 最小生产修改及原有过期/撤销/固定期限回归通过。
- [ ] CI明确执行必需数据库测试且拒绝skip，保留失败与所有权清理事实。
- [ ] Linux正常登录/改密/重新登录通过，接续TASK-112并诚实记录全部业务结果。
- [ ] 独立复核完成，技术证据齐备后由用户签署验收闸。

## 验证

本地JDK21编译及不依赖外部服务的测试；数据库与完整业务只在一次性GitHub VM。本机IDEA、Docker业务、旧ZIP和原服务器不动。不同根因或更广修改先重审。

## 执行记录

2026-09-11：仅形成修复材料，尚未实施。
- 2026-09-11：状态变更：todo → ready。原因：已核验WORK-053意图闸passed且用户明确允许开始；进入限定复现与修复范围
- 2026-09-11：状态变更：ready → doing。原因：先补真实数据库精度回归和CI必需执行检查，再按旧红证据实施最小修复

## 依据

ISSUE-017、DESIGN-047、DECISION-031和PLAN-037。

## 可查看范围

以read_paths为准；生产仅用于定位和精确修复。

## 产出

一个生产类的最小修复、两类回归测试及CI必需执行证据。

## 风险

旧会话不自动修正；不增加容差或迁移来隐藏这一限制。

- 2026-09-11：本地JDK21新增签发精度测试确定性1FAIL，原3项通过；真实MySQL回归已编译且接入必需CI检查，尚未运行。保留生产旧实现以取得数据库旧红。本工作独立复核、测试及后续修复提交推送/Actions需明确授权，未复用TASK-112发布许可。

- 2026-09-11：用户明确授权WORK-053独立复核、分批commit/push及GitHub CI（先复现批次，确认根因后最小修复）；启动只读独立复核。本授权不涉及现有服务器、用户数据、其他生产类或WORK-049。

- 2026-09-11：ef3312e的Linux认证测试精度2FAILURE、其余6PASS、零ERROR/SKIP；真实MySQL回读+211ns。取得证据后实施单处签发期限微秒截断，本地4项认证测试转绿，生产差异独立复核通过，修复批次Linux待运行。
