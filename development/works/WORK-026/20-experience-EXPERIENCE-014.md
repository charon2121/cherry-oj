---
id: "EXPERIENCE-014"
type: "experience"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "approved"
work: "WORK-026"
owners: ["codex/root"]
depends_on: ["CAPABILITY-007"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-31"
updated_at: "2026-08-31"
---


# EXPERIENCE-014：为 Java 服务提供可直接启动的本地默认配置

## 体验类型

开发体验与运维要求；不改变终端用户界面。

## 入口与主流程

开发者先按项目说明准备本地 MySQL schema/账号和 Redis，然后进入 `apps/server`，直接执行
`./mvnw -pl <service> spring-boot:run`。五个服务均不要求当前 shell 预先存在 `CHERRY_*` 变量。显式
变量仍用于连接非默认地址、账号或稳定密钥。

## 异常状态

- 数据库或 Redis 未运行：保留清楚的连接失败，不伪装为配置缺失。
- 本地数据库账号与约定不一致：日志指出认证失败，开发者设置相应覆盖或校正本地账号。
- user-service 本地临时密钥：启动日志只说明“临时密钥，仅限本地”，不输出密钥材料；重启后旧 JWT
  失效属于预期。
- production profile 缺少凭据/密钥：启动立即失败并点明缺少的属性，不自动降级。
- 显式配置不可读或格式错误：保留 fail-fast，不能因存在默认值而吞掉错误。

## 交互与文案

README 用一段主流程解释“默认配置只省去 export，基础设施仍需先准备”，再列出覆盖示例和生产要求。
错误信息使用属性名而不是泄露属性值；不得把数据库密码、私钥内容或 JWT 打进日志。

## 可访问性

不涉及图形界面、键盘或移动端。终端说明保持可复制命令与纯文本错误，不依赖颜色表达成败。

## 调试与恢复

开发者先根据错误区分基础设施不可达、账号不匹配和 production Secret 缺失，再通过原有环境变量覆盖。
可用 Spring 属性报告/测试确认来源，但调试输出必须遮蔽 Secret。出现回归时回退配置和临时密钥分支，
恢复原有显式 export 行为，无数据迁移需要撤销。

## 变更记录

- 2026-08-31：状态进入 review；明确直接启动、覆盖、故障定位和生产拒绝降级体验，提交人工审核。
- 2026-08-31：状态变更：review → approved。原因：负责人确认零 export 本地启动与生产 fail-closed 体验，并允许实施
