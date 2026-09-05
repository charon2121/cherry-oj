---
id: "TASK-066"
type: "task"
title: "统一资源服务内部身份验证基座"
status: "done"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["ISSUE-009", "DESIGN-031", "DECISION-021", "PLAN-025"]
related: []
implements: ["ISSUE-009#AC-002", "ISSUE-009#AC-004", "ISSUE-009#AC-007", "ISSUE-009#AC-008", "ISSUE-009#AC-010"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config", "apps/server/user-service/src/main/java/com/cherryoj/userservice/security", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "development/works/WORK-013", "development/works/WORK-025", "development/works/WORK-037"]
write_paths: ["apps/server/pom.xml", "apps/server/identity-security-support", "apps/server/problem-service/pom.xml", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/security", "apps/server/problem-service/src/test/java/com/cherryoj/problemservice/security", "apps/server/problem-service/src/main/resources", "apps/server/submission-service/pom.xml", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/security", "apps/server/submission-service/src/test/java/com/cherryoj/submissionservice/security", "apps/server/submission-service/src/main/resources", "apps/server/judging-service/pom.xml", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/security", "apps/server/judging-service/src/test/java/com/cherryoj/judgingservice/security", "apps/server/judging-service/src/main/resources", "development/works/WORK-037"]
forbidden_paths: ["contracts", "apps/web", "apps/judge-engine", "apps/server/gateway-service", "apps/server/user-service/src/main", "apps/server/user-service/src/test", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/application", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/persistence", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/application", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/persistence", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/application", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/persistence", "apps/server/problem-service/src/main/resources/db", "apps/server/submission-service/src/main/resources/db", "apps/server/judging-service/src/main/resources/db", "database migrations"]
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# TASK-066：统一资源服务内部身份验证基座

## 任务目标

建立不含业务逻辑的 `identity-security-support` 模块，并把 problem、submission、judging 的重复 JWT/JWKS
验证实现迁移到同一基座，使算法、claims、kid、缓存刷新、错误分类和 readiness 不变量只实现一次。

## 依据

实现 `ISSUE-009#AC-002`、`#AC-004`、`#AC-007`、`#AC-008`、`#AC-010`。遵循 DESIGN-031：共享层只
输出认证事实，不接管各服务的路由 matcher、角色授权、AccessDenied 或业务代码。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。此任务完成后，TASK-065 才能在相同 primitives 上实现签名 key ring。

## 产出

- 共享的 kid 指纹、claims validator、authority converter、remote JWKS decoder factory 和失败枚举。
- 明确的已缓存 key/未知 kid/JWKS 不可用语义，以及不泄漏敏感值的 security problem 映射。
- problem、submission、judging 的迁移与每服务独立 route/role 回归测试。
- 共享 fixture，证明三个资源服务对同一 token corpus 给出一致认证结论。

## 完成标准

- [x] 三个资源服务不再各自实现算法、claims、JWKS refresh 或错误原因推断。
- [x] RS256、issuer、audience、时间、必需 claims、role/pwd 和 kid 的正反用例在同一 corpus 下结果一致。
- [x] missing、malformed、expired、unknown kid、bad signature、invalid claims、key unavailable 可观测地区分。
- [x] 未知 kid 刷新有界；缓存合法 key 的短时故障窗口和 readiness 行为有确定测试。
- [x] 各服务原有 route/role/403 规则逐项回归，共享模块没有业务 package 依赖。
- [x] 私钥、完整 token、用户信息不进入错误、日志、metrics 或 fixture snapshot。

## 验证

实施前运行 `scripts/work context TASK-066`。运行共享模块与三个资源服务的定向测试、依赖边界测试、配置
启动测试和聚合 `clean verify`；把精确命令、用例数量和结果写入 VERIFY-038。

## 风险

共享 verifier 是全系统 blast radius。任何“方便”的默认值都可能同时放宽三个服务；配置缺失必须 fail
fast。若需要修改业务 Controller、数据库、Gateway 或公开契约，先更新设计与任务边界并重新人工审核。

## 执行记录

- 2026-09-04：创建任务。
- 2026-09-04：补齐共享验证基座、三服务迁移和安全失败分类边界。
- 2026-09-05：状态变更：todo → ready。原因：意图闸已由用户签署，依赖已满足，准备实施共享 JWT/JWKS 验证模块
- 2026-09-05：状态变更：ready → doing。原因：开始实施共享 JWT/JWKS 验证模块
- 2026-09-05：状态变更：doing → done。原因：共享 identity-security-support 已接管三服务的 RS256/JWKS/claims/authority/失败分类/readiness，定向安全测试通过
