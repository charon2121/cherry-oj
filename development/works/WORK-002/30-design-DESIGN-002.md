---
id: "DESIGN-002"
type: "design"
title: "交付 C++ ACM 答题闭环"
status: "checked"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["FEATURE-001", "EXPERIENCE-001"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-09-07"
---

# DESIGN-002：正式提交闭环方案

## 背景

本方案重整 2026-08-24 的占位设计，依据当前代码与既有全局架构；提案尚未人工批准。

| 代码证据 | 已有能力 | 本轮缺口 |
|---|---|---|
| problem-service 的 PublicProblemController / ProblemPublicationService | 公共题面、发布事务及管理操作 | 服务身份专用的不可变 ProblemJudgeSnapshot API |
| judging-service 的 JudgingReadinessService / JudgeNodeRegistry / NodeDeploymentService | 环境、校准、节点与每节点数据回执 | 普通提交可用的 ExecutionProfile 解析与冻结环境路由 |
| judging-service 的 JudgeGateway.JudgeResult | verdict、环境指纹、CPU、内存、score | 编译诊断、测试点结果的有限解析与安全投影 |
| submission-service 的应用类、安全配置、pom.xml | 用户 JWT 资源服务骨架；尚无提交数据库/Kafka依赖 | Submission、JudgeInput、幂等、Outbox/Inbox、创建与查询 |
| identity-security-support | 用户身份与远程 JWKS 校验 | 后台异步调用的独立服务身份，不能复用用户 token |
| contracts 下既有 submission / judge-input / judge-events / snapshot / profile | 已有领域契约 | 公共提交操作、内部 HTTP 路由和严格公开结果契约 |
| Gateway 与 WORK-041 题目页 | 登录、公共题目、编辑器、草稿 | 提交 BFF、请求恢复与结果轮询 |

`docs/architecture.md` 和 `docs/data-model.md` 是目标职责基线，不意味着其中的表、Worker 和接口已落地。
本轮不把仅有 schema 或管理端 readiness 误记为正式提交能力。

## 目标与限制

沿用浏览器 → Gateway → submission-service → Kafka → judging-service → Go judge → sandbox，
生命周期事件回到 submission-service，浏览器轮询。各服务只写自己的 MySQL 数据库，不跨库、不把源码放进 Kafka。
只支持 cpp + ACM，源码按 UTF-8 最多 256 KiB；前后端一致校验，已有本地草稿恢复行为保持。
协议变化先契约、后各语言 DTO、后实现；原有用户认证、节点协议、/judge 与 /run 语义不改。

## 整体方案

1. Gateway 验证 Session、CSRF 和强制改密状态，转发已验证的用户身份。请求不可指定 userId。
2. submission-service 先按 `(userId, Idempotency-Key)` 查询已有请求；已成功受理的同键同摘要直接返回旧记录，不能因当前题目或环境变化拒绝重放。
3. 首次创建从 problem-service 解析当前 ACTIVE/PUBLIC、PUBLISHED、ACM/cpp、READY 测试数据快照；
   客户端仅传 `expectedProblemVersionId` 作为前置条件，不允许它选择历史送判版本。不同则 409，不创建记录。
4. 从 judging-service 解析 ACTIVE 环境、启用语言、在线节点、该节点当前 session 的 READY 数据摘要、
   同题目版本/语言/环境的 VALID 校准与绝对限制。失败不建提交，不给默认限制。
5. 同一 submission 数据库事务插入 Submission(PENDING)、不可变 JudgeInput、幂等记录与 JudgeRequested Outbox。
   网络快照解析不占长事务；输入一旦受理，随后发布/切换不会改写它。并发同键靠唯一约束收敛，冲突后重读原记录。
6. Outbox 发送引用事件。judging-service 的 Inbox 与 JudgeTask 创建同事务；Worker 领取租约后在事务外拉取 JudgeInput、核验摘要、选节点并调用 Go judge。
7. 只有当前租约的尝试能在 judging 数据库写有效结果与 lifecycle Outbox；submission-service 原子消费 Inbox 并推进读模型。
8. GET 返回当前账号自己的公开投影；前端停止于终态，失败退避，刷新沿用同一编号。

## 模块与数据

submission-service 新增 Flyway 正向迁移：submission、judge_input、submission_request、outbox_event、inbox_event。
judging-service 在已有迁移后追加 judge_task、judge_attempt、outbox_event、inbox_event，禁止重写 V1/V2。
采用已有 MySQL 技术栈，不另选数据库。唯一键、条件更新、时间戳与行版本遵循 data-model 基线。

JudgeInput 保存 schema v2 全部必填值；完整程序与原源码在 ACM 下相同，源码摘要按 UTF-8 计算。
Submission 不作为公共 DTO 直接序列化；内部快照中的环境标识、路径等也不通过 BFF 透传。

公开结果展示总 verdict、时间、语言、题目版本、允许的 CPU/内存、可用时的测试通过/已执行数量、
经处理的 CE 诊断及 SE 安全摘要。实际返回测试点数不能冒充全部测例数；缺失时不造零。
Go 原始 CaseResult 可含 output/diff/message，进入 Kafka 前必须投影删除正式输出、差异、名称、任意运行期消息；
submission-service 再次白名单校验。现有 judge-events 的 result 引用宽松 JudgeResult，TASK-002 必须增加
事件专用的安全结果定义与负向样例，避免“schema 接受”被误解成“可传播全部字段”。
不需要为此改变 Go /judge 返回契约或 sandbox。

## 接口与状态

下列为待审 API 设计，TASK-002 将其落到 contracts 的唯一真源，不能由后续任务各自命名。

| 边界 | 提案 | 主要规则 |
|---|---|---|
| 浏览器创建 | POST /api/submissions；Idempotency-Key；problemId、expectedProblemVersionId、languageId、source | 首次 201，同键重放 200，同键不同内容 409；快照冲突 409；非法模式/语言 422；依赖不可用 503；未登录 401 |
| 浏览器查询 | GET /api/submissions/{id} | 200 状态；他人/不存在统一 404；仅本人，不赋予 ADMIN 跨用户例外 |
| 创建结果确认 | GET /api/submission-requests/{key} | 当前账号内查键对应提交；不存在 404；不会创建，响应不可缓存 |
| problem 内部快照 | POST /internal/submission/problem-snapshot | 仅 submission-service；既有 snapshot schema，补齐请求 schema |
| judging 内部配置 | POST /internal/submission/execution-profile | 仅 submission-service；既有 ResolveExecutionProfileRequest |
| submission 内部输入 | GET /internal/judging/judge-inputs/{id} | 仅 judging-service；JudgeInput v2，no-store |

BFF 使用既有成功 envelope、RFC 9457 错误与一致 requestId。用户身份只授权自己的业务资源。
源码上限失败 413；任何错误不能回显请求源码或下游堆栈。GET 恢复键 404 不能证明在途 POST 不会成功，
恢复操作必须继续使用同一键与原正文。幂等摘要包含 expectedProblemVersionId 与所有业务输入，
不得只散列正在编辑的草稿。MVP 不自动淘汰幂等记录；后续保留期限需独立决定。

Submission 保持 PENDING → JUDGING → DONE；完成事件可先于 Started 到达，允许 PENDING 直接 DONE。
终态不可回退；JudgeFailed 表示重试耗尽后的最终故障，映射 DONE + SE。
同一 submission 只有一个 JudgeTask，attemptNo 单调递增。旧 leaseToken 写入必须失败；
过期尝试可以留下审计但不得产生有效完成事件。消费者校验 eventId、aggregateId、Kafka key、taskId、attemptNo，
重复事件无副作用，Started 不覆盖已完成结果；非法事件进入既有架构规定的死信处理并给出定位记录。

## 安全与失败

服务身份沿用 DECISION-021 已确认的可信内网口径，内部允许 HTTP。本轮建议为三条调用链配置
独立的高熵服务 token：submission → problem、submission → judging、judging → submission。
每个接收端只接受属于该调用链且匹配指定路由的 token，恒定时间比较；凭据通过受保护的环境配置注入，
不入 Git、数据库、消息、日志或浏览器。不复用用户 JWT、用户签名密钥或 Judge 节点 control token。
无需 PKI、签名挑战或新的认证中心；这项新增凭据选择仍待意图闸审核。

独立安全链只匹配本轮内部端点，按 route 明确允许调用方；缺失/错误凭据 fail closed。
USER/ADMIN JWT、其他调用链 token、节点 control token 都不能读取 JudgeInput；调用方头字段不能自证身份。
配置可短期同时接受旧/新 token：先配置接收端，再切发送端，确认新凭据生效后撤旧；重启不能随机更换凭据。
现有用户认证与节点控制链保持原义。异步 Worker 每次读取配置凭据，不依赖提交者 Session 或 JWT 寿命。
公开网络部署或共享内网威胁模型变化时需另行重审，不在此回合扩大为身份系统重构。

节点选择必须匹配冻结 environmentId/fingerprint、在线 session、语言及数据摘要回执；同环境多个合格节点可择一。
不能改回宿主机共享目录，也不能硬编码当前 ACTIVE endpoint 执行旧任务。环境切换后允许匹配的旧环境节点完成在途任务；
无匹配节点有界重试后 SE。测试数据更新不得覆盖旧版本。

重试只针对传输、节点暂失等系统故障；WA/CE/TLE 等不重试。建议默认最多 3 次有效执行尝试，两次重试分别退避 1/5 秒，
任务排队/恢复总截止从提交 createdAt 起算，建议默认 10 分钟；全部配置启动校验为正值并记录实际验证值。受理前校验合法最大执行预算能落在总截止内，预算过大时明确拒绝配置，不把合法程序误判为 TLE。执行 HTTP 超时根据冻结限制、
测试点数量与编译预算有界计算，不机械用 60 秒截断合法大任务；TASK-002 在契约阶段补齐内部预算所需元信息，TASK-076 实现其来源，不暴露测试内容。执行租约定期续约，失去租约丢弃有效提交资格。
系统整体停机时不承诺实时终态；恢复后扫描过期任务，按持久截止时间收敛至 SE。
Outbox 发布采用持久重试，不因进程退出丢失；以 eventId 去重，不宣称恰好一次执行。

## 监控与部署

沿用 requestId/trace 上下文及结构化日志；仅记录 submissionId、taskId、attempt、状态、稳定错误码与耗时。
Outbox 发布积压与任务超期在重启后也必须被发现；无法解析的事件进入死信并按 eventId/提交编号定位，
修复后用保留同一业务编号的重放流程恢复，不能通过新建提交或清队列掩盖。Kafka header 承载 traceparent；不把 transport trace 字段塞入 Go 请求正文。验证报告统计创建、完成、SE与耗时，
不新增监控基础设施。毒消息的定位与重放步骤写在 PLAN/VERIFY，不能靠清空队列恢复。

## 迁移与兼容

本轮增量建表与新增 API；不清库、不修改现有题目/账号记录，不重写已执行迁移。公共题面结构保持兼容，
expectedProblemVersionId 是创建请求的并发前置条件，区别于选择历史版本，批准后再同步全局架构说明。
事件安全结果的收窄需核对所有现存生产者/消费者；当前未找到正式消息链实现，若实施时出现外部消费者，
必须暂停并调整版本迁移方案，不能静默收紧已部署事件契约。

## 备选方案

同步等待 Judge 最少代码，但违反既有异步基线且浏览器超时后恢复困难；一次实现运行、历史、统计扩大验收面。
二者本轮不推荐。服务身份选项与取舍集中于 DECISION-002。

## 风险与重审条件

必须升级方案的情况：需要改 Go 判题语义/节点协议、自动创建校准、默认放宽限制、跨库读取、CORE、
开放隐藏输出、引入新认证中心、修改用户会话协议，或无法在有界重试内正确收敛任务。
具体参数可以在受约束范围内按验证调优并记证据；产品行为、契约或数据权限改变须先更新上游文档。

## 变更记录

- 2026-09-07：结构与内容校验通过，由工具置为 checked。

### 实施补充：受理前预算校验

ExecutionProfile 请求增加 totalCount，响应给出 executionBudgetNs；JudgeInput 冻结该预算。judging-service 以校准限制、测例数、部署配置的编译预算/墙钟倍率/单点通信余量计算，超过总截止时在受理前拒绝。传输预算不改变 Go 判题限制；部署者必须核对墙钟倍率与编译预算覆盖对应环境的实际策略。WORK-002 隔离验证使用现有 Go 默认策略（倍率 10、编译墙钟 20s），通信侧预留编译 30s、每点额外 1s。新环境改变策略时必须同步并验证这些预算配置，不从指纹猜测策略。

### 跨标签页账号切换的一致性前置条件

创建请求必须携带 X-Expected-User-Id，值为编辑器所属账号；Gateway 从已验证会话解析账号并比较，不匹配返回 409 SESSION_CHANGED。该头只拒绝过期操作，绝不提供权限。CSRF 刷新后的重试仍携带同一账号，防止原账号源码在新会话下受理；内部提交服务继续仅信任 JWT。
