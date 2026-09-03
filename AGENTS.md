# AGENTS.md

本文件是智能体的入口路由。**它只回答「该读哪一份」，不复述任何规则**——规则复述两遍就会漂移，
而这个仓库已经因为同一件事被写在两处吃过亏。

## 动手之前

| 你要做什么                      | 先读                                                                                                           |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| 任何事                          | [`CLAUDE.md`](./CLAUDE.md)：项目边界、仓库结构、跨语言铁律、协作协议                                           |
| 开发、修复、重构                | [`development/README.md`](./development/README.md)，并运行 `scripts/work overview`                             |
| 承接某个任务                    | `scripts/work context TASK-xxx`，它给出上游依据与代码读写边界                                                  |
| 了解某个工作的全貌              | `scripts/work board WORK-xxx`                                                                                  |
| 写 Go / Java / TypeScript       | [`docs/engineering/README.md`](./docs/engineering/README.md) 的索引表                                          |
| 动任何 Web UI、组件、样式或主题 | [`docs/design-system.md`](./docs/design-system.md)；三层权威依次是冻结来源、该文合同、`apps/web/design-system/` 可执行真源。设计值只在真源手写一次，禁止把来源 demo 直接当生产代码 |

## 三条硬禁止

即使上面一份都没读到，这三条也不能违反：

1. **不能代替人做决定。** 人工确认收拢在每个工作的两道闸（意图闸、验收闸），只能由人签署。
   格式检查通过、测试全绿、你自己认为内容完整，都不构成授权。
2. **文档与实施是两个回合。** 用户第一次说明意图时只写文档然后停下；只有用户在看到文档后明确
   表示通过并允许执行，才能改业务代码、迁移数据或部署。
3. **不越过任务边界。** TASK 的 `read_paths` / `write_paths` / `forbidden_paths` 是硬边界。
   需要越界时先改边界并写明理由，不要先动文件。

这三条的完整表述和例外情形都在 `CLAUDE.md` 与 [`development/`](./development/README.md)；
本文件只保证它们不会因为你没读到而被绕过。
