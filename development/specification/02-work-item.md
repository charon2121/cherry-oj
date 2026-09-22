> 本文是[《智能体协作开发文档系统规范》](../SPECIFICATION.md)的一章。

# 2. 工作项

## 2.1 什么是工作项

WORK 是一个有目标、边界和验收结果的工作。默认 `format: "compact"`，由主文档同时承载定义与执行方案。
已有工作缺省格式解释为 layered，保留原模板、状态与签署语义。

## 2.2 五种工作类型

| 类型 | 主文档需要说清的内容 |
|---|---|
| product | 使用者、前后变化、关键使用场景与失败行为 |
| infra | 使用者、接入、限制、失败与恢复 |
| fix | 实际与预期、复现条件、证据及尚未确认的根因 |
| maintenance | 当前问题、目标与不能破坏的不变量 |
| improvement | 当前基线、目标与验证办法 |

类型保留语义，不再要求自动生成对应的独立定义文件。

## 2.3 额外关注

concerns 使用 security、privacy、data、performance、reliability、observability、compatibility、
accessibility、cost、release，为工作增加对应的检查结论，不另起一套审批。

## 2.4 风险级别

风险为 low / medium / high / critical。声明数据变化、公共接口变化、安全敏感，或 security、privacy、
data、reliability、release 关注时，工具把最低风险提高至 medium；不可快速回退时最低为 high。
工具只能依据声明的输入升级；Agent 仍须如实评估影响，不得通过少报标记规避检查。

高风险和关键风险要求独立复核与回退检查。风险高不直接要求额外 PLAN、DECISION、MEMORY。

## 2.5 影响面

local / multi-module / system 表示局部、多个模块、整个系统。非 low 或非 local 要求影响分析，
system 要求跨模块回归。影响多远与危险程度分别判断。

## 2.6 当前未知与流程输入

主文档「未知」说明假设及其影响，`blocking_items` 记录会阻止开工的未解决问题。
存在阻塞时先澄清，不固化进实现。没有未知时明确说明，不留占位符。

## 2.7 流程生成顺序

先选择格式，再确定检查要求。compact 使用确认、实施、复核、验证四阶段；layered 按原类型模板和风险
增量生成。重建流程保持当前格式与已记录检查，不进行隐式迁移。

[返回总览](../SPECIFICATION.md)
