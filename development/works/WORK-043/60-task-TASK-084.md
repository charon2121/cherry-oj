---
id: "TASK-084"
type: "task"
title: "提交历史跨账号回归与交付验证"
status: "done"
work: "WORK-043"
owners: ["codex/root"]
depends_on: ["TASK-082", "TASK-083", "PLAN-029"]
related: []
implements: ["FEATURE-011#AC-001", "FEATURE-011#AC-002", "FEATURE-011#AC-003", "FEATURE-011#AC-004", "FEATURE-011#AC-005", "FEATURE-011#AC-006", "FEATURE-011#AC-007", "FEATURE-011#REQ-001", "FEATURE-011#REQ-002", "FEATURE-011#REQ-003", "FEATURE-011#REQ-004", "FEATURE-011#REQ-005", "FEATURE-011#REQ-006", "FEATURE-011#REQ-007", "FEATURE-011#REQ-008", "FEATURE-011#REQ-009", "FEATURE-011#REQ-010"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs", "development/works/WORK-043", "development/works/WORK-002", "development/works/WORK-041", "contracts", "apps/server", "apps/web"]
write_paths: ["apps/server/submission-service/src/test", "apps/server/gateway-service/src/test", "apps/web/e2e", "apps/web/src/features/submissions", "development/works/WORK-043"]
forbidden_paths: ["apps/judge-engine", "apps/server/judging-service", "apps/server/user-service", "apps/server/identity-security-support", "apps/server/submission-service/src/main/resources/db/migration", "apps/web/design-system", "docs/design-system", "apps/web/src/components/ui", "apps/server/problem-service", "apps/web/src/app/shells"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-084：提交历史跨账号回归与交付验证

## 任务目标

跨账号、历史版本、草稿保护与页面交付验证。

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

跨账号、历史版本、草稿保护与页面交付验证的实现或测试与实际验证记录。

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
- 2026-09-07：状态变更：todo → ready。原因：两个实现任务已完成
- 2026-09-07：状态变更：ready → doing。原因：执行最终浏览器、视觉及验收材料整理

- 2026-09-07：自动验证、截图、八问与回退/人工验收材料已记录于 VERIFY-044；独立人工复核尚未发生，第四项保持未勾选。
- 2026-09-07：状态变更：doing → blocked。原因：自动验证及交付材料完成，等待 PLAN-029 要求的独立人工复核

- 2026-09-07：用户独立执行实际环境验收，先报告 405，随后确认系未重启后端，重启后“现在已经没有问题了”，并进一步确认“没有别的问题了”。以此记录人工产品复核完成，不声称用户执行过源码审计；正式验收闸仍交由用户签署。
- 2026-09-07：状态变更：blocked → doing。原因：用户确认实际环境复核通过，整理验收闸
- 2026-09-07：状态变更：doing → done。原因：自动验证与用户独立实际验收完成，405 已确认由旧后端进程导致且重启后消失
