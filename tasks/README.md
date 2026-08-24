# 开发任务中心

`tasks/` 是本仓库研发执行任务的唯一真源，并随 Git 一起提交。产品需求与产品验收以
[`product/`](../product/README.md) 为真源，技术设计仍放在 `docs/`；凡是需要被认领、实现和技术
验收的工作，都要在这里建立独立任务。

## 目录

```text
tasks/
├── README.md              # 本文件：状态和协作协议
├── items/                 # 活跃任务，一个任务一个 Markdown
├── archive/<year>/        # 已完成或取消任务的定期归档
├── templates/task.md      # 新任务模板
└── schema/task.schema.json
```

任务移动到 `archive/` 后 ID 和文件内容保持不变。依赖查找会同时扫描 `items/` 与
`archive/`，因此归档不会破坏已有关系。

## 文件格式

每个任务由 YAML front matter 和 Markdown 正文组成。为了让工具不依赖第三方 YAML 包，
front matter 使用受限子集：每行一个字段，值使用 JSON 兼容写法；不使用缩进对象、块字符串
或 YAML 特有类型。字段约束见 `schema/task.schema.json`。

文件名格式：`TASK-0001-short-slug.md`。ID 创建后永不复用、永不修改。

正文必须回答：为什么做、目标是什么、包含和不包含什么、怎样算完成。代码中的对应锚点写成
`TODO(TASK-0001): ...`，不要再写无法追踪的 `TODO(backlog)`。

新任务可以用 `requirement_ids` 关联一个或多个产品需求，用 `milestone` 标识交付里程碑。纯技术治理
和历史任务允许不关联产品需求；不要为了填字段制造虚假关系。REQ 与 TASK 的双向关系由
`scripts/product check` 校验。

## 状态

- `todo`：尚未认领。
- `in_progress`：已认领并正在开发。
- `blocked`：开发中遇到依赖任务以外的实际阻塞。
- `review`：实现和技术验证已经完成，等待技术检查。
- `done`：验收完成。
- `cancelled`：明确不再执行，正文中必须记录原因。

正常流转为 `todo → in_progress → review → done`。不要求独立审核的任务可以从
`in_progress` 直接进入 `done`。`blocked` 解除后回到 `in_progress`；放弃认领则回到
`todo`。

`READY` 和 `WAITING` 不是持久化状态，而是动态计算的可认领性：

```text
READY = status 为 todo，并且 depends_on 中所有任务均为 done
```

依赖尚未完成的 `todo` 会显示为 `WAITING`。`blocked` 只表示真正的执行阻塞，不能用来表示
普通的前置依赖。

## 依赖

- `depends_on` 是硬依赖；依赖全部 `done` 后任务才能被认领。
- `related` 只表示相关，不影响认领。
- 不维护反向的 `blocks`；反向关系由工具计算，避免两份数据漂移。
- 依赖必须存在、不能指向自己、不能形成环。
- 依赖被取消时，下游任务不会自动就绪；必须先调整下游任务的定义。

## Agent 认领协议

1. 运行 `scripts/task list --ready`，只从可认领任务中选择。
2. 运行 `scripts/task claim TASK-0001 --agent <稳定标识> --branch codex/task-0001`。
3. 单独提交任务文件，commit message 使用 `chore(tasks): 认领 TASK-0001`。
4. 同步该认领提交；只有同步成功后才开始修改代码。若同步冲突，重新读取任务状态。
5. 租约到期只表示任务可以被调查和接管，不允许不检查原分支就覆盖已有工作。

`assignee` 建议使用能定位到执行者或会话的稳定标识。默认租约是 24 小时；长任务应在有实际
进展时重新认领以续期，而不是制造无意义的心跳提交。

## 完成和阻塞规则

进入 `blocked` 时必须记录阻塞原因、已验证事实以及解除阻塞的下一步。

进入 `review` 或 `done` 前必须：

- 勾选全部验收标准；
- 填写完成结果，包括 commit/PR、验证命令和结果、已知风险；
- 确认硬依赖均为 `done`；
- 同步必要的测试、契约、设计文档或教程。

“代码已经写完”不等于任务完成。验证证据和验收标准都是完成定义的一部分。
TASK 完成也不等于产品需求已经接受；REQ 的产品验收状态以 `product/` 为准。

## 命令

```bash
scripts/task list
scripts/task list --ready
scripts/task show TASK-0001
scripts/task new --title "任务标题" --type feature --area server --priority P1 \
  --requirement REQ-0001 --milestone M1-traditional-oj
scripts/task claim TASK-0001 --agent codex/example --branch codex/task-0001
scripts/task block TASK-0001 --reason "阻塞原因和下一步"
scripts/task resume TASK-0001
scripts/task release TASK-0001 --reason "释放原因"
scripts/task review TASK-0001 --result "实现和验证摘要"
scripts/task done TASK-0001 --result "审核结论"
scripts/task cancel TASK-0001 --reason "取消原因"
scripts/task check
```

命令只修改工作区，不会替用户执行 `git add`、commit 或 push。`scripts/task check` 是 CI
执行边界，会校验字段、状态不变量、依赖图和完成定义。
