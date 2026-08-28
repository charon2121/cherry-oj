---
id: "EXPERIENCE-007"
type: "experience"
title: "建立 Web 设计系统代码基建"
status: "deprecated"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["CAPABILITY-006"]
related: []
implements: []
verifies: []
deprecated: "direct-docs 开发流程已被 DESIGN-014 的本地代码包流程替代"
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# EXPERIENCE-007：建立 Web 设计系统代码基建

## 体验类型

主要是开发与评审体验，同时包含全站默认主题变化带来的用户体验。它不新增一条业务流程，而是让已有和
未来页面都从相同的主题、组件、状态与无障碍基线开始。

## 入口与主流程

开发者开始 UI 工作时：

1. 先读 `docs/design-system.md` 与组件 manifest，不从截图反推 token。
2. 页面只组合 `src/components/ui` 和语义 utility；缺组件时先补共享合同实现与 Story。
3. 在 Storybook 全局工具栏切换黑白主题，逐个检查正常、交互、禁用、加载、错误、长中文和窄屏。
4. 运行 Web `check`；它同时检查 docs 设计系统、manifest 派生运行时、源码禁用模式、类型和组件行为。
5. 涉及真实页面时再运行生产构建与 Playwright，确认首屏主题和现有业务链路。

用户首次访问时直接看到默认黑色；以后若某个产品入口调用主题 API 选择浅色，偏好会在刷新和同源标签页
中保持。当前工作不显示该产品入口。

## 异常状态

- 缺失或空偏好：直接使用默认黑色，不显示错误。
- 未知旧值或损坏值：忽略并尽可能清理，直接使用默认黑色。
- localStorage 读取失败：首屏仍为默认黑色；页面和组件继续可用。
- localStorage 写入失败：本标签页仍应用所选主题；调用方可获知未持久化，但不能造成白屏。
- 首屏脚本缺失或被策略阻止：HTML 的默认黑色属性生效，React 启动后用同一 resolver 收敛。
- manifest/生成物漂移、raw color、旧 `.dark` 或 theme-id 分支：开发检查明确失败并报告路径。
- 组件的 loading/disabled 不触发原动作；InlineNotice/AsyncState 提供可读状态和恢复操作。
- 页面 pending、empty、error、unauthorized、not-found 与 success 仍由现有 Router/Query/业务组合承担；
  本工作不改变这些状态的业务判据。

## 交互与文案

共享组件使用现有中文产品文案，不用颜色代替含义。按钮 loading 保持尺寸并显示可读进度，危险操作使用
明确危险动词和图标，正文链接常驻下划线。Storybook 的主题名称来自 manifest label，生产端不暴露
开发术语、theme id 或未交付的切换入口。

## 可访问性

- 所有交互使用语义元素和可访问名称；Button 支持 Enter/Space，Dialog 支持 Escape、焦点约束与恢复，
  Field 的 label/description/error 正确关联。
- 焦点遵循 2px outline + 2px offset，不用透明 glow 作为唯一提示；disabled/placeholder 不再叠 opacity。
- 两主题都检查正文、控件边界、状态、品牌与 danger 的实际组合；结论同时有文字、code、图标或结构。
- Story 覆盖 320px、长中文和键盘顺序；reduced-motion 关闭非必要位移，forced-colors 使用系统色。
- 主题变化不重建业务 DOM 或抢走焦点；只更新根元素属性和语义 token。

## 调试与恢复

生成脚本能打印 manifest、生成文件或非法模式的具体路径。浏览器调试时只需检查根元素
`data-theme`、派生的 `data-color-scheme` 和 localStorage key `cherry-oj.theme`，不用追踪组件分支。
Storybook 可独立复现组件状态。回退按三个 TASK 逆序进行：先恢复页面消费者，再恢复共享组件，最后恢复
主题运行时；没有数据迁移，清除或忽略偏好后始终可回到默认黑色。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：开发、评审、异常恢复与无障碍体验已形成草案，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准 WORK-017 的开发、评审与无障碍体验范围
- 2026-08-28：废弃：direct-docs 开发流程已被 DESIGN-014 的本地代码包流程替代
