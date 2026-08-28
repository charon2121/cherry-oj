# 全局项目文档

`docs/` 只保存已经确认、跨多个工作项长期有效的项目事实。新克隆必须能取得这些文档，它们与代码
一起进入 Git，并作为后续开发的稳定上游依据。

这里适合放：

- 全局产品定位、角色、路线与已确认产品规则；
- 系统拓扑、模块职责、数据模型和数据库边界；
- 判题引擎、前端和后端的长期设计约束；
- 会被多个未来工作复用的图、视觉合同和背景材料。

这里不放：

- 某个功能仍在讨论的需求或待确认项；
- 尚未确认的技术方案和备选比较；
- 开发计划、可认领任务、执行日志和验证结果；
- backlog、临时调查记录或为了当前改动生成的上下文。

这些内容统一进入 [`development/`](../development/README.md)，以 WORK 组织。开发过程中形成的结论
只有同时满足“已经确认”和“会长期约束多个工作项”时才整理进 `docs/`；迁入时保留来源 WORK 或
DECISION，避免历史原因丢失。

## 入口

- [`product.md`](./product.md)：全局产品定位、范围、路线和已确认决定；
- [`architecture.md`](./architecture.md)：系统拓扑、服务职责和通信边界；
- [`data-model.md`](./data-model.md)：跨服务领域模型和所有权；
- [`database-design.md`](./database-design.md)：数据库级设计；
- [`engine.md`](./engine.md)：judge 与 sandbox 引擎设计；
- [`backend.md`](./backend.md)：Java 服务工程与实现边界；
- [`logging.md`](./logging.md)：Java/Go 统一日志字段、Trace 传播与文件滚动规范；
- [`frontend.md`](./frontend.md)：Web 架构、状态、组件和工程规则；
- [`design-system.md`](./design-system.md)：Web 视觉、主题合同、组件规则、可访问性与例外流程；
- [`prd-background.md`](./prd-background.md)：产品缘起与长期背景。

[`design-system/`](./design-system/) 保存设计系统的可执行文档包。其中 Foundation 与主题 CSS 是数值
真源，theme contract 是语义和对比合同；机器快照、组件 HTML 和 preview 是派生或评审材料。

修改全局文档属于系统级影响：先在 `development/` 建立或关联工作项，说明改变了什么长期事实、哪些
工作受影响，以及如何验证代码和其他文档已经同步。拼写、失效链接等不改变语义的低风险修正可以走
快速流程。
