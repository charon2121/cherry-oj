---
id: "MEMORY-031"
type: "memory"
title: "建立题目阅读与 Monaco 编码工作台"
status: "checked"
work: "WORK-041"
owners: ["codex/root"]
depends_on: ["VERIFY-042"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# MEMORY-031：建立题目阅读与 Monaco 编码工作台

## 背景

题目详情的登录链接原为固定占位，页面没有读取会话；不能据此推断认证系统失效。

## 决定与原因

用户确认先交付前端页面与本机草稿，运行/提交标示未开放。用户端按明确要求使用 Monaco，后台仍使用
CodeMirror，触控手机复用后者。C++ 语法直接来自发布版本的起始代码与官方 grammar，不带题解或 LSP。

## 尝试与教训

- localStorage 的“先读 revision 再写”不是 CAS。独立 writer 恢复副本保留并发分支，事件只作通知，
  用户显式选择后才合并；存储错误不能伪装已保存。
- Monaco 0.56 子入口与旧版本文档不同，必须检查锁定包的 exports。C++ 二次 lazy loader 的拒绝
  Promise 不能复位，改为注册已在异步 runtime 内加载的 grammar；原生模块网络失败则提供刷新恢复。
- Playwright 宿主是 Mac，但 Desktop Chrome fixture 的 UA 是 Windows。Monaco 与 CodeMirror 使用
  不同平台判据，测试快捷键必须跟浏览器编辑器的实际判据一致，不能改产品绑定来迎合测试环境。
- 缩放检查不能只断言元素“可见”：初始640×360虽能输入，编辑区只剩10px。加入可读高度与滚动访问
  断言，短视口保留编辑高度后允许代码面板滚动。
- 中文候选输入期间不能发草稿保存。DOM composition 和原生 EditContext 都要在组合完成后报告一次。

## 已知问题

Monaco 首载体积、仅本浏览器草稿和加载失败可能需要完整刷新是已知边界，具体证据见 VERIFY-042。
人工验收完成前不宣称 WORK-041 已 verified。

## 重新考虑条件

接入真实运行/提交、云端草稿、其他语言或 CORE 时重新确认对应产品规则和任务边界。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：记忆正文已完成，VERIFY尚待人工验收，上游通过后再进入checked
- 2026-09-07：结构与内容校验通过，由工具置为 checked。
