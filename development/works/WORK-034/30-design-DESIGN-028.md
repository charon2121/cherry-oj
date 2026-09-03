---
id: "DESIGN-028"
type: "design"
title: "下载版 Cherry OJ Design System 的双主题生产化迁移"
status: "checked"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-002", "DECISION-019"]
related: ["WORK-015", "WORK-019", "WORK-020", "WORK-031", "WORK-033"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# DESIGN-028：下载版 Cherry OJ Design System 的双主题生产化迁移

## 背景

依据 `IMPROVEMENT-002` 与 `DECISION-019`。目标来源位于
`~/Downloads/Cherry OJ Design System`：99 个文件、约 512 KiB。根说明将其定义为 dark-mode-native、
Linear-inspired 的 Cherry OJ 系统，附带 Foundation、14 个核心组件、20 个 guideline specimen、Judge app
和 marketing UI kit。用户明确选择这套视觉，故本方案不再从现有组件自行推演风格。

来源包是设计证据而不是可直接运行的生产依赖。生产代码必须把同一视觉语义翻译进 Cherry OJ 已有的
TypeScript、Tailwind、Base UI、Storybook、测试和无障碍结构。

## 目标与限制

- 暗色视觉和跨主题组件语言以下载版为准；保留现有暗色/浅色主题能力、切换入口和用户偏好。
- 只保留一套新设计系统：两个主题共享组件结构与非颜色 token，不保留旧视觉兼容开关。
- 所有当前可达页面迁移，不能只重做首页或题目工作台后宣称替换完成。
- 业务行为、路由、API、权限、数据序列化和 WORK-033 的编辑可靠性保持不变。
- 下载版没有定义的生产状态，以其颜色、排版、边界、状态点和密度语法扩展，不建立第二套 OJ 皮肤。
- 可访问性、安全、本地依赖和 SSR/hydration 是不可降低的底线；每个适配差异必须可追溯。
- 旧设计系统文件可由 Git 恢复，实施中不做不可恢复删除，也不清理用户业务数据。

## 整体方案

### 1. 来源冻结与权威链

实施开始先对下载目录做只读盘点和 SHA-256 清单，在 `docs/design-system/source/claude-design-v1/` 保存完整
原始快照，文件内容和相对路径不改。补充仓库 NOTICE，说明它由 Claude Design 根据 Linear-inspired
OpenDesign fixture 和用户 Cherry 色要求生成；保留 Apache-2.0 与 Lucide 许可。原始快照不被 Web import，
也不接受页面代码反向修改。

权威顺序为：

1. 人工确认的 `DECISION-019` 与 `IMPROVEMENT-002`；
2. 来源快照的根说明、tokens、组件 prompt、Judge/marketing UI kit；
3. `docs/design-system.md` 的中文生产消费合同与显式适配差异；
4. `apps/web/design-system/` 的运行时合同、生成器和 checker；
5. 共享组件、Storybook、页面与截图。

### 2. 文档侧设计系统

重建现有文档包中的 `cherry-black`/`pure-white` 主题合同、preview 与生成产物：

- `docs/design-system.md`：中文长期合同，覆盖内容语气、色彩、字体、spacing/layout、surface、边界、
  elevation、交互、motion、radius、icon、核心/扩展组件和页面组合；
- `docs/design-system/source/claude-design-v1/`：下载包 99 文件的原始快照；
- `docs/design-system/source-lock.json`：相对路径、大小、SHA-256、总文件数和导入日期；
- `docs/design-system/README.md` / NOTICE / LICENSE：维护入口、来源、生产适配与许可；
- 新 preview/组件清单只从新 tokens/配方生成，视觉评审入口仍以真实 Storybook 和来源 UI kit 为主。

文档侧可保留 HTML 原型中的 CDN/inline SVG，前提是明确标为来源快照且永不进入生产构建。

### 3. Web 运行时基础

`apps/web/design-system/` 按下载版 8 组 Foundation 重建，但使用 `--ds-*` 命名空间：

| 下载版语义 | 生产映射 |
|---|---|
| `--bg`, `--bg-deep`, `--surface-panel`, `--surface`, `--surface-2` | canvas/deep/panel/surface/hover |
| `--fg`, `--fg-2`, `--muted`, `--meta` | primary/body/muted/meta 文本 |
| `--cherry-500/400/300/600` | primary/link/hover/active |
| translucent alpha/border ladder | card、selected、hover、divider、control border |
| 300/400/510/590 + `cv01`/`ss03` | typography primitives |
| micro spacing、2–22px radius、elevation ladder | layout/component aliases |
| 150/200/320ms + standard easing | motion tokens；reduced-motion 归零 |
| 1200px container、220px sidebar、56px header | Shell/layout tokens |

Foundation 分为两层：spacing、typography、radius、layout、motion 等主题无关 token 从单一 `:root` 输出；
surface、text、border、accent、status 与 elevation 由 `cherry-black`/`pure-white` 主题文件提供。暗色关键值
精确来自下载版；浅色以相同语义和现有 `pure-white` 行为为兼容输入，逐级重建 canvas/panel/surface/hover、
文本层级、hairline 和阴影，不做机械反色。继续生成 theme manifest 与 `[data-theme]` 分支。checker 校验
两主题键集合完全一致、暗色关键值、双主题对比度、来源 hash、禁止旧值、文件边界和生成物。Tailwind adapter
只映射语义 token，不让业务页面直接判断主题或读取 primitive。

字体不使用下载版 Google Fonts import。Inter Variable 继续由当前本地依赖提供；mono 评估增加本地
JetBrains Mono 包，未安装时使用系统等宽 fallback，绝不假装已经拥有商业 Berkeley Mono。生产图标全部
来自 `lucide-react`，统一 1.75 stroke/13–18px；来源 UI kit 的 path 只保留在文档快照。

### 4. 双主题运行时保留与收敛

保留 `ThemeProvider`、theme runtime/registry、首屏 `data-theme` bootstrap、ThemeSwitcher、现有 storage key
及其 Storybook/测试，内部只注册 `cherry-black` 和 `pure-white` 两个新合同。默认仍为暗色；合法偏好在刷新、
登录跳转和 Site/Admin Shell 间持续生效，未知/损坏值安全回退到暗色。首屏脚本只负责在 React 启动前设置
已验证主题和 `color-scheme`，不得访问 session cookie 或其它业务存储。

E2E 验证“双主题语义同构、切换/持久化、首屏无闪烁、系统 color-scheme 正确、未知偏好安全回退、
reduced-motion/forced-colors 正确”。

### 5. 组件生产化

下载版 14 个核心配方映射到现有组件层：Button、IconButton、Pill、Input、SearchInput、Textarea、Container、
Stack、NavBar、Badge、Card、Eyebrow、Heading、Text。现有组件可保留文件名和外部 API 以降低页面迁移风险，
但 variant、尺寸、radius、padding、字体和状态必须改为来源语义。

Select、Dialog、DropdownMenu、Popover、Sheet、Sidebar、Collapsible、Alert/InlineNotice、AsyncState、Field、
Link 和 TextEditor 是 Cherry OJ 生产扩展。它们使用相同 surface/border/elevation/type/motion：

- 浮层只用 flat overlay 与 `elev-dialog`/`elev-raised`，不 blur；
- Field 控件为 0.02 white fill、hairline、6px radius，输入 focus 改 accent border；
- prose link 为可访问性保留下划线，nav/toolbar link 按来源无下划线；
- disabled 必要文字使用可读 token，不机械套 0.4 opacity；
- 状态使用文字/code + dot/shape，不只靠颜色；
- CodeMirror 外壳采用同一 recessed/surface/border 语法，不改编辑器数据和安全行为；
- Dialog/Select/Sheet 继续使用 Base UI 的 focus、escape、aria 和 portal 能力，不复制原型 DOM。

所有核心/扩展组件有 Storybook default/hover/pressed/focus/disabled/loading/error 状态和 320px 示例。禁止业务
页面写下载版 primitive raw value；需要新语义先补 Web 合同。

### 6. Shell 与页面模板

下载版 Judge app UI kit 直接约束应用层：220px panel sidebar、56px hairline header、5px×8px 密集侧栏行、
4px row radius、13/14px UI 字、toolbar button、半透明 row hover、细分隔和内部滚动。用户端与管理端共享
这套 chrome，移动端仍用可访问 Sheet；管理端顺序为 Dashboard、账号管理、题目管理。

marketing UI kit 只提供首页/认证页的视觉语言：克制 canvas、有限 heading、单一 primary、ghost secondary、
透明 Card、section hairline；暗色直接对齐来源，浅色使用同一组件与主题 token。当前产品没有注册、contest、
docs marketing 等能力，不新增示例业务。

页面迁移清单：

- 首页、登录、修改密码、403/404/错误；
- 题库列表、题目详情；
- 管理 Dashboard、用户账号；
- 管理题目列表/创建 Dialog、六步题目工作台及所有测试/发布/危险状态。

页面继续遵守 WORK-031 的“直接进入任务，不在操作前堆 title/desc”，但其 24px shell 间距要按下载版
56px header、12/16/24px gutter 和 app UI kit 重新表达。所有页面通过共享模板消费，不保留私有旧布局。

### 7. 题目工作台专项

保留 WORK-033 六步、URL、dirty/save、离开保护和 CodeMirror。对象栏变为 56px 密集 toolbar，步骤导航采用
下载版 sidebar/list row 语法；内容区以 hairline 和 darkness 组织，不堆实体 Card。

零样例上跳不使用 transform/height animation 修补。步骤切换后，将中央滚动容器稳定定位到内容锚点；空状态
使用可用视口 min-block-size 和结构化“添加第一个样例”动作，但不自动写入样例。状态切换只允许 opacity/
background/color 150/200ms，reduced-motion 即时完成。

### 8. 视觉与行为验证

来源本地 HTML 因浏览器安全策略不能以 `file://` 自动打开；实施时使用仓库内只读快照/安全本地预览入口或
用户直接查看来源预览，不能绕过浏览器安全限制。每个迁移批次用同一 viewport 捕获来源 UI kit、Storybook
和产品页面，组合对照检查字体、密度、边界、圆角、surface、状态和动效。

截图不替代行为测试。组件测试验证语义/API；E2E 验证导航、登录、表单、保存、Dialog、响应式、键盘、
reduced-motion/forced-colors；源码检查验证 raw value、旧 token/theme 和禁用动效；人工验收确认审美。

## 模块与数据

- 文档源：`docs/design-system.md`、`docs/design-system/` 与新来源快照/lock。
- 运行时：`apps/web/design-system/`、全局 CSS、设计生成/检查脚本、package/font 依赖。
- 组件：`apps/web/src/components/ui/`、Storybook、组件测试。
- 应用：Shell、页面、routes 的展示层、题目/认证/管理业务组件与 E2E。
- 治理：CLAUDE、AGENTS、TypeScript/TOOLCHAIN、WORK-034。

没有数据模型或 API 变更。应用只替换渲染结构/class/组件 variant；API hooks、query keys、Zod schema、认证
cookie 和业务 mutation 不变。

## 接口与状态

允许共享组件 API 做一次受控迁移，调用点在相应 TASK 同步更新，且每个提交可编译。禁止同时暴露 legacy 与
new variant 让页面长期混用。Theme API 保持对外行为兼容，内部 registry 只接受两个完整的新主题合同；
用 `rg`、类型检查和运行时测试确认不存在绕过语义 token 的主题分支。

状态语义保留 Cherry OJ 的 success/warning/danger/info/special/verdict/lifecycle，但颜色压缩到下载版允许的
neutral/cherry/green/amber/danger 语言，并以文字/code/dot/shape区分。服务端状态值不变。

## 安全与失败

- 来源包按不可信外部输入处理：不执行其中脚本、不加载 CDN、不提交表单、不复制内联 SVG 到生产。
- Markdown 继续净化；CodeMirror 不执行用户内容；认证/参考程序/上传/危险操作边界不变。
- 替换脚本只能作用于 TASK 明确路径，删除前列出精确文件；设计双树通过 Git 可恢复。
- 若许可、字体、对比度或来源完整性不满足，阻断相应批次，不用相近私有颜色或占位资产偷偷继续。

## 监控与部署

项目无生产环境。每批记录构建、CSS/JS/font 体积和 Web E2E，最终记录全路由双主题截图矩阵。设计系统切换
不做远程部署；本地验收后由用户决定提交/推送。若未来上线，以单版本原子部署一套设计系统；运行时只切换
这套系统中的暗色与浅色主题，不提供旧/新系统选择。

## 迁移与兼容

实施前先完成 WORK-033 验收并建立 Git 基线。迁移提交顺序：来源/合同 → 运行时 Foundation → 共享组件 →
Shell/主题移除 → 基础页面 → 题目/管理页面 → 全站收口。中间提交均可编译，但用户可见最终版本不得混搭。

URL、API 和存量数据完全兼容。现有合法 `themeId` localStorage 值继续生效，未知值回退暗色；主题偏好不做
迁移删除。字体失败时使用声明 fallback，不阻塞页面。

## 备选方案

- 只替换 token：无法复现用户认可的组件和页面组合，拒绝。
- 逐字复制原型：视觉近但生产风险不可接受，拒绝。
- 长期旧/新双系统或 feature flag：维护和视觉一致性成本过高，拒绝；这与同一系统下保留双主题不同。
- 只做下载版暗色并删除浅色：与用户明确要求冲突，拒绝。
- 先只改题目工作台：能快速展示，但其它页面仍混用旧系统，不能满足全站重建；仅可作为迁移批次，不是终态。

## 风险与重审条件

最大风险是暗色忠实度、浅色一致性、生产质量和迁移范围的平衡。若浅色无法在不分叉组件的情况下保持清晰
层级、来源 hash/许可不完整、本地字体导致不可接受体积、某个关键扩展组件无法用来源语法表达，必须暂停并
修订 DECISION。若用户在中间截图认为方向不符，应在双主题组件层调整后再迁移页面，不能等全部页面完成才
发现基础风格错误。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：已完成来源冻结、生产适配、单主题、组件、Shell 与全路由迁移设计，等待用户确认。
- 2026-09-03：状态变更：review → draft。原因：主题架构从单一暗色调整为同一设计语言下的暗色与浅色双主题。
- 2026-09-03：状态变更：draft → review。原因：已完成下载版暗色权威、浅色同构推导、主题运行时保留和双主题验证设计。
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
