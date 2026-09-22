# 开发文档系统

这套系统帮助人确认目标与取舍，帮助 Agent 在明确边界内实现，并留下可信的验证证据。
人的默认阅读入口是一份主文档；文档数量不代表工程质量。

`docs/` 保存已经确认、跨工作长期有效的事实；`development/` 保存具体工作的决定与证据。
全部工作见 [WORKS.md](./WORKS.md)，完整规则索引见 [SPECIFICATION.md](./SPECIFICATION.md)。

## 日常使用

新工作默认采用 `format: "compact"`，生成两份 Markdown：

```text
works/WORK-xxx/
├── 00-work.md                  # 主文档：前五节给人审核，后面给 Agent 执行
├── 70-verify-VERIFY-xxx.md      # 交付结果与证据，实施后填写
└── flow.json                   # 工具维护的状态，不需要人工阅读
```

保留既有文件名和永久编号，避免为了简化阅读改变链接。
普通工作不再强制创建 FEATURE、DESIGN、PLAN 或 TASK。五种类型仍表达不同工作语义：
product 写使用场景，infra 写接入与失败行为，fix 写实际/预期与复现，maintenance 写不变量，
improvement 写基线、目标和验证方法；这些内容先写进主文档，需要时再拆分。

```bash
scripts/work new --title "调整页脚" --type product --risk low --impact local \
  --owner team/web --read-path apps/web --write-path apps/web --forbidden-path contracts
scripts/work board WORK-060           # 审核内容与交付结果，直接读取原文
scripts/work board WORK-060 --all     # 展开流程、检查、任务与要求覆盖
scripts/work context WORK-060         # 没有独立 TASK 时，以 WORK 交接
scripts/work overview
```

示例编号应替换为实际创建的 WORK。主文档的 `read_paths`、`write_paths`、`forbidden_paths`
使用仓库相对路径；没有额外禁区时显式写 `forbidden_paths: []`，不能省略范围。

## 你需要审核的内容

`00-work.md` 的前五节是本工作的人工判断依据，不另建需要同步的摘要：

| 章节 | 人要判断什么 |
|---|---|
| 变化 | 谁遇到了什么，完成前后有什么不同 |
| 边界 | 做什么、不做什么、不能破坏什么 |
| 取舍 | 推荐方案及需要接受的代价，没有重要取舍时明确说明 |
| 未知 | 当前假设、未解决问题及对开工的影响 |
| 验收 | 怎样直接判断结果符合预期，使用 `AC-001` 等条目 |

普通工作以一屏为目标，不设为了字数截断风险的硬限制。关键风险、代价与不可逆影响必须呈现，
不能藏在附件链接里。实现步骤、文件路径与测试命令放到「执行方案」或证据中。
第一节使用日常语言；专业词仅在帮助理解结果或取舍时使用。

人的批准只覆盖明确呈现的目标、边界与重要取舍，不表示逐份审查全部技术细节。
附件用于解释与执行；附件中出现新的重要决定，必须先更新主文档并重新取得对应确认。
默认规则没有增加常设实施授权。

## 文档审核与执行授权

初次提出需求授权 Agent 整理主文档、调查和做只读检查。Agent 展示前五节与必要的方案说明后，
由人在后续消息确认并允许执行；意图闸仍只能由人签署。不能从沉默、校验通过或最初的完成请求
推断授权。用户明确只要求文档时，到交付文档为止。

### 人工确认只有两个点

```bash
# 以下两条只由人执行，Agent 可以准备材料，不能代签。
scripts/work gate WORK-060 intent --reason "确认目标、边界与取舍"
scripts/work gate WORK-060 acceptance --reason "确认结果与差异，接受遗留问题"
```

精简工作意图闸要求主文档内容齐备、路径明确、无 blocking_items、至少一个 AC 条目；已经拆出的
上游附件也必须写完。签署记录在 WORK 的 gates 与变更记录中，WORK 保持进度状态，不标成 approved。
附件从 review 经工具校验进入 checked；checked 不表示技术方案已被人逐份批准或已被证明正确。

验收闸要求意图闸通过、WORK 已 implemented、所有有效 TASK 完成、复核完成、全部必需检查通过或有理由地
不适用、所有有效 VERIFY 为 review/approved 且 result=pass，以及主文档的每个 AC 都有锚定证据。
失败、部分通过、未运行的验证不能靠签字变成通过。

撤回仍使用 `gate ... --revoke --reason "..."`。精简工作撤回验收会把 verified 退为 implemented，
证据退为 review；撤回意图前需先撤回验收并将工作退为 todo。实质改变已确认的目标、边界或代价时，
先暂停相关工作并重新确认，不能用修改附件绕开人工判断。

## 执行与交付

没有 TASK 时，直接记录主工作的进度，不再维护另一套相同的任务状态：

```bash
scripts/work set-status WORK-060 ready --reason "意图闸已通过，准备开工"
scripts/work set-status WORK-060 doing --reason "开始实施"
scripts/work set-status WORK-060 implemented --reason "实际实现完成"
```

这几个状态由 Agent 按实际事实记录；它们不会签署任何闸。存在独立 TASK 时仍逐个推进任务，
未完成的任务会阻止主工作声明 implemented。

VERIFY 前四节向人交付「实际结果、承诺差异、验证情况、遗留问题」，最后的「检查与结果」保存
实际命令、环境、输出结论与限制。UI 优先给实际页面效果，修复给复现与前后对比。
证据只保存一次，其他地方引用，不复制测试流水账。

```bash
scripts/work link VERIFY-061 --relation verifies --to WORK-060#AC-001
scripts/work set-status VERIFY-061 review --result pass --reason "已记录实际证据"
scripts/work check-result WORK-060 automated-tests pass --reason "见 VERIFY-061"
scripts/work check-result WORK-060 impact-analysis pass --reason "见 VERIFY-061 的范围复核"
scripts/work set-stage WORK-060 review done --reason "已核对实现与定义、边界和证据"
# 人签署验收闸后：
scripts/work refresh WORK-060
scripts/work check
```

只记录工作实际声明的检查项，查看 `board --all`。没有独立 TASK 也需要复核；不能把同一个 Agent
换个角色称作独立复核。高风险要求独立复核与回退检查，系统级影响要求跨模块回归，concerns 增加专项检查。
风险提高验证强度，不再自动增加 DECISION、PLAN、MEMORY。项目没有生产环境，不生成上线与线上观察阶段。

## 什么时候拆分

| 附件 | 拆分条件 |
|---|---|
| 定义 / EXPERIENCE | 需求或交互较长，需要完整场景或原型；主文档仍保留可供判断的变化与边界 |
| DESIGN | 复杂方案妨碍阅读，或需要独立技术评审 |
| DECISION | 有实质代价、未来需要理解理由的选择 |
| PLAN | 存在多个任务依赖、分批交付或迁移顺序 |
| TASK | 需要独立委派、并行执行或分别验收 |
| MEMORY | 出现会影响未来工作的教训或新结论 |

```bash
scripts/work new-doc --work WORK-060 --type task --title "独立子任务" \
  --depends-on WORK-060 --implements WORK-060#AC-001 \
  --read-path apps/web --write-path apps/web/components --forbidden-path contracts
scripts/work context TASK-133
```

精简工作中，TASK 只能收窄主工作的可读写范围并继承禁区。需要扩大范围时，先更新主文档的方案与边界，
说明理由并取得适用确认，不能先动文件。没有真正的拆分需要时，继续使用主文档。

## 单一来源与长期记忆

状态以元数据与 flow.json 为准，「流程」表、board 和 WORKS.md 都是生成视图。
不要在定义、方案和证据中重复写“当前待验收”等会过期的进度描述。

信息优先级：人工明确确认的决定 → 主文档的目标/边界/验收 → 执行方案及附件 → TASK → 代码与测试
→ 注释 → Agent 推断。实现不能偷偷改写需求。长期、跨工作的事实确认后再进入 docs/；普通日志不强制变成 MEMORY。

`outcome superseded --by WORK-xxx` 标记被替代的结论，`outcome invalidated` 标记被证伪的结论并要求 MEMORY。
它们不改写历史进度。归档、废弃与替代文档仍保留原路径，ID 永不复用。

## 历史格式兼容

没有 format 字段或显式 `format: "layered"` 的工作继续使用原分层规则：00-work.md 只有流程、待确认项、
变更记录；定义与 TASK 独立；意图闸覆盖决定类文档，验收闸覆盖 VERIFY。
旧工作、旧签署和旧证据不批量迁移，也不要求补签。`new --format layered` 仅用于历史格式兼容，日常创建不使用它。
`rebuild-flow` 保持所属格式，不将历史工作隐式改为精简格式。

## 校验与试用

`check` 检查结构、引用、边界与状态一致性；它不能证明需求正确、日志真实或人认真读过材料。
`trace` 查上下游；`audit` 查看检查结论、追踪链和可能多余的拆分；`sync-works` 刷新总览。

先在一个小修复、一个界面变化和一个跨模块工作中试用。观察人能否快速说清“批准了什么”、
能否直接判断交付结果，以及后续 Agent 能否找到正确边界与证据。不要为了试用另外建立日报或评分表。
若改动让现有教程的操作步骤变化，同轮更新实际存在的关联教程。
