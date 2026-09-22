> 本文是[《智能体协作开发文档系统规范》](../SPECIFICATION.md)的一章。

# 9. 文档管理工具

## 9.1 工具边界与模块

scripts/work 使用 Python 标准库管理工作、文档、编号、边界、引用、检查和视图。
自动检查负责材料与状态一致性，不能证明语义正确、测试真实或人工理解。

## 9.2 流程选择器

format=compact 采用四阶段流程并按风险、影响面和 concerns 加检查；缺省或 layered 使用历史规则。
重建保留已记录检查与格式，不自动生成额外文档或迁移历史。

## 9.3 创建与文档生成

new 默认生成 WORK 主文档、VERIFY 证据与 flow.json。可用 --read-path、--write-path、--forbidden-path
声明范围。new-doc 按需添加附件，保留原类型、永久编号和模板；--format layered 只用于历史格式兼容。
常用命令与完整示例以 development/README.md 为单一操作说明。

## 9.4 结构校验

check 校验 Schema 字段、编号、目录、文档归属、章节、引用与依赖环、流程配置及状态、生成视图、
范围与证据。精简 TASK 不能扩大主工作路径或漏掉禁区；精简验收拒绝失败检查及未锚定的 AC。
旧格式继续执行旧规则，不要求回填历史内容。

## 9.5 内容级校验

Agent 检查目标是否明确、主文档是否隐藏取舍、附件是否改变行为、实现是否越界、证据是否支持结论。
不能因为 checked 就宣称技术正确或获得人工批准。

## 9.6 管理动作

set-status 记录实际进度；check-result 记录检查结论；set-stage 记录无 artifact 的复核；refresh 同步事实。
gate 由人签署或撤回两道闸。archive、deprecate、supersede 保留原路径；outcome 保留历史进度。
格式校验通过不会执行 gate。技术检查结论不会因签署而被覆盖。

## 9.7 查询与总览

compact board 默认展示人工审核与交付原文，--all 展开流程、检查、要求覆盖及任务；历史 board 保持原视图。
context 支持精简 WORK 和独立 TASK；overview 展示全局进度与待签闸；WORKS.md 自动生成。
trace 显示引用，audit 揭示可能多余的拆分与证据缺口；有引用不等于内容被认真消费。

## 9.9 最终验收问题

人能否在几分钟内说清批准了什么？能否直接判断交付结果？后续 Agent 能否找到正确边界和证据？
先在小修复、界面变化和跨模块工作中试用，不增加日报、评分或新的审批表。

[返回总览](../SPECIFICATION.md)
