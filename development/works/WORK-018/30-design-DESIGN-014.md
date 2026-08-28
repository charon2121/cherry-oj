---
id: "DESIGN-014"
type: "design"
title: "解除 Web 对设计系统文档目录的依赖"
status: "approved"
work: "WORK-018"
owners: ["codex/root"]
depends_on: ["CHANGE-008"]
related: ["DESIGN-013"]
implements: []
verifies: []
supersedes: "DESIGN-013"
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---






# DESIGN-014：解除 Web 对设计系统文档目录的依赖

## 背景

CHANGE-008 来自 WORK-017 的人工验收反馈。原方案把 `docs/design-system` 设为 canonical 构建输入，
意图是用单一真源避免漂移；实际形成了跨顶层目录的硬依赖。审计确认直接 CSS import、Vite
`server.fs.allow`、Web generator/source checker/self-test 和 `package.json` 均读取或执行 docs 文件。
删除该目录后，检查、Vite build 与 Storybook build 立即失败。

WORK-017 中已完成的主题 resolver、首屏脚本、共享组件、Storybook 和消费者迁移仍可复用。本设计只
改变运行资产的所有权和校验边界，不重新设计视觉或组件。

## 目标与限制

目标是让 Web 在完整仓库只缺少 `docs/design-system` 的情况下，仍能从零安装、检查、构建和通过浏览器
回归，同时保留现有主题合同、对比度门禁、来源与许可证。`apps/web` 内不允许出现依赖该目录存在的
import、命令、生成、fixture、Vite 配置、prebuild copy、symlink 或 fetch。

限制如下：

- 不改变任何 token 数值、主题 id/default/fallback、组件合同、运行时 API、业务页面或公开 API。
- 不建立 JS workspace 或发布 npm package；当前仍只有一个前端消费者。
- 不把 docs reference HTML、preview、组件 manifest 或机器 token 快照机械复制进 Web。
- 不把 docs↔code 比较放回 `npm run check`。两侧只校验各自内部一致性，设计系统真实变更由对应 WORK
  同步维护。
- 保留 `contracts/` OpenAPI 检查；“自包含”特指不依赖设计系统文档，不等于把 Web 从 monorepo 抽离。

## 整体方案

采用“Web 自持代码包 + 本地合同校验 + 文档显式同步”的结构：

```text
apps/web/design-system/               Web 可执行真源
  README.md                           代码维护与变更边界
  manifest.json                       仅登记代码包文件
  LICENSE.open-design + NOTICE.md     来源、修改和许可
  tokens.foundation.css               共享基础量
  theme-contract.json                 语义 token / 对比合同
  themes.manifest.json                主题 id、default、fallback 与源码登记
  themes/*.css                        完整主题源码
  tokens.css                          本地工具生成的稳定 CSS 入口
  tailwind-v4.css                     Tailwind/shadcn 语义 adapter
  tools/build.mjs + tools/check.mjs   只读本目录的生成与合同校验
          │
          ├─ globals.css 本地 import ─────────► Vite / Storybook
          └─ Web generator ─► themes.ts + theme-init.js + distribution notices
                                      │
                         theme runtime / components / routes

docs/design-system/                   面向人的设计说明与参考，不参与上述链路
```

本地包从已验收文档资产做一次受控迁移。此后它是前端运行和 CI 的权威；docs 仍表达产品设计与参考效果，
但不提供 Web 构建输入。未来修改主题、token 或 adapter 时，在同一工作项中显式更新两侧，而不是设置
持续跨树漂移比较。

## 模块与数据

- `apps/web/design-system`：持有运行所需源、生成入口、内部清单、合同校验及合规材料；不 import Web
  业务代码，也不读取仓库外路径。
- 本地 `tools/build.mjs`：按本地 Foundation 与 theme manifest 确定性生成 `tokens.css`；`--check`
  在临时输出中比较，禁止手改生成物。
- 本地 `tools/check.mjs`：从 docs checker 迁入运行资产相关规则：manifest 安全/唯一/default/fallback、
  56 required key、完整声明/alias/opaque、296 个对比组合、Foundation/reduced-motion、Tailwind adapter、
  包清单、来源与许可证；保留现有 checker 的 `expectedFoundationTokens`、`exactThemeValues`、每个 contract
  entry 的 type/contrastClass/allowedOn/opaque/rules、selector 与 CSS color-scheme 精确校验。删除
  reference HTML、preview、components manifest 等文档专用验证。
- `scripts/generate-design-system.mjs`：只读本地主题 manifest/源码，继续生成 TS registry 与首屏脚本；
  同时确保静态分发含本地许可证与 notice。现有文件协议、storage key 和输出 API 不变。
- `scripts/check-design-system.mjs`：只读本地 manifest；stale-generated fixture 在临时 Web 目录建立本地
  design-system 子目录，不再伪造仓库 docs。
- `globals.css` 与 Vite：只访问 Web root 内资产，删除外部 docs allow。
- Web/全局文档：说明“代码是运行权威、docs 是设计说明、真实设计变更同 WORK 同步”，不提供会被
  日常命令调用的跨目录同步脚本。

没有数据库、网络协议、依赖或用户数据变化。代码侧 `manifest.json` 只登记上述本地文件，不能照搬
包含 HTML preview 的 docs package registry。

## 接口与状态

`ThemeId`、默认/fallback、`cherry-oj.theme`、`data-theme`、`data-color-scheme`、
`ThemeProvider/useTheme`、共享组件 props 和生成文件路径保持兼容。`tokens.css` 仍是 CSS 稳定入口，
`themes.manifest.json` 仍是主题 metadata 唯一来源；变化仅是二者从 Web 根目录内解析。

本地包区分三类文件：人工维护源、确定性生成物、合规材料。manifest 记录角色；build/check 在源缺失、
越界路径、未知主题、生成物 stale、合同不完整、低对比或许可证摘要异常时失败。组件与页面仍只消费
semantic token，不识别主题 id。

## 安全与失败

- manifest 中的主题文件继续做词法目录边界校验；再对本地包全树 `lstat` 拒绝 symlink，并对每个输入
  `realpath` 后重新确认仍在本地根内，不能用 `../`、绝对路径或链接逃回 docs/其它目录。自测加入
  symlink-escape 负向 fixture。
- localStorage 仍按不可信字符串处理；本次不改 resolver 或 DOM 写入规则。
- 不允许在安装、prebuild、check 或 Vite plugin 中从 docs 自动复制；否则删除 docs 仍会失败，只是把
  显式 import 换成隐式依赖。
- 本地合同工具的故意坏 fixture 必须证明缺 token、alpha、低对比、adapter/生成物漂移会失败，避免
  精简 checker 时静默降级。
- Apache-2.0 原文、固定来源摘要、非官方 Linear 声明和 Cherry 修改说明进入代码侧；构建产物保留
  必要 notice/license，不依赖 docs 承担分发合规。
- 任何需要改变视觉值、主题合同或组件 API 的发现都停止当前 TASK，回到新的设计系统 WORK。

## 监控与部署

部署仍是 Vite 静态站，不增加服务或网络请求。CI 命令形状保持 `npm ci`、`check`、`build`、
`storybook:build`、Playwright；`check:design-system` 改为只串联本地 build/check、runtime 生成漂移和
Web source scanner。构建后扫描 `dist`/`storybook-static`，确认没有 docs 路径/URL且合规文件存在。

本工作为工程重构，不新增线上产品行为；若没有生产发布环境，release/observe 可继续按维护工作实际情况
处理。首屏主题、控制台错误、静态资源 404 和现有页面 smoke 仍由原浏览器矩阵覆盖。

## 迁移与兼容

迁移按四个可检查步骤进行：

1. 建立本地包并从当前已验收资产一次性迁入运行源、合同和许可；对迁移源/生成物做一次性逐文件 hash
   或解析后逐字段/逐 token 精确等价证明，重写窄 manifest/build/check，并证明当前 2 主题、56 key、
   296 对比组合、完整精度最低值 `3.4035078594052393` 与三位小数报告 `3.404:1` 保持。该迁移证明写入
   VERIFY，不进入日常 docs↔code 检查。
2. 把 CSS、Web generator、source scanner/self-test 和 Vite 改到本地根；重新生成既有 registry、
   bootstrap 与分发 notice，确认输出 API/内容未发生非预期漂移。
3. 删除 `package.json` 中 docs 工具命令，更新路径门禁、E2E 网络断言和开发说明；`apps/web` 不再出现
   对设计系统 docs 目录的可执行引用。
4. 先在正常工作区复验，再在排除 docs 目录、依赖与构建产物的临时完整仓库中从 `npm ci` 复验全链路。

无需兼容旧跨目录 import；它不是公开接口。现有浏览器主题偏好和静态部署产物保持兼容。回退时恢复
WORK-017 的跨目录接线即可，但这只用于短期故障恢复，不能作为验收通过方案。

## 备选方案

1. **A（推荐）：`apps/web/design-system` 本地代码包。** 单消费者下边界清晰、无需发布，删除 docs 后
   全链路可用；代价是文档与代码不再自动逐字一致，真实设计变更需同 WORK 同步。
2. **B：仓库级 workspace/npm package。** 同样能解除 docs 依赖，也适合多消费者，但当前为一个 Web
   引入 workspace、版本与发布治理过重；出现第二消费者时再选。
3. **C：prebuild 从 docs 复制到 Web。** 表面上运行产物本地化，但干净安装/检查仍依赖 docs，直接违反
   删除目录无影响的目标。
4. **D：保留 DECISION-012 的直接 import 或通过 symlink 复用。** 继续保持同一硬依赖，已被人工验收
   明确否决。
5. **E：只提交生成后的 `tokens.css`。** 会丢失其相对源、主题 manifest、可重建性、合同/对比校验和
   来源边界，不足以作为可维护基建。

## 风险与重审条件

主要风险是本地 checker 精简错误、初次迁移漏值、合规材料未随分发、文档和代码后续分叉，以及隔离
验收被旧产物污染。聚合数量/最低对比度不能证明值和 allowedOn 没变，因此还要用一次性精确等价、保留
运行资产逐值/逐字段断言、symlink fixture、生成检查、静态路径扫描和从零隔离构建控制。

出现第二个独立前端消费者、需要发布/版本化设计系统包、设计文档自动生成自代码、严格的组织级第三方
notice 流程、theme contract/default 改变或 Web 要从 monorepo 完全拆出时，重新评估 workspace/package
边界。仅仅担心 docs 与 code 可能漂移，不足以恢复构建期跨目录依赖。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：Web 本地代码包、合同校验、许可与隔离构建方案已形成，提交人工审核
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准 Web 本地设计系统包、校验与隔离构建方案
