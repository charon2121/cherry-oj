# 去版本化与测试数据协议

类型：维护（含产品规则调整） · 创建：2026-10-07 · 状态：第 0–7 步已实施（2026-10-07），其余待授权

## 为什么做

题目现在有一套很重的版本机制：`problem_version`、`test_data_version`，加上草稿、验证、评审、发布、
归档五个状态，以及把测试数据"部署"到判题节点、节点回执、按版本标定。它牵连了数据库、五个 Java 服务、
Go 判题机、前端和契约（约 100 个文件），而当前产品并不需要追溯历史版本。

同时，测试数据靠「控制面推送 ZIP 到节点」交付，Java 与 Go 各维护一套清单、哈希和部署状态，彼此很难对话。

## 打算怎么解决

1. **去掉版本：** 一道题只有一份内容，改了就是改了。题面、样例、可选语言、测试数据直接挂在题目上；
   草稿、发布流水线收敛为一个"私有 / 公开"开关。
2. **统一测试数据协议**（已确认，见 [`docs/testdata-protocol.md`](../../../docs/testdata-protocol.md)）：
   problem-service 按协议写出目录和 `testdata.json`，数据库只存**地址**；judging-service 判题时取题目当前
   地址，原样放进 `POST /judge`；judge 读 `testdata.json`，复制校验后把输入作为标准输入交给执行器。
   节点安装接口、数据回执、部署状态全部删除。

完成后：任何节点都能判任何题，不再有"数据装在哪个节点"的问题。

## 范围和代价

- **推翻现有产品规则（用户已同意）。** `docs/product.md` 的 PRD-RULE-002（发布版本不可变）、"版本与快照优先"和
  "结果可追溯到题目版本、数据版本"作废；改为追溯到判题时所用数据的摘要。
- **用户可见的变化：** 改题面或数据立即对线上生效，没有草稿缓冲；重判旧提交用的是当前数据；提交时的
  "题目已更新，请刷新"提示和提交历史里的题目版本号消失。
  测试数据更新后，旧标定自动过期，直到重新标定前该题不能提交。
- **已有数据一律删除，不做任何兼容**（用户已确认）：数据库直接改写 V1、不写迁移脚本，旧测试数据与提交记录不转换，
  本地开发库重建。
- **体量大：** 分 8 步，每步独立提交、独立验证，预计跨多轮完成。
- 安全限制（地址白名单等）暂不做，记为已知缺口。

## 怎样算完成

- 仓库里不再有题目版本、测试数据版本的概念（历史工作记录除外）。
- 管理员能创建题目、上传测试数据、标定、公开；用户能提交并得到判题结果；全链路在本地与测试服务器跑通并留证据。
- judge 只靠请求里的地址读数据，节点安装接口与回执已删除。
- `docs/` 中受影响的全局文档已同步，Java、Go、Web、契约的既有检查全部通过。

## 技术备注

方案、影响清单、逐服务改动、数据库、分步计划、已确认的决定与风险见 [design.md](./design.md)。

## 进度与验证（第 0–2 步，2026-10-07）

已完成：产品规则改写（`docs/product.md` v0.6）、契约、Go 判题机（含 Compose 与部署渲染）。Java、前端、其余脚本与全局文档同步还没动。

- **契约：** 去掉 `problemVersionId`、`testDataVersionId`、`problemVersionNo`、`testDataContentSha256` 等字段；`judge-node` 删除
  `Install`、`Receipt`、`Manifest`；新增 `problem-test-data.schema.json`；`JudgeRequest` 加 `testDataLocation`，`JudgeResult` 加
  `testDataDigest`。`scripts/contracts_test.py` 13 项通过，并新增"版本字段不得回流"的回归测试（`web-api.openapi.json` 暂为例外）。
- **Go：** `gofmt` 干净、`go vet` 无输出、`go test -race -count=1 -p=1 ./...` 全部通过。`judge/testcase` 新增 20 余个用例
  （各种不合规元数据、文件缺失/截断/改写、大小上限、HTTP、撞车重试一次且只重试一次、工作目录清理）；删除 `judge/node/install`。
- **真实容器端到端：** Compose 起 judge 容器，本地路径与 HTTP 两种地址各判一题：AC、WA 正确，结果带 `testDataDigest`
  （与 `testdata_pack.py` 输出一致）；数据缺失、相对路径判 SE；旧版本字段被 400 拒绝；改了 `.out` 却没更新 `testdata.json` 判 SE
  （不是 WA）；重新打包后新指纹生效；判完容器内工作目录为空。
- **digest 算法两种语言一致：** Go 测试与 `scripts/testdata_pack_test.py` 钉同一个黄金值。
- **没有验证 / 已知遗留：** 没有在 Linux 真沙箱后端（测试服务器）上重装并跑新版 judge；`scripts/work-002-e2e.py`、
  `judging-service/scripts/node-e2e.py`、`deploy/sandbox-linux` 下依赖旧请求字段的回归脚本、`docs/engine.md` 等全局文档都还是旧的，留给第 7 步；
  Java 各服务此刻仍按旧契约工作，系统要到第 8 步才能全链路跑通。

## 进度与验证（第 3 步：problem-service，2026-10-07）

题目与版本合并成单一实体；测试数据按协议写成目录，库里只存地址；对 judging-service 提供取地址的内部接口。

- **数据库：** 直接改写 `V1__create_problem_tables.sql`：`problem` 并入题面、模式、标签、测试数据地址与首次公开时间；样例、语言、审计事件改挂在题目上；
  `problem_version`、`test_data_version` 删除。数据库约束：公开的题目必须有测试数据地址和首次公开时间。
- **存储：** `FileTestDataStore` 校验 ZIP（沿用原有的防 zip 炸弹、包装目录、UTF-8、成对检查，并收紧测试点名字到协议的 124 字符），写成内容寻址的真实目录，
  再用相对符号链接的原子重命名切换题目地址；只保留当前与上一代。`TestDataMetadata` 把协议的排序、digest、结构校验做成纯函数。
- **服务与接口：** 题目增删改查、预览、归档、删除（仅从未公开过的）；`PUT/GET /test-data`；标定、公开检查、公开、取消公开；
  `GET /internal/judging/problems/{id}/test-data`（只认 judging-service 的服务令牌）。版本相关接口、部署、节点回执全部删除。
- **验证：** `mvnw -pl problem-service -am test` 89 个测试全部通过，含 MySQL 8.4 Testcontainers 集成测试：上传写目录与只存地址、替换后同一地址指纹立即变化、
  非法上传不动已有数据、并发上传收敛到一致状态、符号链接切换期间读取方每次读到完整数据（两个方向都断言）、标定带当前地址与指纹且不改题目、
  公开检查之后数据被换则拒绝公开、公开题目不能被改成空题面、只有从未公开的题目能删除。契约对齐测试把 `problem-test-data` 示例解进 Java DTO。
- **跨语言实测：** 用 Java 的存储类写出一份数据（含相对符号链接、测试点 1、2、10），Go judge 容器经 Docker bind mount 读取并判题，AC 与 WA 都正确，
  digest 与 Python 工具、Go 测试算出的完全一致——三种语言对同一份数据得到同一个指纹。
- **验证中发现并处理的问题：** macOS 上读取撞上符号链接被重命名替换的瞬间偶尔得到 `EINVAL`（Linux 上重命名对路径查找严格原子，不会）。
  协议本来就规定读取方重读重试一次，所以把"读本地文件时的操作系统报错"也纳入 Go judge 的那一次重试，并在协议文档里写明；
- **没有验证 / 遗留：** gateway、前端、submission-service、judging-service 仍按旧接口，系统要到第 8 步才能全链路跑通。judging-service 还没有实现
  取地址的客户端与令牌配置，`application-local.yaml`（私有、未入库）里需要自行加上 `cherry.service-calls.judging-problem-tokens`。
  测试数据目录对其他系统用户可读（judge 通常是另一个用户），隐藏数据的访问控制属于已知缺口，与地址白名单一起留到安全工作里。

## 进度与验证（第 4 步：judging-service，2026-10-07）

- **数据库：** V1–V5 合并为一个新的 V1，删除 `EnvironmentRemovalMigrationTests`。`language_calibration` 改按（题目、语言）唯一 VALID，并记录标定时的 `test_data_digest`；
  `judge_attempt` 增加 `test_data_digest`（判题实际读取的数据指纹）；`test_data_node_deployment` 及相关逻辑全部删除。
- **删除：** 节点部署服务、节点安装客户端与回执、`/internal/admin/deployments`、上传限额与 multipart 配置、节点"持有数据"的路由条件。
- **新增：** `problem` 包（向 problem-service 取题目当前测试数据，令牌 `cherry.judging.problem.token`，须属于 problem-service 的 `judging-problem-tokens`）。
- **改写：** 标定请求带 problem-service 给出的地址与指纹，判题结果指纹不一致则标定作废（`TEST_DATA_CHANGED`）；就绪检查只剩 `ONLINE_JUDGE_NODE`、`LANGUAGE`、`CALIBRATION`，
  标定指纹与当前指纹不同视为过期；执行配置按题目×语言解析，预算用题目此刻的测试点数重算；正式判题在派发时取最新地址并任选在线节点，指纹随结果落库；
  自测不读测试数据，任选在线节点。`JudgeRequest` 无版本字段，`JudgeResult` 带 `testDataDigest`。
- **验证：** `mvnw -pl judging-service -am test` 通过（30 个，1 个需真实 judge 的测试默认跳过），含 MySQL 8.4 与 Kafka 的集成测试：数据更新后旧标定过期并要重新标定、
  标定中途数据被换则作废、判题结果缺少指纹同样不算成功、执行预算随当前测试点数变化、指纹随尝试保存。另用 Java 网关对着 Go judge 容器、
  读 Java 写出的数据集，AC/WA/CE 判定正确（`CHERRY_REAL_JUDGE_URL` 测试）。
- **没有验证 / 遗留：** submission-service、gateway、前端仍按旧接口（第 5、6 步）；`judging-service/scripts/node-e2e.py` 仍是旧流程（第 7 步）；
  本机私有 `application-local.yaml` 需自行加 `cherry.judging.problem.token`（与 problem-service 的 `judging-problem-tokens` 配对）。

## 进度与验证（第 5 步：submission-service、gateway-service、web-api.openapi.json，2026-10-07）

- **契约先行：** `contracts/web-api.openapi.json` 去掉全部版本与测试数据版本：删 `…/versions/*`、`…/test-data/{id}/download`、绑定、部署；题目管理改为
  `GET/PATCH/DELETE /api/admin/problems/{id}`、`GET /preview`、`GET/PUT /test-data`（上传即替换，返回指纹、测试点数、清单）、`POST /calibration`、
  `GET /publish-check`（六项）、`POST /publish`、`POST /unpublish`。`AdminProblem` 是题目本身（含 `testData`），列表项是 `AdminProblemSummary`（含 `hasTestData`）；
  校准带 `testDataDigest`、不再带 `rowVersion`；提交与自测请求去掉 `expectedProblemVersionId`，各响应去掉版本字段。该文件重新序列化过，所以 diff 看起来整体改动，语义变化以上述为准。
  `scripts/contracts_test.py` 里的临时例外已移除，现在所有契约文件一律不得含版本字段。
- **submission-service：** DTO（`Create`、`View`、`Snapshot`、`Profile`、`Input`、`Source`）按契约去版本；`Input` 不再冻结测试数据版本、指纹、测试点数和预算；
  执行配置按题目×语言请求；提交总数改由判题结果里的 `totalCount` 提供（有执行数时必须带）；删除 `PROBLEM_VERSION_CHANGED` 冲突；自测同步去版本。数据库 V1 的表结构本来就不含版本列，无需改。
- **gateway-service：** 路由与 DTO 同步新契约；`ProblemServiceClient` 删除版本、下载、绑定、部署相关方法，新增 `replaceTestData`（PUT multipart 流式转发）、`unpublish`、`deleteProblem` 等；
  新增 `OpenApiAlignmentTests`，把网关 DTO 的字段与 OpenAPI 逐一对齐，并断言契约里不再出现任何版本概念。
- **验证：** 后端五个服务的完整测试通过（gateway 84、problem 89、submission 11、judging 30 + 1 个需真实 judge 的默认跳过）；契约测试通过。
- **没有验证 / 遗留：** 前端（第 6 步）仍按旧 API，类型要重新生成；端到端脚本与全局文档（第 7 步）；全链路（第 8 步）。

## 进度与验证（第 6 步：前端，2026-10-07）

- **类型：** 按新的 `web-api.openapi.json` 重新生成 API 类型；`generate:api:check` 通过。
- **删除版本概念：** 路由 `admin.problems.$problemId.versions.$versionId` 改名为 `admin.problems.$problemId`，工作台并入题目页，不再有草稿、修订、绑定测试数据版本、部署。
  管理列表显示标题、可见性与"测试数据已上传/未上传"，直接进入题目工作台。
- **管理工作台：** 一次保存整道题；公开题目可直接编辑并提示"保存会立即对学生生效"；归档题只读。测试数据步骤只有"上传（整体替换）"和"校准"，
  上传后提示旧校准已过期；公开步骤支持公开、取消公开，危险操作为归档与删除（只允许从未公开过的题目）。
- **学生侧：** 题目页去掉"有新版本、请切换"的横幅和切换对话框；本机代码草稿的键、提交恢复记录的键都不再带版本（分别升到 v2，旧记录自然不再被读到）；
  提交、历史、自测请求与展示去掉版本号与 `expectedProblemVersionId`。
- **验证：** `npm run check`（设计系统检查、API 生成检查、prettier、eslint、tsc、170 个单测）通过；Playwright 61 个通过、1 个跳过（`judge-node` 需要真实隔离栈，
  留到第 8 步）。重写与新增：`published-problem.spec.ts`（公开题原地编辑、取消公开、整体替换测试数据、归档只读）取代 `published-deployment.spec.ts`；
  `problem-workspace` 里"发布新版本后需手动切换"的用例改为"管理员改题不会替换正在编辑的代码"，丢失响应重试用例改为"题目被改后仍重试原代码与原请求键"。
- **没有验证 / 遗留：** `e2e/judge-node.spec.ts` 与 `e2e-live/*` 已按新契约改写但没有对真实栈运行；全链路（含这两处）在第 8 步。

## 进度与验证（第 7 步：脚本、部署与全局文档，2026-10-07）

- **端到端脚本：** `scripts/work-002-e2e.py` 按协议模型重写并在隔离栈上**实际跑通**（MySQL/Redis/Kafka + 五服务 + 真实 judge 容器）：上传 ZIP → problem-service 写协议目录 →
  按地址标定 → 公开 → AC/WA/CE/TLE → Kafka 与节点故障恢复 → worker 崩溃后租约回收 → **替换测试数据后旧校准过期、新提交被挡住、重新校准后读到新数据，
  已冻结的 JudgeInput 不变，判题结果记录所读数据的指纹** → 公开题原地修改立即生效、取消公开后不能提交、重新公开恢复 → 回滚开关与账号归属。
  顺手删除了脚本里已不存在的 sandbox 服务引用。旧的 `judging-service/scripts/node-e2e.py`（旧部署流程，且缺 submission 库与 Kafka）已删除，
  节点离线/恢复由 `work-002-e2e.py --keep` + `apps/web/e2e/judge-node.spec.ts` 覆盖（后者已改用新脚本的证据文件与环境变量 `WORK002_E2E_DIRECTORY`）。
- **Linux 部署脚本：** `deploy/sandbox-linux/{tests/judge_program.py,install/verify-faults.py,install/verify-native.py}` 的 JudgeRequest 去掉版本字段；
  `ci/business_*` 的真实业务流程改为新模型（上传即替换、无部署回执、标定核对数据指纹、判题尝试核对所读指纹），并加了 `judging_problem` 服务令牌。
  这些脚本的 Python 单元测试（ci 114 个、install 15 个）通过。
- **清理遗留：** gateway 与前端里已不会出现的 `PROBLEM_VERSION_CHANGED`、`PROBLEM_VERSION_NOT_FOUND` 删除。
- **全局文档：** `architecture.md`、`data-model.md`、`database-design.md`（问题与判题两部分直接据实际 V1 SQL 重写）、`engine.md`、`backend.md`、`frontend.md`、
  `status.md`、`apps/server/{README,TOOLCHAIN}.md`、编码规范中的一条示例同步。`docs_test` 与契约测试通过。
- **没有验证 / 遗留：** Linux 原生路径（`ci/business_*` 用的是原生安装的 judge）没有在 Linux 机器上运行，且原生 judge 以另一个系统用户运行，
  能否读到 `CHERRY_TEST_DATA_ROOT`（CI 里该目录在 0700 的运行目录下）需要在 Linux 上确认；测试服务器上的节点 `cherry-linux-3` 仍是旧版 judge，尚未升级。
  `judge-node` 与 `e2e-live` 的 Playwright 对真实栈运行归第 8 步。
