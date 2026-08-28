---
id: "MEMORY-013"
type: "memory"
title: "建立 Web 设计系统代码基建"
status: "draft"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["VERIFY-017"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# MEMORY-013：建立 Web 设计系统代码基建

## 背景

WORK-015 只建立了 docs 设计系统，明确禁止修改 Web；因此“设计系统已发布”和“运行时代码已迁移”是
两件事。WORK-017 负责后者。迁移前 Web 仍是旧浅色 `:root`、`.dark`、Geist、重复 adapter，
现有自动检查全绿并不代表视觉合同一致。

## 决定与原因

WORK-017 当时获批并实现：Web 直接在构建期消费 docs canonical CSS，只从 manifest 生成主题 metadata
与首屏脚本；继续 Base UI，不复制 token、不安装第二套 primitive。主题状态唯一落在根 `data-theme`，
`data-color-scheme` 是派生值，storage key 为 `cherry-oj.theme`。生产主题切换入口和 OJ 业务组件不
属于基础设施交付。

这条 direct-docs 构建决定随后未通过人工验收：删除设计系统文档会破坏 Web 检查和构建。它只能解释
WORK-017 的历史实现，不能作为长期项目结论；WORK-018 将以代码侧自包含方案替代。

## 尝试与教训

- 只跑设计系统 `check.mjs` 会验证 56 token/296 对比组合，却不会扫描 `apps/web`；Web 必须有独立
  生成和源码门禁。
- 只装 Storybook a11y addon 并静态构建，不等于自动完成所有无障碍验收；仍需执行扫描、键盘和人工复核。
- 把 token 散落复制进 `globals.css` 会形成不可验证的副本，但“因此直接 import 人类文档”同样不安全；
  正确边界应是 Web 自持完整源、合同、生成和校验，文档只在真实设计变更时同步。
- 首屏脚本、React provider 和 Storybook 各自硬编码 theme id 会漂移；必须消费同一生成 registry。
- 源码扫描的正向通过不足以证明门禁有效；临时目录必须注入违规源码与 stale generated，并验证失败后
  移除/重建可恢复。最终门禁覆盖 11 个源码负向、1 个生成漂移负向和 4 个合法反例。
- jsdom 能按 loading 子节点找到 Button 名称，真实 Chromium 可访问树却把 descendant status 分离；
  loading Button 必须显式设置 `aria-label`，关键无障碍合同需要真实浏览器回归。
- disabled 与 `aria-pressed` 同时存在时，Tailwind variant 生成顺序会让 pressed 覆盖 disabled；禁用态
  border/background/foreground 必须在组合态中明确胜出，并比较真实 computed style。
- Link 的“外部”意图不能代替真实导航事实；新窗口图标和辅助说明必须由最终 `target=_blank` 派生，
  才不会在显式 target 冲突时误报。
- 本地 Node 26 可以作实施诊断，但最终必须用仓库合同版本复验。WORK-017 已在 Node 24.20.0 / npm
  11.19.0 的隔离干净 `npm ci` 后通过 check、build、Storybook 和 Playwright。

## 已知问题

- `components.manifest.json` 的 Verdict variants 缺少 `contracts/verdict.json` 中的 `PE`，且
  `check.mjs` 未做精确集合校验；实现 Verdict 前必须单独修复。
- AsyncState manifest 不含 not-found/success；本次已把 not-found 明确归 Router、success 归正常内容。
  若未来要把两者加入 AsyncState，必须先更新组件合同，不能只扩实现 variant。
- TanStack Router runtime 与 Vite plugin 小版本不一致，是既有依赖维护问题，不属于本 WORK。
- 当前没有产品主题切换器、账户同步或生产发布观察证据；“运行时可切换”不能表述成已交付用户入口。
- VERIFY-017 的自动证据在旧范围内齐备，但人工架构验收为 fail，WORK-017 已取消；可复用主题/组件实现
  由 WORK-018 接续。本 MEMORY 保持 draft，不沉淀 direct-docs 为长期事实。

## 重新考虑条件

第二个独立前端消费者、Web 脱离 monorepo 构建、严格 CSP、服务端账户偏好、默认主题/theme contract
变化、Base UI 无法满足交互合同或需要开始 OJ 业务组件时，重新审核 package、bootstrap、primitive 与
组件边界。自包含重构应以 WORK-018/VERIFY-018 的结论为准；当前草案只保留失败路线的历史教训。
