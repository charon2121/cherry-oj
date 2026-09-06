---
id: "VERIFY-039"
type: "verify"
title: "兼容常见测试数据 ZIP 并返回可操作校验错误"
status: "review"
work: "WORK-038"
owners: ["codex/root"]
depends_on: ["TASK-069"]
related: []
implements: []
verifies: ["ISSUE-010#AC-001", "ISSUE-010#AC-002", "ISSUE-010#AC-003", "ISSUE-010#AC-004", "ISSUE-010#AC-005", "ISSUE-010#AC-006", "TASK-069"]
tags: []
result: "pass"
created_at: "2026-09-05"
updated_at: "2026-09-05"
---

# VERIFY-039：兼容常见测试数据 ZIP 并返回可操作校验错误

## 验证对象

验证常见桌面工具生成的包装目录 ZIP 能被安全归一化，并从 problem-service 原包资产、逻辑 manifest
一直到 judging-service 平面部署保持一致；同时验证非法结构仍拒绝、Gateway 错误可操作。

## 对应要求

覆盖 ISSUE-010#AC-001～AC-006：真实问题包、两种逻辑根、macOS 元数据、恶意/歧义输入、可操作错误、
兼容性和聚合回归。

## 检查与结果

- 真实问题证据：`unzip -l /Users/charon/Downloads/testin.zip` 显示 `testin/`、
  `testin/.DS_Store`、`__MACOSX/testin/._.DS_Store`、`testin/test1.in`、`testin/test1.out`；本机
  problem 数据库中对应 FAILED 行为 `TEST_DATA_INVALID_ZIP_ENTRY`。
- problem-service 定向测试：
  `./mvnw -pl problem-service -am -Dtest=FileTestDataAssetStoreTests -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，5 个用例、0 失败。Finder 等价 fixture 的 manifest 只有 `test1.in/test1.out`，原 ZIP 读取字节
  与上传字节完全相同；多根、混合层级、多层目录、未知隐藏文件和元数据条目数限额均有反例。
- judging-service 定向测试：
  `./mvnw -pl judging-service -am -Dtest=FileTestDataDeploymentStoreTests -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，7 个用例、0 失败。相同 Finder 结构按逻辑名核对 manifest，最终目录只含平面
  `test1.in/test1.out`；元数据不落盘且仍计入 entry 上限。
- Gateway 定向测试：
  `./mvnw -pl gateway-service -am -Dtest=ProblemApiErrorsTests,ProblemServiceStreamingTests -Dsurefire.failIfNoSpecifiedTests=false test`
  通过，5 个用例、0 失败。安全 detail 可保留，含控制字符的 detail 被丢弃，非 allowlist code 仍映射
  为 BAD_GATEWAY 且不泄漏上游正文。
- `./mvnw clean verify`：8 个 reactor 模块全部成功，总耗时 1 分 36 秒；49 个 test suite 共 142 个测试，
  0 failure、0 error，1 个仅 Linux 支持的 `RealLinuxJudgeIntegrationTests` 按 macOS 平台跳过。真实
  MySQL 8.4/Flyway、Redis、multipart 上传、问题数据持久化与 judging 部署回归均执行。
- `jq empty contracts/web-api.openapi.json`、`git diff --check`、`scripts/work check`：通过。

## 未通过项

无。本次开发中先后出现的 Maven 单模块依赖入口、lambda effectively-final 和测试断言枚举别名问题均已
在最终验证前修正，不属于遗留失败。

## 范围检查

实际修改仅落在 TASK-069 允许的 contracts、problem/judging 存储与测试、Gateway problem client/错误
映射及 WORK-038 文档；未修改 Web、user/submission service、数据库迁移或 judge-engine。OpenAPI 描述
与两个校验器都使用“平面或单外层目录、明确 macOS 元数据忽略”的同一语义。

## 遗留问题

当前运行中的本地 Java 进程仍是修改前构建，实际页面复测前需要重启 judging-service、problem-service
和 Gateway。无需数据迁移；现有平面 ZIP 与 READY 数据保持兼容。

## 剩余风险

不会接受多个外层目录、测例多层目录或任意隐藏文件，这是刻意保留的边界。若未来测试数据需要资源目录
或 checker 文件，应升级 manifest 契约，不继续扩大忽略名单。

## 结论

通过。实际失败结构已被等价 fixture 覆盖，上传端和部署端得到一致的逻辑文件集合，原包不变，原有
恶意 ZIP/限额防线及 Gateway 上游错误隔离均保持。

## 变更记录

- 2026-09-05：状态变更：draft → review。原因：定向测试与 8 模块 clean verify 全部通过，验证证据和剩余边界已记录，提交负责人验收
