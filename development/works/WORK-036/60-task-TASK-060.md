---
id: "TASK-060"
type: "task"
title: "补齐 Tailwind alias 词汇表并加禁写 lint"
status: "todo"
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
4. 改写前后的页面截图对照。

## 完成标准

- [ ] `apps/web/src` 中除 `components/ui/` 外，`var(--ds-` 出现次数为 0。
- [ ] `components/ui/` 内剩余的 `var(--ds-` 用量相对基线 807 处显著下降，
      每一处剩余用法能说明为什么 alias 无法表达。
- [ ] 新 lint 规则有负向 fixture，`--self-test` 用例数相应增加且全部通过。
- [ ] 改写前后 `dist/` 中 token 解析值无差异；两个主题下逐路由截图对照无外观变化。
- [ ] 新增的 alias 全部有实际消费者；无人使用的不保留。
- [ ] `apps/web/design-system/themes/` 的 diff 为空。

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
