---
id: "VERIFY-057"
type: "verify"
title: "固定沙箱软件包来源与长期留存"
status: "draft"
work: "WORK-056"
owners: ["team/judge-engine"]
depends_on: ["TASK-121", "TASK-122", "TASK-123"]
related: []
implements: []
verifies: ["IMPROVEMENT-006#AC-001", "IMPROVEMENT-006#AC-002", "IMPROVEMENT-006#AC-003", "TASK-121"]
tags: []
result: "pending"
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# VERIFY-057：固定沙箱软件包来源与长期留存

## 验证对象

WORK-056 的固定快照恢复、资产发布和日常 CI 接入。

## 对应要求

AC-001 对应 56 包与 rootfs；AC-002 对应资产身份、许可及安全；AC-003 对应冷热 Linux CI 与实际消费者。

## 检查与结果

2026-09-12：方案阶段已有三包本地只读获取证据，详见 DESIGN-050；全量包、Linux 快照恢复、资产发布及冷热 CI 均未执行，不记为通过。

## 未通过项

TASK-121 的 Linux 获取及 rootfs 清单已通过；资产发布及冷热全链 CI 未完成，result 保持 pending。日常 CI 的 Java 持久化测试失败，见末节。

## 范围检查

已在 TASK-121 边界内修改 CI 获取与测试说明；未修改包锁、默认 rootfs 下载/构建器、工作流或发布资产。

## 遗留问题

WORK-050 内核采样与 business.io 的历史失败留在原工作范围；本工作不修改判题行为以使其通过。

## 剩余风险

完整包集、源码交付与固定资产能力尚未实测。GitHub 或官方源仍有外部可用性风险。

## 结论

意图闸已签署，TASK-121 独立复核、本地及 Linux 获取/rootfs 验证完成。TASK-122/123 尚未完成，日常 CI 仍有独立 Java 测试失败，不能签署验收闸。

## TASK-121 本地实施证据（2026-09-12）

命令：`PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work056-basic`。
136 项单测通过（15 安装 + 27 rootfs + 94 CI），Python AST 71 文件、shell 4 文件通过。报告及日志在上述目录。
最初普通权限测试因本机禁止绑定回环端口而未通过；获得工具执行权限后完整回归通过，未跳过 TLS 负向用例。

命令：`python3 deploy/sandbox-linux/ci/packages.py --lock deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json --output /private/tmp/cherry-work056-snapshot/packages`。
macOS 本地空目录实取 56 包、77,897,048 bytes，64.417629 秒；58 请求（2 索引 + 56 包）均首次 HTTP 200 / curl 0。
acquisition SHA256 `0fb93a421983cb464cc9ec23ee623edd245401edad3d23dd4ae3484e73cb3035`；原锁摘要仍为 DESIGN-050 基线。
日志与额外逐包重算摘要结果见 `/private/tmp/cherry-work056-snapshot/download.log`、同目录 `evidence.json`；获取临时目录已回收，保留已核验包作为本轮证据。
执行时 HEAD 为 `2f499714e2fa0927798928603d6dbdd99da9d97b`，本批代码未提交，日志 scriptsSha 标识实际获取脚本；不能把 HEAD 当作本批实现的提交身份。

本机没有 dpkg-deb，未构建 rootfs，也未运行 GitHub Linux；AC-001 仅完成本地部分。独立复核与发布批次授权尚未完成。
工作工具 refresh 试图把 WORK 推进 doing 时因后续依赖任务尚在 todo 而被“实施任务”阶段阻止，未强行修改控制面；当前 TASK-121 doing、WORK todo，work check 通过。

- 2026-09-12：本批复核和提交推送/CI 授权已获得，开始发布前独立复核。

## TASK-121 发布前独立复核

2026-09-12：用户明确授权后，work056_review 只读复核固定 HTTPS 域、同日期索引/包、原锁绑定、缓存身份、取消回收及任务边界，无阻塞发现；独立运行 4 项针对性单测通过。建议将本地输入 source 标签恢复 existing，避免将当轮 artifact 误报为缓存命中，已修正。修正后包测试 22 项通过。此结论仅覆盖 TASK-121 增量，不替代尚未执行的 Linux 验证或后续资产复核。

## TASK-121 Linux 发布验证

已授权提交推送 `f791b85671ee96bef5554695947627af0cb74361`。
[独立冷检查 34694917091](https://github.com/charon2121/cherry-oj/actions/runs/34694917091) success：56/56 包、77,897,048 bytes、58 请求全部首次 HTTP 200，15.870049639 秒完成；rootfs manifest 摘要 `ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0` 与基线完全一致。
[日常 CI 34694917085](https://github.com/charon2121/cherry-oj/actions/runs/34694917085) 包准备 cacheHit=false：同样 56/56、58 请求首次成功，15.460979249 秒。主 CI 的三个消费者仍在运行，后续结果单独记录。
两份 identity 的 sourceSha 均为上述提交，harnessSha 均为 `3cd7768c4447b1300044cc51f081c81585a1b9cfc693a5f0e8f719bb292a6fc3`，原锁与 acquisition 摘要保持不变。证据目录 `/private/tmp/cherry-work056-34694917091`、`/private/tmp/cherry-work056-34694917085-packages`。
work056_review 独立核对日志与原锁后确认上述事实；不据此声明业务全绿或固定资产/缓存命中验证完成。AC-001 已获本批 Linux 实测支持，AC-002/003 尚未完成。

## 本批完整 CI 最终结果

日常 CI 34694917085 最终 completed/failure，11 个 job 中 10 成功、1 失败。内核报告 63/63 PASS，原生部署 10/10 PASS，基础报告 5/5 PASS（单测 136 项）；三份报告均经本地 validate/verify_files 检查，源码/harness 与本次提交一致，cleanup PASS。下载日志、内核及原生报告支持 TASK-121 完成，不支持整体验收。

业务 job 在 business_prepare.py 的认证回归阶段失败：`UserPersistenceIntegrationTests.authenticatedDeadlineSurvivesMysqlRoundTripAtNanosecondPrecision:190` 调用 AuthenticationService.authenticate 更新 user_account 时，MySQL 拒绝 `ck_user_account_time_order`。8 项测试、1 error、0 skipped；Maven 在 53.711 秒后返回失败。本轮没有 Chromium/页面/Kafka 业务场景结果，缺少业务报告产物是前置失败的后果，不把它当独立判题失败。

这既不是快照包下载失败，也不是历史 business.io 断言；目前仅定位到约束违反，尚未审读应用代码确认根因。apps 属 TASK-121 forbidden_paths，未修改或跳过应用测试、未放宽数据库约束。后续修复须归属适当应用任务并先明确读写边界。当前不以重跑获得一次绿色覆盖本次失败，也不在红色 CI 基础上追加纯记录提交。

证据：`/private/tmp/cherry-work056-ci-final.json`、`/private/tmp/cherry-work056-business-failed.log`、`/private/tmp/cherry-work056-34694917085-business-build/business-build/authentication-tests.log`，以及 `/private/tmp/cherry-work056-34694917085-sandbox-{kernel,native,basic}`。测试数据 ZIP 未提交或删除。
