---
id: "PLAN-023"
type: "plan"
title: "收敛设计系统为单一真源"
status: "checked"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["CHANGE-010", "DESIGN-029"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# PLAN-023：收敛设计系统为单一真源

## 目标

按 [DESIGN-029](./30-design-DESIGN-029.md) 把设计值收敛到 `apps/web/design-system/` 一处手写来源，
删除 `docs/` 侧的可执行副本，并让文档叙述与实际结构一致。全程不改变渲染结果。

## 改动区域

- `apps/web/design-system/tools/build.mjs`、`tools/check.mjs`、`README.md`，新增 `design-tokens.json`；
- `docs/design-system/`：删除可执行副本与自制 preview，改写 `README.md`、`manifest.json`，
  为 `components.manifest.json` 补来源引用；
- `docs/design-system.md`、`CLAUDE.md`、`AGENTS.md` 的叙述与路由。

不进入本次范围：`apps/web/src/**`、`apps/server`、`apps/judge-engine`、`contracts/`。

## 阶段与顺序

顺序不可调换，理由见 DESIGN-029「迁移与兼容」。

**阶段 1 — 在真源侧建立值锚点（TASK-056）**
1. `build.mjs` 增加 `design-tokens.json` 生成，沿用既有确定性输出与 `--check` 比较机制；
2. 生成并提交 `apps/web/design-system/design-tokens.json`；
3. 从 `check.mjs` 删除 `:236-360` 的期望值表及消费逻辑，保留 DESIGN-029 列出的全部结构性校验；
4. `--self-test` 的篡改用例改为从主题文件读取现值再替换，不硬编码真实值；
5. `README.md` 删除复述的具体色值。

出口条件：`npm run check:design-system` 通过；篡改一个主题值会让 `build.mjs --check` 失败。

**阶段 2 — 删除 docs 侧可执行副本（TASK-057）**
1. 先做全仓库引用扫描（`git grep` + `.github/` + `scripts/`），记录被删路径的引用点；
2. 删除 `tokens.foundation.css`、`themes/`、`theme-contract.json`、`themes.manifest.json`、
   `tokens.css`、`tailwind-v4.css`、`design-tokens.json`、`tools/build.mjs`、`tools/check.mjs`、`preview/`；
3. 改写 `docs/design-system/README.md`：文件角色表只列保留文件，维护命令只剩 `source-lock.mjs --check`，
   视觉参考指向 `source/claude-design-v1/guidelines/`；
4. 改写 `manifest.json`，只登记保留下来的文件。

出口条件：把 `docs/` 临时移走后 Web 全套命令仍通过；被删路径的仓库引用数为 0。

**阶段 3 — 叙述与引用对齐（TASK-058）**
1. `docs/design-system.md` 权威顺序由 6 层降为 3 层，来源叙述改写为与 git 历史一致的事实；
2. `components.manifest.json` 每个组件条目补 `sourceRefs`，指向来源中实际存在的
   `*.prompt.md` / `*.card.html`；
3. `CLAUDE.md`、`AGENTS.md` 的设计系统路由指向新结构。

出口条件：`scripts/work check` 与文档检查通过；文档中无指向已删除文件的链接。

## 并行与依赖

三个阶段严格串行：阶段 2 依赖阶段 1 建立的值锚点，阶段 3 依赖阶段 2 已确定的最终文件集合。
阶段内部的步骤可以合并进同一次提交。

## 迁移与交付

无数据迁移。每个阶段一个提交，每个提交都能独立编译并独立通过 `npm run check`。
提交标题使用 Conventional Commits，正文写清"不做这次收敛会怎样"和被删文件的去向。

交付方式为直接合入 `main`（仓库当前无发布流程）。用户签署验收闸后才算完成。

## 风险

- 阶段 2 的删除量大，可能连带删掉唯一保存许可或归因的文件。缓解：删除清单在 DESIGN-029 中已
  逐项列出保留集，执行时以保留集为准而非以删除集为准。
- 阶段 1 重写 `check.mjs` 可能静默削弱门禁。缓解：以 DESIGN-029 的"必须保留的能力"清单逐条核对，
  且 `--self-test` 用例数不得减少。
- 阶段 3 改动 `CLAUDE.md` / `AGENTS.md` 属于协作规则文件，改错会影响后续所有会话。缓解：只改
  设计系统相关的路由行，不动其他条目。

## 验证

- 阶段 1：`node design-system/tools/build.mjs --check`、`node design-system/tools/check.mjs`、
  `node design-system/tools/check.mjs --self-test`、`npm run check:design-system`；
  外加一次真实篡改验证（AC-005）。
- 阶段 2：`git grep` 引用扫描；`mv docs /tmp && npm ci && npm run check && npm run build &&
  npm run storybook:build && npm run test:e2e && mv /tmp/docs .`（AC-003）。
- 阶段 3：`scripts/work check`、仓库文档检查、逐条核对 `sourceRefs` 路径存在。
- 全局：改动前后 `dist/` 中 token 的最终解析值逐值相同（AC-007）。

结果逐条记入 [VERIFY-036](./70-verify-VERIFY-036.md)。

## 回退

每个阶段一个提交，`git revert` 即可单独回退。阶段 2 删除的文件全部在 git 历史中，
`git checkout <commit>^ -- docs/design-system/...` 可完整恢复。没有数据或外部状态需要回滚。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：三阶段计划已写完，等待工具校验
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
