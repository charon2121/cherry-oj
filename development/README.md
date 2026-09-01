# 开发文档系统

`development/` 是功能开发过程中产生的结构化文档中心。它以一个 `WORK` 为入口，把功能定义、
体验、技术方案、计划、任务、验证和长期记忆放在同一条追踪链中。

项目文档只有两层：

- [`docs/`](../docs/README.md) 保存已经确认、跨工作项长期有效的全局事实；
- `development/` 保存具体工作从提出到验证和沉淀记忆的过程文档。

完整规范与术语依据保存在 [`SPECIFICATION.md`](./SPECIFICATION.md)（总览与索引）及其
[`specification/`](./specification/) 分章。本 README 只保留仓库当前实现
和开发者需要执行的规则；两者必须一致，出现差异就是需要修复并由测试约束的文档系统错误。
全部工作项的人类可读总览见 [`WORKS.md`](./WORKS.md)；它从各 `00-work.md` 的元数据生成，使用
`scripts/work sync-works` 刷新，不手工维护。

开发过程中形成的结论在仍然只属于一个工作项时留在这里。只有经过确认、会约束多个未来工作项的
事实，才整理进 `docs/`；迁入后，原工作项保留决定与来源链接，不能用改全局文档来掩盖实现偏差。

## 统一模型

每项工作先分类，再由风险、影响面和额外关注选择流程：

```text
WORK
  ├─ FEATURE / CAPABILITY / ISSUE / CHANGE / IMPROVEMENT
  ├─ EXPERIENCE
  ├─ DESIGN / DECISION
  ├─ PLAN
  ├─ TASK
  ├─ VERIFY
  └─ MEMORY
```

五种工作类型：

- `product`：用户能直接感受到的新能力或行为变化；
- `infra`：给系统、开发者、运维或其他模块使用的能力；
- `fix`：实际行为和预期不一致；
- `maintenance`：外部行为原则上不变的重构和维护；
- `improvement`：性能、稳定性、安全、成本或质量等系统性改进。

流程是控制面，文档是产物面，两者不是一一对应：一个阶段可以没有 Markdown 文档，例如开发与复核；一个阶段可以关联多份 TASK 或 VERIFY；同一份 TASK 也同时支撑“任务拆分”和“开发”
阶段。`00-work.md` 的 `workflow` 使用 `artifacts` 保存这种零到多、多到多关系。

五种 WORK 使用独立流程模板：产品使用 FEATURE 与用户 EXPERIENCE；基建使用 CAPABILITY 与开发/
运维 EXPERIENCE；修复使用 ISSUE；重构维护使用 CHANGE，其中包含当前问题、目标状态、不变条件和
影响检查；工程改进使用 IMPROVEMENT。TASK 不重新决定主流程，它继承所属 WORK 的类型与边界。

风险为 `low / medium / high / critical`，影响面为 `local / multi-module / system`。数据库、持久化
格式、公共接口、安全、隐私和不可快速回退的改动会由工具自动提高最低风险；高风险与系统级工作
自动插入技术决策、显式计划、长期记忆、独立复核和回退检查；系统级工作增加跨模块回归。数据、
关键风险仍会留下人工确认项。

流程里**没有上线与线上观察阶段**：项目处于 MVP 开发阶段，没有生产环境，这两个阶段无论怎样推进都
无法完成，只会让「完成定义」对绝大多数工作永远无法闭合。交付与回退方式在开发计划阶段确定，
性能、可靠性、可观测与成本关注在验证阶段用实际证据覆盖。

## 目录

```text
development/
├── README.md
├── SPECIFICATION.md           # 规范总览与索引
├── specification/             # 规范正文，按九章拆分
├── WORKS.md                   # 全部 WORK 的人类可读总览与入口
├── index.json                 # 永不回退的 ID 计数器
├── schema/                    # 统一元数据 Schema
├── templates/                 # 各文档模板
└── works/
    └── WORK-001/              # 目录名只使用永久编号
        ├── flow.json          # 控制面状态，由 scripts/work 维护
        ├── 00-work.md         # 控制面入口：流程视图 / 待确认项 / 变更记录
        ├── 10-feature-FEATURE-001.md
        ├── 20-experience-EXPERIENCE-001.md
        ├── 30-design-DESIGN-001.md
        ├── 40-decision-DECISION-001.md
        ├── 50-plan-PLAN-001.md
        ├── 60-task-TASK-001.md
        ├── 70-verify-VERIFY-001.md
        └── 80-memory-MEMORY-001.md
```

每个 `WORK` 及其全部附属文档必须放在同一个工作项目录中，不再按文档类型拆到全局目录。文件名前两
位是信息层级：`00` 工作入口，`10` 定义（PRODUCT / FEATURE / CAPABILITY / ISSUE / CHANGE /
IMPROVEMENT），`20` 体验，`30` 设计，`40` 决策，`50` 计划，`60` 任务，`70` 验证，`80` 记忆。
同层文件按类型名和永久 ID 获得稳定顺序。层级前缀只负责文档产物的阅读顺序，不表示流程阶段，也
不要求所有层级都出现；文档永久 ID 仍由 `index.json` 分配。

工作项目录固定为 `WORK-<永久编号>`，不附加标题短名或其他 slug。标题与阅读语义统一放在
`WORKS.md` 和 `00-work.md`，因此标题变化不会再造成目录改名、链接变化或任务路径更新。

小型工作可以只使用 `WORK + 定义 + TASK + VERIFY`。工具不会为了凑齐层级生成不适用的文档；同一
层内容需要独立复核或复用时，可以通过 `new-doc` 在所属工作项目录中继续拆分。

## 创建与推进

```bash
scripts/work new \
  --title "增加题目搜索" \
  --type product \
  --risk medium \
  --impact multi-module \
  --concern performance \
  --owner team/web

scripts/work flow WORK-003
scripts/work list --work WORK-003
scripts/work show FEATURE-002
scripts/work overview
```

创建命令会：

1. 分配永久 ID；
2. 执行风险自动升级；
3. 选择快速、标准或完整流程；
4. 叠加风险、影响面和 concern 产生的额外阶段与检查；
5. 生成必需文档与依赖；
6. 将阶段关联到零份、一份或多份 `artifacts`；
7. 在独立工作项目录中按层级命名文件。

创建和状态命令会同步更新 `WORKS.md`。手工改过 WORK 元数据后使用 `scripts/work sync-works`；
`scripts/work check` 会拒绝缺失或已经过期的总览。

## 文档审核与执行授权

人的初始意图只授权智能体整理和修改文档，不自动授权编码、运行迁移、发布或执行其他实施任务。
协作必须分成两个明确回合：

1. 智能体读取上下文，完成本次需要的 WORK、定义、体验、设计、计划和 TASK 文档；上游文档保持
   `draft` 或 `review`，WORK 与 TASK 保持 `todo`，然后停止实施并请人审核；
2. 人在后续消息中明确表示文档通过并允许执行后，**由人签署意图闸**，智能体才把 TASK 推进到可执行
   状态，并开始编码或其他实施动作。

“请完成这个功能”“按这个意图修改”等初始表达不能同时充当文档审核和执行授权。只读检查、文档
校验和为了完成文档本身而运行的管理命令可以在第一回合执行；会修改产品实现、数据、部署或外部
状态的动作必须等待第二回合授权。若人明确只要求改文档，交付文档后就停止。

### 人工确认只有两个点

每个工作只有两次人工签署，其余文档由工具定稿：

```bash
scripts/work gate WORK-003 intent      --reason "确认这就是要做的事和边界"   # 开工前
scripts/work gate WORK-003 acceptance  --reason "确认已完成，遗留项可接受"   # 收束时
```

| 文档 | 终态 | 谁给出 |
|---|---|---|
| PRODUCT / FEATURE / CAPABILITY / ISSUE / CHANGE / IMPROVEMENT / EXPERIENCE / DECISION | `approved` | 人，意图闸一次覆盖 |
| VERIFY | `approved` | 人，验收闸一次覆盖 |
| DESIGN / PLAN / MEMORY | `checked` | 工具，`refresh` 时校验通过后置位 |

`approved` 表示人已经确认，不是智能体对自己文档质量的自评。这两类文档**不能**再逐份
`set-status ... approved`，工具会直接拒绝并提示改用 `gate`。智能体可以准备材料、说明前置条件
已满足、列出待确认项，但不能执行 `gate`，也不能因为测试或格式检查通过就推断已获授权。

闸有前置条件（意图闸：无未澄清 `blocking_items`、覆盖文档已脱离 `draft`；验收闸：意图闸已过、
TASK 全部完成、VERIFY 已记录结论）。不满足时工具拒绝签署并列出原因；**满足也不代表自动通过**。
签错了可以撤回：`scripts/work gate WORK-003 acceptance --revoke --reason "..."`，被覆盖的文档会退回
`review`；若工作状态正建立在该闸之上，需先用 `set-status` 退回工作状态。

`workflow` 中每个阶段同时记录：`label`、`requirement=required|optional`、实际 `status`、进度来源
`status_source=derived|manual`、关联 `artifacts`、阶段 `checks`、规则 `source` 和选择 `reason`。阶段进度使用：

```text
pending → ready → doing → done
             ↘ blocked
optional 阶段还可以是 skipped
```

不适用的阶段不会塞进该 WORK 的流程。文档、TASK、VERIFY、WORK 状态变化后，工具会同步可以由事实
推导的阶段；`refresh` 同时刷新阶段进度与工作状态。修改类型、风险或影响规则后，可执行：

```bash
scripts/work rebuild-flow WORK-003
```

补充一个独立任务：

```bash
scripts/work new-doc \
  --work WORK-003 \
  --type task \
  --title "实现搜索接口" \
  --implements FEATURE-002#REQ-001 \
  --depends-on DESIGN-002 \
  --read-path apps/server/problem-service \
  --write-path apps/server/problem-service \
  --forbidden-path contracts
```

任务进入 `ready` 前必须明确 `read_paths`、`write_paths` 和 `forbidden_paths`。发现必须越界时先更新
上游设计或计划，不能用扩大路径列表偷偷改变范围。

## 状态与证据

工作项状态：

```text
todo → ready → doing → implemented → verified      # verified 是终态
```

任务状态：

```text
todo → ready → doing → done → verified
             ↘ blocked
```

其他文档状态：

```text
draft → review → checked    # 记录类：工具校验通过
              ↘  approved   # 决定类与 VERIFY：人在闸上签署
                 ├─ deprecated
                 ├─ superseded
                 └─ archived
```

状态变化必须带理由：

```bash
scripts/work set-status DESIGN-002 approved --reason "架构复核通过"
scripts/work set-status VERIFY-002 approved --result pass --reason "验收场景和回归检查通过"
scripts/work set-stage WORK-003 review done --reason "复核确认边界未被越过"
scripts/work refresh WORK-003
```

有 artifacts 的阶段由对应文档、TASK 或 VERIFY 状态推进；`clarify` 由 `blocking_items` 推进。只有复核等没有独立
artifact 的操作阶段使用 `set-stage` 手工推进，必做阶段不能跳过。

`refresh` 只根据已经存在的文档、任务和验证事实推导状态，不代替人工产品判断或关键风险确认。
`implemented` 只代表实现完成；只有存在 `result=pass` 的已确认 `VERIFY`，工作项才会变成 `verified`。
WORK 进入 `ready / implemented / verified` 时，对应边界之前的全部必做阶段必须已经完成。

## 校验、查询和上下文

```bash
scripts/work check
scripts/work overview                 # 全部工作：状态分布、卡点、待签的闸
scripts/work board WORK-021           # 单个工作：闸、流程、要求覆盖、任务、下一步
scripts/work board WORK-021 --all     # 展开全部要求条目
scripts/work list --type work
scripts/work list --needs-human
scripts/work trace FEATURE-002
scripts/work context TASK-004
scripts/work audit                    # 对流程自身体检：哪些门禁从未拦过东西
```

`board` 是**以项目管理方式看单个工作项的入口**。`00-work.md` 里的流程表是静态快照，`board` 才是
视图：它额外回答文件回答不了的那个问题——**这个工作要成立哪些事，成立了几件**。

要求覆盖分三种状态，不能混为一谈：`✔` 有 TASK / VERIFY 锚定到具体条目（`ISSUE-004#AC-001`）；
`~` 只有文档级引用（`ISSUE-004`），说明这份定义有人管，但不保证每条要求都被覆盖；`✖` 无人认领。
把后两者当成一回事的话，追踪链看起来永远是满的。

`check` 是 CI 边界，校验元数据、永久编号、文件位置、状态、引用、局部要求编号、依赖环、类型流程
模板、风险增量、阶段进度、artifact 归属、流程必需文档、任务范围和验证证据。它还会拒绝旧类型
目录、带 slug 的工作项目录、过期的 `WORKS.md`、错误的层级前缀、不在所属工作项目录中的附属文档，
以及工作项目录内的嵌套目录或非 Markdown 文件。进行中工作缺少后续文档会给提示；一旦进入
`ready` 或更后状态，同样的断链会成为错误。

`trace` 深度优先展开引用关系，关系与它指向的文档打在同一行；重复出现的节点折成一行摘要，
避免结构被回边淹没。

`context` 从一个 TASK 组装恰好够用的智能体上下文：项目规则入口、工作项、上游定义与设计、相关
决定和记忆，以及该任务的代码读写边界。它不把整个仓库无差别塞给智能体。

`audit` 是这套系统对自己的检验。§1.3 要求「复杂度应当来自工作本身，而不是来自管理工具」，但此前
没有任何机制检查这句话有没有被违反——流程只能增生，不能收缩。它回答：哪个维度已经不产生信息、
哪些阶段从未产生过分支、哪些检查项从来没有记录过结论、追踪链断在哪、哪些 PLAN 在空转。

## 结论不再成立的工作

工作状态表达「进展到哪」，`outcome` 表达「结论还算不算数」。这两件事必须分开：一个被后续工作
撤回的工作**确实**走完过流程、**确实**通过过验证，把它改成 `cancelled` 是篡改历史。

```bash
scripts/work outcome WORK-010 superseded --by WORK-012 --reason "运行时实现已整体撤回"
scripts/work outcome WORK-007 invalidated --reason "前提被证伪"      # 必须留下 MEMORY
scripts/work outcome WORK-010 --clear --reason "撤回判断有误"
```

`superseded` 必须用 `--by` 指出接替者；`invalidated` 用于没有接替者的证伪，且**强制要求该工作有一份
MEMORY**——「当时为什么判断错」是这类工作唯一的产出。没有产出的工作（还没到 implemented）不能标记
outcome，那种情况用 `cancelled`。

标记会出现在 `WORKS.md` 的状态列、`overview` 的独立分节、`board` 的顶部提示和 `trace` 的关系图里。
不进视图的字段等于不存在。

关系管理与历史操作：

```bash
scripts/work link TASK-004 --relation implements --to FEATURE-002
scripts/work archive DESIGN-001 --reason "工作已结束，设计仅保留历史参考"
scripts/work deprecate DESIGN-001 --reason "基础假设不再成立"
scripts/work supersede DESIGN-001 --by DESIGN-003 --reason "新方案覆盖旧方案"
```

编号由 `index.json` 单调分配。文档删除、废弃、替代或归档后，旧编号都不会复用。
归档只改变文档状态，文件仍保留在原工作项目录中，以免破坏上下文聚合和层级顺序。

## 信息优先级与权限边界

发生冲突时按以下顺序处理：

```text
人工确认的决定
→ 功能/能力/问题/改动/改进定义
→ 体验、技术方案和技术决策
→ 开发计划
→ 开发任务
→ 代码与测试
→ 代码注释
→ 智能体推断
```

下游发现上游定义错误时提出改动升级：技术路线变化先更新 `DESIGN`，用户可观察行为变化先暂停实现
并更新 `FEATURE`。复核默认只报告问题；验证必须记录实际命令、环境、结果、遗留问题和剩余风险。

`00-work.md` 和 `WORKS.md` 的主要读者是产品经理及其他不需要了解实现细节的人。能用日常语言说明
清楚时，不使用专业词；确实需要专业词时，在第一次出现时用一句话解释它对使用者意味着什么。
类名、字段名、框架、协议、数据库表、代码路径和命令等实现细节，原则上放到 `DESIGN`、`PLAN` 或
`TASK`。`00-work.md` 优先回答“为什么做、完成后会有什么变化、怎样算成功、可能影响谁”。
