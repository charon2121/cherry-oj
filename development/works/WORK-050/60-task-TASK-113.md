---
id: "TASK-113"
type: "task"
title: "验证CI汇总失败处理并交付重构基线"
status: "todo"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-112"]
related: []
implements: ["CAPABILITY-008#REQ-001", "CAPABILITY-008#REQ-005", "CAPABILITY-008#REQ-006", "CAPABILITY-008#AC-001", "CAPABILITY-008#AC-005", "CAPABILITY-008#AC-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "development/works/WORK-050", "docs/engineering", ".github/workflows", "apps/judge-engine", "deploy/sandbox-linux", "contracts", "apps/web/e2e-live", "apps/web/playwright.live.config.ts"]
write_paths: ["development/works/WORK-050", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "development/works/WORK-049"]
forbidden_paths: ["apps/judge-engine/internal", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "contracts", "compose.yaml", "compose.legacy.yaml", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md", "apps/server", "apps/web"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-113：验证CI汇总失败处理并交付重构基线

## 任务目标

证明CI不会缺测假绿，将完整两次基线和稳定回归契约交给WORK-049。

## 依据

CAPABILITY-008 REQ-001/005/006与AC-001/005/006；依赖109～112实际交付。

## 可查看范围

以 front matter 的 read_paths 为准；已有实现只读用于核对不变量。

## 可修改范围

以 write_paths 为准。仅测试编排、工作流、报告和用例适配，不实现另一套沙箱或业务服务。远端动作仅指审核后的一次性 GitHub VM，不能连接现有服务器或 IDEA。

## 禁止修改

以 forbidden_paths 为准。禁止修改生产代码、协议、限额/权限来迁就测试；禁止使用旧私有配置、数据和凭据；不得清理未知所有权资源。发现真实缺陷先记录并另拆修复边界。

## 依赖

依 depends_on 顺序推进；本轮只是文档，需人工意图闸及后续实施授权，当前 todo 不可直接执行。

## 产出

ci.yml必需汇总、清单完整性/取消测试、脱敏产物策略、VERIFY-051逐AC证据与WORK-049交接引用。GitHub分支保护只给建议和核验，不自动改设置。

## 完成标准

- [ ] 所有必需job成功且同SHA报告完整、每case唯一且执行、清理成功才通过；独立复核/重启不冒充PASS。
- [ ] 可控断言失败、缺case/报告、能力缺失、取消分别使汇总不成功；失败证据不被重跑覆盖。
- [ ] 未参与实现者核对工作流权限、公开PR隔离、产物脱敏和资源回收，无未解决阻断；委派须符合现有授权规则。
- [ ] 当前代码连续两次完整自动运行通过，至少一次冷缓存；归档源码/harness/环境/工具链摘要和实际运行链接。
- [ ] WORK-049记录基线和同清单验证入口，源码重构开始依赖此结果，最终候选提交重新全量通过后才能声称回归无问题。

## 验证

本地报告/汇总单测加实际Actions负例与取消实验，然后两次完整成功运行；不以重新执行某个失败job后的拼接结果替代完整基线。scripts/work check及链接校验通过，人工验收留用户签署。

## 风险

仓库workflow也可能随PR变化；独立复核需检查测试断言是否被删改。没有真实Actions运行权限/授权时记录未执行，不伪造基线。

## 执行记录

- 2026-09-10：只创建最终验收范围，尚未实施、委派或触发Actions。
