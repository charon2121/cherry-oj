---
id: "EXPERIENCE-002"
type: "experience"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "approved"
work: "WORK-008"
owners: ["codex/root"]
depends_on: ["CAPABILITY-001"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# EXPERIENCE-002：建立 Web 到 Gateway 的 REST 基础连通模块

## 体验类型

以开发与本地联调体验为主，同时首页给使用者一个克制的连通状态反馈。

## 入口与主流程

开发者分别启动 Gateway 与 Web，访问首页。页面立即请求 `/api/status`：请求中显示“正在连接
Gateway”，响应通过校验后显示“REST API 已连通”以及服务名。命令行可直接请求同一 URI 对照排查。

## 异常状态

单例状态资源没有 empty、unauthorized 或提交中状态。加载时展示非阻塞状态；网络、HTTP、JSON 或
契约错误统一展示“REST API 暂时不可用”，保留错误摘要和“重新连接”按钮。成功后 Query 按默认
策略维护服务端状态，不把它复制进局部 state。

## 交互与文案

首页主标题保持“Cherry OJ 前端骨架已就绪”，状态区域只说明连通性，不使用“系统健康”“全部服务
正常”等会夸大范围的文案。失败按钮只触发同一幂等查询，不刷新整个页面。

## 可访问性

加载信息使用 `role=status`，错误使用 `role=alert`，重试使用原生 Button，可通过键盘操作。成功与
失败结论均有文字，不只依赖颜色；窄屏下状态块自然换行，不增加横向滚动。

## 调试与恢复

先用 `curl http://127.0.0.1:8080/api/status` 区分 Gateway 未启动与浏览器代理问题，再检查浏览器
Network 中的状态码和响应体。回退代码后首页恢复静态占位，不涉及数据恢复。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：联调主流程、错误恢复和可访问状态已明确
