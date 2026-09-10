---
id: "TASK-104"
type: "task"
title: "冻结阅读主线、资源归属与数字清单"
status: "todo"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-104：冻结阅读主线、资源归属与数字清单

## 任务目标

在后续授权后固定重构实际起点，形成可执行的符号映射和数值清单；本任务只修改本工作文档。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的主阅读路线、阶段表达与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

先核对 WORK-048 重叠路径交接；该工作无需整体结束，但相应实现基线必须稳定且记录明确。
获得文档通过及后续实施授权、意图闸由人签署后才可推进；当前 todo 不代表可执行。

## 产出

在 DESIGN-043 记录实际基线（HEAD、已跟踪差异及新增文件）、旧符号→新职责/拟议符号的映射、资源所有权表、数字清单与依据未知项。WORK-048 重叠路径的交接事实和既有问题写入 VERIFY-050。

## 完成标准

- [ ] 重叠文件已交接，不以 HEAD 替代未提交内容。
- [ ] /run 与判题两条路径的每个交接均定位到符号，明确进程/线程及完成条件。
- [ ] 数字清单覆盖设计中的八类值，并记录原值、依据及拟议归属。
- [ ] 未知依据显式保留；helper 架构重选未被代决策。

## 验证

只读核对 git 状态、符号和既有测试；检查文档链接及 scripts/work check。源码主线、资源与数字清单必须能相互对应。此任务不把原有测试结果登记为重构通过。

## 风险

在途工作变化会使映射失效；未完成交接时只做不依赖该基线的文档分析，不能移动相关代码。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
