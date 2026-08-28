---
id: "DECISION-012"
type: "decision"
title: "建立 Web 设计系统代码基建"
status: "superseded"
work: "WORK-017"
owners: ["codex/root"]
depends_on: ["DESIGN-013"]
related: []
implements: []
verifies: []
deprecated: "Web 必须在删除 docs/design-system 后仍可独立构建和运行"
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---




# DECISION-012：建立 Web 设计系统代码基建

## 要决定什么

决定 Web 如何消费 docs 设计系统、如何从 manifest 建立首屏与 React 主题运行时、继续使用哪套交互
primitive、首期共享组件范围，以及哪些产品能力和 OJ 组件明确留在后续工作。

## 背景

DESIGN-012 已批准设计值与主题合同，但没有决定 Web 运行时实现。当前 Web 同时存在旧 token、Geist、
`.dark`、Base UI Button 和写着 Radix 的长期前端文档。若只把新 CSS 复制进来，仍会留下多真源、
首屏闪烁和 primitive 双栈；若一次实现全部 OJ 组件，又会把基础设施扩大成业务页面开发。

## 候选方案

- **A（推荐）**：Vite/Storybook 构建时直接 import docs 的 canonical CSS；从 manifest 生成 TS
  registry 与外部首屏脚本；React 使用薄主题模块；继续 Base UI；首期交付通用基础组件与现有消费者。
- **B**：将 CSS/主题 JSON 手工复制到 `apps/web`，应用直接读取副本。目录内自包含，但会形成第二
  数值真源，常规代码评审无法保证同步。
- **C**：先建立并发布独立 design-system npm package。边界理想，但当前只有一个消费者，没有 JS
  workspace/发布流程；会让本次迁移先承担包版本与发布基础设施。
- **D**：浏览器 fetch docs manifest 并动态载入主题。无需生成，但生产依赖文档 URL、网络与异步首屏，
  无法稳定保证无闪烁。
- **Primitive 选择**：继续 Base UI，或迁回 `docs/frontend.md` 写的 Radix。两者都能无样式实现，
  但同时保留两套不可接受。

## 决定

**建议采用方案 A，待人工确认后生效。**

- `docs/design-system/tokens.css` 与 `tailwind-v4.css` 是 Web 构建期直接输入，不复制颜色。
- `themes.manifest.json` 生成 `themes.ts` 和 `public/generated/theme-init.js`；
  `generate:design-system:check` 保证无漂移。
- storage key 固定为 `cherry-oj.theme`；缺失、空、未知、读取异常统一 fallback 到
  `cherry-black`，`data-color-scheme` 只从 manifest 派生。
- HTML 明确默认黑色，外部经典脚本在首屏前应用有效偏好；React 主题模块复用同一 registry，并处理
  写入失败和跨标签页变化。
- 继续使用 Base UI，不安装 Radix 或第二套组件库；批准后更新 `docs/frontend.md` 的旧描述。
- 基础组件范围按 DESIGN-013，现有页面随后迁移；生产端不新增主题选择器。
- editor、data table、submission lifecycle、verdict 等 OJ 业务组件留给后续 WORK。Verdict 开始前先
  修复 manifest 漏 `PE` 的上游问题并建立与契约精确一致的门禁。

## 理由

方案 A 同时满足唯一真源、静态部署、无网络首屏依赖和未来主题扩展。生成物只携带主题 metadata，不复制
数值，因此 docs 仍是唯一视觉合同；外部脚本比 inline 方案更容易符合 CSP，也能在 React 前执行。
Base UI 已是当前直接依赖和首个组件实现，设计系统没有强制 Radix，继续使用的迁移与维护成本最低。
把用户切换入口和 OJ 业务组件留出，可让本 WORK 保持“基建”边界且每个 TASK 都可独立构建、复核和回退。

## 影响与风险

Vite 与 Storybook 需要读取仓库外层 docs；若将来只分发 `apps/web` 子目录，构建会缺源文件，必须先
转为 workspace package。首屏脚本和 React 模块共同依赖生成协议，生成检查必须先于类型/构建。
默认主题切换会影响所有页面视觉；严格源码扫描可能误报 SVG 或测试 fixture，需要小而显式的 allowlist。
Base UI 的长期文档更新是技术路线纠偏，但不改变用户行为。

## 重新考虑条件

出现第二个独立前端消费者、需要脱离 monorepo 构建、严格 CSP 拒绝当前外部脚本、主题需要服务端账户同步、
theme contract/default 改变、Base UI 无法满足键盘/焦点合同，或决定把用户主题切换作为产品能力时，
重新评估 package、bootstrap 和状态所有权。实现 Verdict 前必须先重新审核其枚举与表现合同。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：直接构建期消费、manifest 生成与 Base UI 推荐方案已形成，等待人工决定
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准推荐方案 A、继续 Base UI 及非目标边界
- 2026-08-28：由 DECISION-013 替代：Web 必须在删除 docs/design-system 后仍可独立构建和运行
