---
id: "DESIGN-023"
type: "design"
title: "修复后台题目列表间歇性 502"
status: "checked"
work: "WORK-030"
owners: ["codex/root"]
depends_on: ["ISSUE-007"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# DESIGN-023：修复后台题目列表间歇性 502

## 背景

ISSUE-007 的现场 requestId 证明浏览器 Session/login grant 有效，题目请求却在 problem-service 业务日志
之前失败。Gateway 的 `ProblemApiErrors` 把下游 401/403 和响应解码异常都映射成 502；上一项 WORK-028
只为 Admin 用户列表建立了资源 401 后的强制 exchange，Admin problems 仍使用旧边界。

problem-service 比 user-service 早运行近两天，而本地 user-service 重启会生成新的临时 RSA 密钥。失败
窗口出现一次 JWKS 200 拉取及相邻请求 200/502 混合，因此需要同时验证资源服务密钥轮换和 Gateway
恢复，不能只修改一处错误文案。

## 目标与限制

- 满足 ISSUE-007#AC-001～AC-008，保持 WORK-013 的 Session、login grant、短 JWT 三层边界。
- 先由可控测试判断有效新 key 是否真的在资源服务并发刷新中失败；底层改动必须由失败测试证明。
- 只对 problem-service 明确返回的 HTTP 401 恢复，不按 502 文案或任意异常猜测。
- 自动重试限于能在收到响应前完整重放的普通 JSON GET；写请求、multipart 和流式下载排除。
- 原请求最多重试一次；403 表示权限失败，不能通过换 token 绕过。
- 不改 contracts、数据库、Redis Session 结构、JWT claims/TTL 或 Web 登录跳转协议。

## 整体方案

方案分两层，并以测试分类结果约束实际改动：

1. 在 problem-service 安全边界建立 K1→K2 可控 JWKS 服务器。预热 K1 后切换为 K2，并发提交有效 K2
   token；记录 JWKS 拉取和每个响应。测试同时覆盖未知 key、错误签名、过期 token、已缓存已知 key
   遇到 JWKS 故障等 fail-closed/可用性语义。
2. 若第 1 步复现有效 K2 token 偶发失败，则在 `ResourceSecurityConfig` 显式配置并发安全、有界生命周期的
   JWKS cache/source；要求未知 `kid` 触发一次协调刷新，刷新完成后参与请求看到同一新集合。若测试已经
   稳定通过，保留现有 decoder，不制造无依据改动。
3. 把 Admin 用户列表现有的“一次恢复”能力收敛到 `AdminGatewayAccess` 的普通 GET 调用边界，避免第二份
   状态机。调用者提供 action 及“明确资源 401”分类，组件保留首次使用的 Session 状态并调用现有
   `recoverRejectedAccessToken`。
4. `AdminProblemsController` 只让列表、详情、版本详情/预览、测试数据列表和 publish-check 这类普通 JSON
   GET 进入该边界。本次报告的列表必须覆盖；下载响应含惰性数据流，不纳入自动重放。
5. 首次 401 + exchange 成功后用 fresh token 重试一次；二次 401 转 503。exchange 401 沿用现有逻辑
   失效每个参与请求的 WebSession；exchange 5xx/timeout 保留 Session。所有其它错误继续交给
   `ProblemApiErrors` 映射。

## 模块与数据

- `gateway-service/auth`：持有 login grant、Session 状态和恢复协调；提供只读调用恢复抽象，并让 Admin
  用户列表复用同一实现，防止两份错误语义漂移。
- `gateway-service/problem`：识别 `ProblemServiceClientException` 的 401，普通 GET 接入恢复；业务 DTO、
  分页和错误白名单保持不变。
- `problem-service/security`：提供/修正 JWT decoder 的 JWKS 轮换行为及失败分类日志；业务 application、
  persistence 不参与修复。
- `user-service`：仅作为测试 JWKS/token 来源和只读影响分析，实现原则上不变。

无数据库、Redis schema、公开响应字段或 Web 状态变更。

## 接口与状态

公开 `/api/admin/problems` 系列契约不变，内部状态机为：

```text
普通 GET 成功 ───────────────────────────────► 返回原结果
首次 problem 401 + grant 有效 ─► exchange ─► fresh token 重试一次
首次 problem 401 + grant 失效 ─► 清 Session ─► 公开 401
exchange 临时失败 ───────────────────────────► 公开 5xx，保留 Session
fresh token 再次 401 ────────────────────────► 公开 503，保留 Session
problem 403/非认证异常 ──────────────────────► 原错误映射，不 exchange
```

problem-service 对 token 失败保持内部 Problem Details：无效/未知 token 为 401；JWKS 无法取得且没有可用
key 的基础设施失败为 503。Gateway 不向浏览器泄漏内部错误 detail，只按 status/type 分类。

## 安全与失败

login grant 只留在 Gateway 认证服务。交换和 Session 逐实例回写继续使用 WORK-028 已验证的按 Session
单飞实现，不新建第二套并发锁。二次 401 绝不再 exchange，避免无限循环或身份服务放大。

JWKS 缓存若需调整，必须设置有限缓存期/刷新等待上限和 2 秒网络超时；已缓存且仍在发布集合中的 key 可在
短暂 JWKS 故障时继续验证，未知 key 在取不到 JWKS 时必须拒绝。不得接受无 `kid`、非 RS256、错误 issuer/
audience/claims 或已过期 token。

403 不可恢复；自动重放只覆盖无副作用普通 GET。日志仅记录 requestId、失败阶段、内部 status、是否尝试
exchange/JWKS refresh 及结果分类，不记录敏感身份值。

## 监控与部署

现有 `http.server.completed` 对 Spring Security 链提前拒绝的请求不可见，本工作补充最小安全失败事件，使
同一 requestId 能确认是 `invalid_token`、identity key unavailable 还是 controller 之后的响应问题。
Gateway 相应记录 `resource_token_rejected`、`token_recovery_succeeded/failed` 等分类；不新增外部监控系统。

部署顺序不要求停机；若 problem-service 安全配置有改动，先启动 user-service/JWKS，再重启资源服务，
最后更新 Gateway。正式环境仍必须使用显式可轮换 PEM，并在 JWKS 中保留覆盖在途 token TTL 的前一公钥；
Gateway 恢复不能替代正确密钥轮换。

## 迁移与兼容

无数据迁移和公开 API 版本变化。旧 Redis Session 首次遇到下游 401 时可就地 exchange。Gateway 回退会
恢复为 502；problem-service 若有缓存改动可独立回退到当前 Nimbus builder。两者均不修改持久化数据。

## 备选方案

- 只重启 problem-service：可清空现场缓存，但下一次 user-service 重启/轮换仍会复发，且没有测试证据，
  仅可作为临时处置。
- 只在 Gateway 看到 502 后 exchange：502 同时包含 401、403和解码错误，会对无关故障执行身份操作，拒绝。
- 复制 `AdminUserController` 的恢复代码到 problems：能快速修列表，但形成第二套状态机且不会验证 JWKS
  轮换，拒绝。
- 每个请求都 exchange：增加数据库写和签名成本，破坏短 JWT 的离线验证目的，拒绝。
- 使用共享的一次性 GET 恢复，并由受控测试决定是否修资源 JWKS：既纠正公开边界，又避免无证据改底层，采用。

## 风险与重审条件

最大风险是把 403/响应损坏错判为 401，或把下载/写请求当普通 GET 重放。测试必须直接构造各状态，并审计
每个接入路由。若共享抽象不能明确限制重放类型，应只接入题目列表而不扩大。

若受控轮换测试无法复现现场，不能宣称 Nimbus bug 已修；应保留 Gateway 恢复和新增观测，以后用新的
requestId 继续分类。若所有资源 BFF 都需要同一恢复、Gateway 多实例要求跨进程单飞、JWT 改 introspection/
外部 IdP，或正式轮换策略改变旧 key 保留期，重新评审。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已定义测试先行的双层修复、只读重放边界、失败状态机、可观测性和回退策略
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
