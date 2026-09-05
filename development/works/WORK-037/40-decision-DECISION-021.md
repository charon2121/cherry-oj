---
id: "DECISION-021"
type: "decision"
title: "选择内部身份信任链的系统级重构方案"
status: "approved"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["DESIGN-031"]
related: ["DECISION-008", "DECISION-018", "DECISION-019"]
implements: []
verifies: []
tags: []
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# DECISION-021：选择内部身份信任链的系统级重构方案

## 要决定什么

在保留“浏览器 Session + login grant + JWT + 资源服务独立验签”的现有三层身份模型下，怎样消除
user-service 重启、密钥轮换、资源缓存和 Gateway 转发之间的不一致，使所有读写与流式请求使用同一套
身份语义，而不是继续按业务路由增加失败重试。

## 背景

现场 502 的直接原因是 problem-service 在安全过滤器中拒绝 Gateway 携带的 JWT，但当前错误分类不足以把
这一次拒绝唯一归因到过期、签名、kid 或 claims。可以确认的系统事实是：本地签名密钥随进程重启变化却
复用固定 kid，轮换没有可执行状态机，资源服务复制 verifier，Gateway 各 client 手工转发凭证。WORK-028/
030 的 GET 重试只能掩盖其中一部分窗口，且天然无法安全扩展到 multipart 或非幂等写。

## 候选方案

1. **按路由恢复并重放**：在每个 client 遇到 401 时 exchange，再按请求类型决定是否重发。
2. **每次请求同步 introspection**：资源服务向 user-service 查询 token，取消本地 JWKS 验签。
3. **Gateway 终止认证并转发可信用户头**：资源服务只信任 Gateway 网络身份。
4. **立即迁移到外部 OIDC/IdP**：由成熟身份系统承担签名、发现和轮换。
5. **持久 key ring + 受控轮换 + 共享 verifier + Gateway 统一转发（建议）**：补齐现有模型缺失的
   生命周期、代码边界和发布门禁。

## 决定

建议选择方案 5；这是待负责人通过意图闸确认的架构决策，不视为已经授权实施。

方案包含四个不可拆开的约束：

- 签名密钥来自显式、持久的环境 key ring，`kid` 由公钥内容派生；普通重启不得换 key。
- 轮换必须经过 prepare、activate、retire，且资源探针、JWT 最大寿命和 verifier 缓存窗口构成门禁。
- Java 服务复用同一个身份验证基座，只把路由/角色授权留在本地。
- Gateway 从一个边界为所有内部 JSON、multipart 和 streaming 请求注入身份与 requestId，不以业务 401
  驱动 Controller 重放。

负责人已补充决定：JWT 寿命为 2 小时，Gateway 提前 5 分钟续签；Session/login grant 取消 idle deadline，
保留从首次登录起固定 30 天的 absolute deadline。继续每请求 validate grant/账号状态，使退出、改密、
密码重置和账号禁用立即生效；validate 不延长 absolute，也不再执行 idle 写入。

负责人进一步确认本系统按可信内网和正常运维建模，不追加零信任级加固：内部服务允许 HTTP，生产 Cookie
保留 `Secure` 默认值但不做不可覆盖门禁，普通公钥不实施本机多用户 owner 防护，轮换以三个资源 readiness
实际看到 next kid 为门禁，不建设额外的签名挑战协议。这些取舍不削弱持久 key、共享 verifier、统一转发和
旧新 key 重叠所解决的一致性问题。

## 理由

方案 5 保留 WORK-013 已确认的离线验证和故障隔离，同时直接消除三类结构性根因：不稳定签名事实、复制
且会漂移的验证事实、按业务请求类型分裂的转发事实。上传恢复因此成为正常信任链的结果：ZIP 只发送一次，
不需要额外幂等协议、内存缓存或临时文件。

方案 1 会把身份生命周期泄漏到每个业务接口，写请求还需要逐个证明可重放；方案 2 让全部业务流量同步
依赖身份服务和身份数据库；方案 3 取消资源侧独立验签并扩大内部伪造面；方案 4 长期可取，但会同时改变
账号、部署和运维边界，超过当前故障所需的最小架构迁移。

## 影响与风险

这是系统级、高风险、安全敏感重构，覆盖 user-service、Gateway、problem/submission/judging、共享 Java
模块、开发密钥准备和部署步骤。最大的风险是首次密钥迁移或共享模块错误导致全系统拒绝，因此必须先建立
可回退 K1、跨服务探针和兼容读取，再切 signer；不得在一个发布动作中同时换 key、换 verifier 和删旧配置。

公开 API 和测试数据格式不变；内部 Session/login grant 数据结构及 user identity schema 需要兼容迁移。
若当前本地进程内 K1 无法导出，允许一次明确记录的 token 失效迁移窗口；从持久 K1 开始后的普通重启和
轮换必须无感。Session 最迟在首次登录后 30 天清理；存量有效 Session 保留原 absolute，不在部署时延长。

## 重新考虑条件

出现即时全局撤销、公开 API 客户端、企业 SSO/多组织、跨区域身份服务、HSM/KMS 强制要求，或资源服务
数量增长到本地 key ring 运维不可控时，重新评估标准 OIDC discovery、introspection 和集中密钥管理。

## 变更记录

- 2026-09-04：初稿建议按精确安全 401 对上传做单次恢复。
- 2026-09-04：负责人否决局部重试；废弃原建议，改为系统级身份信任链重构，等待人工确认。
- 2026-09-04：记录 2 小时 JWT、5 分钟提前续签和移除 Session 双超时的负责人决定；撤销传播仍待确认。
- 2026-09-04：负责人最终选择取消 idle、absolute 固定 30 天，并保留每请求 grant validate。
- 2026-09-05：意图闸通过：review → approved。原因：确认实施系统级身份架构重构：JWT 2 小时、提前 5 分钟续签、取消 idle、absolute 固定 30 天，并保留每请求 grant validate
- 2026-09-05：负责人将威胁模型收敛为可信内网，确认撤销过重的内部 TLS、配置不可覆盖、公钥 owner 和
  decoder challenge 要求，继续完成原始一致性重构。
