---
id: "DESIGN-019"
type: "design"
title: "交付题库、题目与测试数据管理"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "EXPERIENCE-013"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-30"
updated_at: "2026-08-30"
---


# DESIGN-019：交付题库、题目与测试数据管理

## 背景

依据 `FEATURE-007` 与 `EXPERIENCE-013`。全局模型已定义 Problem、不可变 ProblemVersion、样例、语言、
TestDataVersion、TestDataDeployment、LanguageCalibration 和发布事务，物理 DDL 位于
`docs/database-design.md`；当前 problem/judging 服务只有鉴权骨架，尚无数据库、资产存储或业务接口。

## 目标与限制

目标是交付公开列表/详情和 ADMIN 可重复完成的 C++ ACM 人工出题链路。限制是浏览器只调用 Gateway、
服务只写本库、测试正文不进 MySQL/Kafka、生产 migration 不带业务 seed、已发布版本与 READY 数据不可
变、公开 DTO 不复用内部快照。首版不建设 CORE、多语言、自动题目工厂、环境管理 UI 或正式提交。

## 整体方案

```text
浏览器 /problems
  → Gateway public API
  → problem-service /internal/public/problems
  → 当前 PUBLIC + PUBLISHED 读模型

ADMIN /admin/problems
  → Gateway（Session + CSRF + rowVersion/资源状态）
  → problem-service（Problem/Version/Sample/Language/TestDataVersion/Audit）
       ├─ 私有 TestDataAssetStore：不可变 ZIP
       └─ 委托 ADMIN JWT 调 judging-service
            ├─ TestDataDeployment / LanguageCalibration / Readiness
            ├─ 原子部署到 judge testdataRoot
            └─ 调 Go judge 验证临时参考程序
```

OpenAPI 先定义 public/admin DTO、multipart 上传和二进制下载；Web 生成类型后仍用 Zod 校验 JSON 边界。
problem-service 内部响应不套公开 `data/meta`，Gateway 负责映射 request ID、pagination 和公开 Problem。
匿名只对 `/internal/public/problems/**` GET 精确放行；管理请求由 Gateway 转发 ADMIN JWT。problem-service
代表该 ADMIN 调 judging-service 部署/验证时委托同一个短 JWT，两个资源服务都独立验角色并记录 actor。

## 模块与数据

- `contracts/web-api.openapi.json`：public 列表/详情，ADMIN 题目/版本/测试数据/部署/校准/发布路径，
  rowVersion、multipart、binary、成功和稳定错误；生成 Web 类型。
- `problem-service`：按既有 DDL 建 V1，使用 MyBatis/Flyway/MySQL；实现公开读模型、草稿/版本/样例/语言/
  审计、测试数据元信息、发布状态机和事务。
- `TestDataAssetStore`：接口由 problem-service 定义。首版文件实现使用配置的私有根目录，`storageRef`
  只保存受控相对引用；上传写唯一临时目录，校验后 fsync/原子 rename 为不可变 ZIP。
- `judging-service`：按数据库设计建环境/语言/部署/标定/审计 V1；接收 ADMIN 委托的数据流，重新校验
  hash/manifest 后原子部署；用参考源码和绝对限制调用 Go judge，AC 后写入/替代 VALID calibration，
  并提供 readiness DTO。首个环境/语言由显式运维 CLI 从部署配置注册并激活；已存在 ACTIVE 时拒绝借此
  切换环境，完整环境管理不进入 Web 题目管理。
- `gateway-service`：持有 problem client 配置与超时，流式代理上传/下载，不访问业务数据库、
  不缓存题目真源、不在内存聚合完整 ZIP。
- `apps/web`：公开题库/详情；ADMIN 题目表、版本工作台、TanStack Form/Table、Markdown/Monaco 编辑、
  上传/下载/部署/验证/发布体验。只消费现有设计系统。

列表从 `problem.visibility/status/updated_at/id` 键集开始并连接当前版本；详情按 slug 后以主键批量读取
version/samples/languages，禁止 N+1。测试数据 ZIP 根只接受安全 case 名的成对 `.in/.out` 普通 UTF-8
文件。上传同时计算 ZIP SHA-256；解压计算文件 sha256/bytes，按稳定文件名生成 manifest，`totalBytes`
是解压后总量。READY 后资产和 metadata 均不可覆盖。

## 接口与状态

公开接口保持：

- `GET /api/problems`：q/difficulty/tag/codeMode/language/sort/cursor/size，返回 cursor pagination。
- `GET /api/problems/{slug}`：只返回当前公开版本；私有、归档、草稿和不存在统一 404。

ADMIN API 按资源组织：

- `/api/admin/problems`：列表、创建；`/{problemId}`：管理详情、归档。
- `/{problemId}/versions`：创建修订；`/{versionId}`：读取/保存/删除草稿与预览。
- `/{problemId}/test-data`：multipart 上传和版本列表；`/{testDataVersionId}/download`：二进制下载；
  绑定通过草稿 PATCH 的 `testDataVersionId` 表达。
- `/{problemId}/versions/{versionId}/deployment`：部署/查询；`/calibration`：提交临时参考源码与绝对限制；
  `/publish-check`：只读检查；`/publish`：显式发布。

保存/归档使用 rowVersion；部署和发布以明确资源/状态作为天然幂等边界。创建 slug、同题活动草稿和同题
READY 内容 hash 在 problem 行锁内查重；上传网络结果不明时先按题目重新列出版本，不盲目重传。
JSON 使用 ApiSuccess/ApiProblem；上传成功返回 201，下载使用
`application/zip + Content-Disposition + no-store + X-Request-Id` 且不套 JSON。字段/包超限 413，校验
422，rowVersion/状态冲突 409，权限 401/403，资产和下游故障 502/503/504。

公开 cursor 是版本化 base64url 负载，绑定最后 `updatedAt + problemId`、排序和规范化筛选摘要；读取
`size + 1`，不做总数。管理状态机由 problem-service 持有：验证前条件更新至 VALIDATING，远程调用在
事务外执行，成功置 READY_FOR_REVIEW、失败恢复 DRAFT；启动时只恢复超过时限的 VALIDATING。发布先在
事务外读取 readiness，再在本地短事务锁定 Problem/Version、重查本库不变量并写 PUBLISHED、当前指针、
审计。正式 Submission 未来仍重新解析 ExecutionProfile。

部署以 `(testDataVersionId, environmentId)` 唯一。judging-service 接收 expectedSha256、manifest 和 ZIP
流，写临时目录、逐项验证后原子 rename；文件成功后才写 DB READY。相同 hash 重试返回现有 READY，
不同 hash 禁止覆盖。校准以 problemVersion/language/environment 为组合，`benchmarkSummary` 只保存
sourceSha256、verdict 和有界资源摘要；参考源码只在本次请求和 Go judge 调用中存在，不写业务表、日志
或审计正文。

## 安全与失败

公开、ADMIN、内部快照 DTO 全部分离并显式映射。公开响应禁止 judgeTemplate、测试数据、storageRef、
manifest、作者和审计；ADMIN 元信息也不返回 storageRef 或测试正文。Markdown 作为不可信文本传输并在
Web 净化。所有查询参数绑定 SQL；slug/cursor/body/数组/Markdown/代码均有业务上限。

ZIP 先限制 Content-Length，再限制实际流；拒绝绝对/父路径、目录、symlink、设备文件、重复名、大小/
数量/压缩比超限、NUL 和 Unicode 名称混淆。目录/文件权限最小化，storageRef 不接受请求输入。下载和
部署逐块传输并在断开时关闭资源。JWT、参考源码、测试正文、标准答案、storageRef 和完整 manifest 不写
日志；审计只存 id、hash、数量、状态和失败 code。

ADMIN 写接口继续要求 CSRF，problem/judging 服务分别验证 JWT role/aud/iss。外部 HTTP、文件传输与 Go
judge 调用不在数据库事务内。上传失败保留 FAILED 元数据并清临时文件；部署失败保留 READY 源资产；
验证/发布失败保留草稿和旧公开指针。所有重试从服务端资源状态恢复，不从页面乐观推断。

## 监控与部署

记录 endpoint、结果分类、返回数、上传/部署字节数、caseCount、耗时和稳定错误 code；不把 q/slug/tag/
cursor、题面、源码或文件名作为指标标签。当前无遥测后端，只建立结构化日志与测试证据。

部署顺序：私有目录/权限/容量/备份 → problem/judging migration → judging-service → problem-service →
Gateway → Web。开发 profile 可幂等准备 ACTIVE 环境、C++ 配置和 A+B 验收素材；生产不 seed 用户、题目、
数据或环境，ACTIVE 环境由部署/运维显式准备。没有 ACTIVE 环境时管理端明确阻止部署/发布。

## 迁移与兼容

problem-service V1 严格创建已有六张表；judging-service V1 创建环境、语言、部署、标定和审计表，不提前
实现任务/消息 V2。发布后禁止改写 migration，只能追加。当前没有历史业务数据。新 public/admin API
兼容新增；回退时保留表、READY ZIP、部署目录和审计，不执行 clean 或递归删除。先关闭管理写入口，再
撤 Web/Gateway；不可变资产的离线清理必须另有审计任务。

## 备选方案

- 浏览器直连资源服务、Gateway 查业务库或缓存题目真源：破坏既定边界，不采用。
- 匿名题库强制登录或新增匿名机器 JWT：前者改变产品，后者为公开数据扩大身份系统；采用窄 public GET。
- offset + 总数：持续发布时漂移且 count 成本高；采用稳定 cursor。
- 测试正文存 MySQL：违背已确认容量/安全边界，排除。
- 首版直接绑定 S3/MinIO：供应商、凭据和部署尚未确定；采用消费方接口 + 私有文件实现，未来替换实现。
- problem-service 直接写 judge testdataRoot：破坏 judging-service 部署所有权和 hash 回执；采用流式部署。
- 先建设机器身份：适合后台自动化，但当前只有同步 ADMIN 动作；采用短 JWT 委托，异步化时重审。

## 风险与重审条件

组合筛选必须用代表数据 EXPLAIN；题量十万级、需要相关性/总数时重审搜索投影。8082/8084 暴露不可信
网络、多租户/付费可见性时重审身份授权。120 秒 ADMIN JWT 不足以启动目标大小部署、需要断点续传、
对象存储直传、异步多环境分发或自动任务时引入服务身份和异步协议。CORE、多语言、自动标定、环境管理
或正式提交必须新增范围，不能塞入本 TASK。

## 变更记录

- 2026-08-30：状态变更：draft → review。原因：契约、数据、problem-service、Gateway、Web、安全、游标、迁移与回退方案已补全，提交技术审核
- 2026-08-30：根据范围反馈加入题目草稿/版本、测试数据资产、部署、手工校准、参考程序验证和发布事务。
- 2026-08-30：状态变更：review → approved。原因：负责人已审核跨服务、数据与安全方案并允许实施
