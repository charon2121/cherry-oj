---
id: "TASK-036"
type: "task"
title: "实现 Web 题库与题目管理工作台"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "EXPERIENCE-013", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "TASK-035"]
related: []
implements: ["FEATURE-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/frontend.md", "docs/design-system.md", "docs/design-system/README.md", "contracts/web-api.openapi.json", "apps/web/TOOLCHAIN.md", "apps/web/design-system", "apps/web/src", "apps/web/e2e"]
write_paths: ["apps/web/package.json", "apps/web/package-lock.json", "apps/web/TOOLCHAIN.md", "apps/web/src", "apps/web/e2e", "development/works/WORK-025"]
forbidden_paths: ["apps/web/design-system", "docs/design-system", "apps/server", "apps/judge-engine"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-036：实现 Web 题库与题目管理工作台

## 任务目标

实现匿名题库/详情和 ADMIN 题目生产工作台；URL 归 Router、服务端状态归 Query、长表单归 TanStack
Form、管理表归 Table、代码归 Monaco，覆盖保存冲突、上传/下载、部署、校准、发布与恢复。

## 依据

落实 front matter 所列 `FEATURE-007` 要求和 `EXPERIENCE-013`，消费 `TASK-033` 生成类型与
`TASK-035` 公开 API。遵守 `docs/design-system.md` 及代码侧现有合同；本任务只消费设计系统，不修改
token、主题、manifest 或设计说明。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `/problems` 与 `/problems/$slug` 文件路由、search schema、路由生成物和桌面/移动主导航入口。
- `features/problems` 的 API/Zod/Query、筛选、列表项、分页、详情/Markdown/样例/语言组件。
- 项目既定 Markdown 安全链所需的明确依赖及 Web 工具链说明；不启用原始 HTML。
- loading、全局 empty、筛选 empty、首批 error、下一批 error、invalid cursor、not found、success UI。
- Vitest/Testing Library/MSW 与 Playwright 场景，覆盖 URL 恢复、匿名访问、敏感 canary、键盘和 320px。
- `/admin/problems` 管理表、新建页和 `/admin/problems/$problemId/versions/$versionId` 版本工作台。
- 题面/样例/C++/测试数据/部署限制/预览发布章节，TanStack Form/Table、Monaco 和上传进度/取消。
- ADMIN 下载、绑定、部署、参考程序验证、publish-check、确认发布、创建修订与归档体验。

## 完成标准

- [x] 桌面与移动导航都能进入题库，active 状态正确；详情真实链接可刷新、复制和新标签打开。
- [x] q/difficulty/tag/codeMode/language/sort/size/cursor 由 Router 校验；改变筛选清空 cursor，前进后退与
  刷新恢复，非法 URL 使用明确默认/错误策略且不崩溃。
- [x] Query 负责请求/缓存/重试，页面不使用 Effect 请求或复制服务端状态；加载下一批失败保留已有结果。
- [x] endpoint Zod schema 校验必需字段并容忍新增未知可选字段，contract 错误不能显示为业务 empty/404。
- [x] Markdown 经 GFM + sanitize 且不执行 raw HTML；敏感 canary 不在 DOM/URL/storage，题目代码块可读。
- [x] loading/empty/error/not-found/success、长中文、键盘、focus、读屏状态和 320px 无裁切通过测试。
- [x] 不写 raw color/theme 分支，不修改设计系统；Web check、build、E2E 与 API 生成漂移检查通过。
- [x] USER/匿名/首次改密不能渲染或请求管理数据；ADMIN 刷新可恢复工作台与服务端实际状态。
- [x] 保存冲突保留本地输入并可重载；上传/部署/验证/发布结果不明时先重新查询，不盲目重复创建。
- [x] 测试正文/storageRef/参考源码不进入 Query 持久化、URL、local/sessionStorage、错误或日志；离开清空参考源码。
- [x] 管理工作台键盘、file input、进度 live region、确认对话框、长 Markdown/代码与 320px 通过。

## 验证

在 `apps/web` 执行格式、lint、typecheck、Vitest、build 和 Playwright。MSW/Playwright 至少覆盖
FEATURE-007 AC-001～AC-014，包括 public 流程、ADMIN 创建/编辑冲突、合法/恶意 ZIP、下载、绑定、部署、
参考程序、发布/修订、权限、故障恢复、XSS/canary、键盘和 320px。运行设计系统消费侧检查但不修改资产。

## 风险

风险是状态重复、Markdown XSS、大文件误存内存、敏感参考源码持久化或页面绕过服务端权限。TanStack
Form/Table 和 Monaco 已在本 TASK 范围；若需要新设计 token/合同、CORE、多语言、环境 UI、submission
或离线持久草稿，停止并升级上游。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全 Web 产出、依赖、状态、可访问性、安全验证与升级边界，等待人工批准。
- 2026-08-30：扩展为公开题库与 ADMIN 题目/测试数据/发布工作台。
- 2026-08-30：状态变更：todo → ready。原因：TASK-035 已完成且用户已批准实施，Web 依赖和契约均已就绪
- 2026-08-30：状态变更：ready → doing。原因：开始实现公开题库与 ADMIN 题目/测试数据工作台
- 2026-08-30：完成公开题库列表/详情、七项 URL search 校验、游标/空/错误/404 状态、安全 Markdown、
  桌面与移动导航；响应 Zod 白名单会容忍并剥离新增字段，敏感 canary 不进入页面状态。
- 2026-08-30：完成 ADMIN 题目 Table、新建 Form、版本长表单与本地 Monaco；接通保存冲突恢复、ZIP
  上传/取消/进度、版本下载/绑定、部署、参考程序校准、发布检查/发布、修订、归档与草稿删除。
- 2026-08-30：`npm run check` 通过（含设计系统/API 漂移、format、lint、typecheck、103 项 Vitest）；
  `npm run build` 通过；`npm run test:e2e` 的 26 项 Chromium 场景全部通过，覆盖匿名详情、安全 canary、
  invalid cursor、ADMIN 新建/刷新恢复、权限、键盘与 320px。
- 2026-08-30：状态变更：doing → done。原因：公开题库与 ADMIN 题目/测试数据工作台已实现，Web check、build 与 26 项 E2E 全部通过
