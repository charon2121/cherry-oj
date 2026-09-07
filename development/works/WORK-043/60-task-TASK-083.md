---
id: "TASK-083"
type: "task"
title: "提交列表、详情与历史代码载入体验"
status: "done"
work: "WORK-043"
owners: ["codex/root"]
depends_on: ["TASK-082", "DESIGN-037", "PLAN-029"]
related: []
implements: ["FEATURE-011#AC-001", "FEATURE-011#AC-002", "FEATURE-011#AC-003", "FEATURE-011#AC-004", "FEATURE-011#AC-005", "FEATURE-011#AC-006", "FEATURE-011#REQ-001", "FEATURE-011#REQ-002", "FEATURE-011#REQ-003", "FEATURE-011#REQ-004", "FEATURE-011#REQ-005", "FEATURE-011#REQ-006", "FEATURE-011#REQ-007", "FEATURE-011#REQ-008", "FEATURE-011#REQ-009", "FEATURE-011#REQ-010"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs", "development/works/WORK-043", "development/works/WORK-002", "development/works/WORK-041", "contracts", "apps/server", "apps/web"]
write_paths: ["apps/web/src/features/submissions", "apps/web/src/features/problems/components", "apps/web/src/features/problems/hooks", "apps/web/src/features/problems/lib", "apps/web/src/features/problems/api", "apps/web/src/routes/_site.problems.$slug.tsx", "apps/web/src/routeTree.gen.ts", "apps/web/src/generated", "apps/web/e2e", "development/works/WORK-043"]
forbidden_paths: ["apps/judge-engine", "apps/server/judging-service", "apps/server/user-service", "apps/server/identity-security-support", "apps/server/submission-service/src/main/resources/db/migration", "apps/web/design-system", "docs/design-system", "apps/web/src/components/ui", "apps/server", "contracts", "apps/server/problem-service", "apps/web/src/app/shells"]
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# TASK-083：提交列表、详情与历史代码载入体验

## 任务目标

题目左侧 Tabs、panel 内列表与详情、确认载入历史代码。

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

题目左侧 Tabs、panel 内列表与详情、确认载入历史代码的实现或测试与实际验证记录。

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
- 2026-09-07：状态变更：todo → ready。原因：后端接口完成，前端任务边界与意图闸已确认
- 2026-09-07：状态变更：ready → doing。原因：开始题目左侧 Tabs 与历史代码载入体验
- 2026-09-07：状态变更：doing → blocked。原因：等待后端最大分页响应边界修正，前端实现与基础回归已完成
- 2026-09-07：状态变更：blocked → doing。原因：后端补充完成，继续页面异常与视觉验证

- 2026-09-07：左侧 Tabs、历史详情和确认载入已实现；Web check 168 项单测及构建通过；原工作台 17 项及新增历史基础 5 项浏览器用例通过。页面与综合复核归 TASK-084 继续执行。
- 2026-09-07：状态变更：doing → done。原因：前端功能与基础回归完成，综合验证进入 TASK-084
