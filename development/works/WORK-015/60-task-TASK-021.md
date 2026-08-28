---
id: "TASK-021"
type: "task"
title: "建立 Cherry OJ Web 设计系统"
status: "done"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["CAPABILITY-005", "DESIGN-012", "DECISION-011", "PLAN-012"]
related: []
implements: ["CAPABILITY-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/README.md", "docs/product.md", "docs/frontend.md", "docs/ui-system.html", "apps/web/README.md", "apps/web/TOOLCHAIN.md", "apps/web/src/styles/globals.css", "/Users/charon/Downloads/open-design-main/LICENSE", "/Users/charon/Downloads/open-design-main/design-systems/linear-app", "development/works/WORK-015"]
write_paths: ["CLAUDE.md", "docs/design-system.md", "docs/design-system", "docs/README.md", "docs/frontend.md", "docs/ui-system.html", "development/works/WORK-015"]
forbidden_paths: ["apps/web", "apps/server", "apps/judge-engine", "contracts", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014"]
created_at: "2026-08-27"
updated_at: "2026-08-27"
---




# TASK-021：建立 Cherry OJ Web 设计系统

## 任务目标

在人工批准上游方案并明确授权执行后，建立自包含的 Cherry OJ 设计系统文档包：Linear-derived
`cherry-black` 为默认、`pure-white` 为完整浅色主题，并用稳定合同支持未来主题；收敛旧视觉合同，
不修改 Web 运行时代码。

## 依据

只依据已获人工批准的 CAPABILITY-005、EXPERIENCE-006、DESIGN-012、DECISION-011 和 PLAN-012。
任何改变默认主题、pure-white 数值、theme contract、Cherry/danger 映射、非 allowlist 黑色值、旧文档
迁移方式或运行时边界的需求都先升级上游文档，不在执行中自行决定。

## 可查看范围

以 front matter 的 `read_paths` 为准。下载目录只读且只作为证据；交付物不得依赖它。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准；只有上游文档获人工批准且用户明确允许执行后才能开始。

## 产出

- `docs/design-system.md` 规范入口。
- `docs/design-system/` 中的 Foundation、schema 完整的 theme contract、`cherry-black`、`pure-white`、
  theme manifest、build/check 工具、生成聚合入口、Tailwind/shadcn adapter、机器 token、组件清单和
  双主题视觉参考。
- Apache-2.0、fixture 来源/非官方说明、集中 NOTICE 与逐文件显著修改声明。
- 更新后的 docs/CLAUDE 入口和旧 UI 合同替代说明。
- VERIFY-015 的格式、完整性、差异、对比、视觉、主题回退、链接、许可与范围证据。

## 完成标准

- [x] CAPABILITY-005 的 REQ-001～REQ-010 全部有可追踪产物和验证。
- [x] 新包不依赖 `~/Downloads`，保留 Apache-2.0、fixture 来源、非官方和修改声明。
- [x] 每个源自 OpenDesign 且被改写的文件自身带显著修改声明；JSON 顶层带 provenance/modified 元数据。
- [x] `tokens.foundation.css` 与 theme contract 角色明确；两个主题完整实现 required key，无隐式
      跨主题颜色 fallback。
- [x] Contract 冻结每个 key 的类型、允许 surface 与对比类别；check 展开 alias 验证全部允许组合。
- [x] Manifest 驱动生成 `tokens.css` 与机器快照；手工漂移、漏登记和 stale output 均使检查失败。
- [x] `cherry-black` 是 `:root` 默认；缺失、空值、显式 black、pure-white、unknown id 行为符合设计。
- [x] 默认黑色的非品牌 raw 值与来源一致；Linear 紫 active 值已由 Cherry 色阶替代；可访问性 semantic
      修正具有记录。
- [x] pure-white 使用 `#ffffff` canvas/raised、规定冷灰层级和可访问的 Cherry/status 映射；亮粉不在
      白底承担文字、必要图形或 focus。
- [x] 组件、页面和 Tailwind 不判断 theme id、不写 raw 颜色；只有 manifest 与主题 CSS selector 识别 id。
- [x] Tailwind/shadcn adapter 只注册一次并覆盖 DESIGN-012 完整 alias 表；品牌 `brand-*`/primary、
      中性 accent pair、ring 与 destructive 不混义。
- [x] accent/danger/verdict 具备非颜色区分；两个主题的正文、控件、焦点和所有规定状态通过门槛。
- [x] Markdown/CSS/JSON/HTML 角色明确且无第二真源；旧蓝紫规则被显式替代。
- [x] 双主题桌面/320px、键盘、长中文、reduced-motion 和内部链接检查通过。
- [x] `apps/web`、服务端、引擎、契约和既有 WORK 未被修改。

## 验证

按 PLAN-012 矩阵执行并在 VERIFY-015 记录真实命令、环境、结果和截图/人工观察。至少包含：

- JSON 解析、required-key/schema/allowedOn 完整性、manifest-driven build 与 CSS/JSON/token 一致性。
- 固定来源差异、Linear 紫残留、raw 色与 theme-id 分支扫描。
- 缺失/black/white/unknown 四种主题选择。
- 两主题全部允许组合的 WCAG 对比、opaque soft 嵌套、组件状态、桌面/320px、键盘 focus、长中文与
  reduced-motion。
- Tailwind/shadcn 完整 alias 对照、内部链接、Apache/逐文件 notice、git 范围和 `scripts/work check`。

不得把未实际运行的检查写成通过；若写近似 OKLCH，必须按浏览器解析后的 sRGB 重算。

## 风险

最易越界的是顺手迁移 `apps/web`、让组件感知 theme id、为 shallow light override 依赖黑色 fallback，
或为了演示修改非 allowlist 黑色值；均禁止。发现合同、许可、对比度或 danger 区分无法满足上游要求
时，将 TASK 保持 todo/blocked 并回到 DECISION，而不是降低标准。

## 执行记录

- 2026-08-27：创建任务。
- 2026-08-27：补全任务边界与完成标准；等待人工批准上游文档和明确执行授权，尚未实施。
- 2026-08-27：按用户确认补充双主题与扩展合同产出/验收；TASK 仍为 todo，未修改全局 docs 或运行时。
- 2026-08-27：状态变更：todo → ready。原因：上游文档已获用户批准，任务边界和完成标准齐备
- 2026-08-27：状态变更：ready → doing。原因：开始在批准范围内建立 docs 设计系统
- 2026-08-27：完成 Foundation、双主题、扩展合同、生成/校验、adapter、组件参考、许可和全局入口；
  实际通过 56-key/296-combination、负向漂移、未来第三主题、链接、浏览器和独立 reviewer 检查，
  `apps/web` 未修改，提交 TASK 完成与 VERIFY 人工复核。
- 2026-08-27：状态变更：doing → done。原因：批准范围内的设计系统文档包、双主题、扩展合同和验证证据均已完成
