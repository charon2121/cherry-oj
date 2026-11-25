# 自动出题协议

## 快速使用

管理员登录后从私有题目的「自动出题」打开工作台，也可提交一份符合 `ProblemAuthoringJobRequest` 的 JSON。

```sh
curl -fsS "$CHERRY/api/admin/problems/$PROBLEM_ID/authoring/jobs" \
  -b "$COOKIE_JAR" -H "X-CSRF-Token: $CSRF_TOKEN" \
  -H 'Content-Type: application/json' --data-binary @request.json
```

HTTP 202 返回现有或新任务，`Location` 指向详情。详情显示当前尝试和阶段。报告为 UTF-8 纯文本，访问仍需管理员身份。

## 参与方

- 浏览器使用 [Web API](../../contracts/web-api.openapi.json)，Gateway 以现有调用令牌代表 ADMIN。
- problem-service 拥有任务、输入快照、产物和应用审计。内部路由使用对应的 `/internal/admin/problems/{problemId}/authoring/jobs`，返回未包装的业务对象。
- problem-service 在私有目录启动 `problem-build`。进程参数、大小限制、进度和结果的真源为 [RunnerInput、RunnerProgress 和 RunnerResult](../../contracts/problem-authoring.schema.json)。代码通过现有原生节点的 [试运行协议](./judge-api.md) 执行。
- 可选的 OpenAI Responses 只接收管理员提交的知识点、难度、目标和明确选中的既有 AI 提案及修订指令。AI 结果存为候选，管理员确认后可填入私有题目编辑器，再运行确定性验题。

## 并发与失败

相同构建快照和输入复用任务。后台一次执行一个任务，最多等候 16 个。取消会终止正在执行的尝试，迟到的结果不能覆盖当前尝试。失败或取消后可重试同一冻结输入，最多 5 次。执行器失联的任务在租约到期后标记中断。

成功构建绑定产物指纹。应用需要管理员操作，提交审核时的 `rowVersion` 和该指纹。服务持题目行锁检查私有状态、行版本、原有数据地址、样例与语言配置。发布 ZIP 按既有 [测试数据协议](./testdata.md) 核对指纹，应用、当前地址与审计在同一事务生效。重复应用返回原收据。正式标定和公开继续走各自的接口。

执行器使用 `schemaVersion: 1`。任务输入出现未知字段或未知版本时拒绝。进程退出成功且结果完整才可应用产物。语言验证记录参考解、CORE 起始代码和实际资源使用，当前在线节点和标定由公开检查提供。

CLI 的运行配置和本地示例见 [problem-build](../../apps/judge-engine/cmd/problem-build/README.md)。

## 实测记录

2026-11-24，隔离的 Gateway、problem-service、MySQL 与原生 Linux 判题节点完成后台构建、去重、取消、重试和失败报告验证。
并发上传后旧构建无法应用，同一产物再次应用只返回原收据。CORE 单任务通过 C、C++、PyPy、Java 的参考解、错误解和起始代码检查。
ACM 产物经标定和显式公开后，普通用户正式提交得到 AC（6/6）。OpenAI 适配器使用本地模拟端点核对发送字段和候选校验。
