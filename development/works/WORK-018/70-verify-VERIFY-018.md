---
id: "VERIFY-018"
type: "verify"
title: "解除 Web 对设计系统文档目录的依赖"
status: "review"
work: "WORK-018"
owners: ["codex/root"]
depends_on: ["TASK-026"]
related: []
implements: []
verifies: ["CHANGE-008", "TASK-026"]
tags: []
result: "pending"
created_at: "2026-08-28"
updated_at: "2026-08-28"
---





# VERIFY-018：解除 Web 对设计系统文档目录的依赖

## 验证对象

CHANGE-008 与 TASK-026 的实际实现：Web 本地设计系统包、CSS/Vite 接线、主题生成、源码与合同门禁、
许可分发、说明文档，以及在真正缺少 `docs/design-system` 时的安装、检查、两类构建和浏览器行为。
验证重点是构建边界独立，不重新批准视觉值或产品功能。

## 对应要求

- REQ-001～REQ-004 / AC-001～AC-002：所有可执行设计系统输入位于 Web，本地合同与生成门禁完整。
- REQ-005 / AC-001：日常命令不比较/复制 docs，源码、配置和产物无 docs 依赖。
- REQ-006 / AC-003～AC-005：正常与删除 docs 的干净环境均通过完整工程/行为矩阵。
- REQ-007 / AC-006：来源、许可证与静态分发合规材料不依赖 docs。
- REQ-008～REQ-011 / AC-005～AC-006：主题、组件、业务、依赖与 monorepo 既有边界不变。

## 检查与结果

### 环境与正常工作区

2026-08-28 在 macOS、Node `24.19.0`、npm `11.19.0`、Playwright `1.62.1` 与 Google Chrome for Testing
`151.0.7922.34` 下，从当前 `apps/web/package-lock.json` 执行：

```text
npm run check
npm run build
npm run storybook:build
npm run test:e2e
npm run dev -- --host 127.0.0.1 --port 5183 --strictPort
```

前四个自动命令退出码均为 `0`；Vite smoke 完成后用 SIGINT 清理开发服务器：

- 本地设计系统 build/check current，报告 `2` 个主题、每主题 `56` 个 required key、`296` 个允许对比
  组合；完整精度最低值为 `3.4035078594052393:1`（`pure-white` 的 `--ds-border-strong` on
  `--ds-surface-hover`），三位小数为 `3.404:1`。
- package checker 的 `17` 个负向 fixture 全部被准确拒绝并恢复；覆盖错误但仍满足对比阈值的 token、
  缺失/额外 token、`allowedOn` 替换、alpha、低对比、错误 selector、注释伪装、重复/错误
  `color-scheme`、contract `required`/`opaque` 类型与语义、额外 rule/manifest 文件、stale adapter/生成物、
  越界和 symlink 输入、NOTICE/许可证篡改。
- Web source checker 拒绝并恢复 `14` 个源码违规 fixture、`1` 个扫描树 symlink、`1` 个生成漂移、`3`
  个 generator 路径 symlink；`4` 个合法 fixture 无误报。实际源码/配置无跨 docs 路径、raw color、旧主题
  分支或状态透明度违规。
- `npm run check` 随后通过 OpenAPI 漂移、Prettier、ESLint、TypeScript 与 Vitest：`22` 个测试文件、
  `90` 个测试全部通过。
- production build 转换 `2085` modules；Storybook `10.5.10` 静态构建转换 `2236` modules；两类产物
  均不含设计文档路径或 URL，并都分发与包内逐字一致的 `legal/NOTICE.md` 和
  `legal/LICENSE.open-design`。
- Playwright `19/19` 首轮通过、无跳过；覆盖首帧主题、存储异常、跨标签页收敛、reduced motion、
  forced colors、组件状态、两主题 320px、登录/改密/用户管理/权限和 404 回归，页面请求中无 `/docs/`。
- Vite `8.2.2` 在 `728 ms` 就绪；首页、`src/styles/globals.css`、`generated/theme-init.js` 和
  `legal/NOTICE.md` 均返回 HTTP `200`，没有 5xx、资源 404 或 docs 请求，随后服务器已终止。

### 删除设计文档后的独立工作区

另创建 `/private/tmp/cherry-oj-work018-final.HE36Nb`，复制完整仓库但明确排除 `.git`、
`docs/design-system`、所有 `node_modules`、`dist`、`storybook-static`、测试结果、报告、coverage 与缓存；
开始前断言设计文档目录和旧产物均不存在、本地设计系统全树无 symlink。随后从 lock 执行：

```text
npm ci --no-audit --no-fund
npm run check
npm run build
npm run storybook:build
npm run test:e2e
npm run dev -- --host 127.0.0.1 --port 5184 --strictPort
```

- `npm ci` 从 lock 干净安装 `789` packages，退出码 `0`；lock SHA-256 为任务前后相同的
  `16cd1e73427f992dbc7f8dca1766220a0ced517aed08ef31850f1b523bf88728`。
- check 重现 `2` 主题、`56` keys、`296` 组合、完整最低值 `3.4035078594052393:1`、全部负向 fixture、
  `22` 文件/`90` 测试；production/Storybook 再次分别转换 `2085`/`2236` modules。
- fresh build 后 Playwright 再次 `19/19` 通过。隔离 Vite `8.2.2` 在 `667 ms` 就绪；首页、CSS、启动脚本
  和 NOTICE 均返回 HTTP `200`，响应正文无 docs 路径，探针后服务器已终止。
- 最终扫描确认 `docs/design-system` 仍不存在，`apps/web/design-system` 没有 symlink，`dist` 与
  `storybook-static` 无 docs 路径；包内、`public/legal` 和两类构建产物中的 NOTICE/许可证逐字一致。

### 一次性迁移与独立复核

Foundation、聚合 tokens、Tailwind adapter、theme contract、theme manifest、两份主题和 Apache-2.0
许可证共 `8/8` 项与批准实施时的文档源 byte-exact；文档中的 token 值与工具未改。两名独立复核者分别
审查 TASK 边界和 checker 抵抗绕过能力；修复上述精确性问题并复跑 exploit 后，最终均无剩余 P1/P2。

## 未通过项

正常工作区、无设计文档隔离工作区、合同负向测试、浏览器行为和独立复核均无未通过项。Vite 对
`src/routes/index.tsx` 的 `HomePage` 导出给出既存 code-split 优化提示，不影响退出码、资源或行为，也不由
本工程边界重构顺手修改路由 API。

## 范围检查

已按 TASK-026 的 read/write/forbidden paths 对照实施前基线、`git status --short`、路径检索、hash 与逐字
比较：TASK-026 只修改允许的本地设计系统包、接线/门禁/E2E、法务分发和说明文档。docs 下仅更新
`README.md` 的职责说明，设计数值、主题、contract、manifest、工具和许可源未改；CI、theme runtime、
组件、routes/features、API、服务端、判题和 contracts 均没有本 TASK 写入。

共享工作区中的组件/页面/theme runtime 与 `package-lock.json` 差异来自已批准并先行完成的 WORK-017，
不是 TASK-026 写入；锁文件在 TASK-026 开始与结束的 SHA-256 均为
`16cd1e73427f992dbc7f8dca1766220a0ced517aed08ef31850f1b523bf88728`。源码门禁还显式拒绝
`docs/design-system`、`docs//design-system` 与 `docs/./design-system` 等规范化变体，并拒绝扫描树 symlink。

根目录 `scripts/work check` 输出 `✓ 149 份开发文档通过校验（0 个进行中提示）`，`git diff --check`
无输出且退出码为 `0`。由于 WORK-017～019 尚未进入真实 Git index，最终链接检查在隔离完整副本中执行
`git init`/`git add -A` 后运行 `python3 scripts/docs_test.py`，输出
`✓ 182 份 Markdown 文档入口和本地链接有效`；没有修改用户的真实 Git index。

## 遗留问题

- 用户可见主题切换器、账户同步、OJ 业务组件、Verdict manifest 的 `PE` 漂移和 Router 版本差异继续
  属于 WORK-017 已明确排除的后续事项。
- Web 继续在 monorepo 中读取 `contracts/` 做 OpenAPI 检查；本工作不承诺单独复制 `apps/web` 即可构建。

## 剩余风险

即使删除 docs 的技术验证通过，未来设计修改仍可能只更新代码或只更新说明。这是 DECISION-013 明确接受
的流程风险，依靠相应 WORK/TASK 同步而不是持续跨树耦合。自动检查也不能代替第三方许可的法律意见或
人工视觉评审；VERIFY 只记录仓库内可证明的完整性与分发事实。

## 结论

TASK-026 已满足自动验收：删除 `docs/design-system` 后，Web 仍能从干净安装完成生成、检查、两类构建、
19 项浏览器回归与真实 Vite 资源请求；代码门禁、合同精度和许可分发未削弱。现提交人工复核，按项目规则
保持 `result: pending`；本结论不代表生产发布或线上观察。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：正常与删除 docs/design-system 的干净安装、检查、构建、E2E 和 Vite smoke 证据已记录，提交人工复核
