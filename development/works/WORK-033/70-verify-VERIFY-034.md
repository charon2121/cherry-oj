---
id: "VERIFY-034"
type: "verify"
title: "重设计后台题目创建与编辑体验"
status: "approved"
work: "WORK-033"
owners: ["codex/root"]
depends_on: ["TASK-051"]
related: []
implements: []
verifies: ["IMPROVEMENT-001#AC-001", "IMPROVEMENT-001#AC-002", "IMPROVEMENT-001#AC-003", "IMPROVEMENT-001#AC-004", "IMPROVEMENT-001#AC-005", "IMPROVEMENT-001#AC-006", "IMPROVEMENT-001#AC-007", "IMPROVEMENT-001#AC-008", "IMPROVEMENT-001#AC-009", "IMPROVEMENT-001#AC-010", "IMPROVEMENT-001#AC-011", "IMPROVEMENT-001#AC-012", "TASK-051"]
tags: []
result: "pass"
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# VERIFY-034：重设计后台题目创建与编辑体验

## 验证对象

待实施后验证 WORK-033 的 CodeMirror 编辑器、题目创建 Dialog、六步工作台、保存/恢复、结构化样例、
测试校准链、发布/危险操作、响应式与兼容边界。

## 对应要求

front matter 锚定 IMPROVEMENT-001#AC-001～AC-012 与 TASK-051，实际证据如下：

- AC-001～AC-002：列表只保留搜索、状态、列表和“新建题目”入口；Dialog 按标题、slug、难度组织并显示
  ACM/C++ 事实。创建成功进入草稿；六步导航写入受 Zod 约束的 `step`，缺失/非法值回退基本信息。
- AC-003：TanStack Form 持有草稿，sticky 上下文栏显示 dirty/saving/saved/error 与参考源码未校准；
  `Ctrl/Cmd+S`、Router/beforeunload 离开保护、409 本地复制与显式重载均落地。Playwright 特别延迟首个
  保存响应，并确认保存期间继续输入不会被旧响应覆盖。
- AC-004～AC-005：题面五字段、样例长字段、起始代码和参考程序统一消费本地 CodeMirror 6 薄适配；
  Markdown 支持编辑/分屏/安全预览与展开，窄屏隐藏分屏；生产源码和直接依赖已无 Monaco。
- AC-006：标签改为 chip；样例改为结构化输入/输出/解释，支持新增、复制、移动和确认删除，保存边界生成
  连续 ordinal，不再要求业务人员编辑 JSON。
- AC-007～AC-008：测试数据呈现上传并自动绑定、部署、校准三段依赖；绑定半失败保留 READY 数据和重试
  入口。界面输入秒/MiB，请求前校验并无损换算到 ns/bytes，校准响应再换回友好单位。
- AC-009：publish-check 自动读取，六个稳定 code 映射到对应步骤，未知 code 留在发布步骤；发布只认
  服务端 ready。可见性/修订与归档/删除分组，发布和危险操作均使用项目 Dialog。
- AC-010：编辑器具有业务化 aria label、描述/错误关联、语义 token 主题且不绑定 Tab；保存快捷键忽略
  composition。组件测试、Storybook 和真实 Chromium 覆盖受控同步、中文输入、两个主题、320px、
  forced-colors 与 reduced-motion；200% 等效尺寸由既有设计系统回归覆盖。
- AC-011～AC-012：后端、OpenAPI、生成 API、数据、主题合同和公开题库未修改；`docs/design-system.md`
  已沉淀编辑器选择、结构化重复项、长工作台、保存/禁用原因和危险操作规则。

## 检查与结果

- `cd apps/web && npm run check`：设计系统包/源码自测、OpenAPI 生成一致性、format、lint、typecheck 与
  112 项 Vitest 全部通过。
- `npm run build`：生产构建通过；版本工作台懒加载 chunk 从基线 2653.49 kB / gzip 681.50 kB 降到
  754.24 kB / gzip 255.80 kB，分别减少约 71.6% / 62.5%。Vite 仍提示该 chunk 超过 500 kB，但没有
  误引入 Monaco 或全语言包。
- `npm run storybook:build`：包含 TextEditor default/invalid 状态的 Storybook 静态构建通过。
- `npm run test:e2e`：Chromium 30 项通过，覆盖创建 Dialog、保存中继续编辑、CodeMirror 中文输入与
  Ctrl+S、步骤 URL/状态保留、320px 无水平溢出，并回归认证、公开题库、双主题、forced-colors、
  reduced-motion 和 Admin main-only scroll。
- `npm ls @monaco-editor/react monaco-editor codemirror @codemirror/view --depth=0`：只剩 CodeMirror
  直接依赖；`rg` 确认题目生产组件没有 Monaco、Textarea、samplesJson 或原生 confirm。
- `scripts/work check`、`git diff --check` 与 TASK 边界核对通过。

## 未通过项

首次运行设计系统源码门禁时，CodeMirror theme 函数参数名被规则误识别为旧 `dark:` utility；改为
`EditorView.darkTheme` facet 后既保留 color scheme 语义又通过门禁。首次扩展保存并发场景时暴露旧响应会
错误清除 dirty；改用编辑修订号与提交快照后，延迟响应回归通过。两项均已关闭，无产品未通过项。

## 范围检查

改动限定在 TASK-051 允许的 Web 依赖/工具链、通用 UI、题目组件、版本路由、E2E、三份长期说明与
WORK-033；工作状态命令同步更新全局索引。未修改 Problem API client、OpenAPI/生成 API、后端、数据库、
设计系统代码包/主题运行时、公开题库或其它 WORK 内容。

## 遗留问题

Vite 仍对 754.24 kB 的工作台 chunk 给出大包提示；相较 Monaco 基线已显著下降，继续拆分 CodeMirror
语言或步骤组件只在真实加载指标显示必要时进行，当前不为了消除阈值提示引入异步编辑器故障面。

## 剩余风险

本轮自动化使用 Node 26.3.0，而仓库正式引擎约束为 Node 24；类型、构建与浏览器测试均通过，但 CI 仍是
最终 Node 24 兼容证据。应用内自动化浏览器没有继承用户先前登录态，因此真实数据上的视觉/IME/读屏体验
留给验收闸人工检查；隔离 Chromium 已覆盖对应结构与主链路，不把缺失登录态伪装成真人验收。

## 结论

实现、独立复核与自动验证通过，提交用户人工验收。新的创建入口和六步工作台已替换旧长表单，草稿/API/
数据保持兼容；发布资格仍以服务端检查为准。

## 变更记录

- 2026-09-03：记录 CodeMirror、创建/六步工作台、保存并发、结构化样例、测试发布链和范围证据，自动
  验证结论为 pass，提交人工验收。
- 2026-09-03：状态变更：draft → review。原因：Web 112 项、Chromium 30 项、build、Storybook、设计系统、OpenAPI 与范围门禁通过，提交人工验收
- 2026-09-03：验收闸通过：review → approved。原因：题目创建与编辑体验验收通过
