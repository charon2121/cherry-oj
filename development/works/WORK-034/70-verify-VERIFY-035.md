---
id: "VERIFY-035"
type: "verify"
title: "验证下载版主导的双主题设计系统重建"
status: "approved"
work: "WORK-034"
owners: ["codex/root"]
depends_on: ["TASK-052", "TASK-053", "TASK-054", "TASK-055"]
related: ["WORK-015", "WORK-019", "WORK-020", "WORK-031", "WORK-033"]
implements: []
verifies: ["IMPROVEMENT-002#REQ-001", "IMPROVEMENT-002#REQ-002", "IMPROVEMENT-002#REQ-003", "IMPROVEMENT-002#REQ-004", "IMPROVEMENT-002#REQ-005", "IMPROVEMENT-002#REQ-006", "IMPROVEMENT-002#REQ-007", "IMPROVEMENT-002#REQ-008", "IMPROVEMENT-002#REQ-009", "IMPROVEMENT-002#REQ-010", "IMPROVEMENT-002#REQ-011", "IMPROVEMENT-002#REQ-012", "IMPROVEMENT-002#REQ-013", "IMPROVEMENT-002#REQ-014", "IMPROVEMENT-002#REQ-015", "IMPROVEMENT-002#REQ-016", "IMPROVEMENT-002#AC-001", "IMPROVEMENT-002#AC-002", "IMPROVEMENT-002#AC-003", "IMPROVEMENT-002#AC-004", "IMPROVEMENT-002#AC-005", "IMPROVEMENT-002#AC-006", "IMPROVEMENT-002#AC-007", "IMPROVEMENT-002#AC-008", "IMPROVEMENT-002#AC-009", "IMPROVEMENT-002#AC-010", "IMPROVEMENT-002#AC-011", "IMPROVEMENT-002#AC-012", "IMPROVEMENT-002#AC-013", "IMPROVEMENT-002#AC-014", "IMPROVEMENT-002#AC-015", "IMPROVEMENT-002#AC-016", "TASK-052", "TASK-053", "TASK-054", "TASK-055"]
tags: []
result: "pass"
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# VERIFY-035：验证下载版主导的双主题设计系统重建

## 验证对象

WORK-034 的来源冻结、docs/Web Foundation、暗色/浅色主题、共享组件、主题运行时、两个 Shell、全部可达
路由、题目工作台、生产适配、业务不变边界、性能、发布回退和独立复核。

## 对应要求

front matter 逐条锚定 `IMPROVEMENT-002#AC-001` 至 `AC-016` 及 TASK-052～055。实施后每项必须记录命令、
代码位置、截图/几何、对照来源和结论，不能用“测试全绿”代替视觉或人工验收。

## 检查与结果

| 验收项 | 证据 | 结果 |
|---|---|---|
| AC-001 来源 | 下载目录与冻结目录 `diff -qr`；99 文件、239831 bytes、根摘要 `68d93d…f9e7d`；source-lock checker 重算逐文件 hash/size 并拒绝 symlink | 通过 |
| AC-002～003 Foundation | docs/Web 2.0 双树；Cherry Black 来源精确值；Pure White 同构颜色层；65 个主题键、308 组允许组合，最低 3.4035:1 | 通过 |
| AC-004 组件 | 14 个来源核心配方与 Overlay/Feedback/Sidebar/Editor 扩展；双主题 Storybook、组件测试、Base UI 与稳定可访问名称 | 通过 |
| AC-005 主题运行时 | Chromium 覆盖首帧、损坏偏好、storage 失败、跨标签、刷新与切换持久化；两个主题共用 registry/DOM | 通过 |
| AC-006 页面 | 1280×760 同视口对照下载版 marketing/app 与首页、登录、题库、详情、管理列表、工作台；两主题及 320×720 代表页面再次并排检查 | 通过，待用户审美确认 |
| AC-007 动效 | checker 拒绝 transform/scale/translate/blur 等禁用实现；浏览器 reduced-motion 归零且主题不变 | 通过 |
| AC-008 工作台 | WORK-033 全流程 E2E；六步、CodeMirror、保存期间继续编辑、校准/发布状态不变；长步骤切空样例的内容偏移断言 76–84px | 通过 |
| AC-009 可访问性 | 语义链接/按钮、键盘 Enter/Tab、焦点、可访问名称、forced-colors、320px 与状态冗余 E2E；源码拒绝鼠标专用状态和随机 id | 通过；真实读屏器仍属人工验收 |
| AC-010 业务不变 | 生成 API drift check 通过；后端、contracts、数据库、认证与问题 API 无 source diff；Server 133 项与 Judge Engine 全包测试通过 | 通过 |
| AC-011 资产 | 构建产物使用本地 Inter/JetBrains Mono，NOTICE/OFL 随发布；checker 禁止 CDN、手绘 SVG 与 inline 原型复制 | 通过 |
| AC-012 一套系统 | 旧色值/token/variant、raw visual 值、主题分叉和页面私补丁负向扫描；19 个源码 fixture、18 个包 fixture 均正确失败 | 通过 |
| AC-013 性能 | CSS gzip 16.03 kB；工作台 757.24/256.14 kB，较 WORK-033 754.24/255.80 kB 约 +0.4%；无动画库 | 通过，既有 500 kB warning 保留 |
| AC-014 回退 | 四个 TASK 按 Foundation→组件→Shell→业务完成；tracked diff 通过 `git apply --check --reverse`，新增文件均为独立 additions；未实际回退用户工作树 | 通过 dry-run |
| AC-015 治理 | CLAUDE/AGENTS 只路由，设计合同、Storybook、Web tooling 和来源快照指向同一 2.0 系统 | 通过 |
| AC-016 门禁 | `npm run check`（32/116）、build、Storybook build、Chromium 30、Maven reactor、Go test、`git diff --check` 均通过 | 自动化通过；独立复核与人工验收待完成 |

## 未通过项

自动化与当前视觉对照没有产品失败项。第一次在受限沙箱运行 Maven/Go 时，Mockito JVM attach、Docker 和
`httptest` 本地端口被宿主权限阻止；使用同一命令在用户已授权的本地权限下重跑后全部通过，这不是源码失败。

## 范围检查

逐 TASK 对照 write/forbidden paths；TASK-055 只修改题目展示组件、对应 E2E 与 WORK-034。全工作 diff 未命中
`contracts`、`apps/server`、`apps/judge-engine`、数据库、Web 业务 API 或生成 API。TASK-055 的 route 边界名称
按当前文件结构纠正并留有执行记录，没有扩大功能范围。

## 遗留问题

独立复核尚未由另一名复核者完成；最终审美与真实管理员数据仍需用户验收。工作台懒加载 chunk 仍超过
500 kB，但与 WORK-033 基线基本持平，未因视觉迁移形成显著回归。

## 剩余风险

Pure White 没有下载版逐像素真源，它依据同一语义层级、结构和非颜色 token 推导；虽然对比度、双主题截图和
行为矩阵已通过，仍以用户视觉验收为最终判断。真实读屏器、中文输入法候选窗和生产数据没有自动化覆盖。

## 结论

WORK-034 实施、自动验证和用户视觉验收均已通过，未发现阻断性缺陷。用户于 2026-09-03 明确确认验收通过
并授权签署 WORK-034，验证结论记录为 `pass`，由验收闸统一将本文档定稿为 `approved`。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：WORK-034 自动化、视觉对照、性能与范围证据已记录，提交独立复核和用户验收
- 2026-09-03：验收闸通过：review → approved。原因：用户明确确认 WORK-034 验收通过并授权签署
