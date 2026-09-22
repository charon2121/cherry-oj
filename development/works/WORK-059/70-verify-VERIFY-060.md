---
id: "VERIFY-060"
type: "verify"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "review"
work: "WORK-059"
owners: ["team/server"]
depends_on: ["TASK-132"]
related: []
implements: []
verifies: ["ISSUE-020#AC-001", "ISSUE-020#AC-002", "ISSUE-020#AC-003", "ISSUE-020#AC-004"]
tags: []
result: "partial"
created_at: "2026-09-15"
updated_at: "2026-09-22"
---

# VERIFY-060：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

## 验证对象

2026-09-22，用户签署意图闸后实施 TASK-132 的诊断与异常边界修复。基线 HEAD=454aaa7；
保留先前只读调查记录，以下「实施验证」记录新增事实。历史 PATCH 500 的根因尚未定位，整体 partial。

## 对应要求

| 条目 | 当前证据与结果 |
|---|---|
| AC-001 | 结构化与精确旧模板导出、requestId 关联和 controller 前后诊断已修复，本地测试通过 |
| AC-002 | 三种公开码已有固定分类；题目服务未知异常返回稳定 code，既有状态与鉴权组件回归通过 |
| AC-003 | 历史 PATCH 的异常类名与触发条件未知，没有根因修复证据 |
| AC-004 | Python 124 项通过；Java 全量 verify 与最终修改模块的 verify 通过，既有真实 Linux 测试未运行的限制见下；本次候选前两轮 CI 各 12 个 job、93 项必需回归通过 |

## 检查与结果

环境：本机 macOS、Python 3。使用 PYTHONDONTWRITEBYTECODE=1，不写入源码目录缓存。
本节是实施前调查；不能用这些历史输出描述已修复代码的当前行为。

### 分类器反例

直接调用现有 business_api.failure_kind，输入 JSON 的 detail 固定为已知安全文案，结果如下：

| code | 输出 |
|---|---|
| INTERNAL_ERROR | UNCLASSIFIED |
| BAD_GATEWAY | UNCLASSIFIED |
| GATEWAY_TIMEOUT | UNCLASSIFIED |
| SERVICE_UNAVAILABLE | UPSTREAM_UNAVAILABLE |

说明当前 UNCLASSIFIED 并不能证明响应没有 code；前述三种 code 在网关已有实现。

### 日志提取反例

直接调用现有 business_journal.fact，所有输入均为本地构造的合成内容：

1. ERROR、logger=com.cherryoj.gatewayservice.api.ApiProblemHandler，message 为
   `Unhandled browser API error requestId=req_0123abcd errorType=java.lang.IllegalStateException`。
   输出含 thrown，但不含 requestId。
2. ERROR、logger=example.OtherLogger，message 为
   `user-controlled text errorType=example.synthetic.PrivateMarker`。
   输出 thrown 包含 example.synthetic.PrivateMarker，说明未限制日志来源。
3. 合法网关模板的类名尾部追加 `/invalid`，仍输出 java.lang.IllegalStateException，
   说明正则接受非法字段的合法前缀。

这些是诊断补丁的确定缺陷，不是原 PATCH 500 的复现，也不证明已发生过真实敏感信息泄漏。

### 代码路径核对

- ApiProblemHandler.handleUnexpected 返回 500；handleHttpStatus 也接受 ResponseStatusException 的
  500，ProblemApiErrors.map 还会原样返回 ApiProblemException。不能凭响应状态认定只走兜底。
- 当前 make_public 请求还带 slug、rowVersion，不能把 visibility 片段描述成原始完整 body。
- ProblemExceptionHandler 仅定义业务、校验和上传错误处理；未知异常没有该服务保证的稳定 code。
- Stack.diagnose 已导出白名单服务日志事实，可复用现有产物入口。

### 已执行测试

`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/ci -p business_test.py`
退出码 0，28 tests / OK。已有测试不覆盖上述全部反例，因此不能据此把补丁视为完成。

Java 与完整 CI 是意图闸后的实施验证；未执行，不复用 WORK-058 的 93 项通过来证明 WORK-059。

文档检查：scripts/work check 的 507 份工作文档通过，保留 WORK-033、WORK-058 的原有进度提示；
python3 scripts/docs_test.py 的 583 份 Markdown 入口与本地链接通过；git diff --check 通过。

## 未通过项

AC-003 无定位证据。AC-004 已补入本次候选的完整 CI，不能据此认定 AC-003 通过。

## 实施验证

### 结果与不变量

- 网关与题目服务未知异常使用固定事件和安全字段；不记录 Throwable、异常正文或请求内容。
  网关原因链最多 8 项、应用栈最多 12 项，跳过文件路径和框架栈。实际 Logstash JSON 数组已验证。
- CI 仅从允许来源的结构化事件或精确旧模板读取异常事实；保留 requestId，非法完整字段拒绝，
  三种已知公开错误码得到固定分类。未知响应仍为 UNCLASSIFIED，不导出任意 code/detail。
- WebExceptionHandler 补齐 controller 前诊断，原异常继续交给框架，已提交响应也不改头部或正文。
  CSRF Session 读取失败测试证明 PATCH 执行 0 次；Session 保存失败测试证明写入执行 1 次且不重试。
  这些注入实验只证明可达路径与诊断覆盖，不能认定它们就是历史事故原因。
- 题目服务保留直接及包装后的业务、校验、上传、安全和框架 HTTP 异常行为；未知异常得到安全 500。
  读取被防火墙拒绝的诊断头时只省略关联信息，仍处理原异常；没有放宽防火墙规则。

### 修复前后证据

| 对象 | 修复前 | 修复后 |
|---|---|---|
| Python 日志与分类器 | 新增反例触发 20 个失败、3 个错误 | business_test 32 项通过；完整 CI Python 自测 124 项通过 |
| 网关未知异常日志 | 2 个新增测试均因缺少结构化事实失败 | 请求/响应/事件关联、循环与深原因链、实际 JSON 编码通过 |
| 题目服务未知异常 | 首轮 17 项中 10 项错误，异常穿出 Servlet | 最终组件测试 22 项通过，包括实际 PATCH、安全过滤器及三种 RequestRejectedException 包装 |
| controller 前诊断 | 同一测试关闭 observer 时没有安全事件且 advice 未触发 | 启用后能关联请求，最终 14 项 WebFlux 组件测试通过 |

Python 新测试首次把合法 Java 标识符后缀 `$` 当成非法，已改为真正非法的 `@` 后缀；
Java 日志编码测试最初缺少测试用 Spring Environment，后已用独立 logger context 修正；
observer 测试的 Spring 7 HttpHeaders API 编译问题已修正。这些属于测试搭建问题，不当成生产缺陷。

循环 cause 的网关提取有确定性测试；题目服务仅证明局部处理器不会无限循环，Spring MVC 在进入
处理器前可能递归失败，未宣称整个 MVC 链支持循环 cause。已记录该限制，没有修改框架。

### 命令与实际执行

- Python：`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/ci -p '*_test.py'`，
  124 tests / OK。本机网络测试最初受沙箱限制，解除本地测试限制后完整执行。
- Java：从 apps/server 以 Homebrew JDK 21 执行 `./mvnw clean verify`，BUILD SUCCESS，229 项中
  228 通过、0 失败、0 错误、1 跳过。gateway 78、problem 64、identity 8、user 30、submission 11、judging 38。
  所有本次新增测试和 MySQL/Redis/Kafka 测试实际运行。
- 唯一跳过：RealLinuxJudgeIntegrationTests 因未设置 CHERRY_REAL_JUDGE_URL 不运行；没有声称这项已通过，
  不把 CI 的其他 Linux 测试冒称为这个 Java 测试。

本机日志：`/tmp/work059-python-before.log`、`/tmp/work059-python-after.log`、
`/tmp/work059-java-before.log`、`/tmp/work059-problem-before.log`、`/tmp/work059-java-verify.log`。
`/tmp/work059-observer-baseline.log` 文件名虽含 baseline，但实际运行已包含 observer 完整实现，
7 项全部通过；缺失诊断的证据是其内部关闭 observer 的对照组，不声称透传 stub 曾通过行为验证。

### 独立复核与最终补测

Boole（Agent 01a0c83c-5e82-7361-8434-bd99a92727c3）独立复核 Python 与 Java 诊断代码，
12 项定向 Python 测试、144 组畸形字段与优先级探针通过。随后提出两项建议并复核修正：

- P2：原始及包装的 RequestRejectedException 应交回原安全链。已补入原因分流；新增三种异常包装的
  HTTP 对照测试比较有/无本次 advice，实际均保持 400，并断言重新抛出的是原异常。
- P3：明确 4xx 不应标为未知服务器故障。新增五个 4xx 反例在旧 observer 上失败，修正后不记录
  unexpected 事件；500/503 与未知异常仍记录，原异常与响应不变。

修正后从 apps/server 执行 `./mvnw -pl gateway-service,problem-service -am verify`，
gateway 85、problem 67、identity 8，共 160 项全部通过，无跳过，打包成功。
日志为 `/tmp/work059-final-boundaries-verify.log`；4xx 反例日志为 `/tmp/work059-observer-4xx-before.log`。
这轮覆盖最终修改的模块，其他模块沿用上方全量 clean verify 的实际结果。
独立复核只评价已实现的诊断补丁，不声称原事故已经定位。

### 完整 CI 与有限复现

候选实现提交为 bd7fec836c33465a52b762b0e6249f9f2a6daf49。每轮独立运行，保留各自 runId 和
attempt，不自动重发写请求。以下状态来自下载的 summary.json、business report.json 和
preparation-requests.json，已核对 sourceSha、runId、runAttempt。

| 轮次 | CI / attempt | 结果 | 目标公开 PATCH |
|---|---|---|---|
| 1 | [35707446283](https://github.com/charon2121/cherry-oj/actions/runs/35707446283) / 1 | 12 个 job 全绿；basic 5、kernel 63、native 10、business 15 均通过 | HTTP 200，51,989,874 ns；requestId=req_8cf7347c5d6d4c978cfa78707ba3581b |
| 2 | [35708448036](https://github.com/charon2121/cherry-oj/actions/runs/35708448036) / 1 | 12 个 job 全绿；basic 5、kernel 63、native 10、business 15 均通过 | HTTP 200，45,206,472 ns；requestId=req_c5c607850d7e4a709bb2dd808f84d301 |

两轮 preparation-requests.json 各 21 条记录，全部为 2xx；每轮目标 PATCH 只有一次，后续公开题目 GET
也为 200。两轮 business cleanup.status=PASS、cleanup.json confirmed=true，均没有 failure.json 或
business-service-facts.json；成功路径未触发失败诊断，不能把文件缺失描述为「服务没有异常日志」。
同提交的 [冷下载检查 35707446284](https://github.com/charon2121/cherry-oj/actions/runs/35707446284)
也通过，该独立工作流不计入三轮完整 CI。

首轮产物为 sandbox-summary-35707446283-1 与 sandbox-business-35707446283-1，SHA-256：

- summary.json：e4e7b9eac817035a9381914b9325d4cf3b89068facbee244fa2c14fe6217f4c0。
- report.json：482c45d87b202cc36bbfbf353263dca25434d8725af81eac4ac93361d3e8452e。
- preparation-requests.json：eafb0cd526d6d70236eec26f0c3c6bc65b84db003f3dcb9cbe6df8f5e0676ddb。

第二轮产物为 sandbox-summary-35708448036-1 与 sandbox-business-35708448036-1，SHA-256：

- summary.json：e2915dcc81fc229520bcd9e4edcbf619322268dc0ee3a4ee2d038a1251fbc01b。
- report.json：1af498bc432eb5fb6153ec5066059f9c208894a9a7096d97b951a83050e5f80d。
- preparation-requests.json：2859d45afcf221e3e757f084a3b2238f46282db3c8366171dfccd7adfad1a56c。

第二轮通过 workflow_dispatch 独立启动。第三轮由本次证据文档提交的 push 触发，代码保持 bd7fec8
的实现；运行结果在交付时核对并报告。不再为单纯补记第三轮结果继续推送、触发第四轮。
第三轮未复现时停止主动重跑，后续同类真实失败再按本方案分析，不自动启动后台监控。
前两轮全绿只能证明本次运行通过，不能满足 AC-003，不自动签署验收。

## 范围检查

方案准备阶段只改文档，原有 Python 增量保持原样；
初始 SHA-256：business_journal.py 为 dbf5e11a1408e067e4a6ca968292f708c4c8a5efad89f4dd8de199c65a6b8146，
business_test.py 为 fa82ea152d28a9751ba4efd2e0b746f1fb2111d2e017455753f528a45783c40c。
实施阶段在这些增量上修正诊断，并修改 TASK-132 允许的 Java api 与测试路径。
未改公开契约、配置、数据库、依赖或其他工作；人工意图闸由用户签署，未代签验收。

## 遗留问题

根因待带证据的失败定位；前两步诊断修复不能替代整项 AC-003。

## 剩余风险

有限次数的 CI 可能无法复现偶发故障；届时停止主动重跑，保留未完成结论。
新增未知异常兜底需要特别保护框架 HTTP 和鉴权状态，具体组件场景见 TASK-132。

## 结论

诊断与异常边界修复完成，本地验证和前两轮完整 CI 通过，并记录真实 Linux Java 测试的限制。
原故障根因尚未确认，整体 partial，TASK-132 保持 doing；本次文档提交只再触发最后一轮主动复现。

## 变更记录

- 2026-09-22：状态变更：draft → review。原因：本地修复与复核证据已写完，原故障根因与完整 CI 仍待验证
- 2026-09-22：补入 bd7fec8 的两轮完整 CI、目标 PATCH 请求事实与产物摘要；回归要求满足，根因要求未满足，整体保持 partial。
