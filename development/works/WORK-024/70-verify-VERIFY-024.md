---
id: "VERIFY-024"
type: "verify"
title: "重新设计登录页视觉与体验"
status: "review"
work: "WORK-024"
owners: ["codex/root"]
depends_on: ["TASK-032"]
related: []
implements: []
verifies: ["FEATURE-006", "TASK-032"]
tags: []
result: "pending"
created_at: "2026-08-29"
updated_at: "2026-08-30"
---




# VERIFY-024：重新设计登录页视觉与体验

## 验证对象

TASK-032 在用户人工退回后完成的登录页构图、透明工作区字景资产、无多余页面滚动，以及对认证、Shell、
双主题、响应式和设计系统合同的兼容性。

## 对应要求

覆盖 FEATURE-006、EXPERIENCE-012 与 TASK-032 全部完成标准；本轮重点复核用户指出的两项问题：实际页面
必须还原第 2 个候选的大幅右侧字景，并且 1440×1024 桌面视口不得出现不必要的滚动条。

## 检查与结果

验证环境：macOS、项目锁定的 Node/npm、Playwright Chromium 与应用内浏览器。2026-08-29 实际执行：

- 应用内浏览器：在 `/login`、Cherry Black、1440×1024、密度 1 下检查。透明字景实际区域为
  `644 × 699.38`，位于右侧 `x=748, y=144`；文档 `scrollHeight = innerHeight = 1024`，
  `scrollWidth = innerWidth = 1440`，Footer bottom 为 1024。控制台无 error。
- 视觉对照：参考图 `/Users/charon/.codex/generated_images/01a04819-4181-7a00-a119-e03bdec1ffe5/exec-97c59845-145f-4807-ac84-3e84d7fa8dc2.png`；
  实现截图 `/private/tmp/cherry-oj-login-qa/implementation-revision-1440x1024.png`；同画面对照
  `/private/tmp/cherry-oj-login-qa/side-by-side-revision.png`。`apps/web/design-qa.md` 最终为 `passed`。
- `npm run check`：退出码 0；设计系统门禁、生成一致性、Prettier、ESLint、TypeScript 和 Vitest 全部
  通过，Vitest 为 28 个测试文件、100 个测试通过。
- `npm run build`：退出码 0；生产构建通过。
- `npm run storybook:build`：退出码 0；默认、pending、error、long error 与 mobile 场景构建通过。
- `npm run test:e2e`：退出码 0；Chromium 23/23 通过。新增桌面文档高度不超过视口断言；既有
  Cherry Black/Pure White、320px、200% 等效宽度、键盘、forced-colors、reduced-motion、限流、pending、
  成功跳转、首次改密与秘密不落存储回归继续通过。
- 2026-08-30 短窗口复核：1280×720 默认状态和 401 错误状态的水平、垂直溢出均为 0；应用内浏览器
  同尺寸复测 `scrollWidth = innerWidth = 1280`、`scrollHeight = innerHeight = 720`，Footer bottom 为 720。

## 未通过项

暂无自动化或设计 QA 未通过项。VERIFY-024 等待用户对修正后的实际页面再次独立验收。

## 用户退回项处理

- P1 视觉不符：已删除小字号 HTML/CSS 近似绘制，改为真实透明 PNG；资产保留大幅 `FOCUSED WORKSPACE`、
  行号、代码行、点阵和技术辅助线，并按页面实际槽位重新裁切和定位。
- P1 页面滚动：根因是 `min-height: 100%` 与上下 padding 在 content-box 下叠加。现改为 `box-border`、
  `height: 100%` 和零最小高度网格；浏览器几何与 E2E 均证明桌面无横向或纵向溢出。

## 范围检查

产品实现仅修改登录页面视图、页面测试、登录 E2E 与新增 `public/login-workspace-art.png`；路由认证编排
保持不变。未修改认证、Session/CSRF、Shell、共享 UI、设计系统真源、主题、依赖、后端或契约。
WORK-021 等不相关本地修改保持原样。

## 遗留问题

设计稿的密码 placeholder 未加入产品，因为共享 `PasswordField` 没有该合同；这不影响本轮用户指出的
构图和滚动问题，也不阻断登录主流程。

## 剩余风险

参考图错误提示属于生成示例，默认页面不会伪造错误；真实错误仍由共享 `ErrorNotice` 在请求失败后呈现，
并由 Storybook、组件测试和 E2E 覆盖。当前无阻断性的行为、视觉或响应式风险。

## 结论

用户退回的两项问题均已修正，仓库验证与同画面设计 QA 通过；提交用户再次独立人工验收。在用户确认前
保持 `review/pending`，不由实施者代签 `approved/pass`。

## 变更记录

- 2026-08-29：用户人工验收退回，VERIFY 从 `review` 退回 `draft/pending`。
- 2026-08-29：完成透明资产、无滚动条修正、同画面对照和全量回归，重新提交独立人工验收。
- 2026-08-29：状态变更：draft → review。原因：透明字景、无滚动条、双主题与全量自动化验证通过，重新提交用户独立人工验收
- 2026-08-29：状态变更：review → draft。原因：用户复核未通过，补充短桌面窗口无溢出验证后重新提交
- 2026-08-30：状态变更：draft → review。原因：1280×720 双状态零溢出与 1440×1024 构图复核通过，重新提交用户独立验收
