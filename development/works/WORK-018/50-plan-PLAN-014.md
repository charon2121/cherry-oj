---
id: "PLAN-014"
type: "plan"
title: "解除 Web 对设计系统文档目录的依赖"
status: "approved"
work: "WORK-018"
owners: ["codex/root"]
depends_on: ["CHANGE-008", "DESIGN-014", "DECISION-013"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---






# PLAN-014：解除 Web 对设计系统文档目录的依赖

## 目标

在 CHANGE-008、DESIGN-014 与 DECISION-013 获人工批准并明确允许实施后，用单个 TASK 把 WORK-017 的
有效前端基建改为代码侧自包含：移除所有设计系统 docs 构建依赖，保留视觉/运行行为、合同校验和许可，
并在真的缺少 docs 目录的干净副本中证明全链路可用。

## 改动区域

- `apps/web/design-system`：新增 Web 本地设计系统源码、manifest、生成 CSS、窄化 build/check、README、
  NOTICE 与许可证。
- `apps/web/src/styles/globals.css`、`vite.config.ts`：改为 Web root 内 CSS，删除 docs fs allow。
- `apps/web/scripts`、`src/generated/design-system`、`public/generated`/合规静态文件：主题生成、源码门禁、
  stale fixture 和分发 notice 全部转为本地输入。
- `apps/web/package.json`、必要的 ignore/config：`check:design-system` 只执行本地工具；不新增依赖，
  `package-lock.json` 不变。
- `apps/web/e2e/design-system.spec.ts`：将特定 docs URL 负向断言收敛为不请求任何 `/docs/` 资源；其余
  主题和业务回归保持。
- `apps/web/README.md`、`TOOLCHAIN.md`、`CLAUDE.md`、`docs/frontend.md`、设计系统入口说明：更新所有权、
  命令、同步与调试口径；不修改设计值或参考页面。
- WORK-018：记录任务状态、实际命令、隔离证据与遗留风险。

## 阶段与顺序

0. **人工审核**：批准 CHANGE-008、DESIGN-014、DECISION-013、PLAN-014 的本地代码权威、无日常跨树
   drift check、许可分发和隔离验收，并明确授权 TASK-026。批准后、TASK ready 前按 DECISION-013
   废弃 CAPABILITY-006/EXPERIENCE-007，并以 DESIGN-014/DECISION-013 正式替代
   DESIGN-013/DECISION-012；每个 `scripts/work deprecate/supersede` 命令必须带该决定中记录的理由。
1. **建立本地包**：迁入运行所需源/合同/许可，建立窄 manifest 与本地 build/check；先在不改消费者的
   情况下做一次性逐 token/逐 contract 字段精确等价，再比对 2 主题、56 key、296 组合和完整精度最低
   基线 `3.4035078594052393`（三位小数报告 `3.404:1`），补错误值/allowedOn、symlink escape 等坏
   fixture。
2. **切换依赖**：改 CSS/Vite/generator/scanner/self-test/package scripts 到本地根，生成既有 runtime
   产物与分发 notice，删除每条 docs 执行路径。
3. **收口说明与门禁**：更新 Web/全局说明，泛化 E2E 网络断言，静态扫描 `apps/web` 与构建产物；不
   加 docs↔code 比较或自动 copy。
4. **双重验证**：正常工作区先跑全量；再构造排除 docs 目录、`node_modules`、`dist`、Storybook 与测试
   产物的临时完整仓库，从 `npm ci` 重跑 check/build/Storybook/E2E，并做独立影响复核。

## 并行与依赖

TASK-026 依赖 TASK-025 的现有实现及全部新上游文档。代码资产清单与许可审计可并行复核，但本地 checker
必须在切换 `package.json` 前可独立通过；CSS、generator 和 scanner 共用同一 manifest 根，不能由多个
实现同时改路径。隔离验证必须在所有普通工作区检查通过后执行，避免把实现故障误判为缺 docs 故障。

## 迁移与交付

这是无用户可见变化、无数据迁移的维护重构。迁移以一次提交内的本地包和接线切换完成，不保留兼容
fallback 到 docs；否则无法证明真正解耦。静态产物仍按原部署方式发布，新增/保留必要第三方 notice。
若本仓库没有生产发布环境，release/observe 可按维护流程保持可选；代码验证不能因此省略。

## 风险

- 直接复制 docs checker 会把 HTML preview 等文档包职责带进 Web；只迁运行合同规则并用固定指标验证。
- 只复制生成 CSS 会让未来无法安全修改主题；必须连同源、manifest、contract 和确定性 builder 迁移。
- formatter 可能机械改写迁移快照；应确认语义/合同指标而不是依赖跨目录字节一致，并保留修改说明。
- E2E 只 preview 既有 `dist`；隔离副本必须无旧产物且先 fresh build。
- `apps/web` 仍读 `contracts/` 检查 OpenAPI；不能误把“设计 docs 解耦”扩大成完整单目录发布。
- 许可证若只存在源码包而未随静态分发保留，仍有合规缺口；构建验证必须检查 notice/license。

## 验证

- 本地包：build `--check`、contract check 及负向 self-test；预期 2 主题、56 required key、296 对比组合，
  完整精度最低值与基线 `3.4035078594052393` 一致且三位小数仍报告 `3.404:1`；迁移时逐 token/逐
  contract 字段精确等价。缺 token、仍满足阈值的错误值、allowedOn 替换、alpha/低对比、symlink
  escape、adapter 或生成物篡改均失败并可恢复。
- 静态边界：`apps/web` 的构建关键源码/配置/脚本/package 对 `docs/design-system` 零引用，无 Vite docs
  allow、prebuild copy、symlink 或 fetch；`dist`/`storybook-static` 同样无路径/URL。
- 正常环境：Node 24/npm 11 下 `npm run check`、`npm run build`、`npm run storybook:build`、
  `npm run test:e2e` 全部通过，测试数量和工具版本据实记录；另做有超时和清理的 Vite dev 首页/CSS
  HTTP smoke，确认无 5xx/404/docs 请求后正常终止。
- 隔离环境：复制完整仓库但排除 `docs/design-system` 与所有依赖/产物，从干净 `npm ci` 开始重复上述
  命令；另启动 `npm run dev -- --host 127.0.0.1`，请求首页和 CSS 入口确认无 5xx/404/docs 请求后正常
  终止。检查目录确实不存在、package 内无 symlink，不能先生成或复制它。
- 行为：首屏 missing/empty/black/white/unknown/storage failure、跨标签页、reduced-motion、
  forced-colors、双主题 320px、组件状态及现有登录/权限/管理/404/状态 smoke 不回退。
- 合规与范围：构建产物包含必要 notice/license；`git diff --name-only` 不涉及锁文件、API、业务逻辑、
  设计值、服务端、判题或 contracts。

## 回退

无数据迁移。若实施中本地 checker 或构建故障，可整体回退 TASK-026 的本地包与接线，恢复 WORK-017
工作区继续诊断；主题偏好无需清理。但 direct-docs 架构已经人工验收失败，只能作为短期开发回退，不能
发布或把 WORK-018 记为完成。若迁移发现必须改变设计值/合同，停止任务而不是扩大回退范围。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：单任务迁移顺序、双环境验证和回退边界已形成，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准单任务实施顺序与正常/无 docs 双重验收矩阵
