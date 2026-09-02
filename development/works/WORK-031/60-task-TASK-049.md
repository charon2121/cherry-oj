---
id: "TASK-049"
type: "task"
title: "统一页面任务优先布局并移除状态占位"
status: "done"
work: "WORK-031"
owners: ["codex/root"]
depends_on: ["FEATURE-009", "DESIGN-024", "PLAN-020"]
related: []
implements: ["FEATURE-009#REQ-001", "FEATURE-009#REQ-002", "FEATURE-009#REQ-003", "FEATURE-009#REQ-004", "FEATURE-009#REQ-005", "FEATURE-009#REQ-006", "FEATURE-009#REQ-007", "FEATURE-009#REQ-008", "FEATURE-009#AC-001", "FEATURE-009#AC-002", "FEATURE-009#AC-003", "FEATURE-009#AC-004", "FEATURE-009#AC-005", "FEATURE-009#AC-006", "FEATURE-009#AC-007", "FEATURE-009#AC-008"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/product.md", "docs/design-system.md", "docs/design-system", "docs/engineering/typescript.md", "docs/engineering/java.md", "docs/engineering/conventions.md", "apps/web/TOOLCHAIN.md", "apps/server/TOOLCHAIN.md", "contracts/web-api.openapi.json", "apps/web/design-system", "apps/web/src", "apps/web/e2e", "apps/server/gateway-service", "development/works/WORK-008", "development/works/WORK-019", "development/works/WORK-020", "development/works/WORK-023", "development/works/WORK-025", "development/works/WORK-029", "development/works/WORK-031"]
write_paths: ["docs/design-system.md", "contracts/web-api.openapi.json", "apps/web/src/generated/api", "apps/web/src/app/shells", "apps/web/src/components/ui/layout.tsx", "apps/web/src/routes", "apps/web/src/app/pages", "apps/web/src/features/system-status", "apps/web/src/features/problems/components", "apps/web/e2e", "apps/server/TOOLCHAIN.md", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/api/status", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/api/status", "development/works/WORK-031"]
forbidden_paths: ["apps/web/design-system", "docs/design-system", "apps/web/src/generated/design-system", "apps/web/public/generated", "apps/web/src/lib/theme", "apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "database migrations", "contracts（web-api.openapi.json 除外）", "development/works（WORK-031 除外）"]
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# TASK-049：统一页面任务优先布局并移除状态占位

## 任务目标

完成 status 垂直切片删除和双端页面布局收口，使现有全部路由遵循任务优先、统一 24px 起点与明确滚动
所有权，并用自动化/浏览器证据验证接口、导航、响应式和可访问性没有回归。

## 依据

实现 FEATURE-009#REQ-001～REQ-008 与 AC-001～AC-008；页面语义与异常边界以 EXPERIENCE-016 为准，
契约删除、共享 inset、Admin main scroll 和账号菜单方案以 DESIGN-024/PLAN-020 为准。

## 可查看范围

以 front matter 的 `read_paths` 为准。实施前运行 `scripts/work context TASK-049`，重点核对 OpenAPI
生成、现有页面 H1 分类、Section 覆盖、Admin Shell 高度链和 WORK-023/029 导航兼容边界。

## 可修改范围

以 front matter 的 `write_paths` 为准。允许删除 status 文件和相关生成类型，修改现有页面、共享壳层/
Layout、E2E、长期设计规范及本 WORK；不因布局统一顺手改业务组件视觉。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。不得修改主题包/主题运行时、其它服务、其它合同/WORK、数据
或数据库；不得把所有标题机械删除，也不得以 raw spacing/token 或页面私有 magic number 绕过共享合同。

## 依赖

FEATURE-009、EXPERIENCE-016、DESIGN-024 和 PLAN-020 必须完成意图审核；收到用户后续明确执行授权前保持
`todo`。OpenAPI 删除先于生成类型与 Web 消费者删除。

## 产出

- 删除 `/api/status` 的 OpenAPI/Gateway/Web 完整垂直切片和过期工具链说明。
- `docs/design-system.md` 页面构成与滚动合同。
- 共享 24px 内容 inset、Admin main-only scroll、账号菜单返回入口及全部现有页面适配。
- 组件/路由/服务端测试、E2E 坐标与滚动矩阵、VERIFY-032 和 MEMORY-025。

## 完成标准

- [x] FEATURE-009 的 8 项 REQ 与 8 项 AC 均有实现和 VERIFY-032 精确锚点。
- [x] status path/schema、Gateway 实现/测试、Web query/UI/test/mock 和生成类型均删除，Actuator/其它 API 回归。
- [x] 所有现有功能页面首屏直接进入任务，通用可见 intro 消失而业务/组件/状态标题和非视觉语义保留。
- [x] 双端 Header→首个主体统一 24px，无逐页顶部 magic override；320px/桌面/200% 无遮挡溢出。
- [x] 返回用户端只在管理账号菜单，管理 Header 更简洁，用户端管理入口与账号/主题行为不回退。
- [x] 桌面 Admin 只有 main 滚动，Header/Sidebar 稳定；移动 Sheet、skip link、焦点和路由 guard 正常。
- [x] 长期设计文档、Web/Storybook/E2E、OpenAPI、Gateway/server、工作流和 diff 门禁全部通过。
- [x] 未越过 front matter 边界；仓库内无外部 status 消费者，也未修改主题/其它服务实现。

## 验证

按 PLAN-020 执行 OpenAPI 生成 check、Gateway 定向与 server `clean verify`、Web format/check/build/
Storybook/E2E、设计文档包 check、`scripts/work check` 与 `git diff --check`。Playwright 实测双端
多路由 24px 坐标、Admin main-only scroll、账号菜单键盘、320px/200%/双主题/环境适配。

## 风险

风险集中在公开 API 删除、嵌套 main 滚动和标题语义误删。任何仓库外消费者证据、路由滚动/焦点无法
可靠恢复、或必须改主题/其它服务才能完成时停止实施，更新 FEATURE/DESIGN/TASK 边界并重新走意图闸。

## 执行记录

- 2026-09-02：创建任务。
- 2026-09-02：状态变更：todo → ready。原因：WORK-031 意图闸已由用户签署，进入实施准备
- 2026-09-02：状态变更：ready → doing。原因：开始实施 WORK-031 已批准方案
- 2026-09-02：删除 status 垂直切片，统一 Section 24px 起点、账号菜单跨端入口与 Admin main-only scroll，完成全部现有页面迁移。
- 2026-09-02：Web check/build/Storybook、27 项 Playwright、Gateway 55 项和 server 133 项验证通过，回填 VERIFY-032。
- 2026-09-02：状态变更：doing → done。原因：status 垂直切片、页面布局、账号菜单与 Admin 滚动实施完成，前后端全量验证通过
