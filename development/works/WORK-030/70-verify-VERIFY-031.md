---
id: "VERIFY-031"
type: "verify"
title: "修复后台题目列表间歇性 502"
status: "approved"
work: "WORK-030"
owners: ["codex/root"]
depends_on: ["TASK-048"]
related: []
implements: []
verifies: ["ISSUE-007#AC-001", "ISSUE-007#AC-002", "ISSUE-007#AC-003", "ISSUE-007#AC-004", "ISSUE-007#AC-005", "ISSUE-007#AC-006", "ISSUE-007#AC-007", "ISSUE-007#AC-008", "TASK-048"]
tags: []
result: "pass"
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# VERIFY-031：修复后台题目列表间歇性 502

## 验证对象

ISSUE-007#AC-001～AC-008、TASK-048、problem-service 密钥轮换/JWKS 并发行为、Gateway Admin GET
一次恢复、WebSession 保存和题目读写重放边界。

## 对应要求

front matter 已锚定八项 AC 和 TASK-048。实施后逐项记录正常路径、恢复状态机、非恢复错误、轮换并发、
fail-closed、日志敏感信息和跨模块回归，不能用一次手工刷新或单一 happy path 代替。

## 检查与结果

- JWKS 基线：先用 K1 token 预热当前 Nimbus decoder，再发布 K2，由 16 个不同 `jti` 的有效 K2 token
  并发验证。16 项全部成功，服务只拉取一次新 JWKS；随后发布 K2+K1 重叠集合，K1 token 仍成功。
  因当前实现没有出现并发刷新缺陷，按 DECISION-017 保留生产 `ResourceSecurityConfig`，没有修改 decoder/cache。
- fail-closed：JWKS 不可用时已缓存的已知 K1 仍可验证，未知 key 失败；未知 key、错误签名、过期 token
  继续被拒绝。problem-service 定向安全测试 9 项全部通过。
- Gateway：共享 `AdminGatewayAccess.readWithRecovery` 只在调用方明确分类为资源 401 时执行一次
  exchange，并只重试一次。正常题目 GET 不 exchange；403 仍映射 502 且不恢复；题目创建等写请求只
  发送一次。Admin users 既有 grant 401、exchange 5xx、fresh token 二次 401、Session 保留/清理及并发
  回写语义继续由既有测试覆盖。Gateway 定向测试 8 项全部通过。
- 路由审计：题目列表、详情、版本详情、预览、测试数据列表和发布检查纳入普通 JSON GET 恢复；POST、
  PATCH、PUT、DELETE、上传及流式测试数据下载均未纳入重放。
- 观测：Gateway 记录 `resource_token_rejected`、`token_recovery_succeeded`、
  `token_recovery_failed`；problem-service 记录 `invalid_access_token`、`identity_key_unavailable`、
  `access_denied`。日志只携带受控 requestId、分类和结果；恶意换行 requestId 会被丢弃，响应与日志均不
  写入 Cookie、grant、JWT、用户标识、密钥或异常消息。
- 自动化命令与结果：
  - `./mvnw -pl gateway-service -am test`：57 项通过，0 failure、0 error、0 skip。
  - `./mvnw clean verify`：135 项通过，0 failure、0 error；1 项既有、仅 Linux 执行的集成测试跳过。
  - problem-service K1→K2 定向测试、Gateway 8 项定向测试、problem security 9 项定向测试均通过。
  - `git diff --check` 通过；`scripts/work check` 校验 238 份开发文档通过，0 个进行中提示。
- 独立安全复核未发现可操作 finding：资源 401 仅触发一次 GET 重试；403、写请求、上传及流式下载不
  进入恢复；JWT/JWKS fail-closed、敏感日志和 WebSession 并发语义未被削弱。
- 真实运行服务未由本任务擅自重启或轮换，避免干扰用户当前开发进程；受控轮换由自动化测试提供确定性
  证据，人工验收时可在重启 Gateway/problem-service 后复测 user-service 换钥窗口。

## 未通过项

暂无自动化未通过项。首次在受限沙箱运行涉及本地 JWKS HTTP 监听及 Mockito 动态附加的测试时分别遇到
`Operation not permitted` 和 Byte Buddy self-attach 限制；在获准的非沙箱测试进程中原命令全部通过，
判定为执行环境限制而非产品缺陷。

## 范围检查

改动仅在 TASK-048 的 Gateway `auth`/`problem`、problem-service `security` 及 WORK-030 文档范围内。
没有修改 user-service、其它资源服务、contracts、Web 或数据库；没有让写请求、上传或下载自动重放。
轮换基线未复现生产 decoder 缺陷，因此没有修改生产 JWKS 实现。

## 遗留问题

正式部署仍需配置稳定、可轮换的 PEM，并在 K1→K2 期间保留旧公钥重叠窗口；这不是本次应用层恢复所能
替代的运维条件。

## 剩余风险

自动化证据否定了当前配置下“并发未知 kid 会重复刷新或拒绝有效 K2 token”的假设，但不能代表所有网络
抖动和正式 IdP 实现。即使 Gateway 恢复成功，正式部署仍需使用可轮换 PEM 和旧 key 重叠窗口；进程内
Session 单飞不提供多 Gateway 实例的全局单飞。写请求与下载仍需用户显式重试，避免隐式重复副作用。

## 结论

实现、自动化验证和独立安全复核通过，等待用户人工验收。Gateway 重启加载新代码后，有效登录授权在
题目普通 GET 首次资源 401 时会原地恢复，不再直接暴露间歇性 502；不会放宽资源 JWT 校验。

## 变更记录

- 2026-09-02：创建验证清单并锚定 ISSUE-007 八项验收标准；等待意图闸与实施证据。
- 2026-09-02：记录 K1→K2 并发基线、Gateway 一次恢复、重放边界、日志脱敏及服务端全量回归结果。
- 2026-09-02：独立安全复核未发现可操作 finding。
- 2026-09-02：状态变更：draft → review。原因：K1→K2 并发基线、Gateway 恢复边界、fail-closed、日志脱敏、135 项服务端回归和独立安全复核均通过
- 2026-09-02：验收闸通过：review → approved。原因：用户明确授权签署 WORK-030 验收闸，接受当前实现、验证结果与已记录剩余风险
