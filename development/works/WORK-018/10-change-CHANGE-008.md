---
id: "CHANGE-008"
type: "change"
title: "解除 Web 对设计系统文档目录的依赖"
status: "approved"
work: "WORK-018"
owners: ["codex/root"]
depends_on: []
related: ["VERIFY-017", "CAPABILITY-006"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---





# CHANGE-008：解除 Web 对设计系统文档目录的依赖

## 为什么做

上一项工作把统一的视觉主题和共享组件接进了前端，但为了避免文档和代码说法不一致，前端的安装、检查
和构建直接读取了设计说明目录。验收时认为这个边界不合理：文档的作用是帮人理解和评审产品，不该成为
前端能不能构建的必要条件——现在只要删掉那份说明，前端就跑不起来。本次让前端完整持有自己运行所需的
东西，文档回归说明的角色。

## 当前状态

WORK-017 已建立主题运行时、共享组件和页面迁移，但当前 Web 不是自包含的：全局 CSS 直接 import
`docs/design-system` 的聚合 token 与 Tailwind adapter；Vite 为该外部目录开放读取；Web 的主题生成器、
源码门禁及其负向 fixture 读取其中的 manifest 和主题文件；`npm run check` 还直接执行该目录下的
build/check 工具。README、TOOLCHAIN 和长期前端文档也把这条依赖描述为正式架构。

因此删除 `docs/design-system` 后，`npm ci` 本身可以完成，但 `check`、生产构建和 Storybook 构建都会
失败，干净 CI 也无法进入可信的 E2E。已有 `dist` 可能让单独执行预览式 E2E 假绿。

## 当前问题

设计文档承担了前端源代码仓库的职责。它把“避免两份内容漂移”变成了“代码必须依赖文档存在”，违反
顶层技术栈边界，也让文档整理、拆分或删除能够破坏产品构建。生产浏览器虽不请求 docs URL，但这只说明
运行产物已打包，不能证明安装、检查和构建独立。

## 目标状态

- REQ-001：`apps/web` 持有运行、生成和验证所需的完整设计系统代码资产；其普通安装、开发、检查、
  构建、Storybook 和 E2E 链路不读取或执行仓库任何 `docs/` 文件。
- REQ-002：代码侧本地包包含 Foundation、主题源码、主题 manifest、语义合同、Tailwind adapter、
  生成入口、内部完整性清单、本地 build/check 工具、来源声明与必要许可证。
- REQ-003：前端本地校验继续覆盖 manifest 路径安全与唯一性、default/fallback、56 个 required semantic
  key、主题完整声明与 alias/opaque 规则、296 个允许对比组合、reduced-motion、Tailwind alias 和
  生成物漂移；同时保留现有运行资产 checker 对 Foundation 每个值、主题每个值、contract entry 的
  type/contrastClass/allowedOn/opaque/rules、selector 与 CSS color-scheme 的精确断言。只移除服务文档
  reference HTML、组件说明与 preview 的检查。
- REQ-004：主题 registry、首屏脚本和源码扫描只消费代码侧 manifest；Vite 不再开放 docs 目录，CSS
  只 import Web 根目录内文件。
- REQ-005：Web 常规命令和 CI 不建立 docs↔code 的字节、哈希或自动同步门禁。真正修改设计系统时，
  对应 WORK/TASK 必须同时修改代码与设计说明，并分别通过各自检查。
- REQ-006：在排除 `docs/design-system`、旧依赖与旧构建产物的临时完整仓库中，从 `npm ci` 开始复验
  Web 全链路；前端源码与最终静态产物均不含对该目录的读取或网络依赖。
- REQ-007：Apache-2.0 许可证、固定来源摘要和 Cherry OJ 修改说明不能只留在可删除文档中；代码仓库
  与静态分发保留必要 notice/license。

## 不变条件

- REQ-008：不修改 `cherry-black`/`pure-white` 的 token 数值、默认与 fallback、storage key、根元素
  属性、主题解析规则、首屏时序或跨标签页行为。
- REQ-009：不修改共享组件 props/DOM 语义、Story、现有页面的请求、权限、路由、文案和产品行为；
  不新增主题切换器或 OJ 业务组件。
- REQ-010：不引入 npm workspace、发布包、运行时 fetch、prebuild 从 docs 自动复制、符号链接或新的
  外部依赖；`package-lock.json` 不应变化。
- REQ-011：`apps/web` 对仓库 `contracts/` 的既有 OpenAPI 漂移检查不属于本次解耦，继续保留。

## 影响范围

直接影响 `apps/web/design-system`（新增）、全局样式、Vite、主题生成器、设计系统源码扫描与自测、
`package.json` 检查链、E2E 网络断言及 Web 文档。为避免开发流程继续指导错误边界，还需更新
`CLAUDE.md`、`docs/frontend.md` 与设计系统入口说明；设计 token/theme 源值不在修改范围。

## 风险

最大风险是表面移除路径却削弱质量门禁，或遗漏相对 import、fixture、许可证等隐性依赖。另一个风险是
用旧构建产物验证，造成删除 docs 后仍可运行的假象。通过本地合同检查负向 fixture、全仓路径扫描和干净
隔离构建控制。若必须改变视觉值、主题合同、组件接口或业务行为，停止 TASK-026 并建立相应设计系统工作。

## 回归检查

- AC-001：`apps/web` 的可执行源码、配置、脚本和命令对 `docs/design-system` 为零引用；Vite 不含外部
  docs 读取白名单，浏览器不请求任何 `/docs/` 资源。
- AC-002：本地合同检查仍报告 2 个主题、每主题 56 个 required key、296 个允许对比组合；完整精度最低
  值与迁移前 `3.4035078594052393` 一致，三位小数报告仍为 `3.404:1`。Foundation/theme/token/
  contract/selector/color-scheme 与迁移基线逐项精确等价。故意删 token、改成仍满足阈值的错误颜色、
  替换 allowedOn、加入 alpha/低对比、使用越界 symlink 或篡改生成物时检查失败并可恢复。
- AC-003：Node 24 / npm 11 的普通工作区中，`npm run check`、`npm run build`、
  `npm run storybook:build`、`npm run test:e2e` 全部通过；有超时和清理的 Vite dev smoke 能启动、返回
  首页/CSS 且无 5xx/404/docs 请求后正常终止。
- AC-004：临时完整仓库排除 `docs/design-system`、`node_modules`、`dist`、`storybook-static` 和测试产物，
  干净 `npm ci` 后重复 AC-003 全部通过，并启动 Vite dev 后请求首页/CSS smoke；没有使用旧产物、
  符号链接或预复制。
- AC-005：原有 19 项主题/业务浏览器矩阵、组件测试、Storybook 双主题与首屏生成物行为不回退，构建
  产物中无 docs 路径或 URL。
- AC-006：代码侧来源与许可证校验通过，静态分发包含必要 notice/license；改动范围不涉及依赖锁、
  API、业务页面、服务端、判题引擎或公共契约。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：自包含目标、不变条件与删除 docs 的验收标准已形成，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准 WORK-018 的改动目标、不变条件与验收标准
