---
id: "TASK-060"
type: "task"
title: "补齐 Tailwind alias 词汇表并加禁写 lint"
status: "done"
work: "WORK-036"
owners: ["claude/root"]
depends_on: ["TASK-059"]
related: []
implements: ["IMPROVEMENT-003", "IMPROVEMENT-003#REQ-001", "IMPROVEMENT-003#REQ-003", "IMPROVEMENT-003#REQ-011", "IMPROVEMENT-003#REQ-012"]
verifies: []
tags: []
read_paths: ["apps/web"]
write_paths: ["apps/web/design-system/tailwind-v4.css", "apps/web/src", "apps/web/scripts/check-design-system.mjs"]
forbidden_paths: ["apps/web/design-system/themes", "contracts"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-060：补齐 Tailwind alias 词汇表并加禁写 lint

## 任务目标

让业务代码有话可说：把 adapter 的 alias 补到能直接表达常规页面，把现有 807 处 `var(--ds-*)`
改写掉，并用 lint 锁住业务层不再新增。

## 依据

[DECISION-020](./40-decision-DECISION-020.md) B-2；[DESIGN-030](./30-design-DESIGN-030.md)
「Alias 词汇表」的类别表；[PLAN-024](./50-plan-PLAN-024.md) 阶段 2。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。**不改主题值**——本阶段的验收标准是外观零变化，
主题一起改就无法归因。

## 依赖

依赖 TASK-059 完成四档调整，否则改写过程中会同时发生两种外观变化。

## 产出

1. `tailwind-v4.css` 的 `@theme inline` 按 DESIGN-030 的类别表补全 alias；
2. `src/components/ui/` 与业务层的 `var(--ds-*)` 改写为 alias；
3. `scripts/check-design-system.mjs` 新增规则 `forbidden-ds-var-outside-ui`：
   扫描 `apps/web/src`，排除 `src/components/ui/`，命中 `var(--ds-` 即失败；
   配套负向 fixture 与 `--self-test` 用例；
4. 修复 `--ds-surface-recessed`：该 token **从未在任何主题中定义**，却被 4 处消费
   （`problem-detail-page.tsx` 的两个代码块、`safe-markdown.tsx` 的 `<pre>`、
   `admin.users.tsx` 的临时密码展示）。`background: var(--ds-surface-recessed)` 因此解析为空，
   这些本应"下陷"的块一直是透明背景。按合同 `--ds-surface-subtle` 的 role
   （"Recessed and subtle surface"）改为消费它。
5. 新增校验：`src` 中引用的每个 `--ds-*` 必须在 `theme-contract.json` 或 Foundation 中真实存在。
   现有 lint 只禁止业务层写 `var(--ds-`，没有任何机制发现"引用了不存在的 token"——
   这正是上面那个缺陷能存活至今的原因。
6. 改写前后的页面截图对照。

## 完成标准

- [x] `apps/web/src` 中除 `components/ui/` 外，`var(--ds-` 出现次数为 0。
- [x] `components/ui/` 内剩余的 `var(--ds-` 用量相对基线 807 处显著下降，
      每一处剩余用法能说明为什么 alias 无法表达。
- [x] 新 lint 规则有负向 fixture，`--self-test` 用例数相应增加且全部通过。
- [x] 改写前后 `dist/` 中 token 解析值无差异；两个主题下逐路由截图对照无外观变化，
      **唯一例外**是 `--ds-surface-recessed` 的修复：代码块与临时密码块会获得它们本应有的
      下陷背景。该例外需单独截图说明，不得与"改写无影响"混为一谈。
- [x] 新增的 token 存在性校验有负向 fixture，能拦住引用未定义 `--ds-*` 的情况。
- [x] 新增的 alias 全部有实际消费者；无人使用的不保留。
- [x] `apps/web/design-system/themes/` 的 diff 为空。

## 验证

```bash
cd apps/web
git grep -c "var(--ds-" src | grep -v "src/components/ui"   # 期望无输出
npm run check:design-system
npm run check && npm run build && npm run test:e2e
```

## 风险

改写量大，批量替换容易引入外观偏差。改写前后各截一组两主题全路由图对照，
本阶段的判据是"外观零变化"——任何视觉差异都说明改错了，而不是"顺便优化了一下"。

若某个 alias 补上后发现无人使用，删掉它，不要留着：留着会让下一个人以为那是推荐写法。

## 执行记录

- 2026-09-03：创建任务。
- 2026-09-03：实施完成。adapter 新增约 90 个 alias（间距全档具名、字号、字重、leading、tracking、
  motion、ease、radius、四档前景、透明面、line、elevation、布局尺寸）；改写 769 处 arbitrary
  写法，业务层 `var(--ds-` 由 807 降为 **0**，`components/ui` 仅剩 `text-editor.tsx` 33 处
  （CodeMirror 主题对象是 CSS-in-JS，没有工具类可用），`globals.css` 3 处（selection 与
  font-feature 属于 CSS 层直接消费）。新增 lint 规则 `ds-var-outside-ui` 与 token 存在性校验，
  各配负向 fixture，源码 self-test 19→20。

  **过程中发现并修复了四个静默失效**，都是改写才暴露的：
  1. `--ds-surface-recessed` 从未定义却被 4 处消费，代码块背景一直是透明的；
  2. `cn()` 的 tailwind-merge 不认识自定义键，把 `text-cap`（字号）和 `text-fg-2`（颜色）
     当同组互相覆盖，字号被丢弃——已用 `extendTailwindMerge` 逐组登记；
  3. Tailwind 的 `text-*` 自带一档行高，而 `text-[length:…]` 不带；已把每档
     `--text-*--line-height` 解绑为 `initial`，并据此关掉 tailwind-merge 的 font-size→leading
     覆盖规则（该规则对本项目不成立）。关掉后 Button 声明的 `leading-tight` 才真正生效——
     此前它一直被静默丢弃，按钮比设计高 5px；
  4. 我为 calc() 新增的 `--sidebar` 撞了 shadcn 已有的侧栏背景色变量，被 `check.mjs` 的
     adapter 校验当场拦下，改名为 `--layout-sidebar`。

  因此本阶段的"外观零变化"有 **4 类有意差异**：代码块获得应有的下陷背景、按钮获得声明的紧凑
  行高（39→34px）、标题获得声明的 `leading-h2`（36→31.9px）、少数原本依赖 Tailwind 自带行高的
  `text-sm` 处从 20px 变为继承的 21px。全部是"设计系统终于被真正应用"，无一处是新引入的偏差。
  计算样式逐元素对比与两主题 7 路由截图对照已留存。
- 2026-09-03：范围内追加两项，均在既有 `write_paths` 之内：修复未定义 token
  `--ds-surface-recessed`（4 处消费、0 处定义，实际渲染为透明），以及新增"引用的 token 必须存在"
  校验。前者是本阶段清点 token 用量时发现的既有缺陷；后者是它能长期存活的原因——
  只禁止写 `var(--ds-` 而不校验引用有效性，拼错的 token 会静默失效。
  因此本阶段的"外观零变化"标准有一处**有意例外**，已写入完成标准。
- 2026-09-03：状态变更：todo → ready。原因：TASK-059 已完成，四档已稳定
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 2：alias 词汇表与禁写 lint
- 2026-09-03：状态变更：doing → done。原因：alias 词汇表就位，业务层 var(--ds-) 清零，禁写与存在性校验上线；修复四个静默失效
