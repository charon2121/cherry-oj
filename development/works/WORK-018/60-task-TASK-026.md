---
id: "TASK-026"
type: "task"
title: "解除 Web 对设计系统文档目录的依赖"
status: "done"
work: "WORK-018"
owners: ["codex/root"]
depends_on: ["CHANGE-008", "DESIGN-014", "DECISION-013", "PLAN-014", "TASK-025"]
related: []
implements: ["CHANGE-008"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system", "docs/frontend.md", "apps/web", ".github/workflows/ci.yml", "development/works/WORK-017", "development/works/WORK-018"]
write_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system/README.md", "docs/frontend.md", "apps/web/design-system", "apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/.prettierignore", "apps/web/package.json", "apps/web/vite.config.ts", "apps/web/src/styles/globals.css", "apps/web/scripts/generate-design-system.mjs", "apps/web/scripts/check-design-system.mjs", "apps/web/src/generated/design-system", "apps/web/public/generated", "apps/web/public/legal", "apps/web/e2e/design-system.spec.ts", "development/works/WORK-018"]
forbidden_paths: ["docs/design-system/tokens.foundation.css", "docs/design-system/tokens.css", "docs/design-system/tailwind-v4.css", "docs/design-system/theme-contract.json", "docs/design-system/themes.manifest.json", "docs/design-system/themes", "docs/design-system/design-tokens.json", "docs/design-system/components.manifest.json", "docs/design-system/components.html", "docs/design-system/preview", "docs/design-system/tools", "docs/design-system/manifest.json", "docs/design-system/LICENSE.open-design", "docs/design-system/LICENSE.lucide", "docs/design-system/NOTICE.md", "apps/web/package-lock.json", "apps/web/src/components", "apps/web/src/features", "apps/web/src/routes", "apps/web/src/generated/api", "apps/web/src/lib/theme", "apps/web/src/lib/http", "apps/web/src/lib/api", ".github/workflows/ci.yml", "apps/server", "apps/judge-engine", "contracts", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016", "development/works/WORK-017"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---










# TASK-026：解除 Web 对设计系统文档目录的依赖

## 任务目标

把 WORK-017 的 Web 设计系统实现重构为代码侧自包含，彻底移除 `apps/web` 对
`docs/design-system` 的构建、生成、检查和运行依赖；保留现有视觉/运行行为、可访问性门禁与合规信息，
并用真正删除该目录的干净副本证明结果。

## 依据

实现 CHANGE-008 的 REQ-001～REQ-011，依据已批准的 DESIGN-014、DECISION-013 和 PLAN-014；以
TASK-025 的现有主题/组件/页面实现为行为基线。实施只执行已批准的代码侧自包含路线；若需要改变 token、
主题合同、组件 API、业务行为或依赖范围，停止本 TASK 并回到上游。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `apps/web/design-system` 自包含代码包：运行源、窄 manifest/README、本地生成与合同检查、来源与许可。
- 全局 CSS、Vite、主题 generator、源码 scanner/self-test 和 `package.json` 全部改读本地包。
- 继续生成兼容的 `themes.ts`/`theme-init.js`，并让必要 notice/license 随静态站分发。
- 本地 checker 的运行合同规则与负向 fixture；不复制 reference HTML、preview、组件 manifest 或机器
  token 文档快照。
- `apps/web` 跨 docs 路径零引用门禁，E2E 不请求任何 `/docs/` 资源。
- Web README/TOOLCHAIN 与必要全局说明更新为代码运行权威、文档仅说明、真实设计变更同 WORK 同步。
- 正常工作区和无设计 docs 干净副本的完整验证证据，记录于 VERIFY-018。

## 完成标准

- [x] Web 本地包能独立生成/检查 `tokens.css`，所有输入、工具、fixture、NOTICE 和许可证均位于
      `apps/web`；package manifest 不登记纯文档预览资产。
- [x] 本地合同检查继续验证 2 主题、56 required key、296 允许组合，完整精度最低值与基线
      `3.4035078594052393` 一致且三位小数仍报告 `3.404:1`；逐项固定 Foundation/theme 值、contract
      entry 语义、selector/color-scheme，迁移时与原源做一次性精确等价。拒绝缺 token、仍满足阈值的
      错误值、allowedOn 替换、alpha、低对比、越界/symlink 主题、adapter/生成物漂移和许可来源异常。
- [x] `globals.css`、Vite、generator、scanner/self-test 和 package scripts 不读取/执行 docs，Vite 删除
      外部 docs allow；没有 prebuild copy、symlink、fetch 或 docs fallback。
- [x] `apps/web` 构建关键文件对 `docs/design-system` 零引用；E2E 泛化为无 `/docs/` 请求，
      `dist`/`storybook-static` 也无该路径或 URL。
- [x] 主题 registry、bootstrap、runtime API、storage/根属性、组件、Storybook、页面与业务回归均保持；
      未修改被禁止的主题值、组件/业务/API 文件。
- [x] 来源 NOTICE/Apache-2.0 许可证在代码侧完整且被本地 checker 验证，生产静态分发保留必要材料。
- [x] `package-lock.json` 和 CI 工作流不变；`npm run check` 仍是单一 Web 提交入口且只串联本地设计系统工具。
- [x] Node 24/npm 11 正常工作区中 check、build、Storybook build、完整 E2E 全部通过。
- [x] 正常工作区的 Vite dev server 在有超时和清理的 smoke 中返回首页/CSS，无 5xx、资源 404 或
      `/docs/` 请求后正常终止。
- [x] 临时完整仓库确认 `docs/design-system` 不存在，且排除所有依赖/旧产物后从 `npm ci` 重复全链路通过。
- [x] 同一隔离副本的 Vite dev server 能启动并返回首页/CSS，无 5xx、资源 404 或 `/docs/` 请求；本地
      design-system 全树无 symlink，manifest 每个真实路径都留在包根内。
- [x] Web/全局说明不再把 docs 写成构建输入，明确仅在真正修改设计系统的 WORK 中同步代码与文档。

## 验证

- `node design-system/tools/build.mjs --check`、`node design-system/tools/check.mjs` 及其负向 self-test；
  覆盖错误但仍达标的 token、contract allowedOn 替换与 symlink escape。
- `npm run generate:design-system:check`、`npm run check:design-system:source`、
  `npm run check:design-system:self-test`；注入并恢复非法源码与 stale runtime 生成物。
- 路径/配置扫描覆盖 source、scripts、package、Vite、public 和构建产物；确认无跨 docs import、执行、copy、
  symlink、fetch 或 allow。
- Node 24/npm 11：`npm run check`、`npm run build`、`npm run storybook:build`、
  `npm run test:e2e`，记录测试数量、构建 modules 与浏览器版本；在正常工作区另做有超时/清理的
  Vite dev 首页/CSS HTTP smoke。
- 用临时完整仓库排除 `docs/design-system`、`node_modules`、`dist`、`storybook-static`、测试结果和缓存；
  断言目录不存在且本地包无 symlink，`npm ci` 后重复上一组命令。E2E 必须消费本轮 fresh build；另启动
  Vite dev，实际请求首页/CSS、确认无 5xx/404/docs 请求后正常终止。
- 对照 TASK 路径边界和 `git diff --name-only`；确认无锁文件、CI、设计值、组件/业务、API、后端、判题或
  contracts 变更，并独立复核本地 checker 没有降级。

## 风险

不要把“复制当前生成 CSS”误当成自包含，也不要把 docs checker 的文档包职责整套搬入 Web。对比度和
许可验证必须保留，旧产物必须排除。若本地运行资产清单与当前 docs 不一致，先判断是迁移错误还是需要
新的设计修改；后者不在 TASK-026 内顺手修。若无可用 Node 24/Chromium，只能记录为验证阻塞，不能用
Node 26、jsdom 或旧 `dist` 代签。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：完成依赖、最小运行资产、合同校验、许可证与干净隔离验收边界审计；任务保持 todo，等待
  人工批准 CHANGE-008、DESIGN-014、DECISION-013、PLAN-014 并明确授权执行。
- 2026-08-28：状态变更：todo → ready。原因：四份上游文档已获用户批准，旧 direct-docs 文档已正式废弃或替代，实施边界完整
- 2026-08-28：状态变更：ready → doing。原因：开始建立 Web 本地设计系统包并解除全部 docs 依赖
- 2026-08-28：在 `apps/web/design-system` 建立自包含运行包；机械迁入 8 项运行资产并逐字确认与批准时
  文档源一致，本地 builder 只生成 `tokens.css`，窄 manifest 不登记 preview、reference HTML、组件清单
  或机器 token 文档快照；NOTICE/Apache-2.0 同步到 `public/legal`。
- 2026-08-28：全局 CSS、主题 generator、源码 checker、package scripts 与 E2E 改为只消费 Web 本地资产；
  删除 Vite 外部 docs allow，并增加跨 docs 路径、运行资产边界、生成漂移、许可分发和无 `/docs/` 请求门禁。
- 2026-08-28：独立 checker 复核发现 selector 注释伪装、重复 `color-scheme`、contract 布尔语义、额外
  rule/manifest 资产、路径归一化与 symlink 等绕过面；逐项加固并复跑攻击 fixture，最终无剩余 P1/P2。
- 2026-08-28：Node 24.19.0/npm 11.19.0 正常工作区通过 check、production build、Storybook build、
  19 项 Playwright 与 Vite HTTP smoke；删除 `docs/design-system` 且排除依赖/旧产物的隔离仓库从
  `npm ci` 重复同一矩阵全部通过。完整证据见 VERIFY-018。
- 2026-08-28：状态变更：doing → done。原因：Web 本地设计系统包、自包含门禁、正常与无 docs 隔离矩阵全部通过
