---
id: "MEMORY-012"
type: "memory"
title: "建立 Cherry OJ Web 设计系统"
status: "draft"
work: "WORK-015"
owners: ["codex/root"]
depends_on: ["VERIFY-015"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-27"
updated_at: "2026-08-27"
---

# MEMORY-012：建立 Cherry OJ Web 设计系统

## 背景

用户要求以本地 OpenDesign Linear fixture 为基础建立 Cherry OJ 设计系统，并将 Linear 紫色替换为
Cherry 色。用户随后确认：Linear-derived 黑色必须保留并作为默认；新增 pure-white 浅色；架构要允许
未来主题扩展。该 fixture 是 curated bundled sample，不是官方 Linear 上游。方案已经获用户批准并按
TASK-021 落为 `docs/design-system.md` 与自包含的 `docs/design-system/` 包；当前 MEMORY 记录实际实现
教训，待 VERIFY 获人工通过后再批准为长期记忆。

## 决定与原因

采用共享 Foundation + 完整 semantic theme contract + `cherry-black`/`pure-white` 两套完整映射 +
单次 Tailwind/shadcn adapter。`cherry-black` 由 `:root` 提供固定默认与未知值回退，`pure-white` 显式选择；
组件只消费 `--ds-*`，主题 id 只出现在 manifest 与主题 selector。新增主题文件由
`themes.manifest.json` 动态登记，build 生成聚合入口和机器快照，check 比较实际文件与有效登记集合。

这种分层保留 Linear 的字体、度量、暗色层级与组件语言，同时让浅色和未来主题只替换语义映射。组件
永远不识别 theme id，避免每新增主题就扩大组件分支。

## 尝试与教训

只替换一个 `primary` 不能形成设计系统：来源 brand accent 同时承担 CTA、link、selection、focus 和
状态；shadcn 的 `accent` 又是中性 hover surface，二者不能同名直连。Canonical `--ds-*` 与独立 adapter
可以避免该语义冲突；品牌 canonical 必须使用 `brand-*`，并显式补齐 shadcn 中性
`accent`/`accent-foreground` pair 和其余 alias。

暗色颜色也不能机械复用到白底：`#f9667a` 对白只有 `2.913:1`，pure-white 的 link/focus 必须使用
`#c01242`；Linear 黄 `#eab308` 在白底不能作小字。Status 必须拆 foreground/surface/border/solid/
on-solid。Pure-white 也不等于所有 surface 都白：`#ffffff` canvas/raised 配合
`#f7f8f8/#f5f6f7/#f3f4f5` 才能保留 Linear 式 luminance hierarchy。

来源暗色本身有两个语义风险：`#62666d` 对 `#191a1b` 不够支撑小号 metadata，`#dc2626` 也不够作
暗底普通 danger 文本；raw 值应保留，semantic 角色分别映射到可访问值。对比证据必须按实际 sRGB
组合计算，不能用未解析的近似 OKLCH 或四舍五入后的临界值代签。暗色 soft 若使用透明色，叠到 hover
surface 后会改变对比，因此可承载文字的 soft token 必须使用已验证的 opaque 色并展开所有 allowedOn
组合。Theme manifest 也不会自行让浏览器加载 CSS；必须由它驱动生成聚合入口并检查 stale output。

合同校验不能从待校验合同自身推导“预期”：早期 check 会按当前 allowedOn 数量自算 expected count，
若有人删掉组合仍可能自证通过。最终校验器把 56-key type、contrastClass、allowedOn、opaque、门槛与
Foundation 基线固定下来，再展开两主题 296 个组合；同时以隔离副本证明 stale、漏登记与缺 key 必然
失败。文件登记也要兼顾扩展性：static package files 与 manifest-managed theme files 取并集后对磁盘
做双向精确比较，才能既拒绝 rogue 文件，又让新主题不必修改 package manifest。

静态 token 对比通过仍不足以证明组件可用。CSS 级联曾让 `<a class="button--primary">` 的 hover 被全局
`a:hover` 改成 link color，也曾让 danger solid 的必要边界引用 danger foreground；单看 token 名称列表
不会发现实际 selector 胜负。组件参考必须在两个主题中检查 computed foreground/background、hover/
pressed/focus 和不同元素类型，并为 surface/on-surface 配对显式锁定前景。品牌与 danger 相近时，真实
Lucide Trash2、明确危险动词和确认 dialog 是颜色之外的必要编码。

视觉复核要把来源与改造页放在相同 viewport/状态中一起判断；320px 还要检查 document scrollWidth，
不能只看一张“看起来正常”的截图。自动校验、浏览器交互和独立 reviewer 分别覆盖不同风险，三者不能
互相代替。

## 已知问题

尚无正式 Cherry Logo/品牌手册。Web 运行时仍使用现有字体与 `:root` 浅色/`.dark` 深色合同，尚未接入
默认黑色、pure-white、resolver、持久化或首屏防闪；这是后续迁移任务，不影响本文档包成为未来设计
基线。旧蓝紫文档真源已收敛，但当前产品界面不能宣称已经上线新主题。

发布提交曾误收录 `development/roles/`，导致真实根目录的 work 校验失败；WORK-016 已删除这两份没有
消费者和 Schema 的角色说明，且未放宽开发文档目录规则。`docs/diagrams/judge-environment-model.drawio`
的删除仍是 TASK-021 之外的独立变更，不构成设计系统决定。

## 重新考虑条件

正式品牌规范到来、用户测试混淆品牌/danger、对比度不达标、theme contract 需要
删除或改义、默认主题改变、Linear fixture 更新或 Web 技术栈变化时重审。实现既有合同的新主题属于
兼容扩展；若新增主题无法只靠完整 CSS + themes manifest + build/check 通过，说明扩展合同已经回归，
不能在组件内增加主题分支来掩盖。
