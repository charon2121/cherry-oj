---
id: "VERIFY-021"
type: "verify"
title: "修复 IDEA 错误按叶子工程构建 user-service"
status: "draft"
work: "WORK-021"
owners: ["codex/root"]
depends_on: ["TASK-029"]
related: []
implements: []
verifies: ["ISSUE-004", "TASK-029"]
tags: []
result: "pending"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# VERIFY-021：修复 IDEA 错误按叶子工程构建 user-service

## 验证对象

验证 IDEA 共享启动配置和 Maven 根 reactor 构建说明是否消除 ISSUE-004 的
`logging-support:0.0.1-SNAPSHOT` 解析失败，同时保证后端现有构建与运行边界不变。

## 对应要求

覆盖 ISSUE-004 的 AC-001～AC-005，以及 TASK-029 的全部完成标准。

## 检查与结果

等待 TASK-029 获批并实施后，记录实际 IDEA 版本/JDK、根 POM 导入结果、共享启动项行为、Maven
reactor 顺序、局部/全量构建结果和必要的运行基础设施条件；不能在实施前预填通过。

## 未通过项

暂无。

## 范围检查

等待实施后核对 `git diff --name-only` 与 TASK-029 的 write paths，并确认 POM、服务/共享库源码、契约、
数据库与其他技术栈没有变化。

## 遗留问题

暂无。

## 剩余风险

共享 IDEA 配置只能约束仓库提供的入口，不能阻止开发者手工新建一个仍以叶子 POM 为根的 Maven 配置；
文档和失败解释必须保留这一边界。实际 IDEA 启动仍需要用户服务原有的数据库与密钥等运行条件。

## 结论

尚未验证。
