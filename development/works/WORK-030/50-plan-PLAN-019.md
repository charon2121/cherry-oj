---
id: "PLAN-019"
type: "plan"
title: "修复后台题目列表间歇性 502"
status: "checked"
work: "WORK-030"
owners: ["codex/root"]
depends_on: ["ISSUE-007", "DESIGN-023", "DECISION-017"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# PLAN-019：修复后台题目列表间歇性 502

## 目标

按 ISSUE-007#AC-001～AC-008 修复 Admin 题目列表间歇性 502：先复现并分类密钥轮换，再接入共享只读
恢复，必要时修正 problem-service JWKS 刷新，最后完成安全和跨模块回归。

## 改动区域

- Gateway `auth` 的 Admin 读取恢复抽象及既有用户列表适配。
- Gateway `problem` 的 Admin 普通 JSON GET 路由、错误分类和测试。
- problem-service `security` 的轮换/并发测试、失败观测；只有失败测试证明时修改 decoder/cache。
- WORK-030 的验证和长期记忆。

## 阶段与顺序

1. 写 K1→K2、并发 K2 token 的 problem-service 测试，并补齐 Security Filter 失败分类断言；记录当前实现
   是否失败、真实 status 和 JWKS 请求次数。
2. 把 Admin 用户列表已经验证的一次恢复状态机抽入共享 Admin GET 边界，先确保其原测试继续通过。
3. 为题目列表写首次 401、exchange 结果、二次 401、403/解码错误/5xx、并发单飞的 Gateway 失败测试，
   再接入题目列表；逐项审计其它普通 JSON GET 后决定是否同批接入。
4. 若第 1 步证明资源端缺陷，再调整有界 JWKS 缓存/协调刷新；否则不修改生产 decoder，只保留测试和观测。
5. 执行模块测试、服务端 clean verify、范围/日志敏感信息检查和独立安全复核，填写 VERIFY-031。

## 并行与依赖

第 1 步和 Gateway 测试设计可独立准备，但生产代码必须在失败证据明确后按顺序实施。第 2 步依赖
WORK-028 的现有恢复实现；第 3 步依赖共享抽象；第 4 步依赖第 1 步结论。VERIFY/MEMORY 等实现证据完成
后再推进。

## 迁移与交付

无数据迁移和公开契约版本切换。以单个 WORK 提交 Gateway 和必要的 problem-service 安全改动；部署时
确保 JWKS/user-service 先可用，再启动资源服务和 Gateway。旧 Session 自动原地 exchange，无需强制登出。

## 风险

若轮换测试把“旧 key 已从 JWKS 删除”误当作应成功，会削弱安全边界；测试必须区分“新 key 已发布”、
“旧 key 正在重叠期”和“未知 key”。共享恢复若接入流式/写请求可能重复副作用；路由白名单逐个审计。
观测改动不得记录 token/kid 之外可关联用户的敏感值；kid 也只在确有诊断价值且不包含秘密时使用。

## 验证

- problem-service 定向安全测试：K1 预热、K2 发布、并发 K2 验证、未知 key、错误签名、过期、JWKS outage。
- Gateway 定向测试：正常单次、首次 401 恢复、grant 401、exchange 5xx、fresh 401、403/解码错误不恢复、
  并发单飞及 Admin users 既有回归。
- `cd apps/server && ./mvnw -pl problem-service,gateway-service -am test`。
- `cd apps/server && ./mvnw clean verify`。
- `scripts/work check`、`git diff --check`、敏感值和 TASK 边界审计、独立 Security Review。
- 若本地应用可控，最后用真实 Redis + user-service K1→K2 轮换验证题目列表不返回间歇性 502；不以手工
  重启代替自动化断言。

## 回退

Gateway 改动可独立回退到当前 Admin problems 502 映射；problem-service decoder/cache 改动如存在可独立
回退到当前 Nimbus builder。两者均无数据回滚。若新逻辑造成错误 exchange 放大，立即回退 Gateway 接入；
若 JWKS 未知 key 被错误接受，立即回退资源端改动并视为安全阻断。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已按失败测试、共享恢复、条件式资源修复和全量回归拆分顺序及回退
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
