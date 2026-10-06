# 去版本化与测试数据协议

类型：维护（含产品规则调整） · 创建：2026-10-07 · 状态：第 0–2 步已实施（2026-10-07），其余待授权

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
