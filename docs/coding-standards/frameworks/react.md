# React 与 TanStack 编码规范（`apps/web`）

> 框架层规范。语言层约定见 [`languages/typescript.md`](../languages/typescript.md)，本项目跨语言约定见
> [`project-conventions.md`](../project-conventions.md)。
>
> **动任何 UI、组件、样式或主题之前**，必须先读 [`design-system/PROMPT.md`](../../design-system/PROMPT.md)
> 与 [`design-system.md`](../../design-system.md)。设计值只在 `apps/web/design-system/` 手写一次，
> 本文不复制任何 token 值。

## 1. 状态归属

每类状态只设一个主要所有者，不把所有数据塞进「全局状态库」：

| 状态 | 所有者 |
|---|---|
| 可分享、刷新后应保留的页面状态（页码、关键字、难度、标签） | **TanStack Router search params** |
| 来自 `apps/server` 的题目、用户、提交、判题结果 | **TanStack Query** |
| 表单值、校验错误、dirty / submitting | **TanStack Form** |
| 表格列、排序、行选择 | **TanStack Table**（需分享或恢复的筛选分页同步到 Router） |
| 代码草稿 | **IndexedDB**，经 `lib/storage` 的项目级 repository 读写 |
| 侧栏折叠等小型非敏感偏好 | `localStorage` |
| 只影响一个组件树的临时交互 | `useState` / `useReducer` |

- 组件里的编辑器值是当前会话副本，**不把整段源码塞进 Query cache 或全局 store**。
- 主题只存 key 为 `cherry-oj.theme` 的 theme id，由生成 registry 校验，首屏脚本与 `src/lib/theme`
  统一解析；无效值和存储异常安全回退默认黑色。
- **鉴权令牌和用户源码不使用 `localStorage`。**
- 能从其它状态算出来的值不单独存储，在使用处派生。
- 初期不引入 Zustand、Redux Toolkit 或 TanStack Store。只有出现上述边界无法自然承载的跨页面纯
  客户端状态，且有具体用例和测试时才重新评估。

## 2. 组件与 Effect

- 组件用普通函数，**不用 `React.FC`**；Props、返回值和泛型让 TypeScript 自然推断。
- **能在渲染时计算的值不放进 state，也不用 Effect 同步。** Effect 只用来连接 React 外部系统。
- **网络请求不写在组件 Effect 里**，统一走 Query options / Mutation hooks。
- 列表 key 必须来自稳定业务标识，**禁止用数组下标**掩盖增删和排序问题。
- 加载、空数据、错误、无权限和正常内容是五个明确的 UI 状态，**不能只实现成功路径**。

## 3. Query 与 Mutation

- 每个 feature 集中维护 query key factory 和复用的 `queryOptions`，**组件里不散落裸 key 数组**。
- Mutation 成功后**精确失效**相关 key，不全局清缓存。乐观更新只用于失败时能完整回滚的交互，
  且必须同时实现回滚和最终重新同步。
- 默认 Query 只对网络、超时和 5xx 的**幂等读请求**重试一次；aborted、contract 和 4xx 不自动重试。
- Mutation 默认不重试；写请求必须结合 method、Idempotency-Key 和 endpoint 语义单独决定。
- route loader / `beforeLoad` 可以预取或确保关键 Query，但**不在 Router 和 Query 各缓存一份**。

## 4. 路由与页面边界

- `routes/` 只负责路由装配、search params 校验和页面边界，**具体业务留在 `features/`**。
- 应用壳采用退后侧栏 + 统一 location bar + 页面级 view bar + 主工作区。普通内容页用受控最大宽度，
  题目工作台与管理表格可用全宽。
- **公开区** `/`、`/problems`、`/problems/$id`、`/submissions/$id`：公开提交详情是否可见、是否展示
  源码由服务端权限策略决定，前端默认不泄露他人源码。
- **用户区** `/workspace/$problemId`、`/submissions`、`/profile`：未登录访问由 Router 引导登录并
  保留安全回跳地址。
- **管理区** `/admin/*`：整个 route group 按角色懒加载，不进普通用户首屏 bundle。
- 题库分页、关键字、难度、标签、排序放类型安全的 search params，Zod 负责默认值与非法值归一化，
  刷新、分享、前进、后退保持一致。
- 每个路由明确实现 pending、empty、error、unauthorized、not-found、success；**页面错误不能只在
  控制台出现**。
- 页面从设计系统的稳定模板组合，本地参考 Storybook（`cd apps/web && npm run storybook`）。

## 5. 样式、组件与可访问性

- 条件 class 统一走项目 `cn()`，class 顺序交给 `prettier-plugin-tailwindcss`。
- **颜色只用 semantic token，间距和圆角只用 Foundation token**，或各自的 Tailwind alias；禁止任意
  颜色值、raw hex / OKLCH、primitive palette、主题 selector 和 theme-id 分支。
- `bg-primary` 必须配 `text-primary-foreground`，`bg-destructive` 必须配 `text-destructive-foreground`；
  普通品牌 / 危险文字分别用 `text-brand` / `text-danger`，shadcn `accent` 只承担中性 hover。
- 变体多于简单布尔条件时用 shadcn/ui 的 variant 模式，**不在调用处复制长 class 串**。
- `style` 只用于必须运行时计算的连续值，静态视觉规则放 Tailwind / CSS。
- 优先语义化 HTML：可点击元素用 `button` / `a`，**不拿 `div` 模拟**。
- 所有交互必须能用键盘完成，并有可访问名称、正确 label、焦点态和错误提示关联。
- **不通过颜色单独表达 verdict**：颜色之外还要有 code、名称和图标或形状，并检查所有 manifest
  主题的允许 surface 对比度。
- 展示 verdict 时注意 `OLE`、`RAN`、`SE` 都是合法状态，别只处理 AC / WA。

## 6. 测试

- 单元 / 组件测试与被测文件同目录，命名 `*.test.ts(x)`；Playwright 用例只放 `e2e/`。
- **测用户可见行为，不测组件内部 state 和实现细节**；优先 `getByRole`、label 和可见文本。
- 用户操作用 `user-event`，HTTP 用 MSW；**不直接 mock TanStack Query 或 `fetch` 的实现细节**。
- 每个异步测试等待最终可见状态，**禁止靠固定 sleep 碰运气**。
- 默认不用大面积 snapshot；只有输出结构稳定且人工审查确有价值时用小快照。
- 测试数据用具名 builder / factory，避免每个用例复制巨大对象或依赖执行顺序。
