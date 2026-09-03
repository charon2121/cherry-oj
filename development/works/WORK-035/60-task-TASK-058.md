---
id: "TASK-058"
type: "task"
title: "对齐设计系统叙述与来源引用"
status: "done"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["TASK-057"]
related: []
implements: ["CHANGE-010", "CHANGE-010#REQ-006", "CHANGE-010#REQ-007", "CHANGE-010#REQ-008", "CHANGE-010#REQ-013"]
verifies: []
tags: []
read_paths: ["docs/design-system", "apps/web/design-system", "development/works/WORK-034"]
write_paths: ["docs/design-system.md", "docs/design-system/components.manifest.json", "apps/web/design-system/README.md", "CLAUDE.md", "AGENTS.md"]
forbidden_paths: ["docs/design-system/source", "docs/design-system/source-lock.json", "apps/web/src", "apps/web/design-system/themes", "apps/web/design-system/tokens.foundation.css", "apps/web/design-system/theme-contract.json", "contracts"]
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
   - 链接目标已由 TASK-057 修正，本任务只处理叙述：确认正文对这些文件的**描述**也已随之更新，
     视觉参考的说法改指 Storybook 与 `source/claude-design-v1/guidelines/`；
   - §10「当前实现边界」更新为收敛后的结构。
2. `docs/design-system/components.manifest.json`：每个组件条目新增 `sourceRefs`（相对 `docs/design-system/`
   的路径数组），指向 `source/claude-design-v1/` 内实际存在的文件。除 `*.prompt.md` 与 `*.card.html`
   外，也允许指向 `ui_kits/app/*.jsx` 与 `readme.md`：`app-shell-navigation`、`data-table-list`、
   `editor-workspace`、`dialog-popover` 的视觉与构图依据只存在于 app UI kit，`icon-slot` 的图标规范
   只存在于根 readme 的 ICONOGRAPHY 一节。把这些排除在外会让最需要追溯的第 3、4 层组件全部落空，
   而追溯正是 `sourceRefs` 的目的。
   来源中没有对应物的 OJ 业务组件（`verdict`、`submission-lifecycle`、`editor-workspace`、
   `data-table-list`、`app-shell-navigation`、`async-state`、`inline-notice` 等）填空数组，
   不得为了凑齐而指向不相关的卡片。
3. `docs/design-system.md` 散文中复述的具体色值改为语义描述（§3.1 的画布/surface/文字锚点、
   §5 的 Cherry 实心与 hover/active 色值）。TASK-057 完成后，`#d2042d` / `#08090a` 仍出现在本文，
   它是 REQ-001「值只有一处手写定义」的最后一处残留；具体值只以 `themes/*.css` 为准。
4. `apps/web/design-system/README.md`：删除"必须在同一 WORK/TASK 内同步 `apps/web/design-system/`
   与 `docs/design-system/`，并分别通过两侧检查"的维护要求——两侧检查在 TASK-057 后已不存在。
5. `CLAUDE.md`、`AGENTS.md`：设计系统相关的路由行指向新结构，只改这些行，不动其他条目。

## 完成标准

- [x] `docs/design-system.md` 的权威顺序为 3 层，且与 `docs/design-system/README.md`、
      `apps/web/design-system/README.md` 三处表述一致，无第四种说法。
- [x] 来源叙述与 `git log` 可核对：不再声称冻结来源是架构的直接来源。
- [x] 三份文档与两份入口文件中，不存在指向已删除文件的链接。
- [x] `components.manifest.json` 中每条 `sourceRefs` 路径在 `source/claude-design-v1/` 内真实存在，
      逐条核对通过。
- [x] WORK-034 目录下文件的 diff 为空。
- [x] `git grep -l` 确认 `#d2042d`、`#08090a` 等主题值只出现在 `apps/web/design-system/themes/*.css`
      与生成物 `design-tokens.json`、冻结来源 `source/` 之中。

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
- 2026-09-03：实施完成。`docs/design-system.md` 权威顺序 6 层降为 3 层（凭什么是这样 / 应该怎么用 /
  实际是什么），卷首来源叙述改写为与 git 历史一致的事实并点明构图层是已知缺口；§3.1、§5 散文中
  复述的色值全部改为语义描述，全文已无 hex；§3.2、§9 不再要求同步两棵树；§10 增加值锚点说明与
  已知缺口（五个空合同、前景色四档塌陷）；§11 评审清单换掉两侧验证那一条。
  `components.manifest.json` 18 个条目全部补上 `sourceRefs`，43 条引用路径经脚本核对全部存在，
  3 个 Cherry OJ 自有扩展（inline-notice、async-state、submission-lifecycle）保持空数组。
  `CLAUDE.md`、`AGENTS.md` 与 `apps/web/design-system/README.md` 同步为三层权威与单一真源表述。
  WORK-034 目录 diff 为空。
- 2026-09-03：扩大 `write_paths` 增加 `apps/web/design-system/README.md`，并把 `apps/web` 的整体禁止
  收窄为 `apps/web/src` 与三类值文件。原因：该 README 仍写着"必须同步两棵树并分别通过两侧检查"，
  而 TASK-057 已删除文档树的校验器，这条要求指向一个不存在的检查。本任务的完成标准要求三处表述
  一致、无第四种说法，不改它就无法满足。主题、foundation 与合同仍在禁止范围内。
- 2026-09-03：放宽 `sourceRefs` 的可引用文件类型，从 `*.prompt.md` / `*.card.html` 扩展到
  `ui_kits/app/*.jsx` 与 `readme.md`。原因见上方产出第 2 条：第 3、4 层组件的依据只存在于 app UI kit。
  仍然禁止为了凑齐而指向不相关的文件，确无对应物的条目保持空数组。
- 2026-09-03：范围收窄：`docs/design-system.md` 中指向被删文件的链接改由 TASK-057 处理，
  本任务只负责叙述、`sourceRefs` 与入口路由。原因见 TASK-057 执行记录。
- 2026-09-03：状态变更：todo → ready。原因：TASK-057 已完成，最终文件集合已确定
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 3：叙述、sourceRefs 与入口路由对齐
- 2026-09-03：状态变更：doing → done。原因：阶段 3 完成：权威顺序降为 3 层，来源叙述改为事实，sourceRefs 建立，入口路由对齐
