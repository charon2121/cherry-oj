---
id: "VERIFY-022"
type: "verify"
title: "微调双端应用布局页脚"
status: "review"
work: "WORK-022"
owners: ["codex/root"]
depends_on: ["TASK-030"]
related: ["VERIFY-020"]
implements: []
verifies: ["FEATURE-004", "TASK-030"]
tags: []
result: "pending"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---


# VERIFY-022：微调双端应用布局页脚

## 验证对象

TASK-030 交付的管理端 Footer 移除、用户端 Footer 连续表面，以及 WORK-020 全部既有行为回归。

## 对应要求

覆盖 FEATURE-004 的 REQ-001～REQ-009 与 AC-001～AC-005，重点检查：

- 管理端无 Footer/`contentinfo`、无第三网格行和底部占位；
- 用户端 Footer 语义保留，与 Main 同背景且无边框/阴影分区；
- 短页贴底、长页文档流、320px、双主题和 forced-colors；
- WORK-020 的路由、导航、焦点、身份、权限与业务行为无回归。

## 检查与结果

验证环境：macOS，Node `v26.3.0`，npm `12.0.2`，项目锁定的 Playwright Chromium。2026-08-28 实际执行：

- `npm run check`：退出码 0；设计系统生成物、源码门禁与自测试、API 生成一致性、格式、lint、类型和
  Vitest 全部通过，24 个测试文件、92 个测试通过。
- `npm run build`：退出码 0；TypeScript 与 Vite 生产构建通过。
- `npm run storybook:build`：退出码 0；现有 Sidebar/Sheet 组件场景静态构建通过。
- `npm run test:e2e`：退出码 0；Chromium 20/20 通过。新增断言证明管理端没有 `contentinfo`，根网格只有
  Header/Main 两行且 Main 填满视口；用户 Footer 与 Main 同背景、无顶边框/阴影并保持短页贴底。
- 本地浏览器视觉复核：1440×900 用户页面 Main/Footer 计算背景均为透明连续表面，Footer 顶边框 `0px`、
  阴影 `none`、底部为 `900px`；320×720 下保留一个 Footer，横向溢出 `0px`，Footer 底部与视口误差
  `0.5px`，实际截图未见独立面板或内容遮挡。

## 未通过项

暂无自动化未通过项。独立人工验收尚未再次确认，因此本 VERIFY 保持 `review` / `pending`。

## 范围检查

TASK-030 的实现增量只修改两个允许的 App Shell 与 `apps/web/e2e`；流程记录只修改 WORK-022。未修改
设计系统、组件、路由、业务功能、服务端、契约或 docs。`git diff --check` 无空白错误。

## 遗留问题

暂无。

## 剩余风险

自动化和本地视觉复核未发现阻断风险。剩余风险仅为独立人工尚未再次查看最终管理端视觉；其结构、
视口填充、移动导航和业务回归已有带认证 mock 的浏览器断言覆盖。

## 结论

实现与自动化验证通过，提交独立人工验收；在人工确认前不把 VERIFY 置为 `approved/pass`。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：自动化与本地视觉检查通过，提交独立人工验收
