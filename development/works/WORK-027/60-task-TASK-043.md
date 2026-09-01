---
id: "TASK-043"
type: "task"
title: "移除手写组件参考页，视觉参考改指 Storybook"
status: "todo"
work: "WORK-027"
owners: ["codex/root"]
depends_on: ["CHANGE-009", "DESIGN-021", "PLAN-017"]
related: []
implements: ["CHANGE-009#REQ-005", "CHANGE-009#REQ-006", "CHANGE-009#REQ-004"]
verifies: []
tags: []
read_paths: ["apps/web/.storybook", "apps/web/src/components/ui", "docs/design-system"]
write_paths: ["docs/design-system.md", "docs/design-system/components.html", "docs/design-system/components.manifest.json", "docs/frontend.md"]
forbidden_paths: ["apps/web/src", "apps/web/design-system", "contracts", "apps/server"]
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# TASK-043：移除手写组件参考页，视觉参考改指 Storybook

## 任务目标

删除与真实组件长期漂移的手工组件参考页，把视觉参考改指 Storybook，并把「基础组件优先用 shadcn 官方
实现」这条既有原则写成可执行的条款。本任务不改任何前端源码。

## 依据

[CHANGE-009](./10-change-CHANGE-009.md) REQ-005、REQ-006；[DESIGN-021](./30-design-DESIGN-021.md) 整体方案第 1 步。

## 可查看范围

`apps/web/.storybook`、`apps/web/src/components/ui`（只读，用于确认 Storybook 覆盖面）、`docs/design-system`。

## 可修改范围

`docs/design-system.md`、`docs/design-system/components.html`、`docs/design-system/components.manifest.json`、
`docs/frontend.md`。

## 禁止修改

`apps/web/src`、`apps/web/design-system`、`contracts`、`apps/server`。本任务与前端源码无关，越界即说明范围理解有误。

## 依赖

DESIGN-021 定稿。与 TASK-044、TASK-045 无依赖，可并行。

## 产出

- 删除 `docs/design-system/components.html`；
- `components.manifest.json` 的 `reference` 与 `sourceFiles` 不再指向该文件，改为说明视觉参考由 Storybook 承担；
- `docs/design-system.md` §1、§6 同步，说明 Storybook 是唯一视觉参考，且它渲染的是真实组件；
- `docs/frontend.md` 把组件优先级写实：基础组件先查 shadcn registry，官方有就以官方实现为基座、
  只替换 token；官方没有（如 `link`、`typography`）才手写，并注明依据。

## 完成标准

- [ ] `components.html` 已删除，且仓库内没有任何文档或 manifest 仍引用它。
- [ ] `docs/design-system.md` 与 `components.manifest.json` 的视觉参考指向一致，不存在两个入口。
- [ ] `docs/frontend.md` 的组件条款写明「如何判断官方是否有对应组件」，而不只是表达偏好。
- [ ] `link`、`typography`、`layout` 保留手写的理由（registry 无对应）已记录，避免以后重复讨论。

## 验证

`python3 scripts/docs_test.py` 与 `scripts/work check` 通过；`grep -rn components.html` 在仓库内无残留引用
（历史工作项文档中的 `read_paths` 记录除外，那是当时的事实）。

## 风险

低。唯一风险是删掉参考页后，评审的人不知道去哪看组件——因此 §6 必须明确写出 `npm run storybook` 的入口，
而不只是说「见 Storybook」。

## 执行记录

暂无。
