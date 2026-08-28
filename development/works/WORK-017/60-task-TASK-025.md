---
id: "TASK-025"
type: "task"
title: "迁移现有 Web 壳并建立设计系统回归门禁"
status: "done"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["TASK-024", "DESIGN-013", "DECISION-012", "PLAN-013"]
related: []
implements: ["CAPABILITY-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system/README.md", "docs/design-system/components.manifest.json", "docs/design-system/theme-contract.json", "docs/design-system/themes.manifest.json", "apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/index.html", "apps/web/public", "apps/web/src", "apps/web/.storybook", "apps/web/e2e", "apps/web/playwright.config.ts", ".github/workflows/ci.yml", "development/works/WORK-017"]
write_paths: ["apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/index.html", "apps/web/public/favicon.svg", "apps/web/public/icons.svg", "apps/web/scripts/check-design-system.mjs", "apps/web/src/components/ui/button.tsx", "apps/web/src/components/ui/button.test.tsx", "apps/web/src/components/ui/button.stories.tsx", "apps/web/src/routes", "apps/web/src/features/auth/components", "apps/web/src/features/system-status/components", "apps/web/e2e", "development/works/WORK-017"]
forbidden_paths: ["docs/design-system.md", "docs/design-system", "docs/frontend.md", ".github/workflows/ci.yml", "apps/web/package-lock.json", "apps/web/src/app", "apps/web/src/styles", "apps/web/src/lib", "apps/web/src/generated", "apps/web/src/features/auth/api", "apps/web/src/features/auth/lib", "apps/web/src/features/admin-users", "apps/web/src/features/system-status/api", "apps/server", "apps/judge-engine", "contracts", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# TASK-025：迁移现有 Web 壳并建立设计系统回归门禁

## 任务目标

把当前 Web 壳和已有页面的视觉接线迁移到 TASK-024 共享组件/语义 token，并把设计系统生成、禁用模式、
双主题首屏和现有业务回归加入持续门禁；业务请求、权限、路由和文案语义不变。

## 依据

实现 CAPABILITY-006 的 REQ-007～REQ-011，依据已完成的 TASK-023/TASK-024 与获批准的 DESIGN-013、
DECISION-012、PLAN-013。发现必须改变业务行为、API 或组件合同的问题时回到对应上游，不在页面迁移中
顺手决定。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- app shell、首页、登录、修改密码、管理用户、403/404 与系统状态的共享组件/semantic utility 迁移。
- 迁移现有 Button `outline` 到 `secondary`、`lg` 到 `md`，随后从 Button、测试与 Story 中
  删除两个临时兼容别名。
- 清理旧 `text-primary` 文本、透明 status border、局部 Input/Badge/Notice 重复样式和主题分支；
  保持业务逻辑与文案判据不变。
- 删除确认无消费者的紫色 Vite favicon/icons 模板资产及 HTML 引用，不在本 TASK 发明新 Logo。
- `check-design-system.mjs`：扫描 raw color/OKLCH、旧 `.dark`/`dark:`、literal theme id、
  `--ds-raw-*`、必要对比 `color-mix()` 与 disabled/placeholder opacity，并提供最小 allowlist。
- `package.json` 将 docs build/check、design-system 生成检查和 Web 扫描接入 `npm run check`。
- Playwright 主题矩阵：默认、显式黑/白、空/未知/损坏偏好、刷新无闪烁、storage/reduced-motion、
  320px 与现有业务 smoke。
- 更新 Web README/TOOLCHAIN 的接入、调试、审核和命令说明；不宣称生产已有主题切换入口。

## 完成标准

- [x] 当前 routes/features 不再复制基础组件长 class 或使用旧视觉语义，且所有原有业务测试继续通过。
- [x] Button 只保留 manifest 的 primary/secondary/ghost/danger 与 sm/md；临时 `outline`/`lg`
      已退出。
- [x] 普通品牌文字用 brand/link，不用 surface-only primary；danger/status 的前景、soft、border 配对正确。
- [x] 页面 pending/error/unauthorized/not-found/success 的判据与导航行为无变化。
- [x] 扫描能对故意加入的 raw color、`.dark`、literal theme id 和 stale 生成物做负向失败。
- [x] 默认黑与有效 pure-white 在首次绘制前正确；未知值不出现半黑半白或控制台错误。
- [x] 两主题在 320px 与桌面不裁切核心操作；长中文、键盘焦点和 reduced-motion 可用。
- [x] `npm run check` 已成为单一提交入口，CI 无需复制新命令。
- [x] 未修改 API client/generated types、服务端、contracts、设计 token 真源或业务规则。
- [x] 生产页面没有新增主题切换器，旧紫色模板资产不再进入构建产物。

## 验证

- Node 24 / npm 11：`npm run check`、`npm run build`、`npm run storybook:build`、
  `npm run test:e2e`。
- `node ../../docs/design-system/tools/build.mjs --check` 与
  `node ../../docs/design-system/tools/check.mjs`（从 `apps/web` 运行时使用正确相对路径）。
- 静态门禁负向 fixture：raw hex、OKLCH、`.dark`、`dark:`、literal theme id、`--ds-raw-*`
  和 stale generated 各自使检查失败，移除后恢复通过。
- Playwright 在缺失/空/black/white/unknown/storage failure 场景读取首个 document 状态和计算样式，
  并重复现有登录、权限、账户、管理、404 与状态 smoke。
- Storybook 双主题桌面/320px、长中文、键盘、reduced-motion 人工复核。
- `git diff --name-only` 与 TASK 路径边界对照；确认无 API/契约/后端变更。

## 风险

页面迁移最容易顺手改变业务结构，严格禁止把视觉抽取变成路由/API 重构。扫描器必须解析/限定扩展名并
保留小型 allowlist，不能靠误报“保证安全”。若旧页面需要未在 TASK-024 定义的新组件、Logo 或产品
主题入口，回到 PLAN 新建任务，不扩大本 TASK。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：完成消费者、门禁和回归边界草案，等待人工批准上游文档和明确执行授权。
- 2026-08-28：状态变更：todo → ready。原因：TASK-024 已完成且消费者迁移、门禁与浏览器回归边界齐备
- 2026-08-28：状态变更：ready → doing。原因：开始迁移现有 Web 壳并建立设计系统回归门禁
- 2026-08-28：完成 app shell、首页、登录、改密、用户管理、403/404 与系统状态迁移；业务 API、
  guards、mutation、确认和跳转判据保持不变。Button 消费者全部改为 `secondary`/`md`，随后删除
  `outline`/`lg` 临时别名。
- 2026-08-28：新增 Web 源码门禁与临时目录自测试；11 个违规 fixture、1 个 stale-generated fixture
  均被拒绝并在移除/重建后恢复，4 个合法 fixture 无误报。canonical build/check、生成检查与源码门禁
  已接入 `npm run check`；旧 favicon/icons 及引用已删除。
- 2026-08-28：新增 Playwright 主题矩阵并复跑原有业务 smoke。真实 Chromium 暴露并修复 loading Button
  缺可访问名称、forced-colors 计算值归一化和密码标签定位歧义；最终 Playwright 1.62.1 / Chrome
  Headless Shell 151.0.7922.34 共 19 个用例全部通过。
- 2026-08-28：独立复核发现并关闭 Button disabled+pressed 级联和 Link 新窗口公告两项 P2；补充 unit、
  Story 与真实浏览器计算样式回归后无剩余 finding。Node 24.20.0 / npm 11.19.0 的隔离干净
  `npm ci` 后，`check`（22 files/90 tests）、build、Storybook build 与完整 E2E 全部通过。
- 2026-08-28：状态变更：doing → done。原因：现有消费者迁移、严格门禁、旧资产清理与双主题浏览器回归完成，目标 Node/npm 干净安装验证通过
