---
id: "PLAN-007"
type: "plan"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["CAPABILITY-002", "EXPERIENCE-003", "DESIGN-007", "DECISION-006"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# PLAN-007：建立统一的 Web REST 交换协议与请求基建

## 目标

在 DECISION-006 获人工批准后，契约先行地实现 Gateway 与 Web 共用的 REST/JSON 基础设施，并以
WORK-008 status endpoint 作为首个迁移与跨模块验证样本。

## 改动区域

- `contracts/`：新增 browser-facing OpenAPI 与 examples，更新轻量契约测试。
- `apps/server/gateway-service`：request context、success envelope、Problem mapper、status 迁移与测试。
- `apps/web`：生成类型、公共 client/error/parser、status feature 迁移与 MSW/E2E。
- `.github`/scripts：生成物漂移与契约检查；仅在批准方案确定实际生成器后修改。
- `docs/`/`CLAUDE.md`：实现验证完成后，才同步已经确认的长期协议。

## 阶段与顺序

0. 人工审阅：确认 DECISION-006 checklist 与安全边界；未完成时停止。
1. 契约：新增 OpenAPI 公共 components、status path、成功/失败 examples 和兼容测试。
2. 后端：实现 request ID filter/context、`ApiSuccess<T>`、`ApiMeta`、WebFlux Problem mapper 与脱敏测试。
3. 前端：选择并锁定稳定 OpenAPI type generator，实现 client/error/parser，禁止 client 内 Query/导航。
4. 迁移：将 `/api/status` 从裸 DTO 迁到新协议，删除 WORK-008 的临时 helper/shape。
5. 验证：contract drift、Java、Web unit、MSW、E2E、真实 Vite proxy 和安全失败场景。
6. 沉淀：根据实际结果更新全局 docs 与 memory；不自动代签 release/observe。

## 并行与依赖

契约冻结前不并行写 Java/TS。契约通过后，后端适配器与前端 parser 可并行，但 status 迁移依赖两侧
基础层；E2E 依赖 Gateway 和 Web 都完成。生成器选择必须先用最小 OpenAPI 3.1.x schema 验证 optional、
nullable、discriminated union 与 Problem extensions，不凭名称选型。

## 迁移与交付

WORK-008 尚未发布，无兼容消费者，获批后可以原地替换试验 shape。未来已有消费者的 endpoint 必须
使用 additive change、双写/迁移窗口或新 `/api/v2`。本工作只完成代码与本地验证，上线需独立确认，
观察 request error rate、problem code、contract error 和 latency。

## 风险

风险包括 OpenAPI generator 对 3.1.x 支持不完整、Gateway WebFlux 与 MVC 错误模型差异、错误二次包装、
request ID 不一致、5xx 信息泄漏、strict decoder 破坏兼容。每项均在 VERIFY-009 有对应门禁；出现需
修改推荐 wire format 的问题时回到 DECISION-006，不在 TASK 中偷偷改协议。

## 验证

运行 contracts 解析/schema/example 测试；Gateway 单元与组件测试；Web format/lint/typecheck/unit/
build/E2E；生成器重跑后 git diff 为零；真实 Vite → Gateway proxy；构造 400/401/403/409/422/429/
5xx、非 JSON upstream、未知 optional 字段、204、AbortSignal 和 request ID 一致性场景；最后运行
`scripts/work check` 并记录实际证据。

## 回退

在只迁移 status endpoint 的阶段可恢复 WORK-008 临时实现和旧 Web 面板，不涉及数据回退。契约发布
并被业务 endpoint 使用后不能简单回滚字段；必须保留兼容读取或回到上一 API 版本。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：仅在人工批准后执行的契约先行计划已形成草案
- 2026-08-25：状态变更：review → approved。原因：人工授权按契约先行顺序开始开发
