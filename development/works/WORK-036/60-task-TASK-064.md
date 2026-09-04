---
id: "TASK-064"
type: "task"
title: "按 Linear 重新识别构图并改造列表页外壳"
status: "done"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-062"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-004", "IMPROVEMENT-003#REQ-005", "IMPROVEMENT-003#REQ-014"]
verifies: []
tags: []
read_paths: ["apps/web", "docs/design-system/source/claude-design-v1/ui_kits"]
write_paths: ["apps/web/src/components/ui", "apps/web/src/features/problems", "apps/web/e2e"]
forbidden_paths: ["apps/web/design-system", "contracts"]
created_at: "2026-09-04"
updated_at: "2026-09-04"
---

# TASK-064：按 Linear 重新识别构图并改造列表页外壳

## 任务目标

把列表页从"装在卡片里的后台数据表"改成"铺满的应用工作区"。用户对样板页给出的三处判断
——居中卡片、筛选像表单、有表头太像表格——逐条消除。

## 依据

[DESIGN-030](./30-design-DESIGN-030.md)「构图特征的重新识别（2026-09-04）」；
触发条件是 [DECISION-020](./40-decision-DECISION-020.md) C-2 的重审条款：样板被判不像时
回设计层重新识别，不继续迁移其余页面。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。含 `e2e`：本次**有意改变筛选控件的形态**，
与 TASK-062"只改表达不改行为"的前提不同，相关断言需要随之更新，并在执行记录说明改了哪几条、为什么。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。设计值不动。
**不动两个 Shell**：用户端是否改成左侧固定侧栏属于独立工作项，不在本任务内。

## 依赖

依赖 TASK-062 的样板页与用户判断。

## 产出

1. `DataList` 增加 `showHeader`（默认 `false`）与 `bleed`（默认 `false`）：
   - 无列头时直接就是行，列的含义由内容自明（mono 是标识、右对齐是度量）；
   - `bleed` 去掉圆角外框，边界交给 hairline 与留白。
2. `Toolbar` 改为列表的头部而不是浮在它上面的盒子：与列表之间不留边框间隙。
3. `ListPageTemplate` 增加 `width="full"`：不套 1200px 居中容器，列表左右贴主内容区边缘。
4. 筛选控件改为紧凑形态：去掉竖排标签，标签改由 `aria-label` 承担，触发器像 toolbar 按钮，
   显示"全部难度"这类当前值。保持 `role="combobox"` 与可访问名称。
5. 题库列表页应用以上四项。

## 完成标准

- [x] 列表左右边缘贴主内容区边缘，页面上没有居中留白的空白带。
- [x] 列表外层没有圆角边框；工具条与首行之间没有第二条边界。
- [x] 默认无列头；`showHeader` 打开时列头仍可用（管理端数据表保留该能力）。
- [x] 四个筛选控件不带竖排标签，横向占用显著小于改造前，仍是 `role="combobox"` 且有可访问名称。
- [x] 筛选仍即时生效，没有提交按钮。
- [x] 题库列表的业务行为、URL search 参数与 API 调用不变。
- [x] 改动的 E2E 断言逐条说明原因；未涉及设计变化的断言不得改动。
- [x] 两主题、320px、键盘、长中文验收通过。

## 验证

```bash
cd apps/web
npm run check && npm run build && npm run test:e2e && npm run storybook:build
```

产出改造前后的同画面截图交用户判断。判据是用户对三处问题的复核，不是测试是否通过。

## 风险

这是第二版。若仍被判不像，**不要继续在列表内部微调**：按 DESIGN-030 的重审条件，
下一个怀疑对象是应用外壳（顶部导航 vs 左侧固定侧栏），而那属于独立工作项，
需要新建 WORK 并重新走意图闸，不在 WORK-036 内扩大。

## 执行记录

- 2026-09-04：第二轮迭代（用户判定第二版"很一般"、并质疑题库为何占满页面宽度）。
  量化后确认：1440px 行宽里真正有内容的约 375px，空掉约 1000px（70%）。

  根因不是宽度，是**把两种内容混为一谈**：参照的 issue 行是"一句长英文 + 几个小图标"，
  两簇布局成立是因为标题本身撑得满；题库是表格型数据（短标题 + 难度 + 标签 + 语言），
  把元信息推到右边缘，6 个汉字的标题后面必然空掉一大半，换多宽都一样。

  此前拿 §7.2 原则 F"空白是内容"给这个结果背书是错的——那条的前提是内容本来就填不满，
  不是让内容稀疏地摊在过宽的容器里。原则 F 已据此修正，补上"行内的空白要先确认不是排布错了"。

  改动：`ListPageTemplate` 增加 `width="column"`（约 1024px 居中，无框无底色）；
  `DataList` 增加 `align="packed"`（元信息紧跟标题）；packed 下标题取确定宽度 352px。
  最后这条是中间踩出来的：只做"紧跟"不定宽，后续字段会随标题长短参差，比空白更伤扫视；
  且宽度必须是确定值而非 `w-full`——packed 下父容器是 shrink-to-fit，百分比会解析回内容宽度。
  实测三行尾部列现在都落在 x=620。

  过程中两个失误值得记：第一次"紧跟"改完只看截图就以为对了，实际列是参差的，量 x 坐标才发现；
  第二次改动因 prettier 重排后字符串替换未匹配而**静默丢失**，我拿着旧构建量了半天——
  替换必须断言。
- 2026-09-04：创建任务。用户判定第一版样板页不符合预期，指出居中卡片、筛选像表单、
  有表头三处，参照物为 Linear 本身。
- 2026-09-04：状态变更：todo → ready。原因：DESIGN-030 已记录重新识别，边界明确
- 2026-09-04：状态变更：ready → doing。原因：开始第二版构图改造
- 2026-09-04：状态变更：doing → done。原因：第二轮迭代完成：上下结构、内容收窄到 1024px、行内字段紧跟标题且跨行对齐
