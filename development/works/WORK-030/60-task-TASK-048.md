---
id: "TASK-048"
type: "task"
title: "修复后台题目列表间歇性 502"
status: "done"
work: "WORK-030"
owners: ["codex/root"]
depends_on: ["ISSUE-007", "DESIGN-023", "DECISION-017", "PLAN-019"]
related: []
implements: ["ISSUE-007#AC-001", "ISSUE-007#AC-002", "ISSUE-007#AC-003", "ISSUE-007#AC-004", "ISSUE-007#AC-005", "ISSUE-007#AC-006", "ISSUE-007#AC-007", "ISSUE-007#AC-008"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/java.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/gateway-service", "apps/server/problem-service", "apps/server/user-service/src/main/java/com/cherryoj/userservice/api/JwksController.java", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config", "apps/server/user-service/src/main/java/com/cherryoj/userservice/security", "apps/server/user-service/src/test", "development/works/WORK-013", "development/works/WORK-028", "development/works/WORK-030"]
write_paths: ["apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/auth", "apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/problem", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/auth", "apps/server/gateway-service/src/test/java/com/cherryoj/gatewayservice/problem", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/security", "apps/server/problem-service/src/test/java/com/cherryoj/problemservice/security", "apps/server/problem-service/src/test/java/com/cherryoj/problemservice/ProblemServiceApplicationTests.java", "development/works/WORK-030"]
forbidden_paths: ["contracts", "apps/server/user-service/src/main", "apps/server/submission-service", "apps/server/judging-service", "apps/judge-engine", "apps/web", "database migrations", "docs/design-system.md", "docs/design-system", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016", "development/works/WORK-017", "development/works/WORK-018", "development/works/WORK-019", "development/works/WORK-020", "development/works/WORK-021", "development/works/WORK-022", "development/works/WORK-023", "development/works/WORK-024", "development/works/WORK-025", "development/works/WORK-026", "development/works/WORK-027", "development/works/WORK-028", "development/works/WORK-029"]
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# TASK-048：修复后台题目列表间歇性 502

## 任务目标

先用自动化测试确认 problem-service 在 K1→K2/JWKS 并发刷新中的真实行为，再让 Admin 题目普通 JSON
GET 复用 Gateway 的一次性资源 401 恢复；只在失败测试证明时修改 JWKS 实现，并完成安全回归。

## 依据

实现 ISSUE-007#AC-001～AC-008，遵循 DESIGN-023 的双层、测试先行方案和 DECISION-017 的证据门槛；
PLAN-019 规定先分类资源端，再共享 Gateway 状态机，不允许先改安全配置后补解释。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- problem-service 可控 K1→K2、并发刷新和 fail-closed 安全测试，及不含敏感值的失败分类观测。
- Gateway 共享 Admin 普通 GET 恢复能力，Admin users 兼容适配和 Admin problems 列表接入。
- 经逐路由审计后可纳入的其它 Admin problem 普通 JSON GET；下载和写操作明确保持不重放。
- 若失败测试证明需要，提供有界并发安全的 JWKS cache/refresh 修复。
- VERIFY-031 的命令、结果、范围和独立安全复核；MEMORY-024 的长期结论。

## 完成标准

- [x] ISSUE-007#AC-001～AC-008 均有精确自动化证据并锚定 VERIFY-031。
- [x] 先记录当前 decoder 在 K1→K2 并发测试中的结果；没有失败证据则不修改生产 JWKS 实现。
- [x] 正常题目列表不 exchange；首次资源 401 只 exchange/重试一次，授权失效与临时故障语义正确。
- [x] 403、解码错误、非认证 4xx/5xx、下载及所有写操作不触发自动重放。
- [x] Admin users 原有恢复、并发 WebSession 回写、公开问题接口和资源服务 fail-closed 全部回归。
- [x] 日志可按 requestId 分类且不包含 Cookie、grant、JWT、用户标识或密钥材料。
- [x] 未越过 front matter 边界；未修改 user-service、其它资源服务、contracts/Web 或数据库。

## 验证

按 PLAN-019 运行 problem-service/Gateway 定向测试、服务端 `clean verify`、受控真实服务轮换（环境允许时）、
`scripts/work check` 和 `git diff --check`，并完成独立 Security Review。VERIFY-031 记录测试数、当前/修复后
轮换结果、未通过项和剩余风险。

## 风险

资源 JWT 验证是安全关键路径。不得为可用性接受未知 key、错误签名或过期 token；不得把通用 502 当资源
401；不得自动重放写/下载。若无法在测试中区分 JWT 拒绝与响应解码错误，先补可观测性，不继续猜测实现。

## 执行记录

- 2026-09-02：创建任务。
- 2026-09-02：状态变更：todo → ready。原因：人工意图闸已通过，设计、决策、计划、验收标准和代码边界完整
- 2026-09-02：状态变更：ready → doing。原因：开始先建立 JWKS 轮换基线测试，再实现 Gateway Admin GET 单次恢复
- 2026-09-02：受控 K1→K2 基线在 16 路并发下全部通过且仅刷新 JWKS 一次；保留现有生产 decoder，
  未作无失败证据的缓存改动。
- 2026-09-02：抽取 `AdminGatewayAccess.readWithRecovery`，让 Admin users 与逐路由审计后的题目普通
  JSON GET 共用一次恢复；所有写请求和流式下载仍不重放。
- 2026-09-02：Gateway 全量 57 项、服务端聚合 135 项通过（1 项既有 Linux 集成测试跳过），并完成
  fail-closed、日志脱敏和任务边界检查。
- 2026-09-02：状态变更：doing → done。原因：实现共享 Admin 只读 401 单次恢复、题目 GET 接入、JWKS 基线与安全观测，定向及服务端全量测试通过
