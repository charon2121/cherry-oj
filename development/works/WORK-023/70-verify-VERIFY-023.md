---
id: "VERIFY-023"
type: "verify"
title: "设计双端导航栏与导航功能组件"
status: "review"
work: "WORK-023"
owners: ["codex/root"]
depends_on: ["TASK-031"]
related: ["VERIFY-020", "VERIFY-022", "WORK-020", "WORK-022"]
implements: []
verifies: ["FEATURE-005", "TASK-031"]
tags: []
result: "pending"
created_at: "2026-08-29"
updated_at: "2026-08-29"
---


# VERIFY-023：设计双端导航栏与导航功能组件

## 验证对象

TASK-031 实现的用户端 Header/主导航/账号菜单、管理端 Header/Sidebar/移动 Sheet、shadcn Dropdown Menu
覆盖及其对既有身份、权限、路由、Footer 和设计系统合同的兼容性。

## 对应要求

覆盖 FEATURE-005 的 REQ-001～REQ-017 与 AC-001～AC-007，以及 TASK-031 的全部完成标准；重点复核
“已交付才显示”、三种身份、首次改密、退出失败、Dashboard 双入口、共享菜单模型和 320px 可用性。

## 检查与结果

- `npm run check`：通过；27 个测试文件、97 个测试全部通过，同时通过格式、ESLint、TypeScript、API
  生成一致性与设计系统门禁。
- `npm run build`：通过；Web 生产构建完成。
- `npm run storybook:build`：通过；Dropdown Menu、账号菜单及导航状态 Story 可静态构建。
- `npm run test:e2e`：通过；Chromium 21 个场景全部通过，覆盖访客导航、三种身份、账号动作、退出失败/
  重试、管理双入口与既有 Shell 回归。
- 应用内浏览器视觉检查：通过；检查桌面和 320px 下的用户 Header、移动 Sheet、账号菜单、退出失败、
  长用户名及管理二级侧栏，未发现横向溢出、入口重复或当前态层级问题。
- `git diff --check`、`scripts/work check` 与隔离索引下的文档链接检查：通过。

## 未通过项

无自动化或本地视觉检查失败项。

## 范围检查

已对照 TASK-031 `write_paths` 检查：未新增业务路由/API，未修改设计系统真源，未引入 Radix 或全局
状态；WORK-021 的既有本地修改保持原样，未纳入本任务实现。

## 遗留问题

尚待用户进行独立人工验收。

## 剩余风险

当前只有首页与少量管理路由，未来增加真实业务页时仍需按共享模型接入并补充可达性回归；本次未修正
既有 `/admin/dashborad` 拼写。

## 结论

TASK-031 的实现、自动化检查和本地视觉检查通过，提交独立人工验收；在用户确认前保持 `review/pending`。

## 变更记录

- 2026-08-29：建立导航功能验证矩阵，等待 TASK-031 获批并实施。
- 2026-08-29：完成自动化、构建、E2E、范围及本地浏览器视觉检查，提交独立人工验收。
- 2026-08-29：状态变更：draft → review。原因：自动化与本地视觉检查通过，提交独立人工验收
