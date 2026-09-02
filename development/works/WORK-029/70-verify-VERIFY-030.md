---
id: "VERIFY-030"
type: "verify"
title: "新增页面主题切换入口"
status: "approved"
work: "WORK-029"
owners: ["codex/root"]
depends_on: ["TASK-047"]
related: []
implements: []
verifies: ["FEATURE-008#REQ-001", "FEATURE-008#REQ-002", "FEATURE-008#REQ-003", "FEATURE-008#REQ-004", "FEATURE-008#REQ-005", "FEATURE-008#REQ-006", "FEATURE-008#REQ-007", "FEATURE-008#REQ-008", "FEATURE-008#REQ-009", "FEATURE-008#AC-001", "FEATURE-008#AC-002", "FEATURE-008#AC-003", "FEATURE-008#AC-004", "FEATURE-008#AC-005", "FEATURE-008#AC-006", "TASK-047"]
tags: []
result: "pass"
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# VERIFY-030：新增页面主题切换入口

## 验证对象

WORK-029 / TASK-047 实施后的用户可见主题切换入口，包括双端页头装配、manifest 驱动的主题循环、现有
运行时的持久化与跨标签页行为、存储失败恢复、可访问性、响应式和既有导航回归。

## 对应要求

- FEATURE-008#REQ-001～REQ-003：双端入口、即时切换、manifest 驱动与页面状态不丢失。
- FEATURE-008#REQ-004～REQ-006：本地持久化、首屏/跨标签页、偏好边界与存储失败恢复。
- FEATURE-008#REQ-007～REQ-009：设计系统边界、键盘/响应式/非颜色表达与环境适配。
- FEATURE-008#AC-001～AC-006：身份矩阵、双主题往返、刷新/标签页、失败状态、无障碍矩阵与全量门禁。
- TASK-047：文件范围、产出、完成标准与禁止修改项。

## 检查与结果

验证环境：macOS arm64，本地静态预览，Codex bundled Node `24.19.0`、npm `12.0.2`，Playwright Chromium。

1. `npm run check`：通过。设计系统校验确认 2 个主题、每主题 56 个 required key、296 个允许对比组合；
   生成资产/API 无漂移，format、lint、TypeScript 全部通过，Vitest `31` 个文件、`112` 项测试全绿。
2. `npm run build`：通过，Vite 转换 `3508` modules 并生成生产静态资源。已有 Monaco 大 chunk 警告仍在，
   本次没有新增依赖或改变分包边界。
3. `npm run storybook:build`：通过，转换 `2521` modules；ThemeSwitcher 包含黑色当前、白色当前、存储
   失败和 320px 四种 Story。
4. `npm run test:e2e`：`29/29` 通过。新增场景实际覆盖键盘切换、动态名称、页面即时生效、本地存储、
   刷新恢复、用户端/管理端共享偏好、跨标签页同步、写入失败播报、forced-colors、reduced-motion、
   两主题 320px 用户页头与 320px 管理页头；登录、改密、账号菜单、用户管理和题库既有场景全部回归。
5. 实施中曾测得新增按钮让 320px 管理页头溢出 `9px`；仅在 `< sm` 隐藏“返回用户端”的装饰箭头后，
   保留“用户端”文字与完整可访问名称，重新构建并复验为无关键横向溢出。
6. `scripts/work check` 与 `git diff --check`：通过；最终范围复核见下节。

## 未通过项

暂无。沙箱内首次启动 `127.0.0.1:4173` 被 EPERM 拒绝，按授权在本机预览端口重跑后全部通过；这不是
产品或测试失败。

## 范围检查

实现修改严格位于 TASK-047 的允许范围：新增 `theme-switcher.tsx`、测试与 Story，修改双端 Shell 装配和
`e2e/design-system.spec.ts`，并更新 WORK-029 证据。未修改设计系统、生成资产、主题运行时、共享 UI、
路由、feature/API、后端、contracts、docs 或其他 WORK；`package.json` 与 `package-lock.json` 未变化。

源码复核确认 ThemeSwitcher 只消费生成 registry、`getThemeDefinition`、`useTheme` 和现有 IconButton；生产
组件没有 literal theme ID、`data-theme` 分支、raw 颜色、独立 storage key、系统主题逻辑或门禁 allowlist。

## 遗留问题

暂无本工作阻塞项。账户级/跨设备同步、跟随系统和多主题设置面板仍是 FEATURE-008 明确排除的未来能力。

## 剩余风险

浏览器本地偏好不跨设备同步；未来主题数量显著增加后，循环按钮可能不如选择菜单直观。当前 registry 仅
有两个主题，组件会自动按登记顺序循环，两项均为已声明边界，不影响当前验收。

## 结论

通过。TASK-047 的代码、交互、无障碍、响应式、持久化、异常恢复和回归要求均有自动化证据，范围没有
越过；等待用户进行产品验收并签署 WORK-029 验收闸。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：Node 24 下 Web check、build、Storybook 与 29 项 Playwright 全部通过，范围复核无偏差
- 2026-09-02：验收闸通过：review → approved。原因：确认双端主题切换、持久化和响应式体验符合预期
