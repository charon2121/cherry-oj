# 自定义运行预算与期限

规定浏览器 → Gateway → submission-service → judging-service → Go judge 的同步自测调用。
字段以 [公开 API](../../contracts/web-api.openapi.json)、[内部请求](../../contracts/custom-run-internal.schema.json)
和 [执行配置](../../contracts/execution-profile.schema.json) 为准；本文是预算、期限和取消行为的真源。

## 最小调用

已登录、完成 CSRF 校验的浏览器发送 `POST /api/custom-runs`：

```json
{"problemId":"0198cafe-0000-7000-8000-000000000002","languageId":"c","source":"int main(void){return 0;}","inputText":""}
```

同时带当前账号的 `X-Expected-User-Id` 和 CSRF header；Cookie 只到 Gateway。
内部调用按[服务身份协议](./service-identity.md)认证；源码、输入、Cookie 与调用令牌不得记日志。请求只发一次，不自动重试。

## 1. 执行预算

judging-service 统一解析自测与正式提交的当前有效限制。令：

- `C`：部署配置的编译调用预算，默认 30 s；至少覆盖节点编译墙钟及其调度、传输余量。
- `W`：显式 `clockNs`，或 `cpuNs × wallRatio`，默认倍率 10。
- `H`：每测试点调用开销，默认 1 s。
- `N`：正式提交取当前测试点数；自测只取 1。
- `K`：`contracts/languages.json` 的 `requiresCompilation` 为 true 时取 1，否则取 0。

`executionBudgetNs = K × C + N × (W + H)`，所有计算用整数 ns，溢出即拒绝。
未知语言不能当成无需编译。Go 语言注册中的实际编译阶段必须与目录中的声明一致。
编译本身仍受节点配置约束；这不是新增给用户程序的 CPU 或内存配额。

自测的预算上限由 judging-service 配置 `cherry.custom-run.max-execution-budget`
（环境变量 `CHERRY_CUSTOM_RUN_MAX_EXECUTION_BUDGET`）控制，默认 180 s，允许大于 0 且不超过 180 s；
非法配置拒绝启动。预算大于上限返回 `422 RUN_LIMIT_UNSUPPORTED`，等于上限可以准入。
正式提交继续使用正式任务期限，不套用自测上限。两者的 `cpuNs`、`memoryBytes`、`clockNs` 不因调用期限改变。

默认配置下：2 s CPU 的 C/C++ 自测预算为 `30 + 1 × (2 × 10 + 1) = 51 s`，可以准入；
无需编译的语言在同等运行限制下为 21 s。这里描述预算规则，不代表已上线新的语言。

## 2. 全链路期限

| 层 | 上限与行为 |
|---|---|
| Gateway 发起内部调用 | 生成 `now + 195 s` 的绝对期限；其中 180 s 为最大执行预算，15 s 为配置解析、调度与传输余量 |
| Gateway → submission-service | 受信 header `X-Custom-Run-Deadline`，Unix epoch 毫秒；忽略浏览器同名 header |
| submission-service → judging-service | 原有 `deadlineEpochMs`；取上游期限与本地 `now + 195 s` 的较早者；上游缺省时仍有本地界限 |
| judging-service | 同样只缩短期限；配置解析计入期限。剩余时间不足完整执行预算时返回 504，不缩短题目运行限制 |
| judging-service → Go judge | 等待不超过完整执行预算与剩余期限的较小值；编译、排队、运行不能各自重新获得一份预算 |
| Gateway 等待结果 | 最多 200 s，为下游返回与错误映射留余量 |
| 浏览器等待一次自测 | 最多 210 s；用户取消与切换账号仍可提前终止等待 |
| Gateway 账号在途租约 | 215 s 崩溃兜底；按 owner 释放，正常完成不等待 TTL |
| 部署反向代理 | 读取超时至少 210 s，不能先于 Gateway 截断合法自测 |

上述秒数对应的跨语言常量与测试均使用 ns，只有已有的绝对期限字段保留 epoch 毫秒。
各进程需同步系统时钟。每层用绝对期限计算剩余时间，禁止往后延长。
降低 judging-service 的预算上限无需同时修改浏览器或 Gateway；提高到 180 s 以上需要另行审核整条调用链。
自测保留独立并发槽位，槽位不足直接 429 RUN_BUSY，不在业务服务内无限排队。

## 3. 错误与取消

- 程序正常执行、CE、RE、TLE、MLE、OLE 都是 HTTP 200，读取正文状态区分。
- 422 RUN_LIMIT_UNSUPPORTED 仅表示题目预算超过部署上限；不能把传输或解析耗时误报为题目配置不支持。
- 504 CUSTOM_RUN_TIMEOUT 表示结果未确认；浏览器不据此宣称程序 TLE，也不自动重发。
- 取消或期限耗尽时，调用方取消未完成的 HTTP future；judging-service 中断工作任务，槽位只能释放一次。
  Go judge 断连取消请求上下文，并用独立的清理上下文释放执行器进程、cgroup、工作目录和临时引用。
- 浏览器断连不保证同步 MVC 服务立即感知；已接收的工作仍受完整执行预算及绝对期限约束，不能无限存活。
  Gateway 的租约清理覆盖成功、失败与断连；Redis 清理失败由 TTL 兜底，不得删除他人的租约。

## 4. 兼容与发布

公开请求与响应字段、内部请求字段不变；`deadlineEpochMs` 保留已有单位。
语言目录增加必填 `requiresCompilation`，目录版本升至 2；所有消费者与契约对齐测试在同一改动中更新，
仅对正式登记语言要求这个声明，开发用语言桩不因此变成平台可用语言。

先部署浏览器、代理与 Gateway 的较长等待窗口，再部署内部服务的新预算策略；
整体原子切换也可以。滚回旧的 45 s 策略时，2 s CPU 的编译语言自测会恢复 422，正式提交不变。
不能仅放宽内部服务而保留旧浏览器或代理超时。

## 5. 实测记录

2026-11-11，Ubuntu 24.04 测试服务器的真实 C/C++ 独立 rootfs 节点上，
2 s CPU 的自测触发旧 45 s 保护，返回 422，而正式提交可以完成。

2026-11-15，macOS 的隔离五服务 + Docker 开发 judge 全链路通过：C 的 2 s CPU 覆盖标定
在自测中返回 COMPLETED，保留 2 s CPU 限制；同题 C/C++ 的 ACM、CORE、标定切换和历史冻结通过。
Java 测试覆盖预算上限的等号边界、超过上限的 422、剩余期限不足的 504、下游取消和槽位复用；
浏览器自测回归 5 项通过。另经 SSH 转发向测试服务器原生节点补跑真实客户端的 AC/WA/CE，
此时服务器应用仍是旧发布，不能将它记为新预算已部署。
