---
id: "PLAN-022"
type: "plan"
title: "分阶段重建 Cherry OJ 双主题 Web 设计系统"
status: "checked"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-002", "DESIGN-028", "DECISION-019"]
related: ["WORK-015", "WORK-031", "WORK-033"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# PLAN-022：分阶段重建 Cherry OJ 双主题 Web 设计系统

## 目标

把下载版 Cherry OJ Design System 从外部设计产物转成可追溯、可验证、可回退的生产系统，并迁移全部
现有页面。迁移不改变业务行为，最终只保留一套设计系统，并继续提供暗色与浅色主题。

## 改动区域

- 来源与规范：下载版 99 文件快照、`docs/design-system.md`、`docs/design-system/`、NOTICE/LICENSE。
- 运行时：`apps/web/design-system/`、全局样式、生成/检查脚本、字体/package、双主题生成物与主题运行时。
- 组件：全部 `apps/web/src/components/ui/`、Storybook 与测试。
- 应用：站点/管理 Shell、页面、routes 展示层、auth/admin-users/problems 组件与 E2E。
- 治理：CLAUDE、AGENTS、TypeScript/TOOLCHAIN、WORK-034。

禁止修改后端、contracts、生成 API 的业务定义、业务 API client、数据库和 WORK-033 历史。route tree 只允许
由既有生成器刷新，不手改。

## 阶段与顺序

### 0. 冻结前置基线

1. 完成 WORK-033 人工验收并形成可回退 Git 基线；记录当前 build、E2E、Storybook 和 bundle 结果。
2. 对下载目录读取文件数、大小、相对路径和 SHA-256；确认没有隐藏可执行依赖、秘密或缺失许可。
3. 记录现有路由/组件/主题消费者清单，防止迁移漏页。

### 1. TASK-052：来源、合同与运行时 Foundation

1. 将 99 文件原样快照到 docs 来源目录，生成 source lock，保留 OpenDesign/Lucide 许可与新 NOTICE。
2. 重写中文设计合同和文档包，明确 exact source 与生产适配。
3. 重建 Web 共用 token、暗色/浅色主题、Tailwind adapter、manifest、builder/checker；先让设计系统自检通过。
4. 引入本地 mono 字体依赖（若体积/许可检查通过），保持 Inter 本地化，禁止 Google Fonts/CDN。
5. 更新 CLAUDE/AGENTS/TOOLCHAIN/TypeScript 路由和检查说明。

该阶段不切换页面；通过临时 adapter 让现有组件能编译，但不得发布混合视觉。

### 2. TASK-053：共享组件

1. 按 Controls → Typography/Layout → Forms → Surfaces → Navigation 顺序迁移下载版 14 个核心配方。
2. 按 Overlay/Feedback/Sidebar/Editor 顺序迁移生产扩展组件，保留 Base UI 与 a11y。
3. 每组同步 Storybook、组件测试和源码门禁；用下载版 specimen/UI kit 做视觉对照。
4. 删除 legacy variant 和无消费者兼容层，类型检查确保调用点已明确迁移。

### 3. TASK-054：双主题运行时、Shell 与基础页面

1. 保留并收敛 ThemeSwitcher、ThemeProvider/registry/bootstrap 和偏好持久化，让 `cherry-black` 与
   `pure-white` 都只消费新的完整主题合同。
2. 迁移 Site/Admin Shell、AppBrand、账号菜单、导航/移动 Sheet；固定 220px sidebar 和 56px header。
3. 迁移首页、登录、改密、403/404/Async 状态和管理 Dashboard/用户账号。
4. 在两主题下验证登录/退出/returnTo、首屏无闪烁、偏好保持、内部滚动和 320px。

### 4. TASK-055：题目业务页面与全站视觉收口

1. 迁移题库列表/详情、管理题目列表/创建 Dialog 和六步工作台。
2. 修复零样例切换滚动上跳，并复核 TASK-054 已完成的 Dashboard → 账号管理 → 题目管理导航顺序。
3. 对所有可达路由在两主题下执行桌面/320px、长中文、键盘、forced-colors、reduced-motion 和状态矩阵。
4. 比较来源 UI kit/Storybook/产品截图，逐项关闭旧 token、旧 Card、圆角、密度、字体和动效偏差。

### 5. 最终复核与验收材料

1. 全量 check/build/Storybook/E2E、设计系统自检、跨模块回归、bundle/font 体积和 Git 范围检查。
2. 独立复核来源忠实度、生产适配、安全/a11y、API 不变和回退能力。
3. VERIFY-035 按 AC-001–016 收集命令、hash、截图与剩余风险；MEMORY-027 只记录验收后长期结论。

## 并行与依赖

Foundation 是所有组件的前置；核心组件是 Shell/页面前置；Shell 是业务页面视觉验收前置。组件 Storybook 可
按组推进，但共享 token/API 修改不能与页面迁移并行写同一文件。TASK-055 必须建立在 WORK-033 验收基线上。
每个 TASK 完成后执行本阶段回归，不能把所有错误留到最终批次。

## 迁移与交付

源码可用多个小 commit 迁移，每个 commit 必须编译；最终交付只允许单一新系统，暗色/浅色是该系统的两个
主题而非新旧版本。用户未要求 commit/push 前不执行 Git 写操作。无数据库/后端部署。来源快照与生产代码在
同一 WORK 中提交，以便任何视觉值都可追溯。

## 风险

- 浅色没有下载版直接视觉来源；必须先在 Foundation/组件阶段确认它与暗色语义同构且观感可接受，再迁移页面。
- 全站迁移易漏状态和路由；使用自动枚举与截图矩阵，不靠人工记忆。
- 临时 adapter 可能长期残留；TASK-055 完成前由源码门禁禁止旧 token/variant。
- 字体会增加资源体积；先测实际 WOFF2，再决定是否添加 JetBrains Mono 包或只用系统 fallback。
- 下载原型含不可直接复用代码；代码审查需逐项搜索 CDN、inline SVG/path、随机 id、onMouse 状态和 raw 值。
- 当前工作树包含未提交 WORK-033；未先冻结就实施会破坏回退边界。

## 验证

- 来源：99 文件、总大小、逐文件 SHA-256、NOTICE/LICENSE、docs/Web 无运行时反向依赖。
- Foundation：暗色精确 token、浅色语义依据、双主题键完整性/对比矩阵、生成确定性、旧值负向 fixture。
- 组件：双主题 Storybook/a11y、键盘/焦点、disabled/loading/error、320px、长中文和截图对照。
- Shell/页面：两个主题下全部 16 个当前 route 的关键状态，登录/权限/滚动/表单/编辑器/危险操作。
- 动效：源码和浏览器证明只动画 opacity/color/background；reduced-motion 为 0ms。
- 回归：`npm run check`、`npm run build`、`npm run storybook:build`、`npm run test:e2e`、
  `scripts/work check`、`git diff --check`。
- 视觉：同 viewport 对照下载版 UI kit、Storybook 与产品截图，由用户做最终审美确认。

## 回退

每个批次以独立 commit 恢复；若 Foundation/组件方向被否决，在页面迁移前整体回退 WORK-034。若后期单页
回归，可回退该页批次，但最终验收前不能发布混合系统。最终回退恢复旧设计系统及其双主题实现和页面样式，
不回退 WORK-033 业务功能、主题偏好或任何用户数据。实际执行 `git revert` 前仍需用户授权。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：已拆分为 Foundation、共享组件、Shell/基础页、题目业务页四个可验证阶段，等待用户确认。
- 2026-09-03：状态变更：review → draft。原因：实施阶段、回退和验证矩阵需要纳入双主题迁移。
- 2026-09-03：状态变更：draft → review。原因：已将 Foundation、组件、Shell、页面、回退和验收矩阵全部调整为双主题。
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
