---
id: "TASK-041"
type: "task"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "done"
work: "WORK-026"
owners: ["codex/root"]
depends_on: ["CAPABILITY-007", "DESIGN-020", "DECISION-015", "PLAN-016"]
related: []
implements: ["CAPABILITY-007#REQ-001", "CAPABILITY-007#REQ-002", "CAPABILITY-007#REQ-003", "CAPABILITY-007#REQ-004", "CAPABILITY-007#REQ-005", "CAPABILITY-007#REQ-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/backend.md", "apps/server/pom.xml", "apps/server/README.md", "apps/server/TOOLCHAIN.md", "apps/server/gateway-service", "apps/server/user-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "development/works/WORK-013", "development/works/WORK-025", "development/works/WORK-026"]
write_paths: ["apps/server/README.md", "apps/server/TOOLCHAIN.md", "apps/server/gateway-service/src/main/resources/application.yaml", "apps/server/gateway-service/src/test", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config", "apps/server/user-service/src/main/resources/application.yaml", "apps/server/user-service/src/test", "apps/server/problem-service/src/main/resources/application.yaml", "apps/server/problem-service/src/test", "apps/server/submission-service/src/main/resources/application.yaml", "apps/server/submission-service/src/test", "apps/server/judging-service/src/main/resources/application.yaml", "apps/server/judging-service/src/main/resources/application-dev.yaml", "apps/server/judging-service/src/test", "development/works/WORK-026"]
forbidden_paths: ["contracts", "apps/web", "apps/judge-engine", "compose.yaml", "docs", "apps/server/*/src/main/resources/db", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "development/works/WORK-015", "development/works/WORK-016", "development/works/WORK-017", "development/works/WORK-018", "development/works/WORK-019", "development/works/WORK-020", "development/works/WORK-021", "development/works/WORK-022", "development/works/WORK-023", "development/works/WORK-024", "development/works/WORK-025"]
created_at: "2026-08-31"
updated_at: "2026-08-31"
---






# TASK-041：为 Java 服务提供可直接启动的本地默认配置

## 任务目标

实现五服务本地启动默认配置、user-service 内存临时 RSA 与 production 拒绝降级，补齐跨服务配置测试
和启动说明，且不覆盖 WORK-025 现有未提交改动。

## 依据

CAPABILITY-007 REQ-001～REQ-006、DESIGN-020、待确认的 DECISION-015 与 PLAN-016。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

必要的 application 默认、随机密钥配置分支、production fail-closed 校验、模块/配置扫描测试、Server
启动文档和 VERIFY-026 证据；无 migration、固定私钥或公开契约变更。

## 完成标准

- [x] 无 `CHERRY_*` 环境变量时，五服务配置绑定/上下文不因缺省值失败；外部基础设施错误可明确区分。
- [x] 原环境变量覆盖全部通过，显式 RSA PEM 路径行为和 JWKS/JWT 契约不变。
- [x] 本地 RSA 每次进程启动随机生成且不落盘，production 缺失稳定密钥明确启动失败。
- [x] judging 数据库用户名/密码默认与当前本地最小权限账号约定一致；Redis、provision 和 Spring 内部
  占位符未被错误填值。
- [x] 五模块与聚合测试、配置扫描、文档检查、diff 与敏感信息检查通过，改动不越界。

## 验证

从 `apps/server` 运行各受影响模块 `./mvnw -pl <module> -am test` 与最终 `./mvnw clean verify`；测试
本地随机、显式 PEM、production 缺失、数据库默认/覆盖、有效空值白名单。再运行 `scripts/work check`、
`git diff --check`，并扫描私钥头、硬编码生产凭据和无默认 `CHERRY_*` 占位符。

## 风险

不得提交 `BEGIN PRIVATE KEY` 或把当前个人环境中的真实密码复制到新位置。若 production profile/模式
边界无法可靠强制、临时换钥破坏既有本地 Session 恢复、或需要修改公开接口/数据库/Compose，暂停并
更新 DESIGN/DECISION 重新审核。

## 执行记录

- 2026-08-31：创建任务。
- 2026-08-31：补全 REQ 映射、读写边界、完成标准和安全升级条件，等待上游文档批准与执行授权。
- 2026-08-31：无变量启动 smoke 发现 judging 默认 `root` 与服务账号口令约定不一致；按已批准的最小
  权限数据库边界，把默认用户名纳入同一配置修正，不扩大数据库权限或数据范围。
- 2026-08-31：user-service 无相关 `CHERRY_*` 环境变量实际启动成功；production 无 Secret 按预期明确
  失败。judging 无变量启动已使用默认服务账号/密码，到达 MySQL 后因本机账号未创建/授权被拒绝。
- 2026-08-31：新增五服务 application 配置扫描和临时密钥单元测试；Java 七模块 `clean verify` 共执行
  116 项测试，0 失败、1 项真实外部 Judge 联调按环境条件跳过。
- 2026-08-31：状态变更：todo → ready。原因：上游文档已获人工批准，任务范围与读写边界完整
- 2026-08-31：状态变更：ready → doing。原因：开始实现五服务本地默认配置与生产安全边界
- 2026-08-31：状态变更：doing → done。原因：本地默认、临时 RSA、production 安全守卫、跨服务配置扫描、文档和七模块聚合验证均完成
- 2026-08-31：状态变更：done → doing。原因：运行时联调发现三个资源服务的本地 JWKS 默认 URL 多出 /internal，重新打开已批准任务以修正默认配置并补回归
- 2026-08-31：根据请求 `req_08453d3dffd84e219fcd682246943697` 定位到 Gateway 调用
  problem-service 时返回 503；五服务健康检查均为 UP，但资源服务默认访问的
  `/internal/.well-known/jwks.json` 实际返回 401，而 user-service 发布的
  `/.well-known/jwks.json` 返回 200。
- 2026-08-31：修正 problem、submission、judging 三个资源服务的本地 JWKS 默认地址，并增加跨服务
  回归测试，禁止重新引入错误的 `/internal` 前缀；定向测试 2 项通过，七模块 `clean verify` 共执行
  121 项测试，0 失败、1 项真实外部 Judge 联调按环境条件跳过。
- 2026-08-31：状态变更：doing → done。原因：资源服务 JWKS 默认地址已修正，跨服务回归与七模块 121 项聚合验证通过
