---
id: "CAPABILITY-006"
type: "capability"
title: "建立 Web 设计系统代码基建"
status: "deprecated"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["CAPABILITY-005"]
related: []
implements: []
verifies: []
deprecated: "WORK-017 未通过自包含验收；新的构建边界由 CHANGE-008 定义"
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---





# CAPABILITY-006：建立 Web 设计系统代码基建

## 为什么需要

CAPABILITY-005 已经建立可执行的设计系统合同，但 Web 仍使用另一套全局 token、字体、主题选择和组件
状态。文档真源如果没有运行时接入、共享组件与持续门禁，就无法约束真实页面，也无法让后续功能稳定
复用。本能力负责把已批准合同变成 Web 的公共基础设施，而不是重新设计颜色或业务页面。

## 使用者

- Web 开发者：从稳定主题 API、语义 utility 和共享组件开始实现页面。
- 设计与产品评审者：在 Storybook 中用同一组件矩阵检查黑白主题、状态和窄屏。
- 测试与维护者：用生成物漂移、源码扫描、组件测试和浏览器测试发现合同偏差。
- 后续 Agent：通过 TASK 的路径边界消费既有合同，不在业务功能中重造 token 或主题逻辑。

## 能力

- REQ-001：Web 构建直接消费 `docs/design-system/tokens.css` 和
  `docs/design-system/tailwind-v4.css`；运行时代码不得复制可手改的主题值或 Tailwind adapter。
- REQ-002：默认和 fallback 均为 `cherry-black`，`pure-white` 只在显式有效偏好下启用；缺失、
  空白、未知或损坏值必须在首次绘制前归一化为默认黑色，不跟随操作系统。
- REQ-003：`data-theme` 是唯一主题选择真源；`data-color-scheme` 只从 manifest 派生，不单独
  存储。只有生成注册表和主题基础模块能识别 theme id，组件、页面和 CVA variant 不得枚举它。
- REQ-004：提供 manifest 驱动、类型安全的主题注册表、resolver、持久化与 React 消费 API；存储不可用
  时当前会话仍可切换，跨标签页变化可收敛，首屏脚本与 React 不各自维护规则。
- REQ-005：移除旧浅色 `:root`、`.dark`、OKLCH token、Geist 和未使用的紫色脚手架合同；随站点
  交付 Inter Variable，保留合同规定的中文与等宽字体回退。
- REQ-006：Tailwind/shadcn 只消费已批准语义 alias；primary、neutral accent、brand、focus、
  destructive 和各类 status 保持分义，禁止 raw color、primitive、必要对比 `color-mix()`、
  disabled/placeholder opacity 和主题专用样式分支。
- REQ-007：建立基础组件代码层：Button、Field（Input/Textarea/Select 与 label/error 关联）、
  Link style、IconButton、Badge、Card/Panel、Dialog/Popover、Typography/Layout、InlineNotice 和
  AsyncState；每个组件按 manifest 覆盖 anatomy、size、variant、state、token 与键盘行为。
- REQ-008：Storybook 使用 manifest 派生的全局主题工具栏和统一 decorator；Story 不手写
  `cherry-black`、`pure-white` 或 `.dark`，并覆盖双主题、交互状态、长中文和 320px。
- REQ-009：迁移当前 Web 壳与真实消费者使用共享组件和语义 token，保持 API 请求、缓存、权限、路由、
  表单语义与可见业务文案不变。
- REQ-010：Web `check` 必须包含设计系统包校验、主题生成物漂移和源码静态扫描；CI 继续执行组件
  测试、生产构建、Storybook 构建和浏览器主题/业务回归。
- REQ-011：默认黑、显式白、空值、未知值、存储异常、首屏无闪烁、reduced-motion、forced-colors、
  键盘、320px 和长中文都有可重复验证；必要文字/控件对比继续受主题合同门禁保护。
- REQ-012：本能力不实现 OJ 业务组件。首次实现 Verdict 前必须让组件 manifest 与
  `contracts/verdict.json` 精确一致并包含 `PE/OLE/RAN/SE`；not-found 由 Router 还是 AsyncState
  承担也必须先在对应设计中明确。

## 接入方式

应用入口加载设计系统 CSS，并在 React 渲染前由生成的首屏脚本设置根元素主题。React 通过
`src/lib/theme` 的稳定 API 读取或设置主题；共享组件只使用 Tailwind semantic alias 或 canonical
`--ds-*` token。设计者从 Storybook 全局主题工具栏审核同一 Story 的黑白状态。

## 输入与输出

输入是已批准的 DESIGN-012/DECISION-011、`themes.manifest.json`、theme contract、组件 manifest
和当前 Web 实现。输出是 Web 的 CSS 接线、主题生成物与运行时、基础组件、Storybook 状态矩阵、现有
页面迁移、测试与持续门禁。设计 token 数值、主题 id 和公共业务契约不由本工作修改。

## 限制与失败

- manifest 或设计系统派生产物无效时，生成/检查必须失败并阻止构建，不能静默使用旧副本。
- localStorage 缺失、抛错或包含未知值时，页面仍以默认黑色可用；存储失败不应让设置主题的 UI 崩溃。
- 首屏脚本加载失败时，HTML 上的默认黑色属性是安全回退；React 启动后继续使用同一 resolver 收敛。
- 组件需要新增语义 token、改变主题合同或修改默认主题时停止当前 TASK，先升级设计系统 WORK。
- 当前生产页面不提供主题切换入口；API 与持久化能力供 Storybook、测试和后续产品功能使用。

## 质量要求

- 首屏主题脚本小、同步且无网络 fetch；CSS 在构建时打包，生产环境不依赖 `docs/` URL。
- 普通文字与必要控件继续满足设计合同的 WCAG 门槛；交互支持键盘、可访问名称与错误关联。
- 同一组件在所有 manifest 主题下保持相同 DOM 语义和状态模型；320px 不裁掉核心操作。
- 生成与扫描命令输出具体漂移文件/规则，便于本地和 CI 定位；不得依赖只看截图的人工记忆。
- 最终证据使用 Node 24 / npm 11，与 CI 一致；Node 26 的本地诊断不代替正式验证。

## 升级与迁移

按主题运行时、共享组件、现有页面三段推进。未来新增主题只更新 docs 真源和 manifest，再重新生成注册表；
不修改组件。未来增加用户可见主题选择器、OJ 业务组件或改变组件默认行为时建立独立 product/infra WORK。
Base UI 作为既有 primitive 继续使用，批准后同步修正 `docs/frontend.md` 的 Radix 旧描述。

## 不做什么

- 不修改设计 token 数值、theme contract、默认 theme id、服务端、判题引擎或公共 API 契约。
- 不新增题库、编辑器、提交、判题详情等业务页面，也不实现 Verdict/Submission lifecycle 组件。
- 不引入 next-themes、Zustand、第二套组件库或另一套 primitive。
- 不在生产导航加入用户可见主题切换器，不宣称主题产品功能已交付。
- 不把 Storybook、截图或业务 class 反向当成设计系统真源。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：能力范围、唯一真源、主题运行时与明确非目标已形成草案，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准 WORK-017，并允许按 DECISION-012 执行 TASK-023～TASK-025
- 2026-08-28：废弃：WORK-017 未通过自包含验收；新的构建边界由 CHANGE-008 定义
