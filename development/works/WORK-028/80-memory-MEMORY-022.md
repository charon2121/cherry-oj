---
id: "MEMORY-022"
type: "memory"
title: "修复后台用户列表偶发误跳登录页"
status: "checked"
work: "WORK-028"
owners: ["codex/root"]
depends_on: ["VERIFY-029"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# MEMORY-022：修复后台用户列表偶发误跳登录页

## 背景

WORK-013 把浏览器 Session、user-service 登录授权和内部 JWT 定义成三个对象。ISSUE-006 暴露了一个
容易复发的边界错误：资源服务拒绝短 JWT 不自动等于浏览器 Session 失效。

## 决定与原因

Gateway 用服务器持有的 login grant 对 Admin 用户 GET 的资源 401 做一次强制交换和单次重试。资源 401
只说明当前短 JWT 被拒绝；只有 exchange 401 才证明登录授权失效并清 Session。exchange 临时失败或新 token
仍被拒绝时返回 5xx、保留 Session，避免浏览器把身份基础设施不一致误报成退出。

常规 touch 与强制 exchange 共用按 Session ID 的协调槽但携带操作类型：exchange 遇到进行中的 touch 会先
应用其结果再执行 exchange；每个参与请求都把共享结果写回自己的 WebSession。写请求不自动重放。

## 尝试与教训

仅在 Web 二次查询 Session 会掩盖跳转但无法恢复资源请求；只持久化本地临时密钥只覆盖一种触发源。
Reactive Redis Session 并发请求可持有不同对象，共享 Mono 的结果若只写发起者对象，旧状态仍可能覆盖
新 token。TASK-046 的并发用例证明必须逐实例应用共享结果，并阻止晚到 touch 覆盖更新后的 access token。

共享 exchange 的上游 401 不能只在发起者内部清理 Session，否则其它参与请求可能把旧状态重新保存；应先
把结果分类为 `UNAUTHENTICATED`，再由每个参与请求分别失效自己的 WebSession。

## 已知问题

Admin problems 的资源 401 当前收敛为 502，不会触发浏览器退出，但也不会自动恢复短 token；其它新增资源
BFF 必须显式决定 GET 恢复和写请求幂等策略。Admin 用户写操作本次不自动重放，短 token 失配时仍可能让
该次操作失败。多实例 Gateway 只有进程内单飞，严格全局单飞不在本次承诺内。

## 重新考虑条件

内部 JWT 改为 introspection/外部 IdP、Gateway 不再保存 login grant、恢复扩展到非幂等写操作，或部署
要求跨 Gateway 实例严格单飞时，重新评审。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已沉淀资源 401 与浏览器 Session 的边界、并发回写教训、写请求限制和重审条件
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
