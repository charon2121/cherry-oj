---
id: "TASK-0006"
title: "为 PE 返回可见空白差异信息"
type: "tech-debt"
area: "judge/checker"
priority: "P3"
status: "todo"
assignee: null
depends_on: []
related: []
created_at: "2026-08-18T19:10:35+08:00"
updated_at: "2026-08-18T19:10:35+08:00"
claim_branch: null
claimed_at: null
lease_until: null
completed_at: null
review_required: true
---

# TASK-0006：为 PE 返回可见空白差异信息

## 背景

checker 能定位 PE 的差异行，但 `judge.schema.json` 只允许 WA 携带 Diff。直接渲染普通字符串
又无法让空格、制表符和换行差异可见，因此只改后端没有实际价值。

## 目标

确定跨语言的空白可见表示，扩展契约、checker、flow 和前端展示，使 PE 能给出可理解的差异。

## 范围

包含：

- 先确定 `%q` 风格转义或 `␣`、`→`、`⏎` 等稳定表示。
- 在检测差异的时刻截取片段，避免跳过空白后丢失位置。
- 契约、Go 类型、契约对齐测试和前端展示按顺序同步。

不包含：

- 通用富文本 diff 编辑器。
- 改变 PE/WA 的判定规则。

## 验收标准

- [ ] 表示法在契约或设计文档中有明确说明。
- [ ] PE 响应可以返回差异位置和让空白可见的片段。
- [ ] 空格、制表符、换行和文件末尾差异均有测试。
- [ ] UI 展示不会执行原始 HTML，且不能只靠颜色区分差异。

## 执行记录

- 2026-08-18：从本地 `docs/backlog.md` 迁入任务中心。

## 阻塞信息

无。

## 完成结果

尚未完成。

