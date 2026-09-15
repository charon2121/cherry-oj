---
id: "ISSUE-020"
type: "issue"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "review"
work: "WORK-059"
owners: ["team/server"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-15"
updated_at: "2026-09-15"
---

# ISSUE-020：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

## 为什么做

管理员在后台把一道题目改成「公开」时，页面偶尔会报一个「服务器内部错误」。重试一次通常就好了，
所以很容易被当成网络抖动放过去。

真正的问题不是这一次失败，而是**失败之后查不出原因**。我们的自动化验收流程碰到过一次，
但它留下的全部记录只有「这次请求返回了 500、耗时 2 秒」——既不知道是后台的哪一层出的问题，
也不知道到底出了什么错。于是这件事只能等下一次发生，而下一次同样查不出来。

所以这个工作项有两件事要做：**让这种失败下次能被查清楚**，以及**在查清楚之后修掉它**。
前者不依赖复现，可以马上做；后者要等到手上有一次带证据的失败。

## 问题现象

CI run **34925407839**（`55555215`）**第 1 次尝试**，job「sandbox CI（真实页面、校准与 Kafka
业务闭环）」失败，停在 `business.calibrate` 阶段。同一 commit 整条重跑（attempt 3）全绿。

失败的请求是 `PATCH /api/admin/problems/{problemId}`，body 为 `{"visibility": "PUBLIC"}`，
返回 **500**，耗时约 **2.086 秒**；同一次运行中相邻的其他请求都在 0.1–0.6 秒之间完成。
夹具记下的 `failureKind` 是 `UNCLASSIFIED`。

> **证据可得性说明。** 上述请求级数字来自本次分析当时读取的 attempt 1 产物
> `sandbox-business-34925407839-1/preparation-requests.json`。GitHub 只保留最近一次尝试的产物，
> attempt 3 重跑后该产物已被替换，现在只能取到 `-3` 的那一份。仍可取到的是 attempt 1 的 job 日志
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
- **对用户的影响**：管理员操作偶发失败，重试可恢复；无数据损坏迹象
  （`AdminProblemService.updateProblem` 的失败路径不提交事务）。
- **对流程的影响**：更严重的一项——这类失败会被「重跑一次就绿了」吸收掉，而偶发只要不记录就会
  变成常态，最后没人知道它从什么时候开始存在。

## 原因

**尚未确认。** 以下是已经排除的与已经定位的，供接手的人不必重走一遍。

**已排除：身份链路超时。** 最初的怀疑是 2.086 秒对上了
`problem-service/src/main/resources/application.yaml` 里的 `cherry.identity.connect-timeout: 2s`
与 `read-timeout: 2s`（`IdentityVerifierConfiguration` 用 `SimpleClientHttpRequestFactory` 把这两个
超时装到取 JWKS 的 `RestTemplate` 上）。**这条不成立**：读
`problem-service` 的 `SecurityProblemWriter` 可知，身份失败只会产出 **401**
（`INVALID_ACCESS_TOKEN`）或 **503**（`IDENTITY_KEY_UNAVAILABLE`），不会产出 500。

**已定位：这个 500 只可能来自网关的兜底处理器。** 请求打的是网关 `127.0.0.1:8080`。
`gateway-service` 的 `ProblemApiErrors.map` 把上游错误分流成 503（`WebClientRequestException`
与上游 5xx）、504（`TimeoutException` 与上游 504）、502（解码失败与其余未知）——
**其中没有任何一条产出 500**。整个 gateway 里唯一产出 500 的地方是
`api/ApiProblemHandler.java:112` 的 `@ExceptionHandler(Throwable.class) handleUnexpected`。
它是无 `basePackages` 的 `@RestControllerAdvice`，覆盖全部网关控制器。

结论：**当时网关内部抛出了一个既不是 `ApiProblemException`、也不是超时/连接/解码异常的东西**，
落进了兜底分支。它是什么，取决于下一条。

**关键线索：类名当时被打出来了，只是没人导出。** `handleUnexpected` 会记一条 ERROR：

```
LOGGER.error("Unhandled browser API error requestId={} errorType={}", requestId, error.getClass().getName())
```

即当时的网关私有日志里**有**这次失败的异常类名和 requestId。它没有进入产物，是因为
`business_stack.py` 把各服务的输出写到 `BASE/business/<name>.log`（私有目录，不进产物），
而 `Stack.diagnose()` 明写「不导出原始 Java 日志」——那条约束本身是对的（日志里有凭据），
但它把**可导出的部分和不可导出的部分一起挡住了**。

另有一项独立的次要缺陷：`problem-service` 的 `ProblemExceptionHandler` **没有**
`@ExceptionHandler(Exception.class)` 兜底，未预期异常会落到 Spring Boot 的默认错误处理，
响应体不带 `code`，这正是夹具把它归为 `UNCLASSIFIED` 的原因。它不是本次 500 的成因
（500 产生在网关），但同样属于「失败藏起自己的原因」。

## 修复方向

分成互相独立的三步，**前两步不需要复现**：

1. **让证据可得（`deploy/sandbox-linux/ci`，不碰 Java）。** 已在 `66a0a35` / 本工作创建前落地
   `business_journal.py`：从各服务的 logstash 结构化日志里按白名单导出
   `http_method`/`http_route`/`http_status`/`duration_ms`/`request_id`/`trace_id`/`level`/`logger_name`，
   异常只取类名链与 `com.cherryoj.*` 栈帧，形状不符的字段直接丢弃而不是截断，`message` 正文一律不读。
   **本工作又补了两处缺口**：（a）原先要求 ERROR 行必须带 `stack_trace` 才导出，
   而网关兜底那行恰恰不带 throwable，会被整条跳过；（b）网关把类名写在 message 的
   `errorType=` 后面，需要按固定键 + 类名形状把它读回来。两处都已修复并有用例。
   **这一步落地后，下一次发生时应当能直接读出异常类名与出错的那一跳。**
2. **把 `problem-service` 的未预期异常也变成有 `code` 的响应**（`apps/server`，需本工作授权）。
   让 `UNCLASSIFIED` 不再是唯一可能的分类结果。
3. **拿到带证据的一次失败后，修根因**（`apps/server`）。在此之前**不要**猜测性改动——
   已经有一次按猜测动手的教训（见 MEMORY-044 里 S3 那条）。

> **顺序是有意的。** 第 3 步依赖第 1 步的产出；在没有证据之前改 Java，等于用一次不可验证的改动
> 换掉一个不可复现的现象，之后既说不清修没修好，也说不清是不是同一个问题。

## 回归检查

- **AC-001（证据可得）**：构造一条「ERROR 但不带 throwable、类名写在 message 的 `errorType=` 后」
  的日志行，`business_journal` 必须导出它的 `logger` 与 `thrown`，且不得导出该行 message 的任何
  其余文本。**已有用例**：`business_test.py` 的
  `test_error_without_a_throwable_still_names_what_was_caught` 与
  `test_message_yields_nothing_unless_a_fixed_key_introduces_a_class_name`。
- **AC-002（分类不再必然是 UNCLASSIFIED）**：`problem-service` 对未预期异常返回带 `code` 的
  problem+json；夹具的 `failure_kind` 能给出 `UNCLASSIFIED` 以外的结论。
- **AC-003（根因已修）**：待第 3 步立项时补写。必须包含「这次 500 的异常类名是什么、
  为什么会抛、改动如何阻止它」，不接受「重跑不再复现」作为证据。
- **AC-004（不回归）**：`deploy/sandbox-linux/ci` 的 Python 自测全绿；WORK-050 固化的 CI
  93 项必需用例全绿。

## 变更记录

- 2026-09-15：状态变更：draft → review。原因：问题说明、已排除项、已定位的产出点与三步修复方向已写完，等待人工审核与意图闸
