---
id: "VERIFY-059"
type: "verify"
title: "判题引擎结构重切的回归验证"
status: "draft"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["PLAN-041"]
related: ["CHANGE-014", "DESIGN-051"]
implements: []
verifies: ["CHANGE-014#AC-001", "CHANGE-014#AC-002", "CHANGE-014#AC-003", "CHANGE-014#AC-004", "CHANGE-014#AC-005", "CHANGE-014#AC-006", "CHANGE-014#AC-007", "CHANGE-014#AC-008", "CHANGE-014#AC-009", "CHANGE-014#AC-010", "CHANGE-014#AC-011", "CHANGE-014#AC-012", "CHANGE-014#REQ-016"]
tags: []
result: "pending"
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# VERIFY-059：判题引擎结构重切的回归验证

## 验证对象

`apps/judge-engine` 按 [PLAN-041](50-plan-PLAN-041.md) S1–S7 完成结构重切后的最终候选，与基线
`a611be3`（WORK-049 最终候选）比较。

本文档在实施前只记录验证方案；实际命令、环境与结果在各阶段完成后逐条补入。

## 对应要求

覆盖 [CHANGE-014](10-change-CHANGE-014.md) 的 AC-001 至 AC-012，已锚定进 front matter 的 `verifies`。

| 验收标准 | 验证方式 | 产生证据的阶段 |
|---|---|---|
| AC-001 边界由编译器强制 | 构造跨边界引用，记录 `go build` 失败输出，随后撤销 | S2 |
| AC-002 配置互不牵连 | 两组交叉配置分别启动 judge 与 sandbox | S3 |
| AC-003 指纹输入显式 | 基准测试三种情形的实际输出 | S3 |
| AC-004 终止原因覆盖完整 | 删除任一分支使测试失败 | S1 |
| AC-005 一次性调用完成回收 | 四条路径的 Linux 回归与清理证据 | S4 |
| AC-006 结论可离线验证 | macOS 表驱动测试，与基线行为逐条对照 | S5 |
| AC-007 部署校验双向覆盖 | 两类不一致的实际报错输出 | S6 |
| AC-008 预算冲突拒绝启动 | 人为冲突配置的启动失败输出 | S6 |
| AC-009 错误消息统一英文 | 检查命令输出 + 统一检索表达式验证 | S7 |
| AC-010 对外行为不变 | 与基线比对 + WORK-050 固化 CI 全量 | 全部阶段 |
| AC-011 文档与实现一致 | 按重写后文档实走源码 | S7 |
| AC-012 指纹轮换表现正确 | 节点协议观测：新指纹为 `REGISTERED` | S3 后 |

## 检查与结果

尚未执行。每阶段完成后在此追加：阶段、日期、环境（操作系统、架构、Go 版本、CI 运行编号）、
实际命令、实际输出摘要、结论。

固定要求：

- `gofmt -l .`、`go vet ./...`、`go test -race ./...` 三项在每个阶段都必须记录。
- Linux 隔离、资源计量与故障回收回归以 WORK-050 固化的 CI 为准，记录运行编号与 job 通过情况。
- 未执行、跳过与失败分别记录，不互相替代；在非 Linux 平台跳过的项目必须写明跳过原因与补测计划。

## 未通过项

暂无。

## 范围检查

待补充：确认改动只落在 `apps/judge-engine`、`docs/engine.md` 与
`docs/coding-standards/languages/go.md`；确认 `contracts/`、`apps/server`、`apps/web`、`deploy/`、
`.github/`、`go.mod`、`go.sum` 未被修改；确认 WORK-049 与 WORK-050 的既有增量未被覆盖。

## 遗留问题

暂无。

## 剩余风险

待补充。实施前已知的剩余风险见 [DESIGN-051](30-design-DESIGN-051.md) 「风险与重审条件」与
[PLAN-041](50-plan-PLAN-041.md) 「风险」，验证完成后在此更新为实际剩余项。

需要注意的一项：本工作完成后，`ACTIVE` 环境的切换仍是人工动作，不在本次验证范围内，但在它完成
之前新结构不参与实际判题路由。

## 结论

尚未验证。
