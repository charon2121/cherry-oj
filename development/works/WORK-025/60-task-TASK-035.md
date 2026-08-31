---
id: "TASK-035"
type: "task"
title: "接通 Gateway 题库与题目管理 API"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "TASK-034", "TASK-040"]
related: []
implements: ["FEATURE-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "contracts/web-api.openapi.json", "apps/server/TOOLCHAIN.md", "apps/server/gateway-service", "apps/server/problem-service"]
write_paths: ["apps/server/gateway-service", "development/works/WORK-025"]
forbidden_paths: ["apps/server/problem-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-035：接通 Gateway 题库与题目管理 API

## 任务目标

让 Gateway 按 OpenAPI 同时提供匿名题库和 ADMIN 管理端，安全流式代理测试数据上传/下载，并把两个资源
服务的 DTO、cursor、鉴权和失败严格映射为公开协议，不建立题目真源。

## 依据

落实全部 `FEATURE-007` Gateway 边界，消费 TASK-034/040 problem API；judging 状态由 problem-service
编排后返回。内部 DTO
只属于 client 边界，不与其它模块共享 Java 类。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- problem-service base URL/timeout 的强类型配置与启动校验。
- 有超时的 WebClient、内部列表/详情 DTO 解码、ProblemsController 和公开响应映射。
- q/difficulty/tag/codeMode/language/sort/cursor/size 参数校验与透明转发，request ID/meta 一致。
- 公开成功、统一 404、非法输入、下游超时/不可用/非法响应的 WebTestClient/假服务测试。
- ADMIN controller/client：Session/首次改密/role/CSRF、rowVersion/资源状态与稳定 Problem 映射。
- multipart 上传和 ZIP 下载的流式转发、Content-Length/超时/取消/背压/response header 处理。
- 敏感字段、JWT/Session 依赖和内部错误正文不进入浏览器响应或日志的负向测试。

## 完成标准

- [x] 匿名和已登录请求得到相同题目事实，不触发 token refresh，不要求 Redis 中存在浏览器 Session。
- [x] 列表 meta 使用 cursor pagination，nextCursor 保持 opaque；Gateway 不解码、不重排、不计算总数。
- [x] header `X-Request-Id` 与 body `meta.requestId` 一致，现有 request ID 规则不变。
- [x] problem-service 404 只映射 `PROBLEM_NOT_FOUND`；连接、超时、5xx 和 contract 错误分别为稳定、脱敏
  的 503/504/502，均不伪装成空列表或 404。
- [x] 下游返回额外敏感 canary/未知字段时，公开字段白名单映射不传播它；内部 URL/body/JWT 不泄漏。
- [x] USER/匿名/首次改密主体不能访问管理端；ADMIN 写缺 CSRF 拒绝，rowVersion 不得充当 request ID。
- [x] 上传/下载不整包缓冲；断开关闭上下游资源；binary 响应不套 ApiSuccess，仍有 X-Request-Id/no-store。
- [x] Gateway 模块和全后端测试通过，既有 status/auth/admin 行为无回归。

## 验证

使用假 problem 服务覆盖 public/admin JSON、multipart/binary、role/CSRF、资源重试、保存冲突、
413、下载中断、慢响应、5xx、畸形 JSON 和敏感 canary；WebTestClient 检查 status/content-type/cache/
request ID/body 与流关闭。执行 Gateway 模块和后端全量 verify。

## 风险

主要风险是把下游状态机械透传、缓冲大包、泄漏 ADMIN JWT/测试内容，或让匿名浏览依赖登录设施。
若需要 Gateway 查询数据库、缓存题目真源、改 user-service、放宽 CORS/CSRF 或改变 OpenAPI，停止并
升级设计/契约。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全 Gateway 映射、故障语义、匿名边界、测试与升级条件，等待人工批准。
- 2026-08-30：扩展为 public/admin、multipart/binary、幂等和题目发布状态 Gateway 边界。
- 2026-08-30：状态变更：todo → ready。原因：依赖任务和设计文档均已完成，用户已明确批准实施
- 2026-08-30：状态变更：ready → doing。原因：开始实现 Gateway 公开题库、管理端与测试数据流式代理
- 2026-08-30：新增公开题库列表/详情白名单 DTO 与零 Session controller，cursor 和筛选参数透明转发；
  关闭 Spring Security 隐式安全上下文/请求缓存，匿名读取不建立 Redis 会话。
- 2026-08-30：接通全部 ADMIN 题目、版本、测试数据、部署、校准与发布 API；统一 Session、首次改密、
  ADMIN role、CSRF 和短 JWT 委托边界，补全 PUT/DELETE CORS 方法与下载响应头暴露。
- 2026-08-30：multipart 上传使用 FilePart publisher 转发并以 1 MiB 内存阈值/100 MiB 磁盘上限封顶，ZIP
  下载保持 Flux、Content-Length、背压与取消；二进制不套 JSON，内部文件名/JWT/错误正文均不外泄。
- 2026-08-30：假上游测试覆盖 opaque cursor、request ID、敏感 canary、公开 404、畸形 JSON、504、
  ADMIN 首次改密/role 及 multipart/binary 流；Gateway 42 项和全后端 111 项 clean verify 通过（1 项条件跳过）。
- 2026-08-30：状态变更：doing → done。原因：Gateway 公开/管理/流式代理已实现，42 项 Gateway 与 111 项全后端 clean verify 通过
