---
id: "MEMORY-024"
type: "memory"
title: "修复后台题目列表间歇性 502"
status: "checked"
work: "WORK-030"
owners: ["codex/root"]
depends_on: ["VERIFY-031"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# MEMORY-024：修复后台题目列表间歇性 502

## 背景

WORK-028 已确定资源服务拒绝短 JWT 不等于浏览器 Session 失效，但当时只接入 Admin 用户列表，并明确
留下 Admin problems 401 被压成 502 的已知问题。ISSUE-007 用真实 requestId 证实该缺口已影响题目维护，
并暴露资源服务长期运行与签名密钥重启/轮换的诊断盲区。

## 决定与原因

Gateway 以 `AdminGatewayAccess.readWithRecovery` 共享普通 JSON GET 的一次恢复：只有下游明确返回 401
才强制 exchange；fresh token 只重试一次。403、解码错误、其它 4xx/5xx、写请求、上传和流式下载都不
恢复。这样既补齐 Admin problems 与 Admin users 的行为差异，也不把幂等读取方案误用到可能产生副作用的
请求。

受控测试显示当前 Nimbus decoder 在 K1 预热、发布 K2、16 路并发验证 K2 时全部成功且只刷新一次
JWKS；K2+K1 重叠集合也继续接受 K1。因此没有失败证据支持修改生产 JWKS cache/refresh，保留现状，仅
增加轮换回归与安全失败观测。

## 尝试与教训

502 `BAD_GATEWAY` 在 problem BFF 中同时可能代表下游 401/403、响应解码异常或未知错误，不能仅凭公开
响应判断根因。Security Filter 提前拒绝又不出现在现有 `http.server.completed` 日志，因此必须结合
Gateway、user-service、JWKS 拉取和资源服务日志，或补充安全失败事件。

重启 problem-service 能暂时改变缓存状态，但不构成修复证据。fresh token 与旧 token、K2 已发布与旧 K1
已移除、JWKS outage 下已知 key 与未知 key 都必须分别测试。

当前 Nimbus 配置已协调同进程并发未知 `kid` 的刷新；现场“JWKS 拉取 200 后仍混合 502”不能归因于一个
未复现的缓存竞态。已确认、可直接修复的原因是 Admin problems 缺少资源 401 恢复，而公开错误又把 401、
403 与解码失败都收敛为 502。先分类真实下游状态，再决定恢复，比按公开 502 猜测更可靠。

安全 Filter 失败发生在 Controller 之前，必须有专门事件补齐业务完成日志盲区。事件只记录格式受控的
requestId、失败分类和恢复结果；异常消息、token、grant、用户和密钥材料都不应进入日志。

## 已知问题

写请求、上传和流式下载不自动恢复；遇到内部 token 拒绝仍需显式重试。多实例 Gateway 仅进程内单飞，
不能保证多个实例只 exchange 一次。本地临时 RSA 密钥重启必然丢失上一 key；正式环境仍需稳定 PEM、
可控轮换和旧公钥重叠窗口。自动化通过说明当前受控 Nimbus 场景正确，不覆盖所有 IdP 与网络故障组合。

## 重新考虑条件

JWT 改为 introspection/外部 IdP、Gateway 不再保存 login grant、需要恢复非幂等写或流式下载、生产不再
保留前一公钥重叠窗口、或要求跨 Gateway 实例全局单飞时重新评审。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已沉淀未复现 Nimbus 缓存缺陷、共享只读恢复边界、安全观测与部署剩余风险
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
