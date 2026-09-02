---
id: "VERIFY-032"
type: "verify"
title: "统一页面任务优先布局并移除状态占位"
status: "approved"
work: "WORK-031"
owners: ["codex/root"]
depends_on: ["TASK-049"]
related: []
implements: []
verifies: ["FEATURE-009#REQ-001", "FEATURE-009#REQ-002", "FEATURE-009#REQ-003", "FEATURE-009#REQ-004", "FEATURE-009#REQ-005", "FEATURE-009#REQ-006", "FEATURE-009#REQ-007", "FEATURE-009#REQ-008", "FEATURE-009#AC-001", "FEATURE-009#AC-002", "FEATURE-009#AC-003", "FEATURE-009#AC-004", "FEATURE-009#AC-005", "FEATURE-009#AC-006", "FEATURE-009#AC-007", "FEATURE-009#AC-008", "TASK-049"]
tags: []
result: "pass"
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# VERIFY-032：统一页面任务优先布局并移除状态占位

## 验证对象

FEATURE-009 全部 REQ/AC、TASK-049，以及 status 契约删除、页面构成、统一 inset、账号菜单入口、Admin
滚动所有权、响应式与可访问性。

## 对应要求

front matter 已锚定 FEATURE-009#REQ-001～REQ-008、AC-001～AC-008 与 TASK-049。实施后按 API 删除、
任务优先语义、24px 坐标、账号入口、滚动、长期规范、全量门禁和范围八组记录实际证据。

## 检查与结果

- status 全链路：OpenAPI path/schema、Gateway Controller/DTO/测试、Web query/UI/单测、首页消费、E2E mock
  和生成类型全部删除；`rg` 在 `contracts`、Web、Gateway 与工具链说明中无残留。Actuator 未修改，完整
  server 构建仍显示各服务暴露既有两个 Actuator endpoint。
- 页面构成：主页不再显示介绍区或连通状态，也未增加替代占位；题库、用户账号、题目管理、Dashboard 和
  修改密码移除通用可见 intro，使用 `sr-only` H1 保留语义。题目详情/工作台的对象标题、登录、403/404、
  loading/empty/error 与表单/区段标题保持可见。
- 统一间距：共享 `Section` 固定使用 `--ds-space-6` 顶部间距，所有路由既有 `py-8/10/16/20` 与响应式
  `pt-*` 覆盖均删除。Playwright 在 1280×720 实测管理 Header 下首个表单 inset 为 24px；共享 Layout
  单测钉住 token，320px、640px（200% 等效）与桌面构建无水平溢出。
- 导航与滚动：管理 Header 已无独立“返回用户端”链接，320px 与桌面均从头像菜单看到完整菜单项；用户端
  ADMIN 的“管理中心”、主题切换、退出重试和移动 Sheet 回归通过。长用户列表实测 `main.scrollTop > 0`、
  `window.scrollY = 0`、Sidebar 位移不超过 1px；移动端仍走文档滚动和 Sheet。
- 自动化命令与结果：
  - `cd apps/web && npm run check`：设计系统、OpenAPI 生成、format、lint、typecheck 与 109 项单测通过；
  - `npm run build`、`npm run storybook:build`：生产包与 Storybook 构建通过；
  - `npm run test:e2e -- e2e/smoke.spec.ts e2e/design-system.spec.ts`：Chromium 27 项全部通过，覆盖双主题、
    forced-colors、reduced-motion、320px、登录 200% 等效尺寸、账号菜单和滚动坐标；
  - `./mvnw -pl gateway-service test`：55 项通过；`./mvnw clean verify`：7 个 Reactor 模块成功，133 项
    通过、1 项既有 Linux-only 集成测试跳过；
  - `node docs/design-system/tools/check.mjs`、`scripts/work check` 与 `git diff --check` 通过。
- 长期规范：`docs/design-system.md` 已写入任务优先页面、允许保留的标题类型、24px 共享起点、账号菜单
  跨端入口和 Admin 桌面滚动所有权；未改设计系统合同、主题或生成资产。

## 未通过项

暂无产品未通过项。首次直接运行 Playwright 时 preview 使用了旧 `dist`，暴露旧页面断言；执行生产 build
后复跑并更新与新语义一致的断言，27 项全绿。Gateway 测试首次在受限沙箱内因 Mockito/Byte Buddy 无法
self-attach 且 Docker 不可见而失败；获准在非沙箱环境用原命令复跑后 55 项全绿，完整 server verify
也通过，判定为执行环境限制而非代码缺陷。

## 范围检查

逐项对照 TASK-049：业务改动只在允许的 Web shells/Layout/routes/pages/problem 组件/status 删除范围、
Gateway status 目录、Web API 契约/生成物、工具链说明和本 WORK。没有修改 `apps/web/design-system`、
`docs/design-system/`、主题运行时/生成资产、其它服务源码、其它合同、路由层级、数据或数据库。完整 server
verify 产生的 `target` 以及 Web/Storybook 构建产物均为忽略的验证输出，不进入 diff。

## 遗留问题

仓库内没有 `/api/status` 消费者；仓库外客户端无法由本地搜索证明，若存在需由部署方按公开 API 变更另行
通知。此接口原本只供首页占位，且 WORK 明确批准直接删除，因此不在本任务增加兼容代理或弃用期。

## 剩余风险

main-only scroll 自动化当前以 Chromium 为主，Safari/Firefox 的滚动恢复仍需持续关注；新增沉浸式模板或
多层固定工具栏时应重新评审 24px/滚动合同。无可见 intro 的页面必须继续维护 document title 或隐藏 H1，
否则后续重构可能产生语义回归。仓库外 `/api/status` 消费者仍是部署沟通层面的剩余假设。

## 结论

实现与自动化验证通过，等待用户人工验收。页面现在直接进入任务，双端首内容统一为 24px，管理端跨空间
入口归入头像菜单，桌面只滚动 main；`/api/status` 已从公开契约、Gateway 与 Web 全链路删除，Actuator
运维健康检查保持不变。

## 变更记录

- 2026-09-02：创建验证清单并锚定 FEATURE-009 八项 REQ、八项 AC 与 TASK-049。
- 2026-09-02：记录 status 删除、页面语义、24px 坐标、账号菜单、Admin 滚动及前后端全量回归证据。
- 2026-09-02：状态变更：draft → review。原因：Web 109 项、Playwright 27 项、Gateway 55 项、server 133 项及构建/范围门禁全部通过
- 2026-09-02：验收闸通过：review → approved。原因：用户明确验收通过并授权签署 WORK-031 验收闸
