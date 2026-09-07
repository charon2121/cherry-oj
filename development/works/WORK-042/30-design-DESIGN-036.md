---
id: "DESIGN-036"
type: "design"
title: "修复后台用户列表拒绝尚未过期的身份令牌"
status: "checked"
work: "WORK-042"
owners: ["codex/root"]
depends_on: ["ISSUE-013"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# DESIGN-036：修复后台用户列表拒绝尚未过期的身份令牌

## 背景

见 [ISSUE-013](./10-issue-ISSUE-013.md)。共享 verifier 已在 WORK-039 修正，user-service 独立解码器遗漏同类修复。

## 目标与限制

只修正 user-service 令牌时间语义；不调整签发、网关、前端、密钥轮换或权限。

## 整体方案

删除 TokenConfig 中 JwtIssuedAtValidator 导入、实例和链条项。在 requiredClaims 增加 getIssuedAt()!=null。实施测试发现默认 claims converter 会为缺少 iat 的 JWT 补值，因此沿用共享 verifier 的转换方式：先记录原始 iat 是否存在，默认类型转换后移除自动补值。此补充落实既有 iat 必填要求，不改变任务边界。JwtTimestampValidator 继续要求 exp 并校验 exp/nbf。测试用历史签发时间生成真实 RSA 签名令牌并调用生产 decoder，无需等待 30 秒。

## 模块与数据

仅 user-service 的 TokenConfig 和对应测试；无需数据库、Session、密钥或接口迁移。

## 接口与状态

路径和响应不变。修复后合法后台列表请求不再因令牌年龄误报 503；真正认证失败仍按现有链路处理。

## 安全与失败

保留算法、签名、kid、issuer、audience、业务 claims 和 exp/nbf 校验；iat 必填语义与已确认 WORK-039 一致。禁止通过扩大 clock skew 或吞掉 401 修复。

## 监控与部署

构建后 user-service 需重新启动才能加载代码；本回合不重启。实施后通过测试和用户列表验证，保留 requestId，禁止输出 Cookie、JWT 或私钥。

## 迁移与兼容

无迁移。仍未过期的现有令牌可继续使用。

## 备选方案

扩大 clock skew 会扩大时间容忍，网关强制换新仅暂时掩盖缺陷，均不采用。全面合并两个 decoder 涉及密钥来源差异，超出本次最小修复。

## 风险与重审条件

主要风险是移除校验时遗漏 iat 必填或弱化 exp；用正反例分别约束。未来需要令牌最大年龄时另行定义独立规则。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：只读排查和最小修复方案已整理，提交人工意图审核，尚未实施
- 2026-09-07：结构与内容校验通过，由工具置为 checked。
