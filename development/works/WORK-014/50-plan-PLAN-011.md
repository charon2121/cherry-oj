---
id: "PLAN-011"
type: "plan"
title: "统一登录空闲过期配置并修复提前掉线"
status: "approved"
work: "WORK-014"
owners: ["codex/root"]
depends_on: ["ISSUE-002", "DESIGN-011", "DECISION-010"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# PLAN-011：统一登录空闲过期配置并修复提前掉线

## 目标

按 DECISION-010 让 IDLE、绝对上限与 IDLE 刷新策略成为三个秒/布尔部署配置，消除 Gateway 期限
硬编码，证明 120 秒内部 JWT 到期不会要求用户重新登录，并保持 WORK-013 的撤销不变量。

## 改动区域

- Gateway Session 配置、配置属性与 Redis/认证服务测试。
- user-service application 配置、Duration 校验与登录授权时间测试。
- 本 WORK 的 TASK、VERIFY 与 MEMORY；无需修改公开 OpenAPI、数据库 migration 或 Web 页面。

## 阶段与顺序

1. 先用测试钉住默认 1800/43200 秒、刷新 true/false、120 秒 token 刷新和时间边界。
2. 在两个 application 配置中显式读取三个环境变量，以整数/布尔绑定并校验。
3. 用 reactive repository customizer 替换期限硬编码，补充 user-service 授权 touch/条件续期，验证真实
   Redis TTL 与 MySQL 截止时间。
4. 运行两个模块与 Java 聚合回归，记录精确复现结果和剩余环境风险。

## 并行与依赖

Gateway 与 user-service 修改需要遵循同一配置决定；测试可以分别实现，但最终必须在同一组配置值下
联合验证。不能先上线只改一侧的版本。

## 迁移与上线

代码本身无数据迁移。发布时先保持变量缺省，确认兼容的 1800/43200 秒与刷新开启；再同时向两个服务
设置目标值并重启。已有数据库授权保留原绝对期限；IDLE 是否续期按新开关执行。已有 Redis Session 的
行为必须在测试环境观察。生产发布与观察仍需目标环境授权。

## 风险

最大风险是看似统一但实际只向一个进程注入变量，以及测试只检查配置对象、没有检查 Redis/数据库中的
实际期限。另需防止为了避免掉线而引入前端心跳，破坏空闲退出的安全含义。

## 验证

执行 Gateway 与 user-service 模块测试、真实 Redis/MySQL 时间边界集成测试、Java `clean verify`、
`scripts/work check` 和 `git diff --check`。用固定时钟证明 `< idle`、`= idle`、`> idle`、`= absolute`，
刷新 true/false，跨 120 秒 JWT 到期，以及退出/改密/停用的回归矩阵。

## 回退

恢复注解 1800 秒、原 43200 秒绝对上限和现有滑动行为，移除新增变量引用、开关和 customizer；数据库
无 schema 回退。
若新值已经延长 Session，回退发布可选择清理测试 Redis namespace，生产是否清理必须由负责人决定。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：修改范围、实施顺序、真实时间边界验证和回退步骤已就绪，等待上游决定批准
- 2026-08-27：状态变更：review → approved。原因：负责人确认实施顺序、测试矩阵、上线与回退边界，并允许开始 TASK-020
