---
id: "DESIGN-025"
type: "design"
title: "修复 WORK-031 遗留的 CI 测试断言"
status: "checked"
work: "WORK-032"
owners: ["codex/root"]
depends_on: ["ISSUE-008"]
related: ["WORK-031", "FEATURE-009", "DESIGN-024", "TASK-049", "VERIFY-032"]
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# DESIGN-025：修复 WORK-031 遗留的 CI 测试断言

## 背景

ISSUE-008 已确认两个失败均来自 WORK-031 实施后的旧测试断言。FEATURE-009 要求功能页不展示通用
title/desc，并彻底删除 `/api/status` 垂直切片；DESIGN-024 同时要求无可见介绍标题的页面保留语义，
其它成功信封、Problem Details 和 request ID 合同保持不变。当前生产实现符合这些定义，偏差只存在于
`apps/web/e2e/problems.spec.ts` 与 `scripts/contracts_test.py`。

## 目标与限制

- 只修正测试如何观察已批准行为，不改变产品行为、公开契约、CI 配置或依赖。
- 保留题库 URL 筛选恢复、真实详情链接、320px 安全和泄密 canary 等原有覆盖。
- 保留 Web API 成功信封、响应头、Problem Details 和 request ID 样例的一致性覆盖。
- 不把可见页面介绍标题或 status 接口作为兼容资产恢复。

## 整体方案

Web 用例把旧标题的可见性断言替换为关键词筛选输入的可见性断言，下一条现有断言继续验证输入值为
“两数”。这样“页面已进入可操作状态”与该用例本身要验证的 URL 恢复共用同一个稳定业务锚点，不依赖
被禁止的装饰性介绍块；`sr-only` 的“题库”H1 继续由实现与其它可访问性检查负责，不把视觉隐藏元素
错误地断言为可见。

契约测试以现存核心公开接口 `GET /api/problems` 及 `ProblemListSuccess` 替代 status 专属样本：

- `ProblemListSuccess.required` 必须是 `data` 与 `meta`；
- `GET /api/problems` 的 200 响应必须带 `X-Request-Id` 并引用该成功 schema；
- 路径上的成功样例 `meta.requestId` 必须与通用 `ApiMeta.examples[0].requestId` 一致；
- 共享 `ApiProblemResponse` 的 header、媒体类型、schema 与 instance/request ID 关系继续按原逻辑检查。

## 模块与数据

- `apps/web/e2e/problems.spec.ts`：只负责浏览器行为断言，不改页面实现。
- `scripts/contracts_test.py`：只负责读取并校验契约，不改契约真源。
- `contracts/web-api.openapi.json` 与 `apps/web/src`：只读，用来确认测试锚点与已批准实现一致。

没有请求、响应、数据库、持久化或生成物变化。

## 接口与状态

公开接口与页面状态均不变。测试从已删除接口迁移到现存题库接口，不新增兼容 URI，也不改变 API
版本；测试失败仍通过非零退出码阻断 CI。

## 安全与失败

现有详情页 canary、HTML 清洗和 320px 溢出断言原样保留。契约失败时 unittest 仍明确指出缺失 schema、
header 或 request ID 不一致。改动可通过恢复两处测试行回退，但回退会重新制造已知 CI 失败，正确恢复
方式是保留新断言并修复未来真正偏离合同的实现。

## 监控与部署

没有部署、指标或线上观察变化。本地按 CI 相同步骤验证；只有用户之后明确要求提交并推送时，才读取
对应 GitHub Actions run 作为远端证据。

## 迁移与兼容

测试只依赖 WORK-031 已批准的当前合同。`GET /api/problems` 是题库核心公开能力，同时具备完整成功
example，适合作为通用成功信封样本；若未来有意删除该接口，应在新的产品工作中同步迁移这条合同测试。

## 备选方案

- 恢复可见“选择下一道题”或 `/api/status`：违背 FEATURE-009，拒绝。
- 只删除两个失败测试：会丢失 URL 恢复和 API 信封不变量，拒绝。
- 在 E2E 中断言 `sr-only` H1 可见：Playwright 的可见性语义与视觉隐藏相冲突，拒绝；测试真实任务组件。
- 遍历所有 API 成功响应做完全泛化校验：覆盖更广，但会把本次两行旧锚点修复扩大成新的契约测试体系；
  当前保留既有抽样策略，未来需要全量不变量时另建改进工作。

## 风险与重审条件

风险主要是新锚点过度绑定某个页面字段或单一接口。关键词输入正是该 E2E 的目标状态，题库接口则是
现有且带 example 的核心公开接口，因此当前稳定性足够。若 Web 页面改为无关键词筛选、公开题库接口被
替代，或需要证明所有 JSON 成功响应都遵守信封，应重新设计相应测试，而不是静默删断言。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已明确两处测试迁移、兼容边界、备选方案与重审条件，提交结构校验
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
