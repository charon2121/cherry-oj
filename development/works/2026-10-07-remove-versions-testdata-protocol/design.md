# 设计：去版本化与测试数据协议

工作说明见 [00-work.md](./00-work.md)，协议本身见 [`docs/testdata-protocol.md`](../../../docs/testdata-protocol.md)。
下面的事实来自 2026-10-07 对代码的只读调查。**设计已于 2026-10-07 经用户确认**（见 §5），
尚未授权实施。

## 1. 现状

### 1.1 数据在各服务之间怎么流

```
管理员上传 ZIP ──▶ problem-service：存成 assets/<id>.zip，库里记 test_data_version（READY、摘要、清单）
                     │
管理员点"部署" ────▶ problem-service ──▶ judging-service ──▶ 选第一个在线节点，multipart 推送 ZIP
                                                            节点校验落盘，返回绑定会话的回执
                                                            judging-service 记 test_data_node_deployment
管理员点"标定" ────▶ judging-service 在持有这份数据的节点上跑参考解，记 language_calibration
                     （按 problem_version × language）
管理员点"发布" ────▶ publish-check：节点在线、语言支持、数据已部署、标定 VALID，通过后版本 PUBLISHED

用户提交 ─▶ submission-service ─▶ problem-service 取快照（版本号、数据版本号、摘要、测试点数）
                              ─▶ judging-service 取执行画像（标定、限制、预算）
                              ─▶ 冻结 JudgeInput（含 problemVersionId、testDataVersionId……）
判题 ─────▶ judging-service 取 JudgeInput，只选"本会话持有该数据版本"的节点，POST /judge
            judge 按 testDataVersionId 读节点本地 <root>/<版本号>/ 目录
```

### 1.2 版本相关的东西有哪些

| 层 | 内容 |
|---|---|
| problem-service 表 | `problem`（含 `current_published_version_id`）、`problem_version`（题面、代码模式、难度、标签、checker、绑定的数据版本、五状态）、`test_data_version`、`problem_sample`、`problem_version_language`、`problem_audit_event`（都挂在版本上） |
| judging-service 表 | `language_calibration`（按题目版本×语言）、`test_data_node_deployment`（回执）、`judge_node` 的会话与数据可用性联动 |
| 接口 | 管理端 `/versions` 一族（创建、读、改、删、预览、部署、标定、发布检查、发布）；`/test-data/{testDataVersionId}/download`；`/internal/admin/deployments`；节点 `/internal/judge-node/v1/install` |
| 契约 | `judge`、`judge-input`、`judge-node`、`execution-profile`、`problem-judge-snapshot`、`custom-run-internal`、`submission`、`web-api.openapi` 共 8 份 |
| Go | `judge/node/install`（安装、回执）、`testcase.Load(root, versionId)`、`JudgeRequest` 的两个版本字段、`judge.testdataRoot` 配置 |
| 前端 | 路由 `admin.problems.$problemId.versions.$versionId`、管理工作台、题目详情、提交面板与历史、自测、生成的 API 类型、9 个涉及版本的 e2e 文件（`e2e` 6 个、`e2e-live` 3 个） |
| 脚本与部署 | `scripts/work-002-e2e.py`、`judging-service/scripts/node-e2e.py`、`deploy/sandbox-linux` 下的安装与回归脚本、`scripts/contracts_test.py` |

约 100 个文件含版本字段：problem-service 27、judging-service 20、web `features` 16、gateway 8、
submission-service 7、judge-engine 约 17，另有契约、生成物与 e2e。

## 2. 目标状态

```
管理员上传 ZIP ──▶ problem-service：解压、校验成对，按协议写出目录与 testdata.json，
                   库里更新这道题的 地址 / 摘要 / 测试点数 / 总字节数
管理员点"标定" ──▶ judging-service 取题目当前测试数据信息，任选在线节点跑参考解，
                   记 language_calibration（题目 × 语言，带当时的数据摘要）
管理员点"公开" ──▶ 检查：有测试数据、有 cpp 语言、标定 VALID 且摘要未过期、有在线节点

用户提交 ─▶ submission-service ─▶ problem-service 取快照（题目、标题、模式、语言是否支持）
                              ─▶ judging-service 取执行画像（标定 + 限制）
                              ─▶ 冻结 JudgeInput（源码、语言、限制、标定编号；不含任何版本或地址）
判题 ─────▶ judging-service：取 JudgeInput → 向 problem-service 取题目当前测试数据信息（地址、摘要、
            测试点数）→ 重新计算预算 → 任选在线且支持该语言的节点 → POST /judge（带地址）
            judge 按协议读取、复制、校验，逐点把输入喂给执行器；结果带回所用数据的摘要
```

关键决定（均已由你确认）：

- 题目只有一份内容；版本号、数据版本号、草稿与发布流水线全部移除，只保留"私有/公开"和"在用/归档"。
- 地址在**判题时**由 judging-service 取最新，不在提交时冻结。
- 数据库直接改写 V1。

### 2.1 总则：已有数据一律删除，不做任何兼容

用户的原话："目前所有已经存在的数据，都不用考虑任何兼容性处理，直接删除即可。"落实为：

- **没有任何迁移脚本和数据转换。** 各服务直接重写 V1；judging-service 的 V1–V5 合并后，`V2`–`V5` 与
  `EnvironmentRemovalMigrationTests` 一并删除。本地开发库重建，不保留旧表和旧行。
- **代码里没有双读、双写和旧字段兜底。** Go 的 `/judge` 直接不再认 `problemVersionId`、`testDataVersionId`；
  各服务的接口、DTO、契约字段一次改到位，不留过渡期。
- **已有的测试数据一律删除：** problem-service 里的 `assets/*.zip`、各节点本地的 `<root>/<版本号>/` 目录、
  测试服务器上手工放的数据，都不转换，管理员需要时按新流程重新上传。
- **已冻结的 JudgeInput、提交记录、标定、审计事件都随库清掉。** 因此 `JudgeInput.contractVersion` 不需要升版本，
  保持 `"2"`（这是相对原先默认值的唯一调整，见 §5.2 F）。

## 3. 逐服务改动

### 3.1 契约（先于实现）

| 契约 | 改动 |
|---|---|
| `judge.schema.json` | `JudgeRequest` 删 `problemVersionId`、`testDataVersionId`，加 `testDataLocation`（submit 模式必填，trial 模式不填）；`JudgeResult` 加可选 `testDataDigest` |
| `judge-node.schema.json` | 删 `Install`、`Receipt`、`Manifest`；保留 `Registration`、`Heartbeat`、`Lease`、`Error` |
| `judge-input.schema.json` | 删版本、数据摘要、测试点数、预算；`contractVersion` 保持 `"2"`（旧数据已全部删除，见 §2.1） |
| `execution-profile.schema.json` | 请求改为 `problemId` + `languageId` + `purpose`；响应删版本字段 |
| `problem-judge-snapshot.schema.json` | 删版本与数据版本字段 |
| `custom-run-internal`、`submission.json` | 删版本字段 |
| `web-api.openapi.json` | 删 `/versions` 一族与版本字段，题目接口合并；重新生成前端类型。**与 gateway、前端一起改（第 5、6 步）**：前端类型由它生成，契约测试里的 `expectedProblemVersionId` 断言也要同步，单独先改会让前端检查失败 |
| 新增 `problem-test-data.schema.json` | problem-service → judging-service：`GET /internal/judging/problems/{problemId}/test-data` → `{location, digest, caseCount, totalBytes}`，无数据时 404 |
| `scripts/contracts_test.py` | 随契约更新 |

协议 Schema 暂不进 `contracts/`（你说后续再考虑），先以 `docs/testdata-protocol.md` 为准。

### 3.2 Go 判题机（`apps/judge-engine`）

- **扩展 `judge/testcase` 包（不是新建 `judge/testdata`）：** Go 规范禁止把包目录命名为 `testdata`（工具链会整个忽略它）。
  `Load(ctx, opts, location)` 按协议解析地址（绝对路径或 http/https）、读 `testdata.json`、复制到本地工作目录并逐个核对
  大小与 SHA-256，返回 `Set{Cases, Digest}`，`Set.Close()` 删除副本。文件缺失或对不上时重读元数据并重试一次；元数据读不到
  或不合规不重试；出错一律走 SE。**第一版不做跨请求缓存：** 每次判题一份私有副本、判完删除，按 digest 复用缓存留作
  后续优化（需要淘汰与占用保护）；`PrepareWorkRoot` 在启动时清掉上一个进程遗留的副本。
- **`judge/flow`：** `loadCases` 改调新包；结果带回 `testDataDigest`；`ModeTrial` 不变。
- **`internal/contract/judge.go`、`judge/api/judge.go`：** 请求字段按 3.1 调整，submit 模式缺 `testDataLocation` 按
  既有规则返回 400，旧的两个版本字段按未知字段拒绝；`JudgeMode.UsesVersionedTestdata` 改名 `UsesTestData`。
- **删除 `judge/node/install`** 整个包、`node.Handler` 里的安装路由、`judge-node` 契约里的回执类型；
  `registry`（注册与心跳）、`identity`、`preflight`（rootfs 部署清单，与测试数据无关）保留。
- **配置：** 删 `judge.testdataRoot` 与节点安装的五个上限（`maxArchiveBytes` 等）；新增 `judge.testdata`：`workRoot`、`maxFileBytes`、`maxTotalBytes`、`fetchTimeout`。
  `judge.example.yaml`、Compose、`deploy/sandbox-linux` 的渲染与安装同步修改，并保证 judge 对本地地址有只读权限。
- 测试：重写 `testcase` 与 `flow` 相关测试，新增协议读取的单元测试（成功、元数据各种不合规、缺文件、哈希不符、
  顺序、上限、HTTP、撞车重试一次与只重试一次）；删除 `install` 包及其测试。

### 3.3 problem-service

数据库（直接改写 V1）：

| 表 | 改动 |
|---|---|
| `problem` | 并入原 `problem_version` 的内容列（`code_mode`、`title`、各 Markdown、`difficulty`、`tags_json`、`checker_type`）；新增 `test_data_location`、`test_data_digest`、`test_data_case_count`、`test_data_total_bytes`、`test_data_updated_at`（均可空，上传后才有）；删 `current_published_version_id`；约束"公开必须已有测试数据" |
| `problem_sample`、`problem_version_language` | 外键改指 `problem`；后者改名 `problem_language` |
| `problem_audit_event` | 删 `problem_version_id` |
| `test_data_version`、`problem_version` | 删除 |

代码：

- **删除：** 版本相关接口与服务（创建、读、改、删、预览、部署），`TestDataController` 里绑定版本的接口，
  `HttpJudgingClient.deploy`，`SubmissionSnapshotService` 里按版本解析的逻辑。
- **改写：** `AdminProblemService`（题目直接增改，含样例与语言）、`PublicProblemService`（直接读题目）、
  `ProblemPublicationService`（只剩 `calibrate`、`publish-check`、`publish`，作用在题目上）、
  `TestDataService`（上传 → 解压 → 校验成对 → 按协议写目录 → 更新题目上的数据信息）、
  `TestDataRecovery`（清理临时目录与过期旧目录）、`DevProblemSeed`。
- **写目录的方式（建议）：** 每次上传写出内容寻址的真实目录 `<root>/.store/<problemId>-<digest前缀>/`，
  最后写 `testdata.json`；题目地址 `<root>/<problemId>` 是指向它的符号链接，更新时用原子重命名换链接，旧目录
  延迟清理，保证读取方要么看到旧的完整数据，要么看到新的完整数据。
- **第一版只产生本地路径地址**（同机或共享卷）；通过 HTTP 对外提供目录是后续工作。judge 侧从第一天就
  同时支持两种地址。
- **新增** `GET /internal/judging/problems/{problemId}/test-data`，供 judging-service 使用。

对外接口（经 gateway）：

| 现在 | 之后 |
|---|---|
| `POST /problems/{id}/versions`、`GET/PATCH/DELETE …/versions/{vid}`、`…/preview` | 删；`PATCH /problems/{id}` 直接改全部内容，预览由前端用当前内容渲染 |
| `PUT …/versions/{vid}/test-data`（绑定）、`POST …/deployment` | 删 |
| `GET/POST /problems/{id}/test-data`、`…/{tdvId}/download` | `GET/PUT /problems/{id}/test-data`（上传即替换）；下载暂不提供 |
| `POST …/versions/{vid}/calibration`、`GET …/publish-check`、`POST …/publish` | `POST /problems/{id}/calibration`、`GET /problems/{id}/publish-check`、`POST /problems/{id}/publish`，另加取消公开 |

### 3.4 judging-service

数据库（把现有 V1–V5 合并为一个新的 V1，删除 V2–V5 及 `EnvironmentRemovalMigrationTests`）：

- 保留：`judge_node`（去掉环境与元数据，留 `languages_json`）、`judge_node_session`、`judge_node_registry_lock`、
  `judge_task`、`judge_attempt`、`outbox_event`、`inbox_event`、`judging_audit_event`（类型改为 `CALIBRATION`、`TASK`）。
- `language_calibration`：键改为（`problem_id`、`language_id`、`valid_slot`），**新增 `test_data_digest`** 记录
  标定时的数据指纹。
- 删除：`test_data_node_deployment`，以及 `judge_environment*`、`test_data_deployment`（本就在历史迁移里被删）。

代码：

- **删除：** `NodeDeploymentService`、`JudgeNodeClient.install` 与相关异常、`/internal/admin/deployments`、
  `JudgeNodeRepository` 的 `ready`、`recordReady`、`receiptVersion`、`invalidateReceipt`、`hashConflict`，以及
  `register()` 里"换会话就使回执失效"的逻辑。
- **改写：** `JudgingReadinessService`（检查项变为 `ONLINE_JUDGE_NODE`、`LANGUAGE`、`TEST_DATA`、`CALIBRATION`；
  标定必须 VALID 且 `test_data_digest` 等于题目当前摘要）、`SubmissionExecutionProfileController`
  （按 `problemId` 解析，向 problem-service 取当前测试点数来算预算）、`FormalInput`、`FormalWorker`（判题时取
  最新地址与摘要，重算预算，任选在线节点，请求带 `testDataLocation`，保存结果里的 `testDataDigest`）、
  `TrialController`（任选在线节点，不再要求"持有数据"）、`JudgeGateway.JudgeRequest`。
- **新增** 访问 problem-service 内部接口的客户端。

### 3.5 submission-service

- `Input` 删版本、数据版本、数据摘要、测试点数、预算；`contractVersion` 保持 `"2"`。
- 快照与画像两次调用按 3.1 调整；`View`、`Source` 删 `problemVersionId`、`problemVersionNo`。
- 创建提交的请求删 `expectedProblemVersionId`，同时删除 `PROBLEM_VERSION_CHANGED` 冲突。
- `judge_input` 以 JSON 存储，预计表结构不变，实施时核对。

### 3.6 gateway-service

路由与 DTO 同步 3.3 的接口变化；`ProblemServiceClient`、`SubmissionServiceClient` 的版本字段删除。

### 3.7 前端

- 删除路由 `admin.problems.$problemId.versions.$versionId`，其工作台并入 `admin.problems.$problemId`；
  管理列表、详情、提交面板、提交历史、自测、本地代码草稿（`code-draft` 里以版本为键）去掉版本。
- 重新生成 API 类型；涉及版本的 9 个 e2e 文件（`e2e` 6 个、`e2e-live` 3 个）按新流程改写。

### 3.8 脚本、部署与文档

- `scripts/work-002-e2e.py`、`judging-service/scripts/node-e2e.py`、`deploy/sandbox-linux` 的安装与回归脚本
  （`verify-native.py`、`verify-faults.py`、`judge_program.py` 等）改为不再安装测试数据，或直接写协议目录。
- Compose：`PROBLEM_TESTDATA_ROOT`（必填）指向的宿主机目录按**同一个绝对路径**只读挂进 judge 容器，题目上记的本地路径在容器里同样有效；
  `scripts/testdata_pack.py` 给一个放着成对 `.in/.out` 的目录写出 `testdata.json`，用于手工准备数据与后续 benchmark。
- **全局文档必须同步**（至少）：`product.md`（见 §5.1）、`data-model.md`、`architecture.md`、`database-design.md`、
  `engine.md`（§5.2 数据布局、WORK-040 数据交付一节、`/judge` 请求示例）、`status.md`、
  `apps/server/README.md`、`CONFIGURATION.md`、`judge.example.yaml`，以及 `docs/testdata-protocol.md` 的落地说明。

## 4. 分步计划

每步是一个或几个独立提交，每个提交都能独立编译、该步测试通过。中间状态不要求全链路跑通，最后一步统一验证。

| 步 | 内容 | 验证 |
|---|---|---|
| 0 ✅ | 改写 `product.md` 的产品规则（§5.1，A 已确认） | 文档检查 |
| 1 ✅ | 契约：更新各 Schema 与 `contracts_test.py`，新增内部接口定义（`web-api.openapi.json` 推迟到第 5、6 步） | `contracts_test.py`、文档检查 |
| 2 ✅ | Go：扩展 `testcase` 包、请求字段、删除安装接口与回执、配置、Compose、部署脚本渲染 | `go vet`、`go test -race`；用本地目录和 HTTP 夹具做协议读取测试 |
| 3 | problem-service：V1 改写、实体合并、写协议目录、接口改写 | `mvnw clean verify`（含重写的集成测试） |
| 4 | judging-service：V1 合并、删部署、标定改键、画像与 FormalWorker | 同上 |
| 5 | submission-service 与 gateway | 同上 |
| 6 | 前端：路由、页面、生成类型、e2e | `npm run check`、`npm run build` |
| 7 | 脚本、Compose、部署文档与全局文档同步 | 文档检查、`contracts_test.py` |
| 8 | 全链路：本地 Compose 加测试服务器，创建题目→上传数据→标定→公开→提交→得到结果 | 实际运行证据，写入工作说明的"结果与验证" |

## 5. 已确认的决定与风险

### 5.1 产品规则变更（用户已于 2026-10-07 明确同意）

这次重构与 `docs/product.md` 现有的上游约束直接冲突，改写前先列清楚：

| 现有规则 | 之后 |
|---|---|
| **PRD-RULE-002** 发布版本不可变，语义变化创建新版本 | 作废：题目直接修改 |
| 原则 3「版本与快照优先」：已发布题目与正式提交的含义不随改题而变化 | 改写：提交只冻结源码、语言与限制，题面和数据是活的 |
| 原则 6、§3.3、§4.5：结果可追溯到"题目版本、数据版本" | 改写：改为可追溯到**判题时所用数据的摘要**（见 C） |
| PRD-RULE-004、005：限制按"题目版本×语言"解析 | 改为"题目×语言"；限制仍随 JudgeInput 冻结 |
| §3.3、§3.4、§4.1、§4.3–§4.5、§5.1（管理员发布题目）、§九与§十中涉及版本的条目 | 重写为单一 Problem 与简化的公开流程 |

PRD-RULE-003（提交输入不可变）和 006（用户错误与系统错误分离）不受影响。

### 5.2 已确认的决定

2026-10-07 用户答复：A 同意推翻、B 与 C 按默认，并给出 §2.1 的总原则；未单独答复的 D–H 按默认执行。

| # | 问题 | 结论 |
|---|---|---|
| A | 推翻 §5.1 的产品规则 | **同意**。实施的第一步先改写 `product.md` |
| B | 数据更新后旧标定的摘要对不上，是否阻止新提交 | **阻止**直到重新标定；公开题目会暂时不可提交，后台提示"需重新标定" |
| C | 判题结果记录所用数据的摘要 | **记录**（`JudgeResult.testDataDigest`，judging-service 随结果保存） |
| D | problem-service 第一版只生成本地路径地址，HTTP 提供数据留作后续 | 是（默认） |
| E | judging-service 把 V1–V5 合并成新的 V1 | 合并（默认；与 §2.1 一致） |
| F | `JudgeInput.contractVersion` 升到 `"3"` | **不升**，保持 `"2"`：旧数据全部删除，没有需要区分的历史（由 §2.1 推出，调整了原默认值） |
| G | 管理端第一版不提供下载测试数据 ZIP | 不提供（默认） |
| H | 编辑已公开的题目立即生效，没有草稿缓冲 | 接受（默认） |

### 5.3 其他风险

- **体量与回归面大：** 题目、标定、公开、提交是主链路，重构期间主链路会中断，直到步骤 8。建议在独立分支做，
  或接受 main 上这段时间 e2e 不通。
- **重写的测试多：** problem-service、judging-service 的集成测试大部分要改，前端 9 个 e2e 文件要改，这是工作量大头。
- **已知缺口（按你的安排暂缓）：** judge 会读取请求里给出的任何地址；上线前需补地址白名单。
- **没有真实部署过的节点：** 步骤 2 改动 `deploy/sandbox-linux`，需要在测试服务器上重新安装验证。
