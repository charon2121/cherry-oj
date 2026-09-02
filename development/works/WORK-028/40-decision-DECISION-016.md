---
id: "DECISION-016"
type: "decision"
title: "修复后台用户列表偶发误跳登录页"
status: "approved"
work: "WORK-028"
owners: ["codex/root"]
depends_on: ["DESIGN-022"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# DECISION-016：修复后台用户列表偶发误跳登录页

## 要决定什么

当资源接口拒绝 Gateway 当前持有的内部短 JWT、但浏览器 Session 及登录授权可能仍有效时，系统应在哪
一层恢复，以及何时才允许把公开响应定义为“需要重新登录”。

## 背景

WORK-013 已决定浏览器 Session、登录授权和内部 JWT 是三个不同对象，并规定只有明确过期或撤销才清理
Session。ISSUE-006 证明当前 Admin 用户 BFF 把第三层 JWT 的 401 直接当成第一层 Session 失效；Web
随后跳登录，而刷新仍能读取 Redis Session。本地临时签名密钥重建只是触发源之一，决策需要覆盖通用
短令牌失配。

## 候选方案

1. Web 二次检查 Session：前端在资源 401 后再查 `/api/auth/session`，只有匿名才跳转。能缓和误跳，
   但无法恢复资源调用，且浏览器被迫理解内部 token 失败。
2. Gateway 每次都交换 JWT：不会使用旧 token，但增加每请求数据库写与签名成本，改变短 JWT 模型。
3. Gateway 在资源 401 后强制交换并重试一次：用登录授权真值区分真实退出和 JWT 失配，正常路径零额外
   开销；需要严格限制重试次数与写请求幂等性。
4. 只持久化本地签名密钥：缩小一种触发条件，但不修复错误映射，且不能覆盖轮换和边界竞态。

## 决定

建议采用方案 3：由 Gateway 在 Admin 用户列表的下游首次 401 后强制交换内部 JWT，并只重试一次读取。
只有 exchange 也返回 401 才清理浏览器 Session；exchange 临时失败或新 token 仍被拒绝时保留 Session，
以 5xx 表达服务端身份链故障。

该决定须由人工意图闸确认；本文保持 `review` 前不得实施。

## 理由

Gateway 是唯一同时持有 Redis Session、login grant 和内部 JWT 的边界，只有它能在不泄露凭据的前提下
确认授权真值。按失败恢复保持正常请求成本不变，也不改变两分钟 JWT 的撤销上限。单次重试可恢复密钥
重建、轮换窗口和边界过期，同时避免无限循环。

方案 1 只修视觉结果，方案 2 改变性能与安全模型，方案 4 只覆盖一个开发环境触发源，均不能满足
ISSUE-006 的完整语义。

## 影响与风险

Gateway 需要新增明确的“forced exchange after rejection”状态与并发测试；Admin 用户 GET 会在特定失败
路径多一次内部调用和一次业务重试。公开协议、Web token 边界、数据库和密钥配置不变。

风险集中在并发 WebSession 回写和重放：共享刷新结果必须写入每个请求实例，写请求不得未经幂等审查
自动重试。第二次资源 401 必须转为 503，不能再次触发恢复或误清 Session。

## 重新考虑条件

以下任一发生时重新决策：需要把恢复扩展到所有资源 BFF；Admin 写接口具备正式幂等键并希望自动恢复；
内部 JWT 改为 introspection 或外部 IdP；Gateway 不再保存 login grant；生产采用多实例 Gateway 后需要
跨实例单飞而不只依赖进程内协调。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已比较前端复核、每次换 token、失败后恢复与持久化密钥四种方案，建议采用 Gateway 失败后单次恢复，提交人工确认
- 2026-09-02：意图闸通过：review → approved。原因：确认 Gateway 在下游 JWT 401 后单次恢复，只有登录授权失效才退出，并允许实施
