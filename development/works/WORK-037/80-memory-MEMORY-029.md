---
id: "MEMORY-029"
type: "memory"
title: "内部身份信任链故障的架构教训"
status: "checked"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["VERIFY-038"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# MEMORY-029：内部身份信任链故障的架构教训

## 背景

WORK-028/030 曾为两个 Admin GET 场景增加资源 token 拒绝后的恢复，但测试数据上传仍因同类安全拒绝公开
为 502。负责人明确否决继续给 multipart 或其它写接口补重试，要求从现有架构根治。

## 决定与原因

短 JWT 的签发 key、published JWKS、资源 verifier cache 与 Gateway token 生命周期必须被当成同一个
部署单元；任何业务 client 都不负责修复这条信任链。负责人已签署意图闸，实施采用持久 key ring、具名
全资源探针轮换、共享 verifier、Gateway 单一身份转发边界和 readiness 门禁，业务请求不再通过 401 重放
补偿身份基础设施不一致。

## 尝试与教训

- 公开 502 不是根因；同 requestId 的资源安全日志只能确认内部 401，不能在错误分类补齐前断言具体为
  expired、unknown kid 或 bad signature。
- `touch=200` 只证明 Session/login grant 可用，不证明当前短 JWT 会被每个资源接受。
- 进程启动随机换 key、不同 key 复用固定 kid，会让所有 verifier 缓存和路由重试都变成概率性补偿。
- “安全过滤器先于业务执行”只能证明服务端未产生副作用，不能证明 multipart 可安全二次订阅；因此业务
  写重放不是身份架构的通用恢复机制。
- 轮换文档如果没有 key ring 状态、探针、等待窗口和发布门禁，就不构成可执行的轮换能力。
- 安全复核必须服从产品威胁模型。可信内网系统可以保留 HTTP 和可覆盖的生产默认值；不能把零信任、
  本机恶意用户或 HSM 级要求不断追加到一次身份一致性修复中。

## 已知问题

生产 Secret 挂载、现有 K1 的导入和滚动发布仍属于部署环境事实，不能由仓库内测试替代；首次生产发布
必须先确认持久 key ring 和三个资源 readiness，再允许流量。负责人最终决定 JWT 2 小时、提前 5 分钟
续签、取消 Session idle、absolute 固定为首次登录起 30 天。原 `/touch` 的延长语义已替换为每请求只读
`validate`，账号禁用、改密和主动撤销仍即时生效。

## 重新考虑条件

需要即时撤销、外部客户端、企业 SSO、多组织、跨区域、HSM/KMS，或资源服务规模使本地 key ring 无法
可靠运维时，重审标准 OIDC discovery、introspection 与集中密钥管理方案。

## 变更记录

- 2026-09-05：状态变更：draft → review。原因：身份一致性故障与威胁模型边界已沉淀，等待依赖验证随验收定稿
- 2026-09-05：结构与内容校验通过，由工具置为 checked。
