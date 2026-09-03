---
id: "IMPROVEMENT-002"
type: "improvement"
title: "基于下载版重建 Web 设计系统并保留浅色主题"
status: "approved"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["WORK-033"]
related: ["WORK-015", "WORK-019", "WORK-020", "WORK-031", "WORK-033"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# IMPROVEMENT-002：基于下载版重建 Web 设计系统并保留浅色主题

## 当前问题

用户已在 `~/Downloads/Cherry OJ Design System` 提供一套由 Claude Design 生成并明确认可的完整设计
系统，希望它成为 Cherry OJ 的新视觉基线。当前仓库虽然也源自 Linear-inspired 设计材料，但在组件和页面
组合中只保留了部分颜色与间距特征，整体观感没有达到用户认可的下载版效果。

这次工作不再是在现有系统上局部增加“Linear 感”，而是系统级重建：设计说明、设计 token、组件配方、
Storybook、Shell、全部现有用户端与管理端页面都改为下载版语言。下载版负责暗色视觉和跨主题共用的构图、
排版、密度、圆角、边界与动效；产品现有浅色主题、主题切换与用户偏好必须保留，并在同一套新语义下重建。
业务流程、权限、API、数据和 WORK-033 刚完成的题目创建/编辑能力保持不变。

## 当前数据

- 下载目录共 99 个文件、约 512 KiB，包含根说明、8 组 token、14 个组件配方、20 个 guideline specimen、
  Judge app 与 marketing 两套 UI kit、manifest、adherence 配置及生成预览。
- 下载版是 dark-mode-native：主画布 `#08090a`，panel `#0f1011`，surface `#191a1b`，唯一品牌色族为
  `#d2042d` / `#ff4d67` / `#ff7088`；它没有提供完整浅色主题，因此不能直接覆盖产品的浅色能力。
- 下载版明确规定 Inter Variable 的 `cv01`/`ss03`、400/510/590 字重、220px sidebar、56px header、
  半透明 surface、细白边界、2/4/6/8/12/22px 圆角以及只动画 opacity/color/background。
- 下载版组件代码是视觉参考，不是可直接投入生产的实现：它使用 inline style、鼠标 hover state、
  `Math.random()` id、可点击 `div`、外部 CDN 和原型式全局变量，不能覆盖现有 TypeScript、Base UI、
  可访问性和安全实现。
- 当前仓库有 27 个共享 UI 源文件组、16 个路由、两个 Shell、双主题运行时与多组 Storybook/E2E；
  设计系统替换会影响整个 Web，而不是只影响题目工作台。
- WORK-033 的实现仍在未提交工作树中且验收闸尚未签署；WORK-034 必须建立在已确认、可回退的 WORK-033
  基线上，不能在未冻结的业务改动上直接做全站换肤。

## 目标指标

- REQ-001：把下载目录完整冻结为 WORK-034 的外部设计来源，记录来源目录、文件清单、逐文件 SHA-256、
  生成时间与许可说明；仓库内保留可复核的只读来源快照，不依赖 Downloads 长期存在。
- REQ-002：以下载版根 `readme.md`、token 文件、组件 prompt 和两套 UI kit 为视觉/语义权威；HTML card、
  bundle、manifest 和 lint 配置作为证据/生成物保存，但不直接成为生产运行时依赖。
- REQ-003：重写 `docs/design-system.md` 与 `docs/design-system/`，让中文消费合同、来源快照、组件说明、
  preview、许可和维护命令只描述重建后的统一系统；`cherry-black`/`pure-white` 继续作为正式主题 ID，不能把
  旧 token 或旧组件配方误当成新系统事实。
- REQ-004：重建 `apps/web/design-system/` 的 Foundation、颜色、字体、间距、圆角、elevation、motion、
  layout、Tailwind adapter、manifest、生成器和 checker；生产代码仍只读取 Web 自己的代码包。
- REQ-005：保留 `cherry-black` 与 `pure-white`、主题选择器、偏好持久化和首屏主题脚本。暗色主题精确迁移
  下载版颜色；浅色主题沿用现有主题 ID 和用户行为，并用相同语义层级重新校准颜色、边界、阴影与状态，
  不得简单反色，也不得退回另一套组件设计。
- REQ-006：保留 `--ds-*` 作为 Cherry OJ 生产命名空间和 Tailwind 适配边界；跨主题语义、暗色值和组件配方
  以下载版为准，浅色只替换主题相关颜色/elevation，不分叉 spacing、typography、radius、layout、motion 或
  组件结构；不得为减少 diff 而保留旧视觉值或旧 variant 含义。
- REQ-007：生产实现保留 React 19、TypeScript、Base UI、语义 HTML、稳定 id、键盘/焦点、错误关联和
  reduced-motion；不直接复制下载版原型代码的 inline style、鼠标专用交互、全局变量、外部 CDN 或手绘 SVG。
- REQ-008：迁移全部共享组件及其 Storybook/tests，包括下载版 14 个核心配方和项目额外需要的 Select、
  Dialog、Popover、Sheet、Sidebar、AsyncState、InlineNotice、TextEditor 等扩展组件；扩展组件必须使用
  同一视觉语法，不能形成第二套样式。
- REQ-009：迁移站点/管理 Shell、导航、登录、密码修改、错误/空状态、题库列表/详情、管理 Dashboard、
  用户账号、题目列表与题目工作台；当前仓库中每个可达路由都必须纳入视觉验收。
- REQ-010：下载版 Judge app UI kit 是应用壳、密集列表、工作区、命令面板的构图参考；marketing UI kit
  只用于现有首页/认证页的视觉语言，不新增未定义的营销业务、注册能力、竞赛或命令面板功能。
- REQ-011：保留 Cherry OJ 已确认的 OJ 状态、危险操作、长文本 CodeMirror、Markdown 净化、320px、
  中文字体回退和 WCAG 约束。下载版与这些约束冲突时，视觉意图优先保留，生产安全/可访问性通过显式
  适配实现并记录差异，不能静默复制缺陷。
- REQ-012：两个主题的动效都严格遵守下载版：150/200ms 标准 easing，只动画 opacity、color、background；不新增
  transform entrance、scale、spring、parallax、blur 或高度动画，reduced-motion 下非必要动效归零。
- REQ-013：保留 WORK-034 原始反馈：题目工作台零样例切换不得因内容收缩出现意外滚动上跳；管理侧栏顺序
  调整为 Dashboard、账号管理、题目管理。修复必须符合下载版固定 220px sidebar/56px header 和无布局动画规则。
- REQ-014：更新 `CLAUDE.md` 与 `AGENTS.md` 的路由/执行提示，使未来 Web 设计任务先读取下载版来源快照、
  中文合同和相关 Storybook/UI kit，并明确“token 合规、组件合规、页面视觉合规”需要分别验证。
- REQ-015：迁移过程中不修改后端、OpenAPI、生成 API 的业务字段、数据库、路由语义、认证权限、题目保存/
  发布流程或用户数据；设计系统替换不能成为业务重构的入口。
- REQ-016：以小步、可编译提交完成基础→组件→Shell→业务页面→收口迁移；最终切换必须原子化，不能长期
  保留用户可选择的新旧两套设计系统。用户可以切换暗色/浅色，但两者必须由同一组件结构与语义 token 驱动。

## 影响范围

整个 Web：设计系统双树、全局样式与字体、主题运行时、共享组件、Storybook、Shell、全部路由页面、题目
业务组件、自动化视觉/行为测试、Web 工具链，以及 `docs/design-system.md`、CLAUDE/AGENTS 和 WORK-034。
后端、API、数据库、判题与业务产品定义不在范围内。

## 主要风险

- 下载版只有暗色基线，浅色需要从共同语义和现有产品能力推导；若没有双主题逐状态验收，容易出现暗色忠实、
  浅色退化或两套组件逐渐分叉。
- 原型代码与生产要求差距大；若“直接复制”会引入 SSR/hydration、键盘、焦点、表单和安全回归。
- 99 文件来源包没有独立携带完整上游许可链，必须保留仓库现有 OpenDesign/Lucide 许可并补充新快照归因。
- 下载版的 disabled opacity、无下划线链接、可点击行、远程字体和 SVG 路径不能原样进入生产，需要可审计
  的适配；适配过度又可能失去用户喜欢的视觉。
- 全站换肤会与尚未提交的 WORK-033 重叠，若没有先冻结基线，回退和问题归因都会失真。
- 设计系统一次替换涉及大量页面，单靠测试全绿不能证明视觉正确，必须由用户对照来源预览人工验收。

## 验证方式

- AC-001：仓库内可找到下载包 99 文件的来源快照/清单、逐文件 hash 与许可说明；随机抽样和总清单均与
  `~/Downloads/Cherry OJ Design System` 一致，Web 构建不从 Downloads 或 docs 读取运行时资产。
- AC-002：`docs/design-system.md`、文档包和 Web 代码包只描述/实现新的统一设计语言；`cherry-black` 与
  `pure-white` 均有完整合同，旧品牌色 `#de1c4e`、旧 token 值和旧组件配方只存在于历史 WORK 记录。
- AC-003：暗色主题关键颜色 token 与下载版精确一致；字体特性、权重、spacing、radius、motion、1200px
  container、220px sidebar 和 56px header 在两主题共用。浅色 surface/text/border/elevation 有独立对比度与
  层级依据，并通过主题完整性和语义同构检查。
- AC-004：14 个核心组件及所有项目扩展组件在 Storybook 中呈现新视觉，保留 TypeScript/API/a11y 约束；
  不出现 inline raw style 复制、外部 CDN、`Math.random()` id、鼠标专用状态或手绘图标资产。
- AC-005：`cherry-black` 与 `pure-white` 均可通过 ThemeSwitcher 切换并持久化；刷新、首屏、登录跳转和跨
  Shell 导航保持所选主题且无闪烁。未知/损坏偏好安全回退到默认暗色，不影响登录态或其它存储。
- AC-006：全部现有用户端和管理端路由在暗色/浅色下都有桌面与 320px 的新系统截图/行为证据；Shell、列表、
  表单、Dialog、编辑器、状态和危险操作结构一致，暗色符合下载版，浅色符合相同层级与密度语言。
- AC-007：新增/修改动效只涉及 opacity/color/background 且使用 150/200ms token；reduced-motion 下归零，
  不存在 transform、scale、blur、spring、parallax 或布局高度动画。
- AC-008：题目工作台六步 UX、CodeMirror、保存并发保护、校准/发布不回归；零样例切换无意外上跳，管理
  导航顺序为 Dashboard → 账号管理 → 题目管理。
- AC-009：在线文本链接、disabled、焦点、状态、点击行、Dialog、Select、编辑器等通过键盘、对比度、
  forced-colors、200% 和屏幕阅读器可访问名称检查；对下载版的生产适配均在 DECISION-019 记录。
- AC-010：API 请求/响应、路由地址、权限、数据序列化和后端完全不变；现有功能 E2E 与全量 Web 回归通过。
- AC-011：构建不依赖 Google Fonts/unpkg/Babel CDN；Inter 与 mono 字体从本地包/系统 fallback 提供，
  lucide-react 是生产图标唯一来源，下载 UI kit 的内联路径仅留在文档快照。
- AC-012：最终产物不存在新旧设计系统混用或主题专属组件分叉；源码门禁能拒绝旧视觉 token、raw color/px、
  禁用动效、缺失主题语义和越过公共组件的页面私有样式。
- AC-013：构建体积、首屏 CSS/JS 与 WORK-033 基线有记录；新增字体和组件成本在批准预算内，未引入动画库。
- AC-014：迁移提交按计划可独立编译，最终回退能一次恢复旧设计系统而不回退 WORK-033 业务功能；发布/
  回退检查有实际演练证据。
- AC-015：CLAUDE/AGENTS、设计文档、Storybook 与工具链入口一致，未来智能体无需猜测哪个来源有效，也不会
  把下载包中的原型代码当生产实现规范。
- AC-016：`npm run check`、build、Storybook、E2E、设计系统自检、文档检查、跨模块回归、独立复核和
  `scripts/work check` 全部通过，VERIFY-035 逐条给出证据和剩余风险。

## 持续观察

本项目尚无生产环境，持续观察发生在双主题验收矩阵：全站路由切换、刷新首屏、登录/退出、桌面/320px、
长中文、CodeMirror、Dialog/菜单、零样例、reduced-motion 和 forced-colors。若用户仍感到页面像旧系统、
暗色与来源不符、浅色层级失真、主题切换回归、关键功能因换肤回归或无法单次回退，视为重建未完成。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：已按下载版来源重新定义系统级替换范围、16 项要求和验收标准，等待用户确认。
- 2026-09-03：状态变更：review → draft。原因：用户明确要求保留浅色主题，需要重新定义替换边界和验收标准。
- 2026-09-03：状态变更：draft → review。原因：已按用户反馈保留浅色主题，并将要求与验收标准改为一套新设计系统下的双主题重建。
- 2026-09-03：意图闸通过：review → approved。原因：确认保留浅色主题的双主题设计系统重建方案，并允许实施
