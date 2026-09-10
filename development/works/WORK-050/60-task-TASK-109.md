---
id: "TASK-109"
type: "task"
title: "冻结用例清单与基础CI检查"
status: "done"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["CAPABILITY-008", "DESIGN-044", "DECISION-028", "PLAN-034"]
related: []
implements: ["CAPABILITY-008#REQ-001", "CAPABILITY-008#REQ-005", "CAPABILITY-008#REQ-006", "CAPABILITY-008#AC-001"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "development/works/WORK-050", "docs/engineering", ".github/workflows", "apps/judge-engine", "deploy/sandbox-linux", "contracts", "scripts/work", "scripts/contracts_test.py", "scripts/docs_test.py", "apps/web/e2e"]
write_paths: ["development/works/WORK-050", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml"]
forbidden_paths: ["apps/judge-engine", "apps/server", "apps/web", "contracts", "compose.yaml", "compose.legacy.yaml", "deploy/backend", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "development/works/WORK-048", "AGETNTS.local.md"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-109：冻结用例清单与基础CI检查

## 任务目标

逐项整理WORK-048已通过场景，形成稳定case ID与断言/入口/证据映射，并把遗漏的Python及脚本检查接入既有CI。

## 依据

CAPABILITY-008 REQ-001/005/006与AC-001；DESIGN-044分层和清单规则。

## 可查看范围

以 front matter 的 read_paths 为准；已有实现只读用于核对不变量。

## 可修改范围

以 write_paths 为准。仅测试编排、工作流、报告和用例适配，不实现另一套沙箱或业务服务。远端动作仅指审核后的一次性 GitHub VM，不能连接现有服务器或 IDEA。

## 禁止修改

以 forbidden_paths 为准。禁止修改生产代码、协议、限额/权限来迁就测试；禁止使用旧私有配置、数据和凭据；不得清理未知所有权资源。发现真实缺陷先记录并另拆修复边界。

## 依赖

依 depends_on 顺序推进；本轮只是文档，需人工意图闸及后续实施授权，当前 todo 不可直接执行。

## 产出

ci下机器可读清单、schema/汇总基础库与自测、使用说明；ci.yml增加install/rootfs单测与AST/sh语法检查。保留已有六job名称与实际断言，不把Go跳过项算内核PASS。

## 完成标准

- [x] 每个已验收场景映射到具体Go测试或Python模式/业务夹具，含全部权限、计量、回收、状态和部署检查；合并重复用例有映射。
- [x] 独立复核标review-only，重启标deferred，其他平台单列；必需case数量与实际结果可核对。
- [x] 15项install与6项rootfs单测、Python AST、sh语法进入自动job，原有Go/race/tidy/contracts/work/docs/Web/Compose继续执行。
- [x] 报告解析拒绝重复case、未知schema、SHA不匹配和缺结果；测试本身有有意义正反例。

## 验证

本地运行现有21项Python单测、语法检查与报告正反例；工作流静态校验；后续授权发布后记录精确SHA的GitHub基础job执行结果。本阶段不声称特权套件已自动通过。

## 风险

清单只列脚本名无法证明内部断言覆盖；不得把历史失败批次或人工复核伪装成可自动完成的单例。

## 执行记录

- 2026-09-10：只读盘点后创建CI拆分方案，未实施。
- 2026-09-10：状态变更：todo → ready。原因：意图闸已由用户签署且明确允许实施；清单与基础CI边界已冻结
- 2026-09-10：状态变更：ready → doing。原因：开始建立用例清单、严格报告校验与Python基础CI接线

- 2026-09-10：完成93项必需case清单、严格报告/证据校验与基础job接线；本地basic入口通过15项install、6项rootfs、8项CI自测及AST/sh语法。Ruby Psych工作流解析通过。GitHub真实执行留待发布后记录，未声称Linux套件已运行。
- 2026-09-10：状态变更：doing → done。原因：93项清单、基础job与严格证据校验完成，本地29项单测及语法/YAML通过；Actions执行待发布
