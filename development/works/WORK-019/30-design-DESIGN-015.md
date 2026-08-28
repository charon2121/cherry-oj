---
id: "DESIGN-015"
type: "design"
title: "Cherry OJ 任务入口主页 Figma 方案"
status: "approved"
work: "WORK-019"
owners: ["codex/root"]
depends_on: ["FEATURE-002", "EXPERIENCE-008"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# DESIGN-015：Cherry OJ 任务入口主页 Figma 方案

## 背景

FEATURE-002 将根路径定义为任务入口，EXPERIENCE-008 冻结桌面、窄屏、登录状态和文案。视觉依据是
[`docs/design-system.md`](../../../docs/design-system.md) 及其 Foundation、主题和组件 manifest；产品
边界来自 [`docs/product.md`](../../../docs/product.md)、WORK-002 的 C++ ACM 首切片和 WORK-013 的
身份体验。当前 `apps/web/src/routes/index.tsx` 只是工程连通占位，本稿是未来目标态，不做截图翻版。

## 目标与限制

目标是在一个新 Figma Design 文件中交付可编辑、组件化、可校验双主题的主页设计，并同时给出
桌面、窄屏和关键身份状态。限制如下：

- 不修改仓库实现，不新增路由/API，不改设计系统真源。
- 不依赖或伪造当前不存在的 Cherry OJ Figma Library；只建立主页所需的变量与局部组件子集。
- 不使用图片、渐变、玻璃效果、重阴影、大面积品牌粉或营销插画。
- 不展示真实用户数据、隐藏测试点、标准答案、内部凭证或服务拓扑。
- Inter/中文系统回退是产品字体意图；若 Figma 不提供 510/590 精确字重，使用同家族最接近的
  Medium/Semi Bold 并在 Notes 标注，不得静默换成其他字体家族。

## 整体方案

### 文件与页面

若没有用户提供的可编辑目标文件，在 Figma plan `team::1382910566488039307`（“Xian Xian's team”，
Full 席位）的 Drafts 新建 `Cherry OJ · 任务入口主页`。建议页面：

- `01 · Homepage`：全部交付 Frame。
- `02 · Local Components`：主页所需的局部组件与状态说明。
- `03 · Notes`：信息架构、目标态说明、token 来源、可访问性和后续实现边界。

### Frame 矩阵

| Frame | 尺寸 | 用途 |
|---|---:|---|
| `Homepage / Desktop / User / Cherry Black` | 1440 × 1024 | 主稿，普通用户目标态 |
| `Homepage / Desktop / Guest / Cherry Black` | 1440 × 1024 | 匿名动作与导航差异 |
| `Homepage / Desktop / User / Pure White` | 1440 × 1024 | 同结构浅色合同校验 |
| `Homepage / Mobile / User / Cherry Black` | 320 × 844（内容可纵向增长） | 最小宽度阅读与操作 |
| `Homepage / States` | 适配内容 | Session loading/error、首次改密、ADMIN 注释变体 |

### 桌面结构

- 232px 左侧栏使用 `panel`，以细分隔线连接主画布；品牌、普通导航、按角色显示的管理分组从上到下
  排列。主区使用 64px location bar，内容遵循 1200px 最大容器和 24px gutter。
- Hero 采用约 3:2 双列：左侧为定位、标题、正文和动作；右侧为单个“完成一次答题”Panel。层级主要靠
  留白、文字明度和分隔线，不把每段包成 Card。
- 四步路径由编号、Lucide 图标和短文案组成；快速入口用带分隔线的列表行，行内有标题、说明和箭头。
- Gateway 状态位于内容末尾，以 Inline Notice/metadata 规格呈现。

### 变量与组件

受 Figma Starter plan 单集合仅允许一个 mode 的限制，并经用户明确批准，建立四个本地变量集合：

1. `Cherry OJ / Color Primitives`：所有代码真源色值，单 mode `Value`，仅供 semantic alias。
2. `Cherry OJ / Foundation`：字体意图、12/14/16/18/24/32/48px 字级、4px 间距节奏、6/8/12px
   圆角、2px focus 和 150/200ms 动效说明。
3. `Cherry OJ / Theme / Cherry Black`：单 mode `Cherry Black`。
4. `Cherry OJ / Theme / Pure White`：单 mode `Pure White`。两套 semantic collection 使用相同变量名并
   分别 alias 到 Color Primitives，至少覆盖 canvas、panel、surface、hover、fg、fg-2、muted、meta、
   border、border-strong、brand surface/foreground/soft、on-brand、focus、warning 和 danger。

局部组件增加 `Theme=Cherry Black/Pure White` 变体，使双主题 Frame 保持相同 anatomy；这是一项 Figma
Starter 表达层降级，不改变 `docs/design-system/design-tokens.json` 的主题合同，也不新增运行时主题切换器。

局部组件只覆盖本主页重复结构：`Button`、`Nav Item`、`Step Item`、`Quick Entry Row`、`Inline Status` 和
`Brand Mark`。项目 Button/Link/Panel/Inline Notice 合同优先；Lucide 图标从项目 SVG 路径导入为可编辑
向量，不用旋转线段拼图标。局部组件不冒充已发布的全局设计系统。

## 模块与数据

- `01 · Homepage` 只消费 `02 · Local Components` 的实例和本地 variables。
- `02 · Local Components` 只表达 FEATURE-002 所需组件子集；全局合同仍由 `docs/design-system*` 持有。
- `03 · Notes` 记录目标态与当前实现差异，不参与视觉真源。
- 设计没有后端数据输入。所有示例均为静态合成文案；身份状态通过独立 Frame/注释表达，不创建真实
  用户或提交。
- 后续 Web 实现必须重新以 `apps/web/design-system/` 为可执行真源，不能从 Figma 导出硬编码颜色覆盖
  代码侧 token。

## 接口与状态

| 状态 | Hero/动作 | 导航与内容 |
|---|---|---|
| Session loading | 公共 Hero 不变，账号区显示检查中 | 不闪出错误角色入口 |
| Guest | `浏览题库` + `登录` | 无我的提交/管理入口 |
| User | `浏览题库` + `我的提交` | 普通导航完整 |
| Password change required | warning + `修改密码` | 受保护入口禁用并说明原因 |
| Admin | 与 User 相同 | 追加次要管理分组 |
| Session error | 公共 Hero + 就地重试 | 不假定已退出 |
| Gateway error | 主流程仍可读 | 底部状态显示暂不可用与重试 |

Figma 主稿不调用接口。所有入口只注明未来路由意图；不生成 prototype 到当前不存在的页面，以免把视觉
稿误解为已完成产品链路。

## 安全与失败

- 不使用真实姓名、邮箱、提交 ID、源代码、凭证或隐藏测例；示例状态只使用通用合成文案。
- Warning/danger 与 Cherry brand 分开，任何状态都不只靠颜色。
- Figma 写入按小步、单区块进行，每次返回 created/mutated node ids 并截图；失败脚本按工具原子性先
  读错误再修复，不立即盲重试。
- 完成区块必须关闭 placeholder；发现字体、裁切、重叠或对比问题时只修复对应 node。

## 监控与部署

没有生产部署或运行监控。本任务的“交付”是创建 Figma Draft、完成截图/metadata/font 读回检查并返回
可访问链接；“观察”是用户在 Figma 中复核主稿和状态 Frame。仓库只运行 `scripts/work check` 验证文档
链路，不运行 Web 构建作为 Figma 设计通过依据。

## 迁移与兼容

当前 Web 顶部导航和连通状态页保持原样。Figma 采用侧栏的目标态不会自动迁移代码，也不会修改
WORK-018 的行为不变边界。若用户随后批准落地，需要单独产品/实施 TASK，重新核对当时路由、API、
设计系统代码真源、双主题 Storybook 和 E2E；不得直接把 Figma px/hex 复制进业务页面。

## 备选方案

- **A（采用）**：Focused Workspace 任务入口。紧凑 Hero、四步路径、快速入口和小型系统状态，信息与
  当前 MVP 边界一致。
- **B**：营销 Landing Page。视觉空间更大，但会诱导加入客户背书、功能卖点和注册转化，不符合当前
  单工作空间、管理员开通账号的 MVP。
- **C**：个性化 Dashboard。最近提交、进度和统计更“像产品”，但需要尚未定义的首页数据契约，并会
  暗示个人统计能力，当前不采用。
- **D**：照抄现有工程占位页。实现最贴近当前代码，但无法形成产品入口目标，也浪费已确认设计系统。

## 风险与重审条件

主要风险是把目标态误称为当前能力、从文档 token 手抄出第二套 Figma 真源、或为了丰富画面加入未确认
数据。Notes、非目标扫描和变量绑定用于控制这些风险。若题库/提交产品边界改变、正式 Cherry 品牌规范
到来、应用壳决定继续使用顶部导航、设计系统合同变化，或用户要求营销首页/数据看板，应先回到
FEATURE-002/EXPERIENCE-008 重审，不在画布中临时改方向。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：Figma 文件结构、变量、局部组件、Frame 矩阵与验证方案已完成，提交用户审核
- 2026-08-28：状态变更：review → approved。原因：用户明确回复文档通过并允许执行 TASK-027
- 2026-08-28：用户明确批准 Starter plan 降级：原同一 Theme collection 双 mode 改为两个独立单 mode
  semantic collection，并以组件 Theme 变体维持双主题一致结构。
