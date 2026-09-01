# 智能体协作开发文档系统规范

版本：0.2

状态：草案

本规范定义一套适合人和智能体共同开发软件的项目文档系统，也作为后续文档管理工具的需求依据。

它关注的不是“怎样多写几份文档”，而是怎样把人的意图、工程设计、智能体执行、验证证据和长期记忆连成一套可管理、可检查的开发系统。

**本文是总览与索引**，只保留全局约定、术语速查和章节导航；每一章的完整条款拆分在
[`specification/`](./specification/) 目录下。九章描述的是同一个闭环，不应被理解成九套彼此独立的规则。

---

## 章节

| 章 | 标题 | 回答什么 |
|---|---|---|
| 1 | [核心定位](./specification/01-positioning.md) | 为什么文档本身就是开发系统的一部分：要解决的问题、统一闭环、流程的目的与基本行为准则 |
| 2 | [工作项](./specification/02-work-item.md) | 统一入口与流程决策：五种工作类型、额外关注、风险级别、影响面、当前未知与流程生成顺序 |
| 3 | [信息分层](./specification/03-information-layers.md) | 内容边界与决策权：六个必须分开的问题、信息优先级、任务读写边界、三种改动升级与实现偏差 |
| 4 | [差异化流程](./specification/04-workflows.md) | 从基础模板到实际 workflow：控制面与产物面、阶段必需性、五类工作默认流程、快速与强制完整流程 |
| 5 | [文档体系](./specification/05-documents.md) | 从定义到证据的产物：文档类型、各层职责、统一元数据、永久编号、文档关系与工作项目录 |
| 6 | [状态与关卡](./specification/06-status-and-gates.md) | 用事实定义“做到哪里”：三类状态机、状态推导、各层关卡与完成定义 |
| 7 | [追踪、复核与验证](./specification/07-tracing.md) | 让每项要求都有证据：追踪链、局部要求编号、待确认项、风险点、验证矩阵与项目记忆 |
| 8 | [人、智能体与脚本](./specification/08-responsibilities.md) | 责任和写权限：三方边界、逻辑角色、默认写权限、任务上下文包与人工确认 |
| 9 | [文档管理工具](./specification/09-tooling.md) | 生成、校验、查询与状态刷新：工具边界、流程选择器、结构与内容校验、管理动作与查询 |

## 全局约定

以下几条贯穿全部章节，改动任何一章都不能与它们冲突。

- **工作项是唯一入口。** 所有开发从 WORK 开始，工作类型决定主流程，风险、影响面和 concern 只追加
  阶段与关卡，不另起一套流程。
- **流程是控制面，文档是产物面。** 阶段与文档是零到多、多到多的关系，一份文档可以支撑多个阶段，
  阶段也可以没有文档。不要为了填满阶段而制造文档。
- **状态由事实推导。** 工作和阶段的进度来自文档、TASK 与 VERIFY 的真实状态，不由人手工声明。
- **人工确认收拢到两道闸。** 每个工作只保留意图闸与验收闸两次人工确认；智能体不能代签任何一道，
  也不能从格式校验通过、测试全绿或最初的完成请求中推断授权。详见
  [第 8 章](./specification/08-responsibilities.md)。
- **永久编号不回收。** 文档删除、废弃、替代或归档后编号仍然保留，用于维持历史链接。
- **实现完成不等于验证通过。** 代码写完只是 `implemented`，必须有 approved 且 `result=pass` 的 VERIFY
  才是 `verified`；MVP 阶段没有生产环境，`verified` 就是终态。

## 术语速查

| 术语 | 含义 | 详见 |
|---|---|---|
| WORK | 工作项，整个体系的入口，`00-work.md` | [2](./specification/02-work-item.md)、[5](./specification/05-documents.md) |
| 工作类型 | product / infra / fix / maintenance / improvement，决定主流程 | [2](./specification/02-work-item.md) |
| concern | 额外关注（安全、隐私、数据、性能…），只追加阶段与关卡 | [2](./specification/02-work-item.md) |
| 风险 / 影响面 | low…critical / local…system，与 concern 共同决定流程增量 | [2](./specification/02-work-item.md) |
| 阶段 | 控制面上的一步，带 requirement 与 progress 两个维度 | [4](./specification/04-workflows.md) |
| artifacts | 阶段关联的文档，零到多、多到多 | [4](./specification/04-workflows.md) |
| 意图闸 / 验收闸 | 每个工作仅有的两次人工确认 | [6](./specification/06-status-and-gates.md)、[8](./specification/08-responsibilities.md) |
| approved / checked | 人签字的终态 / 工具校验通过的终态 | [6](./specification/06-status-and-gates.md) |
| 追踪链 | 从定义要求到验收标准再到验证证据的引用链 | [7](./specification/07-tracing.md) |

---

仓库当前的实现状态、目录结构和常用命令见 [`README.md`](./README.md)；全部工作项列表见
[`WORKS.md`](./WORKS.md)。
