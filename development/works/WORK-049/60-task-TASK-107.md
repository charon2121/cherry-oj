---
id: "TASK-107"
type: "task"
title: "复核执行顺序可读性并记录行为回归证据"
status: "todo"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-106"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-005", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007", "CHANGE-013#AC-001", "CHANGE-013#AC-002", "CHANGE-013#AC-003", "CHANGE-013#AC-004", "CHANGE-013#AC-005", "CHANGE-013#AC-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-107：复核执行顺序可读性并记录行为回归证据

## 任务目标

独立核对源码阅读路线与等价行为，交付可供人工验收的记录及候选规范处置清单。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的主阅读路线、阶段表达与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

TASK-105、TASK-106 完成并提供可比较 diff；独立复核方式在执行前落实。
获得文档通过及后续实施授权、意图闸由人签署后才可推进；当前 todo 不代表可执行。

## 产出

VERIFY-050 的逐 AC 证据与实际命令/环境/结果；MEMORY-036 的已证实教训；DESIGN-043 候选规则的适用性与未决项。只修改本工作文档。

## 完成标准

- [ ] 未参与相应实现的审查者实走正常执行、超时和取消路径，记录卡点及修复后的复核。
- [ ] 六项 AC 分别记录证据；测试通过不替代阅读判断。
- [ ] 本机与 Linux 结果区分执行/未执行/跳过/失败，不能用交叉编译替代内核验证。
- [ ] 数值、协议、权限、配置及工作区边界完成比对，回退只覆盖本工作。
- [ ] 候选规则逐项列建议保留/调整/不提升及理由，人工确认待签；不修改全局规范。
- [ ] WORK-050 交付的同一必需清单在重构最终提交全部运行通过，包含真实内核、原生部署、真实业务及清理；报告SHA对应本次候选，不复用基线成功记录。

## 验证

按 PLAN-033 验证矩阵执行。发现实现问题交回 TASK-105/106 按原边界处理，本任务不越界直接修改代码。scripts/work check 检查文档；验收闸仅由用户签署。

## 风险

同一实现者只按自身理解复述代码，可能掩盖新读者仍看不懂；阅读结果必须包含具体符号、卡点与解释。环境不可用时保持部分未验证，不作完成承诺。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
