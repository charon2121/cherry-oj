---
id: "DECISION-011"
type: "decision"
title: "建立 Cherry OJ Web 设计系统"
status: "approved"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["DESIGN-012"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# DECISION-011：建立 Cherry OJ Web 设计系统

## 要决定什么

在用户已确认“默认黑色 + pure-white + 可扩展主题”的方向上，决定具体主题合同、两套色值、
Tailwind/shadcn 适配边界、默认/未知主题行为和旧 UI 合同的替代方式。

## 背景

来源 OpenDesign Linear fixture 只有可执行黑色 token，浅色仅有零散 neutral 说明。仓库旧长期规则仍是
蓝紫主交互，Web 当前 token 又是另一套黑白 primary。若不冻结主题合同，新增浅色会成为第四套真源；
若直接复用暗色颜色，`#f9667a`、`#eab308` 等在白底又不能承担可访问文字或必要图形。

## 候选方案

- **A（推荐）**：`cherry-black` 保留 Linear-derived 黑色并作为 `:root` 默认；`pure-white` 完整实现
  同一 semantic contract；Foundation 全主题共享，主题 CSS 完整映射颜色/elevation，manifest 登记
  metadata，组件与 Tailwind 不识别 theme id。
- **B**：保留旧 `:root` 浅色/`.dark` 模式，只换 primary。迁移小，但不满足默认黑色、来源保真与
  可扩展合同。
- **C**：在组件内为 black/white 写条件分支。初期直观，但未来每个主题都会扩大组件状态矩阵。
- **D**：浅色使用滤镜或少量 override。无法保证状态、边界、focus、overlay 和 shadow 完整，未知
  token 会混入黑色值。

## 决定

采用方案 A，具体以 DESIGN-012 为准：

- 稳定 id 为 `cherry-black`（默认）和 `pure-white`；缺失、空值、未知 id 均回退黑色。
- `data-theme` 是唯一选择真源；`data-color-scheme` 如需使用，只能从 manifest 派生。
- 每个主题完整实现同一 `--ds-*` semantic key 集；组件不得消费 raw palette 或枚举 id。
- Manifest 驱动生成 `tokens.css` 聚合入口与机器快照；新增主题不手改聚合入口。
- Tailwind v4 只注册一次 utility；品牌 CTA 映射 shadcn `primary`，中性 hover 才映射 shadcn
  `accent`，focus/brand/danger 各自独立。
- pure-white 使用 `#ffffff` 画布和抬升面，并以 `#f7f8f8/#f5f6f7/#f3f4f5` 建立层级；浅色
  品牌 link/focus 使用 `#c01242`，不复用暗色亮粉。
- Status 拆为 foreground/surface/border/solid/on-solid；品牌与 danger 始终有非颜色区分。

用户已经批准方案方向，但尚未审核具体色阶、selector、manifest 和交付文件，因此本 DECISION 保持
`review`。在人工明确批准 DESIGN-012 并允许执行 TASK-021 前，不修改 `docs/` 全局事实。

## 理由

方案 A 保留用户指定来源的默认黑色，同时避免把 pure-white 做成脆弱的反色补丁。完整主题合同让未来
主题成为“新增一份映射和 metadata”，而不是“改一遍组件”。独立 adapter 还消除了 Linear 品牌
`accent` 与 shadcn 中性 `accent` 的同名双义。

## 影响与风险

批准后将替代 `docs/frontend.md` 的“蓝紫主交互、Cherry 红仅品牌点缀”规则，并让旧
`docs/ui-system.html` 退出规范真源角色。当前 Web 仍与新默认主题、字体和 token 不一致，本任务只
标记“待迁移”。

最大视觉风险仍是 Cherry magenta 与 danger red 接近；危险动作必须使用图标、明确动词和必要确认，
verdict 显示 code、名称与形状。主题合同的兼容风险通过 required-key 完整性、默认/未知回退和 semantic
versioning 控制。

## 重新考虑条件

正式 Cherry 品牌规范到来、用户测试持续混淆品牌与 danger、对比度不达标、需要删除/改变合同字段、
默认主题需要改变、Linear fixture 更新基础结构或 Web 技术栈变化时重审。仅新增一个完整实现既有合同
并通过验证的主题属于兼容扩展，不需要组件级重构。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：候选方案与推荐方案已完整记录，等待人工选择
- 2026-08-27：用户确认默认黑色、pure-white 与未来扩展方向；决策草案改为双主题合同，具体值与执行
  授权仍待人工审核
- 2026-08-27：状态变更：review → approved。原因：用户已确认采用默认黑色加 pure-white 的合同驱动方案
