---
id: "TASK-067"
type: "task"
title: "统一 Gateway 内部身份转发与会话令牌生命周期"
status: "done"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["ISSUE-009", "DESIGN-031", "DECISION-021", "PLAN-025", "TASK-065", "TASK-066", "TASK-068"]
related: []
implements: ["ISSUE-009#AC-005", "ISSUE-009#AC-006", "ISSUE-009#AC-007", "ISSUE-009#AC-008", "ISSUE-009#AC-009", "ISSUE-009#AC-010", "ISSUE-009#AC-011", "ISSUE-009#AC-012"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "apps/server/TOOLCHAIN.md", "apps/server/gateway-service", "apps/server/user-service/src/main/java/com/cherryoj/userservice/api", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/security", "development/works/WORK-028", "development/works/WORK-030", "development/works/WORK-037"]
write_paths: ["apps/server/gateway-service/src/main", "apps/server/gateway-service/src/test", "apps/server/gateway-service/pom.xml", "development/works/WORK-037"]
forbidden_paths: ["contracts", "apps/web", "apps/judge-engine", "apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "database migrations"]
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# TASK-067：统一 Gateway 内部身份转发与会话令牌生命周期

## 任务目标

让 Gateway 从一个认证边界向所有内部 client 提供不可变 DelegatedIdentity，并由统一 request factory/filter
为 JSON、multipart 和 streaming 请求注入 token、requestId 与 trace；删除按业务路由的 401 恢复和重放。

## 依据

实现 `ISSUE-009#AC-005`～`#AC-010` 中 Gateway 与端到端部分。TASK-065/066 必须先证明签发与验证事实
稳定；本任务不能用新的上传特例补偿信任链问题。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- 单一 DelegatedIdentity/credential provider 与统一内部 HTTP 请求构造边界。
- 所有 user/problem/submission/judging client 的迁移清单和结构测试，禁止手工 Authorization 注入。
- 删除或旁路 WORK-028/030 的 `readWithRecovery` 类业务重试状态机。
- login grant 失效、资源身份 401、身份基础设施 503 和业务错误的统一映射。
- Gateway readiness、真实 JSON/multipart/streaming HTTP 与并发 Session 测试。

## 完成标准

- [x] 每个内部 client 都由同一边界注入 Authorization/requestId，静态或架构测试阻止再次绕过。
- [x] token 只按 `expiresAt - refreshAhead` 在业务请求发送前刷新；Controller 不解释资源 401 或重放 action。
- [x] token 寿命为 2 小时并在剩余 5 分钟时续签；TTL/refresh-ahead 的非法组合阻止启动。
- [x] 只有 user-service 明确判定 grant 撤销/过期才清 Session 并返回浏览器 401。
- [x] 资源身份不变量破坏统一返回可观测 503，资源 403 和业务 4xx/5xx 保持既有语义。
- [x] 真实 ZIP 通过 Gateway 单次流向 problem-service，Gateway 不完整缓存/复制/落盘，业务只执行一次。
- [x] 并发 Session 继续单飞 exchange 且互相隔离；日志和指标不包含 token、Cookie、grant 或文件内容。

## 验证

实施前运行 `scripts/work context TASK-067`。覆盖各 client 架构约束、token 生命周期、错误矩阵、并发单飞、
真实 HTTP JSON/multipart/streaming；与 TASK-065/066 联合执行重启和 K1→K2 轮换后再跑聚合验证。

## 风险

统一 request factory 影响所有内部业务调用。必须先建立 client inventory 和 characterization tests，逐类迁移，
不能用“全部 401 自动 exchange”替代。若需要修改公开 API、资源业务代码或上传契约，先升级设计和边界。

## 执行记录

- 2026-09-04：创建任务。
- 2026-09-04：补齐全 client 迁移、错误语义和移除局部恢复的边界。
- 2026-09-04：纳入 5 分钟提前续签和无 idle/30 天 absolute Session 兼容。
- 2026-09-05：状态变更：todo → ready。原因：TASK-065、TASK-066、TASK-068 均已完成，开始统一 Gateway 内部身份转发
- 2026-09-05：状态变更：ready → doing。原因：开始迁移全部内部 client 到 DelegatedIdentity/request factory 并删除业务 401 重放
- 2026-09-05：全部内部 client 已迁移到单一请求构造边界；资源 401 不重放、ZIP 流式单次发送、并发续签和
  fixed-absolute Session 的真实 Redis/HTTP 回归通过；一度追加的强传输门禁随后按负责人威胁模型简化。
- 2026-09-05：状态变更：doing → done。原因：统一 DelegatedIdentity/request factory、资源 401 安全分类、ZIP 单次流式上传、2 小时 JWT/5 分钟续签与生产传输约束已实现；定向测试和全仓 clean verify 通过
- 2026-09-05：按负责人确认的可信内网威胁模型，内部 user/problem/judging 调用恢复支持 HTTP；保留 URI
  合法性、统一凭证边界与生产 Cookie Secure 默认值，不增加不可覆盖式安全门禁。
