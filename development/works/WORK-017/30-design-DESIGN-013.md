---
id: "DESIGN-013"
type: "design"
title: "建立 Web 设计系统代码基建"
status: "superseded"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["CAPABILITY-006", "EXPERIENCE-007", "DESIGN-012", "DECISION-011"]
related: []
implements: []
verifies: []
deprecated: "Web 运行资产必须归属代码侧，设计文档不再参与构建"
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---






# DESIGN-013：建立 Web 设计系统代码基建

## 背景

CAPABILITY-005、DESIGN-012 与 DECISION-011 已确认文档设计系统：`cherry-black` 为默认、
`pure-white` 为显式浅色，56 个主题 semantic key、Tailwind/shadcn adapter 和组件 manifest 位于
`docs/design-system/`。TASK-021 明确禁止修改 `apps/web`，所以当前 Web 仍是旧浅色 `:root`、
`.dark`、Geist、重复 `@theme` 和 OKLCH token；Button 也含 theme-specific class、动态
`color-mix()`、透明 danger 与不同的 variant/state 合同。

当前 Web 已有 React 19、Vite 8、Tailwind 4、shadcn、Base UI、CVA、Lucide、Vitest、Storybook 和
Playwright，无需换框架。需要解决的是唯一真源接入、首屏主题、共享组件和门禁，而不是再选一套 UI 库。
另有两处上游漂移需要明确边界：`docs/frontend.md` 仍写 Radix，但代码已用 Base UI；组件 manifest
的 verdict 变体漏了 `contracts/verdict.json` 中的 `PE`。

## 目标与限制

目标是在不改变已批准 token/theme contract 的前提下交付：

1. 构建期唯一 CSS 真源和 manifest 派生主题运行时；
2. 默认黑、显式白、未知回退、持久化、跨标签页收敛与首屏无闪烁；
3. 设计系统第 1 层和通用第 2 层组件，以及当前页面实际需要的 Notice/AsyncState；
4. 双主题 Storybook、源码扫描、组件与浏览器回归；
5. 当前 Web 壳迁移且业务行为不变。

不新增生产主题切换器，不实现 editor、submission lifecycle、verdict、data table 等 OJ 业务组件，不修改
服务端、公共契约、token 数值、theme id 或设计系统合同。若实施必须改变这些上游事实，停止 TASK 并
升级设计，而不是在 Web 加 override。

## 整体方案

采用“docs 真源直接打包 + manifest 生成薄运行时 + 主题无关组件”：

```text
docs/design-system/
  tokens.css + tailwind-v4.css + themes.manifest.json
             │ build-time import / generation
             ▼
apps/web/
  globals.css ───────────────► Vite 打包后的单一 CSS
  scripts/generate-design-system.mjs
             ├───────────────► src/generated/design-system/themes.ts
             └───────────────► public/generated/theme-init.js
                                      │ head 中同步执行
                                      ▼
                            <html data-theme data-color-scheme>
                                      │
                      src/lib/theme（resolver/store/React API）
                                      │
                 components/ui ──────► routes/features
```

### CSS 接入

`globals.css` 保留 Tailwind、动画、shadcn、Inter 包、canonical `tokens.css`、
`tailwind-v4.css` 和项目 base layer。删除本地颜色声明、重复 `@theme`、`.dark` 与 Geist。
Vite/Storybook 只读允许仓库 `docs/design-system`，生产构建把 CSS 打进静态产物，浏览器不请求 docs URL。
`src/index.css` 及未消费的旧紫色脚手架资产退出，避免第二合同继续被搜索和复制。

### 生成与首屏

`scripts/generate-design-system.mjs` 读取 `themes.manifest.json`，验证 default/fallback、唯一 id、
color scheme 与文件存在，再生成：

- `src/generated/design-system/themes.ts`：只读 theme metadata、`ThemeId`、default/fallback 和
  storage key；
- `public/generated/theme-init.js`：无依赖的经典脚本，在 CSS/React 首次绘制前读取
  `cherry-oj.theme`、校验并同时设置两个根属性。

`generate:design-system:check` 在临时目录重建并逐文件比较；生成物禁止手改。HTML 自身声明默认
`data-theme="cherry-black"` 与 `data-color-scheme="dark"`，脚本缺失时仍安全为黑色。脚本不 fetch、
不 eval、不跟随系统主题；未知/空值回退并在可写时清理。

### React 主题模块

`src/lib/theme` 消费生成注册表并提供 `ThemeProvider` / `useTheme` 或等价小接口：

- 初始化以首屏脚本已写入的 DOM 为准，再用同一 resolver 校正；
- `setTheme(themeId)` 原子更新两个根属性并尝试持久化；
- 存储写失败时当前标签页仍生效，并以可测试结果告知调用方；
- 监听同源 `storage` 事件，未知值回到默认；主题变化不重挂业务组件；
- 只有该模块和生成文件能接触 theme id；业务组件只读 semantic token。

当前生产 UI 不调用 setter；Storybook 全局 toolbar 和测试使用它，后续 product WORK 可接入真实入口。

## 模块与数据

- `docs/design-system`：只读视觉合同与数值真源；本 WORK 不修改 token/contract。
- Web generator：把 manifest metadata 变为两个不可手改生成物，不复制颜色。
- `src/lib/theme`：低层主题状态；不得 import app/routes/features，符合既有依赖方向。
- `src/components/ui`：主题无关共享组件；只依赖 lib、Base UI、CVA、Lucide。
- `.storybook`：消费生成 registry 和主题模块建立全局 decorator，不维护主题列表。
- routes/features：只组合共享组件，保留 Router/Query/API 所有权。
- `apps/web/scripts/check-design-system.mjs`：扫描 Web 源码与配置中的 raw hex/OKLCH、
  `.dark`/literal theme id、`--ds-raw-*`、必要对比 `color-mix()` 和禁用态 opacity；对生成文件、
  合法 SVG/测试 fixture 使用显式小型 allowlist。

持久化只有一个非敏感字符串 `cherry-oj.theme`，无数据库或服务端数据变化，也不与登录账户同步。

## 接口与状态

基础组件范围冻结为：

- Button：`primary|secondary|ghost|danger`，`sm|md`，覆盖 default/hover/pressed/
  focus-visible/disabled/loading；loading 保持尺寸并不可重复激活。
- Field：Input、Textarea、Select 及 Label/description/error 组合；必要边界用
  `border-strong`，placeholder 不替代 label。
- Link style 与 IconButton：导航保持真实链接和常驻下划线；纯图标按钮必须有可访问名称。
- Badge、Card/Panel：只表达状态/层级，不用可点击 `div`。
- Dialog/Popover：继续用 Base UI 处理 Escape、焦点约束、返回 trigger 与 ARIA，视觉只读 token。
- Typography/Layout：提供语义 class/小型 primitive，不用按页面复制任意字号、容器和间距。
- InlineNotice、AsyncState：success/warning/danger/info/special 与 empty/loading/error/unauthorized；
  Router 独立承担 not-found，普通业务内容承担 success，避免把所有页面状态塞进一个万能组件。

这一定义明确了当前 AsyncState 漂移的处理，但不修改上游 component manifest；若未来要把 not-found 纳入
AsyncState，另行升级组件合同。Verdict 不在本工作范围，首次实现时从 `contracts/verdict.json` 生成
或做精确集合测试，先补上游漏掉的 `PE`。

为保证每个 TASK 独立编译，TASK-024 重建 Button 时可以暂留仅供现有消费者使用的
`outline → secondary` variant 和 `lg → md` size 兼容别名，并标注迁移退出条件；TASK-025 改完
现有调用后必须在同一工作内删除这些别名。不能把临时兼容项写进新 Story、文档示例或长期组件合同。

## 安全与失败

- localStorage 是不可信输入，只接受生成 registry 中的精确 id；不把值拼入 selector、HTML 或代码。
- 外部经典脚本避免 inline CSP 例外；内容由仓库脚本生成，不执行远端数据。若部署 CSP 要求 nonce/hash，
  重新审核装配方式。
- HTML 默认属性、CSS `:root` fallback 和 React resolver 三层都安全倒向黑色；存储异常不阻止渲染。
- manifest 解析/生成/漂移失败时检查失败；不得继续使用上一次生成物。
- raw 色扫描不能替代实际 a11y；Storybook、Testing Library、Playwright 与人工键盘/视觉复核共同验收。
- 删除旧资产前用引用扫描确认无消费者；只删除明确未使用的 Vite 模板文件，不发明未经设计的 Cherry Logo。

## 监控与部署

部署仍是 Vite 静态站，不增加服务。CI 的 Web job 已使用 Node 24，继续运行 `npm ci`、`check`、
`build`、`storybook:build` 和 Playwright；`check` 新增 docs 设计系统与 Web 漂移门禁。
发布后观察默认黑色首屏、已有浅色偏好的刷新、登录/账户/管理页面以及控制台/资源 404。没有生产环境时
不能代签 release/observe。

## 迁移与兼容

按可回退的三段迁移：

1. token/runtime：先让旧组件在新 semantic adapter 上编译，建立首屏、生成和主题 API；
2. shared components：用新 API 重建基础组件与 Stories，不改业务页面；
3. consumers/gates：逐页替换旧 class/局部组件，最后启用严格扫描和双主题 E2E。

每段结束都运行 check/build。默认视觉从浅色变黑是已批准设计的用户可见变化；业务逻辑和公开 API 不变。
已有未知偏好安全失效为黑色。批准实施后同步把 `docs/frontend.md` 的 Radix 描述改为 Base UI，保持
长期技术基线与现状一致；不改 `docs/design-system` 数值真源。

## 备选方案

1. **A（推荐）：直接构建期 import CSS + manifest 生成薄运行时。** 真源只有一份，生产无 docs URL
   依赖，生成物可检查；需要让 Vite/Storybook 只读访问仓库 docs。
2. **B：把 token/adapter 复制到 Web。** 初期简单，但形成第二份可手改数值，必须再维护复杂同步；不采用。
3. **C：把设计系统发布成独立 npm workspace/package。** 边界清晰，但仓库当前没有 JS workspace，
   为两份 CSS 引入发布版本和依赖管理过重；出现第二个前端消费者时再考虑。
4. **D：浏览器运行时 fetch manifest/docs CSS。** 会把文档部署、网络失败和首屏闪烁带进产品；不采用。

交互 primitive 另有“迁回 Radix”备选，但当前 Base UI 已安装、Button 已采用，设计合同只要求可访问的
无样式 primitive。继续 Base UI 能避免双栈与无价值迁移，推荐同步修正文档。

## 风险与重审条件

风险主要是跨目录 CSS 在 Vite/Storybook 中解析差异、首屏脚本与 React 竞态、严格扫描误报、共享组件
范围膨胀和默认主题带来的全页视觉回归。以构建级 import 测试、同源生成 registry、显式 allowlist、
三 TASK 边界和双主题真实浏览器矩阵控制。

出现以下情况必须重审：theme contract/theme id/default 变化；正式账户级主题同步需求；严格 CSP 不允许
当前脚本装配；出现第二个独立 Web 消费者需要 package；Base UI 无法满足已批准键盘合同；或需要实现
Verdict/Submission/editor 等 OJ 业务组件。不得借本 WORK 顺手修 TanStack Router 版本差异或其他依赖债。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：运行时接入、生成链、组件边界、迁移与失败策略已形成草案，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准 canonical CSS、manifest 生成、主题运行时与组件边界方案
- 2026-08-28：由 DESIGN-014 替代：Web 运行资产必须归属代码侧，设计文档不再参与构建
