---
id: "MEMORY-023"
type: "memory"
title: "新增页面主题切换入口"
status: "checked"
work: "WORK-029"
owners: ["codex/root"]
depends_on: ["VERIFY-030"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# MEMORY-023：新增页面主题切换入口

## 背景

Cherry OJ 在 WORK-015/WORK-018 中已经交付双主题合同、首屏恢复和 React 运行时，但刻意没有交付生产
入口。WORK-029 只负责把这套既有能力变成所有页面可达的用户功能。

## 决定与原因

采用双端页头独立图标按钮，并从生成主题 registry 计算下一个主题。这样访客也能使用，用户端与
管理端位置一致，也不会把主题偏好错误地绑定到账号菜单或复制一份主题 ID/存储状态。

## 尝试与教训

现有 `useTheme` 的同步 setter 和持久化结果已经足够支撑用户反馈，不需要新全局状态、Effect 或第二存储
位置。控件只需从 registry 计算目标、调用 setter，再根据 `persisted` 播报结果。

新增一个 32px 控件会暴露原本刚好贴边的管理端 320px Header：仅缩小 gap 仍有 9px 溢出。最终在最窄
宽度隐藏“返回用户端”的装饰箭头而保留文字，比隐藏功能入口或缩小触控目标更符合优先级。E2E 必须在
每次构建后运行，否则 Vite preview 会继续测试旧 `dist`，造成修复未生效的假象。

## 已知问题

当前工作不提供账户级或跨设备同步、不跟随系统主题，也没有多主题设置面板。本地存储不可用时主题只在
当前页面会话生效。生产构建仍有既有 Monaco 大 chunk 警告，与本功能无关。

## 重新考虑条件

当产品要求跟随系统、账户同步、跨设备同步、主题数量使循环操作难以使用、默认主题或 theme contract
变化，或页头空间无法继续承载全局按钮时，重新评审入口和偏好模型。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：已记录 registry/ThemeProvider 复用、320px 页头取舍、验证教训与重新评审条件
- 2026-09-02：结构与内容校验通过，由工具置为 checked。
