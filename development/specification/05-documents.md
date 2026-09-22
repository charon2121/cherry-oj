> 本文是[《智能体协作开发文档系统规范》](../SPECIFICATION.md)的一章。

# 5. 文档体系

## 5.1 文档类型总览

WORK 与 VERIFY 是新工作的默认产物。PRODUCT、FEATURE、CAPABILITY、ISSUE、CHANGE、IMPROVEMENT、
EXPERIENCE、DESIGN、DECISION、PLAN、TASK、MEMORY 按需创建，并兼容历史工作。

## 5.2 WORK：整个体系的入口

compact 正文顺序为变化、边界、取舍、未知、验收、执行方案、流程、变更记录。
前五节是人工审核依据，执行方案与路径元数据服务于 Agent；流程来自 flow.json，不手工维护。
不在正文重复“当前已完成”等状态。WORKS.md、board 读取同一事实，不成为第二份主文档。

历史 layered WORK 仍只有流程、待确认项、变更记录三节；定义与任务继续保留在原文件中。

## 5.3 定义层文档

谁承担面向人的入口，谁就必须用日常语言说明使用者、问题与结果。
compact 的「变化」是入口；拆出的定义沿用各自模板，第一节保持通俗，不抢占主文档的批准语义。
复杂的功能规则可以拆分，但核心变化、边界、代价和验收不能只藏在链接后。

## 5.4 体验与设计层文档

EXPERIENCE 适用于较长交互或接入流程；DESIGN 用于复杂方案或独立技术评审；DECISION 保存重要选择、
备选方案、代价与重新考虑条件。是否拆分取决于实际用途，不由风险等级直接决定。

## 5.5 执行与证据层文档

PLAN 用于依赖、分批交付或迁移顺序。TASK 用于独立委派或分别验收，引用主文档的 AC/REQ，
不重新定义目标；无拆分需要时直接执行 WORK。

compact VERIFY 前四节为实际结果、承诺差异、验证情况、遗留问题，最后一节「检查与结果」存放命令、
环境、结果和限制。尚未执行保持 pending，失败或部分完成分别使用 fail/partial，不能写 pass。
MEMORY 只保存会影响未来工作的教训与结论，不复制流水账。

## 5.6 统一元数据

front matter 每行使用 `字段: JSON值`；共同必填字段由 schema/document.schema.json 定义。
WORK 使用 type=work 与独立 work_type，format=compact 表示精简格式；缺省或 layered 表示历史格式。
精简 VERIFY 也标记 format=compact，以选择交付模板，其余附件继续使用既有类型模板。

## 5.7 永久编号

WORK、VERIFY 及附件分别分配永久 ID，index.json 单调增长，编号不回收。

## 5.8 文档关系

work 记录归属；depends_on 表达依赖，related 表达关联，implements/verifies 可以锚定到 REQ/AC。
依赖精简 WORK 表示等待其意图闸通过，不表示等待整个工作完成，以免 TASK 等待自身工作形成死锁。

## 5.9 工作项目录

每项工作保存在 works/WORK-xxx/。入口固定为 00-work.md，证据仍为 70-verify-VERIFY-xxx.md，
附件使用原有层级前缀和永久 ID。目录保持扁平，flow.json 由工具维护。
归档、废弃或替代不移动文件，以保留链接和上下文。

## 5.10 文档合并与拆分

默认两份 Markdown；不为层级完整或格式审核额外生成材料。
需要拆分时使用 new-doc，主文档引用细节并保留人需要判断的内容。
历史工作不批量合并，既有签署、证据与路径保持原样。

[返回总览](../SPECIFICATION.md)
