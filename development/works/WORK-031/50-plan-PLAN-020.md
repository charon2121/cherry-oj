---
id: "PLAN-020"
type: "plan"
title: "统一页面任务优先布局并移除状态占位"
status: "checked"
work: "WORK-031"
owners: ["codex/root"]
depends_on: ["FEATURE-009", "EXPERIENCE-016", "DESIGN-024"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# PLAN-020：统一页面任务优先布局并移除状态占位

## 目标

按 FEATURE-009 和 EXPERIENCE-016 同批删除 status 垂直切片、建立任务优先页面规则、统一 24px 内容
起点，把返回入口收进账号菜单，并让管理端只有中央内容滚动。

## 改动区域

OpenAPI 与生成类型；Gateway status 实现/测试/工具链说明；Web 首页、system-status、Shell、AccountMenu、
Layout、现有页面和 E2E；`docs/design-system.md` 与 WORK-031 证据。

## 阶段与顺序

1. 契约优先删除 `/api/status`，生成类型并删除 Gateway/Web 垂直切片及测试引用。
2. 在长期设计文档写入页面构成、24px inset、账号菜单入口和滚动所有权合同。
3. 改共享 Shell/Layout：账号菜单上下文入口、双端内容起点、Admin 视口网格与 main 滚动。
4. 逐路由移除通用 intro 与局部顶部 padding，保留业务/组件/状态语义；补 document/隐藏标题。
5. 更新组件、路由、E2E 与服务端测试，格式化后执行全量 Web、server、契约和文档门禁。
6. 在两个主题、320px、桌面、200% 缩放与长管理内容中做浏览器验收并记录 VERIFY-032。

## 并行与依赖

OpenAPI 删除先于生成类型与 Web 消费者修复；设计规则确定后才实施 Shell/页面。Gateway 删除与 Shell
实现可以独立准备，但最终验证基于完整改动。任何仓库外 `/api/status` 消费者证据都会阻断直接删除。

## 迁移与交付

无数据迁移。OpenAPI、Gateway 和 Web 同一提交交付，不拆成会产生旧 Web 请求 404 的跨版本发布。代码
回退可整体恢复 status 垂直切片，布局与 API 删除也可按文件组分别回退。

## 风险

Main 嵌套滚动可能破坏路由滚动恢复、skip link 或移动端视口；机械删除标题会损坏业务语义；若仍允许
页面顶部 `py-*` 覆盖会名义统一、实际漂移。公开 API 删除可能影响未知外部调用方，当前依据是用户明确
声明已无用途且仓库仅有首页消费者。

## 验证

- status：生产范围无残留、OpenAPI 生成 check、Gateway 路由与 server 回归。
- Web：定向测试、`npm run check`、`npm run build`、`npm run storybook:build`、`npm run test:e2e`。
- 设计文档：docs design-system build/check；源码门禁无 raw token、magic page inset 或主题分支。
- 浏览器：Header→主体 24px、账号菜单键盘、Admin main-only scroll、Sidebar、移动 Sheet、320px/200%、
  双主题、forced-colors、reduced-motion。
- 仓库：`./mvnw clean verify`、`scripts/work check`、`git diff --check` 和范围复核。

## 回退

若 status 删除导致未知客户端故障，整体恢复 OpenAPI、Gateway 和 Web status slice；若布局回归，恢复
Shell 滚动与共享 inset，再保留已验证的页面 intro/账号菜单改动。均无数据回滚。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已按契约优先、长期规范、共享壳层、逐页适配和全量验证拆分实施顺序
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
