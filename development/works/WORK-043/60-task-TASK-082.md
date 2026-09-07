---
id: "TASK-082"
type: "task"
title: "题目内提交记录与代码回看"
status: "done"
work: "WORK-043"
owners: ["codex/root"]
depends_on: ["FEATURE-011", "DESIGN-037", "DECISION-024", "PLAN-029"]
related: []
implements: ["FEATURE-011#AC-001", "FEATURE-011#AC-002", "FEATURE-011#AC-004", "FEATURE-011#AC-007", "FEATURE-011#REQ-001", "FEATURE-011#REQ-002", "FEATURE-011#REQ-003", "FEATURE-011#REQ-004", "FEATURE-011#REQ-006", "FEATURE-011#REQ-007", "FEATURE-011#REQ-010"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs", "development/works/WORK-043", "development/works/WORK-002", "development/works/WORK-041", "contracts", "apps/server", "apps/web"]
write_paths: ["contracts/web-api.openapi.json", "apps/server/submission-service/src/main/java", "apps/server/submission-service/src/test", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/submission", "apps/server/gateway-service/src/test", "development/works/WORK-043"]
forbidden_paths: ["apps/judge-engine", "apps/server/judging-service", "apps/server/user-service", "apps/server/identity-security-support", "apps/server/submission-service/src/main/resources/db/migration", "apps/web/design-system", "docs/design-system", "apps/web/src/components/ui", "apps/web", "apps/server/problem-service", "apps/web/src/app/shells"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-082：题目内提交记录与代码回看

## 任务目标

契约与本人本题提交列表、源码读取。

## 依据

FEATURE-011、EXPERIENCE-019、DESIGN-037、DECISION-024、PLAN-029；验收锚点见 implements。

## 可查看范围

以 read_paths 为准；编码前读取对应 Java/TypeScript 工程规范与工具链。

## 可修改范围

以 write_paths 为准；范围是上限，仅修改满足本任务所需文件。

## 禁止修改

以 forbidden_paths 为准；不改判题链路、身份信任链、设计 token 或数据库迁移。TASK-084 仅添加/修正验证用例与记录，生产缺陷交回对应实现任务，不借测试任务扩展产品行为。

## 依赖

以 depends_on 为准；意图闸及后续实施授权完成前保持 todo。

## 产出

契约与本人本题提交列表、源码读取的实现或测试与实际验证记录。

## 完成标准

- [x] 对应 AC 的代码/测试交付并验证，边界无越界。
- [x] 错误、未登录、跨账号、题目不可见和历史版本场景按本任务范围处理。
- [x] 实际命令与结果写入 VERIFY-044，不以测试 mock 代替权限和数据库证据。
- [x] TASK-084 额外完成页面八问、截图、独立复核、回退检查及人工验收材料；前两任务交付后由其统一验证。

## 验证

执行 PLAN-029 中本任务相关命令；先证明回归覆盖业务不变量，SQL 数据测试与 E2E 必须有真实边界断言。

## 风险

需要修改禁止范围或发现设计缺口时先更新上游与任务边界；源码不得打印。

## 执行记录

- 2026-09-07：完成初始任务拆分，尚未实施。

- 2026-09-07：按用户澄清收敛为题目工作台左侧 Tabs；撤回全局页面、独立详情页、题目解析及导航修改范围，未来个人简介页仅记录为候选方向。

- 2026-09-07：用户明确回复“审核没有问题，开始工作吧”，已取得文档审核意见与实施授权；工具检查意图闸仍为 pending，等待用户本人签署后进入 ready，不重复请求方案确认。
- 2026-09-07：状态变更：todo → ready。原因：意图闸已签署，用户明确允许执行，回退与边界已明确
- 2026-09-07：状态变更：ready → doing。原因：开始契约与本人本题查询实现

- 2026-09-07：后端实现完成；MySQL 本人/本题/分页/判定/源码权限回归及既有 Kafka/Gateway 测试通过，前端与整体复核由后续任务继续。
- 2026-09-07：状态变更：doing → done。原因：后端本人本题查询和源码读取完成，数据库及相关服务回归通过
- 2026-09-07：状态变更：done → doing。原因：补齐最大分页转义响应缓冲边界
- 2026-09-07：状态变更：doing → done。原因：最大分页与大源码边界回归通过，网关客户端 3 项测试成功
