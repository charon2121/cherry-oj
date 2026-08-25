---
id: "MEMORY-007"
type: "memory"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["VERIFY-009"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# MEMORY-007：建立统一的 Web REST 交换协议与请求基建

## 背景

WORK-008 的连通性纵向切片被人工复核认定为“不足以承担未来通用请求基建”，因此另建 WORK-009，
先完成公共协议审阅，再编码。

## 决定与原因

DECISION-006 已人工确认并实现：请求 body 保持 endpoint DTO；普通 JSON 成功响应使用
`{ data, meta: { requestId, pagination? } }`；失败使用 RFC 9457 Problem 并扩展稳定 code、request ID
与可选 violations。Gateway 拥有 browser-facing 契约，内部服务 DTO 不被这一 envelope 污染；初期
保持 `/api`，只有破坏性且无法兼容迁移时才使用 `/api/v2`。

## 尝试与教训

仅实现一个 status DTO 和 GET helper 能证明网络连接，但不能验证成功/失败协议、分页、幂等、兼容、
安全脱敏和生成链路。基础设施任务必须先列全未来消费场景，再用最小 endpoint 验证，而不是把样例
endpoint 反推成通用抽象。

OpenAPI 生成器不能只看知名度。openapi-typescript 7.13.0 在当前 TypeScript 6 工具链中被 peer
dependency 正常拒绝；@hey-api/openapi-ts 0.99.0 明确支持 TS 6，并可只启用 TypeScript plugin，因此
选后者、精确锁版本并用临时目录逐文件检查生成漂移。不得用 `--force` 掩盖工具链不兼容。

静态 OpenAPI 类型不能校验网络数据。公共 client 使用 Zod 校验 envelope/problem/request ID，允许
响应新增未知可选字段，并把错误区分为 http/network/timeout/aborted/contract。Query 只负责有语义的
重试；client 不导航、不 toast、不缓存、不自动重试。

## 已知问题

WORK-008 的旧裸 DTO 和 GET helper 已被原地迁移；`/api/status` 现在是统一契约的首个真实消费者，
但仍只证明 Gateway 连通，不代表业务服务整体健康。生产发布和真实下游错误映射仍待后续工作项。

## 重新考虑条件

公开第三方 API、Gateway 职责变化、GraphQL/大规模流式协议、生成器无法继续支持 OpenAPI/TS 版本，
或需要改变 envelope/status/code/version 语义时，回到 DECISION-006 与 DESIGN-007 重新决策。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：协议决定、生成器兼容性教训、运行时边界和重审条件已沉淀
- 2026-08-25：状态变更：review → approved。原因：长期决定与已验证经验完成复核，可作为后续 browser-facing API 接入依据
