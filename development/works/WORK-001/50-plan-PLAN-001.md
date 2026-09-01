---
id: "PLAN-001"
type: "plan"
title: "重建统一开发文档系统"
status: "approved"
work: "WORK-001"
owners: ["codex/root"]
depends_on: ["CHANGE-001", "DESIGN-001", "DECISION-001"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# PLAN-001：重建统一开发文档系统

## 目标

在一个可独立校验的改动中完成新系统、有效内容迁移、旧系统删除和全部规则入口切换。

## 改动区域

`development/`、`docs/`、`scripts/`、`.github/workflows/ci.yml`、根 README、`CLAUDE.md`、
`AGENTS.md` 与旧 `product/`、`tasks/`。运行时代码不在范围内。

## 阶段与顺序

1. 建立统一目录、元数据、模板、Schema 和流程规则。
2. 实现并测试 `scripts/work`。
3. 把仍有效的产品定义和长期历史迁入新模型。
4. 删除旧系统并切换 README、协作规范、Git 跟踪和 CI。
5. 运行新工具、文档链接、diff 与现有工程回归验证，记录 VERIFY。

## 并行与依赖

目录/模板和工具实现可以交替验证；内容迁移必须在旧目录删除前完成；规则与 CI 只能在新工具通过基础
测试后切换。当前任务由一个执行者完成，避免共享目录中的生成编号冲突。

## 迁移与交付

这是仓库协作基础设施，不产生服务部署。合入主分支即完成发布；切换点是同一提交中的 CI 与规则更新，
避免新目录搭配旧命令或反向组合。

## 风险

主要风险见 WORK-001。迁移前逐项搜索旧路径和 ID，迁移后以 Git diff 和全文搜索确认没有残留入口。

## 验证

执行 Python 编译检查、工具端到端用例、真实仓库 `scripts/work check/list/flow/context`、旧引用搜索、
`git diff --check`，并按改动影响补充现有 Web、Java 与 Go 工程的回归检查。

## 回退

合入前可整体撤销本次文件变更恢复旧系统；合入后旧内容仍在 Git 历史。新系统不迁移运行时数据，
不存在数据库回退。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：实施顺序、验证与回退方式已完成
