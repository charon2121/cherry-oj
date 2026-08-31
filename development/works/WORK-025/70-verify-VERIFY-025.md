---
id: "VERIFY-025"
type: "verify"
title: "交付题库、题目与测试数据管理"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["TASK-036"]
related: []
implements: []
verifies: ["FEATURE-007", "TASK-033", "TASK-034", "TASK-035", "TASK-036", "TASK-037", "TASK-038", "TASK-039", "TASK-040"]
tags: []
result: "pass"
created_at: "2026-08-30"
updated_at: "2026-08-30"
---


# VERIFY-025：交付题库、题目与测试数据管理

## 验证对象

验证 ADMIN 从创建草稿、上传/下载/绑定测试数据、部署、校准、参考程序、发布到修订的完整链路，以及
公开题库从 MySQL 经 Gateway 到 Web 的匿名列表/详情；覆盖不可见数据、文件一致性、失败恢复和无障碍。

## 对应要求

覆盖 `FEATURE-007` REQ-001～REQ-028、AC-001～AC-014，以及 TASK-033～TASK-040 的全部完成标准。

## 检查与结果

等待文档获批并实施后填写实际日期、JDK/Node/MySQL/浏览器环境、命令、结果和证据。至少包含：

- OpenAPI 与生成类型无漂移，公开 schema/响应敏感字段 canary 负例；
- MySQL 8.4 空库 migration、约束、索引、开发 seed 幂等与代表性数据 EXPLAIN；
- 私有源资产与 judge 目录权限、ZIP 恶意输入、原子封存/部署、hash/manifest、故障恢复与流式资源；
- problem/judging/Gateway 单元、MySQL/HTTP/真实 Go judge 集成、安全、幂等、并发和契约测试；
- Web format/lint/typecheck/Vitest/build/Playwright，含 public 与 ADMIN AC-001～AC-014、键盘和 320px；
- 后端全量 `clean verify`、文档系统检查和改动范围检查。

尚未执行，不预填通过。

### 2026-08-30：TASK-033 契约冻结（局部通过）

- `python3 -m unittest scripts.contracts_test`：9 项通过。
- `npm run generate:api` 与 `npm run generate:api:check`：OpenAPI 0.2.0 成功生成 2 个 TypeScript 文件且无漂移。
- `npm run typecheck`：通过，现有 Web 调用与新增类型可共同编译。
- 使用仓库现有 Ajv 对公开列表/详情示例求值：两个示例通过；向详情 `data` 注入
  `judgeTemplate=canary` 后因封闭 schema 被拒绝。
- `scripts/work check`：196 份开发文档通过。整体验证仍为 pending，等待 TASK-034～TASK-040 与
  TASK-035/036 实施。

### 2026-08-30：TASK-034 problem-service 读取基础（局部通过）

- `./mvnw -pl problem-service -am test`：10 项通过；包含 4 项 JWT/JWKS 与精确匿名 matcher、3 项服务/
  cursor 单测、2 项 MySQL 8.4 集成场景和应用入口检查。
- Testcontainers `mysql:8.4` 从空库成功应用 `V1__create_problem_tables.sql`，识别六张业务表；公开题无当前
  版本的 CHECK 负例被 MySQL 拒绝，dev A+B seed 重跑后仍只有一题。
- 25 个相同 `updated_at` 的公开题按 10 项分页，无重复/遗漏；第一页后新增更晚题目不会回流到旧 cursor；
  difficulty/tag/codeMode/language 组合筛选与 `TITLE_ASC` 排序通过。
- 将 `HIDDEN_CANARY` 写入内部 `judge_template` 后，公开详情 JSON 不含 canary、模板、测试数据、存储引用、
  作者或审计字段；私有/归档/非当前记录继续由 SQL 白名单排除并统一为 404。
- 代表性列表 `EXPLAIN` 使用 `idx_problem_listing`；列表只取 `size + 1`，语言按本页版本 ID 一次批量读取，
  详情固定三次查询，不执行 `COUNT(*)` 或 N+1。
- `./mvnw clean verify`：整个 7 模块 reactor 构建成功，Gateway 34、user-service 18、problem-service 10、
  submission-service 2、judging-service 2 项测试通过，共 66 项。

### 2026-08-30：TASK-037 题目草稿与版本管理（局部通过）

- MySQL 8.4 集成测试覆盖创建、整体保存、预览、删除后重建、修订复制、归档与审计；
  旧 rowVersion 不覆盖，已发布版本不可变。
- 两线程竞争相同 slug 及相同 Problem 修订均只有一个成功；失败方分别收到
  `SLUG_CONFLICT` 与 `ROW_VERSION_CONFLICT`。
- 安全 MockMvc 验证 USER 无法访问管理预览，ADMIN 预览响应含 `Cache-Control: no-store`；
  首次改密令牌继续由 JWT 边界拒绝。
- `./mvnw clean verify`：整个 7 模块 reactor 构建成功，Gateway 34、user-service 18、problem-service 16、
  submission-service 2、judging-service 2 项测试通过，共 72 项。

### 2026-08-30：TASK-038 测试数据资产版本（局部通过）

- 私有文件存储以 8 KiB 块接收/下载，同时计算原 ZIP SHA-256；逐文件解码和摘要后生成
  稳定 manifest，合法两 case 原包下载逐字节一致。
- 表驱动文件测试拒绝父路径、目录、symlink、Unicode/非 UTF-8、重复名、孤儿、损坏包、
  文件数/字节/压缩比超限；失败后不留临时文件或 READY 记录。
- MySQL 8.4 集成测试验证同题同 hash 顺序与两线程并发都收敛为同一 READY，跨题绑定拒绝，
  旧 rowVersion 不覆盖；断流、finalize 状态竞争与崩溃恢复均保留安全 FAILED 事实并清理有界文件。
- 元信息 JSON 不含 `storageRef`、测试正文或标准答案，审计只保存 id/hash/数量/状态；ADMIN
  下载带 `Content-Disposition` 和 `Cache-Control: no-store`，USER/匿名均被拒绝。
- `./mvnw clean verify`：7 模块 reactor 构建成功，Gateway 34、user-service 18、problem-service 25、
  submission-service 2、judging-service 2，共 81 项；随后新增的断流/finalize 故障注入 5 场景聚焦测试通过。

### 2026-08-30：TASK-039 部署、校准与 readiness（局部通过）

- Testcontainers `mysql:8.4` 从空库成功应用 judging V1；五张表、唯一 ACTIVE/VALID、READY hash CHECK、
  外键与审计约束通过。开发 fixture 幂等，生产无默认环境且显式 provision 不负责切换。
- 文件测试逐项核对 ZIP 与 manifest 的名称、大小、SHA-256、case 对、总量、UTF-8 和安全限额；目录以
  同盘 atomic rename 切换，0700/0400 权限通过。恶意 ZIP、断流、模拟 ENOSPC、DB finalize 竞争和启动
  恢复均无 READY 半事实或越界清理。
- MySQL/fake judge 测试验证同 hash 顺序/并发幂等、不同 hash 禁止覆盖，AC 新建/替代唯一 VALID，
  WA 与环境指纹不匹配不覆盖旧 VALID；readiness 同时检查 ACTIVE、language、matching deployment 与
  calibration。审计 actor 正确且不含源码、输出和测试正文。
- ADMIN MockMvc 验证匿名 401、USER 403、ADMIN `no-store`；缺失、零值、可选 clock 零值及超出 long 的
  限制在调用服务前返回 400。
- `./mvnw -pl judging-service -am test`：15 项，14 通过、真实 Judge 条件测试默认跳过 1 项；另以
  `CHERRY_REAL_JUDGE_URL=http://127.0.0.1:5051` 对现有 Linux Go Judge/Sandbox 运行该测试，A+B 的
  AC、WA、CE 和 `local-compose` 环境指纹全部通过。
- `./mvnw clean verify`：7 模块 reactor 构建成功，Gateway 34、user-service 18、problem-service 26、
  submission-service 2、judging-service 14（其中条件测试跳过 1）均无失败；新增 ENOSPC 注入后 judging
  最终完整测试再次通过。

### 2026-08-30：TASK-040 发布编排与不可变切换（局部通过）

- HTTP client 测试确认 ZIP 以 multipart 流发送，metadata 与 archive 分部正确，原 ADMIN JWT 和合法
  `traceparent` 委托到 judging-service；token 不进入 URL/正文，5xx 私有响应被映射为稳定安全错误。
- MySQL 8.4/fake judging 集成测试确认远程部署、校准和 readiness 时没有活动 problem 数据库事务；部署只
  接受版本当前绑定的本题 READY 数据与一致 hash，传输字节和 manifest 均与源资产一致。
- 阻塞校准期间读取到 `VALIDATING`；WA 与模拟 504 均条件恢复 DRAFT，AC 进入 READY_FOR_REVIEW；启动
  恢复只处理超过 15 分钟的 VALIDATING，不触碰新近操作。数据库、响应和审计均不保存参考源码 canary。
- publish-check 固定返回 CONTENT/SAMPLES/LANGUAGE/TEST_DATA/DEPLOYMENT/CALIBRATION 六项且不改状态；
  最终发布重新锁定 Problem/Version 和 READY 数据，在一个事务内写 PUBLISHED、当前指针与审计。
- 两线程并发发布只有一个写者，另一方得到 rowVersion 冲突；成功响应丢失后的重复 publish 返回同一
  `publishedAt` 且不重复审计。PUBLISHED 的 update/delete 被拒绝，显式复用测试数据的新修订仍可创建。
- `./mvnw clean verify`：7 模块均成功；Gateway 34、user-service 18、problem-service 34、
  submission-service 2、judging-service 15，共 103 项测试，102 通过、真实 Judge 条件测试跳过 1 项。

### 2026-08-30：TASK-035 Gateway 公开与管理 API（局部通过）

- 公开 controller 不解析 `WebSession`，安全过滤链改用 NoOp security context/request cache；假上游测试在
  无 Redis/Session 条件下取得同一公开事实，筛选与 opaque cursor 原样转发，不做重排、COUNT 或解码。
- 内部响应仅解码到 Gateway 自有白名单 record；题目及列表注入 `secretCanary` 后浏览器 JSON 不含该字段。
  header/body request ID 一致；畸形 JSON 为 502，慢响应为 504，非 `PROBLEM_NOT_FOUND` 的内部 404 为
  502，错误正文、内部 URL 和委托 JWT 均未进入公开响应。
- 全部 ADMIN problem/version/test-data/deployment/calibration/publish API 已接通。访问门面先阻止首次改密，
  再检查 ADMIN；既有 CSRF 对 PUT/PATCH/POST/DELETE 生效，rowVersion 只作资源乐观锁参数。
- multipart 测试确认 FilePart publisher 只订阅一次、分块进入下游 multipart 且 JWT 不进正文；下载测试以
  初始 demand=1 消费首块后取消，上游收到 cancel。Gateway 生成安全附件名，透传已知 Content-Length，
  响应为 `application/zip`、`no-store` 且不套 `ApiSuccess`。
- `./mvnw clean verify`：7 模块成功；Gateway 42、user-service 18、problem-service 34、
  submission-service 2、judging-service 15，共 111 项测试，110 通过、真实 Judge 条件测试跳过 1 项。

### 2026-08-30：TASK-036 Web 题库与题目工作台（通过）

- 环境为 Node `v26.3.0`、npm `12.0.2`、Chromium；仓库声明的生产基线仍是 Node 24，本机越界版本只产生
  engine 提示，没有绕过任何检查。
- `npm run check` 全部通过：设计系统包/消费侧/自测试、主题生成、OpenAPI 生成漂移、Prettier、ESLint、
  TypeScript 和 30 个 Vitest 文件的 103 项测试均无失败。
- endpoint schema 校验必要字段并剥离未知字段；MSW canary 测试确认 `storageRef` 不进入返回页面状态。
  GFM + sanitize 测试确认原始 HTML 不执行、危险 URL 不保留链接能力。
- `npm run build` 通过；Monaco 仅随 ADMIN 工作台异步 chunk 本地交付，不依赖运行时 CDN。构建报告该
  chunk 超过 500 kB 警告，但公开题库 chunk 未携带 Monaco，属于已知管理端加载成本而非失败。
- `npm run test:e2e`：26 项 Chromium 场景全部通过。新增场景覆盖匿名 URL 筛选恢复、真实详情链接、
  invalid cursor 错误、长中文、安全 canary/XSS、键盘、320px，以及 ADMIN 新建后路由到版本工作台并从
  服务端恢复题面、测试数据、部署校准和发布章节；既有 USER/首次改密权限场景继续通过。
- `git diff --check` 通过；TASK-036 未改写 `apps/web/design-system`、设计说明、server 或 judge-engine。

## 未通过项

暂无。

## 范围检查

实施后核对每个 TASK 的 `write_paths/forbidden_paths`，并确认 user/submission、Kafka/正式提交、Go 引擎
源码、判题 contracts、设计系统代码和说明包没有变化；若存在偏差，先升级文档，不在此补签。

## 遗留问题

暂无。

## 剩余风险

即使功能测试通过，真实题量、目标文件系统原子语义/容量/备份、8082/8084 网络隔离和目标 Go judge
环境仍需上线确认；未获得证据前不得把对应阶段标为 confirmed。

## 结论

本地自动化验证通过。真实 Judge 条件测试已在 TASK-039 以本地 Linux Go Judge 单独通过；生产文件系统、
网络隔离、备份与真实题量仍按“剩余风险”在上线阶段确认，不阻塞本次实现交付。

## 变更记录

- 2026-08-30：状态变更：draft → approved。原因：自动化与真实 Judge 验证证据齐全，结果通过
