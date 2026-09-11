---
id: "ISSUE-017"
type: "issue"
title: "修复登录会话期限在数据库往返后的精度不一致"
status: "approved"
work: "WORK-053"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# ISSUE-017：登录后的会话应保持有效

## 为什么做

用户刚登录成功，接着修改密码或打开需要登录的页面，却可能被告知服务不可用。应消除服务之间记录登录期限的精度差异，让有效登录正常使用，同时继续拒绝被延长或失效的会话。

## 问题现象

WORK-050的CI34563521786在空白Linux环境完成bootstrap、login200后，首次password/change503。原生节点与依赖正常、清理通过。诊断轮34564019849已确认IDENTITY_CONFIGURATION_MISMATCH；不能单凭503认定唯一原因。

## 原因

AuthenticationService.now保留Clock.systemUTC纳秒，authenticate把同一absoluteExpiresAt写入MySQL DATETIME(6)并原样返回。validate/exchange回读为微秒，GatewayAuthenticationService.validateUpdatedDeadline要求equals。首次值不是微秒整数时存在确定的不一致机制，独立源码复核确认；尚无真实数据库的确定性旧红新绿证据。

## 预期结果

- REQ-001：签发、持久化和回读的固定会话期限一致，不延长现有配置时长。
- REQ-002：保留网关严格期限、过期、撤销、密码和权限检查；不靠重试或放宽容差绕过。
- REQ-003：真实数据库往返覆盖纳秒时钟，修复后继续真实业务CI，保留原始失败。

## 回归检查

- AC-001：非微秒整数的固定时钟下，真实authenticate→MySQL→validate/exchange测试旧实现失败、修复后严格相等；覆盖微秒和秒边界。
- AC-002：过期、撤销、固定期限不延长等原有认证测试通过，网关生产规则不变。
- AC-003：一次性Linux VM正常bootstrap、登录、改密、重新登录成功，接续部署/校准/页面业务；新失败真实记录，不将未运行项计PASS。
- AC-004：独立复核精度与边界、无现有服务/数据迁移，人工验收后交回WORK-050。

## 当前状态

用户已签署意图闸并允许实施；TASK-117正在补确定性复现和CI证据，生产修复尚未开始。

## 复现方式

在7c8f9a2或3c0b0cc运行完整ci.yml，独立新环境首次登录后改密。两次均失败，尚未有固定时钟数据库最小复现。

## 实际结果

诊断CI34564019849确认IDENTITY_CONFIGURATION_MISMATCH，登录后deadline一致性检查存在明确嫌疑；配置时长和policy未发生变化。没有采集绝对期限差值，仍须确定性实验完成归因。

## 影响与条件

Linux/JDK21的纳秒时钟与MySQL8.4微秒持久化组合，影响首次登录后的认证请求。其他环境不据此承诺必现。

## 修复方向

采用DESIGN-047签发源微秒截断候选，先实验再实施。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：真实业务失败与精度候选及验收条件已整理，待人工审核
- 2026-09-11：意图闸通过：review → approved。原因：确认期限精度修复方案，允许实施
