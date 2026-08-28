---
id: "VERIFY-016"
type: "verify"
title: "修复设计系统发布后的文档 CI"
status: "draft"
work: "WORK-016"
owners: ["codex/root"]
depends_on: ["TASK-022"]
related: ["VERIFY-015"]
implements: []
verifies: ["ISSUE-003", "TASK-022"]
tags: []
result: "pending"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# VERIFY-016：修复设计系统发布后的文档 CI

## 验证对象

验证 TASK-022 对误收录角色文件、5 个失效链接和 WORK-015 过期验证陈述的修复，不重新验证或改变
已经批准的主题视觉方案。

## 对应要求

覆盖 ISSUE-003 的 AC-001～AC-005 和 TASK-022 全部完成标准。

## 检查与结果

2026-08-28 在 macOS 本地工作区对实际 Git index 运行：

```text
python3 scripts/work_test.py
scripts/work check
python3 scripts/docs_test_test.py
python3 scripts/docs_test.py
node docs/design-system/tools/build.mjs --check
node docs/design-system/tools/check.mjs
git diff --cached --check
```

全部退出码为 0：work 工具 21 个测试通过；123 份开发文档通过校验且无进行中提示；链接校验器 3 个
测试通过；156 份 Markdown 文档入口和本地链接有效。设计系统派生产物仍为 current，两个主题各完整
实现 56 个 key，296 个允许对比组合继续通过；最低值仍为 pure-white 的 3.404:1 必要边界组合。

## 未通过项

本地没有未通过项。远端 GitHub Actions 尚未触发，不能提前记录为通过。

## 范围检查

`git diff --name-status` 与暂存差异确认修复只涉及 TASK-022 的 write paths：删除两份角色 JSON，修改
四份全局文档、WORK-015 的两份记录和 WORK-016 文档。CI、校验器、应用代码、公共契约、设计系统包
及三个已删除旧 HTML 均未修改或恢复。

## 遗留问题

暂无；实施中若发现角色文件存在真实消费者或旧页面包含没有迁移的唯一信息，必须在此记录并停止。

## 剩余风险

剩余风险仅是远端 clean checkout 与本地环境存在差异；GitHub CI 未通过前不能把问题标记为验证完成。

## 结论

本地修复与回归检查通过，等待提交推送和远端 CI；当前保持 `result: pending`。
