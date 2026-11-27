# Cherry OJ 设计系统规范（v3.0）

> **适用范围**：`apps/web` 全量界面（公开站点、题库与做题工作台、认证流程、管理中心）。
> **唯一真源**：所有 Foundation 与主题色值仅在 `apps/web/design-system/` 中手写一次；本文定义视觉架构、语义契约、构图规范与可访问性门禁，不重复抄写具体色值。

## 1. 设计定位与架构分层

Cherry OJ 采用**浅色优先、明快现代的卡片化工具界面（Modern Crisp Card & Island Workspace）**：

- **清晰的空间边界**：以冷瓷灰画布衬托纯白圆角卡片与 `1px` 实线边框，摒弃无边界纯黑极简主义带来的视觉粘连，让题面阅读、表单录入、表格扫视与代码调试各自拥有明确边界。
- **中文友好的阅读尺度**：针对中文笔画密度标定 `14px` 标准界面字号与 `15px` 长文阅读字号，正文字重回归 `400` 常规体，标题使用 `600` 半粗体拉开层级。
- **单一可执行真源**：`apps/web/design-system/` 持有 `tokens.foundation.css`、`themes/*.css`、`theme-contract.json`、Tailwind v4 适配器与门禁脚本；`docs/design-system/` 仅存放面向人与智能体的设计说明与组件契约（[`PROMPT.md`](./design-system/PROMPT.md)、[`components.manifest.json`](./design-system/components.manifest.json)），不参与构建输入。

UI 代码按四层单向依赖组织：

1. **Token 与主题层**（`apps/web/design-system/`）：Foundation 尺度 + 双主题语义映射。
2. **基础组件层**（`apps/web/src/components/ui/`）：基于 Base UI 与 shadcn 骨架封装的无业务语义原语。
3. **业务组件层**（`apps/web/src/features/*/components/`）：难度徽章、判题状态、自测工作台、代码编辑器适配等 OJ 专属组合件。
4. **页面模板与外壳层**（`page-templates.tsx`、`apps/web/src/app/shells/`）：统一接管顶栏、侧栏、首屏留白、水平安全区与异步状态挂载位。

---

## 2. 双主题同构契约（Light-First）

系统内置并正式登记两套完整主题，通过 `themes.manifest.json` 生成首屏零闪烁初始化脚本与运行时注册表：

- **`pure-white`（默认主题与安全回退主题，`light`）**：冷瓷灰主画布搭配纯白面板与卡片，高对比深板岩文字阶梯，适合长时间题面阅读与日常刷题。当本地存储缺失、为空、未知或不可读时，一律安全回退到 `pure-white`。
- **`cherry-black`（暗色主题，`dark`）**：柔和深板岩画布搭配抬升一档的暗色卡片面板，避免纯黑底色导致的眩光与层次丢失，满足夜间编码偏好。

### 跨主题硬性约束

1. **65 键完全同构**：每个已登记主题必须完整定义 `theme-contract.json` 中的全部 65 个语义 token，不得遗漏或私自扩充仅单主题生效的变量。
2. **零主题分支**：组件与业务代码中严禁出现 `.dark`、`dark:*`、硬编码主题 ID 判断、原始十六进制/RGB/HSL/OKLCH 色值、`--ds-raw-*` 或 `color-mix()`。主题切换只改变根节点 `data-theme` 与派生的 `data-color-scheme`，DOM 结构、圆角、字号与布局保持 100% 一致。
3. **组件外禁直连 `--ds-*`**：`src/features/`、`src/app/`、`src/routes/` 统一消费 Tailwind 语义工具类（如 `bg-background`、`bg-panel`、`text-foreground`、`border-border`），仅 `src/components/ui/` 与样式适配层可引用底层 CSS 变量。

---

## 3. 排版与空间尺度

### 3.1 字体栈

- **界面与标题（`--ds-font-sans` / `--ds-font-display`）**：本地打包 `Inter Variable`，中文自动回退系统无衬线字体（PingFang SC / Microsoft YaHei UI / Noto Sans CJK SC）。
- **代码与判题指标（`--ds-font-mono`）**：本地打包 `JetBrains Mono Variable`（优先兼容已安装的 Berkeley Mono），所有编号、耗时、内存、提交短哈希与时间戳开启 `tabular-nums` 等宽数字对齐。

### 3.2 字号与字重阶梯

| 角色 | Token / 工具类 | 尺寸与字重 | 典型用途 |
| --- | --- | --- | --- |
| 辅助元信息 / 徽章 | `text-xs` / `text-cap` | `12px–13px` / `400–500` | 标签徽章、列表尾部元信息、快捷键、分组顶栏 |
| 标准界面正文 | `text-sm` | `14px` / `400` | 按钮、输入框、下拉项、导航项、列表主标题、表单说明 |
| 题面阅读正文 | `text-base` / `markdownProseClasses` | `15px` / `400`（行高 `24px`） | 题目描述、输入输出说明、提示与 Markdown 长文 |
| 卡片与分节标题 | `text-15` / `text-base font-heading` | `15px–16px` / `600` | 题面分节标题、工作台子块标题、导览卡片标题 |
| 页面主标题 | `Heading size="xl"` | `20px` / `600` | 列表页 Toolbar 标题、登录与改密卡片标题 |
| 题面与导览大标题 | `text-2xl` | `24px` / `600` | 做题页题面主标题、后台 KPI 指标核心数值、首页主标题 |

### 3.3 布局骨架常量

- **全局顶栏高度（`--ds-header-height`）**：`56px`，固定 `1px` 底边框与纯实色面板背景。
- **管理侧栏宽度（`--ds-sidebar-width`）**：`240px`，桌面端常驻左侧，窄屏收纳进抽屉（`Sheet`）。
- **标准线条宽度（`--ds-hairline`）**：统一为 `1px`，保证在 1x 普通显示器与 2x Retina 屏幕上呈现一致的锐利边缘。
- **响应式水平安全区**：手机 `16px`、平板 `24px`、桌面 `32px`，由页面模板与 `Container` 统一持有，业务内容不再重复叠加外层水平边距。

---

## 4. 表面、卡片与交互原语

1. **画布与卡片分层**：页面根容器使用 `--ds-canvas`（`bg-background`）；承载内容集合的卡片、列表外框、侧栏与顶栏使用 `--ds-panel`（`bg-panel`）配 `border-border` 与 `shadow-subtle`；卡片内部的分组带、代码块底色与分段槽使用 `--ds-surface-subtle`（`bg-surface-subtle`）。
2. **圆角体系**：徽章与小标签 `6px`（`rounded-md`）；按钮、输入框、下拉触发器、属性按钮与代码块 `8px`（`rounded-lg`）；卡片、数据列表外框、浮层菜单与岛式工作台面板 `12px`（`rounded-xl`）；模态对话框 `16px`（`rounded-2xl`）。
3. **控件尺寸**：表单标准输入框、下拉框与主操作按钮高度为 `36px`（`min-h-9`）；工具栏紧凑筛选、次级动作与行内小按钮高度为 `32px`（`h-8`）。
4. **分段控制器 `Tabs`**：标签切换统一采用浅底圆角托盘（`bg-surface-subtle p-1 rounded-lg`）配抬升激活滑块（`data-active:bg-panel data-active:shadow-ring rounded-md`），让当前激活视图一目了然。
5. **克制动效**：常规悬停与状态切换使用 `150ms` 颜色/阴影过渡；弹出菜单、对话框与侧边抽屉允许 `150ms–200ms` 的透明度配合微幅缩放/位移过渡（如 `scale-98`），并在 `prefers-reduced-motion: reduce` 下全部归零。

---

## 5. 品牌色、语义状态与判题表达

1. **樱桃红品牌色（`primary` / `brand`）**：专用于站点品牌标识、页面核心主操作按钮（登录、提交代码、新建题目、保存配置）与管理侧栏当前激活导航态。破坏性操作（删除、停用、放弃修改）必须使用独立的 `danger` / `destructive` 语义色，严禁与品牌色混用。
2. **题目难度双重编码（`DifficultyBadge` / `DifficultyIcon`）**：难度同时使用递增条形图（简单 1 格、中等 2 格、困难 3 格、未评级 0 格）与柔和语义色徽章（绿 `success`、琥珀 `warning`、红 `danger`、中性 `neutral`），兼顾快速扫视与无障碍辨识。
3. **提交生命周期与 Verdict 分离**：
   - 未完成态（`PENDING` / `JUDGING`）展示有序阶段文本与进度计数，不伪造最终判定码。
   - 已完成态必须穷尽 `contracts/verdict.json` 中的全部合法值（含 `AC`、`WA`、`TLE`、`MLE`、`OLE`、`CE`、`RE`、`RAN`、`SE`），同时输出**稳定英文缩写 + 中文名称 + 形状/状态点**，绝不单独依赖红绿文字传达结果。

---

## 6. 核心页面构图规范

1. **双端应用外壳与首页导览**：
   - 用户端采用 `56px` 顶栏 + 主内容画布；首页以导览卡片网格呈现核心工作流入口。
   - 管理端采用左侧固定侧栏 + 顶部面包屑与操作槽 + 冷灰画布工作区；后台总览顶部展示核心 KPI 指标卡，下方按任务优先级排列带框分组列表。
2. **聚焦认证卡片**：登录页与修改密码页采用窄容器居中卡片（最大宽度约 `440px–460px`），标题、说明、表单字段、全宽主按钮与内联错误提示收纳于同一张卡片内，保证在 `1280×720` 标准视口与 `320px` 窄屏下均完整可达且无横向滚动。
3. **卡片化列表与管理表（`ListPageTemplate` + `Toolbar` + `DataList`）**：
   - 工具条第一行放置页面标题、准确计数胶囊、搜索框与主操作按钮，第二行放置即时生效的筛选器。
   - 列表整体包裹在圆角带框卡片内，行间以 `1px` 柔和分隔线切分；分组列表每组独立成卡并带浅底分组标题栏。整行点击通过标题内真实 `<Link>` 或 `<button>` 的 `::after` 伪元素扩展热区，不使用 `<div onClick>`。
4. **工作台构图**：
   - **做题页（`WorkbenchPageTemplate variant="coding"`）**：桌面宽屏采用**三岛可拖拽分栏**——左侧题面与提交记录岛、右上代码编辑岛、右下测试用例与运行/提交结果岛，三张圆角带框面板悬浮在画布之上，通过 `SplitPane variant="island"` 沟槽手柄自由调节宽高比例；窄屏自动切换为顶栏 `Tabs` 单栏视图并保持编辑器挂载状态。
   - **后台出题工作台（`AdminProblemWorkbench`）**：左侧文档卡片专注题面、样例与多语言模板撰写，右侧粘性属性卡片集中承载难度、可见性、标签与测试数据/标定/公开检查入口。

---

## 7. 可访问性与工程门禁

1. **对比度硬门禁**：`apps/web/design-system/tools/check.mjs` 自动校验两套主题共 **296 组**语义配对——正文文本对比度 `>= 4.5:1`，大号标题、控件边界、焦点环与必要图标对比度 `>= 3.0:1`，禁止使用 `opacity` 降低禁用态或占位符对比度。
2. **不抖动焦点环**：键盘 `:focus-visible` 统一使用 `1px` 贴边或内缩 `outline`（`--ds-focus`），不改变盒模型尺寸、不推挤相邻元素，并在操作系统 `forced-colors: active` 高对比模式下自动回退系统高亮色。
3. **窄屏与缩放韧性**：所有页面在 `320px` 视口宽度与 `200%` 缩放等价宽度（`640px`）下不得产生横向滚动条，主操作与导航入口必须保持可触达。
4. **日常验证命令**：修改任何设计 token、基础组件或页面样式后，必须通过 `cd apps/web && npm run check:design-system`、`npm run check` 与 Playwright 关键链路回归。
