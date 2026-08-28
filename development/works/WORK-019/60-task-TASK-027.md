---
id: "TASK-027"
type: "task"
title: "设计 Cherry OJ 任务入口主页"
status: "done"
work: "WORK-019"
owners: ["codex/root"]
depends_on: ["FEATURE-002", "DESIGN-015"]
related: []
implements: ["FEATURE-002"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/frontend.md", "docs/design-system.md", "docs/design-system", "apps/web/src/routes/index.tsx", "apps/web/src/routes/__root.tsx", "development/works/WORK-002", "development/works/WORK-013", "development/works/WORK-015", "development/works/WORK-019"]
write_paths: ["Figma Drafts / Xian Xian's team / Cherry OJ · 任务入口主页", "development/works/WORK-019"]
forbidden_paths: ["apps/web", "apps/server", "apps/judge-engine", "contracts", "docs", "development/works（WORK-019 除外）", "用户已有 Figma 文件"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---



# TASK-027：设计 Cherry OJ 任务入口主页

## 任务目标

在用户批准 FEATURE-002、EXPERIENCE-008 与 DESIGN-015 并明确允许执行后，使用 Figma 工具创建一个
可编辑、组件化的 Cherry OJ 任务入口主页文件，完成桌面、窄屏、双主题和关键身份状态验证，并返回
可打开链接；不修改产品实现。

## 依据

实现 FEATURE-002 的 REQ-001～REQ-016 和 AC-001～AC-007，严格依赖 EXPERIENCE-008 的文案/状态与
DESIGN-015 的文件结构、变量、组件和 Frame 矩阵。WORK-015 是视觉合同，WORK-002/013 只提供产品与
身份边界；不得借本任务推进它们的未确认内容。

## 可查看范围

以 front matter 的 `read_paths` 为准。特别要先核对当前设计系统 token、组件 manifest、产品非目标、
身份状态和现有首页差异；Code Connect 搜索若为空须记录为“无对应文件”，不能假定存在 Figma Library。

## 可修改范围

以 front matter 的 `write_paths` 为准。Figma 只允许新建本任务 Draft，不得写入用户已有文件；仓库内只
允许更新 WORK-019 的执行与验证记录。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。尤其禁止修改 Web 页面、设计系统真源、公共契约、服务端、
判题引擎和其他工作项，也禁止把 Figma 输出当作代码侧 token 真源。

## 依赖

以 front matter 的 `depends_on` 为准。FEATURE-002 和 DESIGN-015 必须由用户从 review 明确批准，且用户
必须在后续消息中允许执行；初始请求不视为批准或 Figma 写入授权。

## 产出

- Figma 文件 `Cherry OJ · 任务入口主页` 及可访问 URL。
- `01 · Homepage` 的五类 Frame、`02 · Local Components` 的局部组件和 `03 · Notes` 的边界注释。
- Foundation/Theme semantic variables；经批准的 Starter 降级为 Cherry Black / Pure White 两个独立
  单 mode semantic collection，并以组件 Theme 变体保持相同 anatomy。
- 每个交付 Frame/区块的截图、metadata、字体读回与对比/响应式检查结果。
- WORK-019 的真实执行记录和 VERIFY-019 待用户复核证据。

## 完成标准

- [x] 在唯一有 Full 席位的 Figma plan Drafts 新建文件，并记录 file key、URL 和主页主 Frame node id。
- [x] 先完成 Code Connect/既有 Screen/Library 发现步骤；无 Cherry Library 时按 DESIGN-015 建立局部变量
      与组件，不使用无来源社区组件替代。
- [x] 两套单 mode Theme variables 与文档 `design-tokens.json` 对齐；颜色、间距和圆角绑定变量，
      Theme 组件变体覆盖 Cherry Black / Pure White。
- [x] Button、Nav Item、Step Item、Quick Entry Row、Inline Status 和 Brand Mark 为组件/实例关系。
- [x] 完成桌面 User/Guest/Light、320px User 与 States Frame，命名和尺寸符合 DESIGN-015。
- [x] Guest、User、首次改密、ADMIN、Session loading/error、Gateway error 的信息与动作符合状态表。
- [x] 逐区块和整页截图无文字裁切、重叠、占位文案、空白图标或遗留 shimmer。
- [x] 字体读回确认自由文本使用 Inter 家族；中文回退意图和 Figma 字重近似在 Notes 标明。
- [x] 320px、双主题、长中文、focus、对比与非颜色状态检查通过。
- [x] 设计不包含 FEATURE-002 非目标，仓库业务实现和用户已有 Figma 文件未被修改。

## 验证

执行时按 Figma 工作流小步验证：

1. `whoami` 确认 Full 席位后创建新 Design 文件；记录 file key。
2. 只读检查 pages、local variables、Code Connect 与可用 Library；新文件无既有 Screen 时明确记为 N/A。
3. 分别创建变量、局部组件、wrapper、各 section 和状态 Frame；每次返回全部 node ids。
4. 对 Hero、四步路径、快速入口、状态区以及每个完整 Frame 获取 screenshot，检查裁切、重叠、主题、
   组件变体和 placeholder。
5. 读回所有自由文本的 `fontName`，确认 Inter family；检查 1440px 与 320px 的阅读顺序和关键动作。
6. 在仓库运行 `scripts/work check`；实际结果写入 VERIFY-019，不把未执行项目标为通过。

## 风险

如果 Figma 计划权限不足、字体不可用、变量 API 不支持所需 mode、现有设计系统值之间冲突，或页面需要
新增统计/路由/产品状态，停止执行并回到上游文档，不用硬编码或虚构能力绕过。若用户提供新的可编辑
目标文件，先更新 write scope 再写入；不得自行改写已有文件。

## 执行记录

- 2026-08-28：创建任务。
- 2026-08-28：补全 Figma-only 范围、Frame 矩阵、组件/变量、验收与禁止路径；等待人工审核，尚未创建
  Figma 文件或修改产品实现。
- 2026-08-28：状态变更：todo → ready。原因：上游功能、体验与 Figma 方案已获用户批准，任务范围和完成标准齐备
- 2026-08-28：状态变更：ready → doing。原因：用户明确确认文档通过并允许执行 TASK-027，开始 Phase 0 Figma 发现与设计构建
- 2026-08-28：Phase 0 完成。在 `Xian Xian's team` 新建 Draft `Cherry OJ · 任务入口主页`，file key
  `DsRbH32JRiOw4G9RAawzGG`；新文件为空，Code Connect 与 Cherry OJ Library 搜索均无结果，Inter 与
  JetBrains Mono 可用。
- 2026-08-28：Phase 1 在 P1.a 暂停。当前 Figma Starter plan 创建 `Pure White` 第二 variable mode 时返回
  `Error: in addMode: Limited to 1 modes only`。测试集合已按返回 ID 回滚，复查为 0 collection、0 variable、
  0 page child；等待用户选择升级/改用支持双 mode 的可编辑目标，或批准单 mode 双集合降级方案。
- 2026-08-28：用户明确批准单 mode 双集合降级，Phase 1 恢复；降级仅改变 Figma 主题表达机制，双主题
  Frame、semantic alias、组件实例与视觉验收仍完整保留。
- 2026-08-28：Phase 1 在 P1.b10 再次暂停。Starter plan 的远程 MCP 工具调用配额已耗尽，返回
  `You've reached the Figma MCP tool call limit on the Starter plan.`；失败调用原子回滚，已成功保留 4 个
  collection、53 个颜色 primitive 与 24 个 Foundation variable。目标 Draft 已在 Figma Desktop 登录态中
  可见；等待用户确认是否运行本任务生成的临时本地 Development Plugin，在同一 Draft 继续构建。
- 2026-08-28：用户明确授权在目标 Draft 运行临时本地 Development Plugin。Figma Desktop 已生成合法
  plugin id `1675083465204103425`；后续改为 Foundations → Structure → Components → Screens → Verify
  五个幂等阶段执行，远程 MCP 不再重试，完成后移除本地插件登记。
- 2026-08-28：五个阶段全部完成。最终文件包含 4 Pages、4 个 variable collection/207 variables、9 个
  text styles、6 个 effect styles、6 个 component set/34 variants、5 个交付 Frame 和 60 个 instance；
  Verify 于 `2026-08-28T10:25:12.859Z` 返回 `pass: true`、`failedChecks: []`。
- 2026-08-28：交付 Frame 为 Desktop User Cherry Black `20:3482`、Desktop Guest Cherry Black
  `20:3580`、Desktop User Pure White `20:3664`、Mobile User Cherry Black `20:3762`、States `20:3851`。
  已逐一在 Figma Desktop fit-to-selection 检查中文换行、裁切、双主题、访客权限、状态与真实 focus。
- 2026-08-28：Figma 在 variable-bound shadow 上会把 1px spread 读回为 0；三个 ring/raised-ring effect
  改用经语义 token 解析后的颜色快照保留 1px 几何，其他颜色、间距和圆角继续绑定变量。该降级已由
  Verify 与视觉检查覆盖。
- 2026-08-28：临时 Development Plugin 登记在成功后移除；仓库产品实现、设计系统真源、其他 WORK 与
  用户已有 Figma 文件均未由本任务修改。
- 2026-08-28：状态变更：doing → done。原因：目标 Draft、局部设计系统、五类 Frame、链接与实施证据均已完成，提交 VERIFY-019 人工复核
