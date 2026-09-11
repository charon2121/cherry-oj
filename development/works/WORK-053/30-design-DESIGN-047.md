---
id: "DESIGN-047"
type: "design"
title: "修复登录会话期限在数据库往返后的精度不一致"
status: "checked"
work: "WORK-053"
owners: ["codex/root"]
depends_on: ["ISSUE-017"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# DESIGN-047：在签发源统一会话期限精度

## 背景

依据ISSUE-017。持久化层只能存微秒；网关把签发值作为不可改变的固定期限，因此应在签发源统一精度。

## 整体方案

候选：仅在AuthenticationService生成新会话absoluteExpiresAt时截断到ChronoUnit.MICROS，再把该唯一值同时交给sessions.insert和AuthenticationResult。不要全局改变Clock或其他审计时间，不把截断逻辑分散到网关、序列化或SQL。截断最多缩短不足1微秒，不向上舍入延长期限。

## 迁移与兼容

不改DATETIME(6)、迁移、JWT TTL、协议字段、固定时长或网关equals。旧数据库行已是微秒值，无需迁移。旧Redis会话若已保存纳秒值不会自动修正；用户可正常退出后重新登录，本批不清空用户Redis。只在独立CI验证，现有IDEA与服务器不部署。

## 验证设计

先在UserPersistenceIntegrationTests通过实际AuthenticationService签发，使用带非微秒纳秒的固定Clock和真实MySQL，再调用validate/exchange，断言完整期限相等及不延长；禁止测试先主动截断输入或直接insert代替业务。补充AuthenticationServiceTests的精度边界和原有生命周期回归。CI准备阶段显式运行这两类测试并检查Surefire报告的必需方法执行且零skip，不能将-DskipTests打包或disabledWithoutDocker当证据。

测试容器由现有Testcontainers归属清理，只在一次性GitHub VM运行；日志有界，报告仅保留测试名/计数/通过状态，不上传凭据和原始认证响应。精确写边界见TASK-117。原业务15项断言和限额不变。

## 备选方案

扩大网关equals容差会弱化固定期限约束；改数据库精度无法超越MySQL支持范围；在CI放慢/重试登录不能解决数据差异。均不采纳。

## 风险与重审条件

必须先证明旧红新绿；若503分类或数据库实验不支持当前原因，回到归因，不能按猜测改生产。需要修改其他生产类、依赖、Schema、既有会话或预算时先更新边界并重新审核。

## 目标与限制

新会话签发和回读期限完全一致，生产只改一个类，不改网关容差。

## 模块与数据

身份服务负责签发，持久化层继续DATETIME(6)，网关消费同一期限。

## 接口与状态

字段、状态、TTL和会话策略不变，仅统一新期限精度。

## 安全与失败

保留过期、撤销与不延长规则，异常仍失败；禁止重试业务或清空现有会话。

## 监控与部署

仅GitHub独占VM；保存测试和清理的脱敏事实，不部署现有环境。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：方案及精确边界已完整提交审核；补正上轮遗漏的记录类文档就绪状态，不代表人工签署
- 2026-09-11：结构与内容校验通过，由工具置为 checked。

## TASK-117复现批次细化（2026-09-11）

意图闸已由用户签署并明确开始。本地先执行仅AuthenticationServiceTests，真实MySQL测试只在一次性GitHub VM；须先发布测试/CI批次确认旧数据库实现失败，再实施已批准的签发源最小修复。业务准备驱动先clean并执行两类必需测试，避免历史Surefire XML冒充本轮；失败时也导出只含固定测试名与状态的摘要，任意skip/缺项/重复/错误计数拒绝。构建清单携带通过摘要，业务入口再次核验。

MySQL测试容器显式使用1GiB内存、swap0、1CPU、256进程上限和1MiB×2日志上限；测试JVM最大堆512MiB，Maven驱动最大堆768MiB，命令墙钟600秒。均属于新认证回归夹具预算，不改变业务栈、沙箱或安装驱动既有限额。正常释放由现有Testcontainers/Ryuk负责，最终一次性VM销毁兜底；不执行Docker全局prune或连接用户Docker。本地只编译此数据库用例，不把未执行计为通过。
