---
id: "MEMORY-020"
type: "memory"
title: "交付题库、题目与测试数据管理"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["VERIFY-025"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-30"
updated_at: "2026-08-30"
---


# MEMORY-020：交付题库、题目与测试数据管理

## 背景

题库与题目/测试数据管理是 `WORK-002` 答题闭环的完整前置切片。长期事实应继续以已确认的
`docs/product.md`、`docs/data-model.md` 和 `docs/database-design.md` 为准；本文件等待实施验证后记录本次
落地经验。

## 决定与原因

- 公开题库使用独立 public DTO、Gateway 白名单 record 和 Web Zod strip 三层边界；只依赖“不渲染未知
  字段”不够，因为秘密仍可能留在 Query 状态或调试工具中。
- 题目版本和测试数据都采用不可变版本；远程部署/校准先于 problem-service 本地发布事务完成，发布只
  切换当前指针。这样跨服务失败不会留下半发布状态。
- URL 只保存公开筛选；服务端资源归 Query，题目长表单归 Form，参考程序仅归组件内存。离开页面和校准
  成功都清空源码，避免敏感输入被缓存、持久化或进入地址栏。

## 尝试与教训

- 文件路由中列表和 `$slug`/`$versionId` 会形成父子层级；父级必须只渲染 `Outlet`，列表放 index route。
  否则地址会变化但用户仍看到父列表。该问题由真实 Playwright 导航暴露并加入回归。
- Zod 的 `loose()` 虽能兼容新增字段，但会把未知字段保留在结果中；公开 endpoint 应使用 strip，既兼容
  演进又防止 canary 进入 Query 状态。
- Monaco 默认 loader 会请求 CDN；生产工作台显式注入本地 Monaco API 和 C++ language contribution。
  这增加了 ADMIN lazy chunk，应继续监测管理端首开耗时，但不会拖累公开题库。
- ZIP 验证、hash/manifest、原子 rename 和数据库状态必须作为同一事实链测试；只验证扩展名或只验证 DB
  行都无法覆盖穿越、断流、重复上传和 finalize 竞争。

## 已知问题

当前计划不含 CORE/多语言、环境管理 UI、对象存储供应商、服务身份/异步部署、自动标定、solveStatus、
正式提交和专用搜索；这些是范围边界。实施后发现的真实遗留问题再记录。

## 重新考虑条件

题目可见性扩展为租户/付费、服务暴露公网、使用对象存储、异步/多环境部署、服务身份、CORE/多语言/
自动标定、题量十万级、相关性/总数、公开历史或 solveStatus 时，重新审查存储、授权、分页和编排。

## 变更记录

- 2026-08-30：状态变更：draft → approved。原因：长期决策、实现教训与重审条件已记录
