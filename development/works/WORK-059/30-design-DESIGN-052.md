---
id: "DESIGN-052"
type: "design"
title: "业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位"
status: "checked"
work: "WORK-059"
owners: ["team/server"]
depends_on: ["ISSUE-020"]
related: []
implements: ["ISSUE-020#AC-001", "ISSUE-020#AC-002", "ISSUE-020#AC-003", "ISSUE-020#AC-004"]
verifies: []
tags: []
created_at: "2026-09-15"
updated_at: "2026-09-22"
---

# DESIGN-052：业务闭环中 PATCH /api/admin/problems/{id} 偶发 500 且证据不可定位

## 背景

依据 [ISSUE-020](./10-issue-ISSUE-020.md)。2026-09-22 用户要求解决此问题；本轮先核对代码和现有诊断增量，
补齐方案与 TASK-132 的范围，交由用户签署意图闸。本工作仍沿用原分层文档格式。

调查需要读取 apps/server 的网关、题目服务、身份与日志支撑代码，deploy/sandbox-linux/ci 的请求、
日志和结果导出代码，以及对应 contracts、docs 和 CI 定义。TASK-132 原先的路径占位符无法约束实施，
因此先明确只读调查范围；拟写入仅限本工作文档、CI 诊断、网关安全异常日志与题目服务异常边界及其测试。
根因修复若需要这些范围以外的文件，先依据实际失败证据补充方案，不预先授权全仓修改。

## 目标与限制

让失败具有可关联、可分类、无敏感正文的诊断证据，并据此解决原 PATCH 故障。
已知诊断缺口可以构造稳定反例；历史 500 的实际异常仍未知，不把诊断修复等同于根因修复。
不改变题目公开条件、乐观锁、事务、鉴权、HTTP 成功响应、数据库或依赖版本。

## 整体方案

### 一、记录和导出同一次失败

网关 ApiProblemHandler 的未预期异常路径使用现有 SLF4J 结构化日志，写入固定 event、
request_id、error_type；如需原因链和应用栈，最多 8 个异常类名、12 个 com.cherryoj 的类名/方法名，
每项长度受限，循环 cause 必须停止。不传入 Throwable、不调用异常正文输出、不记录请求体或凭据。
辅助逻辑保留在 gateway-service 的 api 包，不扩展跨服务 logging-support。

business_journal 只在允许的事件来源上消费这些结构化字段，继续输出现有 requestId、thrown、frames
形状并遵守总量限制。兼容当前已发布旧日志时，要求 ApiProblemHandler 的精确 logger、ERROR 等级、
完整固定消息模板及完整合法 requestId/类名；不得对所有正文使用无边界 findall。结构化字段优先，
不让正文覆盖它们。普通 ERROR 可以留下安全元信息，不能推导出任意正文中的异常类名。

通过响应头 requestId → preparation-requests.json → business-service-facts.json 的关联定位；
有 traceId 时作为补充。没有日志或未匹配字段时保留缺失事实，不拼出虚假的关联。

2026-09-22 实施调查确认了覆盖缺口：CSRF 读取 Redis Session 失败可能发生在 controller 之前，
不经过 RestControllerAdvice。相同写范围内增加只观察未处理异常的 WebExceptionHandler，位于
框架默认错误处理器之前，记录同样有界的诊断后继续传播原异常；不改响应、状态、鉴权或重试语义。
用真实 WebFlux/CSRF 组件和可控 Session 失败验证，包括请求尚未执行、下游写入成功但 Session 保存失败
两个时点。两者均是可达候选路径，不据此认定原事故由 Redis 引起。

### 二、修复两个独立的错误边界

- CI failure_kind 按固定公开错误码映射 INTERNAL_ERROR、BAD_GATEWAY、GATEWAY_TIMEOUT；
  保留现有三种 SERVICE_UNAVAILABLE 文案分类。未知、畸形或恶意响应维持 UNCLASSIFIED；
  任何原始 code/detail 不直接进入产物。
- problem-service 为未预期 Exception 返回安全的 500 application/problem+json，复用 INTERNAL_ERROR
  语义和固定摘要，记录有界、安全的异常事实。沿用内部响应格式，不引入浏览器成功 envelope。
  具体业务、校验、上传异常仍走已有 handler；框架 HTTP 异常及安全异常不得被通用处理器改成 500。
  通过真实 MVC 分发测试证明状态保持，不只直接调用 handler。网关仍将上游 500 映射为 503，
  用已有 gateway 映射测试保护该行为。

### 三、定位与根因修复

先用可控异常检验完整诊断链，再运行真实业务闭环。需要复现时最多进行三轮已授权的完整 CI 运行，
每次保留 runId、attempt、提交 SHA 与有界报告；首次失败立即分析，不以重跑覆盖结论。
不自动重发 PATCH，不提高超时或减弱断言来消除红灯。

拿到证据后定位异常抛出点、发生在写入前后哪一段，以及对应状态；新增确定性反例后修正实现。
超出 TASK-132 写范围时先更新本方案与任务范围。三轮未复现则停下主动重跑，记录 AC-003 未满足，
保留工作未完成，不自动启动后台监控，也不将暂时全绿解释为根因已解决。

## 模块与数据

网关负责公开响应及请求标识；problem-service 负责自己的错误响应和事务；Python 夹具只读取、
分类并导出安全证据。不跨库、不修改题目数据模型、不把错误响应直接透传给浏览器。
读写路径以 TASK-132 为准。允许新增网关 api 包内的局部诊断辅助代码与两端组件测试。
工作进度更新由 scripts/work 同步 development/WORKS.md，仅更新 WORK-059 的自动生成总览行。

## 接口与状态

成功行为不变。网关已有 INTERNAL_ERROR=500、BAD_GATEWAY=502、SERVICE_UNAVAILABLE=503、
GATEWAY_TIMEOUT=504；本轮不新增公开错误码或改变映射。problem-service 内部未预期异常增加稳定 code，
只返回固定安全信息。CI 分类是报告字段，不作为产品 API 字段。

没有根因证据时 VERIFY-060 的 result 不能为 pass，TASK-132 不能声明整项 done。

## 安全与失败

诊断输入按不可信内容处理：允许的 logger/event 与完整字段形状同时满足才提取异常信息。
测试包含合成口令、JWT、路径、请求正文、错误码、类名后缀和长输入，均不能混入导出结果。
保持日志尾读、行大小、事件数、类名数和栈帧数上限；诊断失败不能取代原请求失败。
新增异常处理不得泄漏 cause/message、吞掉鉴权失败或将框架 4xx 改成 500。

## 监控与部署

沿用 Stack.diagnose 的 business-service-facts.json 和 preparation-requests.json 产物，不导出原始
Java 日志，不新增采集服务。按现有 CI 启动隔离测试栈，不操作用户日常环境或 ACTIVE 判题环境。
Java 修改须运行聚合工程 verify，并核对测试实际执行；现有 CI 的 Java package 跳过测试不能替代它。

## 迁移与兼容

无迁移。旧日志格式由精确模板兼容，新日志使用结构化字段。内部未知异常从默认响应变成有 code 的
安全响应，但状态仍为 500。正常请求、既有错误和公开契约保持原有语义。
回退时只撤销 WORK-059 的实现提交，不撤销 WORK-058 的采样器修复，也不恢复宽泛正文提取逻辑。

## 备选方案

只增加超时或重跑请求无法证明原因，且 PATCH 可能已经提交，拒绝采用。
导出完整日志或 Throwable 能提供更多信息，但可能携带口令、JWT 与 SQL 参数，采用有界事实代替。
只读 message 的宽泛正则改动小，但已复现来源混淆、非法后缀被截断和 requestId 丢失；
采用结构化主路径与精确旧模板兼容，接受少量 Java 日志改动以获得确定边界。

## 风险与重审条件

根因未知不阻塞前两步的确定性修复，但阻塞整项验收。新异常兜底可能抢先处理框架或安全异常，
必须用 HTTP 组件回归覆盖这些路径。日志有界意味着部分栈可能缺失，记录缺失而不导出敏感正文。
若失败发生在 controller 之前、字段无法关联或指向数据库/身份/依赖问题，先保存证据，
明确新增范围与行为影响，再推进相应根因修复。

## 变更记录

- 2026-09-22：状态变更：draft → review。原因：修复方案与验证边界已补齐
- 2026-09-22：结构与内容校验通过，由工具置为 checked。
