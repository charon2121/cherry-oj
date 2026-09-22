> 本文是[《智能体协作开发文档系统规范》](../SPECIFICATION.md)的一章。

# 6. 状态与关卡

## 6.1 三类状态机

WORK：todo → ready → doing → implemented → verified，cancelled 表示终止。
TASK：todo → ready → doing → done → verified，执行中可 blocked。
其他文档：draft → review → checked/approved，可 deprecated、superseded、archived。

精简 WORK 由 Agent 记录实际实施进度，refresh 只在验收事实齐备后收敛 verified；不额外维护一个内嵌 TASK 状态。
独立 TASK 的实现完成不等于工作验收；未完成 TASK 阻止 WORK 进入 implemented。

## 6.1.1 两道闸

意图闸确认主文档前五节的目标、范围与重要取舍，不代表人审查了全部附件。
主文档与执行范围必须完整，不能有 blocking_items；验收至少有一个 AC 条目。
独立上游附件也应写完；工具将符合条件的附件置为 checked，不标成用户 approved。

验收闸确认实际结果、承诺差异与遗留问题。必须已签意图闸、主工作 implemented、有效 TASK 完成、
复核完成、全部必需检查 pass 或说明不适用、有效 VERIFY 已写完且 result=pass、每条主文档 AC 都有锚定证据。
格式和引用检查不能证明证据真实；人及复核者仍须判断结果。

两道闸只能由人执行。Agent 不代签、不伪造 gates。撤回精简验收时 verified 自动退为 implemented，
VERIFY 退为 review；意图撤回前先撤回验收并将 WORK 退回 todo。重新实施前先撤回旧验收。

历史 layered 的意图闸仍覆盖决定类文档，验收闸覆盖 VERIFY；保留原前置条件与撤回规则，不追认历史检查。

## 6.1.2 结论是否仍然成立

outcome 与进度分开：superseded 必须指出接替 WORK；invalidated 必须留下 MEMORY 解释失败前提。
没有产出的工作用 cancelled。不能抹掉真实发生过的实施与验收记录。

## 6.2 状态推导

flow.json 记录闸、文档与执行事实形成的阶段状态；主文档流程表是生成视图。
复核必须显式记录，签验收不能倒推“复核做过”。检查失败不能被阶段 done 覆盖。
refresh 不代替人工决定，不会给工作补签。

## 6.5 计划与开发关卡

完成主文档后展示人工审核内容，取得后续明确执行授权并由人签意图闸再实施。
重要行为、边界或代价变化时重新确认；普通实现细节在已确认范围内自主处理。

## 6.6 验证关卡

VERIFY 写实际命令、环境、结果与限制，不能只列未来测试计划。未测、失败和部分通过必须如实保留。
高风险与专项检查结论单独记录，不会随签闸自动变为 pass。

## 6.7 完成定义

实现完成只是 implemented；两道闸与实际验证完成才可 verified。
MVP 没有生产环境，不为了闭环完整而增加无法完成的上线观察。

[返回总览](../SPECIFICATION.md)
