---
id: "DESIGN-026"
type: "design"
title: "重设计后台题目创建与编辑体验"
status: "checked"
work: "WORK-033"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-001", "EXPERIENCE-017", "DESIGN-027"]
related: ["WORK-015", "WORK-025", "WORK-031"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# DESIGN-026：重设计后台题目创建与编辑体验

## 背景

依据 `IMPROVEMENT-001`、`EXPERIENCE-017` 和 `DESIGN-027`。现有 API 已能完成全部业务动作，本工作把
前端从单文件长表单重构为可恢复的分步工作台，并以 CodeMirror 6 替换后台 Textarea/Monaco 编辑面。

## 目标与限制

- 保持 Browser → Gateway、OpenAPI、ProblemVersion、rowVersion 和发布状态机不变。
- URL 状态归 Router、服务端状态归 Query、持久化表单归 TanStack Form、编辑器只做代理。
- CodeMirror 通过官方模块直接集成，不采用第三方 React wrapper，不 fork 上游源码。
- 不在当前工作引入自动保存、协同编辑、WYSIWYG、后端 slug 生成、多语言或用户端答题编辑器。
- Monaco 只有在用户端代码工作台真正实现时再按需加入；当前后台移除后同时删除无消费者依赖。

## 整体方案

### 编辑器适配层

新增本地 `TextEditor`，内部在 mount 时创建一个 `EditorView`，通过 `updateListener` 把文档变化交给受控
`onChange`。外部值变化时仅 dispatch 文档替换，不销毁实例；组合输入期间不反向覆盖，避免中文输入法
中断。卸载时销毁 view。组件 props 只暴露业务需要的 value、onChange、label、description、error、
language、readOnly、height 和 aria 属性，不向页面泄漏 CodeMirror 实例。

扩展按最小集合组装：历史、搜索、行选择/光标、必要快捷键、特殊字符、括号匹配和语法高亮；Markdown
加载官方 markdown language，C++ 加载官方 cpp language。默认不绑定 Tab 缩进，保留键盘离开能力。

主题由 `useTheme().colorScheme` 驱动 `Compartment` 重配置；样式只引用 `--ds-*` 语义变量。CodeMirror
需要的 dark/light 行为只依据生成 registry 的 color scheme，不枚举主题 id。扩展异常通过 exception
sink 进入有界错误处理，不记录编辑内容。

### 工作台状态与拆分

版本路由新增受 Zod 约束的 `step` 搜索参数。`AdminProblemWorkbench` 负责加载 Problem/Version，并将
初始化数据传给 `ProblemWorkbenchProvider`。Provider 持有单一 TanStack Form、保存 mutation、dirty/
save 状态、Query 失效和离开保护；六个步骤组件只消费字段切片和动作，不各自复制表单。

表单从 `samplesJson` 改为 `samples: EditableSample[]`、从逗号字符串改为 `tags: string[]`。提交边界统一
trim、去重、生成 ordinal 和恢复 nullable 字段。保存成功后用响应更新 Query/rowVersion 并 reset 表单
基线，不用 key 强制重挂载整个工作台。若现有 update 响应无法提供完整版本，先 invalidate/refetch 后再
重置基线，期间串行化保存。

站内离开使用 TanStack Router `useBlocker`，浏览器关闭使用 `beforeunload`；仅当表单 dirty 或未提交的
参考源码存在时启用。步骤变化属于同一工作台，不触发离开保护。`Ctrl/Cmd+S` 在非组合输入时调用同一
保存函数并阻止浏览器默认动作。

### 业务编排

创建对话框继续调用现有 create API。上传操作在 upload 成功后串行调用 bind；第二步失败时 mutation
结果保留 uploaded item id，并显示独立“重新绑定”。部署、校准、publish-check 和 publish 仍调用现有
endpoint；前端只把它们编排为显式状态机，不乐观伪造服务端 READY/VALID。

界面秒转换为 `cpuNs/clockNs = seconds × 1_000_000_000`，MiB 转换为
`memoryBytes = MiB × 1_048_576`。Zod 在请求前限制有限数字、正值和安全整数；反向值不能整除时显示足够
精度并保持再次提交不损失原值，不能用格式化后的近似值覆盖服务端事实。

发布检查 code 映射固定为 CONTENT→题面、SAMPLES→样例、LANGUAGE→起始代码、TEST_DATA/DEPLOYMENT/
CALIBRATION→测试与校准。未知新增 code 作为“检查与发布”中的未识别阻塞显示，不被当作通过。

## 模块与数据

- `apps/web/package.json` / lock：加入 CodeMirror 官方核心与 Markdown/C++ language 包；移除当前无消费者
  的 Monaco React wrapper、本体和 C++ side-effect registration。
- `src/components/ui`：编辑器外壳、主题/扩展、Markdown 编辑器及 Storybook/组件测试。
- `src/features/problems/components`：列表创建对话框、工作台 provider/shell/steps、样例和发布映射。
- `src/routes`：仅为版本工作台增加 `step` 搜索参数；路由地址和 params 不变。
- `apps/web/TOOLCHAIN.md`、TypeScript 规范和 `docs/design-system.md`：同步真实依赖与长期选择规则。
- 无数据库、后端、OpenAPI 或生成 API 类型变化。

## 接口与状态

保存/上传/绑定/部署/校准/检查/发布分别保留独立 mutation/query 状态，禁止用一个全局 `isLoading` 隐藏
具体阶段。步骤状态由字段有效性和服务器资源状态派生；发布按钮只依赖最新 publish-check.ready。

保存错误优先读取 ApiProblem 的稳定 field violations/code；未知错误显示 request ID。创建 slug 的原生
pattern 改为 Zod/TanStack Form 字段校验，服务端 409 关联到 slug。所有现有 payload 与 response Zod
边界保留。

## 安全与失败

编辑器内容不进入异常日志、埋点或 DOM data 属性。Markdown 预览继续 sanitize。参考源码只存在 React/
CodeMirror 内存和校准请求，禁止恢复到持久化草稿。上传后绑定的两阶段失败按资源状态恢复；publish 结果
不明时重新读取版本和检查，不盲目重复。

行级/字段级错误不得破坏本地 value。rowVersion 409 时停止后续自动动作，保留本地内容并让用户显式加载
服务端版本。新增 Dialog 基于现有 Base UI 封装，维持焦点锁定、Escape、标题/描述和恢复焦点。

## 监控与部署

CodeMirror 只进入管理工作台懒加载 chunk。构建前后记录对应产物大小；若明显大于原 Monaco chunk，检查
是否误引入全语言包或 `basicSetup` 中不需要的扩展。部署仍是普通 Web 静态资源替换，无服务端顺序要求。

## 迁移与兼容

现有 API 数据在 provider 初始化时转换为编辑模型，保存时恢复原合同。旧 URL 默认 basic step，未知 step
通过 schema 回退。无数据迁移。锁文件与工具链文档必须同批更新，不能同时保留两套编辑器作为无期限兜底。

## 备选方案

- 继续 Textarea 并增加工具栏：依赖最少且原生可访问性好，适合短纯文本；但无法统一长 Markdown/代码的
  搜索、行号、语法和扩展体验，不满足本场景。
- 继续 Monaco：功能强且已有依赖，但当前只使用基础高亮，主题/移动端和 bundle 成本与收益不匹配；
  Monaco 官方也明确不支持移动浏览器，因此不作为响应式后台编辑器。
- Lexical/Tiptap 等富文本：适合所见即所得，不适合把 Markdown 源码作为稳定真源，排除。
- 第三方 React CodeMirror wrapper：接入快，但又增加生命周期和版本兼容层；当前需求足够小，采用官方
  `EditorView` + 本地薄适配。

## 风险与重审条件

CodeMirror contenteditable 行为、IME、受控同步和 a11y 必须用真实浏览器验证，不能只靠 jsdom。若未来
后台需要语言服务器、断点调试、复杂 IntelliSense 或超大工程多文件编辑，重新评估 Monaco；若只剩短备注，
应退回 Textarea，而不是把 CodeMirror 变成所有多行输入的默认答案。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：CodeMirror 适配、工作台状态、业务编排、安全、迁移与替代方案已补全，提交结构校验
- 2026-09-03：结构与内容校验通过，由工具置为 checked。
