---
id: "PLAN-015"
type: "plan"
title: "交付题库、题目与测试数据管理"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "EXPERIENCE-013", "DESIGN-019", "DECISION-014"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-30"
updated_at: "2026-08-30"
---


# PLAN-015：交付题库、题目与测试数据管理

## 目标

按契约、数据基础、题目领域、测试资产、判题部署、发布编排、Gateway、Web 的顺序，交付 ADMIN 能真实
发布 C++ ACM 题目且用户能匿名浏览的完整切片。每个 TASK 只跨一个主要职责边界。

## 改动区域

- 契约/生成类型：`contracts/web-api.openapi.json`、`apps/web/src/generated/api/`。
- 题目与源资产：`apps/server/problem-service/`、私有测试数据根。
- 部署/标定/readiness：`apps/server/judging-service/`、judge testdataRoot；只调用现有 Go judge 契约。
- BFF：`apps/server/gateway-service/`。
- Web：`apps/web/package*.json`、`TOOLCHAIN.md`、`src/`、`e2e/`。
- 证据：`development/works/WORK-025/`。

禁止修改 user/submission 服务、Kafka/提交链路、Go judge/sandbox 实现、跨语言 judge 契约和设计系统资产。

## 阶段与顺序

1. `TASK-033`：冻结 public/admin OpenAPI、multipart/binary、rowVersion/资源状态、错误和生成类型。
2. `TASK-034`：problem-service V1、公开读模型、UUID/MyBatis/Flyway 和 MySQL 基础测试。
3. `TASK-037`：Problem/Version/Sample/Language/Audit 草稿、修订、预览、删除与归档领域。
4. `TASK-038`：TestDataAssetStore、ZIP 安全校验、UPLOADING/READY/FAILED、manifest、下载和绑定。
5. `TASK-039`：judging-service 环境 V1、流式部署、手工绝对限制、参考程序验证、VALID calibration/readiness。
6. `TASK-040`：problem-service 部署/验证 client、VALIDATING 恢复、publish-check 和本地原子发布。
7. `TASK-035`：Gateway 同时接通 public 与 ADMIN JSON/multipart/binary API，完成鉴权、CSRF、幂等与故障映射。
8. `TASK-036`：Web 公开题库和 ADMIN 题目工作台，覆盖保存/冲突/上传/部署/验证/发布全部状态。
9. 独立复核与 `VERIFY-025`：数据泄漏、ZIP/路径、JWT 委托、事务/幂等、迁移、文件一致性、可访问性、
   跨模块回归和真实 A+B 端到端。

## 并行与依赖

契约是共同前置。TASK-034 后题目领域和静态 Web 结构可预研，但同一服务的 TASK-037/038/040 顺序执行，
避免并发编辑。TASK-039 依赖源资产流协议；TASK-040 依赖它的 deployment/calibration/readiness。Gateway
等待两个服务稳定，Web 等 Gateway。需要 CORE、多语言、对象存储、服务身份、环境管理、submission 或
设计系统变更时暂停并升级上游。

## 迁移与交付

先用 Testcontainers MySQL 8.4 验证 problem/judging V1 和重复 migrate，再准备私有源资产根、judge
testdataRoot、权限、容量、备份与同文件系统临时目录。发布顺序：migration/目录 → judging-service →
problem-service → Gateway → Web。生产不 seed 用户、题目、数据或环境；ACTIVE 环境由运维显式准备。
开发 profile 可幂等准备环境/C++ 与 A+B 验收素材。

上线先只开放 ADMIN，完成一条真实 A+B 上传—部署—验证—发布并检查公开响应后，再开放用户导航。禁止
Flyway clean；已发布 migration、READY 资产和审计只能追加/保留。

## 风险

优先级：隐藏数据泄漏或下载越权；ZIP 穿越/炸弹；文件与 DB 半状态；部署 hash 不一致；JWT/CSRF/幂等
错误；跨服务调用进入事务；并发发布/保存覆盖；参考源码泄漏；Markdown XSS；查询/流式传输资源耗尽。
每项均需正常和负向自动测试及独立复核。

## 验证

- 契约：OpenAPI、示例、生成类型、敏感字段/权限/413/409/502-504 负例。
- problem-service：MySQL clean migration、约束、状态机、rowVersion、审计、公开防泄漏、ZIP fuzz/zip-slip/
  bomb/限额、原子文件、崩溃恢复、下载/绑定和发布并发。
- judging-service：MySQL V1、ACTIVE/语言、流式部署、hash/manifest、目录原子切换、重复/冲突部署、参考
  程序 AC/WA/CE/超时、限制零值、VALID 唯一与 readiness。
- Gateway：匿名 public、ADMIN/USER/CSRF、rowVersion/资源状态、multipart/binary 背压/取消、request ID、
  下游契约和故障映射。
- Web：format/lint/typecheck/Vitest/build/Playwright；公开列表/详情和管理端创建、编辑冲突、上传进度/
  失败、下载、绑定、部署、校准、预览、发布、修订、权限、键盘与 320px。
- 全量：后端 `clean verify`、Web `check/build/test:e2e`、文档检查、diff 范围；Linux 目标环境执行文件权限、
  原子 rename 和 Go judge 联调，macOS 单测不能替代。

## 回退

先关闭 ADMIN 写入口并等待在途流结束，再撤 Web、Gateway、problem、judging 应用。表、READY ZIP、已部署
目录、标定和审计保留；不回滚已发布事实、不改 migration、不递归删除资产。失败的临时目录只由明确
恢复工具按配置根和状态清理。若 migration/目录准备在开放流量前失败，停止发布并按备份恢复目标环境。

## 变更记录

- 2026-08-30：状态变更：draft → review。原因：契约先行的四任务顺序、迁移发布、验证和回退计划已补全，提交负责人审核
- 2026-08-30：根据范围反馈扩展为八个实施 TASK，加入题目管理、源资产、判题部署/校准和发布编排。
- 2026-08-30：状态变更：review → approved。原因：负责人已批准八个 TASK 的实施顺序与验证计划
