---
id: "DESIGN-034"
type: "design"
title: "重构判题节点注册与测试数据交付"
status: "checked"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["ISSUE-012"]
related: ["WORK-025", "WORK-038"]
implements: []
verifies: []
tags: []
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# DESIGN-034：重构判题节点注册与测试数据交付

## 背景

[ISSUE-012](./10-issue-ISSUE-012.md) 证明当前 503 不是身份或 ZIP 故障，而是控制面没有真实节点事实。
现有设计同时依赖启动 seed 与共享路径，修复其中任一个仍会在下一阶段暴露另一个。

## 目标与限制

- judging-service 以真实在线节点和节点回执决定可部署、可校准、可判题。
- Go Judge 拥有测试数据目录和原子安装职责，Java 不再直接写 Judge 私有文件系统。
- 本地、测试、生产使用同一协议；环境切换和历史快照语义保持。
- 不改变 problem-service 保存的原始 ZIP/manifest，不把测试内容写数据库或日志。
- 不借本次重构改变用户身份时间、题目状态机或 sandbox run contract。

## 整体方案

系统拆成控制面与节点面：

```text
Go Judge Node
  ├─ register/heartbeat ──► judging-service 控制面
  ├─ 接收 test-data install ◄── judging-service
  └─ 返回节点/指纹/hash 回执

problem-service ──ZIP+manifest──► judging-service
judging-service ──流式安装──► ACTIVE 环境中的 ONLINE Node
```

Go Judge 启动时用稳定 `nodeId` 注册 `environmentFingerprint`、advertise endpoint、judge/sandbox 版本、
架构与语言能力；每 10 秒心跳，默认 35 秒租约。租约只代表进程可路由性，不删除环境或历史数据。首次
注册且系统没有环境时，按节点真实元数据创建 ACTIVE 环境；不同指纹只创建 REGISTERED 环境，必须沿用
既有覆盖检查后才能切换。

部署时 judging-service 选择 ACTIVE 环境中的 ONLINE 节点，把原包与 manifest 以有界流发送到节点安装
接口。节点复用当前安全边界：路径、普通文件、逻辑根、macOS 元数据、测例对、UTF-8、文件数、单文件、
总量和压缩比，写临时目录后原子 rename。成功回执同时包含 nodeId、指纹、版本 ID、原包 hash 和文件数；
控制面逐项比对后才写逐节点 READY。

## 模块与数据

- `contracts`：新增版本化 Judge Node 控制协议，覆盖注册、心跳、安装请求/回执和错误 envelope。
- `apps/judge-engine`：新增节点元数据、注册/心跳客户端、安装 API 与节点本地部署存储。
- `judging-service`：新增 node registry、租约查询、远程安装 client 和逐节点 readiness；移除本地
  `FileTestDataDeploymentStore` 作为生产部署路径。
- judging 数据库：新增 `judge_node` 与 `test_data_node_deployment`；现有 `judge_environment` 继续承载
  指纹分组和 ACTIVE/REGISTERED/RETIRED，环境级 deployment 作为兼容聚合事实或迁移后只读视图。
- `problem-service` 与 Gateway：只解析 allowlist 下游错误并保留安全 detail，不改变成功 DTO。
- Web：复用 readiness，在无 ONLINE 节点时禁用部署并展示明确原因。
- Compose：Judge 持有自己的持久 testdata volume，并配置 control-plane URL、advertise URL、稳定 nodeId
  与一个简单的内部共享 token；不再要求 Java 挂载同一宿主机目录。

## 接口与状态

节点状态由 `lease_expires_at` 派生：租约未过期为 ONLINE，否则 OFFLINE。注册以 nodeId 幂等；同 nodeId
改变环境指纹必须拒绝，避免一台逻辑节点悄悄变成另一环境。部署状态按
`testDataVersionId + nodeId + expectedSha256` 幂等，只有节点 READY 才能参与调度。

环境级“已部署”定义为当前至少一个 ONLINE 节点拥有匹配 READY 回执；实际调度必须选择那一个节点，
不能只看环境聚合状态。节点离线不会改写历史 deployment，只会使实时 readiness 失败。节点恢复后可用
本地数据清单与控制面回执对账，缺失时重新安装。

### 错误语义

内部节点协议使用独立版本，不复用浏览器 ADMIN JWT。节点注册/心跳与控制面调用通过私网和单一共享
control token 保护，避免引入证书中心或复杂服务账号体系；token 不入库、不入日志。

管理 API 成功结构保持。错误新增稳定分类：`NO_ONLINE_JUDGE_NODE`、`JUDGE_NODE_UNREACHABLE`、
`JUDGE_NODE_DATA_REJECTED`、`JUDGE_NODE_RECEIPT_MISMATCH`。problem-service 与 Gateway 只允许这些固定
code 和有界 detail 传到 ADMIN 页面，其他 5xx 仍收敛为通用错误。

## 安全与失败

- 注册/心跳失败：节点指数退避重试，Judge 本地 API 可继续健康，但控制面不向它派发新任务。
- 流中断或校验失败：节点清理临时目录，不返回 READY；控制面记录有界失败 code，可幂等重试。
- 回执丢失：重试相同安装，节点按 hash 返回同一 READY，不重复解压。
- 节点离线：不删除数据和历史回执；恢复后对账。
- 新指纹上线：只 REGISTERED，不替换 ACTIVE；既有环境和在途任务保持。

## 监控与部署

日志和指标至少覆盖 node registered/heartbeat expired/recovered、安装 started/ready/failed、回执不匹配；
使用 nodeId、environmentId、testDataVersionId、requestId/traceId 关联，不记录 ZIP 内容、答案或 token。
readiness 返回 ACTIVE 环境、ONLINE 节点数和数据可用性检查，但不公开 endpoint 和内部版本细节。
交付顺序为契约与 migration、Java 注册控制面、Go 节点注册/安装、Java 远程部署、Compose/Web 切换；
每一步都保留上一条可运行链路，端到端通过后才关闭 legacy-local。

## 迁移与兼容

先新增表与新协议，不立即删除旧字段；将现有环境映射为兼容分组。旧环境没有真实节点时 readiness 明确
失败，不伪造 ONLINE。节点侧安装链路完成并通过端到端后，再停止启动 provision 和 Java 本地部署存储。

切换期由配置开关选择 legacy-local 或 node-remote，默认保持 legacy，验收后一次切为 node-remote；回退
只切回 legacy，不回滚 migration、不删除节点回执。已发布题目、已有 calibration 和历史 JudgeInput 不
迁移或改写。

## 备选方案

1. 默认启用 dev profile：只补数据库行，无法解决节点在线与目录可见性，拒绝。
2. 强制 Java/Go 共享 volume：单机可用，但节点扩容、远程机器和故障检测仍依赖人工，拒绝。
3. 立即引入 S3/MinIO：最终可让节点按 hash 拉取并缓存，但当前增加一套基础设施；先用控制面流式发送，
   协议保留未来把 payload 换成 artifact reference 的空间。
4. 服务发现组件：当前节点规模不需要额外 Consul/Kubernetes CRD；数据库租约足够，未来可替换发现来源
   而不改变逐节点部署回执。

## 风险与重审条件

主要风险是跨语言契约漂移、节点租约与部署回执竞态、切换期间双写不一致。必须用共享 schema fixture、
可控时钟、真实文件系统/MySQL 和 Compose 端到端覆盖。若节点规模达到需要广播大量 ZIP、或跨机房传输
成本明显，应升级为内容寻址对象存储 + 节点拉取；环境与逐节点回执模型保持不变。

## 变更记录

- 2026-09-06：撤回 default-profile 方案，改为节点自注册、租约和节点侧原子安装。
- 2026-09-06：状态变更：draft → review。原因：节点注册、租约、环境归组、节点侧安装、逐节点回执、错误语义与迁移回退设计完整
- 2026-09-06：结构与内容校验通过，由工具置为 checked。

- 2026-09-06：节点协议增加每次进程启动生成的 sessionId。不同 session 注册保留历史回执但标为不可用；节点以幂等安装重新校验本地目录后恢复逐节点 READY，防止数据卷丢失或旧进程心跳复活回执。

- 2026-09-06：独立复核发现旧进程可在心跳冲突后重新注册夺回 nodeId。追加 node/session 历史，已被替换会话永久禁止重注册；Go 对控制面 409 停止注册循环并记录固定事件，避免双进程抢占。

### 实际环境探测

节点启动时通过既有 sandbox /run 执行固定、限时、有界输出的只读环境探测，并读取 /version；CPU/架构/系统/内核/工具链来自实际执行端。资源配额与 Judge、sandbox 两端二进制摘要纳入策略摘要，环境指纹由这些元数据和 Judge 策略计算。探测失败不得使用配置占位值继续注册。异指纹的既有 ACTIVE 环境保留，不自动改写历史标定；未完成显式新环境切换的已有库继续使用 legacy-local。
