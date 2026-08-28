---
id: "PLAN-012"
type: "plan"
title: "建立 Cherry OJ Web 设计系统"
status: "approved"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["CAPABILITY-005", "EXPERIENCE-006", "DESIGN-012", "DECISION-011"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# PLAN-012：建立 Cherry OJ Web 设计系统

## 目标

在 DECISION-011 与 DESIGN-012 获人工批准后，把默认 `cherry-black`、`pure-white` 和可扩展主题
合同落成位于 `docs/` 的自包含 Cherry OJ 设计系统，收敛旧视觉规则；不迁移 Web 运行时代码。

## 改动区域

- `docs/design-system.md`：规范真源、主题/组件/例外规则。
- `docs/design-system/`：许可、来源、Foundation、theme contract、两套主题、adapter、manifest 与参考。
- `docs/README.md`、`docs/frontend.md`、`docs/ui-system.html`：入口、长期规则和兼容关系。
- `CLAUDE.md`：只增加未来 UI 工作必须读取设计系统的入口。
- 当前 WORK：记录决策、执行和验证证据。

## 阶段与顺序

0. 人工审核：确认 DESIGN-012 的 pure-white 色值、Cherry/danger 映射、主题 selector、manifest、
   Tailwind/shadcn 边界与文件结构，并明确允许 TASK-021 执行。
1. 固定来源：记录 fixture 路径、Apache-2.0、非官方性质、校验值和两类 allowlist。
2. 建立合同：先写 `tokens.foundation.css`、schema 完整的 `theme-contract.json`、
   `themes.manifest.json` 与 build/check 工具，冻结 required semantic key、允许组合和门槛。
3. 建立主题：完整实现 `themes/cherry-black.css` 与 `themes/pure-white.css`；不得依赖主题间颜色
   fallback，raw primitive 与 semantic 分离。
4. 建立适配：编写一次性的 `tailwind-v4.css`，显式区分品牌 primary、shadcn 中性 accent、ring 与
   destructive。
5. 生成参考：由 manifest 生成 `tokens.css` import bundle、JSON 快照，再派生组件清单、双主题
   components 和 themes/colors/typography/spacing 预览。
6. 收敛入口：更新全局 docs 与 `CLAUDE.md`，替代旧蓝紫规则和旧 UI 真源。
7. 验证与记录：执行格式、完整性、差异、对比、交互、视觉、许可、链接和范围检查，在 VERIFY/MEMORY
   写实际证据与仍待迁移项。

## 并行与依赖

Foundation、contract 和 manifest 必须先冻结，两套主题随后可并行；adapter 依赖 semantic key 稳定；
JSON/HTML 依赖两主题完成；入口收敛依赖新规范可读。任何需改变合同、默认主题或非 allowlist 黑色值的
发现先回到 DESIGN/DECISION，不在派生文件中修补。

## 验证

- 主题选择：缺失 `data-theme`、显式 `cherry-black`、`pure-white`、unknown id 四种场景；前三种
  预期分别为黑/黑/白，unknown 必须完整回退黑色。
- 完整性：每个 manifest 主题精确实现 required key；build 后聚合入口与 manifest 一致且 check 无
  漂移；组件、Tailwind 和 HTML 不枚举 theme id，只有 manifest/主题 CSS selector 例外。
- 来源差异：`cherry-black` 非品牌 raw 值与固定 Linear 快照对照；pure-white 按合同和 light-neutral
  证据验证，不做暗色逐值相等。
- 可访问性：展开合同 `allowedOn` 的全部组合，两个主题分别检查正文/placeholder ≥4.5:1，必要图形/
  边界/focus ≥3:1；opaque brand/status soft、link 与 selection 使用规定组合且不四舍五入。
- 组件：两个主题分别检查桌面与 320px、键盘 focus、hover/pressed/disabled/loading/error、长中文和
  reduced-motion；verdict 与 danger 具备非颜色表达。
- 适配：逐项对照 DESIGN-012 的完整 alias 表，确认 shadcn `accent`/`accent-foreground` 为中性 pair、
  `primary` 为品牌 CTA、`destructive` 为 danger，`dark:` 不枚举 theme id。
- 工程：解析 JSON，校验 CSS/JSON/manifest 引用、内部链接、逐文件修改声明/JSON provenance、
  Apache-2.0、紫色残留和 git 范围。

## 迁移与上线

本任务是版本库文档交付，无独立生产部署。release 是把已批准规范合并到默认分支；observe 是在干净
克隆复查入口、链接、派生产物，并在首个后续组件工作中确认合同可采用。合并后规范成为未来设计基线，
但当前 Web 仍标记“待迁移”。主题 resolver、localStorage、首屏防闪、字体、现有 token/组件和页面迁移
另建 TASK。

## 风险

风险是主题 key 缺失导致黑白混用、组件按 id 分支、shadcn accent 同名误映射、亮粉/黄色在白底对比
不足、品牌与 danger 混淆、来源归属缺失、派生 HTML 漂移和越界修改 Web。分别由完整主题声明、扫描、
adapter 边界、逐组合对比、非颜色编码、逐文件 notice、派生校验与 TASK write/forbidden path 控制。

## 回退

文档提交可整体回退：恢复旧 `docs/frontend.md`/`docs/ui-system.html` 并移除新入口和设计系统目录，
无数据迁移。若后续代码已消费新 token，则由对应 Web 迁移任务提供双写或组件级回退，不把运行时风险
追溯性地塞回本计划。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：仅在人工批准后执行的文档交付计划已形成草案，提交人工审核
- 2026-08-27：按用户确认增加 pure-white、完整主题合同、adapter 和四种主题选择验证，继续等待审核
- 2026-08-27：状态变更：review → approved。原因：用户已审核实施顺序、验证矩阵并明确允许执行
