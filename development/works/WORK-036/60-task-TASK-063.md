---
id: "TASK-063"
type: "task"
title: "建立构图合同与页面提示词"
status: "done"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-062"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-006", "IMPROVEMENT-003#REQ-007", "IMPROVEMENT-003#REQ-009"]
verifies: []
tags: []
read_paths: ["apps/web"]
write_paths: ["docs/design-system.md", "docs/design-system/PROMPT.md", "docs/design-system/components.manifest.json", "CLAUDE.md", "AGENTS.md"]
forbidden_paths: ["docs/design-system/source", "apps/web"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-063：建立构图合同与页面提示词

## 任务目标

把这次识别出的页面语法写成能在截图上判定的规则，并做成后续每次写页面都会读到的提示词——
让"构图合规"从一次性成果变成可复用的约束。

## 依据

[IMPROVEMENT-003](./10-improvement-IMPROVEMENT-003.md) REQ-006、REQ-007、REQ-009；
[DESIGN-030](./30-design-DESIGN-030.md)；样板页的实际结论来自 TASK-062。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`apps/web` 在禁止范围内：本任务只写文档。

## 依赖

依赖 TASK-062 定稿。文档要描述的是**已经验证过的**构图，不是设想的构图。

## 产出

1. `docs/design-system.md` 新增「构图合同」一节，规则必须能在截图上判定，例如：
   - 列表页 = 工具条 + 数据列表 + 分页；工具条两行的组成与顺序；
   - 过滤即时生效，不放提交按钮（除非查询昂贵，并说明什么算昂贵）；
   - 数据列表是对齐的列，不是卡片网格，不是 `flex-wrap`；行密度 11px/16px + hairline；
   - 卡片半透明、普通分组优先用间距与分隔线；
   - 四档文字各自的用途，以及 disabled 为什么是第五种而不是第四档的延伸；
   - 状态用 8px 圆点 / 2px 圆角方块；度量值用 mono 并给准确数字；
   - 只有 opacity / color / background-color 参与动效。
2. 新增 `docs/design-system/PROMPT.md`：三层来源（shadcn 官方骨架 → 语义 token → 构图语法）、
   页面语法、禁止清单、交付前自检问题。自检要求逐条作答，不接受"已遵守"。
3. `components.manifest.json`：`data-table-list` 与 `app-shell-navigation` 的合同与新实现一致，
   并保持 WORK-035 建立的 `sourceRefs`。
4. `CLAUDE.md`、`AGENTS.md` 的 Web UI 路由指向 `PROMPT.md`。
5. `docs/design-system.md` §10 更新已知缺口：移除已解决的项，明确"其余页面尚未迁移"
   是过渡状态并指明承接工作。

## 完成标准

- [x] 构图合同的每条规则都能对着一张截图判定真假；不含"应当保持克制"这类无法判定的表述。
- [x] `PROMPT.md` 的自检问题是具体的（如"列边缘能连成竖线吗"），不是"是否符合设计系统"。
- [x] `components.manifest.json` 中不再有"有合同无实现"的第 3 层条目（`data-table-list`、
      `app-shell-navigation`），其余仍无实现的条目在 §10 如实列出。
- [x] `CLAUDE.md`、`AGENTS.md` 指向 `PROMPT.md`，且只改设计系统相关行。
- [x] `docs/design-system.md` 中不出现具体色值（延续 WORK-035 的约束）。
- [x] `python3 scripts/docs_test.py` 与 `scripts/work check` 通过，无死链。

## 验证

```bash
python3 scripts/docs_test.py
scripts/work check
```

人工检验方式：拿构图合同去核对一个**未迁移**的页面，应当能明确指出它违反了哪几条。
如果指不出来，说明规则太软，不具备判定力。

## 风险

最大的风险是写成又一份"token 检查清单"——那样它就无法解决"材料合规、页面风格不合规"。
每写一条规则都要自问：拿着这条规则和一张截图，两个人会得出同样的结论吗？

## 执行记录

- 2026-09-03：创建任务。
- 2026-09-03：实施完成。新增 `docs/design-system/PROMPT.md`（120 行）：三层来源、先选模板、
  页面语法、禁止清单、交付前自检七问。`design-system.md` 新增 §7.2 构图合同，§10 区分"已解决"
  与"仍是缺口"，§11 评审清单补五条构图判据。`components.manifest.json` 的 `data-table-list`
  与 `app-shell-navigation` 补 `implementation` 字段并按实际实现重写 anatomy/variants/constraints；
  `CLAUDE.md`、`AGENTS.md` 的 Web UI 路由改指 PROMPT.md。

  **提示词按实际建成的东西校准过，不是照抄草稿**。与最初草稿的差异，每处都有依据：
  - 删掉「搜索（⌘K）」——命令面板未实现，写进提示词等于要求实现一个不存在的东西；
  - 「pill 过滤器」改为"过滤即时生效"这一判据本身。实际实现中互斥小集合用 `ToolbarFilterGroup`
    的 pill，多值选择器用 `SelectField`；判据是"有没有提交按钮"，不是"是不是 pill"；
  - 行内边距 10px/16px 改为 **11px/16px**（来源的 `space-2x` 光学档，实测值）；
  - 四档文字补上具体 class 名与 `fg-disabled` 是第五个、不参与排序的说明——这是 TASK-059
    改合同后才成立的事实；
  - 新增三条只有踩过才知道的禁止：声明列宽必须配 `table-fixed`、新增 alias 必须在
    `extendTailwindMerge` 登记、引用不存在的 `--ds-*` 会静默失效；
  - 自检从六问增加到七问，并把"与来源同画面对照"降级为"对照来源代码里的数值"，
    同时写明该限制的原因与未决状态。
- 2026-09-03：状态变更：todo → ready。原因：TASK-062 样板页已定稿，构图结论可写入文档
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 4 收口：构图合同与 PROMPT.md
- 2026-09-03：状态变更：doing → done。原因：构图合同与 PROMPT.md 已建立，两个组件合同指向实现，入口路由改指提示词
