---
id: "TASK-074"
type: "task"
title: "迁移本地编排并完成跨模块回归"
status: "done"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["TASK-073"]
related: []
implements: ["ISSUE-012#AC-005", "ISSUE-012#AC-006", "ISSUE-012#AC-007", "ISSUE-012#AC-008"]
verifies: []
tags: []
read_paths: [".github/workflows/ci.yml", "CLAUDE.md", "docs/design-system/PROMPT.md", "docs/engineering/README.md", "docs/engineering/java.md", "docs/engineering/go.md", "docs/engineering/typescript.md", "contracts", "compose.yaml", "apps/server", "apps/judge-engine", "apps/web", "docs", "development/works/WORK-040"]
write_paths: [".github/workflows/ci.yml", "apps/judge-engine/config.example.yaml", "scripts/contracts_test.py", "apps/server/user-service/src/test/java/com/cherryoj/userservice/config/JavaServiceConfigurationDefaultsTests.java", "docs/engineering/java.md", "docs/data-model.md", "docs/database-design.md", "apps/judge-engine/cmd/judge", "apps/judge-engine/internal/config", "apps/judge-engine/internal/judge/node", "contracts/web-api.openapi.json", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/application/ProblemPublicationService.java", "apps/server/problem-service/src/main/java/com/cherryoj/problemservice/api/AdminProblemDtos.java", "apps/server/problem-service/src/test", "apps/judge-engine/Dockerfile", "compose.legacy.yaml", "apps/server/judging-service/scripts", "compose.yaml", "apps/server/judging-service/src/main", "apps/server/judging-service/src/test", "apps/server/README.md", "apps/server/TOOLCHAIN.md", "apps/web", "docs/architecture.md", "docs/backend.md", "docs/engine.md", "development/works/WORK-040"]
forbidden_paths: ["apps/server/user-service/src/main", "apps/server/submission-service/src/main", "apps/server/problem-service/src/main/resources/db", "contracts/run.schema.json", "apps/judge-engine/internal/sandbox", "apps/server/judging-service/src/main/resources/db/migration/V1__create_judging_readiness_tables.sql"]
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# TASK-074：迁移本地编排并完成跨模块回归

## 任务目标

让干净本地环境无需 seed 或共享目录即可完成节点注册、上传、部署、校准，并把不可用状态提前显示在
工作台；随后关闭旧默认链路并完成系统回归。

## 依据

实现 ISSUE-012#AC-005～AC-008，完成 PLAN-026 的本地切换、文档对齐和验收阶段。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- Compose 为 Judge 配置稳定 nodeId、控制面/advertise 地址、共享 token 和节点私有持久卷。
- judging-service 默认切为 node-remote，停止 dev provision 与 Java 本地目录承担正确性职责。
- Web 根据 readiness 禁用不可执行的部署动作并显示固定、可恢复原因。
- 更新本地启动、架构、后端和引擎文档；保留显式 legacy 回退说明。
- 从空数据库和空数据卷执行端到端脚本与全仓回归。

## 完成标准

- [x] 普通本地启动自动出现真实 ONLINE 节点，不创建静态 dev fixture。
- [x] 上传 Finder ZIP、绑定、部署、重复部署和参考程序校准在真实 Compose Judge 上成功。
- [x] 停止 Judge 后工作台在调用部署前展示无在线节点；恢复后自动可用。
- [x] Judge 容器只读取自身数据卷，Java 与 Go 不共享 testdata 路径。
- [x] legacy 回退演练、全仓格式/测试/契约检查通过，文档无旧启动指引。

## 验证

按设计系统自检 Web 改动。执行 Go tests/race、Java clean verify、Web lint/typecheck/test/build、contracts
校验和 scripts/work check；重建空 MySQL schema/节点 volume，跑真实页面/API 端到端与节点停止/恢复故障
注入，记录 requestId、数据库回执和目录证据。

## 风险

不得删除用户本地旧目录或数据库数据；只停止新请求依赖。Compose 清理必须使用新命名 volume，回退演练
不得覆盖已有测试资产。若真实端到端未完成，node-remote 不能成为默认。

## 执行记录

- 2026-09-06：创建任务。
- 2026-09-06：状态变更：todo → ready。原因：远程部署链路已通过定向验证，进入本地编排和跨模块回归
- 2026-09-06：状态变更：ready → doing。原因：迁移 Compose 私有卷和控制面配置，接入工作台在线状态，执行真实端到端与回退验证

- 2026-09-06：最终默认 node-remote 的真实五服务/Compose、显式 legacy 回滚、新旧环境切换、已发布快照不变及浏览器验证全部通过；Java 150、Web 136+31、Go race/vet 和契约/文档回归证据见 VERIFY-041。
- 2026-09-06：状态变更：doing → done。原因：默认远程节点、真实 Compose 端到端、legacy 回滚、环境切换与已发布快照不变、150 项 Java 与 Web/Go 回归全部通过

- 2026-09-06：推送前检查发现 CI 容器 smoke 仍依赖旧共享目录与 local-compose 指纹；增加 .github/workflows/ci.yml 读写边界，明确该 smoke 使用 legacy override 和仓库测试数据，保留真实回退覆盖，避免默认节点模式切换导致 CI 失配。
