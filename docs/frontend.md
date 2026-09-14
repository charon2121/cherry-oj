# Web 前端技术栈与工程方案

> `apps/web` 是独立部署的浏览器应用，只调用 `apps/server` 暴露的 REST API，
> 不直接访问 judge 或 sandbox。选型采用 **TanStack-first、按需引入**：优先使用
> TanStack 中与业务边界匹配且已经稳定的工具，不为了“全家桶”引入重复抽象。
>
> **文档状态**：已确定，作为 `apps/web` 技术栈与工程边界的唯一真源；视觉和组件合同以
> [`design-system.md`](./design-system.md) 为唯一入口。未在本文列出的库不因为出现在聊天、示例或
> 脚手架推荐中就自动进入项目。初始化依赖时使用当时最新稳定版，锁定 `package-lock.json`；不在
> 设计文档里维护容易过期的 patch 版本号。文档负责说明设计与工程边界；Web 的可执行设计系统真源在
> `apps/web/design-system/`，不以 `docs/` 作为任何日常命令的输入。

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
- **本地持久化**：IndexedDB 保存按用户、题目、语言隔离的代码草稿；主题偏好只在
  `localStorage` 的 `cherry-oj.theme` 中保存经过 manifest 校验的 theme id，长期鉴权令牌不进入
  任何 Web Storage。
- **UI 与样式**：Tailwind CSS、shadcn/ui、Base UI、Lucide React、
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

  **基础组件不自己造。** 新增基础组件前必须先查 registry：

  ```bash
  curl -s -o /dev/null -w '%{http_code}' https://ui.shadcn.com/r/styles/base-nova/<name>.json
  ```

  返回 `200` 就以官方实现为骨架，只把颜色与尺寸 class 换成本仓库语义 token，并按需追加 OJ 语义
  变体——官方的 DOM 结构、属性接口和可访问性行为不重写。返回 `404` 才允许手写，并在提交说明或
  组件清单中记下这个依据。判断标准是 registry 的响应码，不是「看起来像不像」：用了 `cva` 加语义
  token 只说明写法相似，不等于拥有官方实现的能力（例如官方 `badge` 走 Base UI 的 `useRender`，
  可渲染成任意元素；手写的 `<span>` 版本做不到）。

  已知官方没有对应实现的是 `link`、`typography`、`layout`——shadcn 的定位是交互组件，排版与布局
  原语它一向不提供。
- **Base UI**：作为当前唯一的无样式交互 primitive，承载 Dialog、Popover、Select、Tabs、Tooltip
  等复杂组件的焦点管理、键盘交互和 ARIA 行为；已有 primitive 时不从 `div` 手写一套交互，也不同时
  引入 Radix 形成双栈。
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
- `apps/web/design-system/` 持有 Web 可执行的 Foundation、主题 CSS、theme contract、manifest、
  Tailwind adapter、生成与校验工具、来源和许可证。业务组件只使用其中的 `--ds-*` semantic token 或
  Tailwind alias；禁止 raw 颜色、primitive palette、主题 selector 和 theme-id 分支。
- [`design-system.md`](./design-system.md) 与 [`design-system/`](./design-system/) 是供人阅读和评审的
  设计说明，不参与 `npm ci`、dev、check、build、Storybook 或 E2E。删除该文档目录不影响 Web；普通
  CI 不做跨树 drift、copy、prebuild 或 symlink。
- 只有真正修改主题、token、adapter 或合同的 WORK/TASK 才同时更新代码侧与设计说明，并分别验证两侧；
  不用日常构建强制两份内容逐字一致。
- 代码侧 `themes.manifest.json` 通过 `apps/web/scripts/generate-design-system.mjs` 生成只含主题 metadata 的
  `src/generated/design-system/themes.ts` 和首屏脚本 `public/generated/theme-init.js`。生成物禁止手改；
  使用 `npm run generate:design-system` 更新，使用 `npm run generate:design-system:check` 检查漂移。
- HTML 先声明默认 `cherry-black` / dark color scheme，`theme-init.js` 在 React 启动前读取
  `cherry-oj.theme`。缺失、空值、未知值或存储读取失败均回退 `cherry-black`；`pure-white` 只有显式
  有效偏好才启用，且不自动跟随操作系统。
- `src/lib/theme` 是 resolver、DOM 属性、持久化、跨标签页同步和 React 消费 API 的唯一入口。
  `data-theme` 是选择真源，`data-color-scheme` 只由 manifest metadata 派生；生产页面当前不提供主题
  切换器，setter 仅供 Storybook、测试和后续获批的产品功能使用。
- `primary` 是 Cherry 品牌实心 surface；shadcn `accent` 是中性 hover；`destructive` 和 danger 独立。
  品牌与危险不得共用同一个语义 token。
- UI 分为 design tokens、基础组件、OJ 业务组件与页面模板四层。页面从题库列表、题目工作台、
  提交详情和管理表格等稳定模板开始，不从空白画布重新设计。
- Submission 生命周期和 verdict 分开表达；verdict 映射必须穷尽 `contracts/verdict.json`，
  并同时使用 code、名称和稳定图标或形状，不能只靠颜色区分。
- **Storybook + `@storybook/addon-a11y`**：共享组件在 `cherry-black` 和 `pure-white` 中覆盖 focus、
  pressed、disabled、loading、error、320px 与长中文等状态，并作为组件测试、可访问性检查和后续视觉
  回归的入口。
- 视觉参考见 Storybook（`cd apps/web && npm run storybook`），它渲染的是真实组件；前端架构与工程
  规则以本文为准。Storybook 不反向定义 token。

> **产品边界：** 设计系统运行时与主题设置 API 是前端基建，不等于用户已经获得主题切换功能。生产
> 导航当前不提供切换入口；若要让用户主动选择主题，需在独立产品工作中定义入口、文案和验收。

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

每类状态只设一个主要所有者，避免把所有数据都塞进「全局状态库」。**具体归属表**（Router /
Query / Form / Table / IndexedDB / localStorage / useState 各管什么）见
[`coding-standards/frameworks/react.md`](./coding-standards/frameworks/react.md) §1。

这里只记选型理由：初期**不引入 Zustand、Redux Toolkit 或 TanStack Store**。TanStack 四件套已经
各自承担了一类状态，再加一个全局 store，边界会立刻模糊——「放哪都行」的状态最后一定会放错地方。
只有出现上述边界无法自然承载的跨页面纯客户端状态，并且有具体用例和测试时，才重新评估。


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
  漂移；生成物不得手改，且生成器固定精确版本。该命令继续读取
  `contracts/web-api.openapi.json`：这是保留的 monorepo API 契约依赖，不属于设计文档依赖，也不承诺
  单独复制 `apps/web` 即可完成全部检查。
- OpenAPI 类型描述编译期契约；URL search params、表单、IndexedDB、关键服务端边界等不可信
  运行时数据仍由 Zod 校验。公开响应 decoder 校验必需字段但容忍新增的未知可选字段；请求 schema
  默认拒绝未知字段。避免给每个可信内部对象重复套一层 schema。
- HTTP 状态描述请求是否成功；`AC`、`WA`、`TLE`、`MLE`、`OLE`、`CE`、`RE`、
  `PE`、`RAN`、`SE` 等 verdict 是业务数据，不能用 HTTP 错误代替。

### 4.2 Query 约定

Query key factory、失效范围、重试策略和 loader 预取规则见
[`coding-standards/frameworks/react.md`](./coding-standards/frameworks/react.md) §3。


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

- WORK-041 使用 localStorage 同步保存小型单文件草稿，键包含版本化命名空间及
  `userId + problemId + problemVersionId + languageId`。每页面 writer 使用独立恢复副本，
  revision、父 revision 和合并记录用于发现分支；不把“读后写”当成原子 CAS。
- 约 500ms 防抖保存，空字符串是有效草稿；pagehide/unmount flush 到旧身份键。UTF-8 256 KiB
  以上不截断，保留内存并提示复制/下载备份。存储拒绝、损坏、冲突有独立状态与恢复动作。
- 跨标签页更新不替换当前编辑文本，用户显式选择恢复副本。副本保守保留，浏览器配额用尽时会提示，
  不自动清理其他页面的源码。关闭页面不能保证恢复未落盘内容，因此失败时提供离开保护。
- 登出隐藏并释放旧账号编辑器，保留账号隔离的存储；同页未落盘缓冲区仅在原账号重新认证后恢复。
  草稿属于当前浏览器，不代表提交或跨设备同步，也不抵御同设备开发者工具访问。
- 用户端题目页复用 `GET /api/auth/session` 的现有 Query；15 秒及窗口恢复时重新确认会话，
  网络失败保留已有文本并暂停编辑；确认失效后隐藏旧账号内容。普通用户和管理员均可编写代码。
- Monaco 本地异步加载，C++ ACM 起始代码来自发布版本；手机触控回退 CodeMirror。后台仍使用
  `TextEditor`。本轮运行、提交为禁用能力且显示“暂未开放”，不发请求或展示模拟判题结果。

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

公开区 / 用户区 / 管理区的路由划分、search params 规则、六种页面状态和应用壳布局见
[`coding-standards/frameworks/react.md`](./coding-standards/frameworks/react.md) §4。


## 6. 代码风格与质量管理

已迁出本文，按层查阅：

- 语言层（类型约定、命名与导入、注释与错误处理、工具分层、命令与门禁）：
  [`coding-standards/languages/typescript.md`](./coding-standards/languages/typescript.md)
- 框架层（状态归属、组件与 Effect、Query 约定、路由边界、样式与可访问性、测试）：
  [`coding-standards/frameworks/react.md`](./coding-standards/frameworks/react.md)
- 跨语言通用（命名、零值陷阱、错误边界、资源、依赖方向、待办锚点）：
  [`coding-standards/project-conventions.md`](./coding-standards/project-conventions.md)

本文只保留技术选型、状态归属的**设计理由**和分阶段引入顺序；具体怎么写以上述三份为准。


## 7. 推荐目录结构

目录按业务能力组织，通用基础设施保持小而清晰：

```text
apps/web/
├── .storybook/             # Storybook 全局配置与主题装配
├── design-system/          # Web 可执行设计系统真源、生成/校验工具与许可证
├── public/generated/       # manifest 生成的首屏主题脚本；禁止手改
├── scripts/                # 主题运行时与 OpenAPI 生成/漂移检查
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
│   ├── generated/
│   │   ├── api/             # OpenAPI 生成类型；禁止手改
│   │   └── design-system/   # manifest 生成的主题 metadata；禁止手改
│   ├── lib/
│   │   ├── api/             # fetch 薄封装、ApiSuccess/ApiProblem 运行时校验
│   │   ├── storage/         # IndexedDB 草稿 repository
│   │   ├── theme/           # resolver、DOM、持久化与 React 主题 API
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
shadcn/ui / Base UI、语义 token、Storybook、ESLint、Prettier、Vitest、Testing Library、MSW。

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
- **Radix、MUI、Ant Design、HeroUI 等第二套 primitive/组件库**：shadcn/ui + Base UI + 项目 token 已承担组件
  与视觉边界，再引入完整 UI 套件会形成两套设计语言。
- **Dexie / `idb`**：MVP 的草稿存取先用项目级原生 IndexedDB 封装；复杂度出现后再评估。
- **SSE / WebSocket 判题推送**：MVP 先用 Query 轮询，性能或实时性数据证明需要后再升级。
- **Create React App**：已不适合新项目。
- **alpha/beta/RC 依赖**：除非某阶段有明确收益、替代方案和升级计划，否则不进入主链路。

这些不是永久禁令。若业务条件改变，应先记录问题、候选方案和取舍，再修改本文件与
`AGENTS.md`，避免技术栈靠口头约定漂移。

## 10. 已确定能力、尚未选择具体库

以下能力边界已经确定，但现在没有足够信息选择实现库，因此不进入初始 `package.json`：

- **前端错误监控与产品分析**：尚未决定 Sentry、PostHog 或其它服务；先保留统一日志/错误入口，
  不在业务组件散落厂商 SDK。
- **图表库**：排行榜和统计需求尚未形成，不提前选择 Recharts、ECharts 等库。
- **托管与 CDN**：前端产物是 Vite 静态文件，但由 Gateway、对象存储/CDN 或独立静态服务托管
  仍属于部署阶段决策。
- **云端视觉回归**：Storybook 与本地 story 已确定，是否接入 Chromatic 等服务等 CI 和协作需求
  出现后再决定。
