# Cherry OJ Web

这里是 Cherry OJ 的浏览器端。当前已经完成 React、路由、服务端状态、样式、组件预览和测试工具的基础骨架，业务页面仍在逐步接入。

如果你只想知道“这些 npm 包为什么存在”，直接看 [`TOOLCHAIN.md`](./TOOLCHAIN.md)。本页负责回答另一件事：开发或验收一个页面时，应该怎样把项目跑起来。

## 先把页面跑起来

需要 Node.js 24 和 npm 11。项目在 `package.json` 的 `engines` 中固定了这个范围，版本不符时应先切换运行环境，不要通过忽略警告继续安装。

```bash
cd apps/web
npm ci
npm run dev
```

浏览器打开 `http://127.0.0.1:5173`。Vite 会在文件保存后刷新页面。

前端访问 `/api` 时，开发服务器会把请求转发到 `http://127.0.0.1:8080`，也就是 Java Gateway。只查看静态页面不需要启动后端；需要真实接口时，再启动 Gateway 和对应业务服务。

## 开发一个页面时会经过什么

以新增题目列表页为例，日常工作通常按下面的顺序推进：

1. 在 `src/routes/` 新建路由文件，定义页面地址、URL 参数和页面入口。
2. 把该页面独有的查询、交互和展示放进 `src/features/`；可跨业务复用的基础组件放进 `src/components/`，通用函数放进 `src/lib/`。
3. 用 TanStack Query 请求 `/api`，明确 loading、empty、error、unauthorized 和 success 状态。
4. 用 Tailwind CSS 和项目语义 token 完成样式；交互组件优先复用现有 UI 组件。
5. 为组件或交互补 Vitest + Testing Library 测试，需要隔离接口时用 MSW。
6. 需要让产品经理单独审核组件状态时补 Storybook；关键用户链路补 Playwright。
7. 提交前运行 `npm run check` 和 `npm run build`。

运行 `dev` 或 `build` 时，TanStack Router 插件会根据 `src/routes/` 自动更新 `src/routeTree.gen.ts`。这个文件是生成物，不要手工编辑。

## 常用命令

### 本地开发与预览

```bash
npm run dev
npm run build
npm run preview
```

- `dev`：启动开发服务器，默认端口 5173。
- `build`：先做 TypeScript 构建检查，再生成可部署的 `dist/`。
- `preview`：在本地预览 `dist/`，用于确认生产构建结果，不替代正式部署。

### 提交前质量检查

```bash
npm run check
```

它会按顺序检查格式、ESLint、TypeScript 和组件测试，但不会改写文件。需要主动修复时分别使用：

```bash
npm run format
npm run lint:fix
```

### 组件审核与端到端验收

```bash
npm run storybook
npm run storybook:build
npm run test:e2e
```

- Storybook 默认运行在 `http://127.0.0.1:6006`，适合脱离完整页面审核按钮、表单及其各种状态。
- Playwright 会自动启动生产预览服务器并使用 Chromium 验证关键用户链路。

## 目录怎么分工

- `src/app/`：应用级装配，例如 Router 和 Query Client。
- `src/routes/`：页面入口、布局和 URL 状态。
- `src/features/`：按业务能力组织的查询、交互和组件。
- `src/components/`：不依赖具体业务的共享组件。
- `src/lib/`：通用工具和基础封装。
- `src/styles/`：全局样式、颜色与尺寸 token。
- `src/test/`：组件测试的统一环境和接口 Mock。
- `e2e/`：从用户视角执行的浏览器验收用例。
- `.storybook/`：组件工作台配置。

依赖方向固定为 `app/routes → features → components/lib`。ESLint 会阻止底层目录反向引用页面或业务功能。

## 产品审核时看什么

产品审核不需要逐个理解构建插件。一个页面是否交付，优先看这些结果：

- `npm run dev`：联调中的完整页面和真实接口行为。
- `npm run storybook`：组件在正常、空、错误、禁用等状态下的表现。
- `npm run test:e2e`：关键用户链路是否被自动化验收覆盖。
- `npm run build`：最终是否能生成可部署的静态站点。

工具存在的理由、运行阶段以及删除后的影响见 [`TOOLCHAIN.md`](./TOOLCHAIN.md)。全仓编码与架构边界以 [`CLAUDE.md`](../../CLAUDE.md) 为准。

## 常见问题

### `npm ci` 提示 Node 版本不兼容

先运行 `node --version` 和 `npm --version`，确认 Node 为 24.x、npm 不低于 11。不要用 `--force` 绕过版本约束，否则本地结果可能与 CI 不一致。

### 页面能打开，但 `/api` 请求失败

确认 Gateway 已在 8080 端口启动。Vite 只负责转发请求，不会替你启动后端，也不会伪造真实业务数据。

### 新路由没有出现

确认文件位于 `src/routes/` 且符合 TanStack Router 的文件路由约定，然后重启 `npm run dev`。仍有问题时检查终端里的路由生成错误，不要直接改 `routeTree.gen.ts`。

### `test:e2e` 找不到浏览器

首次使用 Playwright 的机器可能需要安装 Chromium：

```bash
npx playwright install chromium
```

这一步只准备测试浏览器，不会改变应用的生产依赖。
