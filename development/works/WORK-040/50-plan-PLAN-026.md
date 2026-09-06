---
id: "PLAN-026"
type: "plan"
title: "迁移判题环境与测试数据交付链路"
status: "checked"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["DECISION-022", "DESIGN-034"]
related: ["WORK-025", "WORK-038"]
implements: []
verifies: []
tags: []
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# PLAN-026：迁移判题环境与测试数据交付链路

## 目标

在不打断现有题目、测试数据和 calibration 事实的前提下，把判题可用性从静态 provision + 共享目录迁移
到节点自注册、租约和节点侧安装回执，并让本地 Compose 完成真实端到端验收。

## 改动区域

- 内部 contracts 与架构/数据模型文档。
- judging-service V2 migration、节点 registry、readiness、远程部署 client 与兼容开关。
- Go Judge 配置、注册/心跳客户端、测试数据安装 API 和私有存储。
- problem-service/Gateway 的受限错误传播及 Web readiness 呈现。
- Compose 的节点身份、控制面地址、持久卷和端到端脚本。

## 阶段与顺序

1. TASK-071：先固定跨语言协议、迁移和 Java 注册控制面；默认仍走 legacy-local。
2. TASK-072：Go Judge 按契约完成稳定 nodeId、注册/心跳与节点侧原子安装。
3. TASK-073：judging-service 使用 ONLINE 节点远程安装并记录逐节点回执，补齐安全、可操作错误；保留
   legacy-local 开关。
4. TASK-074：Compose 切换到 node-remote，移除本地 seed/共享目录前提，更新 Web/文档并做全链路回归。
5. VERIFY-041：在干净数据库/目录和既有兼容数据库两种环境验证，记录故障注入与回退结果。

## 并行与依赖

协议必须先于 Go/Java 两端实现。Go 节点实现完成后才能切 Java 远程部署；Compose 和 Web 最后接入，避免
用未稳定的中间接口反复改动。文档更新可随对应阶段同步，但全局事实只在最终协议稳定后定稿。

## 迁移与交付

V2 migration 只新增 node 与逐节点 deployment 表及索引，不删除旧表/列。旧 ACTIVE 环境可以保留，但在
没有真实 ONLINE 节点时 readiness 明确失败。首次真实节点注册若数据库无环境则自动创建 ACTIVE；已有
相同指纹环境则挂接；不同指纹只 REGISTERED。

实现期默认 `legacy-local`，node-remote 通过集成与 Compose 回归后改为默认。切换时不双写 READY：每次
请求只选择一条部署路径，避免两个存储结果互相覆盖。现有环境级 READY 不自动伪造成逐节点 READY，必须
由节点实际安装或对账后生成回执。

## 风险

- 节点在安装完成与回执持久化之间失联：幂等重试并核对 hash。
- nodeId 重用但环境改变：注册拒绝并告警。
- 租约边界时节点被选中后离线：远程调用失败，不写 READY，允许重试其他 ONLINE 节点。
- 错误正文穿透：只传播固定 allowlist code 和有界 detail。
- 切换影响现有本地数据：保留 legacy 目录与开关，验收前不删除。

## 验证

每阶段运行 Go/Java/TypeScript 定向测试。使用可控时钟验证租约 35 秒边界；真实 MySQL 验证唯一性、并发
注册和 V2 前进兼容；真实临时文件系统验证 ZIP 校验、原子安装、清理和幂等；mock/真实节点验证流中断、
超时和错误分类；最终从空数据库执行“节点启动→上传→部署→校准”Compose 端到端，并运行全仓回归。

## 回退

在 node-remote 尚未成为唯一可靠链路前，可把 judging-service 部署模式切回 `legacy-local` 并恢复原
Compose volume 配置。V2 migration 和新增行保留，不做向后 schema 回滚；旧代码忽略新表。回退不修改
已发布题目、calibration 或历史 JudgeInput。若新契约已由节点使用，节点继续重试注册但不会影响 `/judge`
旧接口。

## 变更记录

- 2026-09-06：按控制面、节点面、远程部署和本地切换四阶段拆分。
- 2026-09-06：状态变更：draft → review。原因：已拆分协议控制面、Go 节点、Java 远程部署和本地切换四阶段，依赖、验证与回退明确
- 2026-09-06：结构与内容校验通过，由工具置为 checked。

- 2026-09-06：协议阶段同时生成 Go contract 类型与 fixture 对齐测试；TASK-071 增加 internal/contract 写边界，避免在 TASK-072 消费前无法验证双端协议。节点运行逻辑仍归 TASK-072。

- 2026-09-06：TASK-074 增加 Judge Dockerfile 写边界，用于让新命名私有卷在非 root 用户下可写；新增 Compose 回退 override 与独立端到端脚本，验证使用新命名 schema/volume，不触碰用户原数据。

- 2026-09-06：实际 publish-check 只转发六类固定检查，节点在线状态会被丢弃。TASK-074 增加 problem-service 的发布检查映射与 DTO、OpenAPI 写边界，追加 ONLINE_JUDGE_NODE 检查条目；响应外形不变，Web 才能在部署前区分缺数据与缺节点。

- 2026-09-06：独立复核修正纳入 TASK-074：Go 控制循环和 Java 注册会话 fencing、数据损坏重试后的条件回执失效，以及发布检查从固定六项改为六个基础项加节点项。

- 2026-09-06：独立复核要求实际环境探测：TASK-074 增加 cmd/judge 与 internal/config 写边界，在注册前经既有 sandbox /run、/version 获取 CPU、内核、系统和 C++ 工具链元数据，节点指纹由实际元数据与策略摘要计算；探测失败拒绝启动，不用占位值注册。既有异指纹环境保留 legacy 路径，不自动迁移标定。

- 2026-09-06：全仓回归需同步既有配置白名单与契约文件清单，TASK-074 增加 scripts/contracts_test.py、user-service 的 JavaServiceConfigurationDefaultsTests（仅测试）写边界；元数据由部署声明改为运行端探测后同步 docs/engineering/java.md，新增表会话历史/回执修订同步数据模型文档。

- 2026-09-06：最终复核补齐 Judge 可执行文件摘要及 JUDGE_TESTDATA_VOLUME 显式新卷选项；sandbox 升级须重启 Judge。TASK-074 同步 config.example.yaml，删除不再由操作者声明的占位元数据示例。

- 2026-09-06：提交前将 TASK-074 边界扩展至 .github/workflows/ci.yml：既有纯 Go 容器 smoke 没有 Java 控制面，需显式使用 legacy override 和仓库 A+B fixture；节点默认链路仍由 node-e2e.py 的真实五服务回归覆盖。
