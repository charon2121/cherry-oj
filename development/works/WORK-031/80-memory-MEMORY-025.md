---
id: "MEMORY-025"
type: "memory"
title: "统一页面任务优先布局并移除状态占位"
status: "checked"
work: "WORK-031"
owners: ["codex/root"]
depends_on: ["VERIFY-032"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# MEMORY-025：统一页面任务优先布局并移除状态占位

## 背景

早期主页用 `/api/status` 证明 Web→Gateway 连通，后续真实业务 API 与 Actuator 已覆盖这一职责。页面
扩展过程中又反复复制“eyebrow + H1 + desc + mt-6”和不同 `py-*`，导致首屏任务后移、间距漂移；
Admin Header/Sidebar/Main 的滚动所有权也没有形成长期合同。

## 决定与原因

功能页直接进入任务，通用可见 intro 不再作为页面模板；24px 起点由共享 `Section` 持有，页面不能私自用
响应式 `py/pt` 覆盖。业务对象、组件、登录和反馈状态标题保留，无需可见标题时用 document title 或
`sr-only` H1 维持语义。Admin 桌面由 main 独立滚动，移动端继续文档滚动和 Sheet；跨用户/管理空间的
入口归头像菜单。无价值 status 垂直切片从契约、Gateway、Web、测试与说明完整删除，运维继续看 Actuator。

## 尝试与教训

不能通过全局搜索删除所有 H1 来实现“无 page title”，也不能逐页把 `py-10` 改成 `pt-6` 后宣称统一。
前者会伤害业务语义和读屏，后者会在下一页再次漂移。公开接口删除也必须从合同到生成物、实现、消费者、
测试与说明全链路完成，不能只隐藏 UI。

共享间距应落在所有页面必经的布局原语，而不是 Shell main 的裸 padding：这样 loading、not-found、登录
和业务详情仍使用同一合同，组件本身也不感知 Header。隐藏 H1 不占布局流，首个可见表单仍能精确落在
24px。Admin 的高度链需要 root `h-svh/overflow-hidden`、内容区 `min-h-0` 和 main `overflow-y-auto`
同时成立，缺少任一层都可能退回 document scroll。

E2E 使用 Vite preview 时必须先 build，否则测试会命中旧 dist 并产生与源码不一致的假失败。Mockito
动态附加与 Testcontainers 在受限沙箱内也可能失败，应该在获准的正常环境复跑原命令，不把环境限制误判为
业务回归。

## 已知问题

仓库内已无 status 消费者；仓库外客户端无法由代码搜索证明。Admin main-only scroll 当前由 Chromium
自动化覆盖，Safari/Firefox 的滚动恢复仍需持续关注。页面无可见 intro 后，新增路由若忘记 document title
或隐藏 H1 会造成语义回归，应把它保留在评审清单中。

## 重新考虑条件

新增沉浸式全屏编辑器、无全局 Header 的页面、多层固定工具栏、可折叠/可调整管理侧栏、SSR metadata，
或仓库外客户端需要稳定状态 API 时重新评审。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已沉淀任务优先布局、共享 24px、滚动高度链、接口删除与验证环境教训
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
