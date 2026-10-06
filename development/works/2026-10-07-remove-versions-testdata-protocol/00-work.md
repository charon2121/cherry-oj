# 去版本化与测试数据协议

类型：维护（含产品规则调整） · 创建：2026-10-07 · 状态：第 0–3 步已实施（2026-10-07），其余待授权

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
