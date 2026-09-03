---
id: "DECISION-018"
type: "decision"
title: "采用 CodeMirror 6 作为后台长内容编辑器基线"
status: "approved"
work: "WORK-033"
owners: ["codex/root"]
depends_on: ["EXPERIENCE-017", "DESIGN-026", "DESIGN-027"]
related: ["WORK-015", "WORK-025"]
implements: []
verifies: []
tags: []
created_at: "2026-09-03"
updated_at: "2026-09-03"
---

# DECISION-018：采用 CodeMirror 6 作为后台长内容编辑器基线

## 要决定什么

后台题目创作中的长 Markdown、长纯文本和代码输入应使用哪一种编辑器基线，以及项目“自行维护”的边界。

## 背景

现有题面用 Textarea、代码用 Monaco、样例用 JSON Textarea，三套交互互不一致。用户提出长文本不宜继续
使用 Textarea，并建议为项目维护符合现有设计系统的 CodeMirror 或其它 Web 编辑器；同时认为 Monaco
更适合用户端写代码而不是后台。

其中方向合理，但两点需要收窄：Textarea 对短而简单的多行纯文本仍是成本最低、原生可访问性最稳定的
选择；Monaco 是否合适取决于功能密度和运行环境，而不只取决于“前台/后台”。当前后台只需 Markdown、
C++ 高亮、搜索和基础编辑，确实不值得承担 VS Code 级编辑器复杂度。

## 候选方案

1. **Textarea + 自制 Markdown 工具栏**：依赖最少，但长内容搜索、行号、语法、代码和扩展一致性不足。
2. **继续 Monaco**：代码能力最强，已有实现；但后台当前使用不到大部分能力，主题接入和响应式成本高，
   且 [Monaco 官方说明不支持移动浏览器](https://microsoft.github.io/monaco-editor/)。
3. **CodeMirror 6 + 项目薄适配（提议）**：官方扩展模型可按需组合 Markdown/C++、搜索、历史和主题；
   [官方支持通过 EditorView 建立编辑器](https://codemirror.net/examples/basic/)，也允许通过
   [EditorView.theme 定制样式](https://codemirror.net/examples/styling/)。
4. **富文本/WYSIWYG**：输入门槛较低，但 Markdown 双向转换会产生格式漂移，不适合当前源码真源。

## 决定

提议采用方案 3：依赖 CodeMirror 6 官方包，在 `src/components/ui` 维护 React 生命周期适配、Cherry
主题、扩展集合和业务可访问性合同；不 fork、不复制、不发布自有编辑器内核。后台长 Markdown和代码使用
该组件，短备注继续允许 Textarea。移除当前后台 Monaco 消费和依赖；未来用户端答题工作台需要 Monaco
时再独立引入与评审。

## 理由

CodeMirror 的能力与当前问题尺寸相符，能够通过扩展按需安装，而不是把完整 IDE 体验塞进后台。项目只
维护稳定的集成边界和视觉合同，仍能获取上游安全、浏览器和输入法修复。它也让 Markdown 与 C++ 共享
外壳、主题、错误和键盘规则，同时避免把所有多行输入都升级成重型编辑器。

## 影响与风险

新增官方依赖和本地适配测试，移除 Monaco 依赖与工具链说明。主要风险是 contenteditable/IME、受控状态
同步、Tab 导航、主题重配和 bundle 误引入；必须通过真实浏览器覆盖，不能只依赖单元测试。

## 重新考虑条件

后台需要语言服务器、完整 IntelliSense、调试、多文件工程或超大文件时重新评估 Monaco；产品改为真正
的所见即所得内容模型时重新评估富文本；若某字段只是短备注，应继续使用 Textarea。

## 变更记录

- 2026-09-03：状态变更：draft → review。原因：已比较 Textarea、Monaco、CodeMirror 与富文本方案，并提出 CodeMirror 6 薄适配决策，等待用户确认
- 2026-09-03：意图闸通过：review → approved。原因：确认后台题目创建与六步编辑体验、CodeMirror 6 编辑器方案并允许实施
