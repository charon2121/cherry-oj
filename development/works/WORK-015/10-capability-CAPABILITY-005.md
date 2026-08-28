---
id: "CAPABILITY-005"
type: "capability"
title: "建立 Cherry OJ Web 设计系统"
status: "approved"
work: "WORK-015"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---



# CAPABILITY-005：建立 Cherry OJ Web 设计系统

## 为什么需要

当前 `docs/frontend.md`、`docs/ui-system.html` 与 Web 实际 token 对“主色、主题和组件外观”的表达不同。
项目需要一份长期、可追踪、机器可读且能直接指导组件评审的视觉合同，避免每个页面重新决定颜色、
密度和状态。

## 使用者

- 产品和交互设计者：选择已有模式、设计新页面并说明必要例外。
- Web 开发者：把语义 token、组件变体和页面模板映射到项目组件。
- 评审者与测试人员：按状态矩阵、响应式和可访问性规则验收。
- 后续自动化 Agent：先读取规范与 manifest，再创建或修改任何 UI 组件。

## 能力

- REQ-001：提供一个位于 `docs/`、新克隆可直接取得的设计系统规范入口，并明确其权威范围和阅读顺序。
- REQ-002：以指定 OpenDesign Linear fixture 为基底；保留其黑色主题并作为无显式偏好时的默认主题，
  除允许差异清单外不修改该默认主题的表面、文字、边框、字体度量、间距、圆角、阴影、动效、布局和
  基础组件规则。
- REQ-003：提供以 `#ffffff` 为页面画布的 pure-white 浅色主题；它必须覆盖与默认主题相同的语义
  token、组件状态和 OJ 状态，不能用滤镜、全局反色或零散组件覆盖实现。浅色沿用字体、度量和组件
  行为，但允许表面、文字、边框、焦点、overlay 与 elevation/shadow 按浅色环境适配。
- REQ-004：把 Linear 的 `accent` 默认、交互、hover、active、focus、selection、link 和 CTA 用途按
  主题映射到可访问的 Cherry 色阶；不得在组件内直接写品牌色值。
- REQ-005：定义稳定的主题合同、主题 manifest、默认/未知主题回退和新增主题流程；未来主题必须实现
  完整合同并由 manifest 生成聚合入口，组件和 Tailwind 绑定不得判断具体 theme id。
- REQ-006：在基础层之上定义 OJ 的 success、warning、danger、info、special、Submission 生命周期和
  verdict 语义；每个主题均需提供可访问映射，品牌色不得承担成功、失败或危险含义。
- REQ-007：提供 foundation/theme token CSS、Tailwind v4 绑定、机器可读 token、组件清单和按主题可
  浏览参考，且标明真源与派生产物。
- REQ-008：每个共享组件至少定义 default、hover、pressed、focus-visible、disabled、loading、error、
  长中文、窄屏和 reduced-motion 行为；这些状态在所有登记主题中都必须成立。
- REQ-009：保留 OpenDesign Apache-2.0 许可、来源和逐文件修改声明；不得使用 Linear Logo、商标或把
  fixture 描述为 Linear 官方规范。
- REQ-010：未来组件或主题若偏离规范，必须在对应 WORK/DESIGN 中说明理由、影响和回归方式，不得在
  feature 内通过任意色值、随机圆角、复制样式或主题专用分支形成隐式分叉。

## 接入方式

规范入口为计划中的 `docs/design-system.md`。设计和实现组件前按入口指定顺序读取：设计原则 →
theme contract → 当前主题 token → 组件清单/视觉参考 → OJ 语义扩展。Tailwind 只绑定语义 token；
组件变体继续使用项目统一的 CVA 与 `cn()`，交互 primitive 的选型不由视觉规范重复决定。

## 输入与输出

输入是本工作固定的 Linear fixture 快照、仓库现有 Cherry 品牌锚点、产品可访问性规则与 OJ 契约。
输出是规范 Markdown、token/manifest 文件、组件参考页面、来源/许可说明，以及对旧全局文档的明确
替代关系。下载目录只是本次证据，不是运行或阅读依赖。

## 限制与失败

- fixture 是 OpenDesign 整理样本，不是 Linear 官方或实时上游；其未覆盖的 OJ 组件只能按基础原则扩展。
- 默认主题必须保持 Linear 暗色；pure-white 是独立设计并实现完整合同，不能从暗色自动反转。
- 缺失或未知 theme id 必须安全回退到默认黑色；主题选择的持久化与首屏无闪烁属于后续运行时迁移，
  本任务只冻结合同和期望行为。
- 只有主题注册表和主题 CSS selector 可以知道具体 theme id；组件、组件清单、页面模板和 Tailwind
  utility 不得枚举 `cherry-black`、`pure-white` 或未来主题 id。
- 品牌红与 danger 相近，任何只靠颜色区分的方案均视为失败。
- Inter Variable 可作为设计字体；Berkeley Mono 只在已有合法字体时使用，必须保留系统等宽字体回退，
  不复制未获授权的字体文件。
- 若机器 token、CSS、视觉参考或规范文字不一致，停止组件实施，先修复设计系统真源与派生链。

## 质量要求

- 普通正文与控件文字达到 WCAG AA；大字、边框、焦点和非文本状态按其适用门槛检查。
- 键盘焦点清晰，状态不只靠颜色，触控目标、长中文、缩放和窄屏不丢失核心操作。
- `prefers-reduced-motion` 下移除非必要动画；基础交互使用 150–200ms 的统一节奏。
- token 名称、主题合同和 manifest 可被自动检查；每个主题都必须完整实现合同，默认黑色的非品牌
  token 与固定 Linear 快照可做差异对照。
- 文档必须自包含、内部链接有效，JSON 可解析，HTML 在桌面与窄屏均可阅读。

## 升级与迁移

批准后先建立新规范、主题合同、默认黑色与 pure-white 自包含资产，再同步 `docs/README.md` 和
`docs/frontend.md`，最后替代旧 `docs/ui-system.html`。运行时主题选择、样式、字体、现有 Button
和页面迁移不在本任务内；应从新规范建立独立 ready TASK，按组件逐步迁移并用 Storybook/视觉回归
验证。设计系统版本采用语义化变更说明：合同字段删除、token 含义变化或组件默认行为变化必须走兼容
评审；仅新增满足合同的主题可作为兼容扩展。

## 不做什么

- 不在本任务中修改 `apps/web`、安装字体/依赖或重做任何业务页面。
- 不复制 Linear Logo、产品文案和与 OJ 无关的 deck、email、newsletter、poster、marketing landing。
- 不改变 OJ 契约、业务流程、服务端或判题语义。
- 不在本任务中实现主题切换器、持久化或首屏脚本，也不把 HTML 展示页当作唯一规范。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：能力边界、要求与迁移约束已形成草案，提交人工审核
- 2026-08-27：状态变更：review → approved。原因：用户已审核并批准双主题能力范围，允许执行 TASK-021
