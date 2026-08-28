---
id: "VERIFY-015"
type: "verify"
title: "建立 Cherry OJ Web 设计系统"
status: "review"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["TASK-021"]
related: []
implements: []
verifies: ["CAPABILITY-005", "TASK-021"]
tags: []
result: "pending"
created_at: "2026-08-27"
updated_at: "2026-08-27"
---


# VERIFY-015：建立 Cherry OJ Web 设计系统

## 验证对象

验证 TASK-021 实际交付的 Foundation/theme contract、默认 `cherry-black`、`pure-white`、manifest、
Tailwind/shadcn adapter、组件/HTML 参考、全局入口、来源许可和任务范围。验证对象是当前工作区中的
`docs/design-system.md`、`docs/design-system/` 以及本任务允许更新的全局文档；不把尚未迁移的
`apps/web` 当作已上线实现。

## 对应要求

覆盖 CAPABILITY-005 的 REQ-001～REQ-010 与 TASK-021 全部完成标准。证据链为：长期规则在
`docs/design-system.md`，数值真源在 Foundation/主题 CSS，语义矩阵在 `theme-contract.json`，主题登记在
`themes.manifest.json`，组件合同在 `components.manifest.json`，生成/校验在 `tools/`，视觉与交互证据在
`components.html` 和四个 preview。截图只用于视觉检查，不代替合同、生成和对比校验。

## 检查矩阵

| 维度 | 必查内容 |
|---|---|
| 选择/回退 | 缺失 `data-theme`、`cherry-black`、`pure-white`、unknown id；预期 black/black/white/black |
| 合同完整性 | schema/type/allowedOn 有效；每个主题精确实现 required key；未知/缺失/跨主题 fallback 均失败 |
| 生成一致性 | manifest 驱动的 `tokens.css`/JSON 与源文件一致，漏登记、手改和 stale output 均失败 |
| 隔离 | 组件、页面、CVA、Tailwind 和派生 HTML 不枚举 theme id、不消费 raw 色 |
| 来源 | 默认黑色非品牌 raw 值对照固定 Linear 快照；差异只在 allowlist；active 紫色无残留 |
| Pure White | canvas/raised 为 `#ffffff`，冷灰层级、brand/status/focus/overlay/shadow 与设计一致 |
| Adapter | 完整 alias 表逐项相等；brand/primary=品牌、accent pair=中性、ring=focus、destructive=danger |
| 对比 | 展开两主题全部 allowedOn 与 opaque soft 组合；文字 ≥4.5:1，必要图形/边界/focus ≥3:1 |
| 组件 | 两主题的桌面/320px、hover/pressed/focus/disabled/loading/error、键盘、长中文、reduced-motion |
| 非颜色 | link 下划线、danger 图标/动词/确认、verdict code+名称+形状 |
| 工程/许可 | JSON/CSS/manifest 一致、链接、Apache、NOTICE、逐文件修改声明、provenance、git 范围 |

## 检查与结果

### 核心生成、合同与对比

在仓库根目录实际运行：

```text
node --check docs/design-system/tools/build.mjs
node --check docs/design-system/tools/check.mjs
node docs/design-system/tools/build.mjs --check
node docs/design-system/tools/check.mjs
git diff --check
```

全部退出码为 `0`。校验器从固定合同展开两个主题的 `56` 个 required key 和 `296` 个 allowedOn
组合；最低值为 pure-white `--ds-border-strong` on `--ds-surface-hover = 3.404:1`，高于必要图形/
边界的 `3:1` 门槛。普通文字最低值仍不低于 `4.5:1`。检查同时固定 exact contract shape、Foundation
基线、两套已批准色值、同主题 alias、opaque surface、63 个 adapter root alias、单一
`@theme inline`、66 个 utility registration、默认/fallback/selectors 和 active Linear 紫色扫描。

在 `/private/tmp/cherry-ds-negative.KRi1Os/` 的隔离副本实际做了四类反向检查，不修改交付物：

- 手改 `design-tokens.json` 后 check 退出 `1`，报告 generated snapshot stale；
- 新增未登记 `rogue.txt` 后退出 `1`，报告 package file 未登记；
- 删除 pure-white 的 `--ds-fg-meta` 后退出 `1`，报告缺 key、无法展开 6 个对比组合且只检查到 290；
- 复制完整 light 映射为新 `snow` 主题，只修改主题 CSS 与 `themes.manifest.json`，运行 build/check
  通过：`56 keys × 3 themes`、`444` 个组合，未修改 package manifest、聚合入口、adapter 或组件。

### 格式、链接、来源与许可

- `JSON.parse` 实际解析 5 个设计系统 JSON 与 `development/index.json`，通过。
- Python `html.parser` 复查当前 `components.html` 和四个 preview：5 个文件均只有一个 `main`，无重复
  id、缺失 fragment 或 ARIA reference；已删除的旧兼容页不计入当前交付。
- TASK-021 实施期的 `scripts/docs_test.py` 曾通过 current-files 只读 Git shim 运行，输出
  `✓ 152 份 Markdown 文档入口和本地链接有效`；后续发布提交误收录 `development/roles/`，并在删除旧
  HTML 后遗漏 5 个引用，已由 WORK-016 作为独立 CI 修复处理。
- WORK-016 将实际修复内容加入 Git index 后直接运行 `scripts/docs_test.py`，输出
  `✓ 156 份 Markdown 文档入口和本地链接有效`，不再依赖 shim。
- OpenDesign `tokens.css`、`DESIGN.md`、源 LICENSE 的 SHA-256 分别为
  `9f99cf1b…d022e`、`4c7264d8…f8fb7`、`9d95806a…b2b0da`；包内 Apache 副本与源 LICENSE 相同。
  Lucide 1.33.0 LICENSE 与包内副本均为 `b495047b…329c57`。
- 包校验递归比较实际文件与 static + manifest-managed theme 文件，逐文件检查
  `Modified for Cherry OJ` 或 JSON provenance/modified；没有 `~/Downloads` 运行时依赖。
- 组件/preview 扫描未发现 raw color、raw token、硬编码 theme id 或 active Linear 紫色；真实 Lucide
  Search、CircleCheckBig、TriangleAlert、Trash2 path 与项目依赖版本一致。

### 浏览器与交互

使用 Codex 内置浏览器和两个仅绑定 `127.0.0.1` 的临时静态服务器，在同一 `1440×900` viewport、同一
顶部状态中同时采集 OpenDesign 来源组件页与 Cherry `cherry-black` 组件页进行视觉对照。结果保留来源的
纯黑画布、冷灰 luminance hierarchy、紧凑排版、细边框和克制圆角；紫色 CTA 被 Cherry 品牌色替代，
未复制来源商标或产品文案。

同一参考页实际切换 pure-white 后，`data-theme=pure-white` 且浏览器计算
`--ds-canvas=#ffffff`。对缺失、空值、显式 black、pure-white、unknown 五种输入逐项导航，最终
`data-theme` 分别为 black/black/black/white/black。在 `320×812` 下分别检查黑/白主题，二者均
`clientWidth=scrollWidth=320`，长中文标题、theme picker、主要结构和状态没有页面级横向溢出。键盘
focus 的浏览器计算值为 pure-white `2px solid rgb(192, 18, 66)`；dialog 点击取消后关闭并把焦点恢复到
触发按钮。另一次 Chromium smoke 覆盖 5 页 × 2 主题、原生 theme select、Enter/Escape dialog、
disabled/selected/focus、320px 和 reduced-motion，全部通过。内置浏览器的合成 Escape 注入没有触发
native dialog 默认动作，但 Chromium smoke 已验证真实 Escape 路径，取消按钮与焦点恢复也在内置浏览器
独立通过。

视觉/级联复核发现并修复了 danger solid 上错误前景、brand anchor CTA hover、skip-link hover 和
danger soft card 子文本四类 allowedOn 漏洞；修复后 reviewer 复查无剩余 P1/P2。

## 未通过项

最终交付物没有未通过项。上述 stale、漏登记和缺 key 是隔离副本中的预期失败，用于证明校验器会拒绝
漂移，不是当前包失败。

## 范围检查

`git status` 与路径扫描确认本任务只改 `CLAUDE.md`、批准的 `docs/` 入口、`docs/design-system*` 和
WORK-015/索引元数据；`apps/web`、服务端、判题引擎、contracts 和 WORK-001～014 均无本任务改动。
工作区原有 `docs/diagrams/judge-environment-model.drawio` 删除和当时未跟踪的 `development/roles/` 保持
原样，不计入 TASK-021；后者被发布提交误收录后的清理由 WORK-016 单独追踪。

WORK-016 删除无消费者的角色文件并修正旧 HTML 引用后，真实根目录运行 `scripts/work check` 已通过：
`✓ 123 份开发文档通过校验（0 个进行中提示）`。

## 遗留问题

本任务没有迁移 `apps/web`。当前 Web 的旧 `:root`/`.dark`、resolver、持久化、首屏防闪、字体和组件消费
仍需独立 WORK/TASK；设计系统发布不等于产品已经提供主题切换。

## 剩余风险

实现已经通过自动、负向、浏览器和独立 reviewer 检查，但 VERIFY 仍提交人工复核，不能由执行者把
自己的结果直接签成 `approved/pass`。剩余工程风险是后续 Web 迁移时绕开本合同，或在新组件 CSS
级联中覆盖已验证的 foreground/surface 配对；必须复用本包 check、双主题 Storybook/浏览器矩阵和
组件 manifest。

## 结论

TASK-021 的实现检查通过，提交人工验证。当前 VERIFY 保持 `result: pending`，待用户检查已打开的双主题
组件参考后决定是否进入 `approved/pass`。

## 变更记录

- 2026-08-27：状态变更：draft → review。原因：自动、负向、浏览器和独立复核证据已记录，提交用户人工验证
- 2026-08-28：WORK-016 清理发布提交误收录文件与失效链接，真实根目录 work check 恢复通过。
