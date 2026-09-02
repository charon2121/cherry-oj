---
id: "DESIGN-024"
type: "design"
title: "统一页面任务优先布局并移除状态占位"
status: "checked"
work: "WORK-031"
owners: ["codex/root"]
depends_on: ["FEATURE-009", "EXPERIENCE-016"]
related: ["WORK-008", "WORK-020", "WORK-023", "WORK-025", "WORK-029"]
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# DESIGN-024：统一页面任务优先布局并移除状态占位

## 背景

FEATURE-009 要求删除早期连通性占位，禁止功能页以通用 title/desc 介绍块开场，统一双端导航到内容的
距离，并重新分配管理 Header、Sidebar 与 Main 的职责。现有实现中首页仍消费 `/api/status`；OpenAPI、
生成类型、Gateway 测试和 E2E 都有对应资产。页面分别覆盖 `py-8/10/16/20` 或设计系统响应式 Section
token，管理端根布局使用 `min-h-svh`，因此长内容推动整个 document，Sidebar 随之滚动。

## 目标与限制

- 同批完成公开 status 契约、Gateway 实现与唯一 Web 消费者删除，避免单边漂移。
- 用现有 `--ds-space-6` 作为 24px 页面起始间距，不增加 token、不改变主题或 Foundation 数值。
- 页面构成规则必须区分通用页面介绍与业务/组件/状态标题，保持语义和可访问性。
- 管理端只让 main 承担业务滚动；保留移动 Sheet、权限 guard、主题和账号行为。
- 不改业务 API、路由、数据库或其它服务。

## 整体方案

1. **契约优先删除 status 垂直切片**：从 OpenAPI 删除 path/schema并重生成类型，再删除 Gateway
   Controller/DTO/test、Web feature/test/mock、首页引用和工具链过期说明。Actuator 不变。
2. **任务优先页面构成**：在长期设计文档增加合同；逐路由移除只重复路由含义的 intro Stack，保留业务
   对象、表单/组件、登录和反馈状态标题，无可见标题时以 document title/视觉隐藏标题维持语义。
3. **统一壳层内容起点**：双端 Shell/Layout 通过共享类或小型 layout primitive 持有
   `--ds-space-6` 顶部 inset；页面删除覆盖该起点的 `py-*`，只保留自身底部和内部间距。
4. **管理壳层滚动**：Admin Shell 使用 `h-svh overflow-hidden` 两行网格；Main 设置
   `min-h-0 overflow-y-auto`，Sidebar 保持静止，移动端仍用 Sheet。
5. **账号菜单跨空间入口**：移除 Admin Header 独立 Link，扩展共享 AccountMenu 按所在空间显示
   “管理中心”或“返回用户端”，保持 Router 链接、图标、主题和退出流程。

## 模块与数据

- `contracts`：删除 status path/schema，继续作为 Web 生成类型真源。
- Gateway：删除 status Controller/DTO/test；不触碰 auth/problem BFF。
- Web：删除 status 查询切片，逐页移除通用介绍和局部顶部 padding，调整 Shell/Menu/Layout/E2E。
- `docs/design-system.md`：长期规则；不修改 Web 主题包或 docs 参考 token/主题资产。
- 无数据或持久化迁移。

## 接口与状态

`GET /api/status` 从 OpenAPI 与运行时直接删除；仓库内唯一消费者同步移除，因此不提供兼容响应。其它
URI、ApiSuccess/ApiProblem、requestId、Actuator、Session/CSRF 和角色状态均不变。页面本地状态与 Query
key 除 `system-status` 删除外不变。

## 安全与失败

删除浏览器探针不改变真正的健康监控。具体服务不可用仍由业务请求的现有错误状态表达。管理滚动容器不能
破坏 CSRF、认证 redirect、焦点可见性或 skip link。账号菜单只在已登录上下文展示空间入口，不扩大权限。

## 监控与部署

无新指标或部署顺序。前后端和 OpenAPI 必须同一提交交付；部署旧 Web + 新 Gateway 时首页会收到 404，
因此不拆成跨版本发布。验收用现有日志、Actuator、全量门禁与浏览器坐标/滚动断言。

## 迁移与兼容

这是有意的公开 API 删除。仓库内生成客户端同步更新，无弃用期；若人工审核确认存在仓库外调用方，需改为
先弃用再删除并重新签意图闸。CSS 不新增 token，旧主题和 320px 行为兼容。

## 备选方案

- 只隐藏首页组件、保留 `/api/status`：留下无人使用的契约和维护成本，拒绝。
- 逐页改成 `pt-6`：会再次漂移，拒绝；由共享壳层/Layout 持有。
- 用 sticky Sidebar 而让 document 继续滚：不满足“只有中央内容滚动”，拒绝。
- 全局删除所有 H1/标题：会误删题目名称、错误状态和表单语义，损害可访问性，拒绝。

## 风险与重审条件

主要风险是嵌套 main 滚动影响路由滚动恢复、焦点跳转和移动 Safari 视口；必须用真实浏览器覆盖。24px 若
在未来沉浸式编辑器或无 Header 页面不适用，应由明确模板声明例外，而不是普通路由自行覆盖。若引入仓库外
API 消费者、可折叠桌面侧栏、多层固定工具栏或 SSR document metadata，重新评审本方案。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已完成 status 契约删除、共享布局、main 滚动与兼容回退方案
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
