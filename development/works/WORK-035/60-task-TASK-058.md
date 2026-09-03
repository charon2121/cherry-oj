---
id: "TASK-058"
type: "task"
title: "对齐设计系统叙述与来源引用"
status: "todo"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["TASK-057"]
related: []
implements: ["CHANGE-010", "CHANGE-010#REQ-006", "CHANGE-010#REQ-007", "CHANGE-010#REQ-008", "CHANGE-010#REQ-013"]
verifies: []
tags: []
read_paths: ["docs/design-system", "apps/web/design-system", "development/works/WORK-034"]
write_paths: ["docs/design-system.md", "docs/design-system/components.manifest.json", "CLAUDE.md", "AGENTS.md"]
forbidden_paths: ["docs/design-system/source", "docs/design-system/source-lock.json", "apps/web", "contracts"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-058：对齐设计系统叙述与来源引用

## 任务目标

把设计系统的文字叙述改成与实际结构和 git 历史一致：权威顺序由 6 层降为 3 层，来源关系写成事实，
组件合同补上到冻结来源的引用，协作入口文件的路由指向新结构。

## 依据

[CHANGE-010](./10-change-CHANGE-010.md) REQ-006、REQ-007、REQ-008；
[DESIGN-029](./30-design-DESIGN-029.md)「接口与状态」；
[PLAN-023](./50-plan-PLAN-023.md) 阶段 3。

## 可查看范围

以 front matter 的 `read_paths` 为准。`development/works/WORK-034` 只读，用于核对 WORK-034 当时
的结论，避免把它的记录改写成事后叙述。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。**不得修改 WORK-034 的任何已签署文档**——它当时确实
走完了流程，事后改写是篡改历史；本次的新认识写在 WORK-035 自己的文档里。

## 依赖

依赖 TASK-057 已确定最终保留的文件集合，否则叙述会指向刚被删除的文件。

## 产出

1. `docs/design-system.md`：
   - §1 权威关系由 6 条降为 3 条：冻结来源 → 本文的原则与组件合同 → `apps/web/design-system/` 可执行真源；
   - 开头的来源叙述改写为事实：当前系统是 2026-08-28 建立的架构在 2026-09-03 引入冻结来源后
     完成数值适配的结果，冻结来源是视觉依据而非架构来源；
   - 删除对已删文件（`docs/design-system/` 侧 token、主题、adapter、preview）的全部引用，
     视觉参考改指 Storybook 与 `source/claude-design-v1/guidelines/`；
   - §10「当前实现边界」更新为收敛后的结构。
2. `docs/design-system/components.manifest.json`：每个组件条目新增 `sourceRefs`（仓库相对路径数组），
   指向 `source/claude-design-v1/` 内实际存在的 `*.prompt.md` 或 `*.card.html`。
   来源中没有对应物的 OJ 业务组件（`verdict`、`submission-lifecycle`、`editor-workspace`、
   `data-table-list`、`app-shell-navigation`、`async-state`、`inline-notice` 等）填空数组，
   不得为了凑齐而指向不相关的卡片。
3. `CLAUDE.md`、`AGENTS.md`：设计系统相关的路由行指向新结构，只改这些行，不动其他条目。

## 完成标准

- [ ] `docs/design-system.md` 的权威顺序为 3 层，且与 `docs/design-system/README.md`、
      `apps/web/design-system/README.md` 三处表述一致，无第四种说法。
- [ ] 来源叙述与 `git log` 可核对：不再声称冻结来源是架构的直接来源。
- [ ] 三份文档与两份入口文件中，不存在指向已删除文件的链接。
- [ ] `components.manifest.json` 中每条 `sourceRefs` 路径在 `source/claude-design-v1/` 内真实存在，
      逐条核对通过。
- [ ] WORK-034 目录下文件的 diff 为空。

## 验证

```bash
scripts/work check
python3 scripts/docs_test.py
node -e "const m=require('./docs/design-system/components.manifest.json');const fs=require('fs');let bad=[];for(const c of m.components)for(const p of (c.sourceRefs||[]))if(!fs.existsSync('docs/design-system/'+p))bad.push(c.id+': '+p);console.log(bad.length?bad:'all sourceRefs exist')"
```

外加人工通读 `docs/design-system.md`，确认叙述与实际目录一致。

## 风险

`CLAUDE.md` 与 `AGENTS.md` 是协作规则文件，改错会影响后续所有会话对流程的理解。只修改设计系统
相关的路由行；若发现需要改动的不止路由（例如规则本身需要调整），停下来记为待确认项，
由用户判断，不在本任务内扩大范围。

## 执行记录

- 2026-09-03：创建任务。
