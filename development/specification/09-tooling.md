# 9. 工具与迁移

`scripts/work` 现在只提供 `new`、`list`、`show`、`check`。可以手写记录而不使用创建工具；`check` 检查入口和历史文件完整性，不评判工作是否获准或完成。

`gate`、`refresh`、`rebuild`、`advance`、状态修改及关系管理等旧命令已移除。旧工具和旧测试保存在[迁移快照](../history/README.md)，不是日常入口。

已有 WORK 保留目录与编号，主入口换成短说明，原主文档和其他材料原样保存。`flow.json` 为只读历史，不再更新；全局编号表和 Schema 归档，不为新任务生成管理数据。

CI 和 hooks 继续检查工具的实际行为、文档本地链接、Git 收录和迁移完整性，不验证阶段状态，也不要求代码改动新增记录。skill 的显式调用策略在其 `agents/openai.yaml`，全局 `AGENTS.md` 同样明确默认关闭。

[返回总览](../SPECIFICATION.md)
