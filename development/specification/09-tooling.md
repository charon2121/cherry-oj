> 本文是[《智能体协作开发文档系统规范》](../SPECIFICATION.md)的一章。

# 9. 文档管理工具：生成、校验、查询与状态刷新

## 9.1 工具边界与模块

文档管理工具负责把前八章的确定性规则变成可执行操作。建议模块包括工作项管理、流程选择、模板生成、编号管理、元数据解析、引用管理、文档检查、追踪关系、状态计算、待确认管理、风险管理、任务管理、验证管理、上下文生成、项目查询和项目记忆。

工具不负责替代产品判断，也不负责凭空生成正确内容。它可以发现定义缺少关键章节，但“这个业务规则是否正确”仍需要智能体分析和人的责任判断。

## 9.2 流程选择器

流程选择器至少接受：

```yaml
work_type:
risk:
impact:
concerns:
blocking_items:
```

还可以接受用于自动升级和专项判断的事实：

```yaml
reversible:
data_change:
public_api_change:
security_sensitive:
user_visible:
```

选择器至少输出：

```yaml
workflow:
  - stage: definition
    label: 功能定义
    requirement: required
    status: ready
    status_source: derived
    artifacts: [FEATURE-001]
    checks: [definition, scope]
    source: profile:product
    reason: product 基础流程

required_documents: []
required_checks: []
human_confirmations: []
blocking_items: []
```

初次选择时可以先输出没有永久 ID 的阶段与所需文档类型；创建文档后再把 ID 绑定到 `artifacts`，并根据文档、TASK、VERIFY 和 WORK 事实同步阶段状态。重建流程必须保留文档永久 ID，只重新计算阶段、门禁与 artifact 绑定。

## 9.3 创建与文档生成

创建工作项的典型命令是：

```bash
scripts/work new \
  --title "增加题目搜索" \
  --type product \
  --risk medium \
  --impact multi-module \
  --concern performance \
  --owner team/web
```

创建过程应：

1. 建立独立工作项目录和 `00-work.md`；
2. 分配永久且永不回退的 ID；
3. 识别 WORK Type；
4. 执行风险自动升级；
5. 先应用类型基础模板，再叠加风险、影响面与 concern 规则；
6. 生成所需文档和标准章节，避免生成不适用文档；
7. 自动建立依赖与引用；
8. 自动填写创建时间、更新时间和初始流程状态；
9. 按信息层级生成固定文件名前缀；
10. 为阶段生成 requirement、status、status_source、artifacts、checks、source 与 reason；
11. 允许无文档阶段、多文档阶段和一份文档支撑多个阶段；
12. 更新 `WORKS.md` 中的人类可读工作概况与 `00-work.md` 链接。

向已有工作增加独立文档可使用：

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

## 9.4 结构校验

`scripts/work check` 是文档系统的自动边界，至少检查：

- 元数据格式、必填字段、文档类型与状态是否合法；
- 永久编号是否唯一，文件名中的类型和 ID 是否与元数据一致；
- 工作项目录名是否与 WORK ID 一致；
- `WORKS.md` 是否覆盖全部 WORK、内容是否与元数据一致、链接是否指向固定入口；
- 附属文档是否和所属 WORK 位于同一目录；
- 文件层级前缀是否与文档类型一致；
- 引用对象是否存在，依赖是否形成循环；
- workflow 是否匹配 WORK Type 基础模板；
- 风险、影响面和 concern 增量是否进入 workflow；
- 阶段顺序、必需性、检查项、规则来源与 artifacts 是否有效；
- artifacts 是否存在并且属于当前 WORK；
- 必需文档是否至少关联一个阶段；
- 阶段进度是否与文档、TASK、VERIFY 和 WORK 状态一致；
- 卡点是否解决，要求是否有任务，重要验收是否有验证；
- 已验证任务是否有 VERIFY；
- 文档状态与 WORK 状态是否冲突；
- `development/` 顶层是否重新出现按文档类型划分的目录；
- 工作项目录是否保持扁平并只包含受管理 Markdown。

进行中的工作缺少后续文档可以先提示；一旦 WORK 进入 ready 或更后状态，同样的断链应成为错误。

## 9.5 内容级校验

纯规则无法判断所有语义问题，因此智能体还应检查：

- FEATURE 是否混入大量技术实现；
- `00-work.md` 是否能让非技术读者理解，是否用不必要的专业词替代了日常语言；
- DESIGN 是否擅自改变产品行为；
- TASK 是否超出 DESIGN 与 PLAN；
- 代码是否与上游定义冲突；
- VERIFY 是否真正覆盖验收要求；
- 是否存在没有声明的关键假设。

内容级检查通常给出审查结果，不要求全部转成脚本规则。能稳定转化为确定性规则的部分，再逐步下沉到工具。

## 9.6 管理动作

工具需要支持创建、查看、更新、查询、列出、关联、检查、签署与撤回人工闸、归档、废弃、替代、追踪、生成流程、刷新状态、按当前分类与风险重建流程，以及推进没有独立 artifact 的操作阶段。

常用命令包括：

```bash
scripts/work flow WORK-003
scripts/work list --work WORK-003
scripts/work show FEATURE-002
scripts/work overview
scripts/work sync-works
scripts/work list --needs-human
scripts/work trace FEATURE-002
scripts/work context TASK-004
scripts/work rebuild-flow WORK-003
scripts/work refresh WORK-003
```

状态变化必须带理由：

```bash
scripts/work set-status DESIGN-002 approved --reason "架构复核通过"
scripts/work set-status VERIFY-002 approved --result pass --reason "验收场景和回归检查通过"
scripts/work set-stage WORK-003 review done --reason "复核确认边界未被越过"
scripts/work refresh WORK-003
```

有 artifacts 的阶段只能由文档、TASK 或 VERIFY 的事实推进；`clarify` 由 `blocking_items` 推进。复核等没有独立 artifact 的阶段可以显式推进，但 required 阶段不能标为 skipped，每次变化都必须记录理由并检查前置 required 阶段。

人工确认通过闸签署，一次覆盖该闸的全部文档；决定类与 VERIFY 文档不再逐份 `set-status ... approved`：

```bash
scripts/work gate WORK-003 intent --reason "确认这就是要做的事和边界"
scripts/work gate WORK-003 acceptance --reason "确认已完成，遗留项可接受"
scripts/work gate WORK-003 acceptance --revoke --reason "回归用例没覆盖，重新审"
```

工具只检查前置条件是否齐备并在不满足时拒绝签署，不代替人做判断；记录类文档（DESIGN、PLAN、
MEMORY）由 `refresh` 在校验通过后从 `review` 推进到 `checked`，不进入人工确认。

归档、废弃与替代只改变状态，不把文件移出所属工作项目录：

```bash
scripts/work link TASK-004 --relation implements --to FEATURE-002
scripts/work archive DESIGN-001 --reason "工作已结束，设计仅保留历史参考"
scripts/work deprecate DESIGN-001 --reason "基础假设不再成立"
scripts/work supersede DESIGN-001 --by DESIGN-003 --reason "新方案覆盖旧方案"
```

## 9.7 查询与总览

单个工作项的项目管理视图由 `scripts/work board <WORK>` 提供：闸、流程、要求覆盖、任务、卡点和
下一步集中在一屏。`00-work.md` 的流程表是静态快照，用于在仓库里直接阅读；需要按项目流程管理时
使用 `board`，两者读的是同一份事实。

要求覆盖必须区分「锚定到具体条目」「只有文档级引用」和「无人认领」三种状态。把中间那种算作已
覆盖，追踪链看起来永远是满的，断链就永远不会被发现。

`scripts/work audit` 对流程自身做体检：分级维度是否还产生信息、阶段是否产生过分支、检查项有没有
记录过结论、追踪链断在哪、PLAN 是否在空转。一个自称「文档即开发系统」的东西应该能对自己做同样的
验证——否则它只能增生，不能收缩。

系统至少应能回答：

- 这个工作是什么，为什么做，现在做到哪里；
- 下一步是什么，为什么还不能开发；
- 有哪些卡点、风险和待确认；
- 一个功能对应哪些任务；
- 一个 TASK 实现了哪个要求；
- 一个要求是否有测试和验证；
- 哪些文档已经过期；
- 哪些技术决定影响某个模块；
- 某个模块正在被哪些工作修改。

项目级总览应展示工作项状态分布、卡住数量、高风险数量和待人工确认数量。例如：

```text
当前工作：12

待开始      2
可以开发    3
开发中      4
代码完成    1
验证通过    2

卡住        2
高风险      1
需要你确认  3
```

仓库中的固定总览入口是 `development/WORKS.md`。它以 Markdown 表格展示每个 WORK 的工作概况，并
链接到 `development/works/WORK-<编号>/00-work.md`；命令行 `overview` 继续用于查看动态统计、卡点和
下一步。两者分别服务于浏览阅读和即时查询。

单个 WORK 总览需要快速展示工作内容、原因、状态、实际流程、完成阶段、下一步、卡点、风险、文档、任务和验证程度。

## 9.8 创建结果示例

新建工作项后，工具应给出可以直接行动的结果，而不是只返回文件路径：

```text
工作项：WORK-023

名称：增加项目全文搜索
类型：产品功能
风险：中
影响面：跨模块
额外关注：数据、性能、可观测

需要：
功能定义
体验设计
技术方案
开发计划
验证结果

额外检查：
性能
数据
可观测

当前卡点：无
下一步：完成功能定义并提交人工审核；获得明确授权前不开始开发
```

## 9.9 最终验收问题

任何由本体系管理的工作，都应该能够通过文档、工具和智能体稳定回答：

1. 这是什么工作？
2. 为什么做？
3. 现在处于什么状态？
4. 接下来应该做什么？
5. 哪些步骤必须做？
6. 哪些步骤不需要，哪些可选步骤已经明确跳过？
7. 谁会受到影响？
8. 风险有多大？
9. 会波及哪里？
10. 还有什么没有确认，是否存在 blocking 卡点？
11. 当前智能体可以读什么、改什么？
12. 当前智能体不能动什么？
13. 开发依据哪些定义、体验、方案和决定？
14. 对应哪些任务？
15. 每个要求在哪里实现？
16. 怎样证明它已经完成，验证覆盖了哪些验收项？
17. 未来的人或智能体为什么能够理解当初的决定？
18. 文档是否已经由人审核，当前是否明确获得了进入实施阶段的授权？

如果这些问题可以稳定回答，而且系统没有因为流程本身制造大量无意义文档，这套协作开发文档体系才算真正建立起来。

---

[上一章：人、智能体与脚本](./08-responsibilities.md) · [返回总览](../SPECIFICATION.md)
