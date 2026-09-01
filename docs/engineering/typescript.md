# TypeScript 编码规范（`apps/web`）

> 本文从 [`CLAUDE.md`](../../CLAUDE.md) 拆出，按需阅读；根目录只保留每次都必须遵守的部分。

开发或评审任何 Web UI、组件、样式或主题前，必须先读 [`docs/design-system.md`](../design-system.md)
及其 [`docs/design-system/README.md`](../design-system/README.md)，理解设计意图、组件合同与评审规则；
不要在本文件复制 token 值。Web 的可执行设计系统真源位于 `apps/web/design-system/`，安装、开发、检查、
构建、Storybook 与 E2E 均只消费代码侧资产，不读取设计说明目录。删除 `docs/design-system/` 不得影响
Web；普通 CI 也不做两棵目录间的漂移比较、复制或符号链接。只有真正修改设计系统时，才在同一
WORK/TASK 中同时更新代码与设计说明，并分别验证两侧。

前端采用 **TanStack-first、按需引入**，不是无条件安装 TanStack 全家桶：

- 基础：React 19 + TypeScript strict + npm + Vite，独立构建为静态站。
- 路由：TanStack Router；分页、筛选、关键字等可分享状态放类型安全的 search params。
- 服务端状态：TanStack Query；请求、缓存、失效、Mutation 和判题结果轮询都归它。
- 业务组件：TanStack Table、TanStack Form + Zod；TanStack Virtual 只在长列表确有需要时引入。
- UI：Tailwind CSS + shadcn/ui + Lucide React；代码编辑用 Monaco Editor。
- 题面：react-markdown + remark-gfm + rehype-sanitize，默认不执行原始 HTML。
- HTTP：原生 `fetch` 的项目级薄封装，不引入 Axios；前端只调用 Gateway `/api`，不直接调内部服务、
  judge 或 sandbox。公共 client 校验 ApiSuccess / ApiProblem 和 request ID，将失败区分为
  `http | network | timeout | aborted | contract`，但不负责缓存、导航、toast 或自动重试。
- OpenAPI 只生成 `src/generated/api` 的 TypeScript 类型，禁止手改；实际网络响应仍由 Zod 在边界校验。
  响应 decoder 接受新增的未知可选字段，业务分支只能依赖已声明的必需字段、HTTP status 和稳定 code。
- 状态边界：URL 状态归 Router，服务端状态归 Query，表单归 Form，表格归 Table，
  局部交互用 `useState` / `useReducer`。初期不引入 Zustand、Redux Toolkit 或 TanStack Store。
- 测试：Vitest + React Testing Library + MSW；关键用户链路用 Playwright。
- 质量：ESLint Flat Config + typescript-eslint 管正确性，Prettier + Tailwind 插件管格式，
  TypeScript 管类型；三者不配置重复规则。稳定主版本优先，不把 alpha/beta/RC 依赖放进主链路。
- TypeScript：除 `strict` 外启用 `noUncheckedIndexedAccess`、`exactOptionalPropertyTypes`、
  `noFallthroughCasesInSwitch` 和 `verbatimModuleSyntax`；边界输入用 `unknown` + Zod，禁用无理由
  `any`、非空断言和 `enum`，纯类型使用 `import type`。
- 代码组织：文件/目录 `kebab-case`，组件/类型 `PascalCase`，变量/函数 `camelCase`；
  默认具名导出，组件不用 `React.FC`。依赖方向固定为 `app/routes → features → components/lib`。
- React：不拿 Effect 做派生状态或常规请求；请求归 Query，可恢复页面状态归 Router；
  loading / empty / error / unauthorized / success 都要有明确 UI。
- 样式与可访问性：条件 class 统一走 `cn()`，优先设计 token 和语义 HTML；交互必须支持键盘，
  verdict 不能只靠颜色表达。
- 前端已经统一提供 `format(:check)`、`lint(:fix)`、`typecheck`、`test(:run)`、
  `test:e2e`、`build`、`check` 脚本；继续保持 hook 只检查、不改写，CI 使用
  `npm ci` 后跑 check、build 和 E2E。
- 当前不使用 TanStack Start / Next.js / TanStack DB：已有独立 Spring Boot 后端，
  MVP 不需要 SSR/RSC 或客户端关系数据层。
- 展示 verdict 时注意：`OLE`、`RAN`、`SE` 都是合法状态，别只处理 AC/WA。

直接依赖、开发工具和验收入口见 [`apps/web/TOOLCHAIN.md`](../../apps/web/TOOLCHAIN.md)；更细的
分阶段引入顺序见已纳入版本管理的 [`docs/frontend.md`](../frontend.md)。`docs/` 保存跨工作项长期
有效的全局基线，本节只保留所有开发者开始工作前都必须看到的核心约定。
