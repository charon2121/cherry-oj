---
id: "DESIGN-031"
type: "design"
title: "重建内部身份信任链并消除管理请求 502"
status: "checked"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["ISSUE-009"]
related: ["DESIGN-010", "DESIGN-022", "DESIGN-023"]
implements: []
verifies: []
tags: []
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# DESIGN-031：重建内部身份信任链并消除管理请求 502

## 背景

ISSUE-009 证明测试数据上传在 problem-service 安全入口被拒绝，业务代码和 ZIP 尚未运行。WORK-028 与
WORK-030 已分别为两个 GET 场景建立 token rejection 后的 exchange/retry，但把写请求排除，最终形成
“读可恢复、写不可恢复”的业务路由差异。

更根本的问题在部署和代码边界：user-service 本地默认每次启动随机生成新 RSA key，固定复用
`local-ephemeral` kid；密钥重叠轮换只有文字步骤，没有 key ring 状态机和门禁；problem、submission、
judging 各复制一套 decoder/validator；Gateway client 各自手工添加 Authorization。系统无法证明签发方、
转发方和四个验证方在同一时刻共享同一个信任事实。

## 目标与限制

- 满足 ISSUE-009#AC-001～AC-010，修复对象是完整内部身份信任链，不是测试数据路由。
- 保留浏览器 Session + login grant + RS256 JWT + 资源独立验签的三层模型；按负责人意图将 JWT 设为
  2 小时、Gateway 提前 5 分钟续签，取消 Session idle 并将 absolute 固定为首次登录起 30 天。
- 正常重启、滚动发布与受控轮换不让合法 token 产生间歇 401；业务请求不承担身份恢复重放。
- 密钥 material 不进入 Git、数据库、Redis、日志或 API；生产仍只接受外部 Secret。
- 各服务的路由授权规则仍归各服务，不把业务权限塞进共享模块。
- 不修改浏览器 API、业务数据库、测试数据格式、Web 页面、judge 或 sandbox。

## 整体方案

### 1. 把密钥从“进程变量”升级为“环境密钥环”

user-service 只从显式 key ring 读取：一个 active 私钥/公钥，以及零到多个 verification-only 公钥。开发
环境通过受控命令在 Git 忽略目录原子生成一次 RSA key pair，普通启动只读复用；生产从只读 Secret 挂载。
任何模式都不再在服务启动时悄悄生成随机 key。

`kid` 不再由操作者自由填写，而由规范化公钥的 SHA-256 指纹派生。active、next、previous 都使用各自
内容决定的 kid；加载时验证私钥/公钥匹配、kid 唯一、算法/强度，并对私钥和轮换状态执行路径/权限校验。
普通 verification-only 公钥只校验内容与唯一性。相同 kid 对应不同公钥被视为损坏配置并 fail fast。

### 2. 把轮换变成有状态、可验证的三阶段协议

```text
prepare K2:  JWKS=K1+K2，仍由 K1 签名
      ↓ 所有资源服务预取并验证 K2 probe
activate K2: JWKS=K1+K2，改由 K2 签名
      ↓ 等待 maxTokenTtl + clockSkew + verifierCacheSafetyWindow
retire K1:   JWKS=K2，删除前先完成资源与回退检查
```

命令读取 key ring manifest，记录非敏感的 active/published kid、阶段和时间，拒绝跳阶段、过早退休或没有
回退 key。轮换不是数据库业务迁移；manifest 与公钥可由 Secret/部署系统版本化，私钥仍在受控文件中。

JWKS 返回确定排序的公开 key set、ETag 与明确缓存策略。资源端对未知 kid 执行一次有界刷新；已缓存的
合法 key 在短暂 JWKS 故障期间可继续使用，未知 kid 且无法刷新时返回 identity infrastructure 503。

### 3. 建立共享 identity-security 基座

在 `apps/server` 新增纯基础设施模块，集中提供：

- RS256 allowlist、issuer/audience/timestamp/required claims validator；
- 公钥指纹/kid 计算和 key ring 结构校验；
- 统一的远程 JWKS decoder factory、cache/refresh 时限与错误分类；
- `roles` 到 Spring authorities 的转换；
- 不含敏感值的认证失败枚举和测试 fixture。

problem、submission、judging 使用同一 remote verifier；user-service 的自验证复用相同 claims 与 key 选择
规则，但从本地 key ring 验证。各模块保留自己的 `SecurityFilterChain` 路由 matcher 和 AccessDenied
策略。共享模块不包含业务 DTO、Controller 或数据库访问。

### 4. 让 Gateway 只有一个内部身份转发边界

Gateway 的认证服务按 `expiresAt - 5m` 在 Session 层刷新 2 小时 token。它向业务 client 交付不可变
`DelegatedIdentity(accessToken, expiresAt, requestId)`；统一的内部 request factory/filter 负责给 JSON、
multipart 和流式请求添加 Authorization、requestId 与 trace，不允许各 client 自由拼接。

业务 Controller 不再调用 `readWithRecovery` 或判断资源 401，也不自动重放任何业务请求。资源返回身份
401 说明信任链违反不变量：Gateway 统一映射为可观测的 503 并保留仍有效 Session，供修复信任状态后重试；
只有 user-service 明确判定 login grant 已撤销或账号状态失效才清 Session 并向浏览器返回 401。

### 5. 取消 idle，保留 30 天固定 absolute

配置使用显式策略 `session-lifetime-policy=fixed-absolute`，并设置 `session-absolute-timeout=30d`。不再保留
`session-idle-timeout` 或 `refresh-idle-on-activity`，也不使用超大 idle 值冒充取消：

- `user_login_session.idle_expires_at` 迁移为可空并停止参与有效性判断；`absolute_expires_at` 仍为非空，
  在登录时一次性写入 `created_at + 30d`，后续 validate/exchange 均不得延长。
- Gateway `AuthSessionState` 和 user-service 内部响应移除 idle deadline，只传播 absolute deadline；Redis
  WebSession TTL 始终设置为 `absoluteExpiresAt - now`。
- `/internal/auth/touch` 改为 `/internal/auth/validate`：每次管理请求只读验证 grant 未撤销、账号 ACTIVE、
  `session_version` 一致且 absolute 未到期，不再更新 deadline。这样退出、改密、密码重置和账号禁用立即生效。
- `last_used_at` 只在 token exchange 或受控异步观测中更新，避免 validate 每次都锁行并写 MySQL。
- 浏览器 Cookie 继续保持当前 session-cookie 行为，本工作不增加 `Max-Age`；关闭浏览器后是否恢复会话仍
  由浏览器处理，服务端最长只保留到首次登录后的第 30 天。

负责人已确认上述策略。共享撤销索引/事件可在每请求 validate 成为瓶颈时另立工作，不在本次并行引入。

### 6. 让信任链成为 readiness 和部署门禁

user-service readiness 检查 active key、public ring、manifest 与权限。各资源服务 readiness 至少证明
JWKS 可达、配置 issuer/audience 一致并能验证当前 active kid 的受控 probe；Gateway readiness 证明它可
exchange 且内部 client 使用统一凭证构造。readiness 失败时实例不应接收业务流量。

部署/开发检查输出 only kid、generation、阶段与结果，不输出 token 或 key。真实跨服务测试覆盖服务重启、
轮换三阶段、并发、JWKS outage、四种请求体和当前 ZIP 上传。

## 模块与数据

- `apps/server/identity-security-support`（建议名称）：共享密码学/验证基础设施；由 Java 服务消费。
- `user-service`：key ring loader、JWT signer/JWKS、30 天 fixed-absolute login session、轮换/初始化命令、readiness；仍是
  身份唯一签发者与撤销事实源。
- `gateway-service`：30 天 fixed-absolute Redis Session、2 小时 token/5 分钟提前续签、统一 DelegatedIdentity 和内部
  请求工厂；不签 token。
- `problem/submission/judging-service`：共享 verifier + 本地授权规则与安全响应。
- `scripts/` 与 `apps/server/TOOLCHAIN.md`：本地 key 初始化、轮换检查与启动顺序。

不新增业务表。开发 key ring 位于已忽略的受控目录；生产 key ring 由 Secret 管理。若 manifest 需要落盘，
只包含公开 kid、阶段、时间和公钥引用，不包含私钥正文。

## 接口与状态

公开 OpenAPI 不变。内部 JWKS 仍为 `/.well-known/jwks.json`，但具备稳定 kid、确定 key set 与缓存元数据。
可新增仅内网可达的 identity metadata/probe，返回 active kid、published kids、算法、最大 token TTL 与
generation；不返回 token、主体或私钥。

状态所有权：

```text
浏览器是否登录        持久 Gateway Session + user-service 主动撤销型 login grant
JWT 是否仍在有效期     user-service 签发事实 + Gateway expiresAt
JWT 由谁签发           key ring active key
资源接受哪些 key       key ring published set + 共享 verifier cache
业务是否允许当前角色   各资源服务 SecurityFilterChain
```

资源 401 不再驱动业务 action 重试。内部身份不一致使用 503 `SERVICE_UNAVAILABLE` 的现有公开安全降级；
资源 403、业务 4xx/5xx 保持原映射。

## 安全与失败

- 私钥最低 RSA-3072，文件权限仅运行用户可读；符号链接、目录穿越、group/world writable 和公私钥不匹配
  均拒绝。开发初始化使用原子 create-new，不覆盖现有 key。
- kid 由 key 内容派生，杜绝“同 kid 换 key”造成缓存歧义；禁止 `alg=none`、算法协商和请求方指定算法。
- prepare 阶段只发布 K2 公钥，不能用 K2 签名；activate 前所有资源必须验证 K2；retire 前必须经过等待
  窗口并保留可回退配置。
- 共享 verifier 只统一密码学与 claim 不变量，不统一业务路径权限，避免一个模块配置错误放行全系统。
- 日志仅记录受控 reason、kid 指纹短标识、key generation、requestId 和阶段；异常消息不得带 token/JWK
  私有字段。公开错误继续脱敏。
- JWKS 不可用且 token kid 已缓存时按缓存窗口继续；未知 kid 不可验证时 503 fail closed。错误签名、过期
  和非法 claims 为内部 401；Gateway 不把它等同浏览器登录失效。

## 监控与部署

至少记录 key ring loaded、rotation prepared/activated/retired、JWKS generation served、unknown kid refresh、
verification failure reason、Gateway credential injected/missing invariant 和 readiness 结果。指标不使用
userId、token、URL 参数或文件名标签。

兼容交付顺序：先生成并保存当前 K1 key ring，使其与当前 user-service key 对齐；若当前临时私钥已无法
导出，则安排一次显式登录失效窗口作为唯一迁移事件。随后发布共享 verifier、资源 readiness、Gateway
统一转发，再迁移 fixed-absolute Session，最后移除 ephemeral fallback、idle timeout 和业务 GET retry。K2 轮换演练通过
后才完成交付。2 小时 JWT 使旧 key 退休等待窗口同步延长，不能沿用原 120 秒假设。

## 迁移与兼容

Redis Session/login grant 需要结构迁移：新状态去掉 idle deadline，MySQL `idle_expires_at` 改为 nullable，
`absolute_expires_at` 保留；已撤销记录保持撤销，不复活已经过期的旧记录。存量仍有效会话保留原 absolute，
只有新登录使用 30 天，以避免部署时擅自延长既有授权。若无法把当前内存 K1 纳入持久
ring，切换时必须明确使旧 Session token 失效并由 Gateway 按 expiresAt 重新 exchange，不能假装无缝。
正式轮换从持久 K1 开始后应无用户可见中断。

旧 120 秒 token 最多等待原 TTL 后消失；新 token 固定为 2 小时。Gateway 在兼容窗口内可读取旧 Session
字段但写回无 idle 的新格式；Redis 存量 key 保留原 absolute 剩余 TTL，新 Session 使用 30 天 absolute。

旧配置 `key-id/private-key-location/public-key-location/previous-public-keys` 提供一个发布周期的启动兼容与
明确弃用告警；新旧同时配置或产生不同 key ring 时拒绝启动。完成迁移后删除 `generated:local` 与固定
`local-ephemeral`。

## 备选方案

- **每个业务路由收到 401 后重试**：无法覆盖非幂等/流式写，继续制造差异，否决。
- **每次请求向 user-service introspection**：消除 JWKS 轮换，但让所有业务请求同步依赖身份数据库，扩大
  故障面并放弃已确认的短 JWT 离线验证，暂不采用。
- **Gateway 验权后转发裸用户头**：资源服务失去独立验证能力，内部可伪造，否决。
- **完整外部 OIDC/IdP**：能把密钥生命周期交给成熟系统，但改变账号与部署边界；企业 SSO/多组织时重审。
- **持久 key ring + 共享 verifier + 统一 Gateway 转发**：保留当前三层身份模型，同时消除临时密钥、
  重复 validator 和业务重试三类根因，建议采用。

## 风险与重审条件

最大风险是密钥迁移操作错误导致全系统拒绝，或共享模块把业务授权误收拢。任务必须分阶段交付，先建立
K1 可回退 ring 和跨服务探针，再切 signer；共享层只能输出认证事实，路由授权测试必须留在各服务。

若需要即时全局撤销、公开 API 客户端、外部 IdP、多租户、跨区域 JWKS、硬件密钥/HSM 或 Gateway 多集群，
重新评估 introspection、标准 OIDC discovery 与集中密钥管理，不继续扩展本地文件 key ring。

## 变更记录

- 2026-09-04：初稿形成精确 401 后重试方案并进入 review。
- 2026-09-04：根据负责人反馈废弃业务重放方向，重写为系统级 key ring、共享 verifier、统一转发和部署门禁方案。
- 2026-09-04：纳入 2 小时 JWT、提前 5 分钟续签和主动撤销型持久 Session；撤销传播策略待负责人选择。
- 2026-09-04：负责人最终选择取消 idle、absolute 固定 30 天并保留每请求 grant validate，替代永久 Session。
- 2026-09-05：结构与内容校验通过，由工具置为 checked。
