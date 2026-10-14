# judge 调用协议

规定怎样向 judge 服务提交一次判题、怎样读结果，以及 HTTP 状态码、过载和错误各表示什么。
请求与响应的**字段**以 [`judge.schema.json`](../../contracts/judge.schema.json) 为准；本文规定字段之外的调用语义。

## 快速使用

```sh
curl -sS --noproxy '*' -X POST http://127.0.0.1:5051/judge -H 'Content-Type: application/json' -d '{
  "submissionId": "s-1",
  "problemId": "p-a-plus-b",
  "mode": "submit",
  "testDataLocation": "/srv/cherry-oj/testdata/p-a-plus-b",
  "languageId": "cpp",
  "source": "#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<\"\\n\";}",
  "limits": { "cpuNs": 1000000000, "memoryBytes": 268435456, "clockNs": 3000000000 }
}'
```

响应是 HTTP 200，正文是 `JudgeResult`：

```json
{ "verdict": "AC", "score": 100, "cpuNs": 1200000, "memoryBytes": 1048576,
  "testDataDigest": "<testdata.json 的 digest>",
  "testcaseResults": [ { "idx": 1, "verdict": "AC", "cpuNs": 1200000, "memoryBytes": 1048576 } ] }
```

不带测试数据地址、直接在请求里给测试点的试运行：`"mode": "trial"`，加 `"testcases": [{ "input": "1 2", "expected": "3" }]`。
`GET /version` 返回 `{"name":"cherry-oj-judge","version":"…"}`。

## 1. 参与方与传输

| 角色 | 做什么 |
|---|---|
| 调用方（judging-service、benchmark 运行者等） | 发 `POST /judge`，**同步等待**结果 |
| judge | 读测试数据、编译、逐测试点执行、比对，返回结论 |

- HTTP/1.1，JSON，UTF-8。judge 监听配置里的 `httpAddr`，没有鉴权：只放在可信网络里。
- 只有两个端点：`POST /judge`、`GET /version`。其他路径与方法由 HTTP 层返回 404 / 405。
- 一次请求就是一次完整判题；没有任务编号、没有轮询、没有回调。连接断开时，这次请求的上下文被取消，judge 不再继续。

## 2. 请求

| 字段 | 规则 |
|---|---|
| `submissionId`、`problemId` | 必填。只用于日志与对账，judge 不据此查任何东西 |
| `mode` | `submit`（默认）：按 `testDataLocation` 读取正式测试点；`trial`：用请求里的 `testcases` |
| `testDataLocation` | `mode=submit` 时必填，规则见[测试数据协议](./testdata.md)；`trial` 时不需要 |
| `testcases` | `trial` 用；每项 `input` 必填，`expected`、`name` 可选。没有 `expected` 的测试点只运行，结论是 `RAN` |
| `languageId` | 必填。judge 已登记 `cpp`、`python`、`java`；节点实际能跑哪些，取决于它的 rootfs。官方 rootfs 只有 C/C++ 工具链 |
| `source` | 必填，完整源码 |
| `limits.cpuNs`、`limits.memoryBytes` | 必填，**每个测试点**的上限 |
| `limits.clockNs` | 可选，墙钟上限。缺省为 `cpuNs` × 配置的 `clockRatio`（默认 10）；超过执行层硬界（120 s）则这次判题 SE |

- 解码是严格的：**未知字段、第二个 JSON 值、缺少必填字段**都返回 400；请求体超过 16 MiB 返回 413。
- 编译用 judge 自己的配置限制，不受 `limits` 影响。

## 3. 响应与状态码

| HTTP | 含义 |
|---|---|
| `200` | 请求能解开且判题流程走完或在途中失败。**所有结论（AC、WA、TLE、SE……）都是 200**，结论写在 `verdict` 里 |
| `400` | 请求体不合法（见 §2）。正文 `{"error":"…"}`，不会产生 `JudgeResult` |
| `413` | 请求体超过 16 MiB |
| `404` / `405` | 路径或方法不对 |

**HTTP 状态只描述「这次对话成不成」，不描述判题结论。** 调用方判断成败必须读 `verdict`，不能看状态码。

### 结论 verdict

`AC` `WA` `PE` `CE` `RE` `TLE` `MLE` `OLE` `SE`，另有试运行专用的 `RAN`（运行了但没有 `expected`）。
整体结论是各测试点结论按严重度取最坏者：`SE` > `CE` > `RE` > `MLE` > `OLE` > `TLE` > `WA` > `PE` > `RAN` > `AC`。

- **SE** 是判题机自己的问题，不是用户的问题。**无法开始判题**（语言未知、测试数据读不到或校验失败、墙钟超过硬界、上传源码失败）
  同样返回 200 加 `SE`，`message` 给出原因，没有 `testcaseResults`。
- `CE` 没有逐测试点结果。`testcaseResults` 按实际执行顺序排列，`idx` 从 1 起。
- `score`：`AC` 为 100，其余为 0。
- `testDataDigest`：`mode=submit` 时本次实际读取的数据指纹，即 `testdata.json` 的 `digest`。
- 比对对空白的处理由节点配置 `strictWhitespace` 决定：宽松时仅空白不同判 `AC`，严格时判 `PE`。空白差异永远不会判成 `WA`。

## 4. 并发与过载

- judge 同时执行的数量是配置的 `parallelism`，等待队列长度是 `queueSize`。
- **队列满时不排队、不阻塞，立刻返回 HTTP 200 加 `SE`**，`message` 为 `compile request: sandbox queue is full`（或执行阶段的同类原因）。
  这是过载信号，不是用户程序的结论。调用方应重试，或减少并发。
- 同一次判题的各测试点串行执行。
- 想得到可重复的结论（例如 benchmark），**对同一节点串行提交**，不要超过 `parallelism + queueSize`。

## 5. 超时

- judge 不为 `/judge` 设整体期限。一次判题的最长耗时约为：编译期限 + 测试点数 × `limits.clockNs`。
- 调用方必须自己设客户端超时，并大于上式；调用方断开后，这次判题被取消。

## 6. 追踪头

只用 HTTP 头传 W3C `traceparent` / `tracestate`；`X-Request-Id` 只关联这一次同步调用，不承担身份、幂等或业务主键的语义。
追踪字段不进入请求体或响应体。

## 7. 版本与兼容

- 字段的版本由 `judge.schema.json` 的 `x-contract-version` 标识，目前为 `2`。
- 请求侧拒绝未知字段，所以调用方加字段前必须先升级 judge。响应侧新增字段由契约控制，调用方应拒绝未知字段和未知 verdict。
- `GET /version` 的 `version` 是 judge 自报的字符串，**不暴露节点配置**（后端类型、`strictWhitespace` 等）；需要这些值的运行者必须自行声明。
- 改动端点、状态码语义、过载行为或字段，必须同时修改 judge、judging-service、benchmark 运行者、契约和相关测试。

## 8. 实测记录

2026-10-14，本机 macOS，开发用的无隔离后端，`parallelism=1`、`queueSize=1`：
5 个并发的 `trial` 请求（死循环源码）得到 5 个 HTTP 200：2 个 `TLE`；2 个立刻返回 `SE`，`message` 为 `compile request: sandbox queue is full`；
1 个立刻返回 `SE`，`message` 为 `upload source: store capacity exceeded`（存储为在途上传预留容量，容量不足时同样是过载信号）。
`GET /version` 返回 `{"name":"cherry-oj-judge","version":"0.1.0-mvp"}`。
