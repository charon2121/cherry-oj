---
id: "TASK-104"
type: "task"
title: "冻结阅读主线、资源归属与数字清单"
status: "done"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048", ".github", "development/works/WORK-050"]
created_at: "2026-09-10"
updated_at: "2026-09-13"
---

# TASK-104：冻结阅读主线、资源归属与数字清单

## 任务目标

在计划完成后固定重构实际起点，形成可执行的符号映射和数值清单；本任务只修改本工作文档。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的主阅读路线、阶段表达与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

WORK-048 与 WORK-050 已验收，TASK-113 已完成。以 PLAN-033 的当前 SHA/工作区/CI 摘要快照为起点，实际开始任务时再次核对是否变化。
2026-09-13 已核对 WORK-049 意图闸及 WORK-050 验收；用户随后明确开始第一轮，按依赖推进任务并执行，不再等待相同签署。

## 产出

在 DESIGN-043 记录实际基线（HEAD、已跟踪差异及新增文件）、旧符号→新职责/拟议符号的映射、资源所有权表、数字清单与依据未知项。WORK-048 重叠路径的交接事实和既有问题写入 VERIFY-050。

## 完成标准

- [x] 重叠文件已交接，不以 HEAD 替代未提交内容。
- [x] /run 与判题两条路径的每个交接均定位到符号，明确进程/线程及完成条件。
- [x] 数字清单覆盖设计中的八类值，并记录原值、依据及拟议归属。
- [x] 未知依据显式保留；helper 架构重选未被代决策。

## 验证

只读核对 git 状态、符号和既有测试；检查文档链接及 scripts/work check。源码主线、资源与数字清单必须能相互对应。此任务不把原有测试结果登记为重构通过。

## 风险

在途工作变化会使映射失效；未完成交接时只做不依赖该基线的文档分析，不能移动相关代码。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
- 2026-09-13：状态变更：todo → ready。原因：用户已明确开始第一轮重构，意图闸和CI基线已验收；本任务仅冻结实施清单与基线
- 2026-09-13：状态变更：ready → doing。原因：开始核对实际基线、符号映射、资源关闭路径和固定测试入口
- 2026-09-13：状态变更：doing → done。原因：B0 实际基线、源码映射、资源错误回收、八类数字和52个固定测试入口已核对并记录，文档校验通过

## 2026-09-13 批次与边界细化

对应 PLAN-033 的 B0。现有设计中的路线/资源/数字表是盘点起点，完成前还需逐项核对实际关闭错误分支、命名后的精确位置与固定 CI 测试选择器。
产出必须包含源码 SHA、工作区增量归属、harness/cases 摘要、保留的 requiredGoTests 名称/包路径；不把文档已有表格直接勾为完成。

- 2026-09-13：B0 完成。逐项复核源码锚点、错误回收分支与八类数字；52 个固定测试函数均存在，93 项与两个摘要不变；精确映射和增量归属见 DESIGN-043 的 B0 记录。此时尚未修改源码。
