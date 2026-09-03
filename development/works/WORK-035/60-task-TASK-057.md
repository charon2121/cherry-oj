---
id: "TASK-057"
type: "task"
title: "删除文档树设计系统副本"
status: "done"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["TASK-056"]
related: []
implements: ["CHANGE-010", "CHANGE-010#REQ-003", "CHANGE-010#REQ-004", "CHANGE-010#REQ-005", "CHANGE-010#REQ-010", "CHANGE-010#REQ-011", "CHANGE-010#REQ-012"]
verifies: []
tags: []
read_paths: ["docs/design-system", "apps/web/design-system", "apps/web/package.json", ".github", "scripts"]
write_paths: ["docs/design-system", "docs/design-system.md"]
forbidden_paths: ["docs/design-system/source", "docs/design-system/source-lock.json", "apps/web", "contracts"]
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# TASK-057：删除文档树设计系统副本

## 任务目标

删除 `docs/design-system/` 中与 Web 真源重复的可执行副本和自制 preview，让文档树只承担"人读与
追溯"一个角色。删除后 `apps/web` 的全部命令行为不变。

## 依据

[CHANGE-010](./10-change-CHANGE-010.md) REQ-003、REQ-004、REQ-005、REQ-010、REQ-011、REQ-012；
[DESIGN-029](./30-design-DESIGN-029.md)「模块与数据」的删除/保留表；
[PLAN-023](./50-plan-PLAN-023.md) 阶段 2。

## 可查看范围

以 front matter 的 `read_paths` 为准。`.github` 与 `scripts` 是只读的，用于引用扫描，确认没有
CI 或检查脚本依赖被删文件。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。冻结来源 `source/` 与 `source-lock.json` 一字不改；
本任务不碰 `apps/web` 任何文件。

## 依赖

依赖 TASK-056 已在 Web 侧建立 `design-tokens.json` 值锚点。顺序不可颠倒：先删 docs 侧锚点、
后建 Web 侧锚点会留下一段无人检测值漂移的中间状态。

## 产出

1. 引用扫描结果（记入 VERIFY-036）：对下列每条路径在全仓库的引用点清单。
2. 删除：
   - `docs/design-system/tokens.foundation.css`
   - `docs/design-system/tokens.css`
   - `docs/design-system/theme-contract.json`
   - `docs/design-system/themes.manifest.json`
   - `docs/design-system/themes/cherry-black.css`、`themes/pure-white.css`（及空目录）
   - `docs/design-system/tailwind-v4.css`
   - `docs/design-system/design-tokens.json`
   - `docs/design-system/tools/build.mjs`、`tools/check.mjs`
   - `docs/design-system/preview/`（4 个 HTML）
3. `docs/design-system/README.md` 改写：文件角色表只列保留文件；"维护与检查"只保留
   `node docs/design-system/tools/source-lock.mjs --check`；Foundation 与主题的视觉参考指向
   `source/claude-design-v1/guidelines/` 的 20 张 specimen 卡片。
4. `docs/design-system/manifest.json` 改写：只登记保留下来的文件。
5. `docs/design-system.md` 中指向被删文件的 5 处 markdown 链接就地修正（`themes/*.css` 两处、
   `themes.manifest.json`、`tailwind-v4.css`、`preview/`），改为指向 `apps/web/design-system/`
   的对应文件或冻结来源的 `guidelines/`。**本任务只改这些链接，不改叙述**——权威顺序和来源
   表述属于 TASK-058。

**保留集（以此为准，不以删除集为准）**：`source/`、`source-lock.json`、`tools/source-lock.mjs`、
`NOTICE.md`、`LICENSE.open-design`、`LICENSE.lucide`、`README.md`、`components.manifest.json`、
`manifest.json`。

## 完成标准

- [x] 上述文件已删除，保留集完整存在。
- [x] `git grep` 全仓库，被删路径的引用数为 0。
- [x] `docs/design-system/README.md` 与 `manifest.json` 不再引用任何已删除文件。
- [x] OpenDesign Apache-2.0、Lucide、字体许可原文与 `NOTICE.md` 的归因链完整，未因删除而缺失。
- [x] `node docs/design-system/tools/source-lock.mjs --check` 通过，99 文件清单与摘要未变。
- [x] `apps/web` 目录下没有任何文件改动。
- [x] `python3 scripts/docs_test.py` 通过，仓库中不存在指向被删文件的 markdown 链接。
- [x] `docs/design-system.md` 的叙述段落（权威顺序、来源关系、章节结构）未被修改，只动链接目标。

## 验证

```bash
git grep -n "design-system/tokens\|design-system/theme-contract\|design-system/themes\.\|design-system/preview\|design-system/tailwind-v4\|design-system/design-tokens\|design-system/tools/build\|design-system/tools/check"
node docs/design-system/tools/source-lock.mjs --check
```

隔离验证（AC-003）：把 `docs/` 临时移出仓库后执行 Web 全套命令，确认与移出前一致。

```bash
mv docs /tmp/cherry-docs-isolation
cd apps/web && npm ci && npm run check && npm run build && npm run storybook:build && npm run test:e2e
cd ../.. && mv /tmp/cherry-docs-isolation docs
```

## 风险

删除量较大，可能连带删掉唯一保存许可或归因的文件。执行时按保留集正向确认，而不是按删除集
逐个删。若引用扫描发现某个被删文件确有独立读者（例如外部评审流程引用了 `preview/`），
停下来记入 `00-work.md` 待确认项并升级 DESIGN，不要擅自保留副本。

## 执行记录

- 2026-09-03：创建任务。
- 2026-09-03：实施完成。删除 14 个文件（6 个与代码树字节相同的 token/主题/合同文件、tailwind adapter、
  design-tokens.json、两个工具、4 个 preview HTML）。改写 `docs/design-system/README.md`（权威顺序
  3 层、文件角色表只剩 5 行、维护命令只剩 source-lock）与 `manifest.json`（files 12 → 8、
  entrypoints 重定向到代码树、移除 themes.manifest 的 managedFiles）。修正 `docs/design-system.md`
  的 5 处死链。隔离验证：`docs/` 移出后 `npm run check`（32 文件 116 测试）、`build`、
  `storybook:build`、`test:e2e`（30/30）全部通过。`source-lock.mjs --check` 通过，99 文件未变；
  许可与 NOTICE 完整。主题值副本由 7 个文件降到 3 个，剩余 `docs/design-system.md` 的散文复述
  交由 TASK-058 清理。
- 2026-09-03：扩大 `write_paths` 增加 `docs/design-system.md`。原因：引用扫描发现
  `scripts/docs_test.py:68` 校验 markdown 链接目标存在，而 `docs/design-system.md` 有 5 处链接
  指向本任务要删除的文件。若留给 TASK-058，本任务的提交会让文档检查变红，违反"每个提交都能
  独立通过检查"。阶段边界据此修正并同步进 PLAN-023：阶段 2 删除并恢复自洽，阶段 3 改写叙述。
- 2026-09-03：状态变更：todo → ready。原因：TASK-056 已完成，Web 侧值锚点已建立
- 2026-09-03：状态变更：ready → doing。原因：开始阶段 2：删除 docs 树可执行副本
- 2026-09-03：状态变更：doing → done。原因：阶段 2 完成：docs 树可执行副本已删除，隔离验证确认 Web 完全不依赖 docs
