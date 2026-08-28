---
id: "MEMORY-014"
type: "memory"
title: "解除 Web 对设计系统文档目录的依赖"
status: "draft"
work: "WORK-018"
owners: ["codex/root"]
depends_on: ["VERIFY-018"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---


# MEMORY-014：解除 Web 对设计系统文档目录的依赖

## 背景

WORK-017 为避免 token 漂移，让 Web 在构建期直接读取 `docs/design-system`。自动测试、构建和浏览器矩阵
都能通过，但人工验收指出依赖方向错误：删除文档会破坏前端。这说明“生产浏览器不请求 docs URL”和
“前端工程不依赖 docs”是两件不同的事。

## 决定与原因

DECISION-013 已确认：Web 在 `apps/web/design-system` 自持运行源、合同、生成、校验和许可；设计文档
只作说明，不参与普通安装/check/build/Storybook/E2E。未来只有真实修改设计系统时，才在同一 WORK/TASK
中同步代码与文档。这样优先保证代码边界与可构建性，同时保留可审计的人工同步责任。

在 VERIFY-018 通过并获人工确认前，本 MEMORY 保持 draft，不能把建议写成已经生效的长期事实。

## 尝试与教训

- 单一真源不是越集中越好；把人类文档当成编译输入，会把文档生命周期传播成产品故障。
- 生产 bundle 没有 docs 网络 URL 只证明运行时打包成功，不能覆盖干净 install/check/build 的依赖。
- 自包含不能只复制 `tokens.css`：它仍 import Foundation/主题，也需要 manifest、contract、builder、
  checker、NOTICE 和 license 才可维护。
- 验收若保留旧 `dist`，Playwright 的 preview 模式可能假绿；必须排除依赖和所有产物后 fresh build。
- 精简 checker 应删文档 HTML/preview/package registry 职责，但保留主题完整性、对比度、adapter、生成与
  许可负向测试；“不再检查 docs”不等于降低 Web 门禁。
- docs↔code 不做持续比较是明确权衡，不应再用 prebuild copy、symlink 或可选 fallback 偷偷恢复依赖。

## 已知问题

- 本方案有意允许说明与实现暂时漂移；需要未来设计系统 WORK 明确把两侧列入写路径和验收。
- 当前只有单一 Web 消费者，尚未建立 workspace/npm package 版本治理。
- 用户主题切换入口、账户同步、Verdict/Submission/editor/data table 等 OJ 组件仍未交付。
- 代码侧第三方 notice 的仓库完整性可以自动验证，但不代替组织级法律/发布策略。

## 重新考虑条件

出现第二个独立消费者、需要设计系统独立发布/版本化、要求从单一机器源生成全部设计文档、Web 脱离
monorepo、组织级第三方 notice 管线，或 theme contract/default 真实变化时，重新评估 workspace package、
文档生成与同步方式。无论采用何种新方案，都必须保留“删除人类文档不破坏前端”的边界。
