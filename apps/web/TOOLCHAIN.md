# Web 工具链说明

这份文档从“开发一个页面”出发解释 `apps/web/package.json` 里的直接依赖。目标不是让每个人背包名，而是让评审者能判断：这个工具解决了什么问题、在哪个阶段生效、它的产物是否需要验收。

## 先分清 dependencies 和 devDependencies

`dependencies` 通常会被应用源码直接导入，或者参与最终样式与浏览器代码的生成。`devDependencies` 只在开发、检查、测试或构建阶段运行。

本项目最终部署的是 `npm run build` 生成的 `dist/` 静态文件，并不会在生产服务器上运行 Vite、ESLint、Vitest 或 Storybook。也就是说，`devDependency` 不是“不重要”，只是它负责保证交付质量，而不是承担线上功能。

当前 `@tailwindcss/vite`、`tailwindcss` 和 `shadcn` 记录在 `dependencies` 中，但职责更接近构建插件或开发期 CLI。这是已知的分类边界，不影响现有构建；调整分类需要单独任务验证锁文件和 CI，本次只如实说明。

## 一个页面从源码到交付

```text
路由文件和 React 组件
        │
        ├─ TypeScript 检查类型
        ├─ ESLint 检查代码和依赖方向
        ├─ Prettier 统一格式
        ├─ Vitest + Testing Library 验证组件
        ├─ Storybook 单独展示组件状态
        └─ Playwright 从浏览器验证关键链路
        │
Vite 整合 Router、React、Tailwind 并生成 dist/
```

产品经理通常需要审核页面结果、状态覆盖和用户链路，不需要审核某个编译插件的内部实现。技术评审则要确认每个工具是否仍有明确职责，避免重复引入解决同一问题的工具。

## 浏览器中真正使用的基础能力

### `react` 与 `react-dom`

`react` 提供组件、Hook 和界面更新模型；`react-dom` 把 React 应用挂载到浏览器 DOM。两者共同构成前端运行时，删除任意一个都会让应用无法启动。

### `@tanstack/react-router`

负责页面地址、嵌套路由、链接、404 和类型安全的 URL 参数。文件路由定义在 `src/routes/`，生成的路由树在 `src/routeTree.gen.ts`。产品侧能感知的是“地址能否分享、前进后退是否正确、页面不存在时怎么处理”。

### `@tanstack/react-query`

负责服务端数据的请求状态、缓存、重新获取、Mutation 和后续的判题结果轮询。它避免每个页面重复手写 loading、错误处理和缓存逻辑。它不负责本地弹窗开关，也不替代 Router 管理 URL。

### `zod`

在浏览器收到不可信 JSON 时校验 ApiSuccess、ApiProblem、request ID 和 endpoint 关键字段。OpenAPI
生成类型只在编译期生效，不能替代这层运行时检查。响应 schema 容忍新增的未知可选字段，以支持兼容演进。

### `@base-ui/react`

提供无样式、可访问的交互基础能力，适合构建对话框、菜单等复杂组件。它解决键盘操作、焦点和 ARIA 行为，视觉样式仍由项目自己决定。

### `lucide-react`

提供 React 图标组件。它用于表达搜索、关闭、状态提示等辅助语义；重要结论不能只靠图标或颜色传达。

### `@fontsource-variable/geist`

把 Geist 可变字体作为 npm 资源随静态站构建，避免运行时依赖外部字体 CDN。入口在 `src/styles/globals.css`。

## 样式与组件拼装

### `tailwindcss`

读取源码中的工具类并生成实际 CSS，也是项目颜色、字号和间距 token 的载体。它参与构建，不是浏览器里的 JavaScript 框架。

### `@tailwindcss/vite`

把 Tailwind 接入 Vite，使开发服务器和生产构建都能处理 Tailwind 语法。配置入口是 `vite.config.ts`。

### `shadcn`

用于把 shadcn 组件源码和约定加入项目。它更像开发期脚手架，而不是运行时组件库；生成进仓库的组件由项目自己维护。

### `tw-animate-css`

提供与 Tailwind 配合的动画类，供弹窗、折叠等交互使用。它由全局样式导入，若移除需要先确认没有组件依赖这些类。

### `class-variance-authority`

把按钮尺寸、颜色、状态等 class 组合声明成可检查的 variant。它让“同一组件有多种产品状态”变得集中且可复用。

### `clsx`

根据条件拼接 class，例如只在错误状态加入红色边框。它解决条件表达，不处理 Tailwind 类冲突。

### `tailwind-merge`

合并 Tailwind class 并消除冲突，例如同时出现两个 padding 时保留最终有效值。项目的 `cn()` 通常把它和 `clsx` 组合使用。

## 开发服务器与生产构建

### `vite`

开发时启动 5173 端口、提供快速刷新并代理 `/api`；构建时把源码、样式和资源打包进 `dist/`。配置入口是 `vite.config.ts`。它不承担生产服务端业务。

### `@vitejs/plugin-react`

让 Vite 正确转换 React JSX，并接入 React 的开发期刷新能力。没有它，Vite 不知道该怎样按当前约定处理 React 源码。

### `@tanstack/router-plugin`

扫描 `src/routes/`，生成类型安全的路由树，并在构建时启用自动代码拆分。它生成的 `routeTree.gen.ts` 应提交，但不应手改。

## TypeScript 与类型声明

### `typescript`

检查类型并把 TypeScript 项目纳入构建。`npm run build` 会先执行 `tsc -b`，因此类型错误会阻止生成交付物。

### `@types/react`、`@types/react-dom` 与 `@types/node`

前两个为 React 和浏览器挂载 API 提供 TypeScript 类型；`@types/node` 为 Vite、Playwright 等 Node 配置文件提供类型。它们只服务开发和构建，不进入浏览器运行逻辑。

### `@hey-api/openapi-ts`

读取 `contracts/web-api.openapi.json`，只生成 `src/generated/api` 的 TypeScript 类型，不生成或接管
项目的 fetch client。版本被精确固定以避免 0.x 生成行为漂移；`openapi-ts.config.mjs` 是配置入口，
`generate:api:check` 在临时目录重建并与已提交生成物逐文件比较。

## 代码正确性与格式

### `eslint`

执行静态规则检查。项目用它检查常见错误、React Hook、可访问性、导入顺序和目录依赖方向，配置入口是 `eslint.config.js`。

### `@eslint/js`

提供 JavaScript 官方推荐规则，作为 ESLint Flat Config 的基础。

### `typescript-eslint`

让 ESLint 理解 TypeScript，并启用需要类型信息的规则，例如禁止无理由的 `any`、统一类型导入。

### `eslint-plugin-react-hooks`

检查 Hook 调用位置和依赖，避免 Hook 顺序变化或闭包读取旧数据等 React 问题。

### `eslint-plugin-jsx-a11y`

检查 JSX 中常见的可访问性错误，例如缺少标签、错误的交互元素和键盘支持。它是自动检查，不替代人工可用性验收。

### `eslint-plugin-simple-import-sort`

统一 import 和 export 的顺序，减少无意义的评审差异。

### `globals`

告诉 ESLint 哪些浏览器或 Node 全局变量是合法的，避免把 `window`、`process` 等误报成未定义。

### `prettier`

只负责统一缩进、换行等格式，不判断业务正确性。`format` 会改写文件，`format:check` 只检查。

### `eslint-config-prettier`

关闭与 Prettier 冲突的 ESLint 格式规则，让 ESLint 管正确性、Prettier 管排版，两者不重复争夺同一职责。

### `prettier-plugin-tailwindcss`

在 Prettier 格式化时统一 Tailwind class 顺序，降低样式代码的评审噪声。

## 组件测试

### `vitest`

执行快速的单元和组件测试，与 Vite 共用转换配置。`npm run test` 进入监听模式，`npm run test:run` 单次执行，后者属于提交检查。

### `jsdom`

在 Node 中模拟浏览器 DOM，让组件测试不必每次启动真实浏览器。它适合组件行为，不等同于真实浏览器验收。

### `@testing-library/react`

把 React 组件渲染到测试 DOM，并鼓励从用户可见内容和可访问角色查询元素。

### `@testing-library/dom`

提供跨框架的 DOM 查询和事件基础，是 React Testing Library 的底层能力，也可直接用于 DOM 断言。

### `@testing-library/jest-dom`

增加 `toBeInTheDocument`、`toBeDisabled` 等贴近页面语义的断言，在 `src/test/setup.ts` 中统一加载。

### `@testing-library/user-event`

模拟点击、输入和键盘操作，行为比直接触发底层事件更接近真实用户。

### `msw`

在测试层拦截网络请求并返回可控响应，用同一个组件稳定覆盖成功、空数据、权限不足和服务异常。它不应在正式环境伪造业务数据。

## 浏览器端到端测试

### `@playwright/test`

启动真实 Chromium，从地址栏、点击和页面结果验证关键用户链路。配置入口是 `playwright.config.ts`，默认测试生产预览地址 4173。它运行较慢，因此主要覆盖高价值流程，不替代组件测试。

## 组件工作台

### `storybook`

把组件脱离完整业务页面单独展示，适合开发和产品审核正常、空、错误、禁用等状态。它也能生成静态的 `storybook-static/`，但这不是产品站点。

### `@storybook/tanstack-react`

提供与当前 React、Vite 和 TanStack 项目相匹配的 Storybook 框架适配。配置入口是 `.storybook/main.ts`。

### `@storybook/addon-docs`

根据 Story 和组件信息生成组件说明页，帮助审核者理解属性和示例。

### `@storybook/addon-a11y`

在 Storybook 中运行自动可访问性扫描，帮助尽早发现对比度、标签和语义问题。扫描通过不代表人工键盘和读屏验收可以省略。

## npm 命令到底调了谁

- `npm run dev` → Vite，同时加载 React、Router 和 Tailwind 插件。
- `npm run build` → TypeScript 项目构建，再由 Vite 生成 `dist/`。
- `npm run generate:api` / `generate:api:check` → 更新 OpenAPI 类型 / 校验生成物无漂移。
- `npm run format:check` / `format` → Prettier 和 Tailwind 格式插件。
- `npm run lint` / `lint:fix` → ESLint 及其 TypeScript、Hook、可访问性、导入插件。
- `npm run typecheck` → TypeScript。
- `npm run test:run` → Vitest、jsdom、Testing Library，测试需要接口时由 MSW 接管。
- `npm run test:e2e` → Playwright。
- `npm run storybook` / `storybook:build` → Storybook 框架和 addons。
- `npm run check` → OpenAPI 漂移、格式、ESLint、TypeScript、组件测试的串行总入口。

## 产品经理应该怎样审核工具链变更

依赖变更不应只写“升级某个包”。任务至少要说明：

- 它解决了哪个用户体验、交付质量或开发效率问题。
- 它是线上运行能力，还是开发、测试、构建工具。
- 是否与现有工具职责重复，为什么不能复用现有方案。
- 会新增什么可见产物或验收方式，例如一个 Storybook 状态、一条 E2E 链路或新的构建检查。
- 删除或升级失败时，影响页面功能、构建结果还是仅影响开发体验。

纯版本维护通常由技术负责人审核；如果变更会影响路由行为、页面状态、视觉组件、可访问性或用户链路，产品负责人应审核对应结果，而不是 npm 包名本身。

## 新增、升级或删除依赖前

1. 先在本文件中找到现有职责，确认没有重复工具。
2. 明确它应属于 `dependencies` 还是 `devDependencies`。
3. 记录配置入口、使用位置和预期产物。
4. 更新或新增相应测试与审核入口。
5. 运行 `npm run check`、`npm run build`；涉及 Storybook 或用户链路时再运行对应命令。
6. 同步更新本说明，避免半年后只剩一个没人敢删的包名。
