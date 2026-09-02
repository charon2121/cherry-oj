---
id: "ISSUE-007"
type: "issue"
title: "修复后台题目列表间歇性 502"
status: "approved"
work: "WORK-030"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# ISSUE-007：修复后台题目列表间歇性 502

<!--
本节面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。报错原文、依赖坐标、字段、类、框架、协议、表名、路径和
命令从下一节开始再出现。
-->

## 为什么做

管理员进入“账号管理－题目列表”时，同一登录状态下会间歇性看到“服务暂时不可用”。重试有时成功，
账号管理等其它后台页面也仍能使用。这会阻断题目维护，并把身份校验故障伪装成普通上游故障，现场难以
判断是登录、题目数据还是服务可用性问题。

## 问题现象

2026-09-02 14:13:43，请求 `req_338867642cc14d46b3a30aebc1c31b16` 调用
`GET /api/admin/problems`，Gateway 返回 502 `BAD_GATEWAY`。同一 requestId 的
`POST /internal/auth/touch` 返回 200，说明浏览器 Session 和登录授权仍有效；problem-service 没有对应
业务完成日志，说明请求在进入题目查询前已被拒绝。

相邻时间窗口内同一路由出现 200 与 502 混合。14:13:35 problem-service 因未知/变化的签名密钥请求
user-service 的 JWKS 并得到 200，但随后仍有多次 502。14:18:19 Gateway 完成一次 token exchange 后，
14:18:20 的 Admin 题目请求恢复为 200。

## 复现方式

现场触发条件如下；实施阶段先把它固化为可重复的自动化场景：

1. problem-service 保持运行并已缓存当前 JWKS（用于验证 JWT 的公开密钥集合）。
2. user-service 使用本地临时 RSA 密钥启动后发生重启，产生新的 `kid` 和密钥；Gateway/Redis Session
   中仍存在可用的登录授权及不同时间取得的短 JWT。
3. 管理员进入 `/admin/problems`，让一个或多个 `GET /api/admin/problems` 并发或连续发出。
4. 观察 Gateway、user-service、problem-service 三方同一 requestId 的状态及 JWKS 拉取次数。

当前日志已证明现场条件和错误边界，但尚未证明 Nimbus JWKS 并发刷新的具体内部机制；不能把推测当成
已经复现。TASK-048 必须先建立“密钥 K1 → K2、并发验证 K2 token”的失败测试，再决定是否需要改
problem-service 的 JWKS 缓存实现。

## 实际结果

Gateway 将 problem-service 的 401/403 或无法解码的响应统一映射成公开 502，管理端题目列表失败。
浏览器 Session 不会被清除，刷新或稍后重试可能恢复；错误响应无法区分“资源 JWT 被拒绝”和“上游响应
格式损坏”。

## 预期结果

有效管理员的浏览器 Session 和登录授权仍有效时，题目列表不应因一次内部短 JWT 拒绝而失败。Gateway
应只对明确的资源 401 执行一次安全恢复和一次 GET 重试；真实登录授权失效才返回公开 401。JWKS 已成功
发布新公钥时，problem-service 应稳定验证由该密钥签发的有效 token，并对旧的、未知的或签名错误的
token 继续 fail closed。

身份对象边界沿用 WORK-013，资源拒绝后的恢复语义沿用 WORK-028；本工作补齐 Admin problems 和资源
服务密钥轮换验证，不改变公开 API。

## 影响与条件

直接影响后台题目列表；同一 `AdminProblemsController` 下的题目详情、版本预览、测试数据列表和发布检查
等 GET 读取具有相同错误映射。POST/PATCH/PUT/DELETE、文件上传和流式下载有不同重放风险，不因本问题
默认自动重试。

触发条件集中在内部 JWT 被资源服务拒绝的窗口，包括本地临时签名密钥重启、正式密钥轮换、未知 `kid`
刷新竞态或 token 边界过期。公开题目列表在同一现场持续返回 200，未发现题目数据库或查询逻辑异常。

## 原因

已确认的直接原因是：`AdminProblemsController` 不具备 WORK-028 已为 Admin 用户列表实现的资源 401
恢复路径，而 `ProblemApiErrors` 把 problem-service 的 401/403 收敛为 502；因此仍有效的登录授权无法用于
恢复短 JWT，浏览器只看到泛化的 `BAD_GATEWAY`。

现场日志还强烈指向 JWKS/密钥切换窗口：problem-service 进程自 2026-08-31 15:44 持续运行，user-service
于 2026-09-02 13:17 用新的临时密钥重启；失败窗口出现 JWKS 200 拉取及 200/502 混合。具体是并发刷新、
旧 token、边界过期还是其它 Nimbus 选择行为，需由受控测试确认后才能决定底层修复。

## 修复方向

先增加可观察、可断言的密钥轮换和并发验证测试，拿到 problem-service 的真实内部状态码与失败分类。
若有效 K2 token 在 JWKS 已返回 K2 后仍失败，则修正资源服务的 JWKS 缓存/刷新配置；若资源服务行为
正确，则不做无证据的底层改动。

无论底层是否需要调整，Gateway 都为 Admin 题目“普通 JSON GET 读取”接入与 WORK-028 一致的一次性
恢复：首次明确下游 401 时用 login grant 强制 exchange，成功后只重试一次；exchange 401 才清 Session，
exchange 临时失败或新 token 仍被拒绝时返回 5xx 并保留 Session。403、响应解码错误和写请求不触发恢复。

## 回归检查

- AC-001：正常 Admin 题目列表只调用 problem-service 一次，不额外 exchange、不重试，分页和公开响应不变。
- AC-002：首次明确资源 401、login grant 有效时只 exchange 一次、只重试一次，最终返回 200，Session 保持登录。
- AC-003：exchange 返回 401 时失效浏览器 Session 并公开返回 401；刷新不能恢复已撤销的授权。
- AC-004：exchange 超时/5xx或 fresh token 二次 401 时返回 5xx、保留 Session，且没有循环恢复。
- AC-005：下游 403、响应解码失败、429 和其它 5xx 不触发 token exchange；题目写请求和流式下载不自动重放。
- AC-006：受控 K1→K2 轮换中，JWKS 已发布 K2 后的有效 K2 token 在并发验证下稳定成功；未知、过期、
  错误签名 token 以及 JWKS 不可用时的未知 key 继续 fail closed。
- AC-007：Gateway 与 problem-service 日志可用 requestId 区分资源 401、JWKS 获取失败、响应解码失败和
  恢复结果，不记录 Cookie、grant、JWT、用户标识或密钥材料。
- AC-008：Gateway、problem-service 及服务端聚合回归通过；公开题目读取、Admin 403、CSRF、退出、改密、
  题目写入与错误白名单行为不回归。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已用 requestId 日志确定失败边界、区分已确认原因与待证实的 JWKS 机制，并定义八项验收标准
- 2026-09-02：意图闸通过：review → approved。原因：用户明确确认方案通过并允许开始实施 WORK-030
