---
id: "CHANGE-009"
type: "change"
title: "把手写基础组件改为基于 shadcn 官方实现"
status: "approved"
work: "WORK-027"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-01"
updated_at: "2026-09-01"
---

# CHANGE-009：把手写基础组件改为基于 shadcn 官方实现

<!--
本节面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。报错原文、依赖坐标、字段、类、框架、协议、表名、路径和
命令从下一节开始再出现。
-->

## 为什么做

项目一开始就定下一条组件原则：**基础组件不自己造。优先用 shadcn/ui 的组件，shadcn 确实没有的才手写；
用的时候把它的颜色改成消费本仓库的设计 token。** 这条写在 `docs/frontend.md`，但没有落成可检查的约束。

结果是 16 个基础组件里有 6 个是从零手写的，而 shadcn 官方本来就有对应实现。手写的那些能用，但少了官方
组件多年打磨出来的可访问性细节和组合能力——例如表单字段，官方提供 10 个可组合的子组件，仓库里手写的
只有一个整体封装。同时文档里还留着一份 1381 行、手工维护的组件参考页，它跟真实组件长得不一样，也永远
追不上真实组件的变化。

本次把这两件事一起纠正：手写的基础组件改成基于官方实现再套本仓库 token；那份手工参考页删掉，视觉参考
改用 Storybook——它渲染的就是真组件。

## 当前状态

`apps/web/src/components/ui/` 有 16 个基础组件。7 个已建立在 `@base-ui/react` primitive 上
（button、collapsible、dialog、dropdown-menu、popover、sheet、sidebar），其余 9 个是手写实现。

`docs/design-system/components.html` 是 1381 行手写静态 HTML，由 `components.manifest.json` 登记为
`reference`，并被 `docs/design-system.md` §1 与 §6 指为视觉参考。Storybook 已覆盖 16 个组件中的 15 个，
且带 `@storybook/addon-a11y`。

对照 shadcn base-nova 官方 registry 逐个核实的结果：

| 项目组件 | 官方对应 | 这次怎么处理 |
|---|---|---|
| `badge` | `badge` | **换成官方那份文件**，颜色改成本仓库 token，OJ 五态加在官方 4 个变体之上 |
| `card` | `card` | 换成官方那份文件，补齐官方有而我们没有的子组件 |
| `field` | `field` | 换成官方那份文件；官方提供 10 个可组合子组件，且不绑定任何表单库 |
| `inline-notice` | `alert` | 换成官方 `alert`，OJ 五态加在官方 2 个变体之上 |
| `async-state` | `spinner` + `empty` | **维持现状**（执行中修正，见下） |
| `icon-button` | `button` | **维持现状**（执行中修正，见下） |
| `link`、`typography` | 官方没有（registry 返回 404） | **维持现状**，并写明「官方没有」这个依据 |
| `layout` | 官方没有 | 维持现状 |
| 其余 7 个 | 官方有同名组件 | **这次不改代码**，只把与官方的出入写下来 |

「换成官方那份文件」的具体含义、为什么值得换，以及「只把出入写下来」要写什么，见
[DESIGN-021 的术语一节](./30-design-DESIGN-021.md#术语)。

## 当前问题

- 组件原则只写在文档里，没有任何检查阻止下一个开发者继续手写基础组件；
- 手写实现少掉官方组件的可组合 anatomy 与可访问性细节，最典型的是表单字段；
- `components.html` 是手工维护的静态 HTML，无法复用 React 组件，必然与真实组件漂移，而项目已经有
  渲染真实组件的 Storybook——两个评审入口里更差的那个还占着「视觉参考」的名分。

## 目标状态

- REQ-001：`badge`、`card`、`field`、`inline-notice` 四个组件的实现文件替换为 shadcn base-nova 的官方
  版本，只把其中的颜色与尺寸 class 改成本仓库语义 token 或稳定 Tailwind alias；官方的 DOM 结构、
  属性接口与可访问性行为原样保留。
- REQ-002：官方变体集合保留，OJ 语义（`success`/`warning`/`danger`/`info`/`special`）以扩展方式叠加，
  不删除官方变体，也不改变官方变体的含义。
- REQ-003：已基于 `@base-ui/react` 的 7 个组件逐个与官方实现比对，把差异（缺失的子组件、缺失的 slot、
  行为差异）记录在 VERIFY，本次不强制改造。
- REQ-004：`link`、`typography`、`layout`、`icon-button`、`async-state` 维持现状，并在组件清单里
  注明依据，避免以后重复讨论同一个问题。前三个是官方 registry 没有该组件；后两个是**官方有同名
  组件但不承担我们需要的可访问性保证**——判据不是「官方有没有」，而是「官方那份是否覆盖我们依赖
  的行为」。
- REQ-005：删除 `docs/design-system/components.html`，同步 `components.manifest.json` 与
  `docs/design-system.md` §1、§6，视觉参考改指 Storybook。
- REQ-006：把「基础组件优先用 shadcn，官方没有才手写；采用后只替换 token 不重写结构」写成明确条款，
  并说明如何验证下一个组件遵守了它。

## 不变条件

- REQ-007：所有组件在 `cherry-black` 与 `pure-white` 两个主题下保持相同 anatomy、size、variant、state
  与键盘行为；不引入 `dark:`、raw hex/OKLCH、primitive palette 或按 theme id 分支。
- REQ-008：现有 12 个消费者文件的对外行为不变——题库列表、题目详情、题目工作台、登录、改密、
  用户管理与系统状态面板的可见文案、交互结果和路由行为保持一致。
- REQ-009：不引入 Radix 形成第二套 primitive；`@base-ui/react` 仍是唯一无样式交互 primitive。
- REQ-010：不改动 `apps/web/design-system/` 的 token、主题、manifest 或合同；本次只改 token 的消费方。
- REQ-011：普通 Web 命令仍完全不读取、复制或链接 `docs/design-system/`。

## 影响范围

`apps/web/src/components/ui/` 的 6 个组件及其 story 与测试；引用它们的 12 个文件
（题库与管理页面 5 个、登录与改密 2 个、认证与系统状态组件 3 个、用户管理 1 个、密码字段 1 个）；
`docs/design-system.md`、`docs/design-system/components.html`、`components.manifest.json`；
`docs/frontend.md` 的组件原则条款。不涉及后端、契约、判题引擎与部署配置。

## 风险

换文件时最可能出的问题是**悄悄改变可见行为**：官方实现的默认间距、圆角、字重与现有版本不同，
逐个消费者页面可能出现视觉回归；`field` 的 aria 关联方式从 `cloneElement` 改为官方子组件组合，
可能丢掉现有的 `aria-describedby` / `aria-invalid` 行为。

其次是**范围蔓延**：官方组件带来新的子组件和变体，容易顺手在业务页面用上，使本次改动同时变成功能改动。

两者的应对都是同一条：本次只换文件与颜色，不改任何调用方的用法；调用方文件的改动仅限于
因组件 API 变化而必须做的最小适配，且必须在 VERIFY 中逐个列出。

## 回归检查

- AC-001：四个换过文件的组件在两个主题下的 default、hover、pressed、focus-visible、disabled、loading
  状态截图与改动前一致或有书面说明；Storybook 的 a11y addon 无新增违规。
- AC-002：`field` 的 `aria-describedby`、`aria-invalid`、`required` 关联行为由测试覆盖，且登录、改密、
  用户管理三条表单链路的键盘操作与错误提示不变。
- AC-003：12 个消费者文件的现有测试全部通过；新增或修改的适配点在 VERIFY 中逐一列出。
- AC-004：`npm run check`（format:check、lint、typecheck、test:run）与 `npm run build` 通过；
  Playwright E2E 在两个主题下通过。
- AC-005：删除 `docs/design-system/` 整个目录后，`npm ci`、`dev`、`check`、`build`、Storybook 与 E2E
  仍然全部通过——验证文档树没有反向依赖。
- AC-006：`scripts/work check` 与文档链接校验通过，`components.html` 不再被任何文档或 manifest 引用。

## 变更记录

- 2026-09-01：状态变更：draft → review。原因：初稿写完，提交人工审核
- 2026-09-01：意图闸通过：review → approved。原因：负责人审阅后批准：6 个组件换成 shadcn 官方实现文件、7 个已基于 base-ui 的只记录与官方的出入不改代码、link/typography/layout 因官方 registry 无对应而维持手写；调用方用法尽量不动，TASK-044 与 TASK-045 串行执行

## 变更记录

- 2026-09-01：负责人签署意图闸，按 6 个组件换文件的范围批准。
- 2026-09-01：**范围缩小为 4 个**，`icon-button` 与 `async-state` 移入「维持现状」，经负责人确认。
  执行 TASK-044 时取到官方源码逐条比对发现：官方 `button` 不强制无障碍名称
  （`'aria-label' in 源码` 为 False），而 `IconButton` 把 `label` 设为必填，落实的是
  `design-system.md` globalRules「icon-only 控件必须有可访问名称」；官方 `spinner` 只有 21 行转圈
  svg 且依赖 shadcn 站点内部的 `IconPlaceholder`，官方 `empty` 是纯布局（无 `aria-live`、无
  `role="status"`），而 `AsyncState` 覆盖 empty/loading/error/unauthorized 四态并强制 loading 提供
  `progressLabel`。换过去会丢失这些保证，与 REQ-007 冲突。
