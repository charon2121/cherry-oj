---
id: "DESIGN-029"
type: "design"
title: "收敛设计系统为单一真源"
status: "checked"
work: "WORK-035"
owners: ["claude/root"]
depends_on: ["CHANGE-010"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# DESIGN-029：收敛设计系统为单一真源

## 背景

上游定义见 [CHANGE-010](./10-change-CHANGE-010.md)。要解决的是结构问题：同一批设计值分布在
`docs/design-system/`、`apps/web/design-system/` 和两份 `check.mjs` 的手抄期望值表中，共 7 处非
来源副本，且没有任何自动一致性保障。

## 目标与限制

目标是让"值"在仓库里只有一处手写定义，其余全部是生成物或冻结来源；同时不改变任何渲染结果。

硬限制：

- Web 侧的 `npm ci / dev / build / check / storybook / e2e` 不得读取 `docs/`（既有约束，
  由 `scripts/check-design-system.mjs` 的 `forbidden-docs-import` 规则强制）；
- 冻结来源与 `source-lock.json` 不可改动；
- 主题渲染结果逐值不变。

## 整体方案

收敛后的三层职责：

```text
docs/design-system/source/claude-design-v1/   冻结来源（99 文件，只读证据）
        │ source-lock.mjs --check 锁定
        ▼
docs/design-system.md + docs/design-system/   人读：原则、组件合同、来源链、许可
        │ 引用（不复制）
        ▼
apps/web/design-system/                       唯一可执行真源
        tokens.foundation.css   ─┐ 手写
        themes/*.css            ─┘
                │ build.mjs
                ▼
        tokens.css              ─┐ 生成，提交，--check 锚定
        design-tokens.json      ─┘
                │ check.mjs（不持有值）
                ▼
        结构 / 同构 / 对比度 / 生成物一致性
```

关键设计点：**把"值的漂移检测"从手抄表迁移到生成快照**。

现状是 `check.mjs` 里写死了两个主题的完整期望值表（`apps/web/design-system/tools/check.mjs:236-360`）。
这份表由人手写，与被校验的 `themes/*.css` 同源同批，因此不构成独立证据，且必须两树各存一份。

改为：`build.mjs` 从 `tokens.foundation.css` + `themes/*.css` 解析出全部解析后的值，生成
`design-tokens.json` 并提交；`build.mjs --check` 比较磁盘上的快照与重新生成的结果。任何主题值
改动都会让 `--check` 失败，必须显式重新生成快照——这一步在 diff 中可见、可复核，且值仍然只有
一处手写来源。

`design-tokens.json` 的生成逻辑已存在于 `docs/design-system/tools/build.mjs:142`，本次把它移入
Web 侧 `build.mjs`，docs 侧连同 `build.mjs` 一并删除。

## 模块与数据

删除（`docs/design-system/`）：

| 文件 | 处置 | 理由 |
|---|---|---|
| `tokens.foundation.css`、`themes/*.css`、`theme-contract.json`、`themes.manifest.json`、`tokens.css` | 删除 | 与 Web 侧字节相同，纯副本 |
| `tailwind-v4.css` | 删除 | Web 侧有权威版本，两者已相差 2 行 |
| `design-tokens.json` | 删除 | 生成物迁到 Web 侧 |
| `tools/build.mjs`、`tools/check.mjs` | 删除 | Web 侧保留唯一实现 |
| `preview/*.html` | 删除 | 是 `source/guidelines/` 20 张卡片的劣化子集 |

保留（`docs/design-system/`）：`source/`、`source-lock.json`、`tools/source-lock.mjs`、
`NOTICE.md`、`LICENSE.open-design`、`LICENSE.lucide`、`README.md`、`components.manifest.json`、
`manifest.json`（改写为只登记保留下来的文件）。

新增/修改（`apps/web/design-system/`）：

- `tools/build.mjs`：增加 `design-tokens.json` 生成；沿用既有确定性输出与 `--check` 机制；
- `design-tokens.json`：新增生成物，提交入库；
- `tools/check.mjs`：删除手写期望值表及其消费逻辑，保留结构性校验（见下）；
- `manifest.json`：把 `design-tokens.json` 登记为 `generated: true` 的包文件；
- `README.md`：删除散文中复述的具体色值，改为指向 `themes/*.css`。

`check.mjs` 中需要删除的手写期望值表共三张，它们都是被校验文件的手抄副本：

| 表 | 抄自 | 替代锚点 |
|---|---|---|
| `expectedFoundationTokens` | `tokens.foundation.css` | `design-tokens.json` 的 `foundation.tokens` |
| `exactThemeValues` | `themes/*.css` | `design-tokens.json` 的 `themes[].tokens` |
| `expectedThemeDefinitions` | `themes.manifest.json` | `design-tokens.json` 的 `themes[]` 元数据 |

保留的 `expectedContractShape`、`expectedContractRules`、`expectedPackageFiles`、
`expectedMinimumContrast`、`fixedSourceProvenance` 不是设计值的副本，而是策略与来源锚点：
它们表达"合同不允许被悄悄放宽"和"冻结来源摘要不变"，正是校验器应当独立持有的判断。

`check.mjs` 必须逐条保留的能力（删除值表不得连带删掉它们）：

- `theme-contract.json` 的 semantic key 完整性与主题间同构；
- 对比度阈值校验（`contrastThresholds` 与逐 key 的 `contrastClass`）；
- 主题文件路径安全（绝对路径、`..`、符号链接、真实路径越界）；
- 生成物一致性（`tokens.css`、`design-tokens.json` 与源一致）；
- Tailwind adapter 的映射完整性；
- 来源与许可 provenance 校验；
- `--self-test` 全部既有用例。

`--self-test` 中"篡改一个主题值应被拒绝"的用例（当前在 `check.mjs:1126` 硬编码
`--ds-brand-surface: #d2042d`）改为：读取主题文件中该 key 的现值，替换成一个确定的不同值，
断言校验失败，再恢复。这样自检不再硬编码任何真实值。

## 接口与状态

`apps/web/package.json` 的 `check:design-system` 链路保持相同的对外命令名与退出码语义，内部
去掉对已删除文件的依赖。文档侧新增/保留的命令只有：

```bash
node docs/design-system/tools/source-lock.mjs --check
```

`docs/design-system/README.md` 的"维护与检查"一节据此改写，不再列出 build/check。

`components.manifest.json` 的每个组件条目新增 `sourceRefs` 字段，值为仓库相对路径数组，指向
`source/claude-design-v1/` 内实际存在的 `*.prompt.md` 或 `*.card.html`。这是引用而非内容复制，
因此不产生新的副本；路径存在性由人工逐条核对并记入 VERIFY。

## 安全与失败

本次不触碰认证、权限、数据与外部接口。主要失败模式是"删除了仍在被使用的文件"，防护方式是
删除前先做全仓库引用扫描（`git grep` + CI 配置 + `scripts/` 下的文档检查），扫描结果作为
AC-001 的证据保存。

第二个失败模式是"漂移检测退化成恒真"。`build.mjs --check` 必须比较**磁盘上已提交的快照**与
**从源重新生成的结果**，而不是生成两次再比较。AC-005 用一次真实篡改来验证这一点。

## 监控与部署

无生产环境，无部署动作。改动全部落在仓库文件与本地/CI 检查命令上。

## 迁移与兼容

无数据迁移。删除的文件在 git 历史中完整可取回。改动分三个可独立编译、可独立回滚的提交，
顺序见 [PLAN-023](./50-plan-PLAN-023.md)。

顺序上必须**先**让 Web 侧具备 `design-tokens.json` 与去值化的 `check.mjs`，**后**删除 docs 副本。
反过来会出现一个中间状态：docs 的值锚点已删、Web 的还没建，此时任何值改动都无人检测。

## 备选方案

**备选一：保留两棵树，增加 CI 跨树一致性检查（drift gate）。**
不采用。这会把"两份副本"制度化，且与 `docs/design-system.md` §1 明确写下的"普通 Web CI 不比较、
复制或链接代码树与文档树"直接冲突。真正的问题是副本本身，不是副本没对齐。

**备选二：把真源放在 `docs/`，Web 侧构建时复制或 symlink。**
不采用。Web 必须能在没有 `docs/` 的情况下独立构建（REQ-010，且是 WORK-018 已确认的结论）。

**备选三：从 `source/tokens/*.css` 机器生成 `themes/cherry-black.css`。**
不采用。看起来更"忠于来源"，实际收益很低：暗色值经核对已与来源一致，而 `pure-white` 在来源中
没有对应物、必须手写，机器生成只能覆盖一半，反而会造出"半生成半手写"的第三种状态。来源与
生产值的一致性由人在评审时对照 `source/tokens/colors.css` 确认即可。

**备选四：连 `components.manifest.json` 一起删除，组件合同只写在 `docs/design-system.md`。**
不采用。它是 WORK-036 构图层的直接输入，且当前 619 行内容有实际价值；本次只给它补上到来源的
引用，重构留给 WORK-036。

## 风险与重审条件

- 重写 `check.mjs` 时漏掉某项结构校验，会静默降低门禁强度。缓解：上面已列出必须保留的能力清单，
  VERIFY 逐条对照；`--self-test` 用例数不得减少。
- `design-tokens.json` 的生成结果如果包含时间戳或不稳定排序，会导致 `--check` 每次都失败。
  缓解：沿用 Web 侧 `build.mjs` 既有的确定性输出约定（固定 key 顺序、无时间戳）。
- 若后续发现 docs 树的某个被删文件确有独立读者（例如外部评审流程），应重新考虑该文件的归属，
  但仍不得恢复为可执行副本——正确做法是从 Web 真源生成一份只读视图。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：技术方案已写完，等待工具校验
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
