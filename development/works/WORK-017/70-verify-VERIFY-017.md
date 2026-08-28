---
id: "VERIFY-017"
type: "verify"
title: "建立 Web 设计系统代码基建"
status: "approved"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["TASK-023", "TASK-024", "TASK-025"]
related: []
implements: []
verifies: ["CAPABILITY-006", "TASK-023", "TASK-024", "TASK-025"]
tags: []
result: "fail"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---







# VERIFY-017：建立 Web 设计系统代码基建

## 验证对象

CAPABILITY-006 及 TASK-023/024/025 的实际实现：canonical CSS/主题运行时、共享组件与 Storybook、
当前 Web 消费者迁移、持续门禁和双主题浏览器行为。验证不重新批准 token 数值，也不把技术通过代签为
用户主题切换功能、生产发布或线上确认。

## 对应要求

- REQ-001～REQ-006：唯一真源、主题 fallback/派生属性、运行时 API、字体与语义边界。
- REQ-007～REQ-008：基础组件合同和 Storybook 双主题矩阵。
- REQ-009～REQ-010：当前消费者无业务漂移、生成/扫描/CI 门禁。
- REQ-011：主题、首屏、存储异常、可访问性、响应式与环境适配。
- REQ-012：确认实现范围不含 Verdict/OJ 业务组件，并保留 PE/not-found 上游问题。

## 检查与结果

### 规划阶段只读基线（不算实施验证）

- 2026-08-28，本机 macOS、Node 26.3.0 / npm 12.0.2：
  `node docs/design-system/tools/check.mjs` 通过，报告 2 个主题、56 required key、296 个允许对比组合，
  最低 3.404:1。
- 同环境 `npm run check` 通过：9 个测试文件、36 个测试。该 Node/npm 版本不符合仓库要求，只证明
  迁移前基线当前可运行，不能作为最终验收证据。

### 实施后自动检查

最终验收在隔离副本中使用 Node `24.20.0` / npm `11.19.0`，先从当前 `package-lock.json` 执行干净
`npm ci`（安装 789 packages），再运行以下命令；源工作区也用同一 Node/npm 组合重复过完整命令。

```text
npm run check
npm run build
npm run storybook:build
npm run test:e2e
```

结果全部退出码为 `0`：

- `npm run check` 先完成 canonical build/check、Web 主题生成检查、源码扫描及负向自测试，再完成
  OpenAPI 漂移、Prettier、ESLint、TypeScript 和 Vitest；共 `22` 个测试文件、`90` 个用例通过。
- canonical check 报告 `2` 个主题、每主题 `56` 个 required key、`296` 个允许对比组合，最低
  `3.404:1`；生成物与 package manifest 均为 current。
- Web 门禁拒绝并恢复 `11` 个源码违规 fixture 与 `1` 个 stale-generated fixture；`4` 个合法 fixture
  无误报。实际源码扫描无 raw hex/OKLCH、旧 `.dark`/`dark:`、literal theme id、`--ds-raw-*`、
  `color-mix()` 或 disabled/placeholder opacity 违规。
- Vite production build 转换 `2085` modules；`dist/index.html`、打包后的 canonical CSS、Inter 字体和
  `generated/theme-init.js` 正常产出，生产端无 `docs/design-system` 网络 URL。
- Storybook `10.5.10` 静态构建转换 `2236` modules；所有 Story 使用生成 registry 的全局主题 toolbar，
  320px 使用 Storybook 10 `globals.viewport`。构建只有工具自身 deprecation 提示，无失败或跳过。
- Playwright `1.62.1` 使用 Chrome Headless Shell `151.0.7922.34`，本地配置 retries=0；最终 `19/19`
  用例首轮通过，无跳过项。

实施期的诊断回归曾发现 password label 模糊定位、InlineNotice 新增非颜色状态标签、forced-colors 的
UA `color-scheme` 归一化，以及真实浏览器中 loading Button 缺可访问名称；这些问题分别通过精确定位、
语义断言、只检查 DOM 主题 metadata 和 Button loading `aria-label` 修复，最终矩阵已重新全量通过。

### 实施后场景矩阵

- Theme/first paint：missing、empty、explicit black、pure-white、unknown、localStorage 读取失败全部在首个
  `requestAnimationFrame` 读取根属性、计算 color-scheme 与非透明背景；未知/空值可写时清理。保存白色
  即使阻断 React bundle，首帧仍为白；页面没有 docs 请求。
- Runtime：Vitest 覆盖 storage 读写异常、DOM 两属性更新、setter 当前会话生效、同源 storage event 与
  非 localStorage event 过滤；Chromium 覆盖跨标签页 white 收敛及 unknown 回退/清理。
- Components：unit/Story 覆盖双主题 variant/state、键盘、disabled/loading/error、长中文和 320px；
  Chromium 另验证 Button 在 primary/secondary/ghost/danger 下 disabled 视觉压过 pressed。
- Environment：reduced-motion 下 `--ds-motion-fast/base` 均计算为 `0s`；forced-colors 下主题 metadata
  保持正确，键盘可到达登录链接且焦点边界存在。
- Regression：首页、登录限流与重复提交、首次改密、管理员用户管理/一次性密码、非管理员 403、Router
  404、系统状态和两主题 320px 横向溢出全部通过。
- 独立复核累计发现并关闭四项 P2：AsyncState busy/live region、storageArea 过滤、Button
  disabled+pressed 级联、Link 新窗口公告与实际 target 分叉；最终复核无剩余 finding。

## 未通过项

自动、构建、浏览器和独立代码复核在 DECISION-012 的原范围内没有失败，但人工架构验收未通过：Web 的
CSS、Vite、generator、source checker/self-test 和 package scripts 直接依赖 `docs/design-system`；
删除该目录后 `check`、生产构建和 Storybook 构建都会失败。这与用户确认的“移除 docs 后整个前端不受
影响”相冲突，已由 WORK-018 跟踪自包含重构。

另保留一个原范围明确排除的上游问题：
`components.manifest.json` 的 Verdict variants 漏 `contracts/verdict.json` 中的 `PE`，且当前 canonical
check 未做该精确集合对齐；本 WORK 不实现 Verdict，不能记为已修复。AsyncState 不含 not-found 的边界
已按 DESIGN-013 落地，Router 使用语义 `h1` 和恢复链接独立处理，并有 Chromium 回归。

## 范围检查

已用 `git status --short`、`git diff --name-only` 和静态路径检索逐 TASK 对照边界。改动限定在
`apps/web`、`docs/frontend.md` 与 WORK-017/开发索引：未修改设计 token/theme contract、
`src/generated/api`、auth/admin/status API 与业务 lib、服务端、判题引擎、contracts 或既有 WORK。
路由文件只迁移视觉消费者，guards、queries、mutations、确认和导航判据未改；原有业务 smoke 全部通过。

根目录 `scripts/work check` 输出 `✓ 134 份开发文档通过校验（0 个进行中提示）`。由于 WORK-017 尚未
进入真实 Git index，直接运行 `scripts/docs_test.py` 会按设计拒绝 `WORKS.md` 指向未跟踪目录；使用只在
`/private/tmp` 保存 index/object 的隔离 Git index 把 WORK-017 标为 intent-to-add 后复跑，输出
`✓ 167 份 Markdown 文档入口和本地链接有效`，没有修改用户的真实 Git index。

## 遗留问题

- 用户可见主题切换器、账户级主题同步与服务端偏好。
- Verdict/Submission lifecycle/editor/data table 等 OJ 业务组件。
- Verdict manifest/contract 的 PE 漂移门禁。
- TanStack Router runtime/plugin 既存版本差异，不在设计系统迁移中顺手处理。

## 剩余风险

WORK-017 的有效主题/组件实现可由后续工作复用，但 direct-docs 构建边界不能发布或作为最终基建。没有
生产环境，仍无法验证真实 CDN 缓存、CSP、静态资源策略或线上偏好；当前外部经典首屏脚本若未来遇到
严格 CSP，也必须重新审核 nonce/hash/package 策略。

## 结论

TASK-023～TASK-025 在原技术范围内实现并通过自动检查，但核心构建边界被人工验收否决；本 VERIFY
记为 `result: fail`，WORK-017 已终止。可复用实现保留在工作区，由 WORK-018 在获批后解除 docs 依赖并
重新做正常/无 docs 双重验证；本结论不代表生产发布或线上观察。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：Node 24/npm 11 干净安装下 check、build、Storybook 与 19 项浏览器回归全部通过，提交用户人工验证
- 2026-08-28：状态变更：review → approved。原因：人工验收否决 Web 构建期依赖 docs/design-system；删除该目录会破坏前端检查与构建，不满足自包含要求
