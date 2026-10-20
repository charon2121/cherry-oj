# 全局项目文档

`docs/` 只保存已经确认、长期有效的项目事实。新克隆必须能取得这些文档，它们与代码
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

这些内容不另建过程文档：讨论在对话里完成，原因与取舍写进提交说明。开发过程中形成的结论
只有同时满足“已经确认”和“会长期约束多个工作”时才整理进 `docs/`。

## 入口

- [`product.md`](./product.md)：全局产品定位、范围、路线和已确认决定；
- [`architecture.md`](./architecture.md)：系统拓扑、服务职责和通信边界；
- [`data-model.md`](./data-model.md)：跨服务领域模型和所有权；
- [`database-design.md`](./database-design.md)：数据库级设计；
- [`engine.md`](./engine.md)：judge 与 sandbox 引擎设计；
- [`protocols/`](./protocols/README.md)：协议目录：全部协议的总览与文档结构要求，含测试数据协议（`testdata.md`）和 sandbox 执行器协议（`sandbox-executor.md`）；
- [`backend.md`](./backend.md)：Java 服务工程与实现边界；
- [`logging.md`](./logging.md)：Java/Go 统一日志字段、Trace 传播与文件滚动规范；
- [`frontend.md`](./frontend.md)：Web 架构、状态、组件和工程规则；
- [`design-system.md`](./design-system.md)：Web 视觉、主题合同、组件规则、可访问性与例外流程；
- [`prd-background.md`](./prd-background.md)：产品缘起与长期背景；
- [`coding-standards/`](./coding-standards/README.md)：编码规范，分通用 / 语言 / 框架三层；
- [`git-workflow.md`](./git-workflow.md)：提交拆分、hooks 与 CI 门禁；
- [`status.md`](./status.md)：各模块当前是可用实现还是骨架。
  根目录 `AGENTS.md` 只留每次都必须遵守的部分，展开的规范在这里按需读。

[`design-system/`](./design-system/) 保存设计系统的可执行文档包。其中 Foundation 与主题 CSS 是数值
真源，theme contract 是语义和对比合同；机器快照、组件 HTML 和 preview 是派生或评审材料。

## 协议

跨进程、跨语言的约定（接口、报文、文件格式）单独放在 [`protocols/`](./protocols/README.md)：那里有全部协议的总览、
每份协议文档必须具备的结构，以及改动协议时的规则。它们与上面的设计说明不同：设计说明解释「为什么这样做」，协议规定「两端必须怎样」。

修改长期事实前，说明规则为何改变、哪些消费者受影响，并验证代码与其他文档已同步；改协议先交用户审核。
