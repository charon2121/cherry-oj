# Web UI 开发速查与交付自检（PROMPT）

> 修改 `apps/web` 的任何页面、组件、样式或主题前先读本文件。完整契约与设计原理由 [`../design-system.md`](../design-system.md) 定义；可执行 token 唯一真源在 `apps/web/design-system/`。

## 1. 动手前先定骨架

不要从空白 `<div>` 开始拼页面，先按页面职责套用既有模板与外壳：

| 页面类型 | 标准骨架 | 构图要点 |
| --- | --- | --- |
| 列表与管理页（题库、题目管理、语言、账号、后台总览） | `ListPageTemplate`（通常 `width="column"`）+ `Toolbar` + `DataList`（或带框分组表） | 页面水平边距由模板统一负责；列表包裹在 `rounded-xl border bg-panel shadow-subtle` 卡片中，分组时每组独立成卡并配浅底分组头 |
| 居中表单与单列详情页（登录、改密、语言前缀编辑） | `Container` + `Section` + `Card padding="lg"` 或 `DetailPageTemplate width="narrow"` | 标题、辅助说明、表单控件、主按钮与错误提示收进同一张居中卡片，不在卡片外散落孤立操作 |
| 编码工作台（用户端做题页） | `WorkbenchPageTemplate variant="coding"` + `SplitPane variant="island"` + `Pane` | 宽屏为左题面岛 + 右上代码岛 + 右下自测/提交岛三卡分栏，窄屏降级为 `Tabs` 切换且保留草稿状态 |
| 文档与属性双栏编辑（后台出题工作台） | 宽容器双栏网格：左侧 `bg-panel` 文档卡片 + 右侧 `sticky` 属性卡片 | 正文撰写一页到底，测试数据、自动出题、标定与公开检查从右侧属性卡片弹窗打开 |

---

## 2. 组件选型表（优先复用 `src/components/ui/`）

- **动作按钮**：`Button`（主操作 `primary`、次级带框 `secondary`、工具栏紧凑 `toolbar`、轻量无框 `ghost`、破坏性确认 `danger`）与 `IconButton`（必须提供中文 `label`）。
- **表单录入**：`FormField` + `Input` / `Textarea`，下拉统一用 `SelectField`（表单内默认 `stacked`，工具栏筛选传 `labelPlacement="hidden"`）。
- **视图切换与筛选**：面板内视图切换用分段控制器 `Tabs`（`TabsList` + `TabsTrigger`）；工具栏互斥快速筛选用 `ToolbarFilterGroup` / `Pill`。
- **状态与标签**：题目难度用 `DifficultyBadge`（条形数量 + 语义色双重编码）；通用分类、角色与状态用 `Badge`；页内横幅提示用 `InlineNotice`；加载/空数据/失败统一用 `AsyncState`。
- **长文本与代码**：用户端代码编辑用异步加载的 `CodeEditor`（触控端自动回退 `TextEditor`）；后台起始代码、标程与样例块用 `CodeBlockEditor` / `TextEditor`；题面 Markdown 编辑用 `RichMarkdownEditor`，只读渲染用 `SafeMarkdown`。

---

## 3. 编码铁律（源码门禁自动拦截）

1. **零裸色值、零主题分支**：严禁手写 `#hex`、`rgb()`、`hsl()`、`oklch()`、`color-mix()`、`.dark`、`dark:*` 或按主题 ID 判断分支。所有表面与前景必须使用语义工具类（`bg-background`、`bg-panel`、`bg-surface`、`bg-surface-subtle`、`text-foreground`、`text-fg-2`、`text-fg-muted`、`text-fg-meta`、`border-border`、`border-border-solid`）。
2. **业务层不直连底层 CSS 变量**：`src/features/`、`src/app/`、`src/routes/` 只写语义 Tailwind 类；新增自定义工具类同步在 `apps/web/design-system/tailwind-v4.css` 与 `apps/web/src/lib/utils.ts`（`extendTailwindMerge`）登记。
3. **禁止用透明度削弱可读性**：禁用态使用 `--ds-fg-disabled` / `bg-surface-translucent`，占位符使用 `text-muted-foreground`，不得写 `disabled:opacity-50` 或 `placeholder:opacity-60`。
4. **真实交互语义**：导航跳转用 `<Link>` / `<a>`，本页动作用 `<button>`；整行可点击只在标题链接或按钮上用 `dataListRowLinkClasses`（`::after` 覆盖整行），严禁 `<div onClick>`。
5. **状态绝不只靠颜色**：难度、判题 Verdict、节点在线状态与表单校验错误必须同时提供文字、代码或图标形状。

---

## 4. 交付前自检八问

每次完成 UI 改动前逐项核对：

1. **双主题**：在默认 `pure-white` 与暗色 `cherry-black` 下，画布、卡片、边框和文字层级是否都清晰自然？
2. **模板与对齐**：是否复用了标准页面模板？工具条、卡片列表与分页的左右边缘是否落在同一条垂直线上？
3. **排版尺度**：常规 UI 控件是否为 `14px/400`、长文题面是否为 `15px`、数字列是否加了 `tabular-nums`？
4. **五种状态**：`pending`、`empty`、`error`、`unauthorized/readonly` 与 `success` 是否都有明确界面表达？
5. **键盘与焦点**：纯键盘 `Tab` / `Enter` / `Escape` 能否走完流程？`:focus-visible` 是否为不推挤布局的 `1px` 轮廓？
6. **无障碍名称**：所有仅图标按钮、搜索框、无可见标题的区块是否都提供了准确的中文可访问名称？
7. **窄屏韧性**：在 `320px` 与 `640px` 宽度下是否零横向溢出，主操作按钮是否仍在视口内可达？
8. **自动化门禁**：是否已跑通以下命令且零报错？

```bash
cd apps/web && npm run check && npm run build
```
