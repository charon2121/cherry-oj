---
id: "MEMORY-030"
type: "memory"
title: "判题可用性必须由真实节点事实驱动"
status: "checked"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["VERIFY-041"]
related: ["DECISION-022"]
implements: []
verifies: []
tags: []
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# MEMORY-030：判题可用性必须由真实节点事实驱动

## 背景

首版用环境 seed 和共享文件目录完成了单机功能，但它们不能证明节点在线或数据已被节点实际安装。

## 决定与原因

环境是兼容性分组，节点是带租约的运行实例，部署是逐节点回执。readiness 与调度必须同时核对三者；
数据库里存在环境行或本地目录都不能单独代表可判题。

## 尝试与教训

默认启用 dev profile 只能消除 `ACTIVE_ENVIRONMENT_MISSING`，随后仍会遇到 Java 写入路径与 Go 容器挂载
不一致。强制共享 volume 也无法覆盖远程节点和扩缩容。未来不得再用 seed、健康端口或静态 endpoint
替代真实注册/租约。

## 已知问题

本次验证已确认：V1→V2 保留原环境、部署、标定与审计事实；切换只新增节点事实，旧 READY 不自动等同于节点持有数据。

- 节点身份还必须绑定进程会话：记录已接受会话，拒绝旧进程重新注册抢回 nodeId。
- 安装拒绝会使旧回执失效，但必须用修订号进行条件更新，避免旧失败覆盖较新的成功。
- 指纹不能使用 CPU/kernel/toolchain 占位值，也不能只靠人工版本标签；应探测实际 sandbox，纳入两端二进制与资源策略。
- 环境升级同时改变 nodeId 与私有卷，保留旧卷回退。sandbox 升级要同步重启 Judge 重新探测。
- 发布检查的新增节点项必须穿过 problem-service、OpenAPI 和 Web；仅后端 readiness 增项不会自动对用户生效。
- 既有 READY_FOR_REVIEW/PUBLISHED 状态规则保持：完成旧环境发布后，通过新草稿修订在新环境校准，不能改写已发布快照。

## 重新考虑条件

节点规模或跨机房流量需要对象存储，或平台已有统一服务发现时，可以替换传输/发现实现，但仍保留真实
在线事实和逐节点数据回执。

## 变更记录

- 2026-09-06：状态变更：draft → review。原因：已沉淀节点会话、回执修订、实际环境指纹与新卷迁移的验证结论
- 2026-09-06：结构与内容校验通过，由工具置为 checked。
