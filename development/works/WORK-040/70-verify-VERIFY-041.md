---
id: "VERIFY-041"
type: "verify"
title: "重构判题节点注册与测试数据交付"
status: "approved"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["TASK-071", "TASK-072", "TASK-073", "TASK-074"]
related: ["VERIFY-039", "VERIFY-040"]
implements: []
verifies: ["ISSUE-012#AC-001", "ISSUE-012#AC-002", "ISSUE-012#AC-003", "ISSUE-012#AC-004", "ISSUE-012#AC-005", "ISSUE-012#AC-006", "ISSUE-012#AC-007", "ISSUE-012#AC-008", "TASK-071", "TASK-072", "TASK-073", "TASK-074"]
tags: []
result: "pass"
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# VERIFY-041：重构判题节点注册与测试数据交付

## 验证对象

Judge Node 自注册/心跳、环境归组、节点侧测试数据安装、逐节点回执、远程部署/readiness、可操作错误、
本地 Compose 迁移与 legacy 回退的完整链路。

## 对应要求

- ISSUE-012#AC-001～AC-002：注册幂等、首环境激活、租约离线与恢复。
- ISSUE-012#AC-003～AC-004：节点侧原子安装、回执校验、重复/冲突/失败清理。
- ISSUE-012#AC-005：本地与生产使用同一节点协议，不依赖 seed/共享目录。
- ISSUE-012#AC-006：稳定可操作错误和工作台前置状态。
- ISSUE-012#AC-007：V1→V2 兼容、现有事实不改写、legacy 回退。
- ISSUE-012#AC-008：跨语言契约、Go/Java/Web、MySQL/文件系统/Compose 和全仓回归。

## 检查与结果

验证环境：macOS arm64 宿主机、Java 21、Go 1.26、MySQL 8.4 Testcontainers、真实 Linux Compose Judge/sandbox、Chromium。所有迁移、故障注入和发布操作使用独立测试数据库、端口、容器与卷。

| 检查 | 命令 / 证据 | 结果 |
| --- | --- | --- |
| Go 全模块 | `cd apps/judge-engine && GOCACHE=/tmp/work040-go-cache go test -race ./...`；`go vet ./...`；`gofmt -l .` | 通过，race/vet 无错误、无未格式化文件 |
| Java 全聚合 | `cd apps/server && ./mvnw clean verify` | 150 项通过，失败/错误/跳过均为 0；包含真实 AC/WA/CE |
| Node 定向回归 | Registry、NodeDeployment、NodeMigration、JudgeNodeClient tests | 注册/租约/session、条件回执撤销、真实 V1→V2、严格类型及整体超时通过 |
| Web | `cd apps/web && npm run check && npm run build` | 36 个测试文件、136 项测试通过；类型、lint、格式、生成契约与设计系统检查通过 |
| 浏览器 | `WORK040_E2E_DIRECTORY=... npx playwright test` | 31 项通过，包含真实节点停止/恢复、重新部署及双主题/320px |
| 契约 | `python3 scripts/contracts_test.py` | 9 项通过 |
| 开发文档工具 | `python3 scripts/work_test.py`；`scripts/work check` | 44 项工具测试通过；325 份开发文档通过 |
| 文档链接 | `python3 scripts/docs_test.py`，使用独立临时 Git index/object directory 纳入尚未提交的文档 | 394 份 Markdown 通过；未修改用户 index |
| 独立复核 | 经用户授权的只读子智能体 `review_work040` | 最终复核通过，无剩余确定问题 |

### 真实端到端与回退

执行 `python3 apps/server/judging-service/scripts/node-e2e.py --keep`。最终证据目录：
`/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/cherry-work040-s4h51j10`，
主记录为 `evidence.json`，Java 日志在该目录，输出摘要在 `/tmp/work040-e2e.log`。

- 空 judging 库没有 provision，未带令牌/错误令牌注册均为 401；真实节点注册产生首个 ACTIVE 环境。
- Finder ZIP 包含中文 wrapper 和 macOS 元数据，上传、绑定、远程安装、重复安装、C++ 参考校准全部成功。
- ZIP SHA-256 为 `3cb2d97b914e9a6fbd590de75e85d230bd4ed34833ccf352804f31467a0f1084`；测试数据版本为 `01a07612-75d3-749a-8c59-65028052c4a8`。
- 默认链路只写逐节点回执，Java 未创建本地部署目录，旧环境级 deployment 表在显式回退前为空。
- 停止节点后租约到期，readiness 失败、部署返回固定 NO_ONLINE_JUDGE_NODE；重启产生新 session，旧回执暂不可用，重装核验后恢复。
- 显式切为 legacy-local，恢复测试专用只读 bind mount，真实 C++ 判题返回 AC；原节点回执与标定未改写，然后恢复 node-remote。
- 不存在的切换目标触发数据库 guard，原 ACTIVE 保持；新策略、新 nodeId、新私有卷仅产生 REGISTERED 环境，显式事务切换后部署成功。
- 旧版本先在旧环境发布，再创建复用测试数据的新修订，在新环境独立校准；切回旧环境后原标定仍可用，原已发布版本 API 快照逐字段完全相同。
- 原发布版本 `01a07612-7485-7643-a142-aadd32a0d72d`；新修订 `01a07613-2f83-76b9-9930-42a7a2e44c67`；原环境 `01a07612-8fc9-77aa-8a61-db51ff7a9c38`。
- 公开 requestId 示例：`req_cfdc43a927454bc4b07b94cc05fab9a3`、`req_436cd5cc49f04c07bfb0d9dccc02a649`；完整列表见 evidence.json。日志含注册、恢复、安装开始/成功/失败与固定错误码，可关联 request/trace；不记录控制令牌、JWT 或参考源码。

### 独立复核修复与故障测试

复核发现的旧 session 抢占、失败后旧 READY 残留、环境元数据占位、既有库迁移说明缺失、Judge 代码身份未入指纹、新身份复用旧卷六项均已修复并关闭。
注册测试用可控时钟覆盖精确 35 秒边界；保存已接受 session 防止旧进程重新注册。回执按修订号条件失效，旧失败不能撤销较新成功。Go 测试覆盖 hash/manifest/身份/路径/ZIP/UTF-8/资源限制、取消与半成品清理，HTTP 测试覆盖多余 multipart、401、错误体收敛、严格回执字段和响应正文挂起超时。

### Web 设计系统自检

1. 本次新增 0 个框。恢复原因放在已有部署按钮下，使用间距；输入和数据选择的边框沿用控件本身。
2. 沿用工作台三段流程布局，按钮与提示左边缘对齐；截图核对桌面和 320px，窄屏折行，无横向溢出。
3. 标题 foreground、正文、muted 恢复说明、meta 版本信息保持原层级；disabled 只用于控件，原因另用可读 muted 文本。
4. 使用既有 WorkbenchPageTemplate 和 ProcessSection，没有新建页面模板。
5. 新增 0 处 `var(--ds-*)`、0 个 token/alias；使用现有 Text 和 margin alias。
6. 步骤用序号和文字表达；本次新增 0 处饱和色或彩色 Badge。
7. 真实浏览器验证两个主题、320px、200% zoom、键盘焦点、中文恢复提示、forced-colors 与 reduced-motion；窄屏原因完整显示并折行。
8. 对照 measurements.md，沿用原控件字号、行高、边框与间距合同；只新增 Text 说明，不改变控件三态。截图保存在 `/tmp/work040-browser-evidence-final/`，已目视核对深色桌面和浅色窄屏。

### 验证期间修正

真实链路发现并修复 publish-check 的固定六项假设；测试脚本补齐校准后的 rowVersion 刷新、SQL USE 与注释之间换行，以及 READY_FOR_REVIEW→发布→新草稿的合法迁移路径。
真实 Judge 用例与浏览器停机注入使用同一节点，第一次并行运行受到停机干扰；最终改为浏览器完成后串行执行，完整 `clean verify` 150 项通过、0 跳过，日志为 `/tmp/work040-java-final-all.log`。

## 未通过项

无未通过项。

## 范围检查

按 TASK-071～TASK-074 的边界核对，必要扩展均先登记 PLAN-026 和 TASK-074。
未改 user-service/submission-service 业务实现、V1 migration、problem-service 数据库、sandbox 或 run 契约。
user-service 仅更新生产必填配置的测试白名单。保留工作区 WORK-038 的 ZIP 修复与 WORK-039 的 JWT/JWKS 修复；公共 OpenAPI 仅增节点检查/错误说明，既有 ZIP 描述保留。没有提交、推送或执行人工闸命令。

## 遗留问题

无本次范围内未修复的问题。新环境下的校准按原版本状态机通过新草稿修订完成；正式提交 worker 仍以既有工作范围为准。

## 剩余风险

新环境激活是显式操作，会在新修订部署/校准完成前表现为不可用；不能把旧标定复制到新环境。
升级 Judge/sandbox、工具链或运行资源后，需新 nodeId、新卷并重新探测/校准；保留原卷供回退。
本地 control token 是开发默认值；production profile 要求显式配置。未触碰用户现有容器、数据库和资产。
构建仍提示既有 Web 工作台 chunk 大于 500 kB；scripts/work check 仍提示 WORK-033 状态推导问题，与本次实现无关。

## 结论

实现、真实端到端、环境切换、回退、独立复核和全部回归通过，结论 pass。实现完成，提交人工验收；验收闸由用户签署。

最终存储证据见同目录 `node-storage-evidence.json`：Judge 数据目标为私有 named volume，非宿主机 bind；记录实际探测元数据及两环境的逐节点回执。

## 变更记录

- 2026-09-06：状态变更：draft → review。原因：所有验收场景与独立复核已记录实际通过证据，等待用户验收闸

清理结果：最终隔离容器与卷已由脚本回收；`docker ps --filter name=cherry-work040` 为空，用户原 `cherry-oj-engine` Judge/sandbox 仍运行 6 天且 healthy。用户已有数据库、资产、容器和 Git index 未修改。
- 2026-09-06：验收闸通过：review → approved。原因：手工验收未发现问题，确认本次重构完成

## 提交前 CI 配置回归补充

- 2026-09-06：负责人已签署验收闸，`scripts/work refresh WORK-040` 将工作状态更新为 `verified`。
- CI 既有纯 Go 容器 smoke 显式设置 `COMPOSE_FILE=compose.yaml:compose.legacy.yaml`，`TESTDATA_PATH` 指向仓库 `apps/judge-engine/internal/judge/testcase/testdata`；不需要 Java 控制面，验证 legacy 回退语义。
- 本地使用独立 Compose 项目和空闲端口运行该 workflow 的原始 smoke 脚本，仅替换请求端口；`docker compose config --quiet`、健康检查、A+B 三组 AC 与 `local-compose` 指纹断言均通过，退出码 0。执行日志 `/tmp/cherry-work040-ci-smoke.log`。测试结束清理本次容器；用户原服务与手工验收环境保持运行。
