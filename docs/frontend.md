# Web 前端技术栈与工程方案

> `apps/web` 是独立部署的浏览器应用，只调用 `apps/server` 暴露的 REST API，
> 不直接访问 judge 或 sandbox。选型采用 **TanStack-first、按需引入**：优先使用
> TanStack 中与业务边界匹配且已经稳定的工具，不为了“全家桶”引入重复抽象。
>
> **文档状态**：已确定，作为 `apps/web` 技术栈与工程边界的唯一真源；视觉和组件合同以
> [`design-system.md`](./design-system.md) 为唯一入口。未在本文列出的库不因为出现在聊天、示例或
> 脚手架推荐中就自动进入项目。初始化依赖时使用当时最新稳定版，锁定 `package-lock.json`；不在
> 设计文档里维护容易过期的 patch 版本号。

## 1. 目标与边界

- 第一目标是跑通「浏览题目 → 登录 → 编写代码 → 提交 → 查看判题结果」端到端 MVP。
- 前端只负责交互和展示；用户、题目、提交、鉴权的真源都在 `apps/server`。
- `apps/server` 与前端之间使用 REST/JSON；前端不理解 judge / sandbox 的内部协议。
- 默认采用客户端渲染的 SPA。当前没有 SEO、SSR、React Server Components 或前端
  Server Functions 需求。
- 所有工具都按业务需要引入；文档中标为“按需”的包不进入初始脚手架。

## 2. 已确定的技术栈

一页清单：

- **语言与运行时**：React 19、TypeScript strict、浏览器原生 Web API。
- **构建与包管理**：Vite、npm、`package-lock.json`。
- **TanStack 核心**：Router、Query、Table、Form；Virtual 仅在性能数据证明需要时引入。
- **数据契约**：Gateway OpenAPI 生成 TypeScript 类型，Zod 校验不可信运行时输入。
- **网络边界**：原生 `fetch` 薄封装、RFC 9457 Problem Details、`AbortSignal`、Request ID、
  Cookie/CSRF；不使用 Axios。
- **本地持久化**：IndexedDB 保存按用户、题目、语言隔离的代码草稿；主题等轻量偏好迁移后才可使用
  `localStorage`，长期鉴权令牌不进入任何 Web Storage。
- **UI 与样式**：Tailwind CSS、shadcn/ui、Radix Primitives、Lucide React、
  class-variance-authority、`clsx`、`tailwind-merge`。
- **编辑与内容**：Monaco Editor、react-markdown、remark-gfm、rehype-sanitize。
- **组件工作台**：Storybook 与 `@storybook/addon-a11y`；UI 视觉方向为
  Linear-inspired Focused Workspace。
- **测试**：Vitest、React Testing Library、user-event、MSW、Playwright。
- **代码质量**：EditorConfig、ESLint Flat Config、typescript-eslint、React Hooks、jsx-a11y、
  simple-import-sort、Prettier、prettier-plugin-tailwindcss。
- **明确不进入首期**：TanStack Start、TanStack DB、TanStack Store、Next.js、Axios、
  Zustand、Redux Toolkit、Create React App、alpha/beta/RC 主链路依赖。

### 2.1 基础运行与构建

- **React 19**：组件与界面运行时。
- **TypeScript（strict）**：业务代码、组件、路由、接口模型全部使用 TypeScript。
- **npm**：包管理器；`apps/web/package-lock.json` 是依赖锁文件。
- **Vite**：开发服务器、构建和静态资源处理。

不使用 Create React App。`apps/web` 独立构建，生产产物是静态文件，由 CDN、静态
Web 服务或网关托管。

### 2.2 TanStack 核心

- **TanStack Router**：应用路由、嵌套路由、路由级代码分割、加载状态和类型安全的
  path/search 参数。
- **TanStack Query**：服务端状态的请求、缓存、失效、重试、Mutation 和乐观更新。
- **TanStack Table**：题库、提交记录、排行榜和管理后台表格的排序、筛选、分页与选择状态。
- **TanStack Form + Zod**：登录、注册、个人资料和题目编辑等表单；Form 管理交互状态，
  Zod 定义运行时校验规则。使用稳定主版本，不跟进 alpha/beta 版本。
- **TanStack Virtual（按需）**：只有真实数据量证明普通渲染不够时，才用于超长列表或
  表格虚拟化。

TanStack 工具采用 headless 模式，只提供行为、状态和类型，不负责视觉样式。

### 2.3 UI 与样式

- **Tailwind CSS**：布局、设计 token 和组件样式。
- **shadcn/ui**：可复制、可维护的无头组件实现，作为项目组件库的起点；生成后的代码
  归项目维护，不把它当成不可修改的黑盒依赖。
- **Radix Primitives**：承载 Dialog、Popover、Select、Tabs、Tooltip 等复杂组件的焦点管理、
  键盘交互和 ARIA 行为；已有 primitive 时不从 `div` 手写一套交互。
- **Lucide React**：统一图标集。
- **class-variance-authority**：定义 Button、Badge 等基础组件的有限 variant 和 size。
- **`clsx` + `tailwind-merge`**：只通过项目级 `cn()` 合并条件 class 和处理 Tailwind 冲突。
- **Monaco Editor**：代码编辑器，负责语言模式、行号、快捷键和编辑体验。
- **react-markdown + remark-gfm + rehype-sanitize**：安全渲染题面 Markdown 和 GFM。
  Markdown 中的原始 HTML 默认不执行。

### 2.4 设计系统

- Web 视觉唯一规范是 [`design-system.md`](./design-system.md) 与其
  [`design-system/`](./design-system/) 文档包。采用 **Linear-inspired Focused Workspace**：冷静灰阶、
  紧凑但不拥挤的信息密度和克制的层级；借鉴 Linear fixture 的结构，不复制其品牌资产。
- `cherry-black` 是固定默认主题；`pure-white` 是完整浅色主题。缺失、空值和未知 theme id 回退默认
  黑色，不自动跟随操作系统。未来主题必须完整实现 theme contract，组件不得增加 theme-id 分支。
- 应用框架固定为退后的导航侧栏、统一的 location bar、按页面变化的 view bar 和获得主要对比度的
  工作区。侧栏必须比正文区域更安静，导航不能与用户当前任务争夺注意力。
- 列表行、工具栏和表单采用紧凑尺寸；层级优先使用明暗、细边框和对齐表达，普通内容不依赖阴影，
  也不把每个区块都包装成 Card。
- Foundation 与主题 CSS 是数值真源，theme contract 规定语义和允许组合。业务组件只使用 `--ds-*`
  semantic token 或 Tailwind adapter alias；禁止 raw 颜色、primitive palette、主题 selector 和
  theme-id 分支。
- `primary` 是 Cherry 品牌实心 surface；shadcn `accent` 是中性 hover；`destructive` 和 danger 独立。
  品牌与危险不得共用同一个语义 token。
- UI 分为 design tokens、基础组件、OJ 业务组件与页面模板四层。页面从题库列表、题目工作台、
  提交详情和管理表格等稳定模板开始，不从空白画布重新设计。
- Submission 生命周期和 verdict 分开表达；verdict 映射必须穷尽 `contracts/verdict.json`，
  并同时使用 code、名称和稳定图标或形状，不能只靠颜色区分。
- **Storybook + `@storybook/addon-a11y`**：共享组件在 `cherry-black` 和 `pure-white` 中覆盖 focus、
  pressed、disabled、loading、error、320px 与长中文等状态，并作为组件测试、可访问性检查和后续视觉
  回归的入口。
- 视觉参考见 [`design-system/components.html`](./design-system/components.html)；前端架构与工程规则以
  本文为准。HTML 和 Storybook 都不反向定义 token。

> **运行时待迁移：** 当前 `apps/web/src/styles/globals.css` 仍使用旧的浅色 `:root` / `.dark` 合同，
> 尚未实现上述默认主题、theme resolver、偏好持久化或首屏防闪。迁移必须另建 TASK；设计文档发布不
> 等于主题已经上线。

### 2.5 测试与工程质量

- **ESLint Flat Config + typescript-eslint**：静态检查和类型感知规则。
- **Prettier + prettier-plugin-tailwindcss**：统一格式和 Tailwind class 顺序；不与 ESLint
  重复承担格式规则。
- **Vitest**：工具函数、hooks 和组件单元测试。
- **React Testing Library + user-event**：从用户行为出发测试组件。
- **MSW**：在组件测试和开发场景中模拟 `apps/server` 的 HTTP API。
- **Playwright**：覆盖登录、选题、提交代码、轮询判题结果等关键端到端路径。
- **Storybook**：隔离开发共享组件和业务展示组件；story 与组件就近放置，避免另建一套
  与真实组件脱节的演示代码。

## 3. 状态归属

每类状态只设一个主要所有者，避免把所有数据都塞进“全局状态库”：

- 可分享、刷新后应保留的页面状态，例如页码、关键字、难度和标签，归
  **TanStack Router search params**。
- 来自 `apps/server` 的题目、用户、提交和判题结果，归 **TanStack Query**。
- 表单值、校验错误、dirty/submitting 状态，归 **TanStack Form**。
- 表格的列、排序和行选择，默认归 **TanStack Table**；需要分享或恢复的筛选、分页
  同步到 Router。
- 代码草稿归 **IndexedDB**，由 `lib/storage` 的项目级 repository 读写；组件中的编辑器值是
  当前会话副本，不把整段源码塞进 Query cache 或全局 store。
- 侧栏折叠等小型非敏感偏好可以归 `localStorage`。主题偏好只能在后续 Web 迁移任务中按 theme
  manifest 设计 resolver、未知值回退和首屏防闪后持久化；鉴权令牌和用户源码不使用 `localStorage`。
- 只影响一个组件树的临时交互状态，使用 React `useState` / `useReducer`。
- 能从其它状态计算出的值不单独存储，在使用处派生。

初期不引入 Zustand、Redux Toolkit 或 TanStack Store。只有出现无法由上述边界自然
承载的跨页面纯客户端状态，并且有具体用例和测试时，才重新评估。

## 4. 数据、鉴权与异步流程

### 4.1 Gateway 与 HTTP 边界

- 浏览器只访问 Gateway BFF 暴露的 `/api`，不知道 identity、problem、submission、judging
  等微服务地址，也不直接调用 judge / sandbox。
- 生产环境优先让静态站与 `/api` 同源；开发环境由 Vite proxy 转发。跨源开发请求统一使用
  `credentials: 'include'`，不在业务调用点重复配置。
- 使用原生 `fetch` 的项目级薄封装，不引入 Axios。薄封装只处理 base URL、JSON、Cookie、
  CSRF、`AbortSignal`、Request ID、RFC 9457 Problem Details 与统一应用错误；缓存、重试、
  去重和失效归 TanStack Query。
- 请求 body 不套通用 envelope。普通 JSON 成功响应统一解析为 `{ data, meta }`，失败只接受
  `application/problem+json`；`X-Request-Id` 与 body 的 `meta.requestId` 不一致属于契约错误。
- `ApiError` 区分 `http | network | timeout | aborted | contract`；HTTP error 保留 status、稳定 code、
  request ID、Problem 和按 ns 表示的 `Retry-After`。公共 client 不自动导航、toast 或重试。
- Gateway OpenAPI 由 `@hey-api/openapi-ts` 仅生成 `src/generated/api` 的 TypeScript 类型，配置入口为
  `openapi-ts.config.mjs`。`npm run generate:api` 更新生成物，`generate:api:check` 重建并逐文件检查
  漂移；生成物不得手改，且生成器固定精确版本。
- OpenAPI 类型描述编译期契约；URL search params、表单、IndexedDB、关键服务端边界等不可信
  运行时数据仍由 Zod 校验。公开响应 decoder 校验必需字段但容忍新增的未知可选字段；请求 schema
  默认拒绝未知字段。避免给每个可信内部对象重复套一层 schema。
- HTTP 状态描述请求是否成功；`AC`、`WA`、`TLE`、`MLE`、`OLE`、`CE`、`RE`、
  `PE`、`RAN`、`SE` 等 verdict 是业务数据，不能用 HTTP 错误代替。

### 4.2 Query 约定

- Query key 由各 feature 的 factory 集中定义并保持结构稳定；组件中不散落裸数组。
- Mutation 成功后精确失效相关 key，不全局清缓存。乐观更新只用于失败时能完整回滚的交互。
- 默认 Query 只对网络、超时和 5xx 的幂等读请求重试一次；aborted、contract 与 4xx 不自动重试。
  Mutation 默认不重试，写请求必须结合 method、Idempotency-Key 和 endpoint 语义单独决定。
- route loader / `beforeLoad` 可以预取或确保关键 Query，但不在 Router 和 Query 各缓存一份数据。

### 4.3 登录态与权限

- Gateway BFF 负责 OIDC 流程和令牌保管；浏览器只持有 Secure、HttpOnly、SameSite Session
  Cookie，不接触上游 access token / refresh token，也不把长期令牌存进 Web Storage。
- 应用启动通过 `GET /api/session` 恢复登录态，由 TanStack Query 缓存。TanStack Router
  `beforeLoad` 使用该 Query 改善导航体验，真正鉴权和授权仍由 Gateway / 后端执行。
- 有状态写请求携带 CSRF token。`401` 表示会话失效，应清理 session Query 并引导重新登录；
  `403` 表示已经登录但无权限，两者不能共用同一个错误页。
- 公开题库与题面允许匿名访问；用户工作台和个人提交需要登录；管理路由按角色懒加载，但前端
  隐藏入口不构成安全边界。

### 4.4 代码草稿

- 使用浏览器 IndexedDB 保存草稿，key 至少包含 `userId + problemId + languageId`，避免切换语言、
  题目或账号时互相覆盖。
- Monaco 的内容做防抖持久化，并显示“保存中 / 已保存 / 保存失败”；刷新和崩溃恢复不依赖后端。
- 退出登录时清理当前用户的私有草稿，或保证用户命名空间完全隔离；共享设备上不能让下一个账号
  读到上一个账号的源码。
- 初期直接封装 IndexedDB 原生 API；只有事务、迁移或查询复杂度真实上升时才评估 `idb` / Dexie，
  不把未确定的库提前写进依赖。

### 4.5 提交与判题轮询

- 每次用户主动点击“提交”时用 `crypto.randomUUID()` 生成 `Idempotency-Key`；同一次动作的网络
  重试复用该 key，新一次主动提交才生成新 key。它是请求幂等键，不要求使用业务 UUIDv7。
- 创建成功后以服务端返回的 Submission ID 跳转到详情页；页面刷新后从 ID 重新获取状态，不能
  依赖内存中的 Mutation 结果。
- TanStack Query 根据生命周期动态轮询：`Pending` 默认 1 秒，`Judging` 默认 2 秒；连续异常时
  退避，页面隐藏时降低频率，恢复可见时立即刷新，进入 `Done` 后停止。
- `Done` 后再展示 verdict。生命周期与判题结论是两套状态机，不能把 `Judging` 填进 verdict，
  也不能把 `CE` / `SE` 当成 HTTP 失败。
- MVP 使用轮询；当并发量或实时性证明确有需要时，可以把进度通道升级为 SSE，但 Query cache
  仍作为页面读取的服务端状态入口。

## 5. 路由与页面边界

- 应用壳采用 Linear-inspired 的退后侧栏、统一 location bar、页面级 view bar 和主工作区。
  普通内容页使用受控最大宽度；题目工作台与管理表格可使用全宽布局。
- **公开区**：`/`、`/problems`、`/problems/$id`、`/submissions/$id`。公开提交详情是否可见、
  是否展示源码由服务端权限策略决定，前端默认不泄露他人源码。
- **用户区**：`/workspace/$problemId`、`/submissions`、`/profile`。未登录访问由 Router
  引导登录并保留安全的回跳地址。
- **管理区**：`/admin/problems`、`/admin/users`、`/admin/submissions`。整个 route group 按角色
  懒加载，不进入普通用户首屏 bundle。
- 题库分页、关键字、难度、标签和排序放在类型安全的 search params；Zod 负责默认值与非法值
  归一化，刷新、分享、前进和后退保持一致。
- 每个路由明确实现 pending、empty、error、unauthorized、not-found 和 success 边界；页面错误
  不能只在控制台出现。
- 页面从设计系统的稳定模板组合：题库列表、题目工作台、提交详情、个人列表、管理表格与表单。
  本地可视化参考见
  [`design-system/components.html`](./design-system/components.html)。

## 6. 代码风格与质量管理

### 6.1 工具职责

每个工具只负责一层问题，避免规则重叠：

- `.editorconfig`：UTF-8、LF、文件末尾换行、两空格缩进和行尾空白。
- `prettier.config.mjs`：纯格式化，不判断代码正确性。
- `eslint.config.js`：错误模式、React Hooks、可访问性、导入和类型安全。
- `tsconfig*.json`：编译期类型边界。
- Vitest / Playwright：运行时行为与用户链路。

ESLint 使用 Flat Config，启用 `@eslint/js`、typescript-eslint 的类型感知推荐规则、
`eslint-plugin-react-hooks`、`eslint-plugin-jsx-a11y`、`eslint-plugin-simple-import-sort` 和
`eslint-config-prettier`。不启用与 Prettier 冲突的排版规则。

### 6.2 格式化基线

Prettier 采用以下项目级约定：

```js
export default {
  semi: true,
  singleQuote: true,
  trailingComma: 'all',
  printWidth: 100,
  tabWidth: 2,
  endOfLine: 'lf',
  plugins: ['prettier-plugin-tailwindcss'],
};
```

- JavaScript/TypeScript 字符串使用单引号，JSX attribute 保持双引号。
- 禁止用空格手工对齐赋值或参数；格式交给 Prettier。
- 开发者可以启用编辑器 format-on-save，但 Git hook 和 CI 只检查、不改写文件。
- shadcn/ui 生成的代码进入仓库后同样执行项目格式化和 Lint，不设永久豁免区。
- `routeTree.gen.ts` 等真正由工具持续生成的文件不得手改；只有生成器拥有其格式时才加入
  ESLint/Prettier ignore。若生成物入库，CI 必须重新生成并检查工作树没有漂移。

### 6.3 TypeScript 约定

`tsconfig` 除 `strict` 外启用 `noUncheckedIndexedAccess`、`exactOptionalPropertyTypes`、
`noFallthroughCasesInSwitch`、`noImplicitOverride`、`noUnusedLocals`、
`noUnusedParameters` 和 `verbatimModuleSyntax`。

- 禁止无理由的 `any`、非空断言和双重类型断言；不可信输入先保持 `unknown`，校验后再用。
- 类型导入使用 `import type`，避免把纯类型误打进运行时代码。
- 默认使用 `type`；确实需要声明合并或第三方扩展点时才使用 `interface`。
- 不使用 TypeScript `enum`，使用 `as const` 对象或字符串联合；分支用可辨识联合表达。
- `switch` 处理 verdict 等封闭联合时必须穷尽，新增状态不能静默落入含糊的 `default`。
- API 边界优先从 Zod schema 推导类型，避免手写一份运行时 schema、再复制一份 TS 类型。
- 时间、内存等单位保留服务端字段名，例如 `cpuNs`、`memoryBytes`，不在前端私自换单位命名；
  展示层可以格式化，但原始模型不变。

### 6.4 命名、文件与导入

- 目录和源码文件统一 `kebab-case`：`problem-list.tsx`、`use-submission.ts`。
- React 组件、类型使用 `PascalCase`；变量、函数使用 `camelCase`。
- Hook 以 `use` 开头；布尔值用 `is` / `has` / `can` / `should` 表意。
- 回调 prop 使用 `onSubmit`，组件内部处理函数使用 `handleSubmit`。
- Props 命名为 `<Component>Props`，不加 `I`、`T` 等匈牙利前缀。
- 默认使用具名导出，便于重构和全局搜索；只有工具配置或第三方约定要求时使用默认导出。
- 使用 `@/` 指向 `src/`，避免跨层的 `../../../`；同目录短相对导入仍可使用。
- 导入顺序由 ESLint 自动判断并由 `lint:fix` 修复：副作用 → 第三方 → `@/` → 相对路径。
- 不建立把整个目录全部再导出一遍的 barrel 文件；feature 只有在需要稳定公共入口时才显式导出。

依赖方向是 `app/routes → features → components/lib`。一个 feature 不直接导入另一个 feature
的内部文件；跨 feature 协作通过路由、共享 API 模型或显式公共入口完成。初始化前端时用
`no-restricted-imports` 把这条边界写进 ESLint，而不是只靠评审记忆。

### 6.5 React 与 TanStack 写法

- 组件使用普通函数，不使用 `React.FC`；Props、返回值和泛型让 TypeScript 自然推断。
- 能在渲染时计算的值不放进 state，也不用 Effect 同步；Effect 只连接 React 外部系统。
- 网络请求不写在组件 Effect 中，统一通过 Query options / Mutation hooks。
- `routes/` 负责路由装配、search params 校验和页面边界，具体业务留在 `features/`。
- 每个 feature 集中维护 query key factory 和复用的 `queryOptions`；组件里不散落裸 key 数组。
- Mutation 只精确失效相关 Query；乐观更新必须同时实现失败回滚和最终重新同步。
- Router search params 是分页、筛选、排序等可恢复页面状态的真源，不再复制到全局 store。
- Table 默认自行管理纯视图状态；只有需要 URL、服务端或父组件拥有时才提升对应字段。
- 列表 key 必须来自稳定业务标识，禁止用数组下标掩盖增删和排序问题。
- 加载、空数据、错误、无权限和正常内容是明确的 UI 状态，不能只实现成功路径。

### 6.6 Tailwind、组件与可访问性

- 条件 class 统一通过项目 `cn()`；class 顺序交给 `prettier-plugin-tailwindcss`。
- 颜色只使用 semantic token，间距与圆角只使用 Foundation token，或使用各自的 Tailwind alias；
  禁止任意颜色值、raw hex/OKLCH、primitive palette、主题 selector 和 theme-id 分支。
- `bg-primary` 必须与 `text-primary-foreground` 配对，`bg-destructive` 必须与
  `text-destructive-foreground` 配对；普通品牌/危险文字分别使用 `text-brand` / `text-danger`，
  shadcn `accent` 只承担中性 hover。
- 组件变体多于简单布尔条件时使用 shadcn/ui 的 variant 模式，不在调用处复制长 class 串。
- `style` 只用于必须由运行时计算的连续值；静态视觉规则放 Tailwind/CSS。
- 优先语义化 HTML；可点击元素使用 `button` / `a`，不拿 `div` 模拟。
- 所有交互必须可通过键盘完成，并具有可访问名称、正确 label、焦点态和错误提示关联。
- 不通过颜色单独表达 verdict；颜色之外还要有 code、名称和图标或形状，并检查所有 manifest 主题的
  允许 surface 对比度。

### 6.7 注释与错误处理

- 注释解释“为什么这样设计、边界在哪里”，不复述代码字面行为。
- 公共 hook、复杂 query options 和反直觉的浏览器兼容处理写短注释；显而易见的组件不写模板式注释。
- 待办使用 `TODO(TASK-001): 原因/退出条件`，并关联所属 `development/works/WORK-xxx/` 中的
  `60-task-TASK-xxx.md`；
  不留没有工作项上下文的 `TODO`。
- 捕获异常时先按 `unknown` 处理，统一转换成应用错误类型；用户提示与诊断信息分开。
- 生产代码不散落 `console.log`；可观测性通过统一入口，测试中的预期错误需显式断言或抑制。

### 6.8 测试代码风格

- 单元/组件测试与被测文件同目录，命名 `*.test.ts(x)`；Playwright 用例只放 `e2e/`。
- 测用户可见行为，不测组件内部 state 和实现细节；优先 `getByRole`、label 和可见文本。
- 用户操作使用 `user-event`，HTTP 使用 MSW；不直接 mock TanStack Query 或 `fetch` 实现细节。
- 每个异步测试等待最终可见状态，禁止靠固定 sleep 碰运气。
- 默认不使用大面积 snapshot；只有输出结构稳定且人工审查确有价值时使用小快照。
- 测试数据使用具名 builder/factory，避免每个用例复制巨大对象或依赖执行顺序。

### 6.9 npm 命令与质量门禁

初始化 `apps/web` 时统一提供以下脚本，README、hook 和 CI 都只调用这些脚本，不各写一套命令：

```jsonc
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "generate:api": "openapi-ts",
    "generate:api:check": "node scripts/check-generated-api.mjs",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "lint": "eslint .",
    "lint:fix": "eslint . --fix",
    "typecheck": "tsc -b --pretty false",
    "test": "vitest",
    "test:run": "vitest run",
    "test:e2e": "playwright test",
    "storybook": "storybook dev -p 6006",
    "storybook:build": "storybook build",
    "check": "npm run generate:api:check && npm run format:check && npm run lint && npm run typecheck && npm run test:run"
  }
}
```

- 开发中主动运行 `format` / `lint:fix`；提交前运行 `npm run check`。
- pre-commit 只检查暂存前端文件的 Prettier 和 ESLint 结果，不自动修复或重新 `git add`。
- pre-push 在 `apps/web` 有改动时运行 `npm run check` 和 `npm run build`。
- OpenAPI 契约变化后运行 `generate:api` 并提交生成物；`generate:api:check` 与 `check` 会拒绝漂移。
- CI 使用 `npm ci`，运行 `check`、生产构建、Storybook 静态构建和 Playwright；任何 warning
  不作为长期可忽略状态。
- 门禁分层：Prettier 管格式，ESLint 管代码风险，TypeScript 管类型，Vitest 管组件行为，
  Playwright 管端到端链路。某层通过不能替代下一层。

## 7. 推荐目录结构

目录按业务能力组织，通用基础设施保持小而清晰：

```text
apps/web/
├── .storybook/             # Storybook 全局配置与主题装配
├── src/
│   ├── app/                 # Router、QueryClient、全局 Provider、入口
│   ├── routes/              # TanStack Router 文件路由
│   ├── features/
│   │   ├── auth/
│   │   ├── problems/
│   │   ├── submissions/
│   │   ├── profile/
│   │   └── admin/
│   ├── components/
│   │   └── ui/              # shadcn/ui 组件；story 与 test 就近放置
│   ├── generated/api/       # OpenAPI 生成类型；禁止手改
│   ├── lib/
│   │   ├── api/             # fetch 薄封装、ApiSuccess/ApiProblem 运行时校验
│   │   ├── storage/         # IndexedDB 草稿 repository
│   │   └── validation/      # 跨 feature 共用的 Zod schema
│   ├── test/                # Vitest / MSW 公共测试配置
│   └── styles/
├── e2e/                     # Playwright
├── package.json
├── package-lock.json
└── vite.config.ts
```

业务私有组件、schema、query options 留在各自 `features/<name>` 内；只有被多个 feature
真正复用后才提升到 `components` 或 `lib`，不预建“大而全”的公共层。

## 8. 引入顺序

### 阶段 1：应用骨架

React、TypeScript、Vite、TanStack Router、TanStack Query、Tailwind CSS、基础
shadcn/ui / Radix、语义 token、Storybook、ESLint、Prettier、Vitest、Testing Library、MSW。

交付登录、题库、题目详情、代码编辑和提交结果闭环。

### 阶段 2：业务组件

按页面落地 TanStack Form、Zod、TanStack Table、Monaco Editor、Markdown 渲染、IndexedDB
草稿、Gateway OpenAPI 类型生成和 Playwright 关键路径。

### 阶段 3：有证据再优化

在性能测量证明必要后引入 TanStack Virtual；在出现明确业务需求后再评估 SSE、图表、
快捷键、Pacer、IndexedDB 辅助库或额外客户端状态工具。

## 9. 当前明确不采用

- **TanStack Start**：项目已有独立 Spring Boot 业务后端；增加前端服务端会模糊边界。
- **Next.js**：当前不需要 SSR/SSG/RSC，Vite SPA 更直接。
- **TanStack DB**：MVP 的 REST 数据规模和实时需求由 Query 足以覆盖。
- **TanStack Store / Zustand / Redux Toolkit**：当前没有独立的全局客户端状态问题。
- **Axios**：原生 `fetch` 已满足需求。
- **MUI / Ant Design / HeroUI 等第二套组件库**：shadcn/ui + Radix + 项目 token 已承担组件
  与视觉边界，再引入完整 UI 套件会形成两套设计语言。
- **Dexie / `idb`**：MVP 的草稿存取先用项目级原生 IndexedDB 封装；复杂度出现后再评估。
- **SSE / WebSocket 判题推送**：MVP 先用 Query 轮询，性能或实时性数据证明需要后再升级。
- **Create React App**：已不适合新项目。
- **alpha/beta/RC 依赖**：除非某阶段有明确收益、替代方案和升级计划，否则不进入主链路。

这些不是永久禁令。若业务条件改变，应先记录问题、候选方案和取舍，再修改本文件与
`CLAUDE.md`，避免技术栈靠口头约定漂移。

## 10. 已确定能力、尚未选择具体库

以下能力边界已经确定，但现在没有足够信息选择实现库，因此不进入初始 `package.json`：

- **前端错误监控与产品分析**：尚未决定 Sentry、PostHog 或其它服务；先保留统一日志/错误入口，
  不在业务组件散落厂商 SDK。
- **图表库**：排行榜和统计需求尚未形成，不提前选择 Recharts、ECharts 等库。
- **托管与 CDN**：前端产物是 Vite 静态文件，但由 Gateway、对象存储/CDN 或独立静态服务托管
  仍属于部署阶段决策。
- **云端视觉回归**：Storybook 与本地 story 已确定，是否接入 Chromatic 等服务等 CI 和协作需求
  出现后再决定。
