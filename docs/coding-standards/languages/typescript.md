# TypeScript 编码规范（`apps/web`）

> 语言层规范。React / TanStack / 样式与可访问性见 [`frameworks/react.md`](../frameworks/react.md)，
> 本项目跨语言约定见 [`project-conventions.md`](../project-conventions.md)，技术选型与引入顺序见
> [`frontend.md`](../../frontend.md)，依赖与验收入口见 [`apps/web/TOOLCHAIN.md`](../../../apps/web/TOOLCHAIN.md)。

## 1. 类型约定

`tsconfig` 除 `strict` 外启用 `noUncheckedIndexedAccess`、`exactOptionalPropertyTypes`、
`noFallthroughCasesInSwitch`、`noImplicitOverride`、`noUnusedLocals`、`noUnusedParameters`
和 `verbatimModuleSyntax`。

- **禁止无理由的 `any`、非空断言和双重类型断言。** 不可信输入先保持 `unknown`，校验后再用。
- 纯类型导入用 `import type`，避免把类型误打进运行时代码。
- **默认用 `type`**；确实需要声明合并或第三方扩展点时才用 `interface`。
- **不使用 `enum`**，用 `as const` 对象或字符串联合；分支用可辨识联合表达。
- **`switch` 处理 verdict 这类封闭联合时必须穷尽**，新增状态不能静默落进含糊的 `default`——
  这与 [`project-conventions.md`](../project-conventions.md) §1.5「未知情况往严格的方向倒」是同一条规则在 TS 侧的形态。
- API 边界优先从 Zod schema 推导类型，**不要手写一份运行时 schema、再复制一份 TS 类型**。
- OpenAPI 只生成 `src/generated/api` 的类型，**禁止手改**；实际响应仍由 Zod 在边界校验。
  decoder 接受新增的未知可选字段，业务分支只依赖已声明的必需字段、HTTP status 和稳定 code。
- **时间、内存等单位保留服务端字段名**（`cpuNs`、`memoryBytes`），不在前端私自换单位命名；
  展示层可以格式化，原始模型不变。

## 2. 命名、文件与导入

- 目录和源码文件统一 `kebab-case`：`problem-list.tsx`、`use-submission.ts`。
- 组件、类型用 `PascalCase`；变量、函数用 `camelCase`。
- Hook 以 `use` 开头；布尔值用 `is` / `has` / `can` / `should` 表意。
- 回调 prop 用 `onSubmit`，组件内部处理函数用 `handleSubmit`。
- Props 命名 `<Component>Props`，**不加 `I`、`T` 等匈牙利前缀**。
- **默认具名导出**，便于重构和全局搜索；只有工具配置或第三方约定要求时才用默认导出。
- 用 `@/` 指向 `src/`，避免跨层 `../../../`；同目录短相对导入仍可用。
- 导入顺序由 ESLint 自动判断、`lint:fix` 修复：副作用 → 第三方 → `@/` → 相对路径。
- **不建 barrel 文件**把整个目录再导出一遍；feature 只有需要稳定公共入口时才显式导出。

**依赖方向固定为 `app/routes → features → components/lib`。** 一个 feature 不直接导入另一个
feature 的内部文件；跨 feature 协作通过路由、共享 API 模型或显式公共入口完成。这条边界用
`no-restricted-imports` 写进 ESLint，**不靠评审记忆**。

## 3. 注释与错误处理

- 注释解释「为什么这样设计、边界在哪里」，不复述代码字面行为。
- 公共 hook、复杂 query options 和反直觉的浏览器兼容处理写短注释；显而易见的组件不写模板式注释。
- 待办写 `TODO(TASK-001): 原因/退出条件` 并关联工作项，规则见 [`project-conventions.md`](../project-conventions.md) §1.8。
- **捕获异常先按 `unknown` 处理**，统一转换成应用错误类型；用户提示与诊断信息分开。
- 公共 HTTP client 把失败区分为 `http | network | timeout | aborted | contract`，
  但不负责缓存、导航、toast 或自动重试。
- **生产代码不散落 `console.log`**；可观测性走统一入口，测试中的预期错误需显式断言或抑制。

## 4. 工具分层

每个工具只负责一层，规则不重叠：

| 工具 | 负责 |
|---|---|
| `.editorconfig` | UTF-8、LF、文件末尾换行、两空格缩进、行尾空白 |
| `prettier.config.mjs` | 纯格式化，不判断代码正确性 |
| `eslint.config.js` | 错误模式、React Hooks、可访问性、导入、类型安全 |
| `tsconfig*.json` | 编译期类型边界 |
| Vitest / Playwright | 运行时行为与用户链路 |

ESLint 用 Flat Config，启用 `@eslint/js`、typescript-eslint 的类型感知推荐规则、
`eslint-plugin-react-hooks`、`eslint-plugin-jsx-a11y`、`eslint-plugin-simple-import-sort`
和 `eslint-config-prettier`；**不启用与 Prettier 冲突的排版规则**。

Prettier 基线：`semi: true`、`singleQuote: true`、`trailingComma: 'all'`、`printWidth: 100`、
`tabWidth: 2`、`endOfLine: 'lf'`，插件 `prettier-plugin-tailwindcss`。

- JS/TS 字符串用单引号，**JSX attribute 保持双引号**。
- **禁止用空格手工对齐**赋值或参数，格式交给 Prettier。
- 可以开编辑器 format-on-save，但 **Git hook 和 CI 只检查、不改写文件**。
- shadcn/ui 生成的代码进仓后同样执行项目格式化和 Lint，**不设永久豁免区**。
- `routeTree.gen.ts` 等持续生成的文件不得手改；生成物入库时 CI 必须重新生成并检查工作树无漂移。

## 5. 命令与门禁

统一脚本：`format(:check)`、`lint(:fix)`、`typecheck`、`test(:run)`、`test:e2e`、`build`、
`check`，以及 `generate:api(:check)` 和设计系统的 `generate:/check:` 系列。
**README、hook 和 CI 只调用这些脚本，不各写一套命令。**

- 开发中主动跑 `format` / `lint:fix`，提交前跑 `npm run check`。
- pre-commit 只检查暂存文件的 Prettier 和 ESLint 结果，**不自动修复、不重新 `git add`**。
- pre-push 在 `apps/web` 有改动时跑 `npm run check` 和 `npm run build`。
- OpenAPI 契约变化后跑 `generate:api` 并提交生成物，`generate:api:check` 会拒绝漂移。
- CI 用 `npm ci`，跑 `check`、生产构建、Storybook 静态构建和 Playwright；
  **任何 warning 都不作为长期可忽略状态**。
- **门禁分层：Prettier 管格式，ESLint 管代码风险，TypeScript 管类型，Vitest 管组件行为，
  Playwright 管端到端。某层通过不能替代下一层。**
