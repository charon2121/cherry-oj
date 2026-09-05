---
id: "ISSUE-009"
type: "issue"
title: "重建内部身份信任链并消除管理请求 502"
status: "approved"
work: "WORK-037"
owners: ["codex/root"]
depends_on: []
related: ["CAPABILITY-004", "ISSUE-006", "ISSUE-007", "FEATURE-007"]
implements: []
verifies: []
tags: []
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# ISSUE-009：重建内部身份信任链并消除管理请求 502

## 为什么做

管理员的登录明明仍然有效，题目读取和 ZIP 上传却会随机显示“服务暂时不可用”。前两次修复分别给用户
列表和题目读取增加了重试，但写操作仍然失败；继续逐接口加重试会让每种请求都形成一套不同的身份行为，
还可能重复创建或覆盖数据。

这次不再把上传当成孤立故障。目标是让“谁签发身份、各服务信任哪些密钥、密钥怎样更换、Gateway 怎样
携带身份”成为一套稳定的系统能力。正常重启和受控轮换期间，有效登录的读、写、JSON、ZIP 和流式请求
都应一致工作，不依赖业务接口失败后重放。

## 问题现象

2026-09-04 18:57:21，`POST /api/admin/problems/{problemId}/test-data` 返回 502 `BAD_GATEWAY`，
requestId 为 `req_076e03d3f41c45579af7d4bfac28da0c`。同一 requestId 下：

- user-service 的登录授权 touch 返回 200，浏览器 Session 与长期登录授权仍有效；
- problem-service 在 Controller、ZIP 解析、数据库和资产存储之前拒绝请求，记录 `invalid_access_token`；
- Gateway 把资源服务 401 脱敏成浏览器看到的 502。

11 秒前的 `req_79d5a54d4d964c4fa91269677ff4a56e` 结果相同。现场日志只能说明资源身份没有被接受，当前
`SecurityProblemWriter` 把“未携带、格式错误、签名不匹配、过期、claim 错误”合成同一分类，无法继续
判定是密钥、转发还是生命周期问题。

## 复现方式

系统级复现不依赖某一个业务路由：

1. 建立 ADMIN 浏览器 Session，让 Gateway 缓存 user-service 签发的短 JWT。
2. 分别执行 problem、submission、judging 和 user-service 管理端点的 JSON 读、JSON 写与 multipart 上传。
3. 在请求之间依次执行 user-service 普通重启、当前密钥轮换、上一密钥退休和 JWKS 短暂不可用。
4. 观察每个服务接受的 `kid`、JWKS 版本、Gateway 使用的 token 世代和业务调用次数。
5. 对照当前本地默认：user-service 每次启动随机生成 RSA 密钥，却固定使用
   `kid=local-ephemeral`；资源服务各自维护独立的 JWKS decoder 和错误分类。

## 实际结果

- 同一个有效浏览器登录在不同资源、不同 HTTP body 类型上可能得到 200、401 或公开 502。
- 恢复逻辑散落在 Admin 用户 GET 与 Admin problems GET；写请求、上传和下载另有行为。
- 正常本地重启会无条件更换签名密钥，且新旧不同密钥复用同一 `kid`，破坏 key id 的唯一含义。
- 三个资源服务复制 JWT validator、JWKS 客户端、超时和错误映射，长期会继续漂移。
- Gateway 的各业务 client 手工添加 Authorization，请求类型之间没有统一的转发不变量测试。

## 预期结果

沿用 `CAPABILITY-004#REQ-006`、`#REQ-009` 与 `#REQ-012` 的 JWT/独立验签边界，但补齐可执行的密钥
生命周期和共享验证基座：

- 普通重启不更换身份密钥；轮换期间新旧 token 均按明确窗口被接受；同一 `kid` 永不指向不同公钥。
- Gateway 对所有资源请求从统一边界注入身份与 requestId，业务 Controller 不再决定 token 恢复或重放。
- 所有资源服务使用同一验证实现与失败分类，只把路由权限留在各服务本地。
- 测试数据上传在正常信任链上只发送一次并成功；不以 multipart 重试、临时落盘或前端重登作为修复。
- 配置、密钥或 JWKS 不满足不变量时启动/就绪失败，不能带着不一致的信任状态对外服务。
- JWT 寿命调整为 2 小时，Gateway 在剩余 5 分钟时统一续签；浏览器/login grant 会话不再因空闲失效，
  从首次登录起最多保持 30 天，主动退出、改密、重置密码、账号禁用或明确撤销可提前终止。

## 影响与条件

范围覆盖 user-service 签名与 JWKS、Gateway Session token 生命周期和内部请求构造、problem/submission/
judging/user 资源验证、启动配置、开发密钥准备、部署轮换步骤和跨服务测试。属于系统级安全重构。

RS256、issuer/audience、角色模型和各业务 API 保持。JWT 从 120 秒调整为 2 小时，提前刷新量从 15 秒
调整为 5 分钟；Session/login grant 取消 idle、保留 30 天固定 absolute deadline，因此需要 user identity 数据迁移，
但业务 schema、测试数据资产格式、Web 页面、judge/sandbox 不变。密钥文件和私钥不得进入 Git、日志、
数据库、响应或测试快照。

## 原因

直接故障是资源服务拒绝 Gateway 转发的 JWT；更深层原因是身份信任链没有被当成一个可部署单元：

1. 本地默认用进程内临时密钥，正常重启天然破坏仍在有效期内的 JWT。
2. 固定 `local-ephemeral` kid 被不同公钥复用，资源缓存无法把 kid 当稳定主键。
3. “发布新公钥→切换签名→等待旧 token 过期→退休旧公钥”只存在文档原则，没有受控 key ring、状态检查
   和自动化发布门禁。
4. 资源验证代码复制四份，Gateway 身份头按业务客户端手写，信任链不变量无法一次性约束。
5. WORK-028/030 用业务 GET 重试缓解症状，形成读写分裂，无法保证 multipart 等非幂等请求。

本工作不会在证据不足时断言本次 401 一定来自某一种密码学失败；先补细分观测与真实跨服务复现，再用
统一架构消除所有合法 token 的配置/轮换不一致。

## 修复方向

采用“持久化环境密钥环 + 内容派生唯一 kid + 受控重叠轮换 + 共享资源验证基座 + Gateway 统一身份转发”：

- 删除普通 server 模式的随机临时密钥默认。开发环境显式初始化一次本地私钥目录，后续重启复用；生产
  继续只从 Secret 读取。缺失、权限过宽、密钥不匹配或重复 kid 时 fail fast。
- `kid` 由公钥指纹确定，配置不能把同一 kid 绑定到另一把密钥。key ring 可同时发布 active、next、
  previous 公钥，只有 active 私钥签名。
- 提供 prepare/activate/retire 三阶段轮换与检查命令；只有所有资源服务验证 next key 成功后才允许
  activate，只有旧 token 最大寿命和缓存窗口结束后才允许 retire。
- 新建共享的 Java identity security 基座，统一算法、claims、JWKS cache/refresh、超时和安全错误分类；
  各资源服务只声明本地路径权限。
- Gateway 以一个内部请求凭证对象/工厂给所有 JSON、multipart 与流式 client 注入 Authorization 和
  requestId；移除按业务 GET 的 401 重试。合法信任链不需要重放写请求。
- 用显式 `fixed-absolute` 会话策略替代 idle/absolute 双滑动计算。MySQL login session 与 Redis WebSession
  只保留首次登录起 30 天的 absolute deadline；`touch` 改为不延长时间的 grant validate。

## 回归检查

- AC-001：同一开发环境密钥在 user-service 连续重启后保持相同公钥与 kid；已有未过期 2 小时 token 和新 token
  在所有资源服务继续通过，浏览器 Session 不需要重登。
- AC-002：kid 由公钥唯一派生；同 kid/不同 key、私钥公钥不匹配、重复 key、权限过宽、缺失 active key
  均阻止启动或轮换，私钥永不出现在 JWKS。
- AC-003：K1→K2 的 prepare、activate、retire 全流程中，资源验证探针和真实 Admin JSON 读/写、multipart
  上传均无间歇 401/502；K1 只在 2 小时 TTL+skew+cache 窗口后退休。
- AC-004：problem、submission、judging 与 user-service 使用共享的算法/claim/key 选择和错误分类；每个
  服务自己的路径/角色规则保持独立，未知算法、错误签名、错误 issuer/audience/role/pwd 继续 fail closed。
- AC-005：Gateway 所有内部资源 client 通过统一构造器携带 token 与 requestId，在 token 剩余 5 分钟时
  统一续签；JSON、multipart、下载流都有真实 HTTP 测试，业务 Controller 中不再存在按路由 retry 状态机。
- AC-006：测试数据 ZIP 从浏览器到 problem-service 只发送一次、业务上传只执行一次并返回 201；READY
  hash/manifest/原包不变，Gateway 不缓存、复制或重放完整 ZIP。
- AC-007：内部安全观测可区分 missing bearer、malformed、expired、unknown kid、bad signature、invalid
  claims 与 key service unavailable；公开响应保持脱敏，日志不含 token、grant、Cookie、用户或密钥材料。
- AC-008：JWKS/信任链未就绪时相关服务 readiness 为失败，不接收业务流量；已缓存合法 key 遇短暂 JWKS
  故障仍按明确窗口工作，未知 kid 在无法刷新时返回 503 而不是假 401。
- AC-009：旧 Redis Session/login grant 可迁移到 `fixed-absolute` 会话；新会话不因 idle 自动失效，从
  首次登录起 30 天后失效，主动退出、改密、重置密码、账号禁用和显式撤销可提前终止续签。
- AC-010：四个 Java 服务、Gateway、真实 Redis/MySQL、密钥重启/轮换、并发与全量 Maven 回归通过；
  WORK-028/030 的局部恢复代码被移除或证明不再参与业务路径。
- AC-011：JWT `exp-iat=2h`、Gateway `refresh-ahead=5m`，并在启动时校验刷新量小于 TTL；并发请求只触发
  一次 exchange，成功后后续请求使用新 token，exchange 失败不发送业务请求。
- AC-012：Redis WebSession 和 MySQL login session 取消 idle deadline，并以同一个登录时刻计算 30 天
  absolute deadline；连续空闲超过旧 30 分钟仍有效，超过 30 天必须重登，浏览器 Session Cookie 行为保持。

## 变更记录

- 2026-09-04：根据现场 requestId 确认资源安全拒绝，初稿提出 multipart 单次重试。
- 2026-09-04：状态变更：draft → review。原因：现场 requestId 已确认失败发生于资源安全过滤器，复现、预期、范围和八项验收标准已完整记录
- 2026-09-04：负责人否决逐接口重试方向；问题提升为系统级身份信任链重构，重新定义十项验收标准。
- 2026-09-04：负责人指定 JWT 2 小时、提前 5 分钟刷新，并移除 Session idle/absolute 自动失效；新增持久会话验收。
- 2026-09-04：负责人最终选择取消 idle、保留 30 天 absolute，并要求主动撤销继续即时生效。
- 2026-09-05：意图闸通过：review → approved。原因：确认实施系统级身份架构重构：JWT 2 小时、提前 5 分钟续签、取消 idle、absolute 固定 30 天，并保留每请求 grant validate
