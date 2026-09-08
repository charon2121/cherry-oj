---
id: "DESIGN-038"
type: "design"
title: "题目内自定义输入运行"
status: "checked"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["FEATURE-012", "EXPERIENCE-020"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-08"
---

# DESIGN-038：题目内自定义输入运行

## 背景

依据 FEATURE-012、EXPERIENCE-020、docs/architecture.md §9 与 docs/data-model.md §8.3。全局基线已选择同步 trial，不创建 Submission/JudgeTask/Kafka。

已核对：Go JudgeRequest 支持 mode=trial 与 cases[].input；expected 缺省返回 RAN。flow.caseResult 当前仅在 RE 把 stderr 摘到 message，正常退出时 stderr 丢失，CaseResult 只有 output 没有 stderr。Java JudgeGateway.JudgeRequest 尚无 cases；其 CaseResult 只读 idx/verdict/资源。FormalWorker 使用自身 Semaphore 与虚拟线程执行器。已有 profile 预算按正式 totalCount 计算，不能直接把多测例总预算当本次自测预算。

## 整体方案

浏览器 POST /api/custom-runs → Gateway → submission-service POST /api/custom-runs → judging-service POST /internal/submission/trials → Go POST /judge(mode=trial) → 既有 sandbox。不直连 sandbox，不复用正式创建提交端点，不调用任何语言进程在 Java 宿主执行。

submission-service 验证用户身份、公开当前题目版本、C++ ACM 源码，再复用 ProblemJudgeSnapshot/ExecutionProfile 的解析与校验。为现有 profile 请求增加可选 purpose=trial，省略时保持 formal 原预算规则；trial 按单 case 预算计算，仍核验实际题目/数据部署/环境/标定，无须读取隐藏文件内容。purpose 不由浏览器决定。

judging-service 用服务身份保护 trial API，从已验证 profile 选择存活且同指纹节点，向 Go 传一份 input，永远不填 expected；服务端构造 mode/limits/environment，客户端不能指定节点 URL、容器指令、路径或环境。返回后校验环境指纹、单 case 数量、允许状态及有界字段。采用独立 Trial DTO/adapter，避免为 trial 放宽正式安全投影。

## 接口与状态

实施首步在 contracts/web-api.openapi.json 定义 POST /api/custom-runs。Header 使用既有 CSRF 与必填 X-Expected-User-Id；body 为 problemId、expectedProblemVersionId、languageId=cpp、source、inputText，不允许用户传 owner、limit、mode 或 expected。采用普通 ApiSuccess/Problem 信封和 no-store；requestId 只用于本次链路关联。

成功响应 RunData：problemId、problemVersionId、problemVersionNo、languageId、status、cpuNs?/memoryBytes?、stdout?/stderr?、compileDiagnostic?、effectiveLimits。Output 为 text、capturedBytes、truncated；计数是 judge 实际收到文本的 UTF-8 字节数，不声称是进程无限输出的完整总量或原始二进制流的大小。编译未执行用户程序时省略运行输出/资源。源码和 input 不回显，由页面冻结本次快照。

内部 RAN 映射 public COMPLETED（未校验答案）；CE/RE/TLE/MLE/OLE 映射 COMPILE_ERROR/RUNTIME_ERROR/TIME_LIMIT_EXCEEDED/MEMORY_LIMIT_EXCEEDED/OUTPUT_LIMIT_EXCEEDED，均为 HTTP 200 业务结果。SE/环境失效/非预期返回为 503；整链路期限耗尽为 504，不能映射 TLE；参数错误 400、字节超限 413、版本变更 409、不可访问题目 404、不可自测的时限配置 422、频率或并发超限 429（带 Retry-After）。无预期输出的 trial 若收到 AC/WA/PE，按内部契约不符拒绝。

新增 contracts/custom-run-internal.schema.json 保存 submission↔judging DTO；调整 contracts/execution-profile.schema.json 表达 trial 预算；Go 与 Java 类型均依契约。新增字段必须兼容旧 submit 调用和消费者。

## 模块与数据

judge.schema.json 的 CaseResult 加可选 stderr（同受控 Output 结构，描述明确其流类型）；flow 只在 trial 附加标准错误，stdout 继续走 output。stderr 与 RE message 分离：即使程序成功退出也必须可见。编译诊断沿现有 message 安全限长；不把 SE 内部错误和宿主路径直接返回 Web。

保留正式 submit 的输出公开规则及 verdict 聚合。sandbox RunResult 已有 stderr，不增加用户/题目/运行记录语义，不修改隔离机制。现有配置控制真实 stdout/stderr 捕获和执行资源，跨语言 UTF-8 安全截断；非法编码按既有 JSON 文本链路替换，并在输出帮助中明确“纯文本预览，非二进制无损输出”。不承诺检测已在上游丢失的原始字节。

## 大小、预算与容量（首版推荐，待意图确认）

- source ≤256 KiB UTF-8；inputText ≤64 KiB，空串合法，空白不 trim。
- stdout/stderr 各最多向 Web 返回 16 KiB 文本，编译诊断 ≤8 KiB；既有节点捕获上限继续生效，超过输出限制是 OLE，超过展示上限仅标记截断，二者不得混淆。
- 控制器原始请求 body 上限 2 MiB；响应客户端缓冲有界且覆盖合法 JSON 最坏转义体积。精确 UTF-8 上限不能只靠字符 @Size。
- 使用当前环境的单 case CPU/内存/clock 限制，编译时限沿节点配置；本次 trial 总执行预算≤45s，超过则在执行前返回配置不支持，不暗自降低题目限制。profile 单 case 预算包含编译、clock 推导及开销，所有乘加需防溢出。
- trial 调用 deadline 45s、submission 外层50s、Gateway55s、Web60s；全链路传剩余预算，不每层重置45s。执行前耗时计入总期限。节点编译配置与 profile compileBudget 必须校验协调，不满足时拒绝启用。
- 每账号最多1个在途，滑动60s最多10次；复用 Gateway Redis 原子准入（限流元数据无源码/输入），多标签/多 Gateway 实例有效。账号键取认证身份，绝不取浏览器可伪造字段。429 不触发实际执行；Redis 不可用 fail closed 为503。
- judging-service 独立 executor + 非阻塞 trial semaphore，默认全实例本地1个并发，无等待队列；不占 FormalWorker 线程/任务槽。首版部署单 judging 实例，扩实例前须把总额改成分布式或明确实例配额，禁止声称本地 semaphore 是集群总额。
- 单节点共享 sandbox 仍可能竞争 CPU；不承诺物理资源完全隔离或不增加正式提交时延。联调须验证自测负载下正式任务仍能推进并记录延迟；若容量不足，本轮先关闭 trial 开关，不能靠增大线程无限放行。

## 安全与失败

本轮无运行持久化与幂等结果仓库；POST 禁止 WebClient/网关/前端自动重试。同步连接丢失意味着结果未知，用户之后重跑是新运行。账号准入 lease 用随机 owner token + TTL≥完整调用上限与清理余量（建议65s），正常完成按 token 释放；结果不确定/断连不提前释放，避免另一标签再发导致重叠。TTL不能作为杀进程保证，node context/clock 限制必须独立生效。

Java HTTP deadline、Go request context、sandbox 执行/清理联动；验收要证明取消或超时后有限时间释放资源。不能仅测试 HTTP 返回了超时。不可控断连仍由节点 clock 兜底。

前端关闭 mutations 自动 retry，使用账号×题目版本作用域和递增请求序号丢弃迟到响应；组件保持代码编辑器挂载。输入/输出仅内存，既有源码草稿逻辑不动。私有字符串 DTO toString 脱敏，HTTP body不记录，输出只以文本显示，ANSI/控制字符不会作为可执行终端内容解析。内部诊断用白名单安全消息；用户 stderr 不用正则误删普通文本。

## 监控与部署

契约测试→Go trial/submit 回归→Java 路由/鉴权/执行校验→真实 Linux 容器→Web fixtures+真实账号烟测。特别验证成功stderr、空输入、截断/OLE、版本冻结、跨账号、原子限流、并发满、deadline/取消、正式历史数量不变。

后端重启属于后续单独明确的本地联调授权；本设计回合不重启。交付时检查实际运行二进制/路由而不是仅编译产物，避免重演 WORK-043 旧进程405。先部署兼容的 judge/服务端，再打开前端及 custom-run 开关；旧 judge 无 stderr 能力时不能静默宣称完整功能，必须版本配套验证。无数据库迁移。

## 目标与限制

目标遵循 FEATURE-012 单输入自测与正式提交隔离；不引入自测持久化、后台队列或新语言。限额和期限的推荐值集中于上文，人工意图确认前不可实施。

## 迁移与兼容

无数据迁移；profile purpose 缺省保持旧行为，trial stderr 为可选字段，旧 submit 请求仍有效。生产类型与消费者逐个验证，新增 trial 以版本配套及开关控制，不依赖旧节点返回新字段。

## 备选方案

同步 trial、独立异步任务、临时正式提交的比较见 DECISION-025；本设计采用全局架构已有同步边界。

## 风险与重审条件

共享 sandbox 存在物理资源竞争、客户端断线无法确认结果、非文本输出不能无损回看。若需要硬隔离、持久恢复或分布式总并发，先重新确认调度/数据方案，不沿用单实例假设。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：完成现有能力核对、stderr 补齐与有界同步执行方案
- 2026-09-08：结构与内容校验通过，由工具置为 checked。
