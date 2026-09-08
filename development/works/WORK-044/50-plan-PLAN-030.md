---
id: "PLAN-030"
type: "plan"
title: "题目内自定义输入运行"
status: "checked"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["FEATURE-012", "EXPERIENCE-020", "DESIGN-038", "DECISION-025"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-08"
---

# PLAN-030：题目内自定义输入运行

## 目标

按 FEATURE-012 完成 C++ ACM 单输入自测、受控结果展示与正式提交隔离。

## 阶段与顺序

1. 本回合文档审核；用户签署意图闸并在后续明确允许执行后才开始代码。
2. TASK-085：契约与 Go trial 标准错误补齐；兼容 submit 和 sandbox。
3. TASK-086：Java 私有 trial 编排、公开 Gateway/submission 接口、准入/超时/节点校验。
4. TASK-087：生成 API 类型，右侧底部 Tabs、输入/结果和请求快照，保留提交恢复。
5. TASK-088：跨模块/资源/安全回归，真实 Linux sandbox、真实账号端到端及截图八问，独立复核，VERIFY/MEMORY。
6. 用户验收闸；仅后续明确要求时 commit/push。

## 并行与依赖

顺序执行，不主动派生子智能体。独立复核由人执行；用户后续明确授权独立智能体时再委派。定义/体验/决策不能由实现者代签。

## 迁移与交付

无新增数据库表、无 Kafka 事件、无正式提交历史回填。contracts 是唯一真源；Go additive stderr、Java trial及 profile purpose 兼容改动、Web 新 panel 分阶段部署。custom-run 默认关闭，配套版本和资源实测后才启用；回退关闭开关/撤回页面入口，保留兼容可选字段，不回滚正式提交或源码草稿。实际服务重启/启用只在获得明确联调授权后执行。

## 验证

Go：在 apps/judge-engine 执行相关 contract/flow/api 测试、go test -race ./...；隔离安全实测使用仓库现有 Linux 容器流程，不在 macOS 结果上冒充隔离验证。
Java：apps/server 下 ./mvnw -pl submission-service,judging-service,gateway-service -am test；测试覆盖真实路由及 Redis原子准入、版本/profile、服务凭据、bounded response、失败释放与禁止自动重试。
Web：Node24，npm run generate:api、npm run check、npm run build；原 workspace/history E2E加 custom-run E2E、真实后端路由检查。具体命令、版本、实际结果写 VERIFY-045；无真实环境授权时明确 pending，不能把 mock 标成全栈通过。

## 风险

发现需要修改 sandbox 隔离、数据库、身份信任链、全局设计系统值或其他语言时，先更新 DESIGN/TASK 边界并提交范围确认。本轮推荐的限额/期限/页面结构统一交由意图闸审核。

## 改动区域

契约、Go trial 结果、Gateway/submission/judging 自测边界、Web 题目工作台及对应测试；具体路径见四个 TASK。

## 回退

关闭 custom-run 开关并撤回运行入口，兼容可选字段可保留；没有需要回滚的自测数据库记录，不影响正式提交数据。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：四个任务边界、依赖、验证与回退方案完成
- 2026-09-08：结构与内容校验通过，由工具置为 checked。
