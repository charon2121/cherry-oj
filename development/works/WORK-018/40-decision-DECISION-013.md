---
id: "DECISION-013"
type: "decision"
title: "解除 Web 对设计系统文档目录的依赖"
status: "approved"
work: "WORK-018"
owners: ["codex/root"]
depends_on: ["DESIGN-014"]
related: ["DECISION-012", "CAPABILITY-006", "EXPERIENCE-007", "DESIGN-013"]
implements: []
verifies: []
supersedes: "DECISION-012"
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---









# DECISION-013：解除 Web 对设计系统文档目录的依赖

## 要决定什么

决定 Web 运行所需的设计系统资产归谁持有、文档与代码如何保持关系，以及是否继续把自动防漂移置于
前端可独立构建之上。该决定获批后，将正式替代 DECISION-012 中“构建期直接 import docs 真源”的路线；
Base UI、主题状态、共享组件范围和非目标边界不在本次重新决定。

## 背景

DECISION-012 选择直接读取 docs，是为了避免复制 token 后形成两套可手改内容。实施证明生产产物不会请求
docs URL，但人工验收指出更基础的问题：删掉文档源就无法检查或构建 Web。文档结构变化不应成为产品
工程的故障源，避免漂移也不能以反向依赖文档为代价。

当前只有一个前端消费者；主题源码、manifest、合同、生成和校验规模足以形成 Web 内部代码包，但还不
需要发布 npm package。用户进一步明确：日常不需要自动同步，只有真实修改设计系统时才同步文档与代码。

## 候选方案

- **A（推荐）**：在 `apps/web/design-system` 建立自包含代码包。代码侧是 Web 的构建与运行权威，
  docs 只作设计说明；两侧各自校验，真实设计变更在同一 WORK/TASK 中显式同步。
- **B**：建立仓库级 workspace/npm package。可以服务多消费者，但当前引入包版本、workspace 和发布
  生命周期没有收益。
- **C**：保留 docs 真源，在 prebuild 或 generator 中自动复制。普通构建仍需 docs 存在，不满足目标。
- **D**：只复制最终 CSS。构建可暂时通过，但丢失主题注册、可重建性、合同、对比度与许可验证。
- **E**：保持 DECISION-012 的 direct import/symlink。路径依赖不变，已被人工验收否决。

## 决定

**采用方案 A。**

- `apps/web/design-system` 持有 Foundation、主题源码/manifest、theme contract、Tailwind adapter、生成
  CSS、本地 build/check、窄 package manifest、README、来源 NOTICE 和 Apache-2.0 许可证。
- Web 的 CSS、Vite、生成器、源码门禁、自测、package scripts、Storybook 和 E2E 不读取、执行、复制、
  链接或请求任何 docs 资产。
- 代码侧是前端可执行真源；docs 是设计说明和参考。日常 `check`/CI 不比较两棵目录，不提供隐式同步。
- 只有真正改变设计系统时，才在对应 WORK/TASK 中同时修改代码与 docs，并分别验证各自内部合同。
- 本地 checker 保留运行相关的主题完整性、对比度、adapter、生成、provenance/license 门禁；不复制
  reference HTML、preview、组件文档及其专属校验。
- 在不包含 `docs/design-system` 且无旧依赖/构建产物的临时完整仓库中通过全量 Web 命令，是不可替代的
  验收条件。
- 主题行为、组件合同、Base UI、现有页面和“无生产主题切换器/无 OJ 业务组件”边界保持不变。

人工批准四份新上游文档后、TASK-026 进入 ready 前，必须执行以下历史收口，不能让已取消 WORK-017 中
的相反能力、体验、设计和决定继续保持 approved：

```bash
scripts/work deprecate CAPABILITY-006 \
  --reason "WORK-017 未通过自包含验收；新的构建边界由 CHANGE-008 定义"
scripts/work deprecate EXPERIENCE-007 \
  --reason "direct-docs 开发流程已被 DESIGN-014 的本地代码包流程替代"
scripts/work supersede DESIGN-013 --by DESIGN-014 \
  --reason "Web 运行资产必须归属代码侧，设计文档不再参与构建"
scripts/work supersede DECISION-012 --by DECISION-013 \
  --reason "Web 必须在删除 docs/design-system 后仍可独立构建和运行"
```

## 理由

方案 A 直接满足“删除 docs 后前端不受影响”，且没有为单一消费者引入 package 发布治理。它把依赖方向
恢复为文档描述代码、代码独立运行；本地合同与负向 fixture 继续防止 Web 自身漂移。文档与代码可能在
未来分叉是已知维护成本，但用户已明确选择在真实设计变更时同步，而不是让每次构建依赖文档。

B 在出现第二消费者前过重；C/E 没有解决依赖；D 牺牲了设计系统基建的可维护性和质量证明。

## 影响与风险

新增一份 Web 内部设计系统代码源和一套窄化校验器，会产生有意识的“代码与说明分离”。如果未来只改
一侧，评审材料可能暂时落后；通过设计系统 WORK 的同步要求控制，而不是日常技术耦合。校验器迁移若
遗漏规则可能降低可访问性门槛，因此必须用迁移前后的 2/56/296 指标与故意坏 fixture 对齐。

来源 NOTICE 和许可证要随代码/静态分发迁移；只复制 CSS 而把唯一许可留在 docs 不可接受。该决定不
改变用户数据、API、依赖锁或视觉结果，可通过恢复旧接线回退，但旧接线不能作为最终通过状态。

## 重新考虑条件

出现第二个前端消费者、需要独立版本/发布、组织要求从单一机器源生成全部设计文档、Web 完全脱离
monorepo，或现有本地包无法满足合规治理时，重新考虑 workspace/npm package。只有在新的明确决定同时
满足“删除 docs 不影响 Web”时，才可改变当前依赖方向。

## 变更记录

- 2026-08-28：状态变更：draft → review。原因：代码运行权威、文档说明边界与显式同步取舍已形成，提交人工决定
- 2026-08-28：状态变更：review → approved。原因：用户已明确批准代码运行权威、文档说明边界及历史替代动作
