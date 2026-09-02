---
id: "DECISION-017"
type: "decision"
title: "修复后台题目列表间歇性 502"
status: "approved"
work: "WORK-030"
owners: ["codex/root"]
depends_on: ["DESIGN-023"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# DECISION-017：修复后台题目列表间歇性 502

## 要决定什么

面对 Admin problem 资源接口的间歇性 JWT 拒绝，是否把 WORK-028 的一次恢复扩展为共享的 Gateway
普通 GET 边界，以及在什么证据下修改 problem-service 的 JWKS 缓存/刷新实现。

## 背景

ISSUE-007 的 requestId 证明 login grant 有效但题目请求返回 502，直接缺口是 Admin problems 未接入资源
401 恢复。现场又同时具备“problem-service 长期运行、user-service 临时密钥重启、JWKS 刷新、200/502
混合”，但现有日志没有记录 Security Filter 内部 401，尚不能把特定 Nimbus 并发机制定为事实。

## 候选方案

1. 仅重启 problem-service：短期清缓存，无回归保护，拒绝。
2. 只复制用户列表恢复到题目列表：能缓解表现，但重复身份状态机且不解释资源端轮换，代价低但债务高。
3. 共享 Gateway 普通 GET 一次恢复，直接修改 JWKS cache：覆盖两层，但若资源端无法由测试复现，会变成
   基于猜测的安全配置修改。
4. 共享 Gateway 普通 GET 一次恢复；先建立轮换/并发失败测试，只有测试证明有效新 token 会被拒绝时才
   修改资源端：修复已确认缺口，同时用证据控制高风险安全改动。

## 决定

建议采用方案 4。Gateway 将 WORK-028 的强制 exchange/单次重试收敛为共享的普通 JSON GET 能力，
Admin 题目列表及经逐路由确认安全的读取接入；下载和所有写操作不重放。

problem-service 先增加 K1→K2 轮换、并发请求和失败分类测试/观测。只有有效 K2 token 在 JWKS 已返回 K2
后仍出现失败，才允许调整 decoder 的并发缓存/刷新实现；否则保持当前实现并把已证实修复限定在 Gateway。

该决定须由人工意图闸确认；本文保持 `review` 前不得实施。

## 理由

Gateway 是唯一同时持有浏览器 Session、login grant 和短 JWT 的边界，只有 exchange 结果能区分“授权
已撤销”和“当前资源 token 被拒绝”。复用已有协调逻辑可保持并发 Session 语义，单次 GET 重试可限制
放大效应。

JWKS 属于安全验证根，不能仅凭时间相关性调整缓存。失败测试先行既能确认真正机制，也能防止把正确的
fail-closed 行为误改成容错放行。方案 1 不持久，方案 2 重复并遗漏根因，方案 3 缺少证据门槛。

## 影响与风险

Gateway 正常路径无额外交换；首次资源 401 路径增加一次 user-service exchange 和一次 problem-service
重试。资源端若需改缓存，会改变密钥刷新并发行为，必须保留未知 key/JWKS 故障的 fail-closed 断言。

公开 API、数据库、Redis schema、token claims/TTL 不变。风险集中在错误分类、自动重放范围和日志泄密；
由明确异常类型、普通 JSON GET 白名单、单次上限和敏感值禁记约束。

## 重新考虑条件

需要自动恢复写请求或流式下载；正式环境转为外部 IdP/introspection；Gateway 不再持有 login grant；
多实例要求跨进程单飞；或轮换方案不再提供前一公钥重叠窗口时，重新决策。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：建议采用共享 GET 恢复，并以失败测试作为修改资源 JWKS 安全配置的前置证据，提交人工确认
- 2026-09-02：意图闸通过：review → approved。原因：用户明确确认方案通过并允许开始实施 WORK-030
