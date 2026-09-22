---
id: "ISSUE-020"
type: "issue"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "approved"
work: "WORK-059"
owners: ["team/server"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-15"
updated_at: "2026-09-22"
---

# ISSUE-020：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

## 为什么做

CI 在把一道题目改成「公开」时，曾收到一次「服务器内部错误」。同一提交后续整轮重跑通过；
这不证明单次请求重试一定成功，也不证明故障来自网络。

真正的问题不是这一次失败，而是**失败之后查不出原因**。我们的自动化验收流程碰到过一次，
但它留下的全部记录只有「这次请求返回了 500、耗时 2 秒」——既不知道是后台的哪一层出的问题，
也不知道到底出了什么错。于是这件事只能等下一次发生，而下一次同样查不出来。

所以这个工作项有两件事要做：**让这种失败下次能被查清楚**，以及**在查清楚之后修掉它**。
前者不依赖复现，可以马上做；后者要等到手上有一次带证据的失败。

## 问题现象

CI run **34925407839**（`55555215`）**第 1 次尝试**，job「sandbox CI（真实页面、校准与 Kafka
业务闭环）」失败，停在 `business.calibrate` 阶段。同一 commit 整条重跑（attempt 3）全绿。

失败的请求是 `PATCH /api/admin/problems/{problemId}`，操作意图为 `visibility: PUBLIC`，
返回 **500**，耗时约 **2.086 秒**；同一次运行中相邻的其他请求都在 0.1–0.6 秒之间完成。
夹具记下的 `failureKind` 是 `UNCLASSIFIED`。
当前夹具的完整 body 还包含 slug、rowVersion；原报告没有保留正文，不能把上述意图当成完整请求体。

> **证据可得性说明。** 上述请求级数字来自本次分析当时读取的 attempt 1 产物
> `sandbox-business-34925407839-1/preparation-requests.json`。当时分析在 attempt 3 重跑后只能取得
> `-3` 的产物，未再取得 `-1` 原报告；这不作为 GitHub 产物保留机制的普遍结论。当时仍能取得 attempt 1 的 job 日志
> （job id `104242325934`），但它只有一行 `RuntimeError: business suite failed; see bounded report`，
> 以及 `business.py` 在 03:35:59 启动、03:37:48 失败这两个时间点。
> **这正是本问题要解决的那件事本身**：证据不足以定位，且会过期。

## 复现方式

**没有已知的稳定复现方式。** 目前只有一次观测，重跑即消失。

已知条件：GitHub Actions 的 `ubuntu-24.04` 运行器上，同一台机器同时跑着 5 个 JVM、MySQL、Redis、
Kafka 与 Chromium，内存与 CPU 都接近夹具设定的下限（`business_stack.py` 要求至少 10 GiB 可用内存）。
负载是可疑因素，但**没有证据**支持它，写在这里只是为了说明当时的环境，不作为结论。

## 实际结果

`PATCH /api/admin/problems/{problemId}` 返回 500，验收套件中止，`business` 15 项必需用例未完成。

## 预期结果

该请求返回 200，套件继续。若确实发生了上游不可用或超时，也应当按既有约定映射成 503/504
（见下节的代码路径），**而不是 500**——500 在当前系统里的含义是「出现了没有预料到的异常」。

## 影响与条件

- **影响面**：`apps/server`（gateway-service 与 problem-service）与
  `deploy/sandbox-linux/ci`（证据导出）。判题引擎不在链路上。
- **频率**：已观测 1 次。WORK-058 的 S1–S7 共约 10 轮 CI 中仅此一次。
- **对用户的影响**：管理员操作可能报错。本次没有数据损坏证据，但 HTTP 500 本身不能证明数据库未提交；
  下游成功后网关也可能出错，因此不能自动重发 PATCH 来掩盖失败。
- **对流程的影响**：更严重的一项——这类失败会被「重跑一次就绿了」吸收掉，而偶发只要不记录就会
  变成常态，最后没人知道它从什么时候开始存在。

## 原因

**尚未确认。** 以下是已经排除的与已经定位的，供接手的人不必重走一遍。

**不支持直接归因为 JWKS 超时。** 最初的怀疑是 2.086 秒对上了
`problem-service/src/main/resources/application.yaml` 里的 `cherry.identity.connect-timeout: 2s`
与 `read-timeout: 2s`（`IdentityVerifierConfiguration` 用 `SimpleClientHttpRequestFactory` 把这两个
超时装到取 JWKS 的 `RestTemplate` 上）。这个时间吻合不足以归因：读
`problem-service` 的 `SecurityProblemWriter` 可知，身份失败只会产出 **401**
（`INVALID_ACCESS_TOKEN`）或 **503**（`IDENTITY_KEY_UNAVAILABLE`）；不能由此排除整个身份或框架链路的一切异常。

**网关兜底是候选入口，尚无证据锁定。** 请求打的是网关 `127.0.0.1:8080`。
`gateway-service` 的 `ProblemApiErrors.map` 把上游错误分流成 503（`WebClientRequestException`
与上游 5xx）、504（`TimeoutException` 与上游 504）、502（解码失败与其余未知）——
这些新建映射没有直接产生 500，但该方法也会原样返回已有 `ApiProblemException`。
`ApiProblemHandler.handleUnexpected` 会产生 500；同类的 `handleHttpStatus` 也能处理状态为 500 的
`ResponseStatusException`，过滤器或框架错误还可能走其他路径。原报告未保存错误码或异常类名，无法区分。

结论：当前代码可以说明某些异常通常如何映射，不能证明历史请求实际走了哪个分支。

**确定的诊断缺口：若进入兜底，关键日志会被漏掉。** `handleUnexpected` 会记一条 ERROR：

```
LOGGER.error("Unhandled browser API error requestId={} errorType={}", requestId, error.getClass().getName())
```

该分支的私有日志包含异常类名和 requestId，但不能断言历史请求必然产生了这一行。
这类信息没有被完整导出，是因为
`business_stack.py` 把各服务的输出写到 `BASE/business/<name>.log`（私有目录，不进产物），
而 `Stack.diagnose()` 明写「不导出原始 Java 日志」——那条约束本身是对的（日志里有凭据），
但它把**可导出的部分和不可导出的部分一起挡住了**。

另有两项确定的诊断缺口：

- `business_api.failure_kind` 只认识三种 `SERVICE_UNAVAILABLE` 文案。即便网关已经返回合法的
  `INTERNAL_ERROR`、`GATEWAY_TIMEOUT` 或 `BAD_GATEWAY`，它仍产出 `UNCLASSIFIED`。
  因此不能由这个分类反推「响应不带 code」或服务端根因。
- `problem-service` 的 `ProblemExceptionHandler` 没有未预期异常兜底；这类响应的稳定 code
  没有由服务保证。补齐它能改善内部诊断，但网关仍将上游 500 映射为 503，不把内部 code 直接透传。

2026-09-22 的只读实验还确认：工作区原有 Python 补丁没有提取旧网关日志正文中的 requestId，
且接受任意 logger 的 `errorType=` 字符串及非法后缀前的合法前缀。具体输入与结果见 VERIFY-060。

## 修复方向

1. **补齐请求关联与安全证据。** 在网关兜底写结构化的请求标识和有界异常事实；CI 按来源与字段形状
   导出。旧文本日志只兼容精确 logger 和完整固定模板，任意正文不参与类名搜索。
2. **分别修复分类器与题目服务异常边界。** CI 识别已知公开错误码；题目服务为未预期异常提供安全、
   稳定的内部错误响应，保留既有 4xx/5xx、鉴权和网关映射语义。两项各自有反例测试。
3. **根据失败证据修根因。** 用 requestId 串起请求记录、HTTP 完成事件与异常事实，构造确定性回归后修复。
   没有复现时仅报告诊断缺陷已修，整体保持未完成。详细方案与边界见 DESIGN-052、TASK-132。

## 回归检查

- AC-001（证据可得）：受控网关未预期异常产生可与请求记录关联的 requestId、异常类名及有界诊断，
  经日志导出仍保留关联。合法旧模板兼容；其他 logger、非法或超长字段、异常正文中的合成敏感标记不被导出。
- AC-002（稳定分类与响应）：CI 能区分已知 INTERNAL_ERROR、BAD_GATEWAY、GATEWAY_TIMEOUT
  和既有 SERVICE_UNAVAILABLE 分类，未知值仍为 UNCLASSIFIED。problem-service 未预期异常返回
  500 与稳定内部 code，既有状态及网关将上游 500 映射为 503 的行为不变。
- AC-003（根因已修）：保留一次真实或与原链路对应的确定性失败的 requestId、异常类名和触发条件，
  解释为何导致该 PATCH 失败，并证明同一回归修复前失败、修复后通过。仅重跑成功或完成诊断补丁不满足本条。
- AC-004（不回归）：`deploy/sandbox-linux/ci` 的 Python 自测全绿；Java 聚合工程 verify 通过，
  所需测试实际执行；WORK-050 固化的 CI 93 项必需用例全绿。

## 变更记录

- 2026-09-15：状态变更：draft → review。原因：问题说明、已排除项、已定位的产出点与三步修复方向已写完，等待人工审核与意图闸
- 2026-09-22：重新核对代码，纠正「唯一 500 入口」「UNCLASSIFIED 等于没有 code」和请求体、事务结果的过强推断；
  记录诊断补丁的关联与字段边界缺口，补齐可执行验收标准。历史现象与当前推断分开保存。
- 2026-09-22：意图闸通过：review → approved。原因：同意修复方案
