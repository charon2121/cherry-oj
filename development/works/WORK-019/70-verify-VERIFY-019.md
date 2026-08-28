---
id: "VERIFY-019"
type: "verify"
title: "设计 Cherry OJ 任务入口主页"
status: "review"
work: "WORK-019"
owners: ["codex/root"]
depends_on: ["TASK-027"]
related: []
implements: []
verifies: ["FEATURE-002", "TASK-027"]
tags: []
result: "pending"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# VERIFY-019：设计 Cherry OJ 任务入口主页

## 验证对象

验证 TASK-027 实际创建的 Figma 文件、Homepage Frame、局部组件、semantic variables、主题表达、状态
变体和交付链接，以及仓库改动是否严格限于 WORK-019 记录。执行侧机器检查与逐 Frame 视觉检查已完成，
本文件现提交用户人工复核；在人工确认前，front matter 的 `result` 保持 `pending`。

## 对应要求

覆盖 FEATURE-002 的 REQ-001～REQ-016、AC-001～AC-007，以及 TASK-027 的全部完成标准。重点验证：
任务入口定位、状态准确性、双主题、320px、可访问性、组件化、字体和实施范围。

## 检查与结果

Figma 文件：`https://www.figma.com/design/DsRbH32JRiOw4G9RAawzGG`；主 Frame：
`https://www.figma.com/design/DsRbH32JRiOw4G9RAawzGG/Cherry-OJ-%C2%B7-%E4%BB%BB%E5%8A%A1%E5%85%A5%E5%8F%A3%E4%B8%BB%E9%A1%B5?node-id=20-3482`。

| 维度 | 实际证据 | 结果 |
|---|---|---|
| 文件与结构 | 4 Pages；4 collections/207 variables（57/44/53/53）；9 text styles；6 effect styles；6 component sets/34 variants；5 delivery Frames；60 instances | 通过 |
| 主稿 | `20:3482`，1440×1024 User Cherry Black；任务入口、两个动作、四步、快速入口与 Gateway 辅助状态均完整 | 通过 |
| 身份状态 | Guest `20:3580` 不含“我的提交/管理”；States `20:3851` 覆盖首次改密、ADMIN、Session loading/error、Gateway error | 通过 |
| 主题 | User Black `20:3482` 与 User Pure White `20:3664` 采用同一 anatomy 和 Theme variants；两套 single-mode semantic collection 分别 alias 到 primitive | 通过（批准降级） |
| 响应式 | Mobile User Black `20:3762` 为 320×1360；标题、描述、按钮、四步、入口和状态均在 Frame 内，无横向或文字裁切 | 通过 |
| 可访问性 | Verify 检查 focus 2px outline + 2px gap、状态文字/图标双编码、阅读区段顺序和语义颜色；States 实图确认 focus 可见 | 通过 |
| 字体 | 自由文本读回为 Inter family；Code style 使用 JetBrains Mono；Notes 说明中文回退意图与近似字重 | 通过 |
| 完成度 | Verify 扫描 placeholder/shimmer/默认文案、空图标、重叠与溢出；五个完整 Frame 再以 fit-to-selection 人工检查 | 通过 |
| 非目标 | copy/layer 扫描无注册、排行榜、AI、多语言、统计或主题选择器，也没有不存在路由的 prototype reaction | 通过 |
| 范围 | 本任务只写新 Figma Draft、`development/works/WORK-019` 记录及 `development/WORKS.md` 的 WORK-019 状态行；未修改业务实现、设计系统真源、其他 WORK 或用户已有 Figma 文件 | 通过 |

插件 Verify 于 `2026-08-28T10:25:12.859Z` 返回 `pass: true`、`failedChecks: []`，并覆盖 Pages、变量/
样式、组件/实例、精确文案、非目标、布局绑定、focus、区段顺序和 overflow/text。三个需要 1px spread 的
ring/raised-ring effect 使用语义 token 解析后的颜色快照，因为 Figma 会把 variable-bound shadow spread
读回为 0；其 1px 几何与最终视觉已单独核对。临时 Development Plugin 登记随后已移除。

临时插件的最终 `code.js` 通过 `node --check`，`manifest.json` 可解析且 UI 内联脚本可编译。仓库执行
`scripts/work check` 退出码为 `0`，输出为 `✓ 149 份开发文档通过校验（0 个进行中提示）`。

## 未通过项

当前没有执行侧未通过项。以下为执行期间已解决的历史阻塞与处置：

- 2026-08-28，原 Theme 双 mode 不可建立：当前 Full 席位所在团队为 Starter plan，Figma API 在新增第二
  mode 时返回 `Limited to 1 modes only`。测试集合已完整回滚；用户随后明确批准双独立单 mode collection
  降级；最终两套 semantic alias、Theme variants 和双主题 Frame 均通过验证。
- 2026-08-28，远程 Figma MCP 调用配额在 P1.b10 耗尽；该调用未创建 typography variable。此前已创建的
  数据保持原子一致。用户随后授权临时本地 Development Plugin，五阶段构建与 Verify 已完成，插件登记
  已移除；远程 MCP 未重试。

`result: pending` 只表示等待用户人工复核，不表示机器检查失败。

## 范围检查

`git status --short` 显示工作区在 TASK-027 前后均有其他工作产生的业务代码、设计系统与 WORK-017/018
改动；这些内容未由本任务覆盖、清理或归因。本任务的仓库写入限于
`development/works/WORK-019` 与总览中的 WORK-019 状态行，外部写入限于新建 Draft。临时插件源位于
`/private/tmp`，Figma 本地登记已在交付成功后移除。

## 遗留问题

暂无。

## 剩余风险

Starter plan 限制已由用户批准降级解决；剩余结构风险是两个独立 semantic 集合不能用单一 collection
mode 切换，后续维护必须继续通过组件 Theme 变体与 Frame 绑定检查防止漂移。长期风险仍是 Figma 与
代码侧设计系统之间没有自动同步；视觉稿通过也不能证明 Web 已实现或未来不会漂移。若后续落地，必须
由独立 TASK 在当时的代码真源上重新验证双主题和 E2E。

## 结论

执行侧自动验证与五个 Frame 的视觉前置检查通过，TASK-027 已完成；VERIFY-019 进入 review，等待用户
人工复核后再决定 `result`，当前保持 `pending`。
