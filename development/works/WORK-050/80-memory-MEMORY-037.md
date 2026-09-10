---
id: "MEMORY-037"
type: "memory"
title: "将沙箱已验收回归固化为重构 CI"
status: "review"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["VERIFY-051"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# MEMORY-037：回归自动化的边界

## 背景

WORK-048已验收并推送，用户要求先固化这些测试再重构judge-engine。

## 决定与原因

提议一次性GitHub Ubuntu VM分层运行，现有服务器和IDEA保持独立；方案待审核。

## 尝试与教训

普通Go测试可跳过内核用例，trusted-host容器冒烟和模拟浏览器响应不能证明真实隔离业务链。旧手工脚本包含固定端口、UID与业务ID，不能直接批量执行便宣称自动化完成。

## 已知问题

尚无本工作新CI执行证据。必须用清单和报告检测缺用例/跳过/取消/清理失败，重构身份改变仍须重新注册校准；机器重启继续留置。

## 重新考虑条件

托管平台缺能力、业务资源不足、执行策略变化或目标平台增加时重审方案。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已盘点既有验收与CI缺口，补齐分层方案、边界及验收条件供人工审核；尚未实施
