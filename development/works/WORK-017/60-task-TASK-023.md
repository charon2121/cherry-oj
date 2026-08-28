---
id: "TASK-023"
type: "task"
title: "接入 canonical token 与主题运行时"
status: "done"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["CAPABILITY-006", "DESIGN-013", "DECISION-012", "PLAN-013"]
related: []
implements: ["CAPABILITY-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system/README.md", "docs/design-system/theme-contract.json", "docs/design-system/themes.manifest.json", "docs/design-system/tokens.css", "docs/design-system/tailwind-v4.css", "docs/frontend.md", "apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/index.html", "apps/web/vite.config.ts", "apps/web/src/styles", "apps/web/src/main.tsx", "apps/web/src/test/setup.ts", "development/works/WORK-017"]
write_paths: ["docs/frontend.md", "apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/index.html", "apps/web/vite.config.ts", "apps/web/scripts", "apps/web/public/generated", "apps/web/src/styles", "apps/web/src/index.css", "apps/web/src/lib/theme", "apps/web/src/generated/design-system", "apps/web/src/main.tsx", "apps/web/src/test/setup.ts", "development/works/WORK-017"]
forbidden_paths: ["docs/design-system.md", "docs/design-system", "apps/web/src/app", "apps/web/src/routes", "apps/web/src/features", "apps/web/src/components", "apps/web/src/generated/api", "apps/web/e2e", "apps/server", "apps/judge-engine", "contracts", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# TASK-023：接入 canonical token 与主题运行时

## 任务目标

让 Web 构建直接消费已批准的设计 token/adapter，并建立 manifest 派生、首屏无闪烁、可持久化且可测试
的主题运行时；业务路由和共享组件保持不变，为后续两个 TASK 提供稳定基础。

## 依据

实现 CAPABILITY-006 的 REQ-001～REQ-006、REQ-010 中的生成漂移部分和 REQ-011 的主题基础场景，
只依据获人工批准后的 DESIGN-013、DECISION-012 与 PLAN-013。不得重新决定 DESIGN-012 的 token 数值、
theme id、默认主题或 adapter。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `globals.css` 直接接入 canonical tokens/adapter，移除旧本地 token、`.dark`、Geist 与重复映射。
- Inter Variable 依赖与更新后的锁文件；保留合同规定的 CJK/mono 回退。
- manifest 驱动 generator、`src/generated/design-system/themes.ts`、
  `public/generated/theme-init.js` 和生成物漂移命令。
- HTML 默认主题/语言/标题与首屏外部脚本；Vite/Storybook 后续可复用的 docs 只读构建配置。
- `src/lib/theme` resolver、DOM/store/React API 及缺失、未知、存储异常、跨标签页单测。
- `main.tsx` 的主题运行时装配；不新增可见切换器。
- 删除未使用的 `src/index.css`；同步 Web README/TOOLCHAIN 和 `docs/frontend.md` 的 Base UI 基线。

## 完成标准

- [x] Web 不保存主题色或第二份 Tailwind adapter，构建可解析 docs 的稳定 CSS 入口。
- [x] `:root` 默认黑，pure-white 显式生效；missing/empty/unknown 均在首次绘制前为黑。
- [x] `data-color-scheme` 精确由 manifest 派生，只有生成/主题模块出现 literal theme id。
- [x] 生成文件可重复，手改、漏生成、无效 manifest 与 stale output 都使 check 失败。
- [x] localStorage 读写异常不白屏；写失败当前会话仍应用，storage event 能收敛。
- [x] 首屏脚本不 fetch/eval/跟随系统主题，失败时 HTML 默认黑色仍可用。
- [x] Geist、旧 OKLCH token、`.dark` 定义和未使用 `src/index.css` 已退出。
- [x] 未修改 routes/features/components、API 生成物、docs token 真源或后端。

## 验证

- `node docs/design-system/tools/build.mjs --check`
- `node docs/design-system/tools/check.mjs`
- `npm run generate:design-system:check`
- 主题模块 Vitest：missing/empty/black/white/unknown、storage read/write failure、storage event。
- `npm run format:check && npm run lint && npm run typecheck && npm run test:run`
- `npm run build` 后检查 `dist` 含主题初始化脚本且浏览器不请求 docs URL。
- `git diff --name-only` 与 TASK read/write/forbidden path 对照。

## 风险

跨目录 CSS import、首屏脚本顺序和生成物双端协议是主要风险。若 Vite/Storybook 无法稳定直接 import，
先回到 DESIGN 比较 package 方案，不手工复制 token。若需要 inline CSP 例外、服务端主题同步或改
theme contract，保持 TASK todo/blocked 并升级上游。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：完成任务边界、产出与验证草案，等待人工批准上游文档和明确执行授权。
- 2026-08-28：状态变更：todo → ready。原因：上游文档已获用户批准，任务边界和完成标准齐备
- 2026-08-28：状态变更：ready → doing。原因：开始接入 canonical token、manifest 生成与主题运行时
- 2026-08-28：Web 已直接打包 canonical CSS，manifest 生成器、首屏脚本、主题 controller/Provider、
  Inter 与长期文档接线完成。Node 26 临时实施环境下，docs build/check、生成漂移、格式、ESLint、
  TypeScript、11 个测试文件/47 个用例和 Vite build 全部通过；最终 Node 24/npm 11 复验归 VERIFY-017。
- 2026-08-28：构建产物含与源码一致的 `dist/generated/theme-init.js`，`dist` 不含 `docs/design-system`
  运行时 URL；变更范围未触及 routes/features/components、API 生成物、设计 token 真源或后端。
- 2026-08-28：后续真实浏览器矩阵确认首屏默认/显式/未知/storage failure 与跨标签页行为；controller
  增加 storageArea 过滤，NodeNext import 使用生成 registry 的 `.js` 说明符。最终目标版本干净安装验证
  归 VERIFY-017。
- 2026-08-28：状态变更：doing → done。原因：canonical CSS、manifest 生成、首屏主题、React 运行时与文档接线已完成，定向和全量 Web 检查及构建均通过
