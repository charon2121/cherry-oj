---
id: "TASK-091"
type: "task"
title: "统一服务配置与环境启动管理"
status: "done"
work: "WORK-046"
owners: ["codex/root"]
depends_on: ["CHANGE-012", "DESIGN-040", "PLAN-031"]
related: []
implements: ["CHANGE-012#REQ-001", "CHANGE-012#REQ-002", "CHANGE-012#REQ-003", "CHANGE-012#REQ-004", "CHANGE-012#REQ-005", "CHANGE-012#REQ-006", "CHANGE-012#REQ-007", "CHANGE-012#REQ-008"]
verifies: []
tags: []
read_paths: ["apps", "scripts", "compose.yaml", "compose.legacy.yaml", ".github", "docs", "development", "README.md", ".gitignore"]
write_paths: ["apps/server", "deploy", ".gitignore", ".dockerignore", "README.md", "docs/engineering", "development/works/WORK-046"]
forbidden_paths: ["apps/judge-engine", "apps/web", "contracts"]
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# TASK-091：重构五个后端服务配置

## 任务目标

按 DESIGN-040 完成五服务标准 local 文件、基础配置、Compose 环境覆盖及迁移验证。

## 依据

CHANGE-012、DESIGN-040、PLAN-031，按当前文件内容执行，旧 local.yaml 和用户目录方案已撤销。

## 可查看范围

以 front matter 为准，秘密只在内存中比较，不打印。

## 可修改范围

以 front matter 为准；apps/server 仅资源配置、POM、绑定校验、相关测试、IDE 与文档。deploy 仅后端 Compose 配置样例和必要适配，不扩建 Web/引擎。

## 禁止修改

业务实现、公开契约、数据库数据/密码、Web、Go；不提交推送或实际上线。

## 依赖

CHANGE-012、DESIGN-040、PLAN-031；WORK-046 意图闸由用户签署后执行。

## 产出

五服务 application.yaml、application-local.example.yaml、本地 application-local.yaml、打包/测试隔离、Compose 环境样例及验证证据。

## 完成标准

- [x] 五服务字段盘点完整，真实私有值仅存本地，示例可用。
- [x] 默认 local，无配置参数/环境变量直接运行；旧配置安全迁移并核对配套值。
- [x] 测试不加载 local，Compose 选择非 local 且环境覆盖正确。
- [x] Git、JAR 和镜像排除私有文件，检查残留资源与重打包。
- [x] 旧导入/参数清理、回退备份、适当后端回归和集成证据完成。

## 验证

对应 CHANGE-012#AC-001 至 AC-006；记录真实命令、结果、环境和缺口，不以静态配置检查替代运行验收。

## 风险

字段或秘密来源不明时保留原值并定位，不自动覆盖、降级或修改凭据。

## 执行记录

- 2026-09-08：用户确认 application-local.yaml 命名并要求重构后端配置；文档已收敛到五服务，尚待意图闸签署。
- 2026-09-08：状态变更：todo → ready。原因：意图闸已签署
- 2026-09-08：状态变更：ready → doing。原因：开始五服务配置重构

- 2026-09-08：实施完成。五服务标准 local 配置、私有备份迁移、IDE 清理、测试隔离、JAR 排除及 Compose 样例已完成；证据见 VERIFY-047，真实用户实例重启与完整容器 smoke 尚未执行并明确保留。
- 2026-09-08：状态变更：doing → done。原因：配置重构与可执行检查完成，真实重启和容器闭环验证缺口已记录
- 2026-09-08：状态变更：done → doing。原因：修复 IDE 实际运行配置并验证两个失败服务启动
- 2026-09-08：状态变更：doing → done。原因：已清理 IDEA 内存旧参数，两个服务实际启动且 readiness HTTP200 UP
